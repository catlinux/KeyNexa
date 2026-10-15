package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.BackupError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** The persistent backup state: it survives instances, forgets errors and tolerates a broken file. */
class FileBackupStateTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun theStatePersistsBetweenInstances() {
        FileBackupState(file()).recordSuccess(VAULT_ID, AT)

        assertEquals(AT, FileBackupState(file()).lastSuccess(VAULT_ID))
    }

    @Test
    fun anErrorIsRememberedAndThenCleared() {
        val state = FileBackupState(file())
        state.recordError(VAULT_ID, BackupError.Unavailable, AT)

        assertEquals(BackupError.Unavailable, state.lastError(VAULT_ID))
        assertEquals(AT, state.lastErrorAt(VAULT_ID))

        state.clearError(VAULT_ID)

        assertNull(state.lastError(VAULT_ID))
        assertNull(state.lastErrorAt(VAULT_ID))
        assertNull(FileBackupState(file()).lastError(VAULT_ID))
    }

    @Test
    fun vaultsAreKeptApart() {
        val state = FileBackupState(file())
        state.recordSuccess("one", AT)
        state.recordError("two", BackupError.NotAuthorized, AT.plusSeconds(1))

        assertNull(state.lastSuccess("two"))
        assertNull(state.lastError("one"))
        assertEquals(AT, state.lastSuccess("one"))
        assertEquals(BackupError.NotAuthorized, state.lastError("two"))
    }

    @Test
    fun aMissingFileMeansNoState() {
        assertNull(FileBackupState(file()).lastSuccess(VAULT_ID))
    }

    @Test
    fun aCorruptFileMeansNoStateAndNeverThrows() {
        file().parentFile?.mkdirs()
        file().writeText("this is not a properties file\n\u0000")

        assertNull(FileBackupState(file()).lastSuccess(VAULT_ID))
        assertNull(FileBackupState(file()).lastError(VAULT_ID))
    }

    @Test
    fun writesLeaveNoTemporaryFileBehind() {
        FileBackupState(file()).recordSuccess(VAULT_ID, AT)

        val names = requireNotNull(file().parentFile).list().orEmpty()
        assertFalse(names.any { it.endsWith(".tmp") })
        assertTrue(file().isFile)
    }

    @Test
    fun vaultIdIsAStableShortSha256() {
        val id = vaultIdOf("content://vault.kdbx")

        assertEquals(16, id.length)
        assertTrue(id.all { it in "0123456789abcdef" })
        assertEquals(id, vaultIdOf("content://vault.kdbx"))
        assertFalse(id == vaultIdOf("content://other.kdbx"))
    }

    private fun file(): File = File(requireNotNull(temporary.root), "cloud/backup-state.properties")

    private companion object {
        const val VAULT_ID = "0123456789abcdef"
        val AT: Instant = Instant.parse("2026-10-15T10:20:30Z")
    }
}
