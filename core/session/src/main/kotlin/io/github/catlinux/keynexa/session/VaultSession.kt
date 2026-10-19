package io.github.catlinux.keynexa.session

import io.github.catlinux.keynexa.sync.Absorbed
import io.github.catlinux.keynexa.sync.BackupKind
import io.github.catlinux.keynexa.sync.BackupRef
import io.github.catlinux.keynexa.sync.BackupStore
import io.github.catlinux.keynexa.sync.ConflictCopy
import io.github.catlinux.keynexa.sync.ConflictMerger
import io.github.catlinux.keynexa.sync.ConflictScan
import io.github.catlinux.keynexa.sync.CopyProblem
import io.github.catlinux.keynexa.sync.Expect
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.LoadedVault
import io.github.catlinux.keynexa.sync.MergeResult
import io.github.catlinux.keynexa.sync.RecoveryOutcome
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveFailure
import io.github.catlinux.keynexa.sync.SaveOutcome
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.SyncFile
import io.github.catlinux.keynexa.sync.SyncIds
import io.github.catlinux.keynexa.sync.VaultMerger
import io.github.catlinux.keynexa.sync.VaultReader
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.CustomDataValue
import io.github.catlinux.keynexa.vault.FileMetadata
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
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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

    /** The key derivation asks for more memory than the device can give. */
    NotEnoughMemory,
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
 *
 * [absorbRemote] and [assignSyncId] hand work to [executor] and wait for it at most [executorWaitMillis]; a caller
 * already running on [executor] never waits (that would deadlock) and is answered as unavailable instead.
 */
