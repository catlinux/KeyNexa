package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.DeletedObject
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import java.time.Instant

/**
 * Two-way merge with the semantics of KeePass 2.x synchronisation, re-implemented from its documented behaviour
 * (`docs/ARCHITECTURE.md`, section Merge):
 *
 * - Groups and entries are matched by UUID anywhere in the tree.
 * - When both sides have an object, the one with the newer last-modification time wins; for entries, the losing
 *   version joins the history, histories are united without duplicates and trimmed to the vault's limits.
 * - An object's place in the tree follows the side with the newer location-changed time; if that group no longer
 *   exists, the object goes to the nearest surviving ancestor, or to the root.
 * - Deletions apply only through the deleted-objects lists, and only to objects not modified after the deletion;
 *   a deleted object modified later comes back and its deletion record is dropped.
 * - Metadata merges field by field using the vault's `*Changed` times.
 *
 * Ties favour `ours` (the user's in-memory version). The result keeps `ours`' file format. Editing code must set an
 * object's last-modification time (and location-changed time on moves), or its changes may lose a tie.
 */
class KeePassMerger : VaultMerger {
    override fun merge(
        ours: Vault,
        theirs: Vault,
    ): MergeResult {
        if (ours.root.id != theirs.root.id) return MergeResult.Unmergeable("The two files are different vaults.")

        val sides = Sides(TreeIndex(ours.root), TreeIndex(theirs.root))
        val deletions = mergeDeletions(ours.deletedObjects, theirs.deletedObjects)
        val alive = AliveSet(sides, deletions)
        val meta = mergeMeta(ours.meta, theirs.meta)
        val history = HistoryLimits(meta.historyMaxItems, meta.historyMaxSizeBytes)

        val root =
            TreeBuilder(
                sides = sides,
                rootId = ours.root.id,
                groups = mergeGroups(sides, alive.groups),
                entries = mergeEntries(sides, alive.entries, history),
            ).build()

        val referenced = referencedBinaries(root)
        return MergeResult.Merged(
            ours.copy(
                meta = meta,
                root = root,
                deletedObjects = deletions.filter { it.id !in alive.resurrected },
                binaries = (theirs.binaries + ours.binaries).filterKeys { it in referenced },
                unknownElements = ours.unknownElements.ifEmpty { theirs.unknownElements },
                unknownRootElements = ours.unknownRootElements.ifEmpty { theirs.unknownRootElements },
            ),
        )
    }
}

private fun mergeDeletions(
    ours: List<DeletedObject>,
    theirs: List<DeletedObject>,
): List<DeletedObject> =
    (ours + theirs)
        .groupBy { it.id }
        .map { (_, records) -> records.maxBy { it.deletedAt ?: Instant.MIN } }

private fun referencedBinaries(root: Group): Set<BinaryId> {
    val ids = HashSet<BinaryId>()

    fun visit(group: Group) {
        for (entry in group.entries) {
            (listOf(entry) + entry.history).forEach { version -> version.attachments.forEach { ids += it.id } }
        }
        group.groups.forEach(::visit)
    }
    visit(root)
    return ids
}
