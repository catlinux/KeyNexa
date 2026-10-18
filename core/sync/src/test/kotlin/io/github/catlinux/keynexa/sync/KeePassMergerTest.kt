package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.CustomDataValue
import io.github.catlinux.keynexa.vault.CustomIcon
import io.github.catlinux.keynexa.vault.DeletedObject
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.ProtectableField
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultMeta
import io.github.catlinux.keynexa.vault.XmlElement
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenarios of the KeePass-style merge: which version of an object is current, where it ends up in the tree, what
 * the history keeps, and how deletions, metadata and binaries are combined.
 */
class KeePassMergerTest {
    private val internet = uuid("$ROOT/Internet")
    private val work = uuid("$ROOT/Work")
    private val archive = uuid("$ROOT/Archive")
    private val github = uuid("$ROOT/Internet/github")
    private val mail = uuid("$ROOT/Internet/mail")
    private val rootId = uuid(ROOT)

    /** Two groups with three entries; one entry carries an attachment whose binary the vault holds. */
    private fun sample(): Vault =
        vault {
            group("Internet", at(1)) {
                entry("github", at(2)) {
                    userName = "ana"
                    password = "v1"
                    attachments = listOf(attachment(CERT))
                }
                entry("mail", at(3)) { url = "https://mail.example" }
            }
            group("Work", at(4)) {
                entry("jira", at(5)) { notes = "n1" }
            }
            group("Archive", at(6))
        }.withBinaries(binaries(CERT))

    @Test
    fun identicalVaultsMergeToTheSameVault() {
        val vault = sample()

        val merged = mergeVaults(vault, vault)

        assertEquals(vault, merged)
        assertEquals(vault.meta, merged.meta)
        assertEquals(vault.deletedObjects, merged.deletedObjects)
    }

    @Test
    fun anEntryAddedOnEachSideIsKeptOnBothSides() {
        val ours = sample().addEntry(internet, newEntry("ours-new", at(10)))
        val theirs = sample().addEntry(work, newEntry("theirs-new", at(11)))

        val merged = mergeVaults(ours, theirs)

        assertNotNull(merged.findEntry(uuid("ours-new")))
        assertNotNull(merged.findEntry(uuid("theirs-new")))
        assertEquals(internet, merged.parentOfEntry(uuid("ours-new")))
        assertEquals(work, merged.parentOfEntry(uuid("theirs-new")))
    }

    @Test
    fun anEntryEditedOnlyElsewhereWinsAndTheVersionHereGoesToTheHistory() {
        val ours = sample()
        val theirs = sample().editEntry(github, at(10)) { it.withField(PASSWORD, "v2") }

        val entry = requireNotNull(mergeVaults(ours, theirs).findEntry(github))

        assertEquals("v2", entry.fields[PASSWORD]?.value)
        assertEquals(listOf("v1"), entry.history.map { it.fields[PASSWORD]?.value })
    }

    @Test
    fun ofTwoEditsTheNewerWinsAndTheOtherGoesToTheHistory() {
        val ours = sample().editEntry(github, at(10)) { it.withField(PASSWORD, "ours") }
        val theirs = sample().editEntry(github, at(11)) { it.withField(PASSWORD, "theirs") }

        val entry = requireNotNull(mergeVaults(ours, theirs).findEntry(github))

        assertEquals("theirs", entry.fields[PASSWORD]?.value)
        // Neither side pushed the version it replaced into a history, so only the losing version is left.
        assertEquals(listOf("ours"), entry.history.map { it.fields[PASSWORD]?.value })
    }

    @Test
    fun anExactTieWithDifferentContentKeepsOursAndHistoriesTheirs() {
        val ours = sample().editEntry(github, at(10)) { it.withField(PASSWORD, "ours") }
        val theirs = sample().editEntry(github, at(10)) { it.withField(PASSWORD, "theirs") }

        val entry = requireNotNull(mergeVaults(ours, theirs).findEntry(github))

        assertEquals("ours", entry.fields[PASSWORD]?.value)
        assertTrue(entry.history.any { it.fields[PASSWORD]?.value == "theirs" })
    }

