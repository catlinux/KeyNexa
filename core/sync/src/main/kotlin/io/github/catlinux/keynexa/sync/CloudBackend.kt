package io.github.catlinux.keynexa.sync

import java.io.IOException
import java.time.Instant

/** What a [CloudBackend] can do beyond storing and reading objects. */
data class CloudCapabilities(
    /** When true, [CloudBackend.put] honours its `ifMatch`; when false it ignores it. */
    val conditionalWrites: Boolean,
)

/** One object in the cloud store, as the provider reports it. */
data class CloudObject(
    val name: String,
    val size: Long,
    val modifiedAt: Instant?,
    val etag: String?,
)

/** The service rejected the credentials or the grant; the user has to connect again. */
class CloudAuthException(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * A place where encrypted backups can be stored, outside the device (ADR-0008 §3).
 *
 * It only moves opaque bytes: a backup is the `.kdbx` file exactly as it is on disk, never decrypted, decoded or
 * logged here. Every method may fail with [IOException] (network, permissions, quota); a credentials failure is a
 * [CloudAuthException] so the caller can tell the user to connect again. Implementations live outside `core/sync`
 * (the network module arrives in a later step); this module only defines the contract.
 */
interface CloudBackend {
    val capabilities: CloudCapabilities

    /**
     * Stores [content] under [name] and returns what the provider says about it. When [ifMatch] is given and
     * [capabilities] allow conditional writes, the write only happens if the current object still has that entity
     * tag; otherwise [ifMatch] is ignored.
     */
    fun put(
        name: String,
        content: ByteArray,
        ifMatch: String? = null,
    ): CloudObject

    fun get(name: String): ByteArray

    /** The objects whose name starts with [prefix], as the provider reports them. */
    fun list(prefix: String): List<CloudObject>

    fun delete(name: String)
}
