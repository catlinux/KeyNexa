package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.CloudObject
import io.github.catlinux.keynexa.sync.CloudPreconditionFailed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The S3 backend against a fake transport: URLs, payload hashes, listing and the error mapping. */
class S3BackendTest {
    @Test
    fun putUsesThePathStyleUrlAndHashesTheBody() {
        val content = "encrypted bytes".toByteArray()
        val transport = RecordingTransport { HttpResponse(200, mapOf("etag" to "\"abc\""), ByteArray(0)) }

        val objectInfo = backend(transport).put("KeyNexa/vault.kdbx", content)

        assertEquals(CloudObject("KeyNexa/vault.kdbx", content.size.toLong(), null, "abc"), objectInfo)
        val request = transport.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("https://s3.example.com/mybucket/KeyNexa/vault.kdbx", request.url)
        assertEquals(sha256Hex(content), request.headers["x-amz-content-sha256"])
        assertTrue(request.headers.containsKey("authorization"))
    }

    @Test
    fun putUsesTheVirtualHostedUrlWhenConfigured() {
        val transport = RecordingTransport { HttpResponse(200, emptyMap(), ByteArray(0)) }

        backend(transport, config = PATH_CONFIG.copy(pathStyle = false)).put("KeyNexa/vault.kdbx", byteArrayOf(1))

        assertEquals("https://mybucket.s3.example.com/KeyNexa/vault.kdbx", transport.requests.single().url)
    }

    @Test
    fun theSecretKeyIsNeverSent() {
        val transport = RecordingTransport { HttpResponse(200, emptyMap(), ByteArray(0)) }

        backend(transport).put("KeyNexa/vault.kdbx", byteArrayOf(1))

        val request = transport.requests.single()
        assertFalse(request.url.contains(SECRET))
        assertTrue(request.headers.values.none { it.contains(SECRET) })
    }

    @Test
    fun listReadsOnePage() {
        val xml =
            listPage(
                truncated = false,
                token = null,
                contents = listOf(contents("KeyNexa/a.kdbx", 10, "2026-10-15T10:20:30.000Z", "\"etag-a\"")),
            )
        val transport = RecordingTransport { HttpResponse(200, emptyMap(), xml.toByteArray()) }

        val objects = backend(transport).list("KeyNexa/")

        assertEquals(
            listOf(CloudObject("KeyNexa/a.kdbx", 10, Instant.parse("2026-10-15T10:20:30Z"), "etag-a")),
            objects,
        )
        val url = transport.requests.single().url
        assertTrue(url.contains("list-type=2"))
        assertTrue(url.contains("prefix=KeyNexa%2F"))
    }

