package io.github.catlinux.keynexa.sync

import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** How many cloud copies to keep and how often to upload one. */
data class BackupOptions(
    val keep: Int = 10,
    val minInterval: Duration = Duration.ofDays(1),
    val prefix: String = "KeyNexa/",
)

/** Why a backup was not attempted. */
enum class SkipReason {
    /** A previous upload succeeded less than [BackupOptions.minInterval] ago. */
    NotDue,
}

/** What went wrong with an upload. */
enum class BackupError {
    /** The service rejected the credentials or the grant. */
    NotAuthorized,

    /** The network, the provider or the device made the upload impossible for now. */
    Unavailable,

    /** The service refused the object (quota, policy). */
    Rejected,

    /** Anything else. */
    Unknown,
}

/** What one backup attempt did. */
sealed interface BackupResult {
    data class Uploaded(
        val name: String,
        val rotated: Int,
    ) : BackupResult

    data class Skipped(
        val reason: SkipReason,
    ) : BackupResult

    data class Failed(
        val error: BackupError,
    ) : BackupResult
}

/**
 * The last success and the last error of each vault's cloud backup.
 *
 * Kept apart from the uploader so the persistent version can arrive later without touching the upload logic; the
 * in-memory one is enough for tests and for a session that has not been saved anywhere.
 */
interface BackupState {
    fun lastSuccess(vaultId: String): Instant?

    fun recordSuccess(
        vaultId: String,
        at: Instant,
    )

    fun lastError(vaultId: String): BackupError?

    fun recordError(
        vaultId: String,
        error: BackupError,
        at: Instant,
    )

    fun clearError(vaultId: String)
}

/** The state of a single run of the app; nothing survives a restart. */
class InMemoryBackupState : BackupState {
    private val successes = HashMap<String, Instant>()
    private val errors = HashMap<String, BackupError>()
    private val errorTimes = HashMap<String, Instant>()

    override fun lastSuccess(vaultId: String): Instant? = successes[vaultId]

    override fun recordSuccess(
        vaultId: String,
        at: Instant,
    ) {
        successes[vaultId] = at
    }

    override fun lastError(vaultId: String): BackupError? = errors[vaultId]

    override fun recordError(
        vaultId: String,
        error: BackupError,
        at: Instant,
    ) {
        errors[vaultId] = error
        errorTimes[vaultId] = at
    }

    override fun clearError(vaultId: String) {
        errors.remove(vaultId)
        errorTimes.remove(vaultId)
    }

    /** When the last error of [vaultId] happened, for callers that want to show it. */
    fun lastErrorAt(vaultId: String): Instant? = errorTimes[vaultId]
}

/**
 * Uploads encrypted vault backups and rotates the old ones, without ever touching the vault content (ADR-0008 §3).
 *
 * [encryptedBytes] are the `.kdbx` bytes exactly as they are on disk: they are never decrypted, decoded or logged.
 * The methods are synchronous and never throw [IOException] at the caller, because a failed upload must never stop a
 * save; the caller runs them off the main thread. A programming error (a `RuntimeException`) is not swallowed.
 */
class BackupUploader(
    private val backend: CloudBackend,
    private val state: BackupState,
    private val options: BackupOptions = BackupOptions(),
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Uploads only when the previous success is old enough; otherwise leaves the backend alone. */
    fun backupIfDue(
        vaultId: String,
        vaultName: String,
        encryptedBytes: ByteArray,
    ): BackupResult {
        val now = clock.instant()
        val last = state.lastSuccess(vaultId)
        if (last != null && Duration.between(last, now) < options.minInterval) {
            return BackupResult.Skipped(SkipReason.NotDue)
        }
        return upload(vaultId, vaultName, encryptedBytes, now)
    }

    /** Uploads whatever the interval says. */
    fun backupNow(
        vaultId: String,
        vaultName: String,
        encryptedBytes: ByteArray,
    ): BackupResult = upload(vaultId, vaultName, encryptedBytes, clock.instant())

    private fun upload(
        vaultId: String,
        vaultName: String,
        encryptedBytes: ByteArray,
        at: Instant,
    ): BackupResult {
        val safeName = sanitize(vaultName)
        val name = objectName(safeName, at)
        return try {
            backend.put(name, encryptedBytes)
            state.recordSuccess(vaultId, at)
            state.clearError(vaultId)
            BackupResult.Uploaded(name, rotate(safeName))
        } catch (unauthorized: CloudAuthException) {
            state.recordError(vaultId, BackupError.NotAuthorized, at)
            BackupResult.Failed(BackupError.NotAuthorized)
        } catch (unavailable: IOException) {
            state.recordError(vaultId, BackupError.Unavailable, at)
            BackupResult.Failed(BackupError.Unavailable)
        }
    }

    // Deletes the oldest copies this uploader itself created, and only those: anything whose name does not match the
    // exact pattern is left alone, so another vault or a file the user put there is never touched.
    private fun rotate(safeName: String): Int {
        val folder = "${options.prefix}$safeName/"
        val pattern = Regex("^${Regex.escape(folder)}${Regex.escape(safeName)}-$TIMESTAMP_PATTERN\\.kdbx$")
        val ours =
            try {
                backend.list(folder).map { it.name }.filter { pattern.matches(it) }
            } catch (_: IOException) {
                return 0
            }
        val keep = options.keep.coerceAtLeast(1)
        var rotated = 0
        for (name in ours.sorted().dropLast(keep)) {
            try {
                backend.delete(name)
                rotated++
            } catch (_: IOException) {
                // A copy that cannot be deleted stays; the upload itself already succeeded.
            }
        }
        return rotated
    }

    private fun objectName(
        safeName: String,
        at: Instant,
    ): String = "${options.prefix}$safeName/$safeName-${TIMESTAMP.format(at)}.kdbx"

    private companion object {
        /** The UTC timestamp of a copy's name; it also sorts the names chronologically. */
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        const val TIMESTAMP_PATTERN = "\\d{8}T\\d{6}Z"
    }
}

/** Keeps only letters, digits, hyphen and underscore; anything else becomes an underscore. */
private fun sanitize(name: String): String {
    if (name.isEmpty()) return "vault"
    val sanitized =
        name.map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
        }
    return sanitized.joinToString("")
}
