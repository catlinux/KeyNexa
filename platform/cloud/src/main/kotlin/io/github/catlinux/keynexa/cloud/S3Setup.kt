package io.github.catlinux.keynexa.cloud

import java.net.URI
import java.net.URISyntaxException
import java.time.Clock

/**
 * The S3 configuration the user saved, as a value object that can be stored in the credentials file.
 *
 * Its [toString] never shows the access key or the secret. [toValues] and [fromValues] are the round trip to and from
 * a `Map<String, String>`, which is what `CloudCredentialStore` seals on disk.
 */
data class S3Setup(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val pathStyle: Boolean,
    val accessKeyId: String,
    val secretAccessKey: String,
    val keep: Int = DEFAULT_KEEP,
    val sync: Boolean = false,
    val syncProbe: String? = null,
) {
    /** True only when synchronisation was turned on and the probe still matches this service. */
    val syncEnabled: Boolean
        get() = sync && syncProbe == probeKey(endpoint, bucket)

    override fun toString(): String =
        "S3Setup(endpoint=$endpoint, region=$region, bucket=$bucket, " +
            "pathStyle=$pathStyle, keep=$keep, credentials=redacted)"

    fun toValues(): Map<String, String> =
        buildMap {
            put(KEY_ENDPOINT, endpoint)
            put(KEY_REGION, region)
            put(KEY_BUCKET, bucket)
            put(KEY_PATH_STYLE, pathStyle.toString())
            put(KEY_ACCESS, accessKeyId)
            put(KEY_SECRET, secretAccessKey)
            put(KEY_KEEP, keep.toString())
            put(KEY_SYNC, sync.toString())
            syncProbe?.let { put(KEY_SYNC_PROBE, it) }
        }

    /** The backend this setup describes, over [transport] and [clock] (the defaults are the production ones). */
    fun backend(
        transport: HttpTransport = UrlConnectionTransport(),
        clock: Clock = Clock.systemUTC(),
    ): S3Backend =
        S3Backend(
            config = S3Config(endpoint, region, bucket, pathStyle),
            credentials = S3Credentials(accessKeyId, secretAccessKey),
            transport = transport,
            clock = clock,
        )

    companion object {
        const val DEFAULT_KEEP = 10

        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_REGION = "region"
        private const val KEY_BUCKET = "bucket"
        private const val KEY_PATH_STYLE = "pathStyle"
        private const val KEY_ACCESS = "accessKeyId"
        private const val KEY_SECRET = "secretAccessKey"
        private const val KEY_KEEP = "keep"
        private const val KEY_SYNC = "sync"
        private const val KEY_SYNC_PROBE = "syncProbe"
        private const val MIN_KEEP = 1
        private const val MAX_KEEP = 100
        private const val HTTPS_PREFIX = "https://"

        /** The `endpoint|bucket` string a passing probe stores, so a change of service voids synchronisation. */
        fun probeKey(
            endpoint: String,
            bucket: String,
        ): String = "$endpoint|$bucket"

        /** Reads a stored setup; null when a required value is missing or invalid. */
        fun fromValues(values: Map<String, String>): S3Setup? {
            val endpoint = values[KEY_ENDPOINT]?.takeIf { it.startsWith(HTTPS_PREFIX) } ?: return null
            val region = values[KEY_REGION]?.takeIf { it.isNotBlank() } ?: return null
            val bucket = values[KEY_BUCKET]?.takeIf { it.isNotBlank() } ?: return null
            val access = values[KEY_ACCESS]?.takeIf { it.isNotBlank() } ?: return null
            val secret = values[KEY_SECRET]?.takeIf { it.isNotBlank() } ?: return null
            val keep = values[KEY_KEEP]?.toIntOrNull() ?: DEFAULT_KEEP
            if (keep !in MIN_KEEP..MAX_KEEP) return null
            val pathStyle = values[KEY_PATH_STYLE]?.toBooleanStrictOrNull() ?: true
            val sync = values[KEY_SYNC]?.toBooleanStrictOrNull() ?: false
            val syncProbe = values[KEY_SYNC_PROBE]?.takeIf { it.isNotBlank() }
            return S3Setup(endpoint, region, bucket, pathStyle, access, secret, keep, sync, syncProbe)
        }
    }
}

/**
 * The region an S3 endpoint implies, or null when it cannot be deduced.
 *
 * It knows the shapes used by Backblaze B2 and Amazon S3 (`s3.<region>.<provider>.com`) and by Cloudflare R2
 * (`<account>.r2.cloudflarestorage.com`, whose region is `auto`). It parses the host, ignores case, and uses no
 * regular expressions. An endpoint without `https://` has no host to read, so it answers null.
 */
@Suppress("SwallowedException")
fun regionFromEndpoint(endpoint: String): String? {
    val host =
        try {
            URI(endpoint.trim()).host
        } catch (failure: URISyntaxException) {
            null
        } ?: return null
    val labels = host.lowercase().split('.')
    if (labels.size != HOST_LABELS) return null
    return when {
        labels[0] == "s3" && labels[PROVIDER_LABEL] == "backblazeb2" && labels[TLD_LABEL] == "com" -> labels[1]
        labels[0] == "s3" && labels[PROVIDER_LABEL] == "amazonaws" && labels[TLD_LABEL] == "com" -> labels[1]
        labels[1] == "r2" && labels[PROVIDER_LABEL] == "cloudflarestorage" && labels[TLD_LABEL] == "com" -> R2_REGION
        else -> null
    }
}

private const val HOST_LABELS = 4
private const val PROVIDER_LABEL = 2
private const val TLD_LABEL = 3
private const val R2_REGION = "auto"
