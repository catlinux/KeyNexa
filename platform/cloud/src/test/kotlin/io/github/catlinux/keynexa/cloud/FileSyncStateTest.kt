package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.SyncError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** The persistent sync state: it survives instances, forgets errors and tolerates a broken file. */
class FileSyncStateTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun theStatePersistsBetweenInstances() {
        FileSyncState(file()).recordSuccess(SYNC_ID, ETAG, FINGERPRINT, AT)

        val state = FileSyncState(file())

        assertEquals(ETAG, state.lastEtag(SYNC_ID))
        assertEquals(FINGERPRINT, state.lastLocalFingerprint(SYNC_ID))
        assertEquals(AT, state.lastSuccess(SYNC_ID))
    }

    @Test
    fun anErrorIsRememberedAndThenCleared() {
        val state = FileSyncState(file())
        state.recordError(SYNC_ID, SyncError.Unavailable, AT)

        assertEquals(SyncError.Unavailable, state.lastError(SYNC_ID))
        assertEquals(AT, state.lastErrorAt(SYNC_ID))

        state.recordSuccess(SYNC_ID, ETAG, FINGERPRINT, AT.plusSeconds(1))

        assertNull(state.lastError(SYNC_ID))
        assertNull(state.lastErrorAt(SYNC_ID))
        assertNull(FileSyncState(file()).lastError(SYNC_ID))
    }

    @Test
    fun idsAreKeptApart() {
        val state = FileSyncState(file())
        state.recordSuccess("one", ETAG, FINGERPRINT, AT)
        state.recordError("two", SyncError.NotAuthorized, AT.plusSeconds(1))

        assertNull(state.lastEtag("two"))
        assertNull(state.lastError("one"))
        assertEquals(ETAG, state.lastEtag("one"))
        assertEquals(SyncError.NotAuthorized, state.lastError("two"))
    }

    @Test
    fun aMissingFileMeansNoState() {
        assertNull(FileSyncState(file()).lastEtag(SYNC_ID))
    }

    @Test
    fun aCorruptFileMeansNoStateAndNeverThrows() {
        file().parentFile?.mkdirs()
        file().writeText("this is not a properties file\n\u0000")

        assertNull(FileSyncState(file()).lastEtag(SYNC_ID))
        assertNull(FileSyncState(file()).lastError(SYNC_ID))
    }

    @Test
    fun writesLeaveNoTemporaryFileBehind() {
        FileSyncState(file()).recordSuccess(SYNC_ID, ETAG, FINGERPRINT, AT)

        val names = requireNotNull(file().parentFile).list().orEmpty()
        assertFalse(names.any { it.endsWith(".tmp") })
        assertTrue(file().isFile)
    }

    @Test
    fun clearAllForgetsEverythingAndDeletesTheFile() {
        val state = FileSyncState(file())
        state.recordSuccess(SYNC_ID, ETAG, FINGERPRINT, AT)
        state.recordError(SYNC_ID, SyncError.Unavailable, AT)

        state.clearAll()

        assertNull(state.lastEtag(SYNC_ID))
        assertNull(state.lastError(SYNC_ID))
        assertFalse(file().exists())
        assertNull(FileSyncState(file()).lastSuccess(SYNC_ID))
    }

    @Test
    fun clearAllOnAMissingFileDoesNothing() {
        FileSyncState(file()).clearAll()

        assertFalse(file().exists())
    }

    @Test
    fun aNullEtagIsForgotten() {
        val state = FileSyncState(file())
        state.recordSuccess(SYNC_ID, ETAG, FINGERPRINT, AT)

        state.recordSuccess(SYNC_ID, null, FINGERPRINT, AT)

        assertNull(FileSyncState(file()).lastEtag(SYNC_ID))
    }

    private fun file(): File = File(requireNotNull(temporary.root), "cloud/sync-state.properties")

    private companion object {
        const val SYNC_ID = "0123456789abcdef0123456789abcdef"
        const val ETAG = "etag-1"
        val FINGERPRINT = Fingerprint.of(byteArrayOf(1, 2, 3))
        val AT: Instant = Instant.parse("2026-10-19T10:20:30Z")
    }
}
