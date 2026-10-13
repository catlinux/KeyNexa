package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.KdbxEngine
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultLocation
import java.time.Clock

/** Tunables of the save pipeline. */
data class SaveOptions(
    val clock: Clock = Clock.systemUTC(),
    /** How many copies of previous versions to keep per vault. */
    val keepBackups: Int = 10,
    /** How many times to merge and retry when the file keeps changing during a save. */
    val maxRounds: Int = 3,
)

/**
 * The safe-save pipeline of `docs/ARCHITECTURE.md`.
 *
 * A storage provider cannot replace a file atomically, so a crash in the middle of a write can tear it. What this
 * pipeline guarantees instead is that a decryptable copy always exists and is put back automatically: before
 * writing, the previous file and the new bytes are copied to [BackupStore] and the save is recorded in
 * [SaveJournal]; if the file is then found torn, by this save or by [SaveRecovery] after a crash, the previous
 * version is restored and the new one stays available as unsaved changes. A save is reported as
 * [SaveOutcome.Saved] only after the file has been read back and its fingerprint matches the bytes written.
 *
 * Only one key derivation runs per save: the new bytes are decrypted once before being written, and afterwards
 * byte equality (same fingerprint) proves the file decrypts as well. Telling a torn file from one replaced by
 * another program needs no decryption either (see [isTornByOwnWrite]).
 *
 * Calls for the same vault must not run concurrently.
 */
class SafeSaver(
    private val storage: StoragePort,
    private val engine: KdbxEngine,
    private val backups: BackupStore,
    journal: SaveJournal,
    private val merger: VaultMerger? = null,
    private val options: SaveOptions = SaveOptions(),
) {
    private val writer = VerifiedWriter(storage, backups, journal, options.clock, options.keepBackups)

    /** Saves [edited], which the user derived from [loaded]. */
    fun save(
        loaded: LoadedVault,
        edited: Vault,
        credentials: Credentials,
    ): SaveOutcome {
        val encoded =
            encodeVerified(edited, credentials) ?: return notSaved(SaveFailure.EncodeFailed, loaded.location, null)
        var round = Round(loaded, edited, merged = false, encoded)
        repeat(options.maxRounds) {
            when (val step = attempt(round, credentials)) {
                is Step.Finished -> return step.outcome
                is Step.Next -> round = step.round
            }
        }
        return notSaved(SaveFailure.ChangedDuringSave, loaded.location, round.encoded)
    }

    /** One pass: write if the file is still the version the edits started from, otherwise merge and go again. */
    private class Round(
        val base: LoadedVault,
        val candidate: Vault,
        val merged: Boolean,
        val encoded: ByteArray,
    )

    private sealed interface Step {
        class Next(
            val round: Round,
        ) : Step

        class Finished(
            val outcome: SaveOutcome,
        ) : Step
    }

    private fun attempt(
        round: Round,
        credentials: Credentials,
    ): Step {
        val location = round.base.location
        val current =
            storage.readOrNull(location)
                ?: return Step.Finished(notSaved(SaveFailure.StorageError, location, round.encoded))
        val currentFingerprint = Fingerprint.of(current)
        if (currentFingerprint != round.base.fingerprint) return merge(round, current, currentFingerprint, credentials)

        return when (val written = writer.write(location, current, round.encoded)) {
            is WriteResult.Done ->
                Step.Finished(
                    SaveOutcome.Saved(LoadedVault(location, round.candidate, written.fingerprint), round.merged),
                )
            is WriteResult.Failed ->
                Step.Finished(SaveOutcome.NotSaved(written.reason, written.file, written.unsavedCopy))
            WriteResult.FileChanged -> Step.Next(round)
        }
    }

    private fun merge(
        round: Round,
        current: ByteArray,
        currentFingerprint: Fingerprint,
        credentials: Credentials,
    ): Step {
        val location = round.base.location
        return when (val step = mergeWith(current, round.candidate, credentials)) {
            is MergeStep.Stop -> Step.Finished(notSaved(step.reason, location, round.encoded))
            is MergeStep.Continue -> {
                val encoded = encodeVerified(step.merged, credentials)
                if (encoded == null) {
                    Step.Finished(notSaved(SaveFailure.EncodeFailed, location, null))
                } else {
                    Step.Next(Round(LoadedVault(location, step.theirs, currentFingerprint), step.merged, true, encoded))
                }
            }
        }
    }

    private sealed interface MergeStep {
        class Continue(
            val theirs: Vault,
            val merged: Vault,
        ) : MergeStep

        class Stop(
            val reason: SaveFailure,
        ) : MergeStep
    }

    private fun mergeWith(
        current: ByteArray,
        candidate: Vault,
        credentials: Credentials,
    ): MergeStep {
        val activeMerger = merger ?: return MergeStep.Stop(SaveFailure.ExternalChangeNeedsMerge)
        val theirs =
            try {
                engine.decode(current, credentials)
            } catch (wrong: VaultException.WrongCredentials) {
                return MergeStep.Stop(SaveFailure.CredentialsChanged)
            } catch (unreadable: VaultException) {
                return MergeStep.Stop(SaveFailure.FileUnreadable)
            }
        return when (val result = activeMerger.merge(candidate, theirs)) {
            is MergeResult.Merged -> MergeStep.Continue(theirs, result.vault)
            is MergeResult.Unmergeable -> MergeStep.Stop(SaveFailure.Unmergeable)
        }
    }

    // Decrypting the new bytes once before writing proves them good; afterwards byte equality is enough.
    private fun encodeVerified(
        vault: Vault,
        credentials: Credentials,
    ): ByteArray? =
        try {
            engine.encode(vault, credentials).also { engine.decode(it, credentials) }
        } catch (failure: VaultException) {
            null
        }

    private fun notSaved(
        reason: SaveFailure,
        location: VaultLocation,
        encoded: ByteArray?,
    ): SaveOutcome.NotSaved {
        val copy = encoded?.let { backups.putOrNull(location, BackupKind.UnsavedChanges, it, options.clock.instant()) }
        return SaveOutcome.NotSaved(reason, FileState.Untouched, copy)
    }
}
