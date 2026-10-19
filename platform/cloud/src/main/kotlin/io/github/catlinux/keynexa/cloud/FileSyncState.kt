package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudSyncState
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.SyncError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Properties

private const val TEMPORARY_SUFFIX = ".tmp"

/**
 * The synchronisation state on disk, as a `Properties` text file: no secret and no vault content ever reach it.
 *
 * Each synchronisation id has `syncId.lastEtag`, `syncId.lastLocalFingerprint`, `syncId.lastSuccess`,
 * `syncId.lastError` and `syncId.lastErrorAt`. Reads are tolerant: a missing, unreadable or corrupt file means "no
 * state", never an exception. Writes are atomic (a temporary file and a move).
 */
@Suppress("TooManyFunctions")
class FileSyncState(
    private val file: File,
) : CloudSyncState {
    private val etags = HashMap<String, String>()
    private val fingerprints = HashMap<String, Fingerprint>()
    private val successes = HashMap<String, Instant>()
    private val errors = HashMap<String, SyncError>()
    private val errorTimes = HashMap<String, Instant>()
    private var loaded = false

    override fun lastEtag(syncId: String): String? {
        ensureLoaded()
        return etags[syncId]
    }

    override fun lastLocalFingerprint(syncId: String): Fingerprint? {
        ensureLoaded()
        return fingerprints[syncId]
    }

    override fun lastSuccess(syncId: String): Instant? {
        ensureLoaded()
        return successes[syncId]
    }

    override fun lastError(syncId: String): SyncError? {
        ensureLoaded()
        return errors[syncId]
    }

    override fun lastErrorAt(syncId: String): Instant? {
        ensureLoaded()
        return errorTimes[syncId]
    }

    override fun recordSuccess(
        syncId: String,
        etag: String?,
        fingerprint: Fingerprint,
        at: Instant,
    ) {
        ensureLoaded()
        if (etag == null) etags.remove(syncId) else etags[syncId] = etag
        fingerprints[syncId] = fingerprint
        successes[syncId] = at
        errors.remove(syncId)
        errorTimes.remove(syncId)
        persist()
    }

    override fun recordError(
        syncId: String,
        error: SyncError,
        at: Instant,
    ) {
        ensureLoaded()
        errors[syncId] = error
        errorTimes[syncId] = at
        persist()
    }

    /** Forgets every vault and deletes the state file, so a new connection starts with no old state. */
    fun clearAll() {
        etags.clear()
        fingerprints.clear()
        successes.clear()
        errors.clear()
        errorTimes.clear()
        loaded = true
        if (file.exists()) file.delete()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val properties = readProperties() ?: return
        properties.stringPropertyNames().forEach { key -> applyProperty(key, properties.getProperty(key)) }
    }

    private fun applyProperty(
        key: String,
        value: String?,
    ) {
        val dot = key.lastIndexOf('.')
        if (dot <= 0) return
        val syncId = key.substring(0, dot)
        when (key.substring(dot + 1)) {
            ETAG_KEY -> value?.let { etags[syncId] = it }
            FINGERPRINT_KEY -> value?.let { fingerprints[syncId] = Fingerprint(it) }
            SUCCESS_KEY -> value.toInstantOrNull()?.let { successes[syncId] = it }
            ERROR_KEY -> SYNC_ERRORS.firstOrNull { it.toString() == value }?.let { errors[syncId] = it }
            ERROR_AT_KEY -> value.toInstantOrNull()?.let { errorTimes[syncId] = it }
        }
    }

    @Suppress("SwallowedException")
    private fun readProperties(): Properties? {
        if (!file.isFile) return null
        return try {
            Properties().apply { load(ByteArrayInputStream(file.readBytes())) }
        } catch (failure: IOException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }
    }

    private fun persist() {
        val properties = Properties()
        etags.forEach { (syncId, etag) -> properties.setProperty("$syncId.$ETAG_KEY", etag) }
        fingerprints.forEach { (syncId, fingerprint) ->
            properties.setProperty("$syncId.$FINGERPRINT_KEY", fingerprint.hex)
        }
        successes.forEach { (syncId, at) -> properties.setProperty("$syncId.$SUCCESS_KEY", at.toString()) }
        errors.forEach { (syncId, error) -> properties.setProperty("$syncId.$ERROR_KEY", error.toString()) }
        errorTimes.forEach { (syncId, at) -> properties.setProperty("$syncId.$ERROR_AT_KEY", at.toString()) }
        val out = ByteArrayOutputStream()
        properties.store(out, null)
        writeAtomically(out.toByteArray())
    }

    @Suppress("SwallowedException")
    private fun writeAtomically(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}$TEMPORARY_SUFFIX")
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (failure: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    @Suppress("SwallowedException")
    private fun String?.toInstantOrNull(): Instant? =
        if (this == null) {
            null
        } else {
            try {
                Instant.parse(this)
            } catch (failure: DateTimeParseException) {
                null
            }
        }

    private companion object {
        const val ETAG_KEY = "lastEtag"
        const val FINGERPRINT_KEY = "lastLocalFingerprint"
        const val SUCCESS_KEY = "lastSuccess"
        const val ERROR_KEY = "lastError"
        const val ERROR_AT_KEY = "lastErrorAt"

        // SyncError is a sealed interface, so the stored code is matched back against its known objects by name.
        val SYNC_ERRORS =
            listOf(
                SyncError.Busy,
                SyncError.NotAuthorized,
                SyncError.Unavailable,
                SyncError.NotSupported,
                SyncError.Unreadable,
                SyncError.InvalidSyncId,
                SyncError.OtherPassword,
                SyncError.NotSameVault,
                SyncError.SaveFailed,
            )
    }
}
