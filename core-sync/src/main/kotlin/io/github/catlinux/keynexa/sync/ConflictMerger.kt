package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.KdbxEngine
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException

/**
 * The conflict copies already folded into each vault, by fingerprint (ADR-0007 §2).
 *
 * A copy whose fingerprint is here is not opened again: not paying a key derivation for a copy that was already
 * merged is the point of keeping the ledger. Only fingerprints are stored, never vault content or secrets.
 */
interface MergedCopies {
    fun isMerged(
        vault: VaultLocation,
        copy: Fingerprint,
    ): Boolean

    fun record(
        vault: VaultLocation,
        copies: Collection<Fingerprint>,
    )
}

/** A conflict copy found next to the vault: the file and the fingerprint of the bytes it holds right now. */
data class ConflictCopy(
    val file: SiblingFile,
    val fingerprint: Fingerprint,
)

/** Why a conflict copy could not be merged; all of them leave the copy on storage. */
enum class CopyProblem {
    /** Written with another password, changed on one device only. The app asks for that password to merge it. */
    OtherPassword,

    /** Damaged or still being transferred; trying again on the next open may work. */
    Damaged,

    /** It belongs to another vault, so nothing of it belongs here. */
    OtherVault,

    /** It could not be read at all. */
    Unreadable,
}

/**
 * What one scan found. [merged] is the vault with every copy that added something folded in, or null when no copy
 * added anything (the caller then has nothing to save). [absorbed] lists the copies that were folded in, including
 * the ones that added nothing, so they can be recorded and later offered for deletion. [failed] lists the copies that
 * could not be merged, each with its reason, and those are never deleted.
 */
data class ConflictScan(
    val merged: Vault?,
    val absorbed: List<ConflictCopy>,
    val failed: List<Pair<SiblingFile, CopyProblem>>,
)

/**
 * Folds the Syncthing conflict copies of a vault into it, following ADR-0007.
 *
 * A scan never fails because of a copy: a copy that cannot be read or decoded is reported in
 * [ConflictScan.failed] and left untouched, so the vault can be opened anyway. Copies whose fingerprint is already
 * in [ledger] are skipped without decrypting them, and the copies that were folded in are returned so that the
 * caller records them only once the result has been saved.
 *
 * Nothing is written or deleted here: [delete] is the only method that removes anything, and it does so only for
 * copies that the caller asked for and whose bytes are still the ones that were recorded.
 */
