@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import java.util.UUID

/** What a context menu offers for one node of the tree or of the list. */
internal enum class NodeAction { NewSubgroup, Rename, MoveTo, MoveToBin, Edit, Restore, DeleteForever }

/** The groups from the root down to the one that holds [objectId], the root included; empty when it is not here. */
internal fun holderPath(
    vault: Vault,
    objectId: UUID,
): List<Group> {
    val path = mutableListOf<Group>()

    fun walk(group: Group): Boolean {
        path += group
        if (group.id == objectId || group.entries.any { it.id == objectId }) return true
        for (child in group.groups) if (walk(child)) return true
        path.removeAt(path.lastIndex)
        return false
    }
    return if (walk(vault.root)) path else emptyList()
}

/** The group that holds [objectId], or null when this vault has no such object. */
internal fun holderOf(
    vault: Vault,
    objectId: UUID,
): Group? {
    val path = holderPath(vault, objectId)
    if (path.isEmpty()) return null
    val last = path.last()
    return if (last.id == objectId) path.getOrNull(path.lastIndex - 1) else last
}

/** [group] and every group below it. */
internal fun subtreeIds(group: Group): Set<UUID> = setOf(group.id) + group.groups.flatMap { subtreeIds(it) }

/** True when [objectId] is the recycle bin itself or lives inside it. */
internal fun isInsideBin(
    vault: Vault,
    objectId: UUID,
): Boolean {
    val bin = vault.meta.recycleBinGroup ?: return false
    return holderPath(vault, objectId).any { it.id == bin }
}

/**
 * The actions the context menu offers for the group [groupId]: the contents of the recycle bin can only be restored
 * or erased for good, and the root and the bin themselves offer nothing.
 */
internal fun groupActions(
    vault: Vault,
    groupId: UUID,
): List<NodeAction> {
    if (isInsideBin(vault, groupId)) {
        if (groupId == vault.meta.recycleBinGroup) return emptyList()
        return listOf(NodeAction.Restore, NodeAction.DeleteForever)
    }
    if (groupId == vault.root.id) return emptyList()
    return listOf(NodeAction.NewSubgroup, NodeAction.Rename, NodeAction.MoveToBin)
}

/** The actions the context menu offers for the entry [entryId]. */
internal fun entryActions(
    vault: Vault,
    entryId: UUID,
): List<NodeAction> =
    if (isInsideBin(vault, entryId)) {
        listOf(NodeAction.Restore, NodeAction.DeleteForever)
    } else {
        listOf(NodeAction.Edit, NodeAction.MoveTo, NodeAction.MoveToBin)
    }

/**
 * The groups [objectId] may be moved to, in tree order: every group but the recycle bin, the one that already holds
 * it and, for a group, itself and its own subtree, which cannot hold it.
 */
internal fun moveDestinations(
    vault: Vault,
    objectId: UUID,
): List<Group> {
    val bin = vault.meta.recycleBinGroup
    val holder = holderOf(vault, objectId)
    val ownSubtree =
        vault.root
            .findGroup(objectId)
            ?.let { subtreeIds(it) }
            .orEmpty()
    val allowed = mutableListOf<Group>()

    fun walk(
        group: Group,
        insideBin: Boolean,
    ) {
        val nowInside = insideBin || group.id == bin
        if (!nowInside && group.id != holder?.id && group.id !in ownSubtree) allowed += group
        group.groups.forEach { walk(it, nowInside) }
    }
    walk(vault.root, false)
    return allowed
}

/** The entry to select once [removed] is gone from [rows]: the one that took its place, or the last one, or none. */
internal fun selectionAfterRemoval(
    rows: List<UUID>,
    removed: UUID,
): UUID? {
    val index = rows.indexOf(removed)
    if (index < 0) return null
    val remaining = rows.filterNot { it == removed }
    if (remaining.isEmpty()) return null
    return remaining.getOrNull(index) ?: remaining.last()
}
