package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudContent
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.CloudPreconditionFailed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The conditional-write probe: a service that honours the headers, one that ignores them and one that rejects them. */
class ConditionalWriteProbeTest {
    @Test
    fun aServiceThatHonoursTheHeadersIsSupported() {
        val backend = ProbeBackend(Mode.HONOURS)

        val result = ConditionalWriteProbe(backend).run()

        assertEquals(ProbeResult.Supported, result)
        assertEquals(1, backend.deletes)
    }

    @Test
    fun aServiceThatIgnoresTheHeadersIsNotSupported() {
        val backend = ProbeBackend(Mode.IGNORES)

        val result = ConditionalWriteProbe(backend).run()

        assertEquals(ProbeResult.NotSupported(2), result)
        assertEquals(1, backend.deletes)
    }

    @Test
    fun aServiceThatRejectsTheHeadersIsNotSupported() {
        val backend = ProbeBackend(Mode.REJECTS)

        val result = ConditionalWriteProbe(backend).run()

        assertEquals(ProbeResult.NotSupported(1), result)
        assertEquals(1, backend.deletes)
    }

    @Test
    fun aFailedCleanupDoesNotChangeTheResult() {
        val backend = ProbeBackend(Mode.HONOURS)
        backend.failDelete = IOException("cleanup failed")

        val result = ConditionalWriteProbe(backend).run()

        assertEquals(ProbeResult.Supported, result)
        assertEquals(1, backend.deletes)
    }

    @Test
    fun aCredentialsFailureIsFailed() {
        val backend = ProbeBackend(Mode.HONOURS)
        backend.failPut = CloudAuthException("denied")

        val result = ConditionalWriteProbe(backend).run()

        assertTrue(result is ProbeResult.Failed)
        assertTrue((result as ProbeResult.Failed).error is CloudAuthException)
    }

    @Test
    fun aNetworkFailureIsFailed() {
        val backend = ProbeBackend(Mode.HONOURS)
        backend.failPut = IOException("no network")

        val result = ConditionalWriteProbe(backend).run()

        val failed = result as ProbeResult.Failed
        assertEquals("no network", failed.error.message)
    }

    @Test
    fun theTemporaryObjectIsUnderTheSyncPrefixAndUnique() {
        val first = ProbeBackend(Mode.HONOURS)
        val second = ProbeBackend(Mode.HONOURS)

        ConditionalWriteProbe(first).run()
        ConditionalWriteProbe(second).run()

        val name = requireNotNull(first.lastName)
        assertTrue(name.startsWith("KeyNexa/sync/.probe-"))
        assertEquals("KeyNexa/sync/.probe-".length + 16, name.length)
        assertTrue(name != second.lastName)
    }
}

/** How the fake treats conditional headers. */
private enum class Mode { HONOURS, IGNORES, REJECTS }

/** An in-memory store that honours, ignores or rejects `If-Match` and `If-None-Match`. */
private class ProbeBackend(
    private val mode: Mode,
) : CloudBackend {
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = mode == Mode.HONOURS)

    private val objects = LinkedHashMap<String, ByteArray>()
    private val etags = HashMap<String, String>()
    private var writes = 0

    var failPut: IOException? = null
    var failDelete: IOException? = null

    var deletes: Int = 0
        private set
    var lastName: String? = null
        private set

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
        ifNoneMatch: Boolean,
    ): CloudObject {
        lastName = name
        failPut?.let { throw it }
        if (mode == Mode.REJECTS && (ifMatch != null || ifNoneMatch)) {
            throw IOException("S3 request failed: HTTP 501 (NotImplemented)")
        }
        if (mode == Mode.HONOURS) {
            if (ifNoneMatch && objects.containsKey(name)) throw CloudPreconditionFailed("an object already exists")
            if (ifMatch != null && etags[name] != ifMatch) throw CloudPreconditionFailed("stale entity tag")
        }
        objects[name] = content.copyOf()
        val etag = "etag-${++writes}"
        etags[name] = etag
        return CloudObject(name, content.size.toLong(), null, etag)
    }

    override fun head(name: String): CloudObject? = null

    override fun fetch(name: String): CloudContent? {
        val bytes = objects[name] ?: return null
        return CloudContent(bytes.copyOf(), etags[name])
    }

    override fun get(name: String): ByteArray = objects[name]?.copyOf() ?: throw IOException("missing")

    override fun list(prefix: String): List<CloudObject> = emptyList()

    override fun delete(name: String) {
        deletes++
        failDelete?.let { throw it }
        objects.remove(name)
        etags.remove(name)
    }
}
