package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * AC3: a process killed in the middle of a write never leaves an undecryptable file. Recovery needs no
 * credentials: it compares the file's bytes with the private copies the interrupted save recorded in the journal.
 */
class SaveRecoveryTest {
    private val location = VaultLocation("vault.kdbx")
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val credentials = Credentials.of(password = "correct horse".toCharArray())
    private val engine = FakeKdbxEngine()

    /** Long enough that the previous version is bigger than the new one, which the no-truncating write needs. */
    private val previous = engine.encode(testVault("previous-" + "x".repeat(LONG_NAME)), credentials)
    private val outgoing = engine.encode(testVault("outgoing"), credentials)

    @Test
    fun aCutToTenPercentIsRestoredByteForByte() {
        val crash = crash()
        val cut = percentOf(outgoing.size, 10.0)
        // Telling a torn file from another program's needs 16 bytes of agreement past the 12-byte fixed header.
        assertTrue(cut > SHARED_HEADER + MIN_LEAD)

        crash.cutWriteAfter(cut)

        val restored = crash.recover() as RecoveryOutcome.Restored

        assertArrayEquals(previous, crash.storage.read(location))
        assertEquals(crash.previousCopy(), restored.previousCopy)
        assertArrayEquals(outgoing, crash.backups.read(restored.unsavedCopy))
        assertFalse(crash.journal.isPending(location))
    }

    @Test
    fun aCutToHalfIsRestoredByteForByte() {
        val crash = crash()
        crash.storage.cutWriteFraction = HALF
        crash.writeUntilDeath()

        val restored = crash.recover() as RecoveryOutcome.Restored

        assertArrayEquals(previous, crash.storage.read(location))
        assertEquals(Fingerprint.of(previous), Fingerprint.of(crash.storage.read(location)))
        assertEquals(crash.outgoingCopy(), restored.unsavedCopy)
        assertFalse(crash.journal.isPending(location))
    }

    @Test
    fun aCutToNinetyNinePercentIsRestoredByteForByte() {
        val crash = crash()
        crash.cutWriteAfter(percentOf(outgoing.size, 99.0))

        val restored = crash.recover() as RecoveryOutcome.Restored

        assertArrayEquals(previous, crash.storage.read(location))
        assertArrayEquals(outgoing, crash.backups.read(restored.unsavedCopy))
    }

    @Test
    fun aWriteThatDidNotTruncateAndThenDiedIsRestored() {
        val crash = crash()
        crash.writeWithoutTruncatingAndDie()

        val restored = crash.recover() as RecoveryOutcome.Restored

        assertArrayEquals(previous, crash.storage.read(location))
        assertArrayEquals(outgoing, crash.backups.read(restored.unsavedCopy))
    }

    @Test
    fun aCutToZeroBytesIsRestored() {
        // Nothing of our bytes reached the file, but an empty file has no header, so it cannot be another program's.
        val crash = crash()
        crash.cutWriteAfter(0)

        val outcome = crash.recover()

        assertTrue(outcome is RecoveryOutcome.Restored)
        assertArrayEquals(previous, crash.storage.read(location))
    }

    @Test
    fun aCutInsideTheSharedHeaderIsRestored() {
        // The shared fixed header plus a few bytes: too short to tell apart by prefix, but the header is incomplete.
        val crash = crash()
        crash.cutWriteAfter(SHARED_HEADER + 8)

        val outcome = crash.recover()

        assertTrue(outcome is RecoveryOutcome.Restored)
        assertArrayEquals(previous, crash.storage.read(location))
    }

    @Test
    fun aWriteThatCompletedBeforeTheCrashIsSaveHadCompleted() {
        val crash = crash()
        crash.completeWriteBeforeDeath()

        val outcome = crash.recover()

        assertEquals(RecoveryOutcome.SaveHadCompleted, outcome)
        assertFalse(crash.journal.isPending(location))
        // The copy of the changes is no longer needed: the file already holds them.
        assertTrue(crash.backups.stored(BackupKind.UnsavedChanges).isEmpty())
    }

    @Test
    fun aWriteThatNeverStartedIsSaveHadNotStarted() {
        val crash = crash()

        val outcome = crash.recover() as RecoveryOutcome.SaveHadNotStarted

        assertArrayEquals(outgoing, crash.backups.read(outcome.unsavedCopy))
        assertArrayEquals(previous, crash.storage.read(location))
        assertFalse(crash.journal.isPending(location))
    }

    @Test
    fun aFileReplacedByAnotherProgramIsLeftAsItIs() {
        val external = engine.encode(testVault("another-program"), credentials)
        val crash = crash(file = external)

        val outcome = crash.recover()

        assertTrue(outcome is RecoveryOutcome.ChangedElsewhere)
        assertArrayEquals(external, crash.storage.read(location))
        assertFalse(crash.journal.isPending(location))
    }

    @Test
    fun withoutAJournalEntryThereIsNothingToRecover() {
        val crash = crashedWorld(previous, previous, outgoing)

        assertEquals(RecoveryOutcome.NothingPending, crash.recover())
    }

