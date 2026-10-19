package io.github.catlinux.keynexa.sync

import java.io.IOException
import java.time.Clock

/** The bytes of a vault file and their fingerprint; two equal fingerprints mean byte-identical files. */
class SyncFile(
    val bytes: ByteArray,
    val fingerprint: Fingerprint = Fingerprint.of(bytes),
) {
    /** Never prints the content, which is an encrypted vault. */
    override fun toString(): String = "SyncFile(size=${bytes.size}, fingerprint=$fingerprint)"
}

/** What the reconciler expects of the remote vault before merging it. */
sealed interface Expect {
    /** The remote vault's `KeyNexa.SyncId` must be [id]. */
    data class SyncId(
        val id: String,
    ) : Expect

    /** The remote vault must have the same root group as the open one; used while adopting an existing object. */
    data object SameRoot : Expect
}

/** What merging the remote vault into the open one did. */
sealed interface Absorbed {
    /** The merge finished; [file] is the local file to keep and [remoteLacksLocal] says it still has to be uploaded. */
    data class Merged(
        val file: SyncFile,
        val remoteLacksLocal: Boolean,
    ) : Absorbed

    /** The vault is closed or busy saving; the next trigger will try again. Never an error. */
    data object Postponed : Absorbed

    /** The remote vault does not open with the open vault's credentials. */
    data object OtherPassword : Absorbed

    /** The remote bytes could not be read or merged. */
    data object Unreadable : Absorbed

    /** The remote vault is not the same vault this device is synchronising. */
    data object NotSameVault : Absorbed

    /** The merged vault could not be written to the local file. */
    data object SaveFailed : Absorbed
}

/** The vault as the reconciler sees it: the local side of a synchronisation step. */
interface SyncLocal {
    /** The id in the vault's custom data, or null when it has none. */
    fun syncId(): String?

    /** The local file as it is right now. */
    fun currentFile(): SyncFile

    /** Merges the remote [bytes] into the open vault and saves the result, per [expect]. */
    fun absorb(
        bytes: ByteArray,
        expect: Expect,
    ): Absorbed

    /**
     * Stores [id] in the vault's custom data and saves it; false when it could not (the vault was busy or closed, or
     * already carries an id). Nothing may be uploaded under [id] unless this answered true.
     */
    fun assignSyncId(id: String): Boolean
}

/** What went wrong in a synchronisation step; none of them carries vault content. */
sealed interface SyncError {
    /** Too many precondition failures in a row: the remote object kept changing. */
    data object Busy : SyncError

    /** The service rejected the credentials or the grant. */
    data object NotAuthorized : SyncError

    /** The network, the provider or the device made the step impossible for now. */
    data object Unavailable : SyncError

    /** The service returns no entity tag, so conditional writes cannot be relied on. */
    data object NotSupported : SyncError

    /** The remote bytes could not be read or merged. */
    data object Unreadable : SyncError

    /** The vault has no synchronisation id, or it is not well formed. */
    data object InvalidSyncId : SyncError

    /** The remote vault does not open with the open vault's credentials. */
    data object OtherPassword : SyncError

    /** The remote vault is not the same vault this device is synchronising. */
    data object NotSameVault : SyncError

    /** The merged vault could not be saved locally. */
    data object SaveFailed : SyncError
}

/** What one synchronisation step did. */
sealed interface SyncResult {
    /** The remote object did not exist and was created from the local file. */
    data object Uploaded : SyncResult

    /** The remote object already held everything the local file had; nothing was uploaded. */
    data object Downloaded : SyncResult

    /** The merged file was uploaded because the remote object lacked some of it. */
    data object Exchanged : SyncResult

    /** Nothing to do: the remote object and the local file are the ones this device last synchronised. */
    data object UpToDate : SyncResult

    /** The vault was closed or busy; no error was recorded and the next trigger will try again. */
    data object Postponed : SyncResult

    data class Failed(
        val error: SyncError,
    ) : SyncResult
}

/**
 * Reconciles one vault with its object in the cloud, one step at a time (ADR-0009).
 *
 * The remote object is always merged into the local vault before anything is uploaded, and only what the remote
 * lacks is uploaded, so two devices converge instead of bouncing files. A conditional write against the entity tag
 * that was read keeps a second writer from being overwritten: a precondition failure restarts the step a few times
 * and is then reported as [SyncError.Busy]. It never throws [IOException]; failures become [SyncResult.Failed], and
 * the state keeps the last success and the last error without any vault content.
 */
