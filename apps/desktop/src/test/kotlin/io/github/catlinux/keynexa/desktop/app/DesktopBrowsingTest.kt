package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.Times
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

private const val VAULT_NAME = "Personal"
private const val INTERNET = "Internet"
private const val WORK = "Work"
private const val NESTED = "Nested"
private const val BIN = "Recycle bin"

private const val GITHUB = "GitHub"
private const val MAIL = "Mail"
private const val JIRA = "Jira"

/** The pieces the three-panel browser works out without drawing anything. */
class DesktopBrowsingTest {
    private val search = VaultSearch()
    private val data = Fixture()

    @Test
    fun leavesTheRecycleBinOutOfTheTree() {
        assertEquals(listOf(INTERNET, WORK), treeChildren(data.vault.root, data.binId).map { it.name })
    }

    @Test
    fun countsTheEntriesOfTheWholeSubtree() {
        assertEquals(5, entryCount(data.vault.root))
        assertEquals(1, entryCount(data.group(data.workId)))
        assertEquals(1, entryCount(data.group(data.binId)))
    }

    @Test
    fun namesTheGroupsFromTheRootDownToTheGroup() {
        assertEquals(listOf(VAULT_NAME), groupPath(data.vault.root, data.vault.root.id))
        assertEquals(listOf(VAULT_NAME, WORK, NESTED), groupPath(data.vault.root, data.nestedId))
        assertTrue(groupPath(data.vault.root, UUID.randomUUID()).isEmpty())
    }

    @Test
    fun showsTheSelectedGroupsOwnEntriesWithTheirPath() {
        val rows = entryRows(data.vault, data.internetId, "", search)

        assertEquals(listOf(GITHUB), rows.map { it.entry.title })
        assertEquals(listOf(VAULT_NAME, INTERNET), rows.single().groupPath)
    }

    @Test
    fun showsTheMatchesOfASearchOverTheWholeVault() {
        val rows = entryRows(data.vault, data.internetId, "work", search)

        assertEquals(listOf(JIRA), rows.map { it.entry.title })
        assertEquals(listOf(VAULT_NAME, WORK, NESTED), rows.single().groupPath)
    }

    @Test
    fun neverShowsTheRecycleBinInASearch() {
        assertTrue(entryRows(data.vault, null, "deleted", search).isEmpty())
    }

    @Test
    fun ordersByTitlePuttingUntitledLast() {
        val rows = sortedRows(entryRows(data.vault, data.vault.root.id, "", search), EntrySort.Title)

        assertEquals(listOf(MAIL, ""), rows.map { it.entry.title })
    }

    @Test
    fun ordersByTheNewestChangeFirst() {
        val rows = sortedRows(entryRows(data.vault, data.vault.root.id, "", search), EntrySort.Modified)

        assertEquals(listOf(MAIL, ""), rows.map { it.entry.title })
    }

    @Test
    fun showsTheDomainOfAWebAddress() {
        assertEquals("github.com", domainOf("https://www.github.com/ana/repo"))
        assertEquals("example.org", domainOf(" http://example.org "))
        assertNull(domainOf("not an address"))
        assertNull(domainOf(""))
    }

    @Test
    fun opensOnlyWebAddressesWithAHost() {
        assertEquals("https://github.com/ana", browsableUri("https://github.com/ana")?.toString())
        assertNull(browsableUri("ftp://github.com"))
        assertNull(browsableUri("https://"))
        assertNull(browsableUri("not an address"))
    }

    @Test
    fun formatsALocaleAwareDate() {
        val modified = Instant.parse("2026-10-18T09:30:00Z")

        assertTrue(shortDate(modified, ZoneId.of("UTC"), Locale.ENGLISH).contains("2026"))
        assertEquals("", shortDate(null, ZoneId.of("UTC"), Locale.ENGLISH))
    }

    @Test
    fun listsTheCustomFieldsInNameOrder() {
        val entry = data.vault.entry(data.githubId) ?: error("the entry must be there")

        assertEquals(listOf("Account", "Seed"), customFields(entry).map { it.first })
    }

    @Test
    fun findsAnEntryById() {
        assertEquals(GITHUB, data.vault.entry(data.githubId)?.title)
        assertNull(data.vault.entry(null))
        assertNull(data.vault.entry(UUID.randomUUID()))
    }
}

/** A vault with two groups, a nested one, a filled recycle bin and the entries the tests look for. */
private class Fixture {
    val binId: UUID = UUID.randomUUID()
    val internetId: UUID = UUID.randomUUID()
    val workId: UUID = UUID.randomUUID()
    val nestedId: UUID = UUID.randomUUID()
    val githubId: UUID = UUID.randomUUID()
    val jiraId: UUID = UUID.randomUUID()

    val vault: Vault =
        NewVault().create(VAULT_NAME).let { created ->
            val root =
                Group(
                    id = created.root.id,
                    name = VAULT_NAME,
                    entries =
                        listOf(
                            entry(MAIL, modified = Instant.parse("2026-10-18T10:00:00Z")),
                            entry(""),
                        ),
                    groups = listOf(internet(), work(), bin()),
                )
            created.copy(root = root, meta = created.meta.copy(recycleBinGroup = binId))
        }

    fun group(id: UUID): Group = vault.root.findGroup(id) ?: error("the group must be there")

    private fun internet(): Group =
        Group(
            id = internetId,
            name = INTERNET,
            entries =
                listOf(
                    Entry(
                        id = githubId,
                        times = Times(lastModified = Instant.parse("2026-10-01T10:00:00Z")),
                        fields =
                            mapOf(
                                Entry.TITLE to FieldValue(GITHUB),
                                Entry.URL to FieldValue("https://www.github.com/ana"),
                                "Seed" to FieldValue("a-seed"),
                                "Account" to FieldValue("ana"),
                            ),
                    ),
                ),
        )

    private fun work(): Group =
        Group(
            id = workId,
            name = WORK,
            groups = listOf(Group(id = nestedId, name = NESTED, entries = listOf(entry(JIRA, notes = "work login")))),
        )

    private fun bin(): Group = Group(id = binId, name = BIN, entries = listOf(entry("Deleted", notes = "deleted here")))

    private fun entry(
        title: String,
        notes: String = "",
        modified: Instant? = null,
    ): Entry =
        Entry(
            id = UUID.randomUUID(),
            times = Times(lastModified = modified),
            fields = mapOf(Entry.TITLE to FieldValue(title), Entry.NOTES to FieldValue(notes)),
        )
}
