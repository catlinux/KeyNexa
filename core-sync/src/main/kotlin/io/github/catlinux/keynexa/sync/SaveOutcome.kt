package io.github.catlinux.keynexa.sync

/** What a save did. The interface must report a save as done only for [SaveOutcome.Saved]. */
sealed interface SaveOutcome {
    /** The file now holds exactly the bytes KeyNexa wrote, verified by reading them back. */
    data class Saved(
        val loaded: LoadedVault,
        /** True when changes made elsewhere were merged in before writing. */
        val merged: Boolean,
    ) : SaveOutcome

    /**
     * Nothing was saved. The user's changes are still in memory and, when possible, in [unsavedCopy], a private
     * encrypted copy that survives the app being closed.
     */
    data class NotSaved(
        val reason: SaveFailure,
        val file: FileState,
        val unsavedCopy: BackupRef?,
    ) : SaveOutcome
}

enum class SaveFailure {
    /** The file changed elsewhere and no merger is available yet. */
    ExternalChangeNeedsMerge,

    /** The file changed elsewhere and the two versions cannot be combined safely. */
    Unmergeable,

    /** The file changed again while KeyNexa was saving; saving again will merge the newest version. */
    ChangedDuringSave,

    /** The file on storage is damaged or still being transferred; retry later. */
    FileUnreadable,

    /** The file on storage no longer opens with these credentials (changed on another device). */
    CredentialsChanged,

    /** KeyNexa could not produce a valid encrypted file from the vault. */
    EncodeFailed,

    /** The storage failed (permission revoked, provider error, out of space). */
    StorageError,

    /** The write did not leave the expected bytes in the file. */
    WriteNotVerified,
}

/** The state of the vault file after a failed save. */
enum class FileState {
    /** Not modified by this save. */
    Untouched,

    /** Modified, then put back byte for byte as it was before the save. */
    Restored,

    /** Another program replaced the file after KeyNexa wrote it; that version is left as it is. */
    ReplacedElsewhere,

    /**
     * Modified and could not be put back. A private copy of the previous file exists and the interrupted save is
     * still recorded, so recovery runs again on the next attempt.
     */
    NeedsRecovery,
}

/** What [SaveRecovery.recover] found after a save that may have been interrupted. */
sealed interface RecoveryOutcome {
    data object NothingPending : RecoveryOutcome

    /** The interrupted save had finished writing; the file holds the saved version. */
    data object SaveHadCompleted : RecoveryOutcome

    /** The write had not started; the file is the previous version and [unsavedCopy] holds the changes. */
    data class SaveHadNotStarted(
        val unsavedCopy: BackupRef,
    ) : RecoveryOutcome

    /** Another program replaced the file after the interruption; it is left as it is. */
    data class ChangedElsewhere(
        val unsavedCopy: BackupRef,
    ) : RecoveryOutcome

    /** The file was torn by the interruption and the previous version was put back from [previousCopy]. */
    data class Restored(
        val previousCopy: BackupRef,
        val unsavedCopy: BackupRef,
    ) : RecoveryOutcome

    /** The file is torn and could not be restored yet; [previousCopy] still holds the previous version. */
    data class StillDamaged(
        val previousCopy: BackupRef,
    ) : RecoveryOutcome

    /** The storage could not be read; the pending save is kept for a later attempt. */
    data object StorageUnavailable : RecoveryOutcome

    /**
     * No save was interrupted, but [unsavedCopy] keeps changes that an earlier save could not write and that the
     * file still lacks. [SaveRecovery] never reports it: only the open session can decrypt the copy to know.
     */
    data class UnsavedCopyKept(
        val unsavedCopy: BackupRef,
    ) : RecoveryOutcome
}
