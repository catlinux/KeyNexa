package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.BinaryId
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Vault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Random
import java.util.UUID

/**
 * Invariants that the merge must keep whatever each side did: nothing is lost, the tree stays a tree with one place
 * per object, attachments and binaries stay consistent, and merging is symmetric and idempotent. The pairs of
 * vaults are random but reproducible from their seed.
 */
class KeePassMergerPropertiesTest {
    @Test
    fun theMergedTreeHoldsExactlyTheObjectsThatSurvive() {
        for (seed in SEEDS) {
            val pair = RandomPair(seed)
            val records = pair.records()
            val groups = pair.merged.root.groupIds()
            val entries = pair.merged.root.entryIds()

            assertEquals("seed $seed: groups in the tree", pair.aliveGroups(records), groups)
            assertEquals("seed $seed: entries in the tree", pair.aliveEntries(records), entries)
        }
    }

    @Test
    fun everyObjectThatLeavesTheTreeHasANewEnoughDeletionRecord() {
        for (seed in SEEDS) {
            val pair = RandomPair(seed)
            val records = pair.records()
            val inTree = pair.merged.root.allIds()

            for (id in pair.ours.root.allIds() + pair.theirs.root.allIds()) {
                if (id in inTree) continue
                val record = records[id] ?: error("seed $seed: object $id was lost without a deletion record")
                assertTrue("seed $seed: record of $id is older than the object", record >= pair.lastModified(id))
            }
        }
    }

    @Test
    fun theMergedTreeHasNoRepeatedObject() {
        for (seed in SEEDS) {
            val pair = RandomPair(seed)
            val groups = pair.merged.root.groupIds()
            val entries = pair.merged.root.entryIds()
            val objects = groups.size + entries.size
            val inTree = pair.merged.root.allIds()

            assertEquals("seed $seed: an object appears twice in the merged tree", objects, inTree.size)
        }
    }

    @Test
    fun binariesMatchTheAttachmentsOfTheMergedTree() {
        for (seed in SEEDS) {
            val merged = RandomPair(seed).merged
            val referenced = referencedBinaries(merged.root)

            for (id in referenced) assertTrue("seed $seed: binary $id is missing", id in merged.binaries)
            assertEquals("seed $seed: binaries nobody references", referenced, merged.binaries.keys)
        }
    }

    @Test
    fun mergingTheOtherWayRoundKeepsTheSameObjectsAndVersions() {
        for (seed in SEEDS) {
            val pair = RandomPair(seed)
            val forward = pair.merged
            val backward = mergeVaults(pair.theirs, pair.ours)

            assertEquals("seed $seed: live objects differ", forward.root.allIds(), backward.root.allIds())
            assertEquals(
                "seed $seed: deletion records differ",
                forward.deletedObjects.map { it.id }.toSet(),
                backward.deletedObjects.map { it.id }.toSet(),
            )
            for (id in forward.root.entryIds()) {
                assertEquals("seed $seed: current version of $id differs", current(forward, id), current(backward, id))
            }
        }
    }

    @Test
    fun mergingAgainChangesNothing() {
        for (seed in SEEDS) {
            val pair = RandomPair(seed)
            val merged = pair.merged

            assertEquals("seed $seed: merge(m, m) is not m", merged, mergeVaults(merged, merged))
            val again = mergeVaults(merged, pair.theirs)
            assertEquals("seed $seed: live objects changed", merged.root.allIds(), again.root.allIds())
            assertEquals("seed $seed: current versions changed", versionsOf(merged), versionsOf(again))
        }
    }

    @Test
    fun mergedHistoriesAreOrderedAndHaveNoDuplicates() {
        for (seed in SEEDS) {
            val merged = RandomPair(seed).merged
            for (entry in allEntries(merged.root)) {
                val times = entry.history.map { it.times.lastModified ?: Instant.MIN }
                assertEquals("seed $seed: history of ${entry.id} is not ordered", times.sorted(), times)
                val versions = entry.history.map { it.copy(history = emptyList()) }
                assertEquals(
                    "seed $seed: repeated version in the history of ${entry.id}",
                    versions.size,
                    versions.distinct().size,
                )
                assertFalse(
                    "seed $seed: the history of ${entry.id} holds its current version",
                    versions.any { sameVersion(it, entry) },
                )
            }
        }
    }

