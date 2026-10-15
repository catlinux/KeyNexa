package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.CloudAuthException
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudCapabilities
import io.github.catlinux.keynexa.sync.CloudObject
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/** Where an S3-compatible bucket lives; [pathStyle] is for providers that do not support virtual-hosted buckets. */
data class S3Config(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val pathStyle: Boolean = true,
)

/**
 * An [CloudBackend] over an S3-compatible service (Backblaze B2, Cloudflare R2, MinIO, AWS).
 *
 * It sends the request signed with SigV4 and treats every object name from the contract as the exact S3 key. The
 * bodies are opaque encrypted bytes; they are never logged, and neither are the URLs or the headers. Only an
 * [IOException] (or a [CloudAuthException] for a credentials problem) leaves the methods, with a message that carries
 * the status and the S3 code at most.
 */
@Suppress("TooManyFunctions")
class S3Backend(
    private val config: S3Config,
    private val credentials: S3Credentials,
    private val transport: HttpTransport,
    private val clock: Clock = Clock.systemUTC(),
) : CloudBackend {
    override val capabilities: CloudCapabilities = CloudCapabilities(conditionalWrites = false)

    override fun put(
        name: String,
        content: ByteArray,
        ifMatch: String?,
    ): CloudObject {
        val response = send("PUT", objectUrl(name), emptyMap(), content)
        expectSuccess(response)
        return CloudObject(name, content.size.toLong(), null, etagOf(response.headers["etag"]))
    }

    override fun get(name: String): ByteArray {
        val response = send("GET", objectUrl(name), emptyMap(), null)
        if (response.status == NOT_FOUND) throw IOException("the object does not exist")
        expectSuccess(response)
        return response.body
    }

    override fun list(prefix: String): List<CloudObject> {
        val objects = mutableListOf<CloudObject>()
        var token: String? = null
        do {
            val query = StringBuilder("list-type=2&prefix=").append(uriEncode(prefix, encodeSlash = true))
            if (token != null) query.append("&continuation-token=").append(uriEncode(token, encodeSlash = true))
            val response = send("GET", "${bucketUrl()}?$query", emptyMap(), null)
            expectSuccess(response)
            val page = parseList(response.body)
            objects += page.objects
            token = page.nextToken.takeIf { page.truncated }
        } while (token != null)
        return objects
    }

    override fun delete(name: String) {
        val response = send("DELETE", objectUrl(name), emptyMap(), null)
        if (response.status == NOT_FOUND) return
        expectSuccess(response)
    }

    private fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): HttpResponse {
        val signed = S3Signer.sign(method, url, headers, body, credentials, config.region, clock.instant())
        return transport.execute(HttpRequest(method, url, signed, body))
    }

    private fun expectSuccess(response: HttpResponse) {
        if (response.status in SUCCESS_RANGE) return
        val code =
            try {
                errorCode(response.body)
            } catch (_: IOException) {
                // A body that cannot be read must not change how the status itself is mapped.
                null
            }
        val detail = if (code == null) "HTTP ${response.status}" else "HTTP ${response.status} ($code)"
        if (response.status == UNAUTHORIZED || response.status == FORBIDDEN || code in AUTH_CODES) {
            throw CloudAuthException("S3 rejected the credentials: $detail")
        }
        throw IOException("S3 request failed: $detail")
    }

    private fun objectUrl(name: String): String = "${bucketUrl()}/${uriEncode(name, encodeSlash = false)}"

    private fun bucketUrl(): String {
        val endpoint = config.endpoint.trimEnd('/')
        if (config.pathStyle) return "$endpoint/${config.bucket}"
        val uri = URI(endpoint)
        val port = if (uri.port < 0) "" else ":${uri.port}"
        return "${uri.scheme}://${config.bucket}.${uri.host}$port"
    }

    private fun etagOf(value: String?): String? = value?.trim()?.removeSurrounding("\"")

    private fun errorCode(body: ByteArray): String? =
        if (body.isEmpty()) {
            null
        } else {
            elementText(parse(body).documentElement, "Code")
        }

    private fun parseList(body: ByteArray): ListPage {
        val document = parse(body)
        val objects =
            document.getElementsByTagName("Contents").let { contents ->
                (0 until contents.length).mapNotNull { index ->
                    (contents.item(index) as? Element)?.let(::toCloudObject)
                }
            }
        val truncated = elementText(document.documentElement, "IsTruncated")?.toBooleanStrictOrNull() ?: false
        return ListPage(objects, truncated, elementText(document.documentElement, "NextContinuationToken"))
    }

    private fun toCloudObject(element: Element): CloudObject? {
        val name = elementText(element, "Key") ?: return null
        val size = elementText(element, "Size")?.toLongOrNull() ?: 0L
        val modified = elementText(element, "LastModified")?.let(::parseInstant)
        return CloudObject(name, size, modified, etagOf(elementText(element, "ETag")))
    }

    private fun parseInstant(value: String): Instant =
        try {
            Instant.parse(value)
        } catch (failure: DateTimeParseException) {
            throw IOException("S3 returned a list that could not be read", failure)
        }

    private fun elementText(
        parent: Element?,
        name: String,
    ): String? = parent?.getElementsByTagName(name)?.item(0)?.textContent

    // A document with a DTD or entities is refused before parsing (an S3 answer never has one); the parser features
    // are then hardening on top, set one by one because Android's parser rejects the Apache ones. A malformed
    // document becomes an IOException.
    @Suppress("ThrowsCount")
    private fun parse(body: ByteArray): org.w3c.dom.Document {
        val text = String(body, Charsets.ISO_8859_1)
        if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) {
            throw IOException("S3 returned a list that could not be read")
        }
        val factory = DocumentBuilderFactory.newInstance()
        return try {
            for (feature in HARDENING_FEATURES) {
                try {
                    factory.setFeature(feature.first, feature.second)
                } catch (_: ParserConfigurationException) {
                    // This parser does not have the feature; the DOCTYPE check above still holds.
                }
            }
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(ByteArrayInputStream(body))
        } catch (failure: ParserConfigurationException) {
            throw IOException("S3 returned a list that could not be read", failure)
        } catch (failure: SAXException) {
            throw IOException("S3 returned a list that could not be read", failure)
        } catch (failure: IOException) {
            throw IOException("S3 returned a list that could not be read", failure)
        }
    }

    private companion object {
        val SUCCESS_RANGE = 200..299
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        val AUTH_CODES = setOf("InvalidAccessKeyId", "SignatureDoesNotMatch", "AccessDenied")
        val HARDENING_FEATURES =
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
                XMLConstants.FEATURE_SECURE_PROCESSING to true,
            )
    }
}

/** One page of a ListObjectsV2 answer. */
private class ListPage(
    val objects: List<CloudObject>,
    val truncated: Boolean,
    val nextToken: String?,
)
