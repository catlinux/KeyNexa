package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The connection test: it cleans up its test object and maps every failure to the right result. */
class ConnectionTestTest {
    @Test
    fun aWorkingBackendIsOkAndTheTestObjectIsDeleted() {
        val backend = FakeCloudBackend()

        val result = testConnection(backend, clock())

        assertEquals(ConnectionResult.Ok, result)
        assertEquals(1, backend.lists)
        assertEquals(1, backend.puts)
        assertEquals(1, backend.deletes)
        assertTrue(backend.objects.isEmpty())
    }

    @Test
    fun aRejectedCredentialIsNotAuthorized() {
        val backend = FakeCloudBackend().apply { failList = CloudAuthException("denied") }

        assertEquals(ConnectionResult.NotAuthorized, testConnection(backend, clock()))
    }

    @Test
    fun aMissingBucketIsWrongBucketOrEndpoint() {
        val backend = FakeCloudBackend().apply { failList = IOException("S3 request failed: HTTP 404 (NoSuchBucket)") }

        assertEquals(ConnectionResult.WrongBucketOrEndpoint, testConnection(backend, clock()))
    }

    @Test
    fun aPlainNotFoundOnListIsWrongBucketOrEndpoint() {
        val backend = FakeCloudBackend().apply { failList = IOException("S3 request failed: HTTP 404") }

        assertEquals(ConnectionResult.WrongBucketOrEndpoint, testConnection(backend, clock()))
    }

    @Test
    fun aNetworkFailureIsUnavailable() {
        val backend = FakeCloudBackend().apply { failList = IOException("the HTTP request failed") }

        assertEquals(ConnectionResult.Unavailable, testConnection(backend, clock()))
    }

    @Test
    fun aFailedDeleteIsUnavailableAndTheObjectWasAttempted() {
        val backend = FakeCloudBackend().apply { failDelete = IOException("denied") }

        assertEquals(ConnectionResult.Unavailable, testConnection(backend, clock()))
        assertEquals(1, backend.deletes)
    }

    @Test
    fun aFailedUploadIsUnavailableWithoutTryingToDelete() {
        val backend = FakeCloudBackend().apply { failPut = IOException("denied") }

        assertEquals(ConnectionResult.Unavailable, testConnection(backend, clock()))
        assertEquals(0, backend.deletes)
    }

    private fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-15T10:20:30Z"), ZoneOffset.UTC)
}
