package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.platform.SecretBox
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.Absorbed
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudContent
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.Expect
import io.github.catlinux.keynexa.sync.SyncError
import io.github.catlinux.keynexa.sync.SyncFile
import io.github.catlinux.keynexa.sync.SyncLocal
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The cloud synchronisation queue: it runs one round per vault, with one more when a trigger arrives mid-round. */
class DesktopCloudSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aTriggerRunsARound() {
        val backend = SyncRecordingBackend()
        val sync = sync(stores(credentials = true, syncEnabled = true, consent = true)) { backend }
        val local = local(ID)

        sync.trigger(vault(), local)
        awaitDone(sync, local)

        assertEquals(1, backend.puts.size)
        assertEquals(NOW_INSTANT, sync.statusOf(local).lastSuccess)
    }

    @Test
    fun aTriggerWhileARoundRunsAsksForExactlyOneMore() {
        val backend = SyncRecordingBackend()
        val sync = sync(stores(credentials = true, syncEnabled = true, consent = true)) { backend }
        val local = local(ID, blockFirst = true)

        sync.trigger(vault(), local)
        awaitRounds(local, 1)
        sync.trigger(vault(), local)
        sync.trigger(vault(), local)
        sync.trigger(vault(), local)
        local.release()
        awaitDone(sync, local)

        assertEquals(2, local.rounds.get())
    }

    @Test
    fun aTriggerForAnotherVaultDoesNotCollide() {
        val backend = SyncRecordingBackend()
        val sync = sync(stores(credentials = true, syncEnabled = true, consent = true)) { backend }
        val first = local(ID, blockFirst = true)
        val second = local(OTHER_ID)

        sync.trigger(vault("one.kdbx"), first)
        awaitRounds(first, 1)
        sync.trigger(vault("two.kdbx"), second)
        first.release()
        awaitDone(sync, first)
        awaitDone(sync, second)

        assertEquals(1, first.rounds.get())
        assertEquals(1, second.rounds.get())
    }

    @Test
    fun nothingIsRecordedWithoutSetupConsentOrTheSwitch() {
        listOf(
            stores(),
            stores(credentials = true, syncEnabled = true),
            stores(credentials = true, syncEnabled = false, consent = true),
        ).forEach { stores ->
            var factoryCalls = 0
            val sync =
                sync(stores) {
                    factoryCalls++
                    SyncRecordingBackend()
                }
            val local = local(ID)

            sync.trigger(vault(), local)
            awaitDone(sync, local)

            assertEquals(0, factoryCalls)
            assertNull(sync.statusOf(local).lastSuccess)
            assertNull(sync.statusOf(local).lastError)
        }
    }

    @Test
    fun statusOfReflectsAFailure() {
        val sync =
            sync(
                stores(credentials = true, syncEnabled = true, consent = true),
            ) { SyncRecordingBackend(headFails = true) }
        val local = local(ID)

        sync.trigger(vault(), local)
        awaitDone(sync, local)

        assertEquals(SyncError.Unavailable, sync.statusOf(local).lastError)
        assertEquals(NOW_INSTANT, sync.statusOf(local).lastErrorAt)
    }

    @Test
    fun resetForgetsEveryVault() {
        val stores = stores(credentials = true, syncEnabled = true, consent = true)
        val sync = sync(stores) { SyncRecordingBackend() }
        val local = local(ID)
        sync.trigger(vault(), local)
        awaitDone(sync, local)

        sync.reset()

        assertNull(sync.statusOf(local).lastSuccess)
        assertNull(stores.syncState.lastSuccess(ID))
        assertFalse(sync.status.value.running)
    }

    /** Waits until the queue has finished with [local]'s vault, so the test does not race the daemon thread. */
    private fun awaitDone(
        sync: DesktopCloudSync,
        local: SyncLocal,
    ) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (sync.statusOf(local).running && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertFalse(sync.statusOf(local).running)
    }

    /** Waits until [local] has started its round number [count]. */
    private fun awaitRounds(
        local: CountingLocal,
        count: Int,
    ) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (local.rounds.get() < count && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertTrue(local.rounds.get() >= count)
    }

    private fun sync(
        stores: DesktopCloudStores,
        factory: (S3Setup) -> CloudBackend,
    ): DesktopCloudSync =
        DesktopCloudSync(
            stores = stores,
            backendFactory = factory,
            clock = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC),
        )

    private fun stores(
        credentials: Boolean = false,
        syncEnabled: Boolean = false,
        consent: Boolean = false,
    ): DesktopCloudStores =
        DesktopCloudStores(data(), SyncTestSecretBox()).apply {
            if (credentials) this.credentials.save(setupValues(syncEnabled))
            if (consent) this.consent.accept()
        }

    private fun setupValues(syncEnabled: Boolean): Map<String, String> =
        S3Setup(
            endpoint = ENDPOINT,
            region = "eu-west-1",
            bucket = "keynexa",
            pathStyle = true,
            accessKeyId = "access",
            secretAccessKey = "secret",
            sync = syncEnabled,
            syncProbe = if (syncEnabled) S3Setup.probeKey(ENDPOINT, "keynexa") else null,
        ).toValues()

    private fun data(): DesktopDirs =
        object : DesktopDirs {
            override val data: Path = folder.newFolder().toPath()
        }

    private fun vault(name: String = "personal.kdbx"): KnownVault =
        KnownVault("Personal", VaultLocation(folder.root.resolve(name).absolutePath))

    private fun local(
        id: String,
        blockFirst: Boolean = false,
    ): CountingLocal = CountingLocal(id, blockFirst)

    private companion object {
        const val ENDPOINT = "https://s3.example.org"
        const val ID = "0123456789abcdef0123456789abcdef"
        const val OTHER_ID = "fedcba9876543210fedcba9876543210"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
        val NOW_INSTANT: Instant = Instant.parse("2026-10-19T20:00:00Z")
        val CONTENT = "vault-bytes".toByteArray()
    }
}

