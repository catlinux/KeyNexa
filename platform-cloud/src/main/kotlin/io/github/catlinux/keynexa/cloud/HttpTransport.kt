package io.github.catlinux.keynexa.cloud

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP request, with the headers already signed. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
)

/** One HTTP response; header names are lowercased. The body is kept for the error cases too. */
data class HttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
)

/** The HTTP client behind a small interface, so the S3 code can be tested without a network. */
interface HttpTransport {
    /** Runs [request]. Every network failure is an [IOException] whose message never carries the URL. */
    fun execute(request: HttpRequest): HttpResponse
}

/**
 * The platform client: [HttpsURLConnection], plain [HttpURLConnection] only when [allowPlainHttp] is set (tests
 * against a local server). Redirects are not followed, so a signed request is never re-sent to another host.
 */
class UrlConnectionTransport(
    private val allowPlainHttp: Boolean = false,
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val url = URL(request.url)
        if (url.protocol != "https" && !(allowPlainHttp && url.protocol == "http")) {
            throw IOException("only https is allowed")
        }
        return try {
            openAndRun(request, url)
        } catch (failure: IOException) {
            throw IOException("the HTTP request failed", failure)
        }
    }

    private fun openAndRun(
        request: HttpRequest,
        url: URL,
    ): HttpResponse {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.requestMethod = request.method
        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (request.body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(request.body) }
        }
        val status = connection.responseCode
        val stream = if (status >= ERROR_STATUS) connection.errorStream else connection.inputStream
        val body = stream?.use { it.readBytes() } ?: ByteArray(0)
        return HttpResponse(status, headersOf(connection), body)
    }

    private fun headersOf(connection: HttpURLConnection): Map<String, String> =
        connection.headerFields
            .filterKeys { it != null }
            .map { (name, values) -> name.lowercase() to values.orEmpty().joinToString(", ") }
            .toMap()

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val ERROR_STATUS = 400
    }
}
