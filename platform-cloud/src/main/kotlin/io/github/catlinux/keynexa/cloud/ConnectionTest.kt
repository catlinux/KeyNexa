@file:Suppress("MatchingDeclarationName")

package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.CloudBackend
import java.io.IOException
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Checks that a configured backend can be used: it lists the backup folder, uploads a small test object and deletes
 * it. The test object is removed even when a later step fails, and only the result is returned: no content, URL or
 * header is logged or kept.
 */
@Suppress("ReturnCount")
fun testConnection(
    backend: CloudBackend,
    clock: Clock = Clock.systemUTC(),
    prefix: String = "KeyNexa/",
): ConnectionResult {
    val name = "$prefix.connection-test-${TIMESTAMP.format(clock.instant())}"
    try {
        backend.list(prefix)
    } catch (unauthorized: CloudAuthException) {
        return ConnectionResult.NotAuthorized
    } catch (failure: IOException) {
        return listFailure(failure)
    }
    try {
        backend.put(name, TEST_CONTENT)
    } catch (unauthorized: CloudAuthException) {
        return ConnectionResult.NotAuthorized
    } catch (failure: IOException) {
        return ConnectionResult.Unavailable
    }
    // The object exists now: always try to remove it, whatever happens next.
    return try {
        backend.delete(name)
        ConnectionResult.Ok
    } catch (unauthorized: CloudAuthException) {
        ConnectionResult.NotAuthorized
    } catch (failure: IOException) {
        ConnectionResult.Unavailable
    }
}

private fun listFailure(failure: IOException): ConnectionResult {
    val message = failure.message.orEmpty()
    return if (message.contains(NO_SUCH_BUCKET) || message.contains(NOT_FOUND)) {
        ConnectionResult.WrongBucketOrEndpoint
    } else {
        ConnectionResult.Unavailable
    }
}

private val TIMESTAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
private val TEST_CONTENT = "keynexa-connection-test".toByteArray()
private const val NO_SUCH_BUCKET = "NoSuchBucket"
private const val NOT_FOUND = "404"
