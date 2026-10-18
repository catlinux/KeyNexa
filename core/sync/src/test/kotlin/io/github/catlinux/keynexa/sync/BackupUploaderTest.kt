package io.github.catlinux.keynexa.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The uploader: naming, the daily limit, rotation and the error it reports without ever stopping a save. */
class BackupUploaderTest {
    @Test
    fun anUploadUsesThePrefixAndATimestampedName() {
        val backend = InMemoryCloudBackend()

        val result = uploader(backend).backupNow(VAULT_ID, "My Vault", BYTES)

        assertEquals(BackupResult.Uploaded("KeyNexa/My_Vault-vault-1/My_Vault-20261015T102030Z.kdbx", 0), result)
    }

    @Test
    fun theVaultNameIsSanitized() {
        val backend = InMemoryCloudBackend()

        val result = uploader(backend).backupNow(VAULT_ID, "a b/c*d", BYTES)

        assertEquals(BackupResult.Uploaded("KeyNexa/a_b_c_d-vault-1/a_b_c_d-20261015T102030Z.kdbx", 0), result)
    }

    @Test
    fun anEmptyVaultNameBecomesVault() {
        val backend = InMemoryCloudBackend()

        val result = uploader(backend).backupNow(VAULT_ID, "", BYTES)

        assertEquals(BackupResult.Uploaded("KeyNexa/vault-vault-1/vault-20261015T102030Z.kdbx", 0), result)
    }

    @Test
    fun theUploadedBytesAreExactlyTheOnesGiven() {
        val backend = InMemoryCloudBackend()
        val content = ByteArray(64) { it.toByte() }

        uploader(backend).backupNow(VAULT_ID, "My Vault", content)

        val stored = backend.stored("KeyNexa/My_Vault-vault-1/My_Vault-20261015T102030Z.kdbx")
        assertEquals(content.toList(), stored?.toList())
    }

    @Test
    fun backupIfDueSkipsWhileTheIntervalHasNotPassed() {
        val backend = InMemoryCloudBackend()
        val state = InMemoryBackupState()
        val uploader = uploader(backend, state)

        uploader.backupNow(VAULT_ID, "My Vault", BYTES)
        val second = uploader.backupIfDue(VAULT_ID, "My Vault", BYTES)

        assertEquals(BackupResult.Skipped(SkipReason.NotDue), second)
        assertEquals(1, backend.puts)
    }

    @Test
    fun backupIfDueUploadsOnceTheIntervalHasPassed() {
        val backend = InMemoryCloudBackend()
        val state = InMemoryBackupState()
        val clock = FixedClock(START)
        val uploader = uploader(backend, state, clock = clock)

        uploader.backupNow(VAULT_ID, "My Vault", BYTES)
        clock.advance(Duration.ofDays(1))
        val second = uploader.backupIfDue(VAULT_ID, "My Vault", BYTES)

        assertTrue(second is BackupResult.Uploaded)
        assertEquals(2, backend.puts)
    }

    @Test
    fun backupNowIgnoresTheInterval() {
        val backend = InMemoryCloudBackend()
        val state = InMemoryBackupState()
        val uploader = uploader(backend, state)

        uploader.backupNow(VAULT_ID, "My Vault", BYTES)
        val second = uploader.backupNow(VAULT_ID, "My Vault", BYTES)

        assertTrue(second is BackupResult.Uploaded)
        assertEquals(2, backend.puts)
    }

    @Test
    fun rotationKeepsExactlyKeepCopiesAndDeletesTheOldest() {
        val backend = InMemoryCloudBackend()
        backend.objects[copy("20260101T000000Z")] = byteArrayOf(1)
        backend.objects[copy("20260102T000000Z")] = byteArrayOf(2)
        backend.objects[copy("20260103T000000Z")] = byteArrayOf(3)

        val result = uploader(backend, options = BackupOptions(keep = 2)).backupNow(VAULT_ID, "My Vault", BYTES)

        assertEquals(BackupResult.Uploaded("KeyNexa/My_Vault-vault-1/My_Vault-20261015T102030Z.kdbx", 2), result)
        assertNull(backend.stored(copy("20260101T000000Z")))
        assertNull(backend.stored(copy("20260102T000000Z")))
        assertNotNull(backend.stored(copy("20260103T000000Z")))
        assertNotNull(backend.stored(copy("20261015T102030Z")))
    }

    @Test
    fun rotationOrdersTheCopiesByTheDateInTheirName() {
        val backend = InMemoryCloudBackend()
        backend.objects[copy("20260103T000000Z")] = byteArrayOf(3)
        backend.objects[copy("20260101T000000Z")] = byteArrayOf(1)
        backend.objects[copy("20260102T000000Z")] = byteArrayOf(2)

        uploader(backend, options = BackupOptions(keep = 2)).backupNow(VAULT_ID, "My Vault", BYTES)

        assertNull(backend.stored(copy("20260101T000000Z")))
        assertNull(backend.stored(copy("20260102T000000Z")))
        assertNotNull(backend.stored(copy("20260103T000000Z")))
    }