    @Test
    fun historiesAreUnitedWithoutDuplicatesAndOrderedOldestFirst() {
        val ours = sample().newVersion(github, at(9)) { it.withField(PASSWORD, "v3") }
        val theirs = sample().newVersion(github, at(11)) { it.withField(PASSWORD, "v4") }

        val entry = requireNotNull(mergeVaults(ours, theirs).findEntry(github))

        assertEquals("v4", entry.fields[PASSWORD]?.value)
        assertEquals(listOf("v1", "v3"), entry.history.map { it.fields[PASSWORD]?.value })
        assertEquals(listOf(at(2), at(9)), entry.history.map { it.times.lastModified })
    }

    @Test
    fun aVersionBothSidesShareIsNotDuplicated() {
        // Both sides made the same edit before diverging: the common version appears once.
        val common = sample().newVersion(github, at(9)) { it.withField(PASSWORD, "v2") }
        val ours = common.newVersion(github, at(12)) { it.withField(PASSWORD, "v3") }
        val theirs = common.editEntry(github, at(13)) { it.withField(PASSWORD, "v4") }

        val entry = requireNotNull(mergeVaults(ours, theirs).findEntry(github))

        assertEquals("v4", entry.fields[PASSWORD]?.value)
        assertEquals(listOf("v1", "v2", "v3"), entry.history.map { it.fields[PASSWORD]?.value })
    }

    @Test
    fun theHistoryIsTrimmedToTheMergedItemLimit() {
        val ours = versioned().withMeta(VaultMeta(name = ROOT, historyMaxItems = 2, settingsChanged = at(20)))

        val entry = requireNotNull(mergeVaults(ours, versioned()).findEntry(github))

        assertEquals(listOf(at(5), at(9)), entry.history.map { it.times.lastModified })
    }

    @Test
    fun aHistoryLimitOfZeroDropsTheWholeHistory() {
        val ours = versioned().withMeta(VaultMeta(name = ROOT, historyMaxItems = 0))

        val entry = requireNotNull(mergeVaults(ours, versioned()).findEntry(github))

        assertTrue(entry.history.isEmpty())
        assertEquals("v4", entry.fields[PASSWORD]?.value)
    }

    @Test
    fun aHistoryLimitOfMinusOneKeepsEveryVersion() {
        val ours = versioned().withMeta(VaultMeta(name = ROOT, historyMaxItems = -1))

        val entry = requireNotNull(mergeVaults(ours, versioned()).findEntry(github))

        assertEquals(listOf(at(2), at(5), at(9)), entry.history.map { it.times.lastModified })
    }

    @Test
    fun theHistoryIsTrimmedToTheMergedSizeLimit() {
        val ours = versioned()
        val perVersion = sizeOf(requireNotNull(ours.findEntry(github)).history.first())
        val limited = ours.withMeta(VaultMeta(name = ROOT, historyMaxSizeBytes = perVersion * 2 + 1))

        val entry = requireNotNull(mergeVaults(limited, versioned()).findEntry(github))

        assertEquals(listOf(at(5), at(9)), entry.history.map { it.times.lastModified })
    }

    @Test
    fun anEntryDeletedElsewhereAndUntouchedHereDisappearsWithItsRecord() {
        val ours = sample()
        val theirs = sample().deleteEntry(github, at(10))

        val merged = mergeVaults(ours, theirs)

        assertNull(merged.findEntry(github))
        assertEquals(listOf(DeletedObject(github, at(10))), merged.deletedObjects)
    }

    @Test
    fun anEntryDeletedElsewhereButEditedHereAfterwardsComesBackWithoutItsRecord() {
        val ours = sample().editEntry(github, at(11)) { it.withField(PASSWORD, "v2") }
        val theirs = sample().deleteEntry(github, at(10))

        val merged = mergeVaults(ours, theirs)

        assertEquals("v2", requireNotNull(merged.findEntry(github)).fields[PASSWORD]?.value)
        assertTrue(merged.deletedObjects.isEmpty())
    }

    @Test
    fun anEntryModifiedAfterItsOwnDeletionRecordComesBack() {
        val ours = sample()
        val theirs =
            sample()
                .editEntry(github, at(11)) { it.withField(PASSWORD, "v2") }
                .withDeleted(listOf(DeletedObject(github, at(10))))

        val merged = mergeVaults(ours, theirs)

        assertEquals("v2", requireNotNull(merged.findEntry(github)).fields[PASSWORD]?.value)
        assertTrue(merged.deletedObjects.isEmpty())
    }

