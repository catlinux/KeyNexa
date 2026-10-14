package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/*
 * The helpers at the bottom of this file (clock, ids, fixture vaults) are shared with VaultSearchTest and
 * NewVaultTest: the task keeps the change to the files it lists, so there is no separate fixtures file.
 */

/** The instant the fixture tree was created at. */
internal val FIXTURE_TIME: Instant = Instant.parse("2026-01-01T00:00:00Z")

/** The instant every edit in these tests happens at. */
internal val EDITED_AT: Instant = Instant.parse("2026-03-01T12:00:00Z")

internal const val VAULT_NAME = "MyVault"
internal const val FIRST_GENERATED_ID = 100L

internal val ROOT_ID: UUID = id(1)
internal val INTERNET_ID: UUID = id(2)
internal val WORK_ID: UUID = id(3)
internal val GITHUB_ID: UUID = id(4)
internal val MAIL_ID: UUID = id(5)
internal val JIRA_ID: UUID = id(6)
internal val NESTED_ID: UUID = id(7)
internal val NESTED_ENTRY_ID: UUID = id(8)
internal val RECYCLE_BIN_ID: UUID = id(50)
internal val OLD_ENTRY_ID: UUID = id(51)
internal val OLDER_ENTRY_ID: UUID = id(52)

class VaultEditorTest {
    @Test
    fun addingAGroupPutsItInItsParentWithAFreshIdAndTimes() {
        val vault = editorVault()

        val edited = editor().addGroup(vault, INTERNET_ID, "Bank")

        val added = requireNotNull(edited.root.findGroup(id(FIRST_GENERATED_ID)))
        assertEquals("Bank", added.name)
        assertEquals(testTimes(EDITED_AT), added.times)
        assertEquals(INTERNET_ID, edited.root.parentGroupOf(added.id))
        assertEquals(vault, editorVault())
    }

    @Test
    fun addingAnEntryCreatesItWithItsTitleAndTimes() {
        val vault = editorVault()

        val edited = editor().addEntry(vault, WORK_ID, "Release notes")

        val added = requireNotNull(edited.root.findEntry(id(FIRST_GENERATED_ID)))
        assertEquals("Release notes", added.title)
        assertEquals(testTimes(EDITED_AT), added.times)
        assertEquals(WORK_ID, edited.root.parentGroupOf(added.id))
        assertEquals(vault, editorVault())
    }

    @Test
    fun editingAFieldKeepsTheVersionThatWasThereInTheHistory() {
        val vault = editorVault()

        val edited = editor().editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }

