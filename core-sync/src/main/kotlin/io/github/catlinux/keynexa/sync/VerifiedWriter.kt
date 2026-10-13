package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException
import java.time.Clock

/** Result of one guarded write of the vault file. */
internal sealed interface WriteResult {
    class Done(
        val fingerprint: Fingerprint,
    ) : WriteResult

    class Failed(
        val reason: SaveFailure,
        val file: FileState,
        val unsavedCopy: BackupRef?,
    ) : WriteResult

    /** The file changed between the check and the write; nothing was written, another round merges it. */
    data object FileChanged : WriteResult
}

/**
 * Replaces the vault file with new bytes, guarded by private copies and the save journal, and verifies the result
 * by reading it back. Steps 3 to 6 of the safe-save pipeline in `docs/ARCHITECTURE.md`.
 */
internal class VerifiedWriter(
    private val storage: StoragePort,
    private val backups: BackupStore,
    private val journal: SaveJournal,
    private val clock: Clock,
    private val keepBackups: Int,
) {
    fun write(
        location: VaultLocation,
        current: ByteArray,
        encoded: ByteArray,
    ): WriteResult {
        val now = clock.instant()
        val previous = Fingerprint.of(current)
        // Never write without a copy of what is replaced and of what replaces it.
        val previousCopy = backups.putOrNull(location, BackupKind.BeforeSave, current, now)
        val outgoingCopy = backups.putOrNull(location, BackupKind.UnsavedChanges, encoded, now)
        if (previousCopy == null || outgoingCopy == null || !begin(location, previousCopy, outgoingCopy, now)) {
            return WriteResult.Failed(SaveFailure.StorageError, FileState.Untouched, outgoingCopy)
        }

        // A last check right before writing narrows the window in which a sync tool's write could be overwritten.
        val again = storage.readOrNull(location)
        if (again == null || Fingerprint.of(again) != previous) return abandon(location, again, outgoingCopy)

        val failure = if (tryWrite(location, encoded)) SaveFailure.WriteNotVerified else SaveFailure.StorageError
        val after =
            storage.readOrNull(location) ?: return WriteResult.Failed(failure, FileState.NeedsRecovery, outgoingCopy)
        return settle(Attempt(location, current, encoded, outgoingCopy), after, failure)
    }

    /** What one write tried to replace and with what. */
    private class Attempt(
        val location: VaultLocation,
        val current: ByteArray,
        val encoded: ByteArray,
        val outgoingCopy: BackupRef,
    )

    // Nothing was written: either the file could not be read again or it changed since the check.
    private fun abandon(
        location: VaultLocation,
        again: ByteArray?,
        outgoingCopy: BackupRef,
    ): WriteResult {
        journal.clearQuietly(location)
        if (again == null) return WriteResult.Failed(SaveFailure.StorageError, FileState.Untouched, outgoingCopy)
        backups.deleteQuietly(outgoingCopy)
        return WriteResult.FileChanged
    }

    private fun begin(
        location: VaultLocation,
        previousCopy: BackupRef,
        outgoingCopy: BackupRef,
        now: java.time.Instant,
    ): Boolean =
        try {
            journal.begin(
                PendingSave(
                    location,
                    previousCopy.fingerprint,
                    outgoingCopy.fingerprint,
                    previousCopy,
                    outgoingCopy,
                    now,
                ),
            )
            true
        } catch (failure: IOException) {
            false
        }

    private fun tryWrite(
        location: VaultLocation,
        bytes: ByteArray,
    ): Boolean =
        try {
            storage.write(location, bytes)
            true
        } catch (failure: IOException) {
            false
        }

    // Decides what the file now holds after the write and repairs it when KeyNexa's own write tore it.
    private fun settle(
        attempt: Attempt,
        after: ByteArray,
        failure: SaveFailure,
    ): WriteResult {
        val location = attempt.location
        val outgoingCopy = attempt.outgoingCopy
        val afterFingerprint = Fingerprint.of(after)
        val result =
            when {
                afterFingerprint == outgoingCopy.fingerprint -> {
                    backups.deleteQuietly(outgoingCopy)
                    runCatching { backups.prune(location, keepBackups) }
                    WriteResult.Done(afterFingerprint)
                }
                afterFingerprint == Fingerprint.of(attempt.current) ->
                    WriteResult.Failed(failure, FileState.Untouched, outgoingCopy)
                // A complete file from another program replaced ours after the write; it is left as it is.
                !isTornByOwnWrite(after, attempt.encoded, attempt.current) ->
                    WriteResult.Failed(SaveFailure.ChangedDuringSave, FileState.ReplacedElsewhere, outgoingCopy)
                storage.writeVerified(location, attempt.current) ->
                    WriteResult.Failed(failure, FileState.Restored, outgoingCopy)
                // The journal stays, so recovery tries again on the next attempt.
                else -> return WriteResult.Failed(failure, FileState.NeedsRecovery, outgoingCopy)
            }
        journal.clearQuietly(location)
        return result
    }
}