@Suppress("LargeClass", "LongParameterList", "TooManyFunctions")
class VaultSession(
    private val storage: StoragePort,
    private val engine: KdbxEngine,
    private val readers: VaultReaders,
    private val recovery: SaveRecovery,
    private val saver: SafeSaver,
    private val backups: BackupStore,
    private val vaultMerger: VaultMerger,
    private val executor: Executor,
    private val conflicts: ConflictMerger? = null,
    private val afterSave: (KnownVault) -> Unit = {},
    private val now: () -> Long = { System.currentTimeMillis() },
    private val executorWaitMillis: Long = DEFAULT_EXECUTOR_WAIT_MILLIS,
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Closed)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    @Volatile
    private var credentials: Credentials? = null

    /** Whether the current thread is inside a task running on [executor]; waiting for the executor there deadlocks. */
    private val onExecutor = ThreadLocal.withInitial { false }

    /** When the last full read of the vault file happened, so a full check runs at least once a minute. */
    private var lastFullCheckMillis = 0L

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
        submit {
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
        submit {
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
        if (accepted) submit(::saveNow)
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
        submit {
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
        submit {
            val merger = conflicts ?: return@submit
            val open = mutableState.value as? SessionState.Open ?: return@submit
            val report = open.conflicts ?: return@submit
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
        submit {
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
        submit {
            credentials?.wipe()
            credentials = null
            mutableState.value = SessionState.Closed
        }
    }

    /** Leaves a failed unlock attempt. */
    fun dismissFailure() {
        mutableState.update { if (it is SessionState.Failed) SessionState.Closed else it }
    }

    /**
     * Reconnects the open vault to [newLocation] (a newly chosen file or folder entry) without closing it: it keeps
     * the edited and loaded vaults, the credentials and the private unsaved copy, updates the location in the open
     * session, and retries the pending save through the normal safe-save pipeline.
     */
    fun relocate(newLocation: VaultLocation) {
        submit { relocateNow(newLocation) }
    }

    /**
     * Restores the unsaved copy recorded for the previous interrupted save (`recovered`), merging it into the open
     * vault and saving through the normal pipeline. The copy is deleted only when the save succeeds.
     */
    fun restoreUnsaved() {
        submit { restoreUnsavedNow() }
    }

    /** Discards the unsaved copy recorded for the previous interrupted save and clears the recovery notice. */
    fun discardUnsaved() {
        submit { discardRecoveredNow() }
    }

    /**
     * Merges a remote copy of this vault into the open one and saves the result if the merge changes anything
     * (ADR-0009). The work runs on [executor] and the caller waits for it, at most [executorWaitMillis].
     *
     * A caller that is itself running on [executor] is answered [Absorbed.Postponed] without waiting: waiting there
     * for the executor's own thread would deadlock, and the next trigger will try again.
     */
    fun absorbRemote(
        bytes: ByteArray,
        expect: Expect,
    ): Absorbed = askExecutor(Absorbed.Postponed) { absorbRemoteNow(bytes, expect) }

    /**
     * Stores [id] in the open vault's custom data (`KeyNexa.SyncId`) and saves it (ADR-0009 §5), answering whether
     * the save finished. Runs on [executor] like [absorbRemote], with the same deadline and the same rule for a
     * caller on [executor], which is answered false.
     */
    fun assignSyncId(id: String): Boolean = askExecutor(false) { assignSyncIdNow(id) }

    /**
     * The synchronisation id in the open vault's custom data, or null when no vault is open, it has none, or the
     * value is malformed (a malformed id is never synchronised).
     */
    fun syncId(): String? {
        val open = mutableState.value as? SessionState.Open ?: return null
        return SyncIds.readCustomData(customDataOf(open.current))
    }

    private fun relocateNow(newLocation: VaultLocation) {
        val open = mutableState.value as? SessionState.Open ?: return
        val secrets = credentials ?: return
        var updated: SessionState.Open? = null
        mutableState.update { state ->
            if (state is SessionState.Open && state.vault.location == open.vault.location) {
                val next =
                    state.copy(
                        vault = state.vault.copy(location = newLocation),
                        // The metadata belonged to the old file: the next look starts with a full read.
                        loaded = state.loaded.copy(location = newLocation, metadata = null),
                        saving = true,
                        saveProblem = null,
                        conflicts = null,
                    )
                updated = next
                next
            } else {
                state
            }
        }
        val target = updated ?: return
        val outcome = saver.save(target.loaded, target.current, secrets)
        mutableState.update { state ->
            if (state is SessionState.Open && state.vault.location == newLocation) {
                state.after(outcome)
            } else {
                state
            }
        }
        dropEarlierUnsavedCopy(outcome, target.unsavedCopy)
        notifySaved(outcome, target.vault)
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun restoreUnsavedNow() {
        val open = mutableState.value as? SessionState.Open ?: return
        val recovered = open.recovered
        val ref = unsavedCopyOf(recovered) ?: open.unsavedCopy ?: return
        val secrets = credentials ?: return
        val bytes =
            try {
                backups.read(ref)
            } catch (failure: IOException) {
                return
            }
        val restoredVault =
            try {
                engine.decode(bytes, secrets)
            } catch (_: Exception) {
                return
            }
        val merged =
            when (val result = vaultMerger.merge(open.current, restoredVault)) {
                is MergeResult.Merged -> result.vault
                is MergeResult.Unmergeable -> return
            }
        var target: SessionState.Open? = null
        mutableState.update { state ->
            if (state is SessionState.Open && state.isIdle) {
                val next = state.copy(current = merged, saving = true, saveProblem = null)
                target = next
                next
            } else {
                state
            }
        }
        val toSave = target ?: return
        val outcome = saver.save(toSave.loaded, merged, secrets)
        mutableState.update { state ->
            if (state is SessionState.Open) {
                val after = state.after(outcome)
                if (outcome is SaveOutcome.Saved) {
                    runCatching { backups.delete(ref) }
                    after.copy(recovered = null)
                } else {
                    after
                }
            } else {
                state
            }
        }
        notifySaved(outcome, toSave.vault)
    }

    private fun discardRecoveredNow() {
        val open = mutableState.value as? SessionState.Open ?: return
        val ref = unsavedCopyOf(open.recovered) ?: open.unsavedCopy
        if (ref != null) runCatching { backups.delete(ref) }
        mutableState.update { state ->
            if (state is SessionState.Open) {
                state.copy(
                    recovered = null,
                    unsavedCopy = if (ref != null && ref == state.unsavedCopy) null else state.unsavedCopy,
                )
            } else {
                state
            }
        }
    }

    /**
     * The body of [absorbRemote], on the executor. The remote bytes are decrypted with the open vault's credentials
     * and merged into it; the merged vault is saved only when the merge changed it, and [Absorbed.Merged] then says
     * whether the remote still lacks part of the result, which is what has to be uploaded (ADR-0009 §2 and §3).
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun absorbRemoteNow(
        bytes: ByteArray,
        expect: Expect,
    ): Absorbed {
        val open = mutableState.value as? SessionState.Open ?: return Absorbed.Postponed
        val secrets = credentials ?: return Absorbed.Postponed
        if (!open.isIdle) return Absorbed.Postponed
        val remote =
            try {
                engine.decode(bytes, secrets)
            } catch (_: VaultException.WrongCredentials) {
                return Absorbed.OtherPassword
            } catch (_: VaultException) {
                return Absorbed.Unreadable
            }
        if (!sameVault(open.current, remote, expect)) return Absorbed.NotSameVault
        val merged =
            when (val result = vaultMerger.merge(open.current, remote)) {
                is MergeResult.Merged -> result.vault
                is MergeResult.Unmergeable -> return Absorbed.Unreadable
            }
        if (merged == open.current) {
            val file = readLocalFile(open.vault.location) ?: return Absorbed.SaveFailed
            return Absorbed.Merged(file, remoteLacksLocal = merged != remote)
        }
        val (savedState, outcome) = claimAndSave(open.current, merged, secrets) ?: return Absorbed.Postponed
        if (outcome !is SaveOutcome.Saved) return Absorbed.SaveFailed
        val file = readLocalFile(savedState.vault.location) ?: return Absorbed.SaveFailed
        // The open vault becomes what the file now holds (it keeps whole seconds), otherwise the next absorb would see
        // a difference in the time resolution alone and rewrite the file again. Reuses the notice the apps already
        // show for a version written elsewhere.
        val written = decodeQuietly(file.bytes, secrets)
        mutableState.update { state ->
            if (state is SessionState.Open && state.isIdle && state.current == merged) {
                if (written == null) {
                    state.copy(lastExternalChangeMillis = now())
                } else {
                    state.copy(
                        current = written,
                        loaded = state.loaded.copy(vault = written),
                        lastExternalChangeMillis = now(),
                    )
                }
            } else if (state is SessionState.Open) {
                state.copy(lastExternalChangeMillis = now())
            } else {
                state
            }
        }
        return Absorbed.Merged(file, remoteLacksLocal = (written ?: outcome.loaded.vault) != remote)
    }

    /**
     * The body of [assignSyncId], on the executor: it writes the id into the open vault's custom data and saves it
     * through the normal pipeline. False when the vault is closed, busy or the save does not finish.
     */
    private fun assignSyncIdNow(id: String): Boolean {
        val open = mutableState.value as? SessionState.Open ?: return false
        val secrets = credentials ?: return false
        if (!open.isIdle) return false
        if (SyncIds.readCustomData(customDataOf(open.current)) == id) return true
        val updated =
            open.current.copy(
                meta =
                    open.current.meta.copy(
                        customData =
                            open.current.meta.customData +
                                (SyncIds.KEY to CustomDataValue(id, Instant.ofEpochMilli(now()))),
                    ),
            )
        val (_, outcome) = claimAndSave(open.current, updated, secrets) ?: return false
        return outcome is SaveOutcome.Saved
    }

    /** Whether the remote vault is the one [expect] describes, so it may be merged into the open one. */
    private fun sameVault(
        current: Vault,
        remote: Vault,
        expect: Expect,
    ): Boolean =
        when (expect) {
            is Expect.SyncId -> SyncIds.readCustomData(customDataOf(remote)) == expect.id
            Expect.SameRoot -> remote.root.id == current.root.id
        }

    // Claims the vault for a save like saveAndReport does, then saves; null when the vault stopped being idle or an
    // edit landed since [base] was read, so a merge computed from stale content never overwrites it.
    private fun claimAndSave(
        base: Vault,
        merged: Vault,
        secrets: Credentials,
    ): Pair<SessionState.Open, SaveOutcome>? {
        var target: SessionState.Open? = null
        mutableState.update { state ->
            if (state is SessionState.Open && state.isIdle && state.current == base) {
                val next = state.copy(current = merged, saving = true, saveProblem = null)
                target = next
                next
            } else {
                state
            }
        }
        val toSave = target ?: return null
        val outcome = saver.save(toSave.loaded, merged, secrets)
        mutableState.update { state -> if (state is SessionState.Open) state.after(outcome) else state }
        notifySaved(outcome, toSave.vault)
        return toSave to outcome
    }

    private fun decodeQuietly(
        bytes: ByteArray,
        secrets: Credentials,
    ): Vault? =
        try {
            engine.decode(bytes, secrets)
        } catch (_: VaultException) {
            null
        }

    private fun readLocalFile(location: VaultLocation): SyncFile? =
        try {
            SyncFile(storage.read(location))
        } catch (_: IOException) {
            null
        }

    private fun customDataOf(vault: Vault): Map<String, String> = vault.meta.customData.mapValues { it.value.value }

    private fun unsavedCopyOf(recovered: RecoveryOutcome?): BackupRef? =
        when (recovered) {
            is RecoveryOutcome.SaveHadNotStarted -> recovered.unsavedCopy
            is RecoveryOutcome.ChangedElsewhere -> recovered.unsavedCopy
            is RecoveryOutcome.Restored -> recovered.unsavedCopy
            is RecoveryOutcome.UnsavedCopyKept -> recovered.unsavedCopy
            else -> null
        }

    /**
     * The newest private copy of changes that an earlier save could not write and that would still add something to
     * [current]. On the way, copies that add nothing are deleted; copies that do not open with [secrets] or cannot be
     * merged are left untouched, never offered and never deleted. Each copy costs a key derivation, but copies only
     * exist after a failed save and go away once restored, discarded or found to add nothing.
     */
    private fun leftoverUnsavedCopy(
        location: VaultLocation,
        current: Vault,
        secrets: Credentials,
    ): BackupRef? {
        val copies =
            try {
                backups.list(location).filter { it.kind == BackupKind.UnsavedChanges }
            } catch (_: IOException) {
                return null
            }
        for (ref in copies) {
            when (addsSomething(ref, current, secrets)) {
                true -> return ref
                false -> runCatching { backups.delete(ref) }
                null -> Unit
            }
        }
        return null
    }

    /** Whether the copy [ref] adds something to [current]; null when that cannot be told (unreadable, unmergeable). */
    private fun addsSomething(
        ref: BackupRef,
        current: Vault,
        secrets: Credentials,
    ): Boolean? {
        val copy =
            try {
                engine.decode(backups.read(ref), secrets)
            } catch (_: IOException) {
                return null
            } catch (_: VaultException) {
                return null
            }
        return when (val result = vaultMerger.merge(current, copy)) {
            is MergeResult.Merged -> result.vault != current
            is MergeResult.Unmergeable -> null
        }
    }

    // A successful save holds everything the copy of an earlier failed attempt held, so that copy can go.
    private fun dropEarlierUnsavedCopy(
        outcome: SaveOutcome,
        earlier: BackupRef?,
    ) {
        if (outcome is SaveOutcome.Saved && earlier != null) runCatching { backups.delete(earlier) }
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
            lastFullCheckMillis = now()
            val offered =
                if (created || unsavedCopyOf(recovered) != null) {
                    recovered
                } else {
                    leftoverUnsavedCopy(vault.location, loaded.vault, secrets)
                        ?.let { RecoveryOutcome.UnsavedCopyKept(it) } ?: recovered
                }
            mutableState.value =
                SessionState.Open(
                    vault = vault,
                    loaded = loaded,
                    current = loaded.vault,
                    recovered = offered,
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
        dropEarlierUnsavedCopy(outcome, open.unsavedCopy)
        notifySaved(outcome, open.vault)
    }

    // Tells the after-save hook about a completed save, and never lets it break the save itself.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun notifySaved(
        outcome: SaveOutcome,
        vault: KnownVault,
    ) {
        if (outcome !is SaveOutcome.Saved) return
        try {
            afterSave(vault)
        } catch (failure: Throwable) {
            // The hook is best effort: a failure there must never reach the save.
        }
    }

    // Runs a task on the executor, marking the thread so a task can tell it must not wait for the executor itself.
    private fun submit(task: () -> Unit) {
        executor.execute {
            onExecutor.set(true)
            try {
                task()
            } finally {
                onExecutor.remove()
            }
        }
    }

    /**
     * Runs [task] on [executor] and waits for its answer, at most [executorWaitMillis]. [ifUnavailable] is answered
     * when the call comes from the executor's own thread (waiting there would deadlock), when the wait times out, or
     * when the task fails unexpectedly; nothing is thrown at the caller and the next trigger can try again.
     */
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun <T : Any> askExecutor(
        ifUnavailable: T,
        task: () -> T,
    ): T {
        if (onExecutor.get()) return ifUnavailable
        val answer = CompletableFuture<T>()
        submit {
            answer.complete(
                try {
                    task()
                } catch (_: Exception) {
                    ifUnavailable
                },
            )
        }
        return try {
            answer.get(executorWaitMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            ifUnavailable
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            ifUnavailable
        } catch (_: ExecutionException) {
            ifUnavailable
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

    // After a save or a reconnect the loaded vault has no metadata; once a full look finds the file unchanged, the
    // metadata taken before that look is kept so the next looks can be cheap again.
    private fun rememberMetadata(
        open: SessionState.Open,
        metadata: FileMetadata?,
    ) {
        if (metadata == null) return
        mutableState.update { state ->
            if (state is SessionState.Open && state.loaded == open.loaded) {
                state.copy(loaded = state.loaded.copy(metadata = metadata))
            } else {
                state
            }
        }
    }

    // Loads the file again if another program changed it, and folds in the conflict copies that came with it. A
    // transfer in progress or a storage hiccup is not an error: the next look will see it again.
    private fun reloadIfChanged(
        reader: VaultReader,
        open: SessionState.Open,
        secrets: Credentials,
    ) {
        try {
            // The cheap comparison (size and time) is enough most times; a full read runs at least once a minute,
            // so a file system with an inexact date cannot hide a change.
            val forceFull = now() - lastFullCheckMillis >= FULL_CHECK_INTERVAL_MILLIS
            // Taken before any read, so it can only be older than the content it will describe.
            val metadataBeforeLook =
                if (open.loaded.metadata == null) reader.readMetadata(open.vault.location) else null
            val changed =
                reader.hasChanged(
                    location = open.vault.location,
                    known = open.loaded.fingerprint,
                    knownMetadata = open.loaded.metadata,
                    forceFull = forceFull,
                )
            if (forceFull || changed) lastFullCheckMillis = now()
            if (!changed) {
                rememberMetadata(open, metadataBeforeLook)
                return
            }
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
        notifySaved(outcome, open.vault)
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

/** How often the cheap metadata comparison is backed by a full read of the vault file. */
private const val FULL_CHECK_INTERVAL_MILLIS = 60_000L

/** How long the calls that hand work to the session's executor wait for their answer (ADR-0009). */
private const val DEFAULT_EXECUTOR_WAIT_MILLIS = 2L * 60L * 1_000L

private fun VaultException.toUnlockError(): UnlockError =
    when (this) {
        is VaultException.WrongCredentials -> UnlockError.WrongCredentials
        is VaultException.InvalidKeyFile -> UnlockError.InvalidKeyFile
        is VaultException.NotAVault -> UnlockError.NotAVault
        is VaultException.UnsupportedFormat -> UnlockError.UnsupportedFormat
        is VaultException.Corrupted -> UnlockError.Damaged
        is VaultException.NotEnoughMemory -> UnlockError.NotEnoughMemory
    }
