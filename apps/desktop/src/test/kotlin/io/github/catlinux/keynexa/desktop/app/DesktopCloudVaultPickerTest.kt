package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudContent
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.SyncIds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

/** The list of synchronised vaults, the download of one and the sizes the picker shows. */
class DesktopCloudVaultPickerTest {
    @Test
    fun keepsOnlyWellFormedNames() {
        val objects =
            listOf(
                CloudObject(SyncIds.objectName(ID_A), 10, OLDER, "e1"),
                CloudObject("KeyNexa/sync/.probe-0123456789abcdef", 10, OLDER, "e2"),
                CloudObject("KeyNexa/sync/0123456789abcdef.kdbx", 10, OLDER, "e3"),
                CloudObject("KeyNexa/sync/${ID_A.uppercase()}.kdbx", 10, OLDER, "e4"),
                CloudObject("KeyNexa/sync/$ID_A.kdbx.bak", 10, OLDER, "e5"),
                CloudObject("KeyNexa/sync/${ID_A}extra.kdbx", 10, OLDER, "e6"),
                CloudObject("KeyNexa/backup/$ID_A.kdbx", 10, OLDER, "e7"),
            )

        assertEquals(
            listOf(CloudVault(ID_A, SyncIds.objectName(ID_A), 10, OLDER)),
            cloudVaults(objects),
        )
    }

    @Test
    fun ordersNewestFirstWithNoDateLast() {
        val objects =
            listOf(
                CloudObject(SyncIds.objectName(ID_A), 1, OLDER, "e1"),
                CloudObject(SyncIds.objectName(ID_C), 3, null, "e3"),
                CloudObject(SyncIds.objectName(ID_B), 2, NEWER, "e2"),
            )

        assertEquals(
            listOf(
                CloudVault(ID_B, SyncIds.objectName(ID_B), 2, NEWER),
                CloudVault(ID_A, SyncIds.objectName(ID_A), 1, OLDER),
                CloudVault(ID_C, SyncIds.objectName(ID_C), 3, null),
            ),
            cloudVaults(objects),
        )
    }

    @Test
    fun breaksTiesByName() {
        val objects =
            listOf(
                CloudObject(SyncIds.objectName(ID_B), 1, NEWER, "e1"),
                CloudObject(SyncIds.objectName(ID_A), 1, NEWER, "e2"),
            )

        assertEquals(listOf(ID_A, ID_B), cloudVaults(objects).map { it.id })
    }

    @Test
    fun shortensTheIdToItsFirstEightCharacters() {
        assertEquals("01234567", shortVaultId(ID_A))
    }

    @Test
    fun suggestsAFileNameFromTheShortId() {
        assertEquals("KeyNexa-01234567.kdbx", suggestedVaultFileName(ID_A))
    }

    @Test
    fun writesTheExactBytesToANewFile() =
        withTempFolder { folder ->
            val destination = File(folder, "vault$VAULT_EXTENSION")
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val backend = PickerCloudStub(CloudContent(bytes, "e1"))

            val outcome = runBlocking { downloadCloudVault(backend, SyncIds.objectName(ID_A), destination) }

            assertEquals(DownloadOutcome.Saved, outcome)
            assertArrayEquals(bytes, destination.readBytes())
        }

    @Test
    fun neverTouchesAFileThatIsAlreadyThere() =
        withTempFolder { folder ->
            val destination = File(folder, "vault$VAULT_EXTENSION")
            val sentinel = byteArrayOf(9, 9, 9)
            destination.writeBytes(sentinel)
            val backend = PickerCloudStub(CloudContent(byteArrayOf(1, 2, 3), "e1"))

            val outcome = runBlocking { downloadCloudVault(backend, SyncIds.objectName(ID_A), destination) }

            assertEquals(DownloadOutcome.AlreadyExists, outcome)
            assertArrayEquals(sentinel, destination.readBytes())
            assertEquals(0, backend.fetchCount)
        }

    @Test
    fun writesNothingWhenTheObjectIsGone() =
        withTempFolder { folder ->
            val destination = File(folder, "vault$VAULT_EXTENSION")
            val backend = PickerCloudStub(null)

            val outcome = runBlocking { downloadCloudVault(backend, SyncIds.objectName(ID_A), destination) }

            assertEquals(DownloadOutcome.Gone, outcome)
            assertFalse(destination.exists())
        }

    @Test
    fun formatsSizesInBytesKilobytesAndMegabytes() {
        assertEquals("512 B", formatSize(512))
        assertEquals("1.0 kB", formatSize(1024))
        assertEquals("1.5 kB", formatSize(1536))
        assertEquals("2.0 MB", formatSize(2L * 1024L * 1024L))
    }

    private fun <T> withTempFolder(block: (File) -> T): T {
        val folder = Files.createTempDirectory("keynexa-picker").toFile()
        try {
            return block(folder)
        } finally {
            folder.deleteRecursively()
        }
    }

    private companion object {
        const val ID_A = "0123456789abcdef0123456789abcdef"
        const val ID_B = "fedcba9876543210fedcba9876543210"
        const val ID_C = "aabbccddeeff00112233445566778899"
        val OLDER: Instant = Instant.parse("2026-10-17T10:00:00Z")
        val NEWER: Instant = Instant.parse("2026-10-19T10:00:00Z")
    }
}

/** A [CloudBackend] that answers [fetch] with a fixed content and refuses everything else the picker never uses. */
private class PickerCloudStub(
    private val content: CloudContent?,
) : CloudBackend {
    override val capabilities = CloudCapabilities(conditionalWrites = false)

    /** How many times [fetch] was asked; a picker that must not touch the network keeps it at zero. */
    var fetchCount = 0
        private set

    override fun fetch(name: String): CloudContent? {
        fetchCount++
        return content
    }

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
        ifNoneMatch: Boolean,
    ): CloudObject = throw UnsupportedOperationException("the picker never writes")

    override fun head(name: String): CloudObject? = throw UnsupportedOperationException("the picker never heads")

    override fun get(name: String): ByteArray = throw UnsupportedOperationException("the picker never reads")

    override fun list(prefix: String): List<CloudObject> = throw UnsupportedOperationException("the picker never lists")

    override fun delete(name: String) = throw UnsupportedOperationException("the picker never deletes")
}
