package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.KdbxEngine
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultFormat
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conflict-copy merger: what it folds in, what it leaves alone and what it deletes only when asked.
 *
 * The engine and the storage are this module's fakes, so a "copy" is just bytes that decode to a vault. The last
 * test does the same with the real [KeePassMerger] and vaults of the domain: two devices that edited the same base
 * without seeing each other end up with the union of their changes.
 */
class ConflictMergerTest {
    private val location = VaultLocation("memory/vault.kdbx")
    private val credentials = Credentials.of(password = "correct horse".toCharArray())
    private val engine = FakeKdbxEngine()
    private val ledger = FakeMergedCopies()
    private val internet = uuid("$ROOT/Internet")
    private val work = uuid("$ROOT/Work")
    private val mail = uuid("$ROOT/Internet/mail")
    private val bank = uuid("$ROOT/Internet/bank")
    private val jira = uuid("$ROOT/Work/jira")
    private val old = uuid("$ROOT/Work/old")

    @Test
    fun aCopyThatAddsSomethingIsFoldedIn() {
        val base = sharedVault()
        val other = base.addEntry(internet, newEntry("added-by-the-other-device", at(20)))
        val storage = storageWith(engine.encode(base, credentials), engine.encode(other, credentials))

        val scan = scan(storage, base)

        val merged = requireNotNull(scan.merged)
        assertNotNull(merged.findEntry(uuid("added-by-the-other-device")))
        assertEquals(listOf(copyName()), scan.absorbed.map { it.file.name })
        assertEquals(Fingerprint.of(storage.read(copyLocation())), scan.absorbed.single().fingerprint)
        assertTrue(scan.failed.isEmpty())
    }

    @Test
    fun aCopyAskingForFarMoreKeyDerivationIsNotDecrypted() {
        val base = sharedVault()
        val other = base.addEntry(internet, newEntry("added-by-the-other-device", at(20)))
        val storage = storageWith(engine.encode(base, credentials), engine.encode(other, credentials))
        engine.resetCounters()

        val scan = scanWithCopyCost(storage, base, factor = 100)

        assertNull(scan.merged)
        assertEquals(listOf(CopyProblem.Damaged), scan.failed.map { it.second })
        assertEquals(0, engine.decodes)
    }

    @Test
    fun aCopyWithASomewhatStrongerKeyDerivationIsStillFoldedIn() {
        val base = sharedVault()
        val other = base.addEntry(internet, newEntry("added-by-the-other-device", at(20)))
        val storage = storageWith(engine.encode(base, credentials), engine.encode(other, credentials))

        val scan = scanWithCopyCost(storage, base, factor = 4)

        assertNotNull(requireNotNull(scan.merged).findEntry(uuid("added-by-the-other-device")))
    }

    @Test
    fun aCopyAlreadyInTheLedgerIsNotDecrypted() {
        val base = sharedVault()
        val bytes = engine.encode(base.addEntry(internet, newEntry("already-merged", at(20))), credentials)
        ledger.record(location, listOf(Fingerprint.of(bytes)))
        val storage = storageWith(engine.encode(base, credentials), bytes)
        engine.resetCounters()

        val scan = scan(storage, base)

        assertNull(scan.merged)
        assertTrue(scan.absorbed.isEmpty())
        assertEquals(0, engine.decodes)
    }

    @Test
    fun aCopyThatAddsNothingIsAbsorbedAndLeavesTheVaultAlone() {
        val base = sharedVault()
        val storage = storageWith(engine.encode(base, credentials), engine.encode(base, credentials))

        val scan = scan(storage, base)

        assertNull(scan.merged)
        assertEquals(1, scan.absorbed.size)
        assertTrue(scan.failed.isEmpty())
    }

    @Test
    fun aCopyWrittenWithAnotherPasswordIsReportedAndKept() {
        val base = sharedVault()
        val otherKey = Credentials.of(password = "another key".toCharArray())
        val storage = storageWith(engine.encode(base, credentials), engine.encode(base, otherKey))

        val scan = scan(storage, base)

        assertEquals(CopyProblem.OtherPassword, scan.failed.single().second)
        assertNull(scan.merged)
        assertTrue(scan.absorbed.isEmpty())
        assertNotNull(storage.files[copyLocation()])
    }

    @Test
    fun scanWithOpensACopyWrittenWithAnotherPassword() {
        val base = sharedVault()
        val otherKey = Credentials.of(password = "another key".toCharArray())
        val other = base.addEntry(internet, newEntry("added-with-the-other-key", at(20)))
        val storage = storageWith(engine.encode(base, credentials), engine.encode(other, otherKey))
        val copy = reportedCopy(storage, base)

        val scan = merger(storage).scanWith(location, base, listOf(copy), otherKey)

        val merged = requireNotNull(scan.merged)
        assertNotNull(merged.findEntry(uuid("added-with-the-other-key")))
        assertEquals(listOf(copyName()), scan.absorbed.map { it.file.name })
        assertTrue(scan.failed.isEmpty())
    }