        val entry = requireNotNull(edited.root.findEntry(GITHUB_ID))
        assertEquals(GITHUB_URL, entry.fields[Entry.URL]?.value)
        assertEquals(EDITED_AT, entry.times.lastModified)
        val previous = entry.history.single()
        assertEquals(vault.root.findEntry(GITHUB_ID), previous)
        assertNull(previous.fields[Entry.URL])
        assertEquals(FIXTURE_TIME, previous.times.lastModified)
        assertTrue(previous.history.isEmpty())
    }

    @Test
    fun editingATagIsAnEditOfTheContents() {
        val edited = editor().editEntry(editorVault(), GITHUB_ID) { it.copy(tags = it.tags + "work") }

        val entry = requireNotNull(edited.root.findEntry(GITHUB_ID))
        assertEquals(listOf("work"), entry.tags)
        assertEquals(1, entry.history.size)
    }

    @Test
    fun editingAnAttachmentIsAnEditOfTheContents() {
        val attachment = Attachment("cert.pem", BinaryId(CERT_ID))

        val edited = editor().editEntry(editorVault(), GITHUB_ID) { it.copy(attachments = listOf(attachment)) }

        val entry = requireNotNull(edited.root.findEntry(GITHUB_ID))
        assertEquals(listOf(attachment), entry.attachments)
        assertEquals(1, entry.history.size)
    }

    @Test
    fun addAttachmentStoresBinaryAndAttachmentAndCreatesHistory() {
        val vault = editorVault()
        val content = byteArrayOf(1, 2, 3, 4)

        val edited = editor().addAttachment(vault, GITHUB_ID, "file.bin", content)

        val entry = requireNotNull(edited.root.findEntry(GITHUB_ID))
        val attachment = entry.attachments.single()
        assertEquals("file.bin", attachment.name)
        assertEquals(content.toList(), edited.binaries[attachment.id]?.content?.toList())
        assertEquals(1, entry.history.size)
    }

    @Test
    fun addAttachmentReplacesAnAttachmentWithTheSameName() {
        val vault = editorVault()
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(9, 8, 7)

        var edited = editor().addAttachment(vault, GITHUB_ID, "file.bin", first)
        edited = editor().addAttachment(edited, GITHUB_ID, "file.bin", second)

        val entry = requireNotNull(edited.root.findEntry(GITHUB_ID))
        assertEquals(1, entry.attachments.size)
        assertEquals(second.toList(), edited.binaries[entry.attachments.single().id]?.content?.toList())
    }

    @Test
    fun removeAttachmentDropsItFromTheEntry() {
        val vault = editorVault()
        val content = byteArrayOf(1, 2, 3)

        val added = editor().addAttachment(vault, GITHUB_ID, "file.bin", content)
        val removed = editor().removeAttachment(added, GITHUB_ID, "file.bin")

        val entry = requireNotNull(removed.root.findEntry(GITHUB_ID))
        assertTrue(entry.attachments.isEmpty())
    }

    @Test
    fun removeAttachmentGarbageCollectsUnreferencedBinaries() {
        val vault = editorVault(VaultMeta(name = VAULT_NAME, historyMaxItems = 0))
        val content = byteArrayOf(1, 2, 3)

        val added = editor().addAttachment(vault, GITHUB_ID, "file.bin", content)
        val id = requireNotNull(added.root.findEntry(GITHUB_ID)).attachments.single().id
        val removed = editor().removeAttachment(added, GITHUB_ID, "file.bin")

        assertTrue(id !in removed.binaries)
    }

    @Test
    fun removeAttachmentKeepsBinariesStillReferencedElsewhere() {
        val vault = editorVault(VaultMeta(name = VAULT_NAME, historyMaxItems = 0))
        val content = byteArrayOf(1, 2, 3)

        var edited = editor().addAttachment(vault, GITHUB_ID, "shared.bin", content)
        edited = editor().addAttachment(edited, MAIL_ID, "shared.bin", content)
        val id = requireNotNull(edited.root.findEntry(GITHUB_ID)).attachments.single().id
        edited = editor().removeAttachment(edited, GITHUB_ID, "shared.bin")

        assertTrue(id in edited.binaries)
        edited = editor().removeAttachment(edited, MAIL_ID, "shared.bin")
        assertTrue(id !in edited.binaries)
    }

    @Test
    fun storeBinariesAddsNewContentAndDropsWhatNobodyReferences() {
        val vault = editorVault(VaultMeta(name = VAULT_NAME, historyMaxItems = 0))
        val kept = byteArrayOf(9, 8, 7)
        val withAttachment =
            editor().editEntry(
                vault,
                GITHUB_ID,
            ) { it.copy(attachments = listOf(Attachment("kept.bin", binaryIdOf(kept)))) }

        val stored = editor().storeBinaries(withAttachment, listOf(kept, byteArrayOf(5)))

        assertTrue(binaryIdOf(kept) in stored.binaries)
        // The second content is referenced by nobody, so it is not kept in the file.
        assertTrue(binaryIdOf(byteArrayOf(5)) !in stored.binaries)
        val withoutAttachment = editor().editEntry(stored, GITHUB_ID) { it.copy(attachments = emptyList()) }
        assertTrue(binaryIdOf(kept) !in editor().storeBinaries(withoutAttachment, emptyList()).binaries)
    }

    @Test
    fun binaryIdIsTheLowercaseSha256OfTheContent() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            binaryIdOf("abc".toByteArray()).hex,
        )
    }

    @Test
    fun addAttachmentRejectsOversizedContent() {
        val vault = editorVault()

        assertThrows(IllegalArgumentException::class.java) {
            editor().addAttachment(vault, GITHUB_ID, "big.bin", ByteArray(MAX_ATTACHMENT_BYTES + 1))
        }
    }

    @Test
    fun addAttachmentRejectsBlankName() {
        val vault = editorVault()

        assertThrows(IllegalArgumentException::class.java) {
            editor().addAttachment(vault, GITHUB_ID, "   ", byteArrayOf(1, 2, 3))
        }
    }

    @Test
    fun clearHistoryEmptiesTheHistory() {
        val vault = editorVault()
        val edited = editor().editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }

        val cleared = editor().clearHistory(edited, GITHUB_ID)

        val entry = requireNotNull(cleared.root.findEntry(GITHUB_ID))
        assertTrue(entry.history.isEmpty())
        assertEquals(GITHUB_URL, entry.fields[Entry.URL]?.value)
    }

    @Test
    fun clearHistoryDoesNotChangeLastModified() {
        val vault = editorVault()
        val edited = editor().editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }

        val cleared = editor().clearHistory(edited, GITHUB_ID)

        assertEquals(
            requireNotNull(edited.root.findEntry(GITHUB_ID)).times.lastModified,
            requireNotNull(cleared.root.findEntry(GITHUB_ID)).times.lastModified,
        )
    }

    @Test
    fun clearHistoryGarbageCollectsBinariesOnlyTheHistoryReferenced() {
        val vault = editorVault()
        val content = byteArrayOf(1, 2, 3)

        var edited = editor().addAttachment(vault, GITHUB_ID, "file.bin", content)
        edited = editor().removeAttachment(edited, GITHUB_ID, "file.bin")
        val editedEntry = requireNotNull(edited.root.findEntry(GITHUB_ID))
        val historyAttachment = editedEntry.history.flatMap { it.attachments }.single()
        val id = historyAttachment.id

        val cleared = editor().clearHistory(edited, GITHUB_ID)

        assertTrue(id !in cleared.binaries)
        assertTrue(requireNotNull(cleared.root.findEntry(GITHUB_ID)).history.isEmpty())
    }

    @Test
    fun clearHistoryKeepsBinariesReferencedByTheCurrentVersion() {
        val vault = editorVault()
        val content = byteArrayOf(1, 2, 3)

        val edited = editor().addAttachment(vault, GITHUB_ID, "file.bin", content)
        val id = requireNotNull(edited.root.findEntry(GITHUB_ID)).attachments.single().id

        // A following edit creates a history version, but the binary is still current.
        val withHistory = editor().editEntry(edited, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }
        val cleared = editor().clearHistory(withHistory, GITHUB_ID)

        assertTrue(id in cleared.binaries)
    }

    @Test
    fun clearHistoryWithNoHistoryReturnsTheSameVault() {
        val vault = editorVault()

        assertSame(vault, editor().clearHistory(vault, GITHUB_ID))
    }

    @Test
    fun clearHistoryRejectsAnUnknownEntry() {
        val vault = editorVault()

        assertThrows(IllegalArgumentException::class.java) { editor().clearHistory(vault, id(999)) }
    }

    @Test
    fun anEditThatChangesNoContentReturnsTheSameVault() {
        val vault = editorVault()
        val editor = editor()

        assertSame(vault, editor.editEntry(vault, GITHUB_ID) { it })
        // The times, the history and the id are the editor's business, not part of the contents.
        assertSame(vault, editor.editEntry(vault, GITHUB_ID) { it.copy(times = Times(expiry = EDITED_AT)) })
        assertSame(vault, editor.editEntry(vault, GITHUB_ID) { it.copy(id = id(999)) })
        val withHistory = { entry: Entry -> entry.copy(history = listOf(entry.copy(history = emptyList()))) }
        assertSame(vault, editor.editEntry(vault, GITHUB_ID, withHistory))
    }

    @Test
    fun theHistoryKeepsOnlyTheNewestVersionsUpToTheLimit() {
        val editor = editor()
        var vault = editorVault(VaultMeta(name = VAULT_NAME, historyMaxItems = 2))

        vault = editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }
        vault = editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, "https://gist.github.com") }
        vault = editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, "https://pages.github.com") }

        val entry = requireNotNull(vault.root.findEntry(GITHUB_ID))
        assertEquals("https://pages.github.com", entry.fields[Entry.URL]?.value)
        assertEquals(listOf(GITHUB_URL, "https://gist.github.com"), entry.history.map { it.fields[Entry.URL]?.value })
    }

    @Test
    fun aHistoryLimitOfZeroLeavesNoHistory() {
        val editor = editor()
        var vault = editorVault(VaultMeta(name = VAULT_NAME, historyMaxItems = 0))

        vault = editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }
        vault = editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, "https://gist.github.com") }

        assertTrue(requireNotNull(vault.root.findEntry(GITHUB_ID)).history.isEmpty())
    }

    @Test
    fun withNoHistoryLimitEveryVersionIsKept() {
        val editor = editor()
        var vault = editorVault(VaultMeta(name = VAULT_NAME, historyMaxItems = NO_LIMIT))

        vault = editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }
        vault = editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, "https://gist.github.com") }

        assertEquals(2, requireNotNull(vault.root.findEntry(GITHUB_ID)).history.size)
    }

    @Test
    fun deletingAnEntryCreatesTheRecycleBinAndMovesTheEntryThere() {
        val vault = vaultWithRecycleBinEnabled()

        val edited = editor().delete(vault, GITHUB_ID)

        val binId = requireNotNull(edited.meta.recycleBinGroup)
        val bin = requireNotNull(edited.root.findGroup(binId))
        assertEquals(RECYCLE_BIN_GROUP_NAME, bin.name)
        assertEquals(RECYCLE_BIN_ICON_ID, bin.icon.standardId)
        assertEquals(ROOT_ID, edited.root.parentGroupOf(binId))
        assertTrue(edited.meta.recycleBinEnabled)
        assertEquals(EDITED_AT, edited.meta.recycleBinChanged)
        val entry = requireNotNull(edited.root.findEntry(GITHUB_ID))
        assertEquals(binId, edited.root.parentGroupOf(GITHUB_ID))
        assertEquals(EDITED_AT, entry.times.locationChanged)
        assertEquals(INTERNET_ID, entry.previousParentGroup)
        assertTrue(edited.deletedObjects.isEmpty())
        assertEquals(vault, vaultWithRecycleBinEnabled())
    }

    @Test
    fun deletingIntoARecycleBinThatAlreadyExistsLeavesItsMetadataAlone() {
        val vault = vaultWithRecycleBin()

        val edited = editor().delete(vault, GITHUB_ID)

        assertEquals(RECYCLE_BIN_ID, edited.meta.recycleBinGroup)
        assertEquals(FIXTURE_TIME, edited.meta.recycleBinChanged)
        assertEquals(RECYCLE_BIN_ID, edited.root.parentGroupOf(GITHUB_ID))
        assertEquals(1, edited.root.allGroupIds().count { it == RECYCLE_BIN_ID })
    }

    @Test
    fun deletingWithTheRecycleBinDisabledErasesTheEntryAndRecordsIt() {
        val vault = editorVault(VaultMeta(name = VAULT_NAME, recycleBinEnabled = false))

        val edited = editor().delete(vault, GITHUB_ID)

        assertNull(edited.root.findEntry(GITHUB_ID))
        assertEquals(listOf(DeletedObject(GITHUB_ID, EDITED_AT)), edited.deletedObjects)
        assertNull(edited.meta.recycleBinGroup)
    }

    @Test
    fun deletingAGroupIntoTheRecycleBinTakesItsContentAlong() {
        val edited = editor().delete(binEnabledNested(), INTERNET_ID)

        val binId = requireNotNull(edited.meta.recycleBinGroup)
        assertEquals(binId, edited.root.parentGroupOf(INTERNET_ID))
        assertEquals(INTERNET_ID, edited.root.parentGroupOf(GITHUB_ID))
        assertEquals(INTERNET_ID, edited.root.parentGroupOf(NESTED_ID))
        assertEquals(ROOT_ID, requireNotNull(edited.root.findGroup(INTERNET_ID)).previousParentGroup)
        assertEquals(EDITED_AT, requireNotNull(edited.root.findGroup(INTERNET_ID)).times.locationChanged)
        assertTrue(edited.deletedObjects.isEmpty())
    }

    @Test
    fun erasingAGroupRecordsEveryObjectItHeld() {
        val vault = vaultWithNestedGroup().let { it.copy(meta = VaultMeta(name = VAULT_NAME)) }

        val edited = editor().delete(vault, INTERNET_ID)

        assertNull(edited.root.findGroup(INTERNET_ID))
        assertEquals(
            setOf(INTERNET_ID, GITHUB_ID, MAIL_ID, NESTED_ID, NESTED_ENTRY_ID),
            edited.deletedObjects.map { it.id }.toSet(),
        )
        assertNotNull(edited.root.findGroup(WORK_ID))
    }

    @Test
    fun anObjectAlreadyInTheRecycleBinIsErasedForGood() {
        val vault = vaultWithRecycleBin(entries = listOf(testEntry(OLD_ENTRY_ID, "Old")))

        val edited = editor().delete(vault, OLD_ENTRY_ID)

        assertNull(edited.root.findEntry(OLD_ENTRY_ID))
        assertEquals(listOf(DeletedObject(OLD_ENTRY_ID, EDITED_AT)), edited.deletedObjects)
    }

    @Test
    fun theRecycleBinItselfIsErasedForGood() {
        val vault = vaultWithRecycleBin(entries = listOf(testEntry(OLD_ENTRY_ID, "Old")))

        val edited = editor().delete(vault, RECYCLE_BIN_ID)

        assertNull(edited.root.findGroup(RECYCLE_BIN_ID))
        assertEquals(setOf(RECYCLE_BIN_ID, OLD_ENTRY_ID), edited.deletedObjects.map { it.id }.toSet())
    }

    @Test
    fun restoringAnEntryPutsItBackWhereItCameFrom() {
        val deleted = editor().delete(vaultWithRecycleBinEnabled(), GITHUB_ID)

        val restored = editor().restore(deleted, GITHUB_ID)

        assertEquals(INTERNET_ID, restored.root.parentGroupOf(GITHUB_ID))
        val entry = requireNotNull(restored.root.findEntry(GITHUB_ID))
        assertNull(entry.previousParentGroup)
        assertEquals(EDITED_AT, entry.times.locationChanged)
    }

    @Test
    fun restoringAnObjectWithoutAPreviousParentUsesTheRoot() {
        val vault = vaultWithRecycleBin(entries = listOf(testEntry(OLD_ENTRY_ID, "Old")))

        val restored = editor().restore(vault, OLD_ENTRY_ID)

        assertEquals(ROOT_ID, restored.root.parentGroupOf(OLD_ENTRY_ID))
    }

    @Test
    fun restoringToAGroupThatIsGoneUsesTheRoot() {
        val gone = testEntry(OLD_ENTRY_ID, "Old").copy(previousParentGroup = id(999))
        val vault = vaultWithRecycleBin(entries = listOf(gone))

        val restored = editor().restore(vault, OLD_ENTRY_ID)

        assertEquals(ROOT_ID, restored.root.parentGroupOf(OLD_ENTRY_ID))
    }

    @Test
    fun restoringAGroupTakesItsContentBack() {
        val deleted = editor().delete(binEnabledNested(), INTERNET_ID)

        val restored = editor().restore(deleted, INTERNET_ID)

        assertEquals(ROOT_ID, restored.root.parentGroupOf(INTERNET_ID))
        assertEquals(INTERNET_ID, restored.root.parentGroupOf(GITHUB_ID))
        assertEquals(INTERNET_ID, restored.root.parentGroupOf(NESTED_ID))
    }

    @Test
    fun emptyingTheRecycleBinErasesItsContentAndKeepsTheGroup() {
        val nested = testGroup(NESTED_ID, "Nested", entries = listOf(testEntry(NESTED_ENTRY_ID, "Nested")))
        val vault =
            vaultWithRecycleBin(entries = listOf(testEntry(OLD_ENTRY_ID, "Old"), testEntry(OLDER_ENTRY_ID, "Older")))
                .withChild(RECYCLE_BIN_ID, nested)

        val emptied = editor().emptyRecycleBin(vault)

        val bin = requireNotNull(emptied.root.findGroup(RECYCLE_BIN_ID))
        assertTrue(bin.entries.isEmpty())
        assertTrue(bin.groups.isEmpty())
        assertEquals(
            setOf(OLD_ENTRY_ID, OLDER_ENTRY_ID, NESTED_ID, NESTED_ENTRY_ID),
            emptied.deletedObjects.map { it.id }.toSet(),
        )
        assertNotNull(emptied.root.findEntry(GITHUB_ID))
    }

    @Test
    fun emptyingARecycleBinWithNothingInItChangesNothing() {
        val vault = vaultWithRecycleBin()

        assertSame(vault, editor().emptyRecycleBin(vault))
    }

    @Test
    fun theRootGroupCannotBeMovedOrDeleted() {
        val vault = editorVault()
        val editor = editor()

        assertThrows(IllegalArgumentException::class.java) { editor.delete(vault, ROOT_ID) }
        assertThrows(IllegalArgumentException::class.java) { editor.move(vault, ROOT_ID, INTERNET_ID) }
    }

    @Test
    fun aGroupCannotBeMovedIntoItselfOrIntoItsDescendants() {
        val vault = vaultWithNestedGroup()
        val editor = editor()

        assertThrows(IllegalArgumentException::class.java) { editor.move(vault, INTERNET_ID, INTERNET_ID) }
        assertThrows(IllegalArgumentException::class.java) { editor.move(vault, INTERNET_ID, NESTED_ID) }

        val moved = editor.move(vault, NESTED_ID, WORK_ID)
        assertEquals(WORK_ID, moved.root.parentGroupOf(NESTED_ID))
    }

    @Test
    fun movingAnEntryRecordsWhereItWasPut() {
        val edited = editor().move(editorVault(), GITHUB_ID, WORK_ID)

        assertEquals(WORK_ID, edited.root.parentGroupOf(GITHUB_ID))
        assertEquals(EDITED_AT, requireNotNull(edited.root.findEntry(GITHUB_ID)).times.locationChanged)
    }

    @Test
    fun movingAGroupTakesEverythingInsideIt() {
        val edited = editor().move(vaultWithNestedGroup(), INTERNET_ID, WORK_ID)

        assertEquals(WORK_ID, edited.root.parentGroupOf(INTERNET_ID))
        assertEquals(INTERNET_ID, edited.root.parentGroupOf(GITHUB_ID))
        assertEquals(INTERNET_ID, edited.root.parentGroupOf(NESTED_ID))
        assertEquals(EDITED_AT, requireNotNull(edited.root.findGroup(INTERNET_ID)).times.locationChanged)
    }

    @Test
    fun anUnknownIdIsAUsageError() {
        val vault = editorVault()
        val editor = editor()
        val unknown = id(999)

        assertThrows(IllegalArgumentException::class.java) { editor.addEntry(vault, unknown, "New") }
        assertThrows(IllegalArgumentException::class.java) { editor.addGroup(vault, unknown, "New") }
        assertThrows(IllegalArgumentException::class.java) { editor.editEntry(vault, unknown) { it } }
        assertThrows(IllegalArgumentException::class.java) { editor.move(vault, unknown, WORK_ID) }
        assertThrows(IllegalArgumentException::class.java) { editor.move(vault, GITHUB_ID, unknown) }
        assertThrows(IllegalArgumentException::class.java) { editor.delete(vault, unknown) }
        assertThrows(IllegalArgumentException::class.java) { editor.restore(vault, unknown) }
    }

    @Test
    fun noOperationChangesTheVaultItWasGiven() {
        val vault = editorVault()
        val editor = editor()

        editor.addEntry(vault, WORK_ID, "New")
        editor.addGroup(vault, WORK_ID, "Sub")
        editor.editEntry(vault, GITHUB_ID) { it.withField(Entry.URL, GITHUB_URL) }
        editor.move(vault, GITHUB_ID, WORK_ID)
        editor.move(vault, INTERNET_ID, WORK_ID)
        editor.delete(vault, GITHUB_ID)
        editor.delete(vault, INTERNET_ID)
        editor.restore(vault, GITHUB_ID)
        editor.emptyRecycleBin(vault)

        assertEquals(editorVault(), vault)
    }

    @Test
    fun randomSequencesKeepEveryObjectOrRecordItsDeletion() {
        for (seed in SEEDS) {
            val random = Random(seed)
            val editor = editor()
            var vault = editorVault(VaultMeta(name = VAULT_NAME, historyMaxItems = 2))
            val everSeen = vault.root.allIds().toMutableSet()

            repeat(ROUNDS) {
                vault = randomStep(editor, vault, random)
                everSeen += vault.root.allIds()
            }

            assertNothingWasLost(seed, vault, everSeen)
        }
    }

    private fun assertNothingWasLost(
        seed: Long,
        vault: Vault,
        everSeen: Set<UUID>,
    ) {
        val inTree = vault.root.allIds()
        val recorded = vault.deletedObjects.map { it.id }.toSet()
        for (objectId in everSeen) {
            assertTrue(
                "seed $seed: $objectId left the tree without a deletion record",
                objectId in inTree || objectId in recorded,
            )
        }
        assertEquals(
            "seed $seed: an object is in two places of the tree",
            inTree.size,
            vault.root.allGroupIds().size + vault.root.entryIdsInTree().size,
        )
        assertEquals("seed $seed: the root changed", ROOT_ID, vault.root.id)
    }

    /** One random edit: adds, moves, deletes, restores or empties the bin. */
    private fun randomStep(
        editor: VaultEditor,
        vault: Vault,
        random: Random,
    ): Vault {
        val groups = vault.root.allGroupIds().toList()
        val entries = vault.root.entryIdsInTree().toList()
        val movable = groups.filter { it != ROOT_ID }
        val entry = entries.randomOrNull(random)
        val anyObject = entry ?: movable.randomOrNull(random)
        return when (random.nextInt(STEPS)) {
            ADD_ENTRY -> editor.addEntry(vault, groups.random(random), "Added entry")
            ADD_GROUP -> editor.addGroup(vault, groups.random(random), "Added group")
            EDIT_ENTRY -> if (entry == null) vault else editUrl(editor, vault, entry)
            MOVE -> moveSomewhereLegal(editor, vault, anyObject, random)
            DELETE_ENTRY -> if (entry == null) vault else editor.delete(vault, entry)
            DELETE_GROUP -> if (movable.isEmpty()) vault else editor.delete(vault, movable.random(random))
            RESTORE -> if (anyObject == null) vault else editor.restore(vault, anyObject)
            else -> editor.emptyRecycleBin(vault)
        }
    }

    private fun editUrl(
        editor: VaultEditor,
        vault: Vault,
        entryId: UUID,
    ): Vault = editor.editEntry(vault, entryId) { it.withField(Entry.URL, GITHUB_URL) }

    private fun moveSomewhereLegal(
        editor: VaultEditor,
        vault: Vault,
        objectId: UUID?,
        random: Random,
    ): Vault {
        if (objectId == null) return vault
        val group = vault.root.findGroup(objectId)
        val forbidden = group?.allGroupIds().orEmpty()
        val targets = vault.root.allGroupIds().filter { it !in forbidden }
        val target = targets.randomOrNull(random) ?: return vault
        return editor.move(vault, objectId, target)
    }

    private companion object {
        const val STEPS = 8
        const val ROUNDS = 24
        const val NO_LIMIT = -1
        const val ADD_ENTRY = 0
        const val ADD_GROUP = 1
        const val EDIT_ENTRY = 2
        const val MOVE = 3
        const val DELETE_ENTRY = 4
        const val DELETE_GROUP = 5
        const val RESTORE = 6
        val SEEDS = listOf(1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L)
    }
}

