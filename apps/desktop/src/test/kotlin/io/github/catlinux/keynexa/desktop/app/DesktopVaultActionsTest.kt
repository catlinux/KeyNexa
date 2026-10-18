package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.session.findEntry
import io.github.catlinux.keynexa.session.findGroup
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FieldValue
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** What the browser offers and does around groups, entries and the recycle bin. */
class DesktopVaultActionsTest {
    private val data = BinFixture()

    @Test
    fun aNormalGroupOffersTheThreeActions() {
        assertEquals(
            listOf(NodeAction.NewSubgroup, NodeAction.Rename, NodeAction.MoveToBin),
            groupActions(data.vault, data.workId),
        )
    }

    @Test
    fun theRootAndTheBinOfferNothing() {
        assertTrue(groupActions(data.vault, data.vault.root.id).isEmpty())
        assertTrue(groupActions(data.vault, data.binId).isEmpty())
    }

    @Test
    fun whatIsInsideTheBinOnlyOffersRestoreAndErase() {
        assertEquals(
            listOf(NodeAction.Restore, NodeAction.DeleteForever),
            groupActions(data.vault, data.binnedGroupId),
        )
        assertEquals(
            listOf(NodeAction.Restore, NodeAction.DeleteForever),
            entryActions(data.vault, data.binnedEntryId),
        )
    }

    @Test
    fun anEntryOffersEditMoveAndBin() {
        assertEquals(
            listOf(NodeAction.Edit, NodeAction.MoveTo, NodeAction.MoveToBin),
            entryActions(data.vault, data.entryId),
        )
    }

    @Test
    fun knowsWhatIsInsideTheBin() {
        assertTrue(isInsideBin(data.vault, data.binId))
        assertTrue(isInsideBin(data.vault, data.binnedEntryId))
        assertTrue(isInsideBin(data.vault, data.binnedGroupId))
        assertFalse(isInsideBin(data.vault, data.entryId))
        assertFalse(isInsideBin(data.vault, data.workId))
    }

    @Test
    fun findsTheGroupThatHoldsAnObject() {
        assertEquals(data.workId, holderOf(data.vault, data.entryId)?.id)
        assertEquals(data.workId, holderOf(data.vault, data.nestedId)?.id)
        assertEquals(data.vault.root.id, holderOf(data.vault, data.workId)?.id)
        assertNull(holderOf(data.vault, UUID.randomUUID()))
    }

    @Test
    fun aMoveMayNotGoToTheBinTheHolderOrItsOwnSubtree() {
        val destinations = moveDestinations(data.vault, data.workId).map { it.id }

        assertTrue(destinations.isEmpty())

        val forTheEntry = moveDestinations(data.vault, data.entryId).map { it.id }
        assertTrue("destinations were $forTheEntry", data.vault.root.id in forTheEntry)
        assertTrue("destinations were $forTheEntry", data.nestedId in forTheEntry)
        assertFalse("destinations were $forTheEntry", data.workId in forTheEntry)
        assertFalse("destinations were $forTheEntry", data.binId in forTheEntry)
    }

    @Test
    fun anEntryMayNotMoveToTheGroupItIsAlreadyIn() {
        val destinations = moveDestinations(data.vault, data.entryId).map { it.id }

        assertTrue(data.nestedId in destinations)
        assertFalse(data.workId in destinations)
        assertFalse(data.binId in destinations)
    }

    @Test
    fun theSelectionMovesToTheNextEntryOfTheList() {
        val rows = listOf(data.entryId, data.otherEntryId, data.thirdEntryId)

        assertEquals(data.otherEntryId, selectionAfterRemoval(rows, data.entryId))
        assertEquals(data.thirdEntryId, selectionAfterRemoval(rows, data.otherEntryId))
        assertEquals(data.otherEntryId, selectionAfterRemoval(rows, data.thirdEntryId))
        assertNull(selectionAfterRemoval(listOf(data.entryId), data.entryId))
        assertNull(selectionAfterRemoval(rows, UUID.randomUUID()))
    }

    @Test
    fun theSubtreeOfAGroupIsTheGroupAndEverythingBelow() {
        assertEquals(setOf(data.workId, data.nestedId), subtreeIds(data.group(data.workId)))
        assertTrue(subtreeIds(data.group(data.nestedId)).single() == data.nestedId)
    }

    @Test
    fun aDeletedEntryLeavesItsGroupAndComesBack() {
        val editor = VaultEditor()
        val deleted = editor.delete(data.vault, data.entryId)
        assertTrue(
            deleted.root
                .findGroup(data.workId)
                ?.entries
                .isNullOrEmpty(),
        )
        assertTrue(
            deleted.root
                .findGroup(data.binId)
                ?.entries
                ?.any { it.id == data.entryId } == true,
        )

        val restored = editor.restore(deleted, data.entryId)
        assertEquals("GitHub", restored.root.findEntry(data.entryId)?.title)
        assertTrue(
            restored.root
                .findGroup(data.workId)
                ?.entries
                ?.any { it.id == data.entryId } == true,
        )
    }
}

/** A vault with a group, a nested group, an entry and a filled recycle bin. */
private class BinFixture {
    val binId: UUID = UUID.randomUUID()
    val workId: UUID = UUID.randomUUID()
    val nestedId: UUID = UUID.randomUUID()
    val entryId: UUID = UUID.randomUUID()
    val otherEntryId: UUID = UUID.randomUUID()
    val thirdEntryId: UUID = UUID.randomUUID()
    val binnedEntryId: UUID = UUID.randomUUID()
    val binnedGroupId: UUID = UUID.randomUUID()

    val vault: Vault =
        NewVault().create("Personal").let { created ->
            val bin =
                Group(
                    id = binId,
                    name = "Recycle Bin",
                    groups = listOf(Group(id = binnedGroupId, name = "Old work")),
                    entries = listOf(entry(binnedEntryId, "Old entry")),
                )
            val nested = Group(id = nestedId, name = "Nested")
            val work =
                Group(
                    id = workId,
                    name = "Work",
                    groups = listOf(nested),
                    entries = listOf(entry(entryId, "GitHub")),
                )
            val root = Group(id = created.root.id, name = "Personal", groups = listOf(work, bin))
            created.copy(root = root, meta = created.meta.copy(recycleBinGroup = binId))
        }

    fun group(id: UUID): Group = vault.root.findGroup(id) ?: error("the group must be there")

    private fun entry(
        id: UUID,
        title: String,
    ): Entry = Entry(id = id, fields = mapOf(Entry.TITLE to FieldValue(title)))
}