    @Test
    fun scanWithARejectedPasswordLeavesTheCopyReported() {
        val base = sharedVault()
        val otherKey = Credentials.of(password = "another key".toCharArray())
        val storage = storageWith(engine.encode(base, credentials), engine.encode(base, otherKey))
        val copy = reportedCopy(storage, base)
        val wrongKey = Credentials.of(password = "one more key".toCharArray())

        val scan = merger(storage).scanWith(location, base, listOf(copy), wrongKey)

        assertEquals(copy, scan.failed.single().first)
        assertEquals(CopyProblem.OtherPassword, scan.failed.single().second)
        assertNull(scan.merged)
        assertTrue(scan.absorbed.isEmpty())
    }

    @Test
    fun scanWithSkipsACopyAlreadyInTheLedger() {
        val base = sharedVault()
        val bytes = engine.encode(base.addEntry(internet, newEntry("open-with-the-other-key", at(20))), credentials)
        ledger.record(location, listOf(Fingerprint.of(bytes)))
        val storage = storageWith(engine.encode(base, credentials), bytes)
        val copy = SiblingFile(copyLocation(), copyName(), FileMetadata(bytes.size.toLong(), null))
        engine.resetCounters()

        val scan = merger(storage).scanWith(location, base, listOf(copy), credentials)

        assertNull(scan.merged)
        assertTrue(scan.absorbed.isEmpty())
        assertEquals(0, engine.decodes)
    }

    @Test
    fun aDamagedCopyIsReported() {
        val base = sharedVault()
        val damaged =
            engine.encode(base, credentials).also { bytes ->
                bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
            }
        val storage = storageWith(engine.encode(base, credentials), damaged)

        val scan = scan(storage, base)

        assertEquals(CopyProblem.Damaged, scan.failed.single().second)
        assertNull(scan.merged)
    }

    @Test
    fun aCopyFromAnotherVaultIsReported() {
        val base = sharedVault()
        val otherVault = FakeVaultMerger { _, _ -> MergeResult.Unmergeable("another vault") }
        val storage = storageWith(engine.encode(base, credentials), engine.encode(base, credentials))

        val scan = scan(storage, base, otherVault)

        assertEquals(CopyProblem.OtherVault, scan.failed.single().second)
        assertNull(scan.merged)
    }

    @Test
    fun anUnreadableCopyIsReported() {
        val base = sharedVault()
        val storage = storageWith(engine.encode(base, credentials), engine.encode(base, credentials))
        storage.failReadFor = copyLocation()

        val scan = scan(storage, base)

        assertEquals(CopyProblem.Unreadable, scan.failed.single().second)
        assertNull(scan.merged)
        assertNotNull(storage.files[copyLocation()])
    }

    @Test
    fun deletableListsOnlyTheCopiesThatWereRecorded() {
        val base = sharedVault()
        val recorded = engine.encode(base, credentials)
        val untouched = engine.encode(base, credentials)
        ledger.record(location, listOf(Fingerprint.of(recorded)))
        val storage = storageWith(engine.encode(base, credentials), recorded, device = "AAAAAA")
        storage.files[copyLocation("BBBBBB")] = untouched

        val deletable = merger(storage).deletable(location)

        assertEquals(listOf(copyName("AAAAAA")), deletable.map { it.file.name })
    }

    @Test
    fun deleteKeepsACopyThatChangedSinceTheScan() {
        val base = sharedVault()
        val recorded = engine.encode(base, credentials)
        val stored = engine.encode(base, credentials)
        ledger.record(location, listOf(Fingerprint.of(recorded), Fingerprint.of(stored)))
        val storage = storageWith(engine.encode(base, credentials), recorded, device = "AAAAAA")
        storage.files[copyLocation("BBBBBB")] = stored
        val merger = merger(storage)
        val copies = merger.deletable(location)
        storage.files[copyLocation("AAAAAA")] = engine.encode(base, credentials)

        val deleted = merger.delete(location, copies)

        assertEquals(1, deleted)
        assertNotNull(storage.files[copyLocation("AAAAAA")])
        assertNull(storage.files[copyLocation("BBBBBB")])
    }

    @Test
    fun aCopyThatCannotBeDeletedDoesNotStopTheOthers() {
        val base = sharedVault()
        val storedHere = engine.encode(base, credentials)
        val storedThere = engine.encode(base, credentials)
        ledger.record(location, listOf(Fingerprint.of(storedHere), Fingerprint.of(storedThere)))
        val storage = storageWith(engine.encode(base, credentials), storedHere, device = "AAAAAA")
        storage.files[copyLocation("BBBBBB")] = storedThere
        val copies = merger(storage).deletable(location)
        storage.failDeleteFor = copyLocation("AAAAAA")

        val deleted = merger(storage).delete(location, copies)

        assertEquals(1, deleted)
        assertNotNull(storage.files[copyLocation("AAAAAA")])
        assertNull(storage.files[copyLocation("BBBBBB")])
    }

