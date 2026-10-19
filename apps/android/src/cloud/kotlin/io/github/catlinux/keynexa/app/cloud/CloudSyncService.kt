package io.github.catlinux.keynexa.app.cloud

import android.content.Context
import io.github.catlinux.keynexa.cloud.CloudSyncRunner
import io.github.catlinux.keynexa.cloud.FileSyncState
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.vaultIdOf
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.SyncError
import io.github.catlinux.keynexa.sync.SyncLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
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
 * The process-wide cloud synchronisation queue: its own daemon thread and one round per vault at a time (ADR-0009 §4).
 *
 * [onSaved] and [onTrigger] enqueue a round and return at once; everything (credentials, consent, reading the vault
 * and the network) happens off the caller's thread. When a trigger arrives while a round for the same vault is
 * running, exactly one more round runs when it ends, so a save is never lost. Nothing here is ever logged.
 */
object CloudSyncService {
    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, THREAD_NAME).apply { isDaemon = true }
        }
    private val queue = SyncRoundQueue(executor)
    private val lastIds = ConcurrentHashMap<String, String>()
    private val mutableStatus = MutableStateFlow(CloudSyncStatus(null, null, null, running = false))

    /** The state of the last vault treated; the screens observe it. */
    val status: StateFlow<CloudSyncStatus> = mutableStatus.asStateFlow()

    /** Enqueues a round after a successful save and returns at once. */
    fun onSaved(
        context: Context,
        vault: KnownVault,
        local: SyncLocal,
    ) = submit(context.applicationContext, vault, local)

    /** Enqueues a round for an unlock, a return to the foreground or "Sync now" and returns at once. */
    fun onTrigger(
        context: Context,
        vault: KnownVault,
        local: SyncLocal,
    ) = submit(context.applicationContext, vault, local)

    /** The persisted state of the vault [local] belongs to, for a screen that shows it without waiting for a round. */
    fun statusOf(
        context: Context,
        local: SyncLocal,
    ): CloudSyncStatus {
        val id = local.syncId() ?: return CloudSyncStatus(null, null, null, running = false)
        val state = stateFor(context.applicationContext)
        return CloudSyncStatus(
            lastSuccess = state.lastSuccess(id),
            lastError = state.lastError(id),
            lastErrorAt = state.lastErrorAt(id),
            running = lastIds.any { (vaultId, syncId) -> syncId == id && queue.isRunning(vaultId) },
        )
    }

    /**
     * Forgets every vault's state and deletes the state file, and resets the published status. Used when the user
     * disconnects, so a new connection does not show the previous "last synchronised".
     */
    fun reset(context: Context) {
        stateFor(context.applicationContext).clearAll()
        lastIds.clear()
        mutableStatus.value = CloudSyncStatus(null, null, null, running = false)
    }

    private fun submit(
        context: Context,
        vault: KnownVault,
        local: SyncLocal,
    ) {
        val vaultId = vaultIdOf(vault.location.value)
        local.syncId()?.let { lastIds[vaultId] = it }
        queue.submit(vaultId) { runRound(context, vaultId, local) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun runRound(
        context: Context,
        vaultId: String,
        local: SyncLocal,
    ) {
        mutableStatus.update { it.copy(running = true) }
        val state = stateFor(context)
        val runner =
            CloudSyncRunner(
                loadSetup = { CloudStores.credentials(context).load()?.let(S3Setup::fromValues) },
                consentAccepted = { CloudConsentStore(context).accepted() },
                state = state,
                backendFactory = { it.backend() },
            )
        try {
            runner.run(vaultId, local)
        } catch (failure: IOException) {
            recordUnavailable(state, local)
        } catch (failure: RuntimeException) {
            // A failure here must not end the worker thread: the app's uncaught-exception handler locks the vault.
            recordUnavailable(state, local)
        } finally {
            local.syncId()?.let { lastIds[vaultId] = it }
            mutableStatus.value = statusFor(state, vaultId, local)
        }
    }

    private fun recordUnavailable(
        state: FileSyncState,
        local: SyncLocal,
    ) {
        val id = local.syncId() ?: return
        state.recordError(id, SyncError.Unavailable, Instant.now())
    }

    private fun statusFor(
        state: FileSyncState,
        vaultId: String,
        local: SyncLocal,
    ): CloudSyncStatus {
        val id = local.syncId() ?: lastIds[vaultId] ?: return CloudSyncStatus(null, null, null, running = false)
        return CloudSyncStatus(
            lastSuccess = state.lastSuccess(id),
            lastError = state.lastError(id),
            lastErrorAt = state.lastErrorAt(id),
            running = false,
        )
    }

    private fun stateFor(context: Context): FileSyncState = FileSyncState(CloudStores.syncStateFile(context))

    private const val THREAD_NAME = "keynexa-cloud-sync"
}
