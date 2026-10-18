package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.BackupError
import io.github.catlinux.keynexa.sync.BackupResult
import io.github.catlinux.keynexa.sync.BackupState
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.InMemoryBackupState
import io.github.catlinux.keynexa.sync.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The backup engine: setup and consent gates, reading failures and the daily limit (and how force ignores it). */
class CloudBackupRunnerTest {
    @Test
    fun withoutASetupNothingHappens() {
        val backend = FakeCloudBackend()

        val result = runner(backend, loadSetup = { null }).run(VAULT_ID, "Vault", { BYTES }, force = false)

        assertNull(result)
        assertEquals(0, backend.puts)
    }

    @Test
    fun withoutConsentNothingHappens() {
        val backend = FakeCloudBackend()

        val result = runner(backend, consentAccepted = { false }).run(VAULT_ID, "Vault", { BYTES }, force = false)

        assertNull(result)
        assertEquals(0, backend.puts)
    }

    @Test
    fun aFailedReadIsAnUnavailableFailureAndIsRecorded() {
        val backend = FakeCloudBackend()
        val state = InMemoryBackupState()

        val result =
            runner(backend, state = state).run(VAULT_ID, "Vault", { throw IOException("unreadable") }, force = false)

        assertEquals(BackupResult.Failed(BackupError.Unavailable), result)
        assertEquals(BackupError.Unavailable, state.lastError(VAULT_ID))
        assertEquals(0, backend.puts)
    }

    @Test
    fun theDailyLimitIsRespectedAndForceIgnoresIt() {
        val backend = FakeCloudBackend()
        val state = InMemoryBackupState()
        val runner = runner(backend, state = state)

        val first = runner.run(VAULT_ID, "Vault", { BYTES }, force = false)
        val second = runner.run(VAULT_ID, "Vault", { BYTES }, force = false)
        val forced = runner.run(VAULT_ID, "Vault", { BYTES }, force = true)

        assertTrue(first is BackupResult.Uploaded)
        assertEquals(BackupResult.Skipped(SkipReason.NotDue), second)
        assertTrue(forced is BackupResult.Uploaded)
        assertEquals(2, backend.puts)
    }

    @Test
    fun theUploadedBytesAreTheOnesRead() {
        val backend = FakeCloudBackend()
        val content = ByteArray(32) { it.toByte() }

        val result = runner(backend).run(VAULT_ID, "Vault", { content }, force = true)

        val uploaded = result as BackupResult.Uploaded
        assertEquals(content.toList(), backend.objects[uploaded.name]?.toList())
    }

    private fun runner(
        backend: CloudBackend,
        state: BackupState = InMemoryBackupState(),
        loadSetup: () -> S3Setup? = { setup() },
        consentAccepted: () -> Boolean = { true },
    ): CloudBackupRunner =
        CloudBackupRunner(
            loadSetup = loadSetup,
            consentAccepted = consentAccepted,
            state = state,
            backendFactory = { backend },
            clock = Clock.fixed(AT, ZoneOffset.UTC),
        )

    private fun setup(): S3Setup =
        S3Setup(
            endpoint = "https://s3.example.com",
            region = "us-east-1",
            bucket = "mybucket",
            pathStyle = true,
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            keep = 5,
        )

    private companion object {
        const val VAULT_ID = "0123456789abcdef"
        val BYTES = byteArrayOf(1, 2, 3, 4)
        val AT: Instant = Instant.parse("2026-10-15T10:20:30Z")
    }
}
