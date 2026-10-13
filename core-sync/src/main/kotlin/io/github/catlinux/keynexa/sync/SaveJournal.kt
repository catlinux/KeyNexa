package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.VaultLocation
import java.time.Instant

/**
 * A save that started writing the vault file. It is recorded before the write and cleared once the result is
 * verified, so after a crash the next launch knows whether the file may be torn and which copy restores it.
 */
data class PendingSave(
    val location: VaultLocation,
    /** The file before the write; [previousCopy] holds exactly these bytes. */
    val previous: Fingerprint,
    /** What the file must contain if the write completed; [outgoingCopy] holds exactly these bytes. */
    val expected: Fingerprint,
    val previousCopy: BackupRef,
    val outgoingCopy: BackupRef,
    val startedAt: Instant,
)

/** Durable record of saves in progress, one per vault, in app-private storage. May throw [java.io.IOException]. */
interface SaveJournal {
    fun begin(pending: PendingSave)

    fun pending(location: VaultLocation): PendingSave?

    fun clear(location: VaultLocation)
}
