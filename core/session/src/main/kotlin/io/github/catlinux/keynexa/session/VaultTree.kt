package io.github.catlinux.keynexa.session

import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Group
import java.util.UUID

/** The group with [id] in this subtree, or null. */
fun Group.findGroup(id: UUID): Group? {
    if (this.id == id) return this
    return groups.firstNotNullOfOrNull { it.findGroup(id) }
}

/** The entry with [id] in this subtree, or null. */
fun Group.findEntry(id: UUID): Entry? {
    entries.firstOrNull { it.id == id }?.let { return it }
    return groups.firstNotNullOfOrNull { it.findEntry(id) }
}

/** Names of the groups from the root down to the group that holds the entry [id]; empty when it is not here. */
fun Group.pathToEntry(id: UUID): List<String> {
    if (entries.any { it.id == id }) return listOf(name)
    for (child in groups) {
        val below = child.pathToEntry(id)
        if (below.isNotEmpty()) return listOf(name) + below
    }
    return emptyList()
}

/** This subtree with the entry [id] replaced by [change]'s result. */
fun Group.mapEntry(
    id: UUID,
    change: (Entry) -> Entry,
): Group =
    copy(
        entries = entries.map { if (it.id == id) change(it) else it },
        groups = groups.map { it.mapEntry(id, change) },
    )
