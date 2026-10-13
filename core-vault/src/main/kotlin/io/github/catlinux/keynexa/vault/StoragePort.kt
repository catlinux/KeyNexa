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
 * This is the minimal surface needed to open and list vaults. The safe-save pipeline (write modes, truncation,
 * verification, private backups) is specified in `docs/ARCHITECTURE.md` and extends this port in phase D1.
 */
interface StoragePort {
    /** Reads the whole file. */
    fun read(location: VaultLocation): ByteArray

    /** Size and modification time, when the storage knows them. */
    fun metadata(location: VaultLocation): FileMetadata

    /** Files in the same folder as [location], excluding the file itself. Empty if the folder cannot be listed. */
    fun listSiblings(location: VaultLocation): List<SiblingFile>
}