    @Test
    fun aNewEntryInsideAGroupTheOtherSideDeletedRisesToTheNearestLivingAncestor() {
        // The group keeps its own last-modification time, older than the deletion, so it stays deleted; the entry
        // added here is new, so it survives and looks for the closest living group above the one it was added to.
        val ours = sample().addEntry(internet, newEntry("fresh", at(11)))
        val theirs = sample().deleteGroup(internet, at(10))

        val merged = mergeVaults(ours, theirs)

        assertNotNull(merged.findEntry(uuid("fresh")))
        assertEquals(rootId, merged.parentOfEntry(uuid("fresh")))
        assertNull(merged.findGroup(internet))
        assertNull(merged.findEntry(github))
        assertNull(merged.findEntry(mail))
    }

    @Test
    fun thePlaceOfAnEntryFollowsTheNewestLocationChange() {
        val ours = sample().moveEntry(github, to = archive, at = at(10))
        val theirs = sample().moveEntry(github, to = work, at = at(11))

        val merged = mergeVaults(ours, theirs)

        assertEquals(work, merged.parentOfEntry(github))
    }

    @Test
    fun aMoveHereBeatsAnOlderMoveThere() {
        val ours = sample().moveEntry(github, to = archive, at = at(12))
        val theirs = sample().moveEntry(github, to = work, at = at(11))

        val merged = mergeVaults(ours, theirs)

        assertEquals(archive, merged.parentOfEntry(github))
    }

    @Test
    fun anEntryMovedIntoAGroupTheOtherSideDeletedRisesToTheNearestLivingAncestor() {
        val ours = sample().moveEntry(github, to = work, at = at(10))
        val theirs = sample().deleteGroup(work, at = at(11))

        val merged = mergeVaults(ours, theirs)

        assertNull(merged.findGroup(work))
        assertEquals(rootId, merged.parentOfEntry(github))
    }

    @Test
    fun crossedMovesThatWouldFormACycleKeepBothGroupsAndTheirEntries() {
        val block = {
            vault {
                group("A", at(1)) { entry("a-entry", at(2)) { notes = "a" } }
                group("B", at(3)) { entry("b-entry", at(4)) { notes = "b" } }
            }
        }
        val ours = block().moveGroup(uuid("$ROOT/A"), to = uuid("$ROOT/B"), at = at(10))
        val theirs = block().moveGroup(uuid("$ROOT/B"), to = uuid("$ROOT/A"), at = at(11))

        val merged = mergeVaults(ours, theirs)

        assertEquals(setOf(rootId, uuid("$ROOT/A"), uuid("$ROOT/B")), merged.root.groupIds())
        assertEquals(setOf(uuid("$ROOT/A/a-entry"), uuid("$ROOT/B/b-entry")), merged.root.entryIds())
        // Everything is still reachable by walking down from the root, whatever the cycle was broken into.
        assertNotNull(merged.findGroup(uuid("$ROOT/A")))
        assertNotNull(merged.findGroup(uuid("$ROOT/B")))
        assertNotNull(merged.findEntry(uuid("$ROOT/A/a-entry")))
        assertNotNull(merged.findEntry(uuid("$ROOT/B/b-entry")))
    }

    @Test
    fun conflictingGroupPropertiesFollowTheNewestModification() {
        val ours = sample().editGroup(internet, at(10)) { it.copy(name = "Red", notes = "mine") }
        val theirs = sample().editGroup(internet, at(11)) { it.copy(name = "Net", notes = "theirs") }

        val group = requireNotNull(mergeVaults(ours, theirs).findGroup(internet))

        assertEquals("Net", group.name)
        assertEquals("theirs", group.notes)
    }

    @Test
    fun childrenKeepOurOrderAndNewOnesFromThereGoLast() {
        val ours = twoChildren()
        val theirs = twoChildren().addEntry(uuid("$ROOT/G"), newEntry("c", at(14)))

        val group = requireNotNull(mergeVaults(ours, theirs).findGroup(uuid("$ROOT/G")))

        assertEquals(listOf("a", "b", "c"), group.entries.map { it.fields[TITLE]?.value })
    }