/** A [SyncLocal] that counts its rounds and can hold the first one until the test releases it. */
private class CountingLocal(
    private val syncIdValue: String,
    private val blockFirst: Boolean,
) : SyncLocal {
    val rounds = AtomicInteger()
    private val gate = CountDownLatch(1)
    private val blocked = AtomicBoolean(false)

    override fun syncId(): String = syncIdValue

    override fun currentFile(): SyncFile {
        rounds.incrementAndGet()
        if (blockFirst && blocked.compareAndSet(false, true)) gate.await()
        return SyncFile(CONTENT)
    }

    override fun absorb(
        bytes: ByteArray,
        expect: Expect,
    ): Absorbed = Absorbed.Merged(SyncFile(bytes), remoteLacksLocal = false)

    override fun assignSyncId(id: String): Boolean = true

    /** Lets the first round finish. */
    fun release() = gate.countDown()

    private companion object {
        val CONTENT = "vault-bytes".toByteArray()
    }
}

/** A cloud store that only remembers what it was asked to put, and can fail the first look. */
private class SyncRecordingBackend(
    private val headFails: Boolean = false,
) : CloudBackend {
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = true)

    val puts: MutableList<ByteArray> = mutableListOf()

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
        ifNoneMatch: Boolean,
    ): CloudObject {
        puts += content.copyOf()
        return CloudObject(name, content.size.toLong(), null, "etag-${puts.size}")
    }

    override fun head(name: String): CloudObject? {
        if (headFails) throw IOException("no head")
        return null
    }

    override fun fetch(name: String): CloudContent? = null

    override fun get(name: String): ByteArray = throw IOException("not stored")

    override fun list(prefix: String): List<CloudObject> = emptyList()

    override fun delete(name: String) = Unit
}

/** A [SecretBox] for the tests: it marks what it seals, so a blob from anywhere else cannot be opened. */
private class SyncTestSecretBox : SecretBox {
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
