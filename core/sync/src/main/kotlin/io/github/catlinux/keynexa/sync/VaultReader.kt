package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.KdbxEngine
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException

/**
 * A vault as read from storage, with the fingerprint of the exact bytes it came from and the [metadata] read just
 * before those bytes. The metadata can be older than the content, never newer, so comparing it later can only ask for
 * an extra full read, never miss a change.
 */
data class LoadedVault(
    val location: VaultLocation,
    val vault: Vault,
    val fingerprint: Fingerprint,
    val metadata: FileMetadata? = null,
)

/** Waits between attempts; replaced in tests so they do not sleep. */
fun interface Sleeper {
    fun sleep(millis: Long)
}

/**
 * Reads and decrypts vault files, tolerating files that are still being written by a sync tool: a file that fails
 * its integrity checks is read again after a delay instead of being reported as broken at once.
 */
class VaultReader(
    private val storage: StoragePort,
    private val engine: KdbxEngine,
    private val sleeper: Sleeper = Sleeper(Thread::sleep),
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
) {
    /**
     * Opens [location]. Set [credentialsKnownGood] when these credentials already opened this vault: in KDBX 3.x a
     * damaged file and a wrong key fail the same way, so the failure is then retried as possible damage.
     *
     * @throws VaultException as [KdbxEngine.decode] does, once the retries are exhausted.
     * @throws IOException when the storage keeps failing.
     */
    fun open(
        location: VaultLocation,
        credentials: Credentials,
        credentialsKnownGood: Boolean = false,
    ): LoadedVault {
        var attempt = 0
        while (true) {
            val failure = attemptOpen(location, credentials, credentialsKnownGood)
            if (failure is LoadedVault) return failure
            if (attempt >= retryDelaysMillis.size) throw failure as Exception
            sleeper.sleep(retryDelaysMillis[attempt])
            attempt++
        }
    }

    // Returns the vault, or the transient failure to retry; any other failure is thrown at once.
    private fun attemptOpen(
        location: VaultLocation,
        credentials: Credentials,
        credentialsKnownGood: Boolean,
    ): Any =
        try {
            // The metadata is read before the bytes: if the file changes in between, the stored metadata is older
            // than the content and the next comparison asks for a full read again.
            val metadata = readMetadata(location)
            val bytes = storage.read(location)
            try {
                LoadedVault(location, engine.decode(bytes, credentials), Fingerprint.of(bytes), metadata)
            } catch (wrong: VaultException.WrongCredentials) {
                // In KDBX 4 the header HMAC proves the key, so a wrong key there is real (the password may have
                // been changed on another device). Only the ambiguous 3.x case is retried as possible damage.
                if (credentialsKnownGood && isKdbx3(bytes)) wrong else throw wrong
            }
        } catch (damaged: VaultException.Corrupted) {
            damaged
        } catch (unavailable: IOException) {
            unavailable
        }

    /** Whether the file at [location] differs from the bytes [known] was read from. Reads and hashes, no decryption. */
    fun hasChanged(
        location: VaultLocation,
        known: Fingerprint,
    ): Boolean = hasChanged(location, known, knownMetadata = null, forceFull = false)

    /**
     * Whether the file changed, avoiding the full read when [knownMetadata] and the current metadata agree on both
     * size and modification time. Any doubt (missing metadata, a different value or [forceFull]) falls back to
     * reading and hashing the whole file, so a change is never missed.
     */
    fun hasChanged(
        location: VaultLocation,
        known: Fingerprint,
        knownMetadata: FileMetadata?,
        forceFull: Boolean = false,
    ): Boolean {
        if (!forceFull && knownMetadata != null) {
            val knownSize = knownMetadata.sizeBytes
            val knownTime = knownMetadata.lastModified
            if (knownSize != null && knownTime != null) {
                val current = readMetadata(location)
                if (current?.sizeBytes == knownSize && current.lastModified == knownTime) return false
            }
        }
        return Fingerprint.of(storage.read(location)) != known
    }

    /** The size and time the storage reports now, or null when it cannot say. Take it BEFORE reading the bytes. */
    @Suppress("SwallowedException")
    fun readMetadata(location: VaultLocation): FileMetadata? =
        try {
            storage.metadata(location)
        } catch (unavailable: IOException) {
            null
        }

    private fun isKdbx3(bytes: ByteArray): Boolean =
        runCatching { engine.inspect(bytes).version.major == KDBX_3 }.getOrDefault(false)

    private companion object {
        const val KDBX_3 = 3
        val DEFAULT_RETRY_DELAYS_MILLIS = listOf(1_000L, 2_000L, 4_000L, 8_000L)
    }
}
