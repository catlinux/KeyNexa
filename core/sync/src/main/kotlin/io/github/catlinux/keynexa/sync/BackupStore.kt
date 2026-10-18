package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.VaultLocation
import java.time.Instant

/** Why a private copy was kept. */
enum class BackupKind {
    /** The file as it was just before KeyNexa overwrote it. Rotated: only the newest few are kept. */
    BeforeSave,

    /**
     * KeyNexa's own version that could not be written (external change, write failure). Never rotated away
     * automatically: it holds the user's unsaved changes until they are merged or discarded on purpose.
     */
    UnsavedChanges,
}

data class BackupRef(
    val id: String,
    val location: VaultLocation,
    val kind: BackupKind,
    val createdAt: Instant,
    val fingerprint: Fingerprint,
)

/**
 * Private, encrypted copies of vault files, in app storage that is excluded from Android backup
 * (`platform/android` implements it under `noBackupFilesDir`). Only encrypted bytes are stored, never plaintext.
 * Every method may throw [java.io.IOException].
 */
interface BackupStore {
    fun put(
        location: VaultLocation,
        kind: BackupKind,
        bytes: ByteArray,
        createdAt: Instant,
    ): BackupRef

    /** Backups of [location], newest first. */
    fun list(location: VaultLocation): List<BackupRef>

    fun read(ref: BackupRef): ByteArray

    fun delete(ref: BackupRef)
}

/** Keeps the newest [keep] `BeforeSave` copies of a vault and deletes older ones. Unsaved changes are never pruned. */
internal fun BackupStore.prune(
    location: VaultLocation,
    keep: Int,
) {
    list(location)
        .filter { it.kind == BackupKind.BeforeSave }
        .sortedByDescending { it.createdAt }
        .drop(keep)
        .forEach(::delete)
}
