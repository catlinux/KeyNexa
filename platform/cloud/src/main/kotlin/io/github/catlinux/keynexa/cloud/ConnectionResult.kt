package io.github.catlinux.keynexa.cloud

/** How a connection test ended. */
enum class ConnectionResult {
    /** The credentials, the endpoint and the bucket all worked. */
    Ok,

    /** The service rejected the credentials. */
    NotAuthorized,

    /** The endpoint or the bucket is wrong (for example an S3 `NoSuchBucket`). */
    WrongBucketOrEndpoint,

    /** The network or the service failed for another reason; trying again may work. */
    Unavailable,
}