/** An id from the fixture's low range, so tests can name the objects they look for. */
internal fun id(number: Long): UUID = UUID(0L, number)

/** A clock that stands still: every edit in a test gets the same instant. */
internal class TestClock(
    private var now: Instant = EDITED_AT,
) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}

/** An id source that hands out predictable ids, the way an injected random one would. */
internal class TestIds(
    private var next: Long = FIRST_GENERATED_ID,
) : () -> UUID {
    override fun invoke(): UUID = id(next++)
}

/** Times of a fixture object, all set to [at]. */
internal fun testTimes(at: Instant): Times =
    Times(created = at, lastAccessed = at, lastModified = at, locationChanged = at)

/** The vault with [child] added inside the group [parentId]. */
internal fun Vault.withChild(
    parentId: UUID,
    child: Group,
): Vault = copy(root = root.mapGroup(parentId) { parent -> parent.copy(groups = parent.groups + child) })

internal fun testGroup(
    id: UUID,
    name: String,
    groups: List<Group> = emptyList(),
    entries: List<Entry> = emptyList(),
): Group = Group(id = id, name = name, times = testTimes(FIXTURE_TIME), groups = groups, entries = entries)

internal fun testEntry(
    id: UUID,
    title: String,
    fields: Map<String, FieldValue> = emptyMap(),
): Entry = Entry(id = id, times = testTimes(FIXTURE_TIME), fields = mapOf(Entry.TITLE to FieldValue(title)) + fields)

