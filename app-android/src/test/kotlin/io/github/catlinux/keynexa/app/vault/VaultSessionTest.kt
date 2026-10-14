package io.github.catlinux.keynexa.app.vault

import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.platform.FileBackupStore
import io.github.catlinux.keynexa.platform.FileSaveJournal
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
import io.github.catlinux.keynexa.vault.VaultEditor
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private val storage =
        object : StoragePort {
            override fun read(location: VaultLocation): ByteArray {
                if (unreadable) throw IOException("injected")
                return files[location]?.copyOf() ?: throw IOException("missing")
            }

            override fun metadata(location: VaultLocation) = FileMetadata(files[location]?.size?.toLong(), null)

            override fun listSiblings(location: VaultLocation): List<SiblingFile> = emptyList()

            override fun name(location: VaultLocation): String = location.value.substringAfterLast('/')

            override fun delete(location: VaultLocation) {
                files.remove(location) ?: throw IOException("missing")
            }

            override fun write(
                location: VaultLocation,
                data: ByteArray,
            ) {
                files[location] = data.copyOf()
            }
        }

    private val engine = KotpassKdbxEngine()
    private val direct = Executor { it.run() }

    private fun session(): VaultSession {
        val backups = FileBackupStore(folder.newFolder())
        val journal = FileSaveJournal(folder.newFolder())
        return VaultSession(
            storage = storage,
            engine = engine,
            readers = VaultReaders(VaultReader(storage, engine, retryDelaysMillis = emptyList())),
            recovery = SaveRecovery(storage, engine, backups, journal),
            saver = SafeSaver(storage, engine, backups, journal, merger = KeePassMerger()),
            executor = direct,
        )
    }

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
}
