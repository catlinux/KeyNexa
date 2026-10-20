package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HIDDEN_USER_PASSWORD = "hidden-user"

class UserNameSuggestionsTest {
    private val ids = TestIds()

    @Test
    fun theMostFrequentUserNameComesFirst() {
        val vault = vaultOf(groupOf("Internet", "ana", "ana", "ana", "bob", "bob", "cara"))

        assertEquals(listOf("ana", "bob", "cara"), UserNameSuggestions.of(vault))
    }

    @Test
    fun aTieIsOrderedAlphabeticallyIgnoringCase() {
        val vault = vaultOf(groupOf("Internet", "Zoe", "ana", "Bob"))

        assertEquals(listOf("ana", "Bob", "Zoe"), UserNameSuggestions.of(vault))
    }

    @Test
    fun blankUserNamesAreIgnoredAndTheKeptOnesAreTrimmed() {
        val vault = vaultOf(groupOf("Internet", "  Ana  ", "", "   ", "\t"))

        assertEquals(listOf("Ana"), UserNameSuggestions.of(vault))
    }

    @Test
    fun leavesOutTheRecycleBinAndItsSubgroups() {
        val deleted = testGroup(ids(), "Old", entries = listOf(entryOf("older")))
        val bin =
            testGroup(
                ids(),
                RECYCLE_BIN_GROUP_NAME,
                groups = listOf(deleted),
                entries = listOf(entryOf("deleted")),
            )

        val vault = vaultOf(groupOf("Internet", "ana"), bin = bin)

        assertEquals(listOf("ana"), UserNameSuggestions.of(vault))
    }

    @Test
    fun theSameUserNameInDifferentGroupsIsOneValueWithTheAddedFrequency() {
        val vault = vaultOf(groupOf("Internet", "ana"), groupOf("Work", "ana", "ana", "bob"))

        assertEquals(listOf("ana", "bob"), UserNameSuggestions.of(vault))
    }

    @Test
    fun namesThatOnlyDifferInCaseStayApartAndTieBreakOnTheirText() {
        val vault = vaultOf(groupOf("Internet", "ana", "Ana"))

        assertEquals(listOf("Ana", "ana"), UserNameSuggestions.of(vault))
    }

    @Test
    fun readsNoPasswordSoASecretNeverBecomesASuggestion() {
        val passwordOnly =
            testEntry(
                ids(),
                "Hidden",
                fields = mapOf(Entry.PASSWORD to FieldValue(HIDDEN_USER_PASSWORD, protected = true)),
            )
        val vault = vaultOf(testGroup(ids(), "Internet", entries = listOf(passwordOnly)))

        assertTrue(UserNameSuggestions.of(vault).isEmpty())
    }

    private fun entryOf(userName: String): Entry =
        testEntry(ids(), "Entry", fields = mapOf(Entry.USER_NAME to FieldValue(userName)))

    private fun groupOf(
        name: String,
        vararg userNames: String,
    ): Group = testGroup(ids(), name, entries = userNames.map(::entryOf))

    private fun vaultOf(
        vararg groups: Group,
        bin: Group? = null,
    ): Vault {
        val all = if (bin == null) groups.toList() else groups.toList() + bin
        return testVault(
            testGroup(ROOT_ID, VAULT_NAME, groups = all),
            VaultMeta(name = VAULT_NAME, recycleBinGroup = bin?.id),
        )
    }
}
