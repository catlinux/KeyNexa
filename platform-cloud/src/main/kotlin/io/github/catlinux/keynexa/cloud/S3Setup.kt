package io.github.catlinux.keynexa.cloud

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
) {
    override fun toString(): String =
        "S3Setup(endpoint=$endpoint, region=$region, bucket=$bucket, " +
            "pathStyle=$pathStyle, keep=$keep, credentials=redacted)"

    fun toValues(): Map<String, String> =
        mapOf(
            KEY_ENDPOINT to endpoint,
            KEY_REGION to region,
            KEY_BUCKET to bucket,
            KEY_PATH_STYLE to pathStyle.toString(),
            KEY_ACCESS to accessKeyId,
            KEY_SECRET to secretAccessKey,
            KEY_KEEP to keep.toString(),
        )

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
        private const val MIN_KEEP = 1
        private const val MAX_KEEP = 100
        private const val HTTPS_PREFIX = "https://"

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
            return S3Setup(endpoint, region, bucket, pathStyle, access, secret, keep)
        }
    }
}