internal fun testVault(
    root: Group,
    meta: VaultMeta = VaultMeta(name = VAULT_NAME),
): Vault = Vault(testFormat(), meta, root)

/** A small tree to edit: the vault with an Internet group (GitHub and Mail) and a Work group (Jira). */
internal fun editorVault(meta: VaultMeta = VaultMeta(name = VAULT_NAME)): Vault = testVault(fixtureRoot(), meta)

/** The tree of [editorVault], for tests that need to build on it. */
internal fun fixtureRoot(): Group =
    testGroup(
        ROOT_ID,
        VAULT_NAME,
        groups =
            listOf(
                testGroup(
                    INTERNET_ID,
                    "Internet",
                    entries = listOf(testEntry(GITHUB_ID, "GitHub"), testEntry(MAIL_ID, "Mail")),
                ),
                testGroup(WORK_ID, "Work", entries = listOf(testEntry(JIRA_ID, "Jira"))),
            ),
    )

/** The fixture with a recycle bin group already in place under the root. */
internal fun vaultWithRecycleBin(entries: List<Entry> = emptyList()): Vault {
    val meta =
        VaultMeta(
            name = VAULT_NAME,
            recycleBinEnabled = true,
            recycleBinGroup = RECYCLE_BIN_ID,
            recycleBinChanged = FIXTURE_TIME,
        )
    val bin = testGroup(RECYCLE_BIN_ID, RECYCLE_BIN_GROUP_NAME, entries = entries)
    return editorVault(meta).withChild(ROOT_ID, bin)
}

