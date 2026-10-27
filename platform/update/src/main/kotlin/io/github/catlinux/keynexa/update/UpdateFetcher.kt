package io.github.catlinux.keynexa.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Where the updater gets the manifest, its signature and the installer. Meant to be the only network access. */
interface UpdateFetcher {
    /**
     * The body at [url], which must not be longer than [maxBytes].
     *
     * Throws [IOException] on a longer body and on any network, redirect or HTTP problem.
     */
    fun fetch(
        url: String,
        maxBytes: Int,
    ): ByteArray

    /**
     * Downloads the body at [url] into [target], which is written in one step only when the whole body arrived.
     *
     * A body longer than [maxBytes] and any network, redirect or HTTP problem throw [IOException] and leave
     * neither [target] nor a partial file behind.
     */
    fun download(
        url: String,
        target: Path,
        maxBytes: Long,
    )
}

/**
 * [UpdateFetcher] over HTTPS, with the redirects followed by hand so every hop is checked.
 *
 * ADR-0015: the only parties contacted are GitHub's own hosts, and the request carries nothing but a
 * `User-Agent` that names the app and its version. Cookies are never sent, because no cookie handler is
 * installed. [allowPlainHttp] exists for the tests, which serve on the loopback address; the app leaves it off.
 */
@Suppress("TooManyFunctions")
class HttpsUpdateFetcher(
    userAgentVersion: String,
    private val allowPlainHttp: Boolean = false,
    allowedHosts: Set<String> = DEFAULT_HOSTS,
    private val connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = READ_TIMEOUT_MILLIS,
) : UpdateFetcher {
    private val userAgent = USER_AGENT_PREFIX + userAgentVersion
    private val hosts = allowedHosts.map(String::lowercase).toSet()

    override fun fetch(
        url: String,
        maxBytes: Int,
    ): ByteArray {
        val connection = open(url)
        try {
            val body = ByteArrayOutputStream()
            connection.inputStream.use { input -> copyLimited(input, body, maxBytes.toLong()) }
            return body.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    override fun download(
        url: String,
        target: Path,
        maxBytes: Long,
    ) {
        val part = partOf(target)
        try {
            val connection = open(url)
            try {
                writeBody(connection, part, maxBytes)
            } finally {
                connection.disconnect()
            }
            replace(part, target)
        } catch (failure: IOException) {
            deleteQuietly(part)
            throw failure
        }
    }

    private fun writeBody(
        connection: HttpURLConnection,
        part: Path,
        maxBytes: Long,
    ) {
        connection.inputStream.use { input ->
            Files.newOutputStream(part).use { output -> copyLimited(input, output, maxBytes) }
        }
    }

    /** The connection of the last hop, already answered with 200, so the caller only has to read the body. */
    private fun open(url: String): HttpURLConnection {
        var current = checked(url)
        var redirects = 0
        while (true) {
            val connection = connect(current)
            val code = status(connection)
            if (code == HTTP_OK) return connection
            // Read before disconnecting: a closed connection may no longer report its headers.
            val location = connection.getHeaderField(LOCATION_HEADER)
            connection.disconnect()
            if (code !in REDIRECT_CODES) throw IOException("HTTP $code")
            redirects++
            if (redirects > MAX_REDIRECTS) throw IOException("too many redirects")
            current = checked(resolved(current, location))
        }
    }

    private fun connect(uri: URI): HttpURLConnection {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.setRequestProperty(USER_AGENT_HEADER, userAgent)
        return connection
    }

    private fun status(connection: HttpURLConnection): Int =
        try {
            connection.responseCode
        } catch (failure: IOException) {
            connection.disconnect()
            throw IOException(NETWORK_ERROR, failure)
        }

    /** The absolute address of the next hop, resolved against [current] so a relative `Location` works. */
    private fun resolved(
        current: URI,
        location: String?,
    ): String {
        if (location == null) throw IOException("no location")
        return try {
            current.resolve(location.trim()).toString()
        } catch (_: IllegalArgumentException) {
            throw IOException(INVALID_URL)
        }
    }

    private fun checked(text: String): URI {
        val uri = parse(text)
        requireAllowed(uri)
        return uri
    }

    private fun parse(text: String): URI =
        try {
            URI(text)
        } catch (_: URISyntaxException) {
            throw IOException(INVALID_URL)
        }

    private fun requireAllowed(uri: URI) {
        if (!allowedScheme(uri.scheme)) throw IOException(NOT_HTTPS)
        val host = uri.host?.lowercase()
        if (host == null || host !in hosts) throw IOException(HOST_NOT_ALLOWED)
    }

    private fun allowedScheme(scheme: String?): Boolean =
        when (scheme?.lowercase()) {
            HTTPS -> true
            HTTP -> allowPlainHttp
            else -> false
        }

    /** Moves the finished download over [target], deleting the previous one first so the move may be atomic. */
    private fun replace(
        part: Path,
        target: Path,
    ) {
        Files.deleteIfExists(target)
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun partOf(target: Path): Path = target.resolveSibling(target.fileName.toString() + PART_SUFFIX)

    private fun deleteQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            // Best effort: the next download starts from an empty .part file.
        }
    }

    /** Copies [input] to [output], failing as soon as the body is known to be longer than [maxBytes]. */
    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            total += read
            if (total > maxBytes) throw IOException(TOO_LARGE)
            output.write(buffer, 0, read)
        }
    }

    companion object {
        /** The hosts an update may come from: the manifest, the release page and GitHub's asset redirects. */
        val DEFAULT_HOSTS: Set<String> =
            setOf(
                "raw.githubusercontent.com",
                "github.com",
                "objects.githubusercontent.com",
                "release-assets.githubusercontent.com",
            )

        /** How long a connection may take to open and to answer, in milliseconds. */
        const val CONNECT_TIMEOUT_MILLIS: Int = 15_000
        const val READ_TIMEOUT_MILLIS: Int = 30_000

        private const val USER_AGENT_HEADER = "User-Agent"
        private const val USER_AGENT_PREFIX = "KeyNexa/"
        private const val LOCATION_HEADER = "Location"
        private const val HTTPS = "https"
        private const val HTTP = "http"
        private const val HTTP_OK = 200
        private const val MAX_REDIRECTS = 5
        private const val BUFFER_SIZE = 8 * 1024
        private const val PART_SUFFIX = ".part"
        private const val NOT_HTTPS = "not https"
        private const val HOST_NOT_ALLOWED = "host not allowed"
        private const val INVALID_URL = "invalid url"
        private const val NETWORK_ERROR = "network error"
        private const val TOO_LARGE = "too large"

        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
