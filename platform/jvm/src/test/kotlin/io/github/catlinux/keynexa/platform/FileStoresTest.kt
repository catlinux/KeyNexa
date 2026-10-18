package io.github.catlinux.keynexa.platform

import io.github.catlinux.keynexa.sync.BackupKind
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.PendingSave
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Instant

class FileStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val location = VaultLocation("content://docs/tree/primary%3AVaults/document/primary%3AVaults%2FClaus.kdbx")
    private val other = VaultLocation("content://docs/tree/primary%3AVaults/document/primary%3AVaults%2FOther.kdbx")
    private val t0 = Instant.parse("2026-10-13T10:00:00.123456789Z")

    @Test
    fun backupRoundTripKeepsBytesAndMetadata() {
        val store = FileBackupStore(folder.newFolder())
        val bytes = byteArrayOf(1, 2, 3, 4)
        val ref = store.put(location, BackupKind.UnsavedChanges, bytes, t0)

        assertArrayEquals(bytes, store.read(ref))
        val listed = store.list(location).single()
        assertEquals(ref, listed)
        assertEquals(t0, listed.createdAt)
        assertEquals(Fingerprint.of(bytes), listed.fingerprint)
    }

    @Test
    fun listIsNewestFirstAndPerLocation() {
        val store = FileBackupStore(folder.newFolder())
        val old = store.put(location, BackupKind.BeforeSave, byteArrayOf(1), t0)
        val recent = store.put(location, BackupKind.BeforeSave, byteArrayOf(2), t0.plusSeconds(5))
        store.put(other, BackupKind.BeforeSave, byteArrayOf(3), t0.plusSeconds(9))

        assertEquals(listOf(recent, old), store.list(location))
    }

    @Test
    fun deleteRemovesTheCopyAndIsIdempotent() {
        val store = FileBackupStore(folder.newFolder())
        val ref = store.put(location, BackupKind.BeforeSave, byteArrayOf(1), t0)
        store.delete(ref)
        store.delete(ref)
        assertTrue(store.list(location).isEmpty())
    }

    @Test
    fun listIgnoresForeignAndTemporaryFiles() {
        val directory = folder.newFolder()
        val store = FileBackupStore(directory)
        val ref = store.put(location, BackupKind.BeforeSave, byteArrayOf(1), t0)
        java.io.File(directory, "${ref.id}.tmp").writeBytes(byteArrayOf(9))
        java.io.File(directory, "junk.bak").writeBytes(byteArrayOf(9))

        assertEquals(listOf(ref), store.list(location))
    }

    @Test
    fun emptyStoreListsNothing() {
        assertTrue(FileBackupStore(java.io.File(folder.root, "missing")).list(location).isEmpty())
    }

    @Test
    fun journalRoundTrip() {
        val directory = folder.newFolder()
        val store = FileBackupStore(directory)
        val journal = FileSaveJournal(directory)
        val previous = store.put(location, BackupKind.BeforeSave, byteArrayOf(1), t0)
        val outgoing = store.put(location, BackupKind.UnsavedChanges, byteArrayOf(2), t0)
        val pending = PendingSave(location, previous.fingerprint, outgoing.fingerprint, previous, outgoing, t0)

        assertNull(journal.pending(location))
        journal.begin(pending)
        assertEquals(pending, journal.pending(location))
        assertNull(journal.pending(other))

        journal.clear(location)
        assertNull(journal.pending(location))
        journal.clear(location)
    }

    @Test
    fun corruptJournalIsAnErrorNotNothing() {
        val directory = folder.newFolder()
        val journal = FileSaveJournal(directory)
        val store = FileBackupStore(directory)
        val ref = store.put(location, BackupKind.BeforeSave, byteArrayOf(1), t0)
        journal.begin(PendingSave(location, ref.fingerprint, ref.fingerprint, ref, ref, t0))
        val file = directory.listFiles { _, name -> name.endsWith(".journal") }!!.single()
        file.writeText("previous=zz\n")

        val failure = runCatching { journal.pending(location) }.exceptionOrNull()
        assertTrue(failure is IOException)
    }
}
