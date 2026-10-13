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
 * Access to the bytes of a vault file. `platform-android` implements it with the Storage Access Framework; tests
 * use fakes that inject failures.
 *
 * Every method may throw [java.io.IOException] (file missing, permission revoked, provider failure). The safe-save
 * pipeline in `core-sync` builds its guarantees on top of this port, so implementations stay simple.
 */
interface StoragePort {
    /** Reads the whole file. */
    fun read(location: VaultLocation): ByteArray

    /** Size and modification time, when the storage knows them. */
    fun metadata(location: VaultLocation): FileMetadata

    /** Files in the same folder as [location], excluding the file itself. Empty if the folder cannot be listed. */
    fun listSiblings(location: VaultLocation): List<SiblingFile>

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
