package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.session.findEntry
import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultSearch
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.util.Locale
import java.util.UUID

/** A row of the centre panel: the entry and the names of the groups it lives under, root included. */
internal data class EntryRow(
    val entry: Entry,
    val groupPath: List<String>,
)

/** The order the centre panel shows its rows in. */
internal enum class EntrySort { Title, Modified }

/** The web addresses the browser may open. */
private val WEB_SCHEMES = setOf("http", "https")

/** The subgroups the tree shows under [group]: the recycle bin is listed on its own, not among them. */
internal fun treeChildren(
    group: Group,
    recycleBin: UUID?,
): List<Group> = group.groups.filter { it.id != recycleBin }

/** How many entries live under [group], its own and those of every subgroup. */
internal fun entryCount(group: Group): Int = group.entries.size + group.groups.sumOf(::entryCount)

/** The names from the root down to the group [id], the root included; empty when this vault has no such group. */
internal fun groupPath(
    root: Group,
    id: UUID,
): List<String> {
    if (root.id == id) return listOf(root.name)
    for (child in root.groups) {
        val below = groupPath(child, id)
        if (below.isNotEmpty()) return listOf(root.name) + below
    }
    return emptyList()
}

/**
 * The rows the centre panel shows: the matches of [query] over the whole vault, each with the path of its group, or
 * the entries of the selected group when there is no search.
 */
internal fun entryRows(
    vault: Vault,
    groupId: UUID?,
    query: String,
    search: VaultSearch,
): List<EntryRow> {
    if (query.isNotBlank()) return search.search(vault, query).map { EntryRow(it.entry, it.path) }
    val group = groupId?.let { vault.root.findGroup(it) } ?: return emptyList()
    val path = groupPath(vault.root, group.id)
    return group.entries.map { EntryRow(it, path) }
}

/** The rows in the order [sort] asks for: by title (untitled last) or by the newest change first. */
internal fun sortedRows(
    rows: List<EntryRow>,
    sort: EntrySort,
): List<EntryRow> =
    when (sort) {
        EntrySort.Title ->
            rows.sortedWith(
                compareBy<EntryRow> { it.entry.title.isBlank() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.entry.title },
            )
        EntrySort.Modified -> rows.sortedByDescending { it.entry.times.lastModified ?: Instant.EPOCH }
    }

/** The domain of [url] for the list, without a leading `www.`, or null when it has no host. */
@Suppress("SwallowedException")
internal fun domainOf(url: String): String? {
    val host =
        try {
            URI(url.trim()).host
        } catch (failure: URISyntaxException) {
            null
        }
    return host?.takeIf { it.isNotBlank() }?.removePrefix("www.")
}

/** The address the system browser may open for [url]: only `http` and `https`, and only with a host. */
@Suppress("SwallowedException")
internal fun browsableUri(url: String): URI? {
    val uri =
        try {
            URI(url.trim())
        } catch (failure: URISyntaxException) {
            return null
        }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return if (scheme in WEB_SCHEMES && !uri.host.isNullOrBlank()) uri else null
}

/** The custom fields of [entry], in name order: everything that is not one of the five standard ones. */
internal fun customFields(entry: Entry): List<Pair<String, FieldValue>> =
    entry.fields.entries
        .filter { it.key !in STANDARD_FIELDS }
        .sortedBy { it.key.lowercase(Locale.ROOT) }
        .map { it.key to it.value }

/** The five fields every KDBX entry has; the rest are custom. */
internal val STANDARD_FIELDS = setOf(Entry.TITLE, Entry.USER_NAME, Entry.PASSWORD, Entry.URL, Entry.NOTES)

/** The entry with [id] in this vault, or null. */
internal fun Vault.entry(id: UUID?): Entry? = id?.let { root.findEntry(it) }
