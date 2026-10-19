package io.github.catlinux.keynexa.cloud

import io.github.catlinux.keynexa.sync.Absorbed
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.CloudSyncState
import io.github.catlinux.keynexa.sync.CloudSyncer
import io.github.catlinux.keynexa.sync.Expect
import io.github.catlinux.keynexa.sync.SyncError
import io.github.catlinux.keynexa.sync.SyncIds
import io.github.catlinux.keynexa.sync.SyncLocal
import io.github.catlinux.keynexa.sync.SyncResult
import java.io.IOException
import java.time.Clock

/**
 * The sync engine without any Android dependency, so it can be tested on the JVM (ADR-0009).
 *
 * [run] does nothing (and answers null) when there is no valid setup, the consent is not accepted or synchronisation
 * is not enabled. It resolves the vault's synchronisation id: a malformed one is reported as [SyncError.InvalidSyncId]
 * and nothing is synced; when there is none, it adopts an existing object under the sync prefix that decrypts with the
 * open vault's credentials and has the same root group, or assigns a fresh id. A service failure while adopting is
 * reported as [SyncError.Unavailable], never thrown.
 */
class CloudSyncRunner(
    private val loadSetup: () -> S3Setup?,
    private val consentAccepted: () -> Boolean,
    private val state: CloudSyncState,
    private val backendFactory: (S3Setup) -> CloudBackend,
    private val clock: Clock = Clock.systemUTC(),
    private val prefix: String = DEFAULT_PREFIX,
) {
    private val candidatePattern = Regex("^${Regex.escape(prefix)}[0-9a-f]{32}\\.kdbx$")

    /**
     * Runs one reconcile step for [local]; null when synchronisation does not apply.
     *
     * [vaultId] is kept for symmetry with the backup runner; the state is keyed by the synchronisation id.
     */
    @Suppress("UnusedParameter")
    fun run(
        vaultId: String,
        local: SyncLocal,
    ): SyncResult? {
        val setup = loadSetup() ?: return null
        if (!consentAccepted() || !setup.syncEnabled) return null
        val backend = backendFactory(setup)
        val syncer = CloudSyncer(backend, state, clock)
        val current = local.syncId()
        if (current != null) return syncer.sync(current, local)
        return try {
            val id = adopt(backend, local) ?: SyncIds.newId().also(local::assignSyncId)
            syncer.sync(id, local)
        } catch (failure: IOException) {
            SyncResult.Failed(SyncError.Unavailable)
        }
    }

    private fun adopt(
        backend: CloudBackend,
        local: SyncLocal,
    ): String? {
        val candidates =
            backend
                .list(prefix)
                .filter { candidatePattern.matches(it.name) }
                .sortedByDescending { it.modifiedAt }
                .take(MAX_CANDIDATES)
        for (candidate in candidates) {
            val bytes = backend.fetch(candidate.name)?.bytes ?: continue
            if (local.absorb(bytes, Expect.SameRoot) is Absorbed.Merged) {
                return candidate.name.removePrefix(prefix).removeSuffix(OBJECT_SUFFIX)
            }
        }
        return null
    }

    private companion object {
        const val DEFAULT_PREFIX = "KeyNexa/sync/"
        const val OBJECT_SUFFIX = ".kdbx"
        const val MAX_CANDIDATES = 10
    }
}