    @Test
    fun listFollowsTheContinuationToken() {
        val first =
            listPage(
                truncated = true,
                token = "tok",
                contents = listOf(contents("KeyNexa/a.kdbx", 1, "2026-10-15T10:20:30.000Z", "\"a\"")),
            )
        val second =
            listPage(
                truncated = false,
                token = null,
                contents = listOf(contents("KeyNexa/b.kdbx", 2, "2026-10-15T10:20:31.000Z", "\"b\"")),
            )
        val transport =
            RecordingTransport { request ->
                if (request.url.contains("continuation-token=tok")) {
                    HttpResponse(200, emptyMap(), second.toByteArray())
                } else {
                    HttpResponse(200, emptyMap(), first.toByteArray())
                }
            }

        val objects = backend(transport).list("KeyNexa/")

        assertEquals(listOf("KeyNexa/a.kdbx", "KeyNexa/b.kdbx"), objects.map { it.name })
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[1].url.contains("continuation-token=tok"))
    }

    @Test
    fun aListWithADoctypeIsRefused() {
        val body = "<!DOCTYPE r [<!ENTITY x \"y\">]><ListBucketResult></ListBucketResult>"
        val transport = RecordingTransport { HttpResponse(200, emptyMap(), body.toByteArray()) }

        assertThrows(IOException::class.java) { backend(transport).list("KeyNexa/") }
    }

    @Test
    fun aMalformedListIsAnIoException() {
        val transport =
            RecordingTransport {
                HttpResponse(200, emptyMap(), "<ListBucketResult><Contents>".toByteArray())
            }

        assertThrows(IOException::class.java) { backend(transport).list("KeyNexa/") }
    }

    @Test
    fun aForbiddenAnswerIsAnAuthException() {
        val transport = RecordingTransport { HttpResponse(403, emptyMap(), errorXml("AccessDenied").toByteArray()) }

        val failure = assertThrows(CloudAuthException::class.java) { backend(transport).get("a.kdbx") }

        assertFalse(failure.message.orEmpty().contains("https"))
    }

    @Test
    fun anInvalidAccessKeyIdIsAnAuthExceptionOnAnyStatus() {
        val transport =
            RecordingTransport {
                HttpResponse(400, emptyMap(), errorXml("InvalidAccessKeyId").toByteArray())
            }

        assertThrows(CloudAuthException::class.java) { backend(transport).get("a.kdbx") }
    }

    @Test
    fun aForbiddenAnswerWithABrokenBodyIsStillAnAuthException() {
        val transport = RecordingTransport { HttpResponse(403, emptyMap(), "<Error><Code>".toByteArray()) }

        assertThrows(CloudAuthException::class.java) { backend(transport).get("a.kdbx") }
    }

    @Test
    fun aServerErrorWithABrokenBodyKeepsItsStatus() {
        val transport = RecordingTransport { HttpResponse(500, emptyMap(), "<Error><Code>".toByteArray()) }

        val failure = assertThrows(IOException::class.java) { backend(transport).get("a.kdbx") }

        assertTrue(failure.message.orEmpty().contains("500"))
    }

    @Test
    fun aMissingObjectOnGetIsAnIoException() {
        val transport = RecordingTransport { HttpResponse(404, emptyMap(), ByteArray(0)) }

        val failure = assertThrows(IOException::class.java) { backend(transport).get("a.kdbx") }

        assertFalse(failure.message.orEmpty().contains("https"))
    }

    @Test
    fun aMissingObjectOnDeleteIsSuccess() {
        val transport = RecordingTransport { HttpResponse(404, emptyMap(), ByteArray(0)) }

        backend(transport).delete("a.kdbx")

        assertEquals("DELETE", transport.requests.single().method)
    }

    @Test
    fun aServerErrorIsAnIoException() {
        val transport = RecordingTransport { HttpResponse(500, emptyMap(), ByteArray(0)) }

        val failure = assertThrows(IOException::class.java) { backend(transport).get("a.kdbx") }

        assertTrue(failure.message.orEmpty().contains("500"))
        assertFalse(failure.message.orEmpty().contains("https"))
    }

    @Test
    fun putSignsAndSendsTheIfMatchHeader() {
        val transport = RecordingTransport { HttpResponse(200, mapOf("etag" to "\"new\""), ByteArray(0)) }

        backend(transport).put("KeyNexa/vault.kdbx", byteArrayOf(1), ifMatch = "abc")

        val request = transport.requests.single()
        assertEquals("\"abc\"", request.headers["if-match"])
        assertTrue(request.headers.getValue("authorization").contains("if-match"))
    }

    @Test
    fun putDoesNotDoubleTheQuotesOfAQuotedEtag() {
        val transport = RecordingTransport { HttpResponse(200, emptyMap(), ByteArray(0)) }

        backend(transport).put("KeyNexa/vault.kdbx", byteArrayOf(1), ifMatch = "\"abc\"")

        assertEquals("\"abc\"", transport.requests.single().headers["if-match"])
    }

    @Test
    fun putSignsAndSendsTheIfNoneMatchHeader() {
        val transport = RecordingTransport { HttpResponse(200, emptyMap(), ByteArray(0)) }

        backend(transport).put("KeyNexa/vault.kdbx", byteArrayOf(1), ifNoneMatch = true)

        val request = transport.requests.single()
        assertEquals("*", request.headers["if-none-match"])
        assertTrue(request.headers.getValue("authorization").contains("if-none-match"))
    }

    @Test
    fun aPreconditionFailureOnPutIsMapped() {
        val transport =
            RecordingTransport {
                HttpResponse(412, emptyMap(), errorXml("PreconditionFailed").toByteArray())
            }

        assertThrows(CloudPreconditionFailed::class.java) {
            backend(transport).put("KeyNexa/vault.kdbx", byteArrayOf(1), ifMatch = "old")
        }
    }

    @Test
    fun aConditionalConflictOnPutIsMapped() {
        val transport =
            RecordingTransport {
                HttpResponse(409, emptyMap(), errorXml("ConditionalRequestConflict").toByteArray())
            }

        assertThrows(CloudPreconditionFailed::class.java) {
            backend(transport).put("KeyNexa/vault.kdbx", byteArrayOf(1), ifNoneMatch = true)
        }
    }

    @Test
    fun headAnswersNullWhenTheObjectIsMissing() {
        val transport = RecordingTransport { HttpResponse(404, emptyMap(), ByteArray(0)) }

        assertNull(backend(transport).head("KeyNexa/vault.kdbx"))

        assertEquals("HEAD", transport.requests.single().method)
    }

    @Test
    fun headReadsTheUnquotedEtagAndSize() {
        val transport =
            RecordingTransport {
                HttpResponse(200, mapOf("etag" to "\"abc\"", "content-length" to "10"), ByteArray(0))
            }

        val objectInfo = backend(transport).head("KeyNexa/vault.kdbx")

        assertEquals(CloudObject("KeyNexa/vault.kdbx", 10, null, "abc"), objectInfo)
    }

    @Test
    fun fetchAnswersNullWhenTheObjectIsMissing() {
        val transport = RecordingTransport { HttpResponse(404, emptyMap(), ByteArray(0)) }

        assertNull(backend(transport).fetch("KeyNexa/vault.kdbx"))

        assertEquals("GET", transport.requests.single().method)
    }

    @Test
    fun fetchReadsTheBytesAndTheUnquotedEtag() {
        val content = byteArrayOf(1, 2, 3)
        val transport = RecordingTransport { HttpResponse(200, mapOf("etag" to "\"abc\""), content) }

        val fetched = backend(transport).fetch("KeyNexa/vault.kdbx")

        assertEquals(content.toList(), fetched?.bytes?.toList())
        assertEquals("abc", fetched?.etag)
    }

    private fun backend(
        transport: HttpTransport,
        config: S3Config = PATH_CONFIG,
    ): S3Backend = S3Backend(config, S3Credentials(ACCESS_KEY, SECRET), transport, Clock.fixed(START, ZoneOffset.UTC))

    private fun contents(
        key: String,
        size: Long,
        modified: String,
        etag: String,
    ): String =
        """
        <Contents>
          <Key>$key</Key>
          <LastModified>$modified</LastModified>
          <ETag>$etag</ETag>
          <Size>$size</Size>
        </Contents>
        """.trimIndent()

    private fun listPage(
        truncated: Boolean,
        token: String?,
        contents: List<String>,
    ): String {
        val tokenLine = token?.let { "  <NextContinuationToken>$it</NextContinuationToken>\n" }.orEmpty()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n" +
            "  <Name>mybucket</Name>\n" +
            "  <IsTruncated>$truncated</IsTruncated>\n" +
            tokenLine +
            contents.joinToString("\n") +
            "\n</ListBucketResult>"
    }

    private fun errorXml(code: String): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Error><Code>$code</Code><Message>denied</Message></Error>
        """.trimIndent()

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) builder.append("%02x".format(byte))
        return builder.toString()
    }

    private class RecordingTransport(
        private val handler: (HttpRequest) -> HttpResponse,
    ) : HttpTransport {
        val requests: MutableList<HttpRequest> = mutableListOf()

        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler(request)
        }
    }

    private companion object {
        const val ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE"
        const val SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        val START: Instant = Instant.parse("2026-10-15T10:20:30Z")
        val PATH_CONFIG = S3Config(endpoint = "https://s3.example.com", region = "us-east-1", bucket = "mybucket")
    }
}
