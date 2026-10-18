package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.DeletedObject
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Times
import java.time.Instant
import java.util.UUID

/** Where every group and entry of one tree sits, with the order of children inside each group. */
internal class TreeIndex(
    root: Group,
) {
    val groups = LinkedHashMap<UUID, Group>()
    val entries = LinkedHashMap<UUID, Entry>()
    val parentOf = HashMap<UUID, UUID>()
    val childOrder = HashMap<UUID, List<UUID>>()

    init {
        visit(root)
    }

    private fun visit(group: Group) {
        groups[group.id] = group
        childOrder[group.id] = group.groups.map { it.id } + group.entries.map { it.id }
        for (entry in group.entries) {
            entries[entry.id] = entry
            parentOf[entry.id] = group.id
        }
        for (child in group.groups) {
            parentOf[child.id] = group.id
            visit(child)
        }
    }
}

/** The vault's history limits; -1 means unlimited. */
internal class HistoryLimits(
    val maxItems: Int,
    val maxSizeBytes: Int,
)

/** Both trees being merged: the user's (`ours`) and the file's (`theirs`). */
internal class Sides(
    val ours: TreeIndex,
    val theirs: TreeIndex,
)

/** Which objects survive the deletions, and which deleted objects come back because they changed afterwards. */
internal class AliveSet(
    sides: Sides,
    deletions: List<DeletedObject>,
) {
    val entries: Set<UUID>
    val groups: Set<UUID>
    val resurrected: Set<UUID>

    init {
        val deletedAt = deletions.associate { it.id to (it.deletedAt ?: Instant.MAX) }
        val revived = HashSet<UUID>()

        fun survives(
            id: UUID,
            modified: Instant,
        ): Boolean {
            val deletion = deletedAt[id] ?: return true
            return (modified > deletion).also { if (it) revived += id }
        }
        val (ours, theirs) = sides.ours to sides.theirs
        entries =
            (ours.entries.keys + theirs.entries.keys)
                .filter { survives(it, newest(ours.entries[it]?.times, theirs.entries[it]?.times)) }
                .toSet()
        groups =
            (ours.groups.keys + theirs.groups.keys)
                .filter { survives(it, newest(ours.groups[it]?.times, theirs.groups[it]?.times)) }
                .toSet()
        resurrected = revived
    }
}

internal fun mergeGroups(
    sides: Sides,
    alive: Set<UUID>,
): Map<UUID, Group> =
    alive.associateWith { id ->
        val mine = sides.ours.groups[id]
        val other = sides.theirs.groups[id]
        when {
            mine == null -> requireNotNull(other)
            other == null -> mine
            modified(other.times) > modified(mine.times) -> other
            else -> mine
        }
    }

/** Rebuilds the merged tree: decides every object's parent, breaks loops and keeps the children's order. */
internal class TreeBuilder(
    private val sides: Sides,
    private val rootId: UUID,
    private val groups: Map<UUID, Group>,
    private val entries: Map<UUID, Entry>,
) {
    private val childGroups = HashMap<UUID, MutableList<UUID>>()
    private val childEntries = HashMap<UUID, MutableList<UUID>>()

    fun build(): Group {
        val groupParent =
            (groups.keys - rootId).associateWithTo(HashMap()) {
                parentOf(it, sides.ours.groups[it]?.times, sides.theirs.groups[it]?.times)
            }
        breakCycles(groupParent)
        groupParent.forEach { (id, parent) -> childGroups.getOrPut(parent) { ArrayList() } += id }
        for (id in entries.keys) {
            val parent = parentOf(id, sides.ours.entries[id]?.times, sides.theirs.entries[id]?.times)
            childEntries.getOrPut(parent) { ArrayList() } += id
        }
        return assemble(rootId)
    }

    private fun assemble(id: UUID): Group =
        groups.getValue(id).copy(
            groups = ordered(id, childGroups[id].orEmpty()).map(::assemble),
            entries = ordered(id, childEntries[id].orEmpty()).map { entries.getValue(it) },
        )

    // Children keep the order the user saw, then the file's order for objects the user did not have.
    private fun ordered(
        parent: UUID,
        ids: List<UUID>,
    ): List<UUID> {
        val preferred = (sides.ours.childOrder[parent].orEmpty() + sides.theirs.childOrder[parent].orEmpty()).distinct()
        return ids.sortedBy { id -> preferred.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    private fun parentOf(
        id: UUID,
        oursTimes: Times?,
        theirsTimes: Times?,
    ): UUID {
        val mine = sides.ours.parentOf[id]
        val other = sides.theirs.parentOf[id]
        val chosen =
            when {
                mine == null -> other
                other == null -> mine
                location(theirsTimes) > location(oursTimes) -> other
                else -> mine
            }
        return nearestAlive(chosen)
    }

    // Walks up the tree (ours first, then theirs) until a group that survived the merge is found.
    private fun nearestAlive(start: UUID?): UUID {
        var current = start
        val seen = HashSet<UUID>()
        while (current != null && current !in groups && seen.add(current)) {
            current = sides.ours.parentOf[current] ?: sides.theirs.parentOf[current]
        }
        return if (current != null && current in groups) current else rootId
    }

    /**
     * Crossed moves (A into B on one side, B into A on the other) can make a loop with no way back to the root,
     * which would drop those groups from the tree. A group found on such a loop is attached to the root instead.
     */
    private fun breakCycles(parentOf: MutableMap<UUID, UUID>) {
        for (start in parentOf.keys.sorted()) {
            val path = HashSet<UUID>()
            var current = start
            while (current != rootId && path.add(current)) current = parentOf[current] ?: rootId
            if (current != rootId) parentOf[current] = rootId
        }
    }
}

internal fun modified(times: Times?): Instant = times?.lastModified ?: Instant.MIN

private fun location(times: Times?): Instant = times?.locationChanged ?: Instant.MIN

private fun newest(
    a: Times?,
    b: Times?,
): Instant = maxOf(modified(a), modified(b))
