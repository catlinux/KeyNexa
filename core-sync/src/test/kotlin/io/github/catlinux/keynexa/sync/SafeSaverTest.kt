package io.github.catlinux.keynexa.sync

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The safe-save channel: what a save reports, what it leaves in the file and in the private copies, and how it
 * refuses to overwrite a file that changed elsewhere (AC5).
 */
class SafeSaverTest {
    private val location = VaultLocation("vault.kdbx")
    private val credentials = Credentials.of(password = "correct horse".toCharArray())
    private val base = testVault("base")
    private val edited = testVault("edited")

    @Test
    fun aNormalSaveWritesExactlyTheNewBytes() {
        val world = SyncWorld()
        world.withFile(location, world.engine.encode(base, credentials))
        world.engine.resetCounters()

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials)

        val saved = outcome as SaveOutcome.Saved
        assertFalse(saved.merged)
        assertSame(edited, saved.loaded.vault)
        assertEquals(Fingerprint.of(world.storage.read(location)), saved.loaded.fingerprint)
        // One key derivation per save: one encode, and one decode that verifies the bytes before writing them.
        assertEquals(1, world.engine.encodes)
        assertEquals(1, world.engine.decodes)
        // The file holds exactly the new bytes: decoding them returns the instance that was encoded.
        assertSame(edited, world.decodeFile(location, credentials))
        assertFalse(world.journal.isPending(location))
        assertTrue(world.backups.stored(BackupKind.UnsavedChanges).isEmpty())
        assertEquals(1, world.backups.stored(BackupKind.BeforeSave).size)
    }

    @Test
    fun rotationKeepsAtMostKeepBackupsAndNeverDropsUnsavedChanges() {
        val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
        val world = SyncWorld(options = SaveOptions(clock = clock, keepBackups = 2))
        world.withFile(location, world.engine.encode(base, credentials))
        val unsaved = world.backups.put(location, BackupKind.UnsavedChanges, byteArrayOf(9, 9, 9), clock.instant())

        var loaded = world.loaded(location, base)
        repeat(4) {
            clock.advance(Duration.ofMinutes(1))
            assertTrue(world.saver.save(loaded, edited, credentials) is SaveOutcome.Saved)
            loaded = world.loaded(location, edited)
        }

        assertEquals(2, world.backups.stored(BackupKind.BeforeSave).size)
        assertEquals(1, world.backups.stored(BackupKind.UnsavedChanges).size)
        assertNotNull(world.backups.bytesOf(unsaved))
    }

    @Test
    fun anEncodeThatFailsIsNotSavedAndTouchesNothing() {
        val world = SyncWorld()
        val file = world.engine.encode(base, credentials)
        world.withFile(location, file)
        world.engine.failEncode = true

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.EncodeFailed, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertNull(outcome.unsavedCopy)
        assertArrayEquals(file, world.storage.read(location))
        assertFalse(world.journal.isPending(location))
        assertTrue(world.backups.stored(BackupKind.BeforeSave).isEmpty())
    }

    @Test
    fun aFailedBackupIsNotSavedAndTheFileIsNotWritten() {
        val file = FakeKdbxEngine().encode(base, credentials)
        val storage = FakeStorage(mapOf(location to file))
        val world = SyncWorld(storage = storage, backups = InMemoryBackupStore(failOnPut = true))

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.StorageError, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertNull(outcome.unsavedCopy)
        assertTrue(storage.operations.none { it.startsWith(WRITE) })
        assertArrayEquals(file, storage.read(location))
    }

    @Test
    fun aFailedJournalWriteIsNotSavedAndTheFileIsNotWritten() {
        val file = FakeKdbxEngine().encode(base, credentials)
        val storage = FakeStorage(mapOf(location to file))
        val world = SyncWorld(storage = storage, journal = InMemorySaveJournal(failOnBegin = true))

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.StorageError, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertNotNull(outcome.unsavedCopy)
        assertTrue(storage.operations.none { it.startsWith(WRITE) })
        assertArrayEquals(file, storage.read(location))
    }

    @Test
    fun aWriteWithoutTruncationIsDetectedAndTheFileIsRestored() {
        val world = SyncWorld()
        val file = world.engine.encode(base, credentials) + ByteArray(LONG_TAIL) { 0x5A.toByte() }
        world.withFile(location, file)
        world.storage.writeWithoutTruncate = true

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.WriteNotVerified, outcome.reason)
        assertEquals(FileState.Restored, outcome.file)
        assertArrayEquals(file, world.storage.read(location))
        assertFalse(world.journal.isPending(location))
        val copy = checkNotNull(outcome.unsavedCopy)
        assertSame(edited, world.engine.decode(checkNotNull(world.backups.bytesOf(copy)), credentials))
    }

    @Test
    fun aWriteWithoutTruncationWhoseRestoreFailsNeedsRecovery() {
        val world = SyncWorld()
        val file = world.engine.encode(base, credentials) + ByteArray(LONG_TAIL) { 0x5A.toByte() }
        world.withFile(location, file)
        world.storage.writeWithoutTruncate = true
        world.storage.failWriteAt = 2

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.WriteNotVerified, outcome.reason)
        assertEquals(FileState.NeedsRecovery, outcome.file)
        assertNotNull(outcome.unsavedCopy)
        assertTrue(world.journal.isPending(location))
    }

    @Test
    fun aProviderThatWritesNothingIsWriteNotVerified() {
        val world = SyncWorld()
        val file = world.engine.encode(base, credentials)
        world.withFile(location, file)
        world.storage.silentWrite = true

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.WriteNotVerified, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(file, world.storage.read(location))
    }

    @Test
    fun anIoFailureOnWriteWithoutChangingTheFileIsStorageError() {
        val world = SyncWorld()
        val file = world.engine.encode(base, credentials)
        world.withFile(location, file)
        world.storage.failWriteAt = 1

        val outcome = world.saver.save(world.loaded(location, base), edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.StorageError, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(file, world.storage.read(location))
    }

    @Test
    fun anExternalChangeWithoutAMergerIsNotOverwritten() {
        val world = SyncWorld()
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val external = world.engine.encode(testVault("external"), credentials)
        world.withFile(location, external)

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.ExternalChangeNeedsMerge, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(external, world.storage.read(location))
        // The new bytes are kept as unsaved changes, untouched and decryptable.
        val copy = checkNotNull(outcome.unsavedCopy)
        assertSame(edited, world.engine.decode(checkNotNull(world.backups.bytesOf(copy)), credentials))
    }

    @Test
    fun anUnmergeableExternalChangeIsNotOverwritten() {
        val world = SyncWorld(merger = FakeVaultMerger { _, _ -> MergeResult.Unmergeable("different master keys") })
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val external = world.engine.encode(testVault("external"), credentials)
        world.withFile(location, external)

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.Unmergeable, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(external, world.storage.read(location))
    }

    @Test
    fun aMergerThatCombinesTheVersionsIsSavedAsMerged() {
        val merged = testVault("merged")
        val merger = FakeVaultMerger { _, _ -> MergeResult.Merged(merged) }
        val world = SyncWorld(merger = merger)
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        world.withFile(location, world.engine.encode(testVault("external"), credentials))

        val saved = world.saver.save(loaded, edited, credentials) as SaveOutcome.Saved

        assertTrue(saved.merged)
        assertSame(merged, saved.loaded.vault)
        assertEquals(1, merger.calls)
        assertSame(merged, world.decodeFile(location, credentials))
    }

    @Test
    fun anUnreadableExternalFileIsFileUnreadable() {
        val world = SyncWorld(merger = FakeVaultMerger { _, _ -> MergeResult.Merged(testVault()) })
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val damaged = world.engine.encode(testVault("external"), credentials)
        damaged[damaged.size - 1] = (damaged[damaged.size - 1] + 1).toByte()
        world.withFile(location, damaged)

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.FileUnreadable, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(damaged, world.storage.read(location))
    }

    @Test
    fun anExternalFileWithOtherCredentialsIsCredentialsChanged() {
        val world = SyncWorld(merger = FakeVaultMerger { _, _ -> MergeResult.Merged(testVault()) })
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val otherKey = Credentials.of(password = "another key".toCharArray())
        val external = world.engine.encode(testVault("external"), otherKey)
        world.withFile(location, external)

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.CredentialsChanged, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(external, world.storage.read(location))
    }

    @Test
    fun anExternalChangeBetweenTheCheckAndTheWriteIsMergedOnTheNextRound() {
        val merger = FakeVaultMerger { _, _ -> MergeResult.Merged(testVault("merged")) }
        val world = SyncWorld(merger = merger)
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val external = world.engine.encode(testVault("external"), credentials)
        world.storage.forgetOperations()
        // Read 1 is the attempt's; read 2 is the writer's last check, right before the write.
        world.storage.beforeRead(2) { world.storage.files[location] = external }

        val saved = world.saver.save(loaded, edited, credentials) as SaveOutcome.Saved

        assertTrue(saved.merged)
        assertEquals(1, merger.calls)
        assertSame(saved.loaded.vault, world.decodeFile(location, credentials))
        // The aborted first round kept a copy of the base file and the second round a copy of the external one:
        // one of the BeforeSave copies holds the version the successful write replaced.
        val copies = world.backups.stored(BackupKind.BeforeSave).map { world.backups.read(it) }
        assertTrue(copies.any { it.contentEquals(external) })
    }

    @Test
    fun anExternalChangeBetweenTheCheckAndTheWriteWithoutAMergerIsNotSaved() {
        val world = SyncWorld()
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val external = world.engine.encode(testVault("external"), credentials)
        world.storage.forgetOperations()
        world.storage.beforeRead(2) { world.storage.files[location] = external }

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.ExternalChangeNeedsMerge, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertArrayEquals(external, world.storage.read(location))
        assertTrue(world.storage.operations.none { it.startsWith(WRITE) })
    }

    @Test
    fun aFileThatChangesInEveryRoundIsChangedDuringSave() {
        val world = SyncWorld(merger = FakeVaultMerger { _, _ -> MergeResult.Merged(testVault("merged")) })
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val first = world.engine.encode(testVault("external-a"), credentials)
        val second = world.engine.encode(testVault("external-b"), credentials)
        world.storage.forgetOperations()
        world.storage.beforeRead(2) { world.storage.files[location] = first }
        world.storage.beforeRead(5) { world.storage.files[location] = second }

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.ChangedDuringSave, outcome.reason)
        assertEquals(FileState.Untouched, outcome.file)
        assertNotNull(outcome.unsavedCopy)
    }

    @Test
    fun anotherProgramReplacingTheFileAfterTheWriteIsLeftAsItIs() {
        val world = SyncWorld()
        world.withFile(location, world.engine.encode(base, credentials))
        val loaded = world.loaded(location, base)
        val external = world.engine.encode(testVault("external"), credentials)
        world.storage.forgetOperations()
        world.storage.afterWrite(1) { world.storage.files[location] = external }

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(SaveFailure.ChangedDuringSave, outcome.reason)
        assertEquals(FileState.ReplacedElsewhere, outcome.file)
        assertArrayEquals(external, world.storage.read(location))
    }

    @Test
    fun aCutToZeroBytesIsKeptForRecoveryAndRestoredOnceWritesWorkAgain() {
        // An empty file has no header, so it is our own torn write. Every write is cut here, the restore included,
        // so the journal must stay; once the storage works again, recovery puts the previous version back.
        val world = SyncWorld()
        val original = world.engine.encode(base, credentials)
        world.withFile(location, original)
        val loaded = world.loaded(location, base)
        world.storage.cutWriteTo = 0

        val outcome = world.saver.save(loaded, edited, credentials) as SaveOutcome.NotSaved

        assertEquals(FileState.NeedsRecovery, outcome.file)
        assertTrue(world.journal.isPending(location))
        assertNotNull(outcome.unsavedCopy)

        world.storage.cutWriteTo = null
        assertTrue(world.recovery.recover(location) is RecoveryOutcome.Restored)
        assertArrayEquals(original, world.storage.read(location))
        assertFalse(world.journal.isPending(location))
    }

    private companion object {
        const val WRITE = "write"
        const val LONG_TAIL = 1_000
    }
}
