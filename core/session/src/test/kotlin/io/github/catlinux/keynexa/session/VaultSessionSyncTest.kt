package io.github.catlinux.keynexa.session

import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.platform.FileBackupStore
import io.github.catlinux.keynexa.platform.FileSaveJournal
import io.github.catlinux.keynexa.sync.Absorbed
import io.github.catlinux.keynexa.sync.Expect
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.KeePassMerger
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.SyncIds
import io.github.catlinux.keynexa.sync.VaultReader
import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.CustomDataValue
import io.github.catlinux.keynexa.vault.FileMetadata
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.NewVault
import io.github.catlinux.keynexa.vault.SiblingFile
import io.github.catlinux.keynexa.vault.StoragePort
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultEditor
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The open session as the cloud reconciler uses it (ADR-0009): merging a remote copy into the vault and carrying the
 * synchronisation id. Integration tests with the real KDBX engine and a vault file kept in memory.
 */
class VaultSessionSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val location = VaultLocation("memory://vault.kdbx")
    private val known = KnownVault("Test", location)
    private val files = HashMap<VaultLocation, ByteArray>()
    private var unreadable = false
    private var writes = 0

    private val storage =
        object : StoragePort {
            override fun read(location: VaultLocation): ByteArray {
                if (unreadable) throw IOException("injected")
                return files[location]?.copyOf() ?: throw IOException("missing")
            }

            override fun metadata(location: VaultLocation) = FileMetadata(files[location]?.size?.toLong(), null)

            override fun lastModified(location: VaultLocation): Long? = null

            override fun listSiblings(location: VaultLocation): List<SiblingFile> = emptyList()

            override fun name(location: VaultLocation): String = location.value.substringAfterLast('/')

            override fun delete(location: VaultLocation) {
                files.remove(location) ?: throw IOException("missing")
            }

            override fun write(
                location: VaultLocation,
                data: ByteArray,
            ) {
                writes++
                files[location] = data.copyOf()
            }
        }

    private val engine = KotpassKdbxEngine()
    private val backups by lazy { FileBackupStore(folder.newFolder()) }
    private val journal by lazy { FileSaveJournal(folder.newFolder()) }

    // The cheapest legal Argon2id cost, so the tests stay fast.
    private val cheapKdf = Kdf.Argon2(Argon2Variant.Argon2id, 1024L * 1024L, 1L, 1, 19)

    private val id = "0123456789abcdef0123456789abcdef"

    private fun credentials(password: String = "test-password-1") = Credentials.of(password = password.toCharArray())

    private fun session(
        executor: Executor = Executor { it.run() },
        executorWaitMillis: Long = 120_000L,
        afterSave: (KnownVault) -> Unit = {},
    ): VaultSession =
        VaultSession(
            storage = storage,
            engine = engine,
            readers = VaultReaders(VaultReader(storage, engine, retryDelaysMillis = emptyList())),
            recovery = SaveRecovery(storage, engine, backups, journal),
            saver = SafeSaver(storage, engine, backups, journal, merger = KeePassMerger()),
            backups = backups,
            vaultMerger = KeePassMerger(),
            executor = executor,
            afterSave = afterSave,
            executorWaitMillis = executorWaitMillis,
        )

    private fun createVault(session: VaultSession) =
        session.create(known, NewVault().create("Test", cheapKdf), credentials())

    private fun openSession(): VaultSession = session().also(::createVault)

    private fun openVault(session: VaultSession): Vault = (session.state.value as SessionState.Open).current

    private fun withId(
        vault: Vault,
        syncId: String,
    ): Vault =
        vault.copy(
            meta = vault.meta.copy(customData = vault.meta.customData + (SyncIds.KEY to CustomDataValue(syncId))),
        )

    private fun encode(
        vault: Vault,
        password: String = "test-password-1",
    ): ByteArray = engine.encode(vault, credentials(password))

    private fun titlesOf(bytes: ByteArray): List<String> =
        engine
            .decode(bytes, credentials())
            .root.entries
            .map { it.title }

    @Test
    fun aRemoteCopyWithNewEntriesIsMergedAndSaved() {
        val session = openSession()
        val base = openVault(session)
        session.apply { VaultEditor().addEntry(it, base.root.id, "Local") }
        val remote = withId(VaultEditor().addEntry(base, base.root.id, "Remote"), id)
        val writesBefore = writes

        val result = session.absorbRemote(encode(remote), Expect.SyncId(id))

        val merged = result as Absorbed.Merged
        // The remote only held one of the two entries, so the merged result still has to be uploaded.
        assertTrue(merged.remoteLacksLocal)
        assertEquals(setOf("Local", "Remote"), titlesOf(merged.file.bytes).toSet())
        assertTrue(writes > writesBefore)
        assertEquals(setOf("Local", "Remote"), titlesOf(requireNotNull(files[location])).toSet())
        val state = session.state.value as SessionState.Open
        assertFalse(state.saving)
        assertFalse(state.hasUnsavedChanges)
        assertNull(state.saveProblem)
    }

    @Test
    fun theFileJustUploadedComesBackWithNothingToUploadOrSave() {
        val session = openSession()
        val base = openVault(session)
        session.apply { VaultEditor().addEntry(it, base.root.id, "Local") }
        val remote = withId(VaultEditor().addEntry(base, base.root.id, "Remote"), id)
        val uploaded = (session.absorbRemote(encode(remote), Expect.SyncId(id)) as Absorbed.Merged).file.bytes
        val writesBefore = writes

        // The other device merges what was uploaded and, having nothing new, uploads the same content back.
        val result = session.absorbRemote(uploaded, Expect.SyncId(id))

        val merged = result as Absorbed.Merged
        assertFalse(merged.remoteLacksLocal)
        assertEquals(writesBefore, writes)
    }

    @Test
    fun aRemoteCopyThatHoldsEverythingIsMergedWithoutUploadingAnything() {
        val session = openSession()
        val base = openVault(session)
        session.apply { VaultEditor().addEntry(it, base.root.id, "Local") }
        // Reopening makes the in-memory vault match the file exactly, including the file's time resolution.
        session.lock()
        session.unlock(known, credentials())
        val remote = withId(openVault(session), id)

        val result = session.absorbRemote(encode(remote), Expect.SyncId(id))

        val merged = result as Absorbed.Merged
        assertFalse(merged.remoteLacksLocal)
        assertEquals(listOf("Local"), titlesOf(requireNotNull(files[location])))
        // The id the remote carried is now in the local file.
        assertEquals(id, session.syncId())
    }

    @Test
    fun aRemoteCopyThatChangesNothingWritesNothing() {
        val session = openSession()
        val bytes = requireNotNull(files[location]).copyOf()
        val writesBefore = writes

        val result = session.absorbRemote(bytes, Expect.SameRoot)

        val merged = result as Absorbed.Merged
        assertFalse(merged.remoteLacksLocal)
        assertEquals(writesBefore, writes)
        assertEquals(Fingerprint.of(bytes), merged.file.fingerprint)
    }

    @Test
    fun aRemoteCopyWithAnotherPasswordIsReportedAndNothingIsWritten() {
        val session = openSession()
        val base = openVault(session)
        val remote = VaultEditor().addEntry(base, base.root.id, "Elsewhere")
        val writesBefore = writes

        val result = session.absorbRemote(encode(remote, password = "another-password"), Expect.SameRoot)

        assertEquals(Absorbed.OtherPassword, result)
        assertEquals(writesBefore, writes)
        assertFalse((session.state.value as SessionState.Open).hasUnsavedChanges)
    }

    @Test
    fun bytesThatAreNotAVaultAreUnreadable() {
        val session = openSession()
        val writesBefore = writes

        val result = session.absorbRemote(byteArrayOf(1, 2, 3), Expect.SameRoot)

        assertEquals(Absorbed.Unreadable, result)
        assertEquals(writesBefore, writes)
    }

    @Test
    fun anotherVaultIsNotTheSameVault() {
        val session = openSession()
        val other = NewVault().create("Other", cheapKdf)
        val writesBefore = writes

        assertEquals(
            Absorbed.NotSameVault,
            session.absorbRemote(encode(withId(other, SyncIds.newId())), Expect.SyncId(id)),
        )
        assertEquals(Absorbed.NotSameVault, session.absorbRemote(encode(other), Expect.SameRoot))
        assertEquals(writesBefore, writes)
    }

    @Test
    fun aRemoteWithTheExpectedIdButAnotherRootIsUnreadable() {
        val session = openSession()
        val other = NewVault().create("Other", cheapKdf)

        // The id matches, so it gets as far as the merge, which cannot combine two different vaults.
        assertEquals(Absorbed.Unreadable, session.absorbRemote(encode(withId(other, id)), Expect.SyncId(id)))
    }

    @Test
    fun unsavedChangesPostponeTheAbsorb() {
        val session = openSession()
        val base = openVault(session)
        unreadable = true
        session.apply { VaultEditor().addEntry(it, base.root.id, "Pending") }
        unreadable = false
        assertTrue((session.state.value as SessionState.Open).hasUnsavedChanges)
        val writesBefore = writes
        val remote = withId(VaultEditor().addEntry(base, base.root.id, "FromCloud"), id)

        assertEquals(Absorbed.Postponed, session.absorbRemote(encode(remote), Expect.SyncId(id)))

        // The failed save left the file as it was, and the absorb wrote nothing either.
        assertEquals(writesBefore, writes)
        assertEquals(emptyList<String>(), titlesOf(requireNotNull(files[location])))
    }

    @Test
    fun aSaveInProgressPostponesTheAbsorb() {
        val executor = QueueExecutor()
        val session = session(executor = executor)
        createVault(session)
        executor.runAll()
        val base = openVault(session)
        val remote = withId(VaultEditor().addEntry(base, base.root.id, "FromCloud"), id)

        session.apply { VaultEditor().addEntry(it, base.root.id, "Pending") }
        assertTrue((session.state.value as SessionState.Open).saving)

        var answer: Absorbed? = null
        val caller = Thread { answer = session.absorbRemote(encode(remote), Expect.SyncId(id)) }
        caller.start()
        waitForQueue(executor, 2)
        // The absorb runs while the save is still waiting: the vault is busy, so it must be postponed.
        executor.runLast()
        caller.join(5_000)
        assertEquals(Absorbed.Postponed, answer)

        executor.runAll()
        assertFalse((session.state.value as SessionState.Open).saving)
        assertEquals(listOf("Pending"), titlesOf(requireNotNull(files[location])))
    }

    @Test
    fun aClosedVaultPostponesTheAbsorbAndTheAssign() {
        val session = session()
        val bytes = encode(NewVault().create("Test", cheapKdf))

        assertEquals(Absorbed.Postponed, session.absorbRemote(bytes, Expect.SameRoot))
        assertFalse(session.assignSyncId(id))
        assertNull(session.syncId())
    }

    @Test
    fun aBusyExecutorDoesNotHangTheCaller() {
        val executor = QueueExecutor()
        val session = session(executor = executor, executorWaitMillis = 50L)
        // A task is already waiting and never runs, so the executor is busy for good.
        executor.execute { }

        var answer: Absorbed? = null
        var assigned: Boolean? = null
        val caller =
            Thread {
                answer = session.absorbRemote(encode(NewVault().create("Test", cheapKdf)), Expect.SameRoot)
                assigned = session.assignSyncId(id)
            }
        caller.start()
        caller.join(5_000)

        // Both calls give up on their deadline instead of waiting for the busy executor forever.
        assertFalse(caller.isAlive)
        assertEquals(Absorbed.Postponed, answer)
        assertEquals(false, assigned)
    }

    @Test
    fun absorbingFromTheExecutorThreadIsPostponedInsteadOfDeadlocking() {
        var answer: Absorbed? = null
        var live: VaultSession? = null
        val session =
            session(
                afterSave = {
                    answer = live?.absorbRemote(encode(NewVault().create("Test", cheapKdf)), Expect.SameRoot)
                },
            )
        live = session
        createVault(session)
        val base = openVault(session)

        // The after-save hook runs on the executor, so this is a call from the executor's own thread.
        session.apply { VaultEditor().addEntry(it, base.root.id, "Mail") }

        assertEquals(Absorbed.Postponed, answer)
    }

    @Test
    fun theAfterSaveHookRunsOnceWhenTheMergeSaves() {
        val saved = mutableListOf<KnownVault>()
        val session = session(afterSave = { saved += it })
        createVault(session)
        val base = openVault(session)
        val remote = withId(VaultEditor().addEntry(base, base.root.id, "Remote"), id)

        session.absorbRemote(encode(remote), Expect.SyncId(id))
        assertEquals(listOf(known), saved)

        saved.clear()
        session.absorbRemote(requireNotNull(files[location]).copyOf(), Expect.SameRoot)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun assigningASyncIdSavesItAndItIsReadBack() {
        val session = openSession()
        assertNull(session.syncId())

        assertTrue(session.assignSyncId(id))

        assertEquals(id, session.syncId())
        assertFalse((session.state.value as SessionState.Open).hasUnsavedChanges)
        val onDisk = engine.decode(requireNotNull(files[location]), credentials()).meta.customData[SyncIds.KEY]
        assertEquals(id, onDisk?.value)

        session.lock()
        session.unlock(known, credentials())
        assertEquals(id, session.syncId())
    }

    @Test
    fun assigningTheSameSyncIdAgainWritesNothing() {
        val session = openSession()
        session.assignSyncId(id)
        val writesBefore = writes

        assertTrue(session.assignSyncId(id))

        assertEquals(writesBefore, writes)
    }

    @Test
    fun theAdapterReadsTheSessionAndDelegates() {
        val session = openSession()
        val adapter = SessionSyncLocal(session, storage)
        assertNull(adapter.syncId())
        val file = adapter.currentFile()
        assertEquals(Fingerprint.of(file.bytes), file.fingerprint)

        adapter.assignSyncId(id)
        assertEquals(id, adapter.syncId())

        val base = openVault(session)
        val remote = withId(VaultEditor().addEntry(base, base.root.id, "Remote"), id)
        assertTrue(adapter.absorb(encode(remote), Expect.SyncId(id)) is Absorbed.Merged)
    }

    private fun waitForQueue(
        executor: QueueExecutor,
        size: Int,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (executor.pending < size && System.currentTimeMillis() < deadline) Thread.sleep(1)
        assertEquals(size, executor.pending)
    }
}

/** An executor that keeps its tasks until the test runs them, so a busy executor can be simulated. */
private class QueueExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    val pending: Int get() = tasks.size

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runLast() {
        tasks.removeLast().run()
    }

    fun runAll() {
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }
}
