package io.github.catlinux.keynexa.cloud

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress

/** The platform HTTP client, against a real local server (the only case where plain HTTP is allowed). */
class UrlConnectionTransportTest {
    private lateinit var server: HttpServer
    private var port: Int = 0

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo") { exchange ->
            val body = exchange.requestBody.readBytes()
            val answer = "${exchange.requestMethod}|${exchange.requestHeaders.getFirst("X-Test")}|${String(body)}"
            exchange.responseHeaders.add("X-Echo", "yes")
            exchange.sendResponseHeaders(200, answer.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(answer.toByteArray()) }
        }
        server.createContext("/fail") { exchange ->
            val body = "boom".toByteArray()
            exchange.sendResponseHeaders(500, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        port = server.address.port
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun aRequestAndResponseRoundTrip() {
        val response =
            transport().execute(
                HttpRequest("POST", "http://127.0.0.1:$port/echo", mapOf("X-Test" to "value"), "hello".toByteArray()),
            )

        assertEquals(200, response.status)
        assertEquals("yes", response.headers["x-echo"])
        assertEquals("POST|value|hello", String(response.body))
    }

    @Test
    fun theErrorBodyIsRead() {
        val response = transport().execute(HttpRequest("GET", "http://127.0.0.1:$port/fail", emptyMap(), null))

        assertEquals(500, response.status)
        assertEquals("boom", String(response.body))
    }

    @Test
    fun plainHttpIsRejectedWithoutTheFlag() {
        val failure =
            assertThrows(IOException::class.java) {
                UrlConnectionTransport().execute(HttpRequest("GET", "http://127.0.0.1:$port/echo", emptyMap(), null))
            }

        assertFalse(failure.message.orEmpty().contains("127.0.0.1"))
    }

    private fun transport(): UrlConnectionTransport = UrlConnectionTransport(allowPlainHttp = true)
}