    @Test
    fun rotationNeverTouchesForeignObjectsOrOtherVaults() {
        val backend = InMemoryCloudBackend()
        backend.objects[copy("20260101T000000Z")] = byteArrayOf(1)
        backend.objects["KeyNexa/My_Vault-vault-1/notes.txt"] = byteArrayOf(2)
        backend.objects["KeyNexa/My_Vault-vault-1/My_Vault-20260101T000000Z.kdbx.bak"] = byteArrayOf(3)
        backend.objects["KeyNexa/My_Vault-vault-1/My_Vault-2026-01-01T000000Z.kdbx"] = byteArrayOf(4)
        backend.objects["KeyNexa/Other/Other-20260101T000000Z.kdbx"] = byteArrayOf(5)
        backend.objects["Other/My_Vault/My_Vault-20260101T000000Z.kdbx"] = byteArrayOf(6)
        // A copy made before the folder carried the vault's id, and one of another vault with the same name.
        backend.objects["KeyNexa/My_Vault/My_Vault-20260101T000000Z.kdbx"] = byteArrayOf(7)
        backend.objects["KeyNexa/My_Vault-vault-2/My_Vault-20260101T000000Z.kdbx"] = byteArrayOf(8)

        uploader(backend, options = BackupOptions(keep = 1)).backupNow(VAULT_ID, "My Vault", BYTES)

        assertNull(backend.stored(copy("20260101T000000Z")))
        assertNotNull(backend.stored("KeyNexa/My_Vault-vault-1/notes.txt"))
        assertNotNull(backend.stored("KeyNexa/My_Vault-vault-1/My_Vault-20260101T000000Z.kdbx.bak"))
        assertNotNull(backend.stored("KeyNexa/My_Vault-vault-1/My_Vault-2026-01-01T000000Z.kdbx"))
        assertNotNull(backend.stored("KeyNexa/Other/Other-20260101T000000Z.kdbx"))
        assertNotNull(backend.stored("Other/My_Vault/My_Vault-20260101T000000Z.kdbx"))
        assertNotNull(backend.stored("KeyNexa/My_Vault/My_Vault-20260101T000000Z.kdbx"))
        assertNotNull(backend.stored("KeyNexa/My_Vault-vault-2/My_Vault-20260101T000000Z.kdbx"))
    }

    @Test
    fun keepBelowOneStillKeepsTheNewestCopy() {
        val backend = InMemoryCloudBackend()
        backend.objects[copy("20260101T000000Z")] = byteArrayOf(1)
        backend.objects[copy("20260102T000000Z")] = byteArrayOf(2)

        val result = uploader(backend, options = BackupOptions(keep = 0)).backupNow(VAULT_ID, "My Vault", BYTES)

        assertEquals(BackupResult.Uploaded("KeyNexa/My_Vault-vault-1/My_Vault-20261015T102030Z.kdbx", 2), result)
        assertEquals(listOf("KeyNexa/My_Vault-vault-1/My_Vault-20261015T102030Z.kdbx"), ourCopies(backend))
    }

    @Test
    fun aNetworkFailureIsReportedAndRemembered() {
        val backend = InMemoryCloudBackend()
        val state = InMemoryBackupState()
        backend.failPut = IOException("no network")

        val result = uploader(backend, state).backupNow(VAULT_ID, "My Vault", BYTES)

        assertEquals(BackupResult.Failed(BackupError.Unavailable), result)
        assertEquals(BackupError.Unavailable, state.lastError(VAULT_ID))
        assertNull(state.lastSuccess(VAULT_ID))
    }

    @Test
    fun aCredentialsFailureIsReportedAsNotAuthorized() {
        val backend = InMemoryCloudBackend()
        val state = InMemoryBackupState()
        backend.failPut = CloudAuthException("bad token")

        val result = uploader(backend, state).backupNow(VAULT_ID, "My Vault", BYTES)

        assertEquals(BackupResult.Failed(BackupError.NotAuthorized), result)
        assertEquals(BackupError.NotAuthorized, state.lastError(VAULT_ID))
    }

    @Test
    fun aFailedDeleteDoesNotTurnTheUploadIntoAnError() {
        val backend = InMemoryCloudBackend()
        val state = InMemoryBackupState()
        backend.objects[copy("20260101T000000Z")] = byteArrayOf(1)
        backend.failDelete = IOException("delete denied")

        val result = uploader(backend, state, options = BackupOptions(keep = 1)).backupNow(VAULT_ID, "My Vault", BYTES)

        assertEquals(BackupResult.Uploaded("KeyNexa/My_Vault-vault-1/My_Vault-20261015T102030Z.kdbx", 0), result)
        assertNull(state.lastError(VAULT_ID))
        assertNotNull(backend.stored(copy("20260101T000000Z")))
    }

    @Test
    fun aSuccessfulUploadClearsThePreviousError() {
        val backend = InMemoryCloudBackend()
        val state = InMemoryBackupState()
        state.recordError(VAULT_ID, BackupError.Unavailable, START)

        val result = uploader(backend, state).backupNow(VAULT_ID, "My Vault", BYTES)

        assertTrue(result is BackupResult.Uploaded)
        assertNull(state.lastError(VAULT_ID))
        assertEquals(START, state.lastSuccess(VAULT_ID))
    }

    private fun uploader(
        backend: InMemoryCloudBackend,
        state: BackupState = InMemoryBackupState(),
        options: BackupOptions = BackupOptions(),
        clock: Clock = FixedClock(START),
    ): BackupUploader = BackupUploader(backend, state, options, clock)

    private fun copy(timestamp: String): String = "KeyNexa/My_Vault-vault-1/My_Vault-$timestamp.kdbx"

    private fun ourCopies(backend: InMemoryCloudBackend): List<String> {
        val ours =
            backend.objects.keys.filter {
                it.startsWith("KeyNexa/My_Vault-vault-1/My_Vault-") && it.endsWith(".kdbx")
            }
        return ours.sorted()
    }

    private companion object {
        const val VAULT_ID = "vault-1"
        val BYTES = byteArrayOf(1, 2, 3, 4)
        val START: Instant = Instant.parse("2026-10-15T10:20:30Z")
    }
}