class CloudSyncer(
    private val backend: CloudBackend,
    private val state: CloudSyncState,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Runs one reconcile step for the vault with [syncId] against [local].
     *
     * A missing or malformed id writes nothing and reports [SyncError.InvalidSyncId].
     */
    fun sync(
        syncId: String?,
        local: SyncLocal,
    ): SyncResult {
        val id = syncId?.takeIf(SyncIds::isValid) ?: return SyncResult.Failed(SyncError.InvalidSyncId)
        val name = SyncIds.objectName(id)
        repeat(MAX_ATTEMPTS) {
            when (val outcome = runAttempt(id, name, local)) {
                is Attempt.Done -> return outcome.result
                Attempt.Retry -> Unit
            }
        }
        return recordError(id, SyncError.Busy)
    }

    private fun runAttempt(
        id: String,
        name: String,
        local: SyncLocal,
    ): Attempt =
        try {
            step(id, name, local)
        } catch (unauthorized: CloudAuthException) {
            Attempt.Done(recordError(id, SyncError.NotAuthorized))
        } catch (unavailable: IOException) {
            Attempt.Done(recordError(id, SyncError.Unavailable))
        }

    private fun step(
        id: String,
        name: String,
        local: SyncLocal,
    ): Attempt {
        val file = local.currentFile()
        val head = backend.head(name) ?: return create(id, name, file)
        val unchanged =
            head.etag != null && head.etag == state.lastEtag(id) && file.fingerprint == state.lastLocalFingerprint(id)
        if (unchanged) return Attempt.Done(SyncResult.UpToDate)

        val remote = backend.fetch(name) ?: return Attempt.Retry
        return if (remote.etag == null) {
            Attempt.Done(recordError(id, SyncError.NotSupported))
        } else {
            absorb(id, name, remote, local)
        }
    }

    private fun absorb(
        id: String,
        name: String,
        remote: CloudContent,
        local: SyncLocal,
    ): Attempt =
        when (val absorbed = local.absorb(remote.bytes, Expect.SyncId(id))) {
            is Absorbed.Merged -> merge(id, name, remote, absorbed)
            Absorbed.Postponed -> Attempt.Done(SyncResult.Postponed)
            Absorbed.OtherPassword -> Attempt.Done(recordError(id, SyncError.OtherPassword))
            Absorbed.Unreadable -> Attempt.Done(recordError(id, SyncError.Unreadable))
            Absorbed.NotSameVault -> Attempt.Done(recordError(id, SyncError.NotSameVault))
            Absorbed.SaveFailed -> Attempt.Done(recordError(id, SyncError.SaveFailed))
        }

    // The object is gone (never created, or deleted while this device remembered its tag), so it is recreated from
    // the local file and only if it is still absent; the shortcut above never fires for a missing object.
    private fun create(
        id: String,
        name: String,
        file: SyncFile,
    ): Attempt =
        try {
            val stored = backend.put(name, file.bytes, ifNoneMatch = true)
            Attempt.Done(
                if (stored.etag == null) {
                    recordError(id, SyncError.NotSupported)
                } else {
                    succeeded(id, stored.etag, file.fingerprint, SyncResult.Uploaded)
                },
            )
        } catch (_: CloudPreconditionFailed) {
            Attempt.Retry
        }

    // Nothing is uploaded over the remote object unless the merge found something it lacks (ADR-0009 §3).
    private fun merge(
        id: String,
        name: String,
        remote: CloudContent,
        merged: Absorbed.Merged,
    ): Attempt {
        if (!merged.remoteLacksLocal) {
            return Attempt.Done(succeeded(id, remote.etag, merged.file.fingerprint, SyncResult.Downloaded))
        }
        return try {
            val stored = backend.put(name, merged.file.bytes, ifMatch = remote.etag)
            Attempt.Done(
                if (stored.etag == null) {
                    recordError(id, SyncError.NotSupported)
                } else {
                    succeeded(id, stored.etag, merged.file.fingerprint, SyncResult.Exchanged)
                },
            )
        } catch (_: CloudPreconditionFailed) {
            Attempt.Retry
        }
    }

    private fun succeeded(
        id: String,
        etag: String?,
        fingerprint: Fingerprint,
        result: SyncResult,
    ): SyncResult {
        state.recordSuccess(id, etag, fingerprint, clock.instant())
        return result
    }

    private fun recordError(
        id: String,
        error: SyncError,
    ): SyncResult {
        state.recordError(id, error, clock.instant())
        return SyncResult.Failed(error)
    }

    /** What one attempt did: a result, or a precondition failure that restarts the whole step from the head. */
    private sealed interface Attempt {
        data class Done(
            val result: SyncResult,
        ) : Attempt

        data object Retry : Attempt
    }

    private companion object {
        /** How many times a precondition failure restarts the step before it is reported as [SyncError.Busy]. */
        const val MAX_ATTEMPTS = 3
    }
}
