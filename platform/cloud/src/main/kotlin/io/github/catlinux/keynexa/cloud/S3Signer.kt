package io.github.catlinux.keynexa.cloud

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HTTPS_PORT = 443
private const val HTTP_PORT = 80
private const val BYTE_MASK = 0xff
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0f
private const val HEX_DIGITS = "0123456789ABCDEF"

/** The S3 access key pair. Its [toString] never shows either value. */
class S3Credentials(
    val accessKeyId: String,
    val secretAccessKey: String,
) {
    override fun toString(): String = "S3Credentials(redacted)"
}

/**
 * AWS Signature Version 4 for an S3 request, by the `Authorization` header.
 *
 * A pure, deterministic function of the request and the [Instant], so the official test vectors can be reproduced
 * exactly. It only uses HMAC-SHA256 and SHA-256 from the JDK; no cryptography of its own.
 */
object S3Signer {
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val TERMINATOR = "aws4_request"
    private const val EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private const val HEADER_DATE = "x-amz-date"
    private const val HEADER_CONTENT_SHA = "x-amz-content-sha256"

    private val TIMESTAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val SHORT_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    private const val HEX = "0123456789abcdef"

    /**
     * Returns the headers to send: the caller's [headers] plus `host`, `x-amz-content-sha256`, `x-amz-date` and the
     * `Authorization` header. [body] is hashed but never copied or logged.
     */
    @Suppress("LongParameterList")
    fun sign(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        credentials: S3Credentials,
        region: String,
        at: Instant,
    ): Map<String, String> {
        val uri = URI(url)
        val payloadHash = if (body == null || body.isEmpty()) EMPTY_HASH else hex(sha256(body))
        val amzDate = TIMESTAMP.format(at)
        val all = LinkedHashMap<String, String>()
        headers.forEach { (name, value) -> all[name.lowercase()] = value.trim() }
        all["host"] = hostHeader(uri)
        all[HEADER_CONTENT_SHA] = payloadHash
        all[HEADER_DATE] = amzDate
        val canonicalHeaders = all.keys.sorted().joinToString("") { "$it:${all.getValue(it)}\n" }
        val signedHeaders = all.keys.sorted().joinToString(";")
        val canonicalRequest =
            listOf(
                method.uppercase(),
                uriEncode(uri.path.orEmpty().ifEmpty { "/" }, encodeSlash = false),
                canonicalQuery(uri.query),
                canonicalHeaders,
                signedHeaders,
                payloadHash,
            ).joinToString("\n")
        val scope = "${SHORT_DATE.format(at)}/$region/$SERVICE/$TERMINATOR"
        val stringToSign =
            listOf(
                ALGORITHM,
                amzDate,
                scope,
                hex(sha256(canonicalRequest.toByteArray(StandardCharsets.UTF_8))),
            ).joinToString("\n")
        val signingKey = signingKey(credentials.secretAccessKey, SHORT_DATE.format(at), region)
        val signature = hex(hmac(signingKey, stringToSign))
        all["authorization"] =
            "$ALGORITHM Credential=${credentials.accessKeyId}/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
        return all
    }

    private fun canonicalQuery(query: String?): String {
        if (query.isNullOrEmpty()) return ""
        return query
            .split('&')
            .filter { it.isNotEmpty() }
            .map { part ->
                val separator = part.indexOf('=')
                val name = if (separator < 0) part else part.substring(0, separator)
                val value = if (separator < 0) "" else part.substring(separator + 1)
                uriEncode(name, encodeSlash = true) to uriEncode(value, encodeSlash = true)
            }.sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
    }

    private fun hostHeader(uri: URI): String {
        val host = uri.host.orEmpty()
        val port = uri.port
        val defaultPort = if (uri.scheme == "https") HTTPS_PORT else HTTP_PORT
        return if (port < 0 || port == defaultPort) host else "$host:$port"
    }

    private fun signingKey(
        secret: String,
        date: String,
        region: String,
    ): ByteArray {
        val kDate = hmac(("AWS4$secret").toByteArray(StandardCharsets.UTF_8), date)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, SERVICE)
        return hmac(kService, TERMINATOR)
    }

    private fun hmac(
        key: ByteArray,
        data: String,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and BYTE_MASK
            builder.append(HEX[value shr NIBBLE_BITS]).append(HEX[value and NIBBLE_MASK])
        }
        return builder.toString()
    }
}

/** Percent-encodes [value] the way AWS SigV4 wants it, leaving the unreserved characters alone. */
internal fun uriEncode(
    value: String,
    encodeSlash: Boolean,
): String {
    val builder = StringBuilder(value.length)
    for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
        val code = byte.toInt() and BYTE_MASK
        val character = code.toChar()
        when {
            isUnreserved(character) -> builder.append(character)
            character == '/' && !encodeSlash -> builder.append('/')
            else -> {
                builder.append('%')
                builder.append(HEX_DIGITS[code shr NIBBLE_BITS])
                builder.append(HEX_DIGITS[code and NIBBLE_MASK])
            }
        }
    }
    return builder.toString()
}

private fun isUnreserved(character: Char): Boolean =
    character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' || character in "-_.~"
