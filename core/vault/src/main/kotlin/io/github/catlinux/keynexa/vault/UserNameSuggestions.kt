package io.github.catlinux.keynexa.vault

import java.util.Locale
import java.util.UUID

/**
 * The user names a vault already uses, most used first, so the editor can suggest them.
 *
 * Only the UserName field of each entry is read: no other field, protected or not, and no history item is looked
 * at. The recycle bin and its subgroups are left out, since what was deleted is no suggestion.
 */
object UserNameSuggestions {
    /** The distinct user names of [vault], most frequent first; ties go alphabetically, ignoring case. */
    fun of(vault: Vault): List<String> {
        val counts = mutableMapOf<String, Int>()
        collect(vault.root, vault.meta.recycleBinGroup, counts)
        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.lowercase(Locale.ROOT) }
                    .thenBy { it.key },
            ).map { it.key }
    }

    private fun collect(
        group: Group,
        recycleBin: UUID?,
        counts: MutableMap<String, Int>,
    ) {
        if (group.id == recycleBin) return
        for (entry in group.entries) {
            val userName = entry.userName.trim()
            if (userName.isNotEmpty()) counts[userName] = (counts[userName] ?: 0) + 1
        }
        group.groups.forEach { collect(it, recycleBin, counts) }
    }
}