    @Test
    fun aDamagedPreviousCopyIsStillDamagedAndKeepsTheJournal() {
        val crash = crash()
        crash.cutWriteAfter(percentOf(outgoing.size, 50.0))
        crash.backups.corrupt(crash.previousCopy())

        val outcome = crash.recover()

        assertTrue(outcome is RecoveryOutcome.StillDamaged)
        assertTrue(crash.journal.isPending(location))
    }

    @Test
    fun anUnreadablePreviousCopyIsStillDamagedAndKeepsTheJournal() {
        val crash = crashedWorld(previous, previous, outgoing, InMemoryBackupStore(failOnRead = true))
        crash.recordPendingSave(now)
        crash.cutWriteAfter(percentOf(outgoing.size, 50.0))

        val outcome = crash.recover()

        assertTrue(outcome is RecoveryOutcome.StillDamaged)
        assertTrue(crash.journal.isPending(location))
    }

    @Test
    fun aJournalThatCannotBeReadIsStorageUnavailable() {
        val crash = crashedWorld(previous, previous, outgoing, journal = InMemorySaveJournal(failOnPending = true))

        assertEquals(RecoveryOutcome.StorageUnavailable, crash.recover())
    }

    @Test
    fun aRestoreThatCannotBeWrittenIsStillDamagedAndKeepsTheJournal() {
        val crash = crash()
        crash.cutWriteAfter(percentOf(outgoing.size, 50.0))
        crash.storage.cutWriteTo = percentOf(previous.size, 50.0)

        val outcome = crash.recover()

        assertTrue(outcome is RecoveryOutcome.StillDamaged)
        assertTrue(crash.journal.isPending(location))
    }

    @Test
    fun anUnreadableStorageIsStorageUnavailableAndKeepsTheJournal() {
        val crash = crash()
        crash.storage.files.remove(location)

        val outcome = crash.recover()

        assertEquals(RecoveryOutcome.StorageUnavailable, outcome)
        assertTrue(crash.journal.isPending(location))
    }

    private fun crash(
        file: ByteArray? = null,
        previous: ByteArray = this.previous,
        outgoing: ByteArray = this.outgoing,
    ): Crash = crashedWorld(file ?: previous, previous, outgoing).also { it.recordPendingSave(now) }

    /** The world as the killed process left it, with the file holding [file]. */
    private fun crashedWorld(
        file: ByteArray,
        previous: ByteArray,
        outgoing: ByteArray,
        backups: InMemoryBackupStore = InMemoryBackupStore(),
        journal: InMemorySaveJournal = InMemorySaveJournal(),
    ): Crash {
        val storage = FakeStorage(mapOf(location to file))
        return Crash(
            engine,
            location,
            storage,
            backups,
            journal,
            previous to outgoing,
        )
    }

    private fun percentOf(
        size: Int,
        percent: Double,
    ): Int = (size * percent / 100).toInt()

    private companion object {
        const val LONG_NAME = 600
        const val SHARED_HEADER = 12
        const val MIN_LEAD = 16
        const val HALF = 0.5
    }
}

/** The state a killed process leaves behind: the file, the private copies and the journal entry. */
private class Crash(
    val engine: FakeKdbxEngine,
    val location: VaultLocation,
    val storage: FakeStorage,
    val backups: InMemoryBackupStore,
    val journal: InMemorySaveJournal,
    /** The previous version and the new one, in that order. */
    versions: Pair<ByteArray, ByteArray>,
) {
    val previous: ByteArray = versions.first
    val outgoing: ByteArray = versions.second

    val recovery = SaveRecovery(storage, engine, backups, journal)

    fun previousCopy(): BackupRef = backups.stored(BackupKind.BeforeSave).single()

    fun outgoingCopy(): BackupRef = backups.stored(BackupKind.UnsavedChanges).single()

    /** The write died halfway: only the first [bytes] bytes of the new version reached the file. */
    fun cutWriteAfter(bytes: Int) {
        storage.cutWriteTo = bytes
        writeUntilDeath()
    }

    /** The process died while writing: whatever the injected failure left in the file stays there. */
    fun writeUntilDeath() {
        runCatching { storage.write(location, outgoing) }
        storage.cutWriteTo = null
        storage.cutWriteFraction = null
    }

    /** The private copies and the journal entry the writer makes before it starts writing. */
    fun recordPendingSave(now: Instant) {
        val previousCopy = backups.put(location, BackupKind.BeforeSave, previous, now)
        val outgoingCopy = backups.put(location, BackupKind.UnsavedChanges, outgoing, now)
        journal.record(
            PendingSave(location, Fingerprint.of(previous), Fingerprint.of(outgoing), previousCopy, outgoingCopy, now),
        )
    }

    /** The write finished, but the process died before the result could be checked. */
    fun completeWriteBeforeDeath() {
        storage.files[location] = outgoing.copyOf()
    }

    /** The provider ignored the truncation and died: the file is the new bytes plus the old tail. */
    fun writeWithoutTruncatingAndDie() {
        storage.writeWithoutTruncate = true
        storage.write(location, outgoing)
    }

    fun recover(): RecoveryOutcome = recovery.recover(location)
}
