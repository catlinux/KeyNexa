package io.github.catlinux.keynexa.app.cloud

import android.content.Context
import io.github.catlinux.keynexa.app.vault.KnownVault
import io.github.catlinux.keynexa.cloud.CloudBackupRunner
import io.github.catlinux.keynexa.cloud.FileBackupState
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.vaultIdOf
import io.github.catlinux.keynexa.sync.BackupError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** What the cloud backup is doing for the last vault it treated, for the screens to observe. */
data class CloudBackupStatus(
    val lastSuccess: Instant?,
    val lastError: BackupError?,
    val running: Boolean,
)

/**
 * The process-wide cloud backup queue: one daemon thread, one task per vault at a time.
 *
 * [onSaved] enqueues the daily check after a successful save; [backupNow] enqueues a forced upload. Both return at
 * once and do all their work (credentials, consent, reading the file, network) off the caller's thread. Nothing here
 * touches the composition or the main thread.
 */
object CloudBackupService {
    private val executor =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "keynexa-cloud-backup").apply { isDaemon = true }
        }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val mutableStatus = MutableStateFlow(CloudBackupStatus(null, null, running = false))

    /** The state of the last vault treated; the screens observe it. */
    val status: StateFlow<CloudBackupStatus> = mutableStatus.asStateFlow()

    fun onSaved(
        context: Context,
        vault: KnownVault,
        read: () -> ByteArray,
    ) = enqueue(context, vault, read, force = false)

    fun backupNow(
        context: Context,
        vault: KnownVault,
        read: () -> ByteArray,
    ) = enqueue(context, vault, read, force = true)

    /** The persisted state of [vaultId], for a screen that shows it without waiting for a backup. */
    fun statusOf(
        context: Context,
        vaultId: String,
    ): CloudBackupStatus {
        val state = stateFor(context.applicationContext)
        return CloudBackupStatus(state.lastSuccess(vaultId), state.lastError(vaultId), vaultId in pending)
    }

    /** True when any vault already has a successful copy on record (for the reminder). */
    fun hasAnyBackup(context: Context): Boolean = stateFor(context.applicationContext).hasAnySuccess()

    /**
     * Forgets every vault's state and deletes the state file, and resets the published status. Used when the user
     * disconnects, so a new connection does not show the previous "last copy".
     */
    fun reset(context: Context) {
        stateFor(context.applicationContext).clearAll()
        mutableStatus.value = CloudBackupStatus(null, null, running = false)
    }

    private fun enqueue(
        context: Context,
        vault: KnownVault,
        read: () -> ByteArray,
        force: Boolean,
    ) {
        val vaultId = vaultIdOf(vault.location.value)
        if (!pending.add(vaultId)) return
        val appContext = context.applicationContext
        executor.execute {
            try {
                runTask(appContext, vault, vaultId, read, force)
            } finally {
                pending.remove(vaultId)
            }
        }
    }

    private fun runTask(
        context: Context,
        vault: KnownVault,
        vaultId: String,
        read: () -> ByteArray,
        force: Boolean,
    ) {
        mutableStatus.update { it.copy(running = true) }
        val state = stateFor(context)
        val runner =
            CloudBackupRunner(
                loadSetup = { CloudStores.credentials(context).load()?.let(S3Setup::fromValues) },
                consentAccepted = { CloudConsentStore(context).accepted() },
                state = state,
                backendFactory = { it.backend() },
            )
        runner.run(vaultId, vault.name, read, force)
        mutableStatus.value = CloudBackupStatus(state.lastSuccess(vaultId), state.lastError(vaultId), running = false)
    }

    private fun stateFor(context: Context): FileBackupState = FileBackupState(CloudStores.backupStateFile(context))
}
