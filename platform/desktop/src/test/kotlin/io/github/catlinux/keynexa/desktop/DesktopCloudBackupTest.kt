package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.vaultIdOf
import io.github.catlinux.keynexa.platform.SecretBox
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.BackupError
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The cloud queue: it only uploads with a valid setup and an accepted consent, and it reports what it did. */
class DesktopCloudBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun doesNothingWithoutCredentials() {
        val backend = RecordingBackend()
        val backup = backup(backend)

        backup.backupNow(vault(), read = { CONTENT })
        awaitDone(backup)

        assertTrue(backend.puts.isEmpty())
        assertNull(backup.status.value.lastSuccess)
        assertFalse(backup.hasAnyBackup())
    }

    @Test
    fun doesNothingWithoutConsent() {
        val backend = RecordingBackend()
        val stores = stores()
        stores.credentials.save(setupValues())
        val backup = backup(backend, stores)

        backup.backupNow(vault(), read = { CONTENT })
        awaitDone(backup)

        assertTrue(backend.puts.isEmpty())
    }

    @Test
    fun uploadsOnceBothAreThere() {
        val backend = RecordingBackend()
        val stores = stores()
        stores.credentials.save(setupValues())
        stores.consent.accept()
        val backup = backup(backend, stores)

        backup.backupNow(vault(), read = { CONTENT })
        awaitDone(backup)

        assertEquals(1, backend.puts.size)
        assertTrue(backend.puts.single().contentEquals(CONTENT))
        assertEquals(Instant.parse(NOW), backup.statusOf(vaultIdOf(vault().location.value)).lastSuccess)
        assertTrue(backup.hasAnyBackup())
    }

    @Test
    fun reportsAFailureToReadTheVault() {
        val backend = RecordingBackend()
        val stores = stores()
        stores.credentials.save(setupValues())
        stores.consent.accept()
        val backup = backup(backend, stores)

        backup.backupNow(vault(), read = { throw IOException("cannot read") })
        awaitDone(backup)

        assertEquals(BackupError.Unavailable, backup.statusOf(vaultIdOf(vault().location.value)).lastError)
        assertTrue(backend.puts.isEmpty())
    }

    @Test
    fun anUnexpectedFailureNeitherEndsTheWorkerNorLeavesItRunning() {
        val backend = RecordingBackend()
        val stores = stores()
        stores.credentials.save(setupValues())
        stores.consent.accept()
        val backup = backup(backend, stores)

        backup.backupNow(vault(), read = { throw IllegalStateException("unexpected") })
        awaitDone(backup)

        assertFalse(backup.status.value.running)
        assertEquals(BackupError.Unavailable, backup.statusOf(vaultIdOf(vault().location.value)).lastError)
        backup.backupNow(vault(), read = { CONTENT })
        awaitDone(backup)
        assertEquals(1, backend.puts.size)
    }

    @Test
    fun resetForgetsEveryVault() {
        val backend = RecordingBackend()
        val stores = stores()
        stores.credentials.save(setupValues())
        stores.consent.accept()
        val backup = backup(backend, stores)
        backup.backupNow(vault(), read = { CONTENT })
        awaitDone(backup)

        backup.reset()

        assertFalse(backup.hasAnyBackup())
        assertNull(backup.status.value.lastSuccess)
    }

    /** Waits until the queue has finished with the vault, so the test does not race the daemon thread. */
    private fun awaitDone(backup: DesktopCloudBackup) {
        val vaultId = vaultIdOf(vault().location.value)
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (backup.statusOf(vaultId).running && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertFalse(backup.statusOf(vaultId).running)
    }

    private fun setupValues(): Map<String, String> =
        S3Setup(
            endpoint = "https://s3.example.org",
            region = "eu-west-1",
            bucket = "keynexa",
            pathStyle = true,
            accessKeyId = "access",
            secretAccessKey = "secret",
        ).toValues()

    private fun backup(
        backend: RecordingBackend,
        stores: DesktopCloudStores = stores(),
    ): DesktopCloudBackup =
        DesktopCloudBackup(
            stores = stores,
            backendFactory = { backend },
            clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC),
        )

    private fun stores(): DesktopCloudStores = DesktopCloudStores(data(), TestSecretBox())

    private fun data(): DesktopDirs =
        object : DesktopDirs {
            override val data: Path = folder.newFolder("data").toPath()
        }

    private fun vault(): KnownVault =
        KnownVault("Personal", VaultLocation(folder.root.resolve("personal.kdbx").absolutePath))

    private companion object {
        const val NOW = "2026-10-18T20:00:00Z"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
        val CONTENT = "vault-bytes".toByteArray()
    }
}

/** A cloud store that only remembers what it was asked to put. */
private class RecordingBackend : CloudBackend {
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = false)

    val puts: MutableList<ByteArray> = mutableListOf()

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
    ): CloudObject {
        puts += content.copyOf()
        return CloudObject(name, content.size.toLong(), null, "etag-${puts.size}")
    }

    override fun get(name: String): ByteArray = throw IOException("not stored")

    override fun list(prefix: String): List<CloudObject> = emptyList()

    override fun delete(name: String) = Unit
}

/** A [SecretBox] for the tests: it marks what it seals, so a blob from anywhere else cannot be opened. */
private class TestSecretBox : SecretBox {
    override fun seal(plain: ByteArray): ByteArray = MARKER + plain

    override fun open(sealed: ByteArray): ByteArray? =
        if (sealed.size >= MARKER.size && sealed.copyOfRange(0, MARKER.size).contentEquals(MARKER)) {
            sealed.copyOfRange(MARKER.size, sealed.size)
        } else {
            null
        }

    private companion object {
        val MARKER = "sealed:".toByteArray()
    }
}
