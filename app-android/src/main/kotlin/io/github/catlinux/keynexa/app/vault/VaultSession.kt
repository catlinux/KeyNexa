package io.github.catlinux.keynexa.app.vault

import io.github.catlinux.keynexa.sync.BackupRef
import io.github.catlinux.keynexa.sync.ConflictCopy
import io.github.catlinux.keynexa.sync.ConflictMerger
import io.github.catlinux.keynexa.sync.ConflictScan
import io.github.catlinux.keynexa.sync.CopyProblem
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.LoadedVault
import io.github.catlinux.keynexa.sync.RecoveryOutcome
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveFailure
import io.github.catlinux.keynexa.sync.SaveOutcome
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.VaultReader
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.KdbxEngine
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.concurrent.Executor

/** Why a vault could not be opened, in terms the screen can explain. */
enum class UnlockError {
    WrongCredentials,
    InvalidKeyFile,
    NotAVault,
    UnsupportedFormat,

    /** The file is damaged or still being transferred by a sync tool; trying again later may work. */
    Damaged,

    /** The file cannot be read: permission revoked, file moved or provider failure. Offer to reconnect it. */
    Unreachable,
}

sealed interface SessionState {
    /** No vault is open. Credentials are not held. */
    data object Closed : SessionState

    data class Unlocking(
        val vault: KnownVault,
    ) : SessionState

    data class Failed(
        val vault: KnownVault,
        val error: UnlockError,
    ) : SessionState

    /**
     * A vault is open. [current] is what the screens show and edit; it differs from [loaded] while changes are not
     * yet on disk. [saveProblem] is set when the last save did not complete, and [unsavedCopy] is then the private
     * encrypted copy that keeps the user's changes across a restart.
     */
    data class Open(
        val vault: KnownVault,
        val loaded: LoadedVault,
        val current: Vault,
        val saving: Boolean = false,
        val saveProblem: SaveFailure? = null,
        val unsavedCopy: BackupRef? = null,
        /** What recovery found when the vault was opened, if a previous save had been interrupted. */
        val recovered: RecoveryOutcome? = null,
        /** What the Syncthing conflict copies did, when there is something to tell or to offer. */
        val conflicts: ConflictReport? = null,
        /** Epoch millis of the last successful save performed by KeyNexa, or null this session. */
        val lastKeyNexaSaveMillis: Long? = null,
        /** Epoch millis of the last version written elsewhere that KeyNexa loaded, or null this session. */
        val lastExternalChangeMillis: Long? = null,
    ) : SessionState {
        val hasUnsavedChanges: Boolean get() = current != loaded.vault

        /** Nothing is being saved and nothing is waiting to be saved. */
        val isIdle: Boolean get() = !saving && !hasUnsavedChanges
    }
}

/**
 * What happened with the vault's Syncthing conflict copies: how many were folded in, which copies may be deleted,
 * which could not be merged and which are waiting for another password. Only counts, fingerprints, files and
 * reasons: never vault content or secrets.
 *
 * [otherPasswordCopies] holds the copies written with another password (ADR-0007 §5), so the user can be asked for
 * that password; [otherPasswordRejected] says the last attempt did not open them.
 */
data class ConflictReport(
    val mergedNow: Int,
    val deletable: List<ConflictCopy>,
    val failed: List<CopyProblem>,
    val otherPasswordCopies: List<SiblingFile> = emptyList(),
    val otherPasswordRejected: Boolean = false,
) {
    /** True when there is nothing to tell and nothing to offer, so the report is not shown at all. */
    val isEmpty: Boolean
        get() = mergedNow == 0 && deletable.isEmpty() && failed.isEmpty() && otherPasswordCopies.isEmpty()
}

/**
 * The two ways to read a vault file: [patient] waits for files still in transit (used to open a vault); [quick]
 * does not (used to look for outside changes, so it never holds up a save). They may be the same reader.
 */
class VaultReaders(
    val patient: VaultReader,
    val quick: VaultReader = patient,
)

