package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.cloud.CloudSyncRunner
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.vaultIdOf
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.CloudBackend
import io.github.catlinux.keynexa.sync.SyncError
import io.github.catlinux.keynexa.sync.SyncLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** What the cloud synchronisation is doing for the last vault it treated, for the screens to observe. */
data class CloudSyncStatus(
    val lastSuccess: Instant?,
    val lastError: SyncError?,
    val lastErrorAt: Instant?,
    val running: Boolean,
)

/**
 * The desktop cloud synchronisation queue: one daemon thread and one round per vault at a time (ADR-0009 §4).
 *
 * [trigger] enqueues a round for a vault and returns at once; everything (credentials, consent, reading the vault and
 * the network) happens off the caller's thread. When a trigger arrives while a round for the same vault is running,
 * exactly one more round runs when it ends, so a save is never lost. Nothing of it is ever logged.
 */
class DesktopCloudSync(
    /** Where the cloud keeps the credentials, the consent and the state; the settings dialog reaches them here. */
    val stores: DesktopCloudStores,
    private val backendFactory: (S3Setup) -> CloudBackend = { it.backend() },
    private val clock: Clock = Clock.systemUTC(),
) {
    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, THREAD_NAME).apply { isDaemon = true }
        }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val again = ConcurrentHashMap.newKeySet<String>()
    private val lastIds = ConcurrentHashMap<String, String>()
    private val mutableStatus = MutableStateFlow(CloudSyncStatus(null, null, null, running = false))

    /** The state of the last vault treated; the screens observe it. */
    val status: StateFlow<CloudSyncStatus> = mutableStatus.asStateFlow()

    /**
     * Enqueues a synchronisation round of [vault] and returns at once.
     *
     * A trigger while a round for the same vault is running asks for one more round, never more than one pending.
     */
    fun trigger(
        vault: KnownVault,
        local: SyncLocal,
    ) {
        val vaultId = vaultIdOf(vault.location.value)
        local.syncId()?.let { lastIds[vaultId] = it }
        if (pending.add(vaultId)) {
            executor.execute { runVault(vaultId, local) }
        } else {
            again.add(vaultId)
        }
    }

    /** The state of the vault [local] belongs to, for a screen that shows it without waiting for a round. */
    fun statusOf(local: SyncLocal): CloudSyncStatus {
        val id = local.syncId() ?: return CloudSyncStatus(null, null, null, running = false)
        return CloudSyncStatus(
            lastSuccess = stores.syncState.lastSuccess(id),
            lastError = stores.syncState.lastError(id),
            lastErrorAt = stores.syncState.lastErrorAt(id),
            running = pending.any { lastIds[it] == id },
        )
    }

    /** Forgets every vault's state, so a new connection does not show the previous "last synchronised". */
    fun reset() {
        stores.syncState.clearAll()
        lastIds.clear()
        mutableStatus.value = CloudSyncStatus(null, null, null, running = false)
    }

    private fun runVault(
        vaultId: String,
        local: SyncLocal,
    ) {
        try {
            runOnce(vaultId, local)
            while (again.remove(vaultId)) {
                runOnce(vaultId, local)
            }
        } finally {
            pending.remove(vaultId)
            // A trigger may have arrived between the loop above and this removal: run the extra round it asked for.
            if (again.remove(vaultId) && pending.add(vaultId)) {
                executor.execute { runVault(vaultId, local) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun runOnce(
        vaultId: String,
        local: SyncLocal,
    ) {
        mutableStatus.update { it.copy(running = true) }
        val state = stores.syncState
        val runner =
            CloudSyncRunner(
                loadSetup = { stores.credentials.load()?.let(S3Setup::fromValues) },
                consentAccepted = { stores.consent.accepted() },
                state = state,
                backendFactory = backendFactory,
                clock = clock,
            )
        try {
            runner.run(vaultId, local)
        } catch (failure: IOException) {
            recordUnavailable(local)
        } catch (failure: RuntimeException) {
            // A failure here must not end the worker thread: the app's uncaught-exception handler locks the vault.
            recordUnavailable(local)
        } finally {
            local.syncId()?.let { lastIds[vaultId] = it }
            mutableStatus.value = statusFor(vaultId, local)
        }
    }

    private fun recordUnavailable(local: SyncLocal) {
        val id = local.syncId() ?: return
        stores.syncState.recordError(id, SyncError.Unavailable, clock.instant())
    }

    private fun statusFor(
        vaultId: String,
        local: SyncLocal,
    ): CloudSyncStatus {
        val id = local.syncId() ?: lastIds[vaultId] ?: return CloudSyncStatus(null, null, null, running = false)
        return CloudSyncStatus(
            lastSuccess = stores.syncState.lastSuccess(id),
            lastError = stores.syncState.lastError(id),
            lastErrorAt = stores.syncState.lastErrorAt(id),
            running = false,
        )
    }

    private companion object {
        const val THREAD_NAME = "keynexa-cloud-sync"
    }
}
