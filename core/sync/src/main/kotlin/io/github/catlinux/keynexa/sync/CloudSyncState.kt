package io.github.catlinux.keynexa.sync

import java.time.Instant

/**
 * What each synchronised vault was last seen as, keyed by its synchronisation id (ADR-0009 §8).
 *
 * It holds no secret and no vault content: only entity tags, fingerprints, times and error kinds. It is kept apart
 * from the reconciler so a persistent version can arrive later without touching the sync logic; the in-memory one is
 * enough for tests and for a session that has not been saved anywhere.
 */
interface CloudSyncState {
    /** The entity tag of the remote object this device last saw, or null when it has none. */
    fun lastEtag(syncId: String): String?

    /** The fingerprint of the local file this device last synchronised, or null when it has none. */
    fun lastLocalFingerprint(syncId: String): Fingerprint?

    fun lastSuccess(syncId: String): Instant?

    fun lastError(syncId: String): SyncError?

    /** When the last error of [syncId] happened, or null when there is none. */
    fun lastErrorAt(syncId: String): Instant?

    /** Records a round that left the local file and the remote object in agreement; it also clears the last error. */
    fun recordSuccess(
        syncId: String,
        etag: String?,
        fingerprint: Fingerprint,
        at: Instant,
    )

    fun recordError(
        syncId: String,
        error: SyncError,
        at: Instant,
    )
}

/** The state of a single run of the app; nothing survives a restart. */
class InMemoryCloudSyncState : CloudSyncState {
    private val etags = HashMap<String, String>()
    private val fingerprints = HashMap<String, Fingerprint>()
    private val successes = HashMap<String, Instant>()
    private val errors = HashMap<String, SyncError>()
    private val errorTimes = HashMap<String, Instant>()

    override fun lastEtag(syncId: String): String? = etags[syncId]

    override fun lastLocalFingerprint(syncId: String): Fingerprint? = fingerprints[syncId]

    override fun lastSuccess(syncId: String): Instant? = successes[syncId]

    override fun lastError(syncId: String): SyncError? = errors[syncId]

    override fun lastErrorAt(syncId: String): Instant? = errorTimes[syncId]

    override fun recordSuccess(
        syncId: String,
        etag: String?,
        fingerprint: Fingerprint,
        at: Instant,
    ) {
        if (etag == null) etags.remove(syncId) else etags[syncId] = etag
        fingerprints[syncId] = fingerprint
        successes[syncId] = at
        errors.remove(syncId)
        errorTimes.remove(syncId)
    }

    override fun recordError(
        syncId: String,
        error: SyncError,
        at: Instant,
    ) {
        errors[syncId] = error
        errorTimes[syncId] = at
    }
}