    @Test
    fun aDifferentOrderThereDoesNotReorderOurChildren() {
        val ours =
            vault {
                group("G", at(1)) {
                    entry("a", at(2))
                    entry("b", at(3))
                }
            }
        val theirs =
            vault {
                group("G", at(1)) {
                    entry("b", at(3))
                    entry("a", at(2))
                }
            }

        val group = requireNotNull(mergeVaults(ours, theirs).findGroup(uuid("$ROOT/G")))

        assertEquals(listOf("a", "b"), group.entries.map { it.fields[TITLE]?.value })
    }

    @Test
    fun metadataFieldsFollowTheirOwnChangeTimes() {
        val ours =
            sample().withMeta(
                VaultMeta(
                    name = "Mine",
                    nameChanged = at(5),
                    description = "d0",
                    descriptionChanged = at(1),
                    defaultUser = "ana",
                    defaultUserChanged = at(1),
                ),
            )
        val theirs =
            sample().withMeta(
                VaultMeta(
                    name = "Theirs",
                    nameChanged = at(6),
                    description = "d1",
                    descriptionChanged = at(2),
                    defaultUser = "bob",
                    defaultUserChanged = at(0),
                ),
            )

        val meta = mergeVaults(ours, theirs).meta

        assertEquals("Theirs", meta.name)
        assertEquals("d1", meta.description)
        assertEquals("ana", meta.defaultUser)
    }

    @Test
    fun theRecycleBinAndTemplatesFollowTheirOwnChangeTimes() {
        val bin = uuid("recycle-bin")
        val templates = uuid("templates")
        val ours =
            sample().withMeta(
                VaultMeta(
                    name = ROOT,
                    recycleBinEnabled = false,
                    recycleBinChanged = at(5),
                    entryTemplatesGroup = templates,
                    entryTemplatesGroupChanged = at(6),
                ),
            )
        val theirs =
            sample().withMeta(
                VaultMeta(
                    name = ROOT,
                    recycleBinEnabled = true,
                    recycleBinGroup = bin,
                    recycleBinChanged = at(6),
                    entryTemplatesGroup = null,
                    entryTemplatesGroupChanged = at(5),
                ),
            )

        val meta = mergeVaults(ours, theirs).meta

        assertTrue(meta.recycleBinEnabled)
        assertEquals(bin, meta.recycleBinGroup)
        assertEquals(templates, meta.entryTemplatesGroup)
    }

    @Test
    fun generalSettingsFollowSettingsChanged() {
        val ours =
            sample().withMeta(
                VaultMeta(
                    name = ROOT,
                    settingsChanged = at(5),
                    color = "red",
                    historyMaxItems = 3,
                    protectedFields = setOf(ProtectableField.Password),
                ),
            )
        val theirs =
            sample().withMeta(
                VaultMeta(
                    name = ROOT,
                    settingsChanged = at(6),
                    color = "blue",
                    historyMaxItems = 7,
                    protectedFields = setOf(ProtectableField.Title, ProtectableField.Password),
                ),
            )

        val meta = mergeVaults(ours, theirs).meta

        assertEquals("blue", meta.color)
        assertEquals(7, meta.historyMaxItems)
        assertEquals(setOf(ProtectableField.Title, ProtectableField.Password), meta.protectedFields)
    }

    @Test
    fun customIconsAndCustomDataAreUnitedWithTheNewestWinning() {
        val shared = uuid("shared")
        val ours =
            sample().withMeta(
                VaultMeta(
                    name = ROOT,
                    customIcons = mapOf(shared to CustomIcon(byteArrayOf(1), lastModified = at(2))),
                    customData = mapOf("k" to CustomDataValue("mine", at(3))),
                ),
            )
        val theirs =
            sample().withMeta(
                VaultMeta(
                    name = ROOT,
                    customIcons =
                        mapOf(
                            shared to CustomIcon(byteArrayOf(2), lastModified = at(4)),
                            uuid("theirs-only") to CustomIcon(byteArrayOf(3)),
                        ),
                    customData = mapOf("k" to CustomDataValue("theirs", at(5)), "theirs-only" to CustomDataValue("t")),
                ),
            )

        val meta = mergeVaults(ours, theirs).meta

        assertEquals(setOf(shared, uuid("theirs-only")), meta.customIcons.keys)
        assertArrayEquals(byteArrayOf(2), meta.customIcons[shared]?.data)
        assertEquals(setOf("k", "theirs-only"), meta.customData.keys)
        assertEquals("theirs", meta.customData["k"]?.value)
    }

