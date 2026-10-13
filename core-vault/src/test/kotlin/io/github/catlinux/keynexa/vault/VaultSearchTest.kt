package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The id of the entry that carries a protected note and a custom field, only used by these tests. */
internal val HIDDEN_ID: UUID = id(9)

private const val CANARY_PASSWORD = "canary-password"
private const val CUSTOM_FIELD = "otp-seed"
private const val WORK_NOTE = "work login"
private const val SPRINT_NOTE = "sprint planning for work"
private const val DELETED_NOTE = "deleted here"

class VaultSearchTest {
    private val search = VaultSearch()

    @Test
    fun findsEntriesByTheirTitle() {
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), "github")))
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), "GITHUB")))
    }

    @Test
    fun findsEntriesByUserNameUrlAndNotes() {
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), "ana")))
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), "https://github.com")))
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), WORK_NOTE)))
    }

    @Test
    fun ignoresAccents() {
        assertEquals(listOf(MAIL_ID), idsOf(search.search(vault(), "nandu")))
        assertEquals(listOf(MAIL_ID), idsOf(search.search(vault(), "ÑANDÚ")))
        assertEquals(listOf(MAIL_ID), idsOf(search.search(vault(), "cafe")))
    }

    @Test
    fun needsEveryWordOfTheQueryInTheSameEntry() {
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), "ana github")))
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), "work git")))
        assertTrue(search.search(vault(), "ana jira").isEmpty())
    }

    @Test
    fun searchesTheNamesOfCustomFieldsButNeverTheirValues() {
        assertEquals(listOf(HIDDEN_ID), idsOf(search.search(vault(), "otp")))
        assertTrue(search.search(vault(), "zzz-otp-value").isEmpty())
    }

    @Test
    fun neverReadsThePasswordFieldNorTheValuesOfProtectedFields() {
        assertTrue(search.search(vault(), CANARY_PASSWORD).isEmpty())
        assertTrue(search.search(vault(), "secret note").isEmpty())
    }

    @Test
    fun canBeLimitedToAGroupAndItsSubgroups() {
        assertEquals(listOf(NESTED_ENTRY_ID), idsOf(search.search(vault(), "sprint", inGroup = WORK_ID)))
        assertTrue(search.search(vault(), "sprint", inGroup = INTERNET_ID).isEmpty())
        assertEquals(listOf(NESTED_ENTRY_ID), idsOf(search.search(vault(), "jira", inGroup = WORK_ID)))
    }

    @Test
    fun alwaysLeavesTheRecycleBinOut() {
        assertEquals(listOf(GITHUB_ID), idsOf(search.search(vault(), "ana")))
        assertTrue(search.search(vault(), DELETED_NOTE, inGroup = RECYCLE_BIN_ID).isEmpty())
    }

    @Test
    fun findsEachMatchOnlyOnceAndInTreeOrder() {
        assertEquals(listOf(GITHUB_ID, NESTED_ENTRY_ID), idsOf(search.search(vault(), "work")))
    }

    @Test
    fun returnsThePathOfGroupsThatLeadsToEachMatch() {
        val match = search.search(vault(), "sprint").single()

        assertEquals(NESTED_ENTRY_ID, match.entry.id)
        assertEquals(NESTED_ID, match.groupId)
        assertEquals(listOf(VAULT_NAME, "Work", "Nested"), match.path)
    }

    @Test
    fun anEmptyQueryFindsNothing() {
        assertTrue(search.search(vault(), "").isEmpty())
        assertTrue(search.search(vault(), "   ").isEmpty())
    }

    @Test
    fun anUnknownGroupIsAUsageError() {
        assertThrows(IllegalArgumentException::class.java) { search.search(vault(), "github", inGroup = id(999)) }
    }

    private fun idsOf(results: List<VaultSearch.Result>): List<UUID> = results.map { it.entry.id }

    /** A tree with entries that use every field the search is allowed to read, and a filled recycle bin. */
    private fun vault(): Vault {
        val root = testGroup(ROOT_ID, VAULT_NAME, groups = listOf(internet(), work(), recycleBin()))
        return testVault(root, binMeta())
    }

    private fun internet(): Group =
        testGroup(
            INTERNET_ID,
            "Internet",
            entries =
                listOf(
                    testEntry(
                        GITHUB_ID,
                        "GitHub",
                        fields =
                            mapOf(
                                Entry.USER_NAME to FieldValue("ana"),
                                Entry.URL to FieldValue("https://github.com"),
                                Entry.NOTES to FieldValue(WORK_NOTE),
                                Entry.PASSWORD to FieldValue(CANARY_PASSWORD, protected = true),
                            ),
                    ),
                    testEntry(MAIL_ID, "Mail", fields = mapOf(Entry.NOTES to FieldValue("Ñandú café"))),
                    hidden(),
                ),
        )

    private fun hidden(): Entry =
        testEntry(
            HIDDEN_ID,
            "Hidden",
            fields =
                mapOf(
                    Entry.NOTES to FieldValue("secret note", protected = true),
                    CUSTOM_FIELD to FieldValue("zzz-otp-value"),
                ),
        )

    private fun work(): Group =
        testGroup(
            WORK_ID,
            "Work",
            groups =
                listOf(
                    testGroup(
                        NESTED_ID,
                        "Nested",
                        entries = listOf(jira()),
                    ),
                ),
        )

    private fun jira(): Entry =
        testEntry(NESTED_ENTRY_ID, "Jira", fields = mapOf(Entry.NOTES to FieldValue(SPRINT_NOTE)))

    private fun recycleBin(): Group =
        testGroup(
            RECYCLE_BIN_ID,
            RECYCLE_BIN_GROUP_NAME,
            entries =
                listOf(
                    testEntry(
                        OLD_ENTRY_ID,
                        "GitHub",
                        fields =
                            mapOf(
                                Entry.USER_NAME to FieldValue("ana"),
                                Entry.NOTES to FieldValue(DELETED_NOTE),
                            ),
                    ),
                ),
        )

    private fun binMeta(): VaultMeta =
        VaultMeta(
            name = VAULT_NAME,
            recycleBinEnabled = true,
            recycleBinGroup = RECYCLE_BIN_ID,
            recycleBinChanged = FIXTURE_TIME,
        )
}