class ConflictMerger(
    private val storage: StoragePort,
    private val engine: KdbxEngine,
    private val merger: VaultMerger,
    private val ledger: MergedCopies,
) {
    /**
     * Folds the conflict copies of [location] into [current]. The result is not saved: the caller saves it and, only
     * once that succeeds, records [ConflictScan.absorbed].
     */
    fun scan(
        location: VaultLocation,
        current: Vault,
        credentials: Credentials,
    ): ConflictScan = absorbAll(location, current, copiesOf(location), credentials)

    /**
     * Folds in only [files], opened with [credentials], instead of every copy next to the vault. Used to retry the
     * copies written with another password once the user types it; the ledger works the same, so a copy already
     * merged is still skipped.
     */
    fun scanWith(
        location: VaultLocation,
        current: Vault,
        files: List<SiblingFile>,
        credentials: Credentials,
    ): ConflictScan = absorbAll(location, current, files, credentials)

    // The one scan both public methods share: absorb each file in turn and collect what happened.
    private fun absorbAll(
        location: VaultLocation,
        current: Vault,
        files: List<SiblingFile>,
        credentials: Credentials,
    ): ConflictScan {
        val absorbed = mutableListOf<ConflictCopy>()
        val failed = mutableListOf<Pair<SiblingFile, CopyProblem>>()
        var folded = current
        for (file in files) {
            when (val step = absorb(location, folded, file, credentials)) {
                is Absorb.Merged -> {
                    folded = step.vault
                    absorbed += step.copy
                }
                is Absorb.Problem -> failed += step.file to step.problem
                Absorb.Skipped -> Unit
            }
        }
        return ConflictScan(merged = folded.takeUnless { it == current }, absorbed = absorbed, failed = failed)
    }

    /** Remembers the copies that were folded in, so the next scan does not open them again. */
    fun record(
        location: VaultLocation,
        absorbed: List<ConflictCopy>,
    ) {
        ledger.record(location, absorbed.map { it.fingerprint })
    }

    /** The copies still on storage whose bytes are exactly the ones recorded as merged: the ones safe to delete. */
    fun deletable(location: VaultLocation): List<ConflictCopy> =
        copiesOf(location).mapNotNull { file ->
            storage.readOrNull(file.location)?.let { bytes ->
                val fingerprint = Fingerprint.of(bytes)
                ConflictCopy(file, fingerprint).takeIf { ledger.isMerged(location, fingerprint) }
            }
        }

    /**
     * Deletes copies that are still next to the vault and still hold the recorded bytes, reading each one again just
     * before: a copy that changed since the scan is kept. An [IOException] on one copy does not stop the others, and
     * the result says how many went.
     */
    fun delete(
        location: VaultLocation,
        copies: List<ConflictCopy>,
    ): Int {
        val present = copiesOf(location).map { it.location }.toSet()
        return copies.count { it.file.location in present && deleteIfUnchanged(it) }
    }

    // Reads the copy again and deletes it only when it still holds exactly the bytes that were recorded.
    private fun deleteIfUnchanged(copy: ConflictCopy): Boolean {
        val bytes = storage.readOrNull(copy.file.location) ?: return false
        if (Fingerprint.of(bytes) != copy.fingerprint) return false
        return try {
            storage.delete(copy.file.location)
            true
        } catch (failure: IOException) {
            false
        }
    }

    // One copy: skipped when it is already known, or decoded with the open vault's credentials and folded in.
    private fun absorb(
        location: VaultLocation,
        current: Vault,
        file: SiblingFile,
        credentials: Credentials,
    ): Absorb {
        val bytes = storage.readOrNull(file.location) ?: return Absorb.Problem(file, CopyProblem.Unreadable)
        val fingerprint = Fingerprint.of(bytes)
        if (ledger.isMerged(location, fingerprint)) return Absorb.Skipped
        return try {
            // The header is unencrypted: a hostile or broken copy could ask for hours of key derivation on the one
            // thread that also saves. A copy of this vault costs about what the vault costs, so far more is refused.
            if (!affordable(engine.inspect(bytes).kdf, current.format.kdf)) {
                return Absorb.Problem(file, CopyProblem.Damaged)
            }
            when (val result = merger.merge(current, engine.decode(bytes, credentials))) {
                is MergeResult.Merged -> Absorb.Merged(result.vault, ConflictCopy(file, fingerprint))
                is MergeResult.Unmergeable -> Absorb.Problem(file, CopyProblem.OtherVault)
            }
        } catch (wrong: VaultException.WrongCredentials) {
            Absorb.Problem(file, CopyProblem.OtherPassword)
        } catch (damaged: VaultException) {
            Absorb.Problem(file, CopyProblem.Damaged)
        } catch (unreadable: IOException) {
            Absorb.Problem(file, CopyProblem.Unreadable)
        }
    }

    private fun copiesOf(location: VaultLocation): List<SiblingFile> =
        conflictCopiesOf(storage.name(location), storage.listSiblings(location))
}

/** How many times the open vault's key derivation cost a conflict copy may ask for; above it, it is not opened. */
private const val MAX_COPY_COST_FACTOR = 16.0

/**
 * Whether a copy's key derivation is within reach of the open vault's: Argon2 work grows with memory times
 * iterations, AES-KDF work with rounds. Different kinds are left to the engine's absolute limits.
 */
private fun affordable(
    copy: Kdf,
    open: Kdf,
): Boolean =
    when {
        copy is Kdf.Argon2 && open is Kdf.Argon2 ->
            copy.memoryBytes.toDouble() * copy.iterations <=
                open.memoryBytes.toDouble() * open.iterations * MAX_COPY_COST_FACTOR
        copy is Kdf.AesKdf && open is Kdf.AesKdf -> copy.rounds <= open.rounds * MAX_COPY_COST_FACTOR
        else -> true
    }

/** What one copy did during a scan. */
private sealed interface Absorb {
    class Merged(
        val vault: Vault,
        val copy: ConflictCopy,
    ) : Absorb

    class Problem(
        val file: SiblingFile,
        val problem: CopyProblem,
    ) : Absorb

    /** The copy was already merged once, so it was not even decrypted. */
    data object Skipped : Absorb
}
