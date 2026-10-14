package io.github.catlinux.keynexa.app.vault

import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.platform.FileBackupStore
import io.github.catlinux.keynexa.platform.FileMergedCopies
import io.github.catlinux.keynexa.platform.FileSaveJournal
import io.github.catlinux.keynexa.sync.ConflictMerger
import io.github.catlinux.keynexa.sync.CopyProblem
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.KeePassMerger
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.VaultReader
import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Credentials
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.Executor

class VaultSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val location = VaultLocation("memory://vault.kdbx")
    private val known = KnownVault("Test", location)
    private val files = HashMap<VaultLocation, ByteArray>()
    private var unreadable = false
    private var unwritable = false

    private val storage =
        object : StoragePort {
            override fun read(location: VaultLocation): ByteArray {
                if (unreadable) throw IOException("injected")
                return files[location]?.copyOf() ?: throw IOException("missing")
            }

            override fun metadata(location: VaultLocation) = FileMetadata(files[location]?.size?.toLong(), null)

            override fun lastModified(location: VaultLocation): Long? = null

            override fun listSiblings(location: VaultLocation): List<SiblingFile> =
                files.keys
                    .filter { it != location }
                    .map { SiblingFile(it, name(it), FileMetadata(files[it]?.size?.toLong(), null)) }

            override fun name(location: VaultLocation): String = location.value.substringAfterLast('/')

            override fun delete(location: VaultLocation) {
                files.remove(location) ?: throw IOException("missing")
            }

            override fun write(
                location: VaultLocation,
                data: ByteArray,
            ) {
                if (unwritable) throw IOException("injected")
                files[location] = data.copyOf()
            }
        }

    private val engine = KotpassKdbxEngine()
    private val direct = Executor { it.run() }

    private fun session(conflicts: ConflictMerger? = null): VaultSession {
        val backups = FileBackupStore(folder.newFolder())
        val journal = FileSaveJournal(folder.newFolder())
        return VaultSession(
            storage = storage,
            engine = engine,
            readers = VaultReaders(VaultReader(storage, engine, retryDelaysMillis = emptyList())),
            recovery = SaveRecovery(storage, engine, backups, journal),
            saver = SafeSaver(storage, engine, backups, journal, merger = KeePassMerger()),
            executor = direct,
            conflicts = conflicts,
        )
    }

    /** A session with conflict merging on, over [ledger]. */
    private fun conflictSession(ledger: FileMergedCopies): VaultSession =
        session(ConflictMerger(storage, engine, KeePassMerger(), ledger))

    private val copyName = "vault.sync-conflict-20261014-120000-ABCDEFG.kdbx"
    private val copyLocation = VaultLocation("memory://$copyName")

    /** Puts a conflict copy of [vault], written with [password], next to the vault file. */
    private fun putCopy(
        vault: Vault,
        password: String = "test-password-1",
    ): ByteArray = engine.encode(vault, credentials(password)).also { files[copyLocation] = it }

    private fun credentials(password: String = "test-password-1") = Credentials.of(password = password.toCharArray())

    // The cheapest legal Argon2id cost, so the tests stay fast.
    private val cheapKdf = Kdf.Argon2(Argon2Variant.Argon2id, 1024L * 1024L, 1L, 1, 19)

    private fun createVault(session: VaultSession) =
        session.create(known, NewVault().create("Test", cheapKdf), credentials())

    @Test
    fun createdVaultOpensAndLockWipesIt() {
        val session = session()
        createVault(session)
        assertTrue(session.state.value is SessionState.Open)

        session.lock()
        assertEquals(SessionState.Closed, session.state.value)
    }

    @Test
    fun editIsSavedAndSurvivesReopening() {
        val session = session()
        createVault(session)
        val root = (session.state.value as SessionState.Open).current.root.id

        assertTrue(session.apply { VaultEditor().addEntry(it, root, "Mail") })

        val open = session.state.value as SessionState.Open
        assertFalse(open.saving)
        assertFalse(open.hasUnsavedChanges)
        assertEquals(
            listOf("Mail"),
            open.current.root.entries
                .map { it.title },
        )

        val reopened = session()
        reopened.unlock(known, credentials())
        val state = reopened.state.value as SessionState.Open
        assertEquals(
            listOf("Mail"),
            state.current.root.entries
                .map { it.title },
        )
    }

    @Test
    fun wrongPasswordFailsWithoutOpening() {
        val session = session()
        createVault(session)
        session.lock()

        session.unlock(known, credentials("not-the-password"))
        assertEquals(SessionState.Failed(known, UnlockError.WrongCredentials), session.state.value)

        session.dismissFailure()
        assertEquals(SessionState.Closed, session.state.value)
    }

    @Test
    fun unreadableFileIsReportedAsUnreachable() {
        val session = session()
        createVault(session)
        session.lock()
        unreadable = true

        session.unlock(known, credentials())
        assertEquals(SessionState.Failed(known, UnlockError.Unreachable), session.state.value)
    }

    @Test
    fun failedSaveKeepsTheEditAndRetryFinishesIt() {
        val session = session()
        createVault(session)
        val root = (session.state.value as SessionState.Open).current.root.id

        unreadable = true
        assertTrue(session.apply { VaultEditor().addEntry(it, root, "Bank") })
        val failed = session.state.value as SessionState.Open
        assertNotNull(failed.saveProblem)
        assertTrue(failed.hasUnsavedChanges)
        assertEquals(
            listOf("Bank"),
            failed.current.root.entries
                .map { it.title },
        )

        unreadable = false
        session.retrySave()
        val saved = session.state.value as SessionState.Open
        assertEquals(null, saved.saveProblem)
        assertFalse(saved.hasUnsavedChanges)
    }

    @Test
    fun applyIsRefusedWhenNoVaultIsOpen() {
        assertFalse(session().apply { it })
    }

    @Test
    fun lockingWipesTheCredentialsAndForgetsTheVault() {
        val session = session()
        val credentials = credentials()
        session.create(known, NewVault().create("Test", cheapKdf), credentials)
        assertTrue(session.state.value is SessionState.Open)

        session.lock()

        assertEquals(SessionState.Closed, session.state.value)
        assertTrue(runCatching { credentials.passwordCopy() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun refreshLoadsAVersionWrittenByAnotherProgram() {
        val session = session()
        createVault(session)
        val root = (session.state.value as SessionState.Open).current.root.id
        val other = session()
        other.unlock(known, credentials())
        other.apply { VaultEditor().addEntry(it, root, "FromElsewhere") }

        session.refreshIfChanged()

        val state = session.state.value as SessionState.Open
        assertEquals(
            listOf("FromElsewhere"),
            state.current.root.entries
                .map { it.title },
        )
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun refreshKeepsTheDeviceVersionWhenItHasUnsavedChanges() {
        val session = session()
        createVault(session)
        val root = (session.state.value as SessionState.Open).current.root.id
        unreadable = true
        session.apply { VaultEditor().addEntry(it, root, "NotSavedYet") }
        unreadable = false

        session.refreshIfChanged()

        val state = session.state.value as SessionState.Open
        assertEquals(
            listOf("NotSavedYet"),
            state.current.root.entries
                .map { it.title },
        )
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun aConflictCopyIsFoldedInAndRecordedAfterSaving() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        val bytes = putCopy(VaultEditor().addEntry(open.current, open.current.root.id, "FromTheOtherDevice"))
        session.lock()

        session.unlock(known, credentials())

        val state = session.state.value as SessionState.Open
        assertEquals(
            listOf("FromTheOtherDevice"),
            state.current.root.entries
                .map { it.title },
        )
        assertFalse(state.hasUnsavedChanges)
        assertEquals(1, state.conflicts?.mergedNow)
        assertEquals(listOf(copyName), state.conflicts?.deletable?.map { it.file.name })
        assertTrue(ledger.isMerged(location, Fingerprint.of(bytes)))
    }

    @Test
    fun aConflictCopyThatArrivesLaterIsFoldedInWhenTheFileChanges() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        // Another device writes the vault again and leaves a conflict copy next to it.
        files[location] = engine.encode(open.current, credentials())
        val bytes = putCopy(VaultEditor().addEntry(open.current, open.current.root.id, "Later"))

        session.refreshIfChanged()

        val state = session.state.value as SessionState.Open
        assertEquals(
            listOf("Later"),
            state.current.root.entries
                .map { it.title },
        )
        assertEquals(1, state.conflicts?.mergedNow)
        assertTrue(ledger.isMerged(location, Fingerprint.of(bytes)))
    }

    @Test
    fun aSaveThatFailsDoesNotRecordTheCopy() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        val bytes = putCopy(VaultEditor().addEntry(open.current, open.current.root.id, "Pending"))
        session.lock()
        unwritable = true

        session.unlock(known, credentials())

        val state = session.state.value as SessionState.Open
        assertNotNull(state.saveProblem)
        assertTrue(state.hasUnsavedChanges)
        assertEquals(
            listOf("Pending"),
            state.current.root.entries
                .map { it.title },
        )
        assertNull(state.conflicts)
        assertFalse(ledger.isMerged(location, Fingerprint.of(bytes)))
        assertTrue(files.containsKey(copyLocation))
    }

    @Test
    fun deleteConflictCopiesRemovesThemAndClearsTheOffer() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        putCopy(VaultEditor().addEntry(open.current, open.current.root.id, "Other"))
        session.lock()
        session.unlock(known, credentials())
        assertNotNull((session.state.value as SessionState.Open).conflicts)

        session.deleteConflictCopies()

        val state = session.state.value as SessionState.Open
        assertNull(state.conflicts)
        assertFalse(files.containsKey(copyLocation))
    }

    @Test
    fun aCopyWithAnotherPasswordIsReportedAndNotDeleted() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        val bytes = putCopy(open.current, password = "another-password")
        session.lock()

        session.unlock(known, credentials())

        val state = session.state.value as SessionState.Open
        assertEquals(listOf(CopyProblem.OtherPassword), state.conflicts?.failed)
        assertTrue(state.conflicts?.deletable?.isEmpty() == true)
        assertTrue(files.containsKey(copyLocation))
        assertFalse(ledger.isMerged(location, Fingerprint.of(bytes)))
    }

    @Test
    fun thePasswordOfACopyIsAskedForAndTheCopyIsThenMerged() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        val bytes =
            putCopy(
                VaultEditor().addEntry(open.current, open.current.root.id, "FromAnotherKey"),
                password = "another-password",
            )
        session.lock()
        session.unlock(known, credentials())
        val reported = (session.state.value as SessionState.Open).conflicts!!
        assertEquals(listOf(CopyProblem.OtherPassword), reported.failed)
        assertEquals(listOf(copyName), reported.otherPasswordCopies.map { it.name })
        assertFalse(reported.otherPasswordRejected)

        val extra = credentials("another-password")
        session.mergeOtherPasswordCopies(extra)

        val state = session.state.value as SessionState.Open
        assertEquals(
            listOf("FromAnotherKey"),
            state.current.root.entries
                .map { it.title },
        )
        assertFalse(state.hasUnsavedChanges)
        assertEquals(1, state.conflicts?.mergedNow)
        assertEquals(listOf(copyName), state.conflicts?.deletable?.map { it.file.name })
        assertTrue(ledger.isMerged(location, Fingerprint.of(bytes)))
        assertTrue(runCatching { extra.passwordCopy() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun aRejectedPasswordLeavesTheCopyAndMarksTheReport() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        val bytes = putCopy(open.current, password = "another-password")
        session.lock()
        session.unlock(known, credentials())

        val extra = credentials("not-that-password")
        session.mergeOtherPasswordCopies(extra)

        val state = session.state.value as SessionState.Open
        assertEquals(listOf(CopyProblem.OtherPassword), state.conflicts?.failed)
        assertTrue(state.conflicts?.otherPasswordRejected == true)
        assertTrue(state.conflicts?.deletable?.isEmpty() == true)
        assertTrue(files.containsKey(copyLocation))
        assertFalse(ledger.isMerged(location, Fingerprint.of(bytes)))
        assertTrue(runCatching { extra.passwordCopy() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun dismissingTheReportHidesItUntilTheNextOpen() {
        val ledger = FileMergedCopies(folder.newFolder())
        val session = conflictSession(ledger)
        createVault(session)
        val open = session.state.value as SessionState.Open
        putCopy(VaultEditor().addEntry(open.current, open.current.root.id, "Elsewhere"))
        session.lock()
        session.unlock(known, credentials())
        assertNotNull((session.state.value as SessionState.Open).conflicts)

        session.dismissConflicts()
        assertNull((session.state.value as SessionState.Open).conflicts)

        session.lock()
        session.unlock(known, credentials())
        assertNotNull((session.state.value as SessionState.Open).conflicts)
    }
}
