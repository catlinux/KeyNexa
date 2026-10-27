package io.github.catlinux.keynexa.desktop

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

/** [HttpsUpdateFetcher] against a loopback server: the redirect rules, the byte limit and the .part handling. */
class UpdateFetcherTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private lateinit var server: HttpServer
    private lateinit var base: String

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getByName(HOST), 0), 0)
        server.start()
        base = "http://$HOST:${server.address.port}"
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun fetchReturnsTheBody() {
        serve("/text", body = BODY)

        assertArrayEquals(BODY, fetcher().fetch("$base/text", MAX_BYTES))
    }

    @Test
    fun fetchRejectsABodyLongerThanTheLimit() {
        serve("/text", body = BODY)

        assertThrows(IOException::class.java) { fetcher().fetch("$base/text", BODY.size - 1) }
    }

    @Test
    fun fetchFollowsARedirect() {
        redirect("/start", "/next")
        serve("/next", body = BODY)

        assertArrayEquals(BODY, fetcher().fetch("$base/start", MAX_BYTES))
    }

    @Test
    fun fetchFollowsARelativeRedirect() {
        redirect("/dir/start", "next")
        serve("/dir/next", body = BODY)

        assertArrayEquals(BODY, fetcher().fetch("$base/dir/start", MAX_BYTES))
    }

    @Test
    fun fetchFollowsFiveRedirects() {
        for (hop in 0 until MAX_REDIRECTS) redirect("/r$hop", "/r${hop + 1}")
        serve("/r$MAX_REDIRECTS", body = BODY)

        assertArrayEquals(BODY, fetcher().fetch("$base/r0", MAX_BYTES))
    }

    @Test
    fun fetchRejectsMoreThanFiveRedirects() {
        for (hop in 0 until MAX_REDIRECTS + 1) redirect("/r$hop", "/r${hop + 1}")
        serve("/r${MAX_REDIRECTS + 1}", body = BODY)

        assertThrows(IOException::class.java) { fetcher().fetch("$base/r0", MAX_BYTES) }
    }

    @Test
    fun fetchRejectsARedirectToAnotherHost() {
        redirect("/start", "http://$OTHER_HOST:${server.address.port}/next")
        serve("/next", body = BODY)

        assertThrows(IOException::class.java) { fetcher().fetch("$base/start", MAX_BYTES) }
    }

    @Test
    fun fetchRejectsARedirectToANameThatIsNotAllowed() {
        redirect("/start", "http://example.com/next")

        assertThrows(IOException::class.java) { fetcher().fetch("$base/start", MAX_BYTES) }
    }

    @Test
    fun fetchRejectsAnErrorStatus() {
        serve("/missing", status = HTTP_NOT_FOUND)

        assertThrows(IOException::class.java) { fetcher().fetch("$base/missing", MAX_BYTES) }
    }

    @Test
    fun sendsOnlyTheUserAgent() {
        var agent: String? = null
        var cookie: String? = null
        serve("/text", body = BODY) { headers ->
            agent = headers.getFirst(USER_AGENT)
            cookie = headers.getFirst(COOKIE)
        }

        fetcher().fetch("$base/text", MAX_BYTES)

        assertEquals("KeyNexa/$VERSION", agent)
        assertNull(cookie)
    }

    @Test
    fun downloadWritesTheWholeFileAndNoPartFile() {
        serve("/file", body = BODY)
        val target = downloads().resolve(FILE_NAME)

        fetcher().download("$base/file", target, BODY.size.toLong())

        assertArrayEquals(BODY, Files.readAllBytes(target))
        assertFalse(Files.exists(partOf(target)))
    }

    @Test
    fun failedDownloadLeavesNothing() {
        serve("/file", body = BODY)
        val target = downloads().resolve(FILE_NAME)

        assertThrows(IOException::class.java) { fetcher().download("$base/file", target, BODY.size - 1L) }

        assertFalse(Files.exists(target))
        assertFalse(Files.exists(partOf(target)))
    }

    @Test
    fun plainHttpNeedsToBeAllowed() {
        serve("/text", body = BODY)

        assertThrows(IOException::class.java) {
            fetcher(allowPlainHttp = false).fetch("$base/text", MAX_BYTES)
        }
    }

    private fun fetcher(allowPlainHttp: Boolean = true): HttpsUpdateFetcher =
        HttpsUpdateFetcher(
            userAgentVersion = VERSION,
            allowPlainHttp = allowPlainHttp,
            allowedHosts = setOf(HOST),
        )

    private fun downloads(): Path = folder.newFolder("downloads").toPath()

    private fun partOf(target: Path): Path = target.resolveSibling(target.fileName.toString() + PART)

    private fun redirect(
        path: String,
        location: String,
    ) {
        serve(path, status = HTTP_FOUND, headers = mapOf(LOCATION to location))
    }

    private fun serve(
        path: String,
        status: Int = HTTP_OK,
        body: ByteArray = ByteArray(0),
        headers: Map<String, String> = emptyMap(),
        inspect: (Headers) -> Unit = {},
    ) {
        server.createContext(path) { exchange ->
            inspect(exchange.requestHeaders)
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            if (body.isEmpty()) {
                exchange.sendResponseHeaders(status, NO_BODY)
            } else {
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val OTHER_HOST = "127.0.0.2"
        const val VERSION = "1.2.3"
        const val FILE_NAME = "KeyNexa-windows-x64-1.2.4.msi"
        const val PART = ".part"
        const val USER_AGENT = "User-Agent"
        const val COOKIE = "Cookie"
        const val LOCATION = "Location"
        const val HTTP_OK = 200
        const val HTTP_FOUND = 302
        const val HTTP_NOT_FOUND = 404
        const val NO_BODY = -1L
        const val MAX_BYTES = 4_096
        const val MAX_REDIRECTS = 5

        val BODY = "the body of the answer".toByteArray()
    }
}