/** The fixture with the recycle bin enabled but not created yet, as a vault wrote it before the first deletion. */
internal fun vaultWithRecycleBinEnabled(): Vault = editorVault(VaultMeta(name = VAULT_NAME, recycleBinEnabled = true))

/** The fixture with a group nested inside Internet. */
internal fun vaultWithNestedGroup(meta: VaultMeta = VaultMeta(name = VAULT_NAME)): Vault =
    editorVault(meta).withChild(
        INTERNET_ID,
        testGroup(NESTED_ID, "Nested", entries = listOf(testEntry(NESTED_ENTRY_ID, "Nested"))),
    )

/** The nested fixture with the recycle bin enabled but not created yet. */
internal fun binEnabledNested(): Vault = vaultWithNestedGroup(VaultMeta(name = VAULT_NAME, recycleBinEnabled = true))

/** The group holding [id] (an entry or a group), or null when the tree does not hold it. */
internal fun Group.parentGroupOf(id: UUID): UUID? = pathTo(id).lastOrNull()?.id

/** An editor with a clock that stands still and predictable ids. */
internal fun editor(): VaultEditor = VaultEditor(TestClock(), TestIds())

/** The vault's own format: anything the editor keeps as it was. */
internal fun testFormat(): VaultFormat =
    VaultFormat(FormatVersion(KDBX_MAJOR, KDBX_MINOR), Cipher.Aes256, Kdf.AesKdf(AES_ROUNDS))

private fun Entry.withField(
    name: String,
    value: String,
): Entry = copy(fields = fields + (name to FieldValue(value)))

private fun Group.entryIdsInTree(): Set<UUID> =
    buildSet {
        entries.forEach { add(it.id) }
        groups.forEach { addAll(it.entryIdsInTree()) }
    }

private const val KDBX_MAJOR = 4
private const val KDBX_MINOR = 1
private const val AES_ROUNDS = 1_000L
private const val GITHUB_URL = "https://github.com"
private const val CERT_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
