package io.github.catlinux.keynexa.app.cloud

import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudContent
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.SyncIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant

/** Listing the synchronised vaults and downloading one: the pure part of "Bring a vault from the cloud". */
class CloudVaultsTest {
    @Test
    fun theListKeepsOnlyWellFormedSynchronisedNames() {
        val valid = "0123456789abcdef0123456789abcdef"
        val objects =
            listOf(
                objectOf(SyncIds.objectName(valid), 10, NOW),
                objectOf("KeyNexa/sync/.probe-0123456789abcdef", 1, NOW),
                objectOf("KeyNexa/sync/0123.kdbx", 1, NOW),
                objectOf("KeyNexa/sync/0123456789ABCDEF0123456789ABCDEF.kdbx", 1, NOW),
                objectOf("KeyNexa/sync/$valid.kdbx.bak", 1, NOW),
                objectOf("other/$valid.kdbx", 1, NOW),
            )

        assertEquals(listOf(valid), cloudVaults(objects).map { it.id })
    }

    @Test
    fun theListIsNewestFirstWithTheUndatedLastAndTiesByName() {
        val older = "11111111111111111111111111111111"
        val newer = "22222222222222222222222222222222"
        val undated = "33333333333333333333333333333333"
        val tieA = "44444444444444444444444444444444"
        val tieB = "55555555555555555555555555555555"
        val objects =
            listOf(
                objectOf(SyncIds.objectName(older), 1, NOW.minusSeconds(3600)),
                objectOf(SyncIds.objectName(newer), 1, NOW),
                objectOf(SyncIds.objectName(undated), 1, null),
                objectOf(SyncIds.objectName(tieB), 1, NOW.minusSeconds(60)),
                objectOf(SyncIds.objectName(tieA), 1, NOW.minusSeconds(60)),
            )

        assertEquals(listOf(newer, tieA, tieB, older, undated), cloudVaults(objects).map { it.id })
    }

    @Test
    fun shortVaultIdTakesTheFirstEightCharacters() {
        assertEquals("01234567", shortVaultId("0123456789abcdef0123456789abcdef"))
        assertEquals("0123", shortVaultId("0123"))
    }

    @Test
    fun theSuggestedNameIsTheAppAndTheShortId() {
        assertEquals("KeyNexa-01234567.kdbx", suggestedVaultFileName("0123456789abcdef0123456789abcdef"))
    }

    @Test
    fun downloadWritesTheExactBytes() =
        runBlocking {
            val bytes = byteArrayOf(0x01, 0x02, 0x03)
            val out = ByteArrayOutputStream()

            val outcome = downloadCloudVault(ListingCloudBackend(content = CloudContent(bytes, "etag-1")), NAME) { out }

            assertEquals(CloudDownloadOutcome.Saved, outcome)
            assertArrayEquals(bytes, out.toByteArray())
        }

    @Test
    fun aMissingObjectWritesNothing() =
        runBlocking {
            val out = ByteArrayOutputStream()

            val outcome = downloadCloudVault(ListingCloudBackend(content = null), NAME) { out }

            assertEquals(CloudDownloadOutcome.Gone, outcome)
            assertEquals(0, out.size())
        }

    @Test
    fun aFailedFetchIsReportedAsFailed() =
        runBlocking {
            val out = ByteArrayOutputStream()

            val outcome = downloadCloudVault(FailingCloudBackend(), NAME) { out }

            assertTrue(outcome is CloudDownloadOutcome.Failed)
            assertEquals(0, out.size())
        }

    @Test
    fun formatSizeUsesTheRightUnit() {
        assertEquals("0 B", formatSize(0))
        assertEquals("1023 B", formatSize(1023))
        assertEquals("1.0 kB", formatSize(1024))
        assertEquals("1.5 kB", formatSize(1536))
        assertEquals("1.0 MB", formatSize(1024L * 1024L))
    }

    private fun objectOf(
        name: String,
        size: Long,
        modifiedAt: Instant?,
    ) = CloudObject(name = name, size = size, modifiedAt = modifiedAt, etag = "etag")

    private companion object {
        const val NAME = "KeyNexa/sync/0123456789abcdef0123456789abcdef.kdbx"
        val NOW: Instant = Instant.parse("2026-10-19T10:00:00Z")
    }
}

/** A backend that only lists and fetches what the test put in it; its writes are never reached. */
private class ListingCloudBackend(
    private val objects: List<CloudObject> = emptyList(),
    private val content: CloudContent? = null,
) : CloudBackend {
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = true)

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
        ifNoneMatch: Boolean,
    ): CloudObject = throw UnsupportedOperationException("put is not used by these tests")

    override fun head(name: String): CloudObject? = objects.firstOrNull { it.name == name }

    override fun fetch(name: String): CloudContent? = content

    override fun get(name: String): ByteArray = content?.bytes ?: ByteArray(0)

    override fun list(prefix: String): List<CloudObject> = objects.filter { it.name.startsWith(prefix) }

    override fun delete(name: String) = Unit
}

/** A backend whose fetch fails, to check that a download reports the failure instead of writing anything. */
private class FailingCloudBackend : CloudBackend {
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = false)

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
        ifNoneMatch: Boolean,
    ): CloudObject = throw UnsupportedOperationException("put is not used by these tests")

    override fun head(name: String): CloudObject? = null

    override fun fetch(name: String): CloudContent? = throw IOException("no connection")

    override fun get(name: String): ByteArray = throw IOException("no connection")

    override fun list(prefix: String): List<CloudObject> = throw IOException("no connection")

    override fun delete(name: String) = Unit
}
