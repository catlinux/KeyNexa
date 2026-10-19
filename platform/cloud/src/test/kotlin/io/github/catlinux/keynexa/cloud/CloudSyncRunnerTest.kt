package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.Absorbed
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudSyncState
import io.github.catlinux.keynexa.sync.Expect
import io.github.catlinux.keynexa.sync.InMemoryCloudSyncState
import io.github.catlinux.keynexa.sync.SyncError
import io.github.catlinux.keynexa.sync.SyncFile
import io.github.catlinux.keynexa.sync.SyncIds
import io.github.catlinux.keynexa.sync.SyncLocal
import io.github.catlinux.keynexa.sync.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The sync runner: the gates, the id, the adoption by root group and the malformed id (ADR-0009). */
class CloudSyncRunnerTest {
    @Test
    fun withoutASetupNothingHappens() {
        val backend = FakeCloudBackend()
        val local = FakeSyncLocal(setOf("base"))

        val result = runner(backend, loadSetup = { null }).run(VAULT_ID, local)

        assertNull(result)
        assertEquals(0, backend.lists)
        assertEquals(0, backend.puts)
    }

    @Test
    fun withoutConsentNothingHappens() {
        val backend = FakeCloudBackend()
        val local = FakeSyncLocal(setOf("base"))

        val result = runner(backend, consentAccepted = { false }).run(VAULT_ID, local)

        assertNull(result)
        assertEquals(0, backend.lists)
        assertEquals(0, backend.puts)
    }

    @Test
    fun whenSynchronisationIsNotEnabledNothingHappens() {
        val backend = FakeCloudBackend()
        val local = FakeSyncLocal(setOf("base"))
        val setup = setup().copy(syncProbe = "https://other.example.com|mybucket")

        val result = runner(backend, loadSetup = { setup }).run(VAULT_ID, local)

        assertNull(result)
        assertEquals(0, backend.lists)
        assertEquals(0, backend.puts)
    }

    @Test
    fun aVaultWithoutAnIdGetsANewOne() {
        val backend = FakeCloudBackend()
        val local = FakeSyncLocal(setOf("base"))

        val result = runner(backend).run(VAULT_ID, local)

        val assigned = local.assigned
        assertNotNull(assigned)
        assertEquals(assigned, local.syncId())
        assertEquals(SyncResult.Uploaded, result)
        assertTrue(backend.objects.containsKey(SyncIds.objectName(requireNotNull(assigned))))
    }

    @Test
    fun anExistingObjectWithTheSameRootIsAdopted() {
        val backend = FakeCloudBackend()
        backend.put(SyncIds.objectName(SYNC_ID), encode(setOf("base", "remote")), null, false)
        val local = FakeSyncLocal(setOf("base"))

        val result = runner(backend).run(VAULT_ID, local)

        assertNull(local.assigned)
        assertEquals(setOf("base", "remote"), local.tokens)
        assertEquals(SyncResult.Downloaded, result)
    }

    @Test
    fun anObjectWithAnotherRootIsNotAdopted() {
        val backend = FakeCloudBackend()
        backend.put(SyncIds.objectName(SYNC_ID), encode(setOf("other")), null, false)
        val local = FakeSyncLocal(setOf("base"), sameRoot = false)

        val result = runner(backend).run(VAULT_ID, local)

        val assigned = local.assigned
        assertNotNull(assigned)
        assertTrue(backend.objects.containsKey(SyncIds.objectName(requireNotNull(assigned))))
        assertEquals(SyncResult.Uploaded, result)
    }

    @Test
    fun aMalformedIdIsRejectedWithoutTouchingTheBackend() {
        val backend = FakeCloudBackend()
        val local = FakeSyncLocal(setOf("base"), id = "not-an-id")

        val result = runner(backend).run(VAULT_ID, local)

        assertEquals(SyncResult.Failed(SyncError.InvalidSyncId), result)
        assertEquals(0, backend.lists)
        assertEquals(0, backend.puts)
    }

    private fun runner(
        backend: CloudBackend,
        state: CloudSyncState = InMemoryCloudSyncState(),
        loadSetup: () -> S3Setup? = { setup() },
        consentAccepted: () -> Boolean = { true },
    ): CloudSyncRunner =
        CloudSyncRunner(
            loadSetup = loadSetup,
            consentAccepted = consentAccepted,
            state = state,
            backendFactory = { backend },
            clock = Clock.fixed(AT, ZoneOffset.UTC),
        )

    private fun setup(): S3Setup =
        S3Setup(
            endpoint = ENDPOINT,
            region = "us-east-1",
            bucket = BUCKET,
            pathStyle = true,
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            keep = 5,
            sync = true,
            syncProbe = S3Setup.probeKey(ENDPOINT, BUCKET),
        )

    private companion object {
        const val VAULT_ID = "0123456789abcdef"
        const val SYNC_ID = "0123456789abcdef0123456789abcdef"
        const val ENDPOINT = "https://s3.example.com"
        const val BUCKET = "mybucket"
        val AT: Instant = Instant.parse("2026-10-19T10:20:30Z")
    }
}

/** A vault whose content is a set of tokens: merging is the union, so equality and "lacks" are easy to reason about. */
private class FakeSyncLocal(
    var tokens: Set<String>,
    private var id: String? = null,
    var sameRoot: Boolean = true,
) : SyncLocal {
    var assigned: String? = null
        private set

    override fun syncId(): String? = id

    override fun currentFile(): SyncFile = SyncFile(encode(tokens))

    override fun absorb(
        bytes: ByteArray,
        expect: Expect,
    ): Absorbed {
        if (expect == Expect.SameRoot && !sameRoot) return Absorbed.NotSameVault
        val remote = decode(bytes)
        tokens = tokens + remote
        return Absorbed.Merged(SyncFile(encode(tokens)), remoteLacksLocal = tokens != remote)
    }

    override fun assignSyncId(id: String) {
        this.id = id
        assigned = id
    }
}

private fun encode(tokens: Set<String>): ByteArray = tokens.sorted().joinToString(",").toByteArray()

private fun decode(bytes: ByteArray): Set<String> = String(bytes).split(",").filter { it.isNotEmpty() }.toSet()
