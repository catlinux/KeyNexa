package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException

/**
 * Finishes or undoes a save that was interrupted (app killed, device off). It needs no credentials: it compares
 * the file's bytes with the private copies the interrupted save recorded in the journal.
 */
class SaveRecovery(
    private val storage: StoragePort,
    private val backups: BackupStore,
    private val journal: SaveJournal,
) {
    /** Call before opening a vault. */
    fun recover(location: VaultLocation): RecoveryOutcome {
        val pending =
            try {
                journal.pending(location)
            } catch (failure: IOException) {
                return RecoveryOutcome.StorageUnavailable
            } ?: return RecoveryOutcome.NothingPending
        val file = storage.readOrNull(location) ?: return RecoveryOutcome.StorageUnavailable
        val outcome =
            when (Fingerprint.of(file)) {
                pending.expected -> {
                    backups.deleteQuietly(pending.outgoingCopy)
                    RecoveryOutcome.SaveHadCompleted
                }
                pending.previous -> RecoveryOutcome.SaveHadNotStarted(pending.outgoingCopy)
                else -> recoverTorn(location, file, pending)
            }
        if (outcome !is RecoveryOutcome.StillDamaged) journal.clearQuietly(location)
        return outcome
    }

    private fun recoverTorn(
        location: VaultLocation,
        file: ByteArray,
        pending: PendingSave,
    ): RecoveryOutcome {
        val previous = backups.readIntactOrNull(pending.previousCopy)
        val outgoing = backups.readIntactOrNull(pending.outgoingCopy)
        return when {
            previous == null || outgoing == null -> RecoveryOutcome.StillDamaged(pending.previousCopy)
            !isTornByOwnWrite(file, outgoing, previous) -> RecoveryOutcome.ChangedElsewhere(pending.outgoingCopy)
            storage.writeVerified(location, previous, pending.previous) ->
                RecoveryOutcome.Restored(pending.previousCopy, pending.outgoingCopy)
            else -> RecoveryOutcome.StillDamaged(pending.previousCopy)
        }
    }
}