    private fun sameVersion(
        a: Entry,
        b: Entry,
    ): Boolean = a.times.lastModified == b.times.lastModified && a.copy(history = b.history) == b

    private fun current(
        vault: Vault,
        id: UUID,
    ): Entry? = vault.findEntry(id)?.copy(history = emptyList())

    private fun versionsOf(vault: Vault): Map<UUID, Entry> =
        allEntries(vault.root).associate { it.id to it.copy(history = emptyList()) }

    private companion object {
        /** Several seeds, so the scenarios the generator reaches are not all the same ones. */
        val SEEDS = listOf(1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L, 55L, 89L)
    }
}

private fun allEntries(group: Group): List<Entry> = group.entries + group.groups.flatMap(::allEntries)

/** Every attachment id the tree uses, on current versions and on their history. */
private fun referencedBinaries(group: Group): Set<BinaryId> {
    val ids = mutableSetOf<BinaryId>()

    fun visit(node: Group) {
        node.entries.forEach { entry ->
            (listOf(entry) + entry.history).forEach { version -> version.attachments.forEach { ids += it.id } }
        }
        node.groups.forEach(::visit)
    }
    visit(group)
    return ids
}

/**
 * A pair of vaults derived from one base with random edits on both sides. Times come from one strictly increasing
 * clock shared by both sides, so no two objects ever tie, and every object keeps a time of its own.
 */