    @Test
    fun binariesAreUnitedAndTheUnreferencedOneIsDropped() {
        val ours = sample().withBinaries(binaries(CERT, "nobody-uses-this"))
        val theirs =
            sample()
                .withBinaries(binaries(CERT, "theirs-only"))
                .editEntry(github, at(10)) { it.withAttachment("theirs-only") }

        val merged = mergeVaults(ours, theirs)

        assertEquals(setOf(binaryId(CERT), binaryId("theirs-only")), merged.binaries.keys)
    }

    @Test
    fun aBinaryOnlyAHistoryVersionUsesIsKept() {
        val ours = sample().editEntry(github, at(10)) { it.withField(PASSWORD, "v2") }
        val theirs = sample()

        val merged = mergeVaults(ours, theirs)

        assertEquals(binaries(CERT).keys, merged.binaries.keys)
        assertTrue(requireNotNull(merged.findEntry(github)).history.any { it.attachments.isNotEmpty() })
    }

    @Test
    fun aBinaryNobodyReferencesIsDroppedEvenWhenAVaultIsMergedWithItself() {
        // Two requirements meet here: identical vaults merge to themselves, and a binary nobody references is
        // dropped. A vault that holds such a binary comes out one binary lighter, so it is not equal to ours.
        val withOrphan = sample().withBinaries(binaries(CERT, ORPHAN))

        val merged = mergeVaults(withOrphan, withOrphan)

        assertEquals(binaries(CERT).keys, merged.binaries.keys)
        assertNotEquals(withOrphan, merged)
    }

    @Test
    fun aDeletionRecordForTheRootGroupIsIgnored() {
        // No manager deletes the root group, so this record can only come from a damaged or hostile file.
        val ours = sample()
        val theirs = sample().withDeleted(listOf(DeletedObject(uuid(ROOT), at(30))))

        val merged = mergeVaults(ours, theirs)

        assertNotNull(merged.findGroup(uuid(ROOT)))
        assertTrue(merged.deletedObjects.none { it.id == uuid(ROOT) })
    }

    @Test
    fun vaultsWithDifferentRootsAreUnmergeable() {
        val ours = vault { group("G", at(1)) }
        val theirs = vault(root = "Other") { group("G", at(1)) }

        val result = KeePassMerger().merge(ours, theirs)

        assertTrue(result is MergeResult.Unmergeable)
        assertTrue((result as MergeResult.Unmergeable).reason.isNotEmpty())
    }

    @Test
    fun unknownElementsOfOursAreKeptAndTheirsOnlyFillTheGap() {
        val mine = XmlElement("Mine")
        val other = XmlElement("Theirs")
        val ours = sample().withUnknown(listOf(mine)).withUnknownRoot(listOf(mine))
        val theirs = sample().withUnknown(listOf(other)).withUnknownRoot(listOf(other))

        val merged = mergeVaults(ours, theirs)
        val filled = mergeVaults(sample(), theirs)

        assertEquals(listOf(mine), merged.unknownElements)
        assertEquals(listOf(mine), merged.unknownRootElements)
        assertEquals(listOf(other), filled.unknownElements)
        assertEquals(listOf(other), filled.unknownRootElements)
    }

    /** The sample with four versions of the github entry: three in its history and the current one. */
    private fun versioned(): Vault =
        sample()
            .newVersion(github, at(5)) { it.withField(PASSWORD, "v2") }
            .newVersion(github, at(9)) { it.withField(PASSWORD, "v3") }
            .newVersion(github, at(13)) { it.withField(PASSWORD, "v4") }

    private fun twoChildren(): Vault =
        vault {
            group("G", at(1)) {
                entry("a", at(2))
                entry("b", at(3))
            }
        }

    /** What the merge charges a history version: its field names and values and its tags, in UTF-16. */
    private fun sizeOf(entry: Entry): Int =
        entry.fields.entries.sumOf { (name, value) -> (name.length + value.value.length) * 2 } +
            entry.tags.sumOf { it.length * 2 }

    private companion object {
        const val CERT = "cert.pem"
        const val ORPHAN = "orphan.bin"
    }
}
