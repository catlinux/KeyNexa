package io.github.catlinux.keynexa.vault

import java.time.Instant

/**
 * Where a vault file lives, as an opaque string the platform understands (a SAF document URI on Android, a path on
 * a desktop). The core never parses it.
 */
@JvmInline
value class VaultLocation(
    val value: String,
)

/** What the storage reports about a file without reading it. [lastModified] can be missing or inexact. */
data class FileMetadata(
    val sizeBytes: Long?,
    val lastModified: Instant?,
)

/** A file next to the vault, for example a Syncthing conflict copy. */
data class SiblingFile(
    val location: VaultLocation,
    val name: String,
    val metadata: FileMetadata,
)

/**
 * Access to the bytes of a vault file. `platform/android` implements it with the Storage Access Framework; tests
 * use fakes that inject failures.
 *
 * Every method may throw [java.io.IOException] (file missing, permission revoked, provider failure). The safe-save
 * pipeline in `core/sync` builds its guarantees on top of this port, so implementations stay simple.
 */
interface StoragePort {
    /** Reads the whole file. */
    fun read(location: VaultLocation): ByteArray

    /** Size and modification time, when the storage knows them. */
    fun metadata(location: VaultLocation): FileMetadata

    /** Files in the same folder as [location], excluding the file itself. Empty if the folder cannot be listed. */
    fun listSiblings(location: VaultLocation): List<SiblingFile>

    /** The file's real name, without any directory part: what the folder calls it, not what the app shows. */
    fun name(location: VaultLocation): String

    /**
     * Deletes the file. It must fail with [java.io.IOException] when the provider refuses or the file is already
     * gone, so the caller can keep for another attempt what it could not delete.
     */
    fun delete(location: VaultLocation)

    /** The file's modification time in epoch milliseconds, or null when the storage does not report it. */
    fun lastModified(location: VaultLocation): Long?

    /**
     * Replaces the whole content of the file with [data]. Implementations must truncate: on Android open with mode
     * `"rwt"` and, where a provider ignores the truncation, truncate explicitly. The caller does not trust this
     * call: it reads the file back and compares fingerprints before reporting success.
     */
    fun write(
        location: VaultLocation,
        data: ByteArray,
    )
}