private class RandomPair(
    seed: Long,
) {
    private val random = Random(seed)
    private var clock = BASE
    private var serial = 0

    val ours: Vault
    val theirs: Vault
    val merged: Vault

    init {
        val base = baseVault()
        var mine = base
        var other = base
        repeat(ROUNDS) {
            mine = mutate(mine, "a")
            other = mutate(other, "b")
        }
        ours = mine
        theirs = other
        merged = mergeVaults(ours, theirs)
    }

    fun records(): Map<UUID, Instant> = merged.deletedObjects.associate { it.id to (it.deletedAt ?: Instant.MAX) }

    fun aliveGroups(records: Map<UUID, Instant>): Set<UUID> =
        (ours.root.groupIds() + theirs.root.groupIds()).filter { alive(it, records) }.toSet()

    fun aliveEntries(records: Map<UUID, Instant>): Set<UUID> =
        (ours.root.entryIds() + theirs.root.entryIds()).filter { alive(it, records) }.toSet()

    /** The newest modification time of an object on either side; the merge compares deletions against this. */
    fun lastModified(id: UUID): Instant =
        listOf(ours, theirs)
            .mapNotNull { it.findGroup(id)?.times?.lastModified ?: it.findEntry(id)?.times?.lastModified }
            .maxOrNull() ?: Instant.MIN

    private fun alive(
        id: UUID,
        records: Map<UUID, Instant>,
    ): Boolean {
        val deletedAt = records[id] ?: return true
        return lastModified(id) > deletedAt
    }

    private fun nextTime(): Instant {
        clock += STEP
        return at(clock)
    }

    private fun nextSerial(): Int = ++serial

    private fun mutate(
        vault: Vault,
        side: String,
    ): Vault =
        when (random.nextInt(OPERATIONS)) {
            EDIT -> editEntry(vault)
            NEW_VERSION -> newVersion(vault)
            ADD_ENTRY -> addEntry(vault, side)
            MOVE_ENTRY -> moveEntry(vault)
            ADD_GROUP -> addGroup(vault, side)
            MOVE_GROUP -> moveGroup(vault)
            EDIT_GROUP -> editGroup(vault)
            DELETE_ENTRY -> deleteEntry(vault)
            DELETE_GROUP -> deleteGroup(vault)
            ATTACH -> attach(vault)
            else -> tag(vault)
        }

    private fun editEntry(vault: Vault): Vault {
        val id = pickEntry(vault) ?: return vault
        return vault.editEntry(id, nextTime()) { it.withField(NOTES, "n${nextSerial()}") }
    }

    private fun newVersion(vault: Vault): Vault {
        val id = pickEntry(vault) ?: return vault
        return vault.newVersion(id, nextTime()) { it.withField(PASSWORD, "p${nextSerial()}") }
    }

    private fun addEntry(
        vault: Vault,
        side: String,
    ): Vault {
        val group = pickGroup(vault) ?: return vault
        val attachment = if (random.nextInt(ODDS) == 0) null else pickAttachment()
        return vault.addEntry(group, newEntry("$side-entry-${nextSerial()}", nextTime(), attachment))
    }

    private fun moveEntry(vault: Vault): Vault {
        val id = pickEntry(vault) ?: return vault
        val group = pickGroup(vault) ?: return vault
        return vault.moveEntry(id, group, nextTime())
    }

    private fun addGroup(
        vault: Vault,
        side: String,
    ): Vault {
        val parent = pickGroup(vault) ?: return vault
        return vault.addGroup(parent, newGroup("$side-group-${nextSerial()}", nextTime()))
    }

    private fun moveGroup(vault: Vault): Vault {
        val moved = pickOtherGroup(vault) ?: return vault
        val subtree = requireNotNull(vault.findGroup(moved)).allIds()
        val target = pick(vault.root.groupIds().filter { it !in subtree }) ?: return vault
        return vault.moveGroup(moved, target, nextTime())
    }

    private fun editGroup(vault: Vault): Vault {
        val id = pickGroup(vault) ?: return vault
        return vault.editGroup(id, nextTime()) { it.copy(notes = "g${nextSerial()}") }
    }

    private fun deleteEntry(vault: Vault): Vault {
        val id = pickEntry(vault) ?: return vault
        return vault.deleteEntry(id, nextTime())
    }

    private fun deleteGroup(vault: Vault): Vault {
        val id = pickOtherGroup(vault) ?: return vault
        return vault.deleteGroup(id, nextTime())
    }

    private fun attach(vault: Vault): Vault {
        val id = pickEntry(vault) ?: return vault
        return vault.editEntry(id, nextTime()) { it.withAttachment(pickAttachment()) }
    }

    private fun tag(vault: Vault): Vault {
        val id = pickEntry(vault) ?: return vault
        return vault.editEntry(id, nextTime()) { it.withTag("t${nextSerial()}") }
    }

    private fun pickEntry(vault: Vault): UUID? = pick(vault.root.entryIds())

    private fun pickGroup(vault: Vault): UUID? = pick(vault.root.groupIds())

    private fun pickOtherGroup(vault: Vault): UUID? = pick(vault.root.groupIds().filter { it != vault.root.id })

    private fun pick(ids: Collection<UUID>): UUID? {
        val list = ids.toList()
        return if (list.isEmpty()) null else list[random.nextInt(list.size)]
    }

    private fun pickAttachment(): String = ATTACHMENTS[random.nextInt(ATTACHMENTS.size)]

    private fun baseVault(): Vault =
        vault {
            group("Group1", at(1)) {
                entry("e1", at(2)) {
                    userName = "ana"
                    password = "p1"
                    attachments = listOf(attachment(ATTACHMENTS[0]))
                }
                entry("e2", at(3)) { url = "https://example.test/2" }
                entry("e3", at(4)) {
                    notes = "n3"
                    tags = listOf("t3")
                }
            }
            group("Group2", at(5)) {
                entry("e4", at(6)) { password = "p4" }
                entry("e5", at(7)) { notes = "n5" }
            }
        }.withBinaries(binaries(ATTACHMENTS[0], ATTACHMENTS[1], ATTACHMENTS[2]))

    private companion object {
        const val BASE = 100
        const val STEP = 7
        const val ROUNDS = 12
        const val OPERATIONS = 11
        const val ODDS = 3
        const val EDIT = 0
        const val NEW_VERSION = 1
        const val ADD_ENTRY = 2
        const val MOVE_ENTRY = 3
        const val ADD_GROUP = 4
        const val MOVE_GROUP = 5
        const val EDIT_GROUP = 6
        const val DELETE_ENTRY = 7
        const val DELETE_GROUP = 8
        const val ATTACH = 9
        val ATTACHMENTS = listOf("file1", "file2", "file3")
    }
}