    @Test
    fun recordingWhatWasAbsorbedMakesTheNextScanSkipIt() {
        val base = sharedVault()
        val storage = storageWith(engine.encode(base, credentials), engine.encode(base, credentials))
        val merger = merger(storage)
        val first = merger.scan(location, base, credentials)

        merger.record(location, first.absorbed)
        engine.resetCounters()
        val again = merger.scan(location, base, credentials)

        assertEquals(1, first.absorbed.size)
        assertNull(again.merged)
        assertTrue(again.absorbed.isEmpty())
        assertEquals(0, engine.decodes)
    }

    @Test
    fun twoDevicesEditingFromTheSameBaseEndWithTheUnionOfTheirChanges() {
        val base = sharedVault()
        val deviceA =
            base
                .editEntry(mail, at(10)) { it.withField(Entry.NOTES, "edited-by-a") }
                .addEntry(internet, newEntry("added-by-a", at(11)))
                .deleteEntry(jira, at(12))
        val deviceB =
            base
                .editEntry(bank, at(13)) { it.withField(Entry.NOTES, "edited-by-b") }
                .addEntry(work, newEntry("added-by-b", at(14)))
                .deleteEntry(old, at(15))
        val storage = storageWith(engine.encode(deviceA, credentials), engine.encode(deviceB, credentials))

        val merged = requireNotNull(scan(storage, deviceA).merged)

        assertEquals("edited-by-a", requireNotNull(merged.findEntry(mail)).fields[Entry.NOTES]?.value)
        assertEquals("edited-by-b", requireNotNull(merged.findEntry(bank)).fields[Entry.NOTES]?.value)
        assertNotNull(merged.findEntry(uuid("added-by-a")))
        assertNotNull(merged.findEntry(uuid("added-by-b")))
        assertNull(merged.findEntry(jira))
        assertNull(merged.findEntry(old))
        assertTrue(merged.deletedObjects.map { it.id }.containsAll(listOf(jira, old)))
    }

    /** A vault of the domain, the same every time it is built: the fixtures use deterministic ids. */
    private fun sharedVault(): Vault =
        vault {
            group("Internet", at(1)) {
                entry("mail", at(2)) {
                    userName = "ana"
                    notes = "before"
                }
                entry("bank", at(3)) { userName = "ana" }
            }
            group("Work", at(4)) {
                entry("jira", at(5)) { userName = "ana" }
                entry("old", at(6)) { userName = "ana" }
            }
        }

    private fun scan(
        storage: FakeStorage,
        current: Vault,
        merger: VaultMerger = KeePassMerger(),
    ): ConflictScan = merger(storage, merger).scan(location, current, credentials)

    /** A scan whose copies' headers ask for [factor] times the key derivation work of [current]. */
    private fun scanWithCopyCost(
        storage: FakeStorage,
        current: Vault,
        factor: Long,
    ): ConflictScan {
        val costly =
            when (val kdf = current.format.kdf) {
                is Kdf.Argon2 -> kdf.copy(iterations = kdf.iterations * factor)
                is Kdf.AesKdf -> kdf.copy(rounds = kdf.rounds * factor)
            }
        val greedy =
            object : KdbxEngine by engine {
                override fun inspect(data: ByteArray): VaultFormat = engine.inspect(data).copy(kdf = costly)
            }
        return ConflictMerger(storage, greedy, KeePassMerger(), ledger).scan(location, current, credentials)
    }

    /** The file of the only copy a plain scan reported, as the app hands it back to [ConflictMerger.scanWith]. */
    private fun reportedCopy(
        storage: FakeStorage,
        current: Vault,
    ): SiblingFile =
        merger(storage)
            .scan(location, current, credentials)
            .failed
            .single()
            .first

    private fun merger(
        storage: FakeStorage,
        merger: VaultMerger = KeePassMerger(),
    ): ConflictMerger = ConflictMerger(storage, engine, merger, ledger)

    private fun storageWith(
        vaultFile: ByteArray,
        copy: ByteArray,
        device: String = DEFAULT_DEVICE,
    ): FakeStorage = FakeStorage(mapOf(location to vaultFile, copyLocation(device) to copy))

    private fun copyName(device: String = DEFAULT_DEVICE): String = "vault.sync-conflict-20261014-120000-$device.kdbx"

    private fun copyLocation(device: String = DEFAULT_DEVICE): VaultLocation =
        VaultLocation("memory/${copyName(device)}")

    private companion object {
        const val DEFAULT_DEVICE = "ABCDEF"
    }
}

/** The ledger of merged copies in memory, for tests. */
private class FakeMergedCopies : MergedCopies {
    private val merged = HashMap<VaultLocation, MutableSet<Fingerprint>>()

    override fun isMerged(
        vault: VaultLocation,
        copy: Fingerprint,
    ): Boolean = merged[vault]?.contains(copy) == true

    override fun record(
        vault: VaultLocation,
        copies: Collection<Fingerprint>,
    ) {
        merged.getOrPut(vault) { mutableSetOf() }.addAll(copies)
    }
}
