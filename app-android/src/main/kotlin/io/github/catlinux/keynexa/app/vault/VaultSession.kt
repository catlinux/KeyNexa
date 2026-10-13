package io.github.catlinux.keynexa.app.vault

import io.github.catlinux.keynexa.sync.BackupRef
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
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
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
    ) : SessionState {
        val hasUnsavedChanges: Boolean get() = current != loaded.vault
    }
}

/**
 * The open vault and everything that happens to it: unlocking, editing, saving and locking. One per app.
 *
 * Slow work (key derivation, storage) runs on [executor], which must run tasks one at a time and in order so a save
 * never overlaps another operation on the same vault. State is published in [state]; screens only observe it.
 * Credentials are kept only while a vault is open and are overwritten by [lock].
 */
class VaultSession(
    private val storage: StoragePort,
    private val engine: KdbxEngine,
    private val reader: VaultReader,
    private val recovery: SaveRecovery,
    private val saver: SafeSaver,
    private val executor: Executor,
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Closed)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private var credentials: Credentials? = null

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
            openAfter(vault, credentials, recovered = null) {
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
    fun apply(change: (Vault) -> Vault): Boolean = startSave(change)

    /** Tries again to save what is not on disk yet, after a failed save. */
    fun retrySave() {
        startSave { it }
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
        prepare: () -> Unit,
    ) {
        try {
            prepare()
            val loaded = reader.open(vault.location, secrets)
            credentials = secrets
            mutableState.value = SessionState.Open(vault, loaded, loaded.vault, recovered = recovered)
        } catch (failure: VaultException) {
            secrets.wipe()
            mutableState.value = SessionState.Failed(vault, failure.toUnlockError())
        } catch (failure: IOException) {
            secrets.wipe()
            mutableState.value = SessionState.Failed(vault, UnlockError.Unreachable)
        }
    }

    private fun startSave(change: (Vault) -> Vault): Boolean {
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
                )
            is SaveOutcome.NotSaved ->
                copy(saving = false, saveProblem = outcome.reason, unsavedCopy = outcome.unsavedCopy)
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
