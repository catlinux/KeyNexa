package io.github.catlinux.keynexa.sync

import java.io.IOException
import java.time.Instant

/** What a [CloudBackend] can do beyond storing and reading objects. */
data class CloudCapabilities(
    /** When true, [CloudBackend.put] honours its `ifMatch` and `ifNoneMatch`; when false it ignores both. */
    val conditionalWrites: Boolean,
)

/** One object in the cloud store, as the provider reports it. */
data class CloudObject(
    val name: String,
    val size: Long,
    val modifiedAt: Instant?,
    val etag: String?,
)

/** The bytes of one object together with the entity tag the provider returned with them. */
class CloudContent(
    val bytes: ByteArray,
    val etag: String?,
) {
    /** Never prints the content, which is an encrypted vault. */
    override fun toString(): String = "CloudContent(size=${bytes.size}, etag=$etag)"
}

/** The service rejected the credentials or the grant; the user has to connect again. */
class CloudAuthException(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

/** A conditional write was refused because the object changed since it was read (ADR-0009). */
class CloudPreconditionFailed(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * A place where encrypted backups and the synchronised vault can be stored, outside the device (ADR-0008 §3,
 * ADR-0009).
 *
 * It only moves opaque bytes: a backup is the `.kdbx` file exactly as it is on disk, never decrypted, decoded or
 * logged here. Every method may fail with [IOException] (network, permissions, quota); a credentials failure is a
 * [CloudAuthException] so the caller can tell the user to connect again. Implementations live outside `core/sync`
 * (the network module arrives in a later step); this module only defines the contract.
 */
interface CloudBackend {
    val capabilities: CloudCapabilities

    /**
     * Stores [content] under [name] and returns what the provider says about it.
     *
     * When [capabilities] allow conditional writes, [ifMatch] makes the write happen only if the current object still
     * has that entity tag and [ifNoneMatch] makes it happen only if no object exists under [name] yet; a refused
     * condition is a [CloudPreconditionFailed]. An implementation without conditional writes ignores both.
     */
    fun put(
        name: String,
        content: ByteArray,
        ifMatch: String? = null,
        ifNoneMatch: Boolean = false,
    ): CloudObject

    /** The object under [name] as the provider reports it, or null when it does not exist. */
    fun head(name: String): CloudObject?

    /** The object under [name] with its bytes and entity tag, or null when it does not exist. */
    fun fetch(name: String): CloudContent?

    fun get(name: String): ByteArray

    /** The objects whose name starts with [prefix], as the provider reports them. */
    fun list(prefix: String): List<CloudObject>

    fun delete(name: String)
}
