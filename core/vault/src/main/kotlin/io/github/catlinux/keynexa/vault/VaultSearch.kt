package io.github.catlinux.keynexa.vault

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** Keeps the parts of an entry's searchable text from joining into words that were never written. */
private const val SEPARATOR = "\n"

private val DIACRITICS = Regex("\\p{M}+")

private val WHITESPACE = Regex("\\s+")

/** Lower case and without accents: how the search compares the query with the text. */
private fun folded(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT)

private fun wordsOf(query: String): List<String> = query.split(WHITESPACE).filter { it.isNotBlank() }.map(::folded)

/**
 * Searches entries by text, the way the search box does: case- and accent-insensitive, splitting the query into
 * words that all have to appear in the entry, and never reading a secret.
 */
class VaultSearch {
    /**
     * A match: the entry, the group holding it and the names of the groups from the root down to that group, the
     * root and that group themselves included.
     */
    data class Result(
        val entry: Entry,
        val groupId: UUID,
        val path: List<String>,
    )

    /**
     * The entries that match every word of [query]. Only Title, UserName, URL, Notes and the names of custom fields
     * are read: the Password field, the values of protected fields and the values of custom fields are never
     * searched. Pass [inGroup] to look only inside that group and its subgroups. The recycle bin is always left out.
     *
     * @throws IllegalArgumentException when [inGroup] is not a group of this vault.
     */
    fun search(
        vault: Vault,
        query: String,
        inGroup: UUID? = null,
    ): List<Result> {
        val words = wordsOf(query)
        if (words.isEmpty()) return emptyList()
        if (inGroup != null) {
            requireNotNull(vault.root.findGroup(inGroup)) { "There is no group with that id in this vault." }
        }
        val seeker = Seeker(words, inGroup, vault.meta.recycleBinGroup)
        seeker.visit(vault.root, emptyList(), inGroup == null)
        return seeker.results
    }

    /** Walks the tree collecting the matches, carrying the path of names that leads to each one. */
    private class Seeker(
        private val words: List<String>,
        private val inGroup: UUID?,
        private val recycleBin: UUID?,
    ) {
        val results: MutableList<Result> = mutableListOf()

        fun visit(
            group: Group,
            ancestors: List<String>,
            inside: Boolean,
        ) {
            if (group.id == recycleBin) return
            val path = ancestors + group.name
            val here = inside || group.id == inGroup
            if (here) {
                group.entries.filter(::matches).forEach { results += Result(it, group.id, path) }
            }
            group.groups.forEach { visit(it, path, here) }
        }

        private fun matches(entry: Entry): Boolean {
            val text = searchableText(entry)
            return words.all { text.contains(it) }
        }

        // Only what the list shows: the standard fields' text and the names of custom fields.
        private fun searchableText(entry: Entry): String {
            val parts = mutableListOf<String>()
            for ((name, value) in entry.fields) {
                when {
                    name == Entry.PASSWORD -> Unit
                    name in STANDARD_FIELDS -> if (!value.protected) parts += folded(value.value)
                    else -> parts += folded(name)
                }
            }
            return parts.joinToString(SEPARATOR)
        }
    }

    private companion object {
        /** The fields whose text is looked at; a custom field only lends its name. */
        val STANDARD_FIELDS = setOf(Entry.TITLE, Entry.USER_NAME, Entry.URL, Entry.NOTES)
    }
}
