package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Vault

/** Result of merging the user's version with a version changed elsewhere. */
sealed interface MergeResult {
    data class Merged(
        val vault: Vault,
    ) : MergeResult

    /** The versions cannot be combined safely (for example, different master keys). Nothing must be written. */
    data class Unmergeable(
        val reason: String,
    ) : MergeResult
}

/**
 * Combines [ours] (what the user edited) with [theirs] (the file as it is now on storage). Implemented in
 * phase D2 following the KeePass synchronisation semantics described in `docs/ARCHITECTURE.md`.
 */
fun interface VaultMerger {
    fun merge(
        ours: Vault,
        theirs: Vault,
    ): MergeResult
}