/**
 * The open vault and everything that happens to it: unlocking, editing, saving and locking. One per app.
 *
 * Slow work (key derivation, storage) runs on [executor], which must run tasks one at a time and in order so a save
 * never overlaps another operation on the same vault. State is published in [state]; screens only observe it.
 * Credentials are kept only while a vault is open and are overwritten by [lock].
 *
 * [conflicts] folds in the Syncthing conflict copies of the vault: on open, and whenever a version written
 * elsewhere is loaded. When it is null the whole feature is off and nothing changes.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class VaultSession(
    private val storage: StoragePort,
    private val engine: KdbxEngine,
    private val readers: VaultReaders,
    private val recovery: SaveRecovery,
    private val saver: SafeSaver,
    private val executor: Executor,
    private val conflicts: ConflictMerger? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Closed)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    @Volatile
    private var credentials: Credentials? = null

    /**
     * The open vault's credentials in pre-hashed form, for enabling biometric unlock; the caller wipes them. Null
     * when no vault is open.
     */
    @Suppress("SwallowedException")
    fun hashedCredentials(): Credentials? =
        try {
            credentials?.let { engine.hashedForm(it) }
        } catch (locked: IllegalStateException) {
            // The vault locked (and wiped the credentials) while this was being asked.
            null
        }

    /** Opens [vault]. Takes ownership of [credentials]: they are wiped on failure and when the session locks. */
    fun unlock(
        vault: KnownVault,
        credentials: Credentials,
    ) {
        mutableState.value = SessionState.Unlocking(vault)
        executor.execute {
            val recovered = recovery.recover(vault.location).takeUnless { it == RecoveryOutcome.NothingPending }
            openAfter(vault, credentials, recovered) { }
        }
    }

    /**
     * Writes a brand-new vault file at [vault] (an empty document the user just created) and opens it. Takes
     * ownership of [credentials] like [unlock].
     */
    fun create(
        vault: KnownVault,
        content: Vault,
        credentials: Credentials,
    ) {
        mutableState.value = SessionState.Unlocking(vault)
        executor.execute {
            openAfter(vault, credentials, recovered = null, created = true) {
                val bytes = engine.encode(content, credentials)
                storage.write(vault.location, bytes)
                if (Fingerprint.of(storage.read(vault.location)) != Fingerprint.of(bytes)) {
                    throw IOException("the new vault was not written correctly")
                }
            }
        }
    }

    /**
     * Applies an edit to the open vault and saves it. Returns false, and changes nothing, when no vault is open or
     * a save is running: the screen keeps the user's input and offers it again.
     */
    fun apply(change: (Vault) -> Vault): Boolean {
        var accepted = false
        mutableState.update { state ->
            if (state is SessionState.Open && !state.saving) {
                accepted = true
                state.copy(current = change(state.current), saving = true, saveProblem = null)
            } else {
                state
            }
        }
        if (accepted) executor.execute(::saveNow)
        return accepted
    }

    /** Tries again to save what is not on disk yet, after a failed save. */
    fun retrySave() {
        apply { it }
    }

    /**
     * Looks at the vault file and, if another program changed it, loads that version. Does nothing while there are
     * edits not on disk or a save is running: those are merged by the next save. Meant to be called every few
     * seconds while the app is in front.
     */
    fun refreshIfChanged() {
        executor.execute {
            val open = mutableState.value as? SessionState.Open
            val secrets = credentials
            if (open != null &&
                secrets != null &&
                open.isIdle
            ) {
                reloadIfChanged(readers.quick, open, secrets)
            }
        }
    }

    /**
     * Deletes the conflict copies that were folded in and have not changed since. The offer is refreshed, and it
     * goes away once there is nothing left to delete or to tell.
     */
    fun deleteConflictCopies() {
        executor.execute {
            val merger = conflicts ?: return@execute
            val open = mutableState.value as? SessionState.Open ?: return@execute
            val report = open.conflicts ?: return@execute
            merger.delete(open.vault.location, report.deletable)
            publishReport(
                location = open.vault.location,
                report =
                    report.copy(
                        mergedNow = 0,
                        deletable = merger.deletable(open.vault.location),
                    ),
            )
        }
    }

    /**
     * Folds in the copies written with another password, opening them with [extra]. Takes ownership of [extra] and
     * always wipes it, also when it opens nothing: it is never kept for a retry. Runs on the executor, only while the
     * vault has nothing unsaved, and follows the same rule as [foldConflicts]: what is merged is saved first and the
     * ledger is written only when that save reports `Saved`. Nothing here is logged, and the password is never stored.
     */
    fun mergeOtherPasswordCopies(extra: Credentials) {
        executor.execute {
            try {
                mergeOtherPasswordCopiesNow(extra)
            } finally {
                extra.wipe()
            }
        }
    }

    /** Hides the conflict report until the vault is opened again. */
    fun dismissConflicts() {
        mutableState.update { state ->
            if (state is SessionState.Open) state.copy(conflicts = null) else state
        }
    }

    /** Closes the vault, wipes the credentials and forgets everything in memory. */
    fun lock() {
        executor.execute {
            credentials?.wipe()
            credentials = null
            mutableState.value = SessionState.Closed
        }
    }

    /** Leaves a failed unlock attempt. */
    fun dismissFailure() {
        mutableState.update { if (it is SessionState.Failed) SessionState.Closed else it }
    }

    private fun openAfter(
        vault: KnownVault,
        secrets: Credentials,
        recovered: RecoveryOutcome?,
        created: Boolean = false,
        prepare: () -> Unit,
    ) {
        try {
            prepare()
            val loaded = readers.patient.open(vault.location, secrets)
            credentials = secrets
            mutableState.value =
                SessionState.Open(
                    vault = vault,
                    loaded = loaded,
                    current = loaded.vault,
                    recovered = recovered,
                    lastKeyNexaSaveMillis = if (created) now() else null,
                )
            foldConflicts()
        } catch (failure: VaultException) {
            secrets.wipe()
            mutableState.value = SessionState.Failed(vault, failure.toUnlockError())
        } catch (failure: IOException) {
            secrets.wipe()
            mutableState.value = SessionState.Failed(vault, UnlockError.Unreachable)
        }
    }

    private fun saveNow() {
        val open = mutableState.value as? SessionState.Open ?: return
        val secrets = credentials ?: return
        val outcome = saver.save(open.loaded, open.current, secrets)
        mutableState.update { state ->
            if (state is SessionState.Open) state.after(outcome) else state
        }
    }

    private fun SessionState.Open.after(outcome: SaveOutcome): SessionState.Open =
        when (outcome) {
            is SaveOutcome.Saved ->
                copy(
                    loaded = outcome.loaded,
                    current = if (outcome.merged) outcome.loaded.vault else current,
                    saving = false,
                    saveProblem = null,
                    unsavedCopy = null,
                    lastKeyNexaSaveMillis = this@VaultSession.now(),
                )
            is SaveOutcome.NotSaved ->
                copy(saving = false, saveProblem = outcome.reason, unsavedCopy = outcome.unsavedCopy)
        }

    // Loads the file again if another program changed it, and folds in the conflict copies that came with it. A
    // transfer in progress or a storage hiccup is not an error: the next look will see it again.
    private fun reloadIfChanged(
        reader: VaultReader,
        open: SessionState.Open,
        secrets: Credentials,
    ) {
        try {
            if (!reader.hasChanged(open.vault.location, open.loaded.fingerprint)) return
            val loaded = reader.open(open.vault.location, secrets, credentialsKnownGood = true)
            mutableState.update { state ->
                if (state is SessionState.Open && state.isIdle && state.loaded == open.loaded) {
                    state.copy(
                        loaded = loaded,
                        current = loaded.vault,
                        lastExternalChangeMillis = this@VaultSession.now(),
                    )
                } else {
                    state
                }
            }
            foldConflicts()
        } catch (ignored: IOException) {
            // Try again at the next look.
        } catch (ignored: VaultException) {
            // The file may be incomplete: try again at the next look.
        }
    }

    /**
     * Folds the conflict copies into the open vault. Whatever is folded in is saved first and recorded in the ledger
     * only when that save reports success: a save that does not finish leaves the copies out, so the next open tries
     * again. Runs on the executor, and only when the vault has nothing unsaved. Nothing here is logged, and a
     * storage failure never keeps the vault from opening: the next open tries the copies again.
     */
    private fun foldConflicts() {
        val merger = conflicts ?: return
        val open = mutableState.value as? SessionState.Open ?: return
        val secrets = credentials ?: return
        if (!open.isIdle) return
        try {
            fold(merger, open, secrets)
        } catch (unavailable: IOException) {
            // The vault, its copies or the ledger cannot be read or written right now.
        }
    }

    private fun fold(
        merger: ConflictMerger,
        open: SessionState.Open,
        secrets: Credentials,
    ) {
        val scan = merger.scan(open.vault.location, open.loaded.vault, secrets)
        saveAndReport(merger, open, secrets, scan, otherPasswordRejected = false)
    }

    // The body of [mergeOtherPasswordCopies], outside the wipe so the secrets are never left behind.
    private fun mergeOtherPasswordCopiesNow(extra: Credentials) {
        val merger = conflicts ?: return
        val open = mutableState.value as? SessionState.Open ?: return
        val secrets = credentials ?: return
        val report = open.conflicts ?: return
        if (!open.isIdle) return
        val copies = report.otherPasswordCopies
        if (copies.isEmpty()) return
        val scan =
            try {
                merger.scanWith(open.vault.location, open.loaded.vault, copies, extra)
            } catch (unavailable: IOException) {
                // The vault, its copies or the ledger cannot be read or written right now.
                return
            }
        // A copy still refusing the password says it was wrong; the others may have merged all the same.
        saveAndReport(
            merger = merger,
            open = open,
            secrets = secrets,
            scan = scan,
            otherPasswordRejected = scan.failed.any { it.second == CopyProblem.OtherPassword },
            keptProblems = report.failed.filter { it != CopyProblem.OtherPassword },
        )
    }

    /**
     * Saves what [scan] folded in and publishes the resulting report. The ledger is written only once the save
     * reports `Saved`, so a failed save leaves the copies to be tried again; the rejected flag only colours the
     * report, it never changes what is saved.
     */
    private fun saveAndReport(
        merger: ConflictMerger,
        open: SessionState.Open,
        secrets: Credentials,
        scan: ConflictScan,
        otherPasswordRejected: Boolean,
        keptProblems: List<CopyProblem> = emptyList(),
    ) {
        val location = open.vault.location
        val merged = scan.merged
        if (merged == null) {
            merger.record(location, scan.absorbed)
            publishConflicts(
                location = location,
                mergedNow = 0,
                deletable = merger.deletable(location),
                failed = scan.failed,
                otherPasswordRejected = otherPasswordRejected,
                keptProblems = keptProblems,
            )
            return
        }
        var claimed = false
        mutableState.update { state ->
            claimed = state is SessionState.Open && state.isIdle
            if (state is SessionState.Open && claimed) {
                state.copy(current = merged, saving = true, saveProblem = null)
            } else {
                state
            }
        }
        // The user edited in the meantime: their save merges the copies' changes later, and the ledger stays empty.
        if (!claimed) return
        val outcome = saver.save(open.loaded, merged, secrets)
        mutableState.update { state -> if (state is SessionState.Open) state.after(outcome) else state }
        // Not recorded when the save did not finish, so the copies are not offered for deletion yet: the save-problem
        // notice says what happened, and the next open merges them again.
        val saved = outcome is SaveOutcome.Saved
        if (saved) merger.record(location, scan.absorbed)
        publishConflicts(
            location = location,
            mergedNow = if (saved) scan.absorbed.size else 0,
            deletable = merger.deletable(location),
            failed = scan.failed,
            otherPasswordRejected = otherPasswordRejected,
            keptProblems = keptProblems,
        )
    }

    // Publishes what the copies did to the vault, or clears the report when it has nothing left to say.
    private fun publishConflicts(
        location: VaultLocation,
        mergedNow: Int,
        deletable: List<ConflictCopy>,
        failed: List<Pair<SiblingFile, CopyProblem>>,
        otherPasswordRejected: Boolean = false,
        keptProblems: List<CopyProblem> = emptyList(),
    ) {
        publishReport(
            location = location,
            report =
                ConflictReport(
                    mergedNow = mergedNow,
                    deletable = deletable,
                    failed = failed.map { it.second } + keptProblems,
                    otherPasswordCopies = failed.filter { it.second == CopyProblem.OtherPassword }.map { it.first },
                    otherPasswordRejected = otherPasswordRejected,
                ),
        )
    }

    private fun publishReport(
        location: VaultLocation,
        report: ConflictReport,
    ) {
        val published = report.takeUnless { it.isEmpty }
        mutableState.update { state ->
            if (state is SessionState.Open && state.vault.location == location) {
                state.copy(conflicts = published)
            } else {
                state
            }
        }
    }
}

private fun VaultException.toUnlockError(): UnlockError =
    when (this) {
        is VaultException.WrongCredentials -> UnlockError.WrongCredentials
        is VaultException.InvalidKeyFile -> UnlockError.InvalidKeyFile
        is VaultException.NotAVault -> UnlockError.NotAVault
        is VaultException.UnsupportedFormat -> UnlockError.UnsupportedFormat
        is VaultException.Corrupted -> UnlockError.Damaged
    }
