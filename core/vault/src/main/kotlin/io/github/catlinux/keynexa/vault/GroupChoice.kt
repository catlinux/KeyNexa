package io.github.catlinux.keynexa.vault

import java.util.UUID

/** A group an entry may be put in, with its path from the root as a label, like `Social / Work`. */
data class GroupChoice(
    val id: UUID,
    val label: String,
)

/** Separator between the names of a group's ancestors in a [GroupChoice] label. */
private const val PATH_SEPARATOR = " / "

/** Every group of [vault] in tree order, the root included, except the recycle bin and what it holds. */
fun groupChoices(vault: Vault): List<GroupChoice> {
    val bin = vault.meta.recycleBinGroup
    val choices = mutableListOf<GroupChoice>()

    fun walk(
        group: Group,
        path: List<String>,
    ) {
        if (group.id == bin) return
        val here = path + group.name
        choices += GroupChoice(group.id, here.joinToString(PATH_SEPARATOR))
        group.groups.forEach { walk(it, here) }
    }
    walk(vault.root, emptyList())
    return choices
}

/** The id of the group that directly holds the entry [entryId], or null when the vault has no such entry. */
fun groupOfEntry(
    vault: Vault,
    entryId: UUID,
): UUID? {
    val path = vault.root.pathTo(entryId)
    return path.lastOrNull()?.id
}
