package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChoiceTest {
    private val ids = TestIds()

    private val entry = testEntry(ids(), "Entry")
    private val work = testGroup(ids(), "Work", entries = listOf(entry))
    private val social = testGroup(ids(), "Social", groups = listOf(work))
    private val bin = testGroup(ids(), "Recycle Bin", groups = listOf(testGroup(ids(), "Old")))
    private val vault =
        testVault(
            testGroup(ROOT_ID, VAULT_NAME, groups = listOf(social, bin)),
            VaultMeta(name = VAULT_NAME, recycleBinGroup = bin.id),
        )

    @Test
    fun listsTheGroupsInTreeOrderWithTheirPathAndWithoutTheBin() {
        assertEquals(
            listOf(VAULT_NAME, "$VAULT_NAME / Social", "$VAULT_NAME / Social / Work"),
            groupChoices(vault).map { it.label },
        )
    }

    @Test
    fun findsTheGroupThatHoldsAnEntry() {
        assertEquals(work.id, groupOfEntry(vault, entry.id))
    }

    @Test
    fun movingAnEntryThroughTheEditorPutsItInTheChosenGroup() {
        val moved = VaultEditor().move(vault, entry.id, social.id)

        assertEquals(social.id, groupOfEntry(moved, entry.id))
    }
}
