package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.cloud.CloudBackupRunner
import io.github.catlinux.keynexa.cloud.S3Setup
import io.github.catlinux.keynexa.cloud.vaultIdOf
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.BackupError
import io.github.catlinux.keynexa.sync.CloudBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.Clock
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
 * The desktop cloud backup queue: one daemon thread, one task per vault at a time.
 *
 * [onSaved] enqueues the daily check after a successful save; [backupNow] enqueues a forced upload. Both return at
 * once and do all their work (credentials, consent, reading the file, network) off the caller's thread, and nothing
 * of it is ever logged.
 */
class DesktopCloudBackup(
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
    private val mutableStatus = MutableStateFlow(CloudBackupStatus(null, null, running = false))

    /** The state of the last vault treated; the screens observe it. */
    val status: StateFlow<CloudBackupStatus> = mutableStatus.asStateFlow()

    /** Enqueues the daily check for [vault] after it was saved. */
    fun onSaved(
        vault: KnownVault,
        read: () -> ByteArray,
    ) = enqueue(vault, read, force = false)

    /** Enqueues a forced upload of [vault], whatever the daily check would have decided. */
    fun backupNow(
        vault: KnownVault,
        read: () -> ByteArray,
    ) = enqueue(vault, read, force = true)

    /** The state of [vaultId], for a screen that shows it without waiting for a backup. */
    fun statusOf(vaultId: String): CloudBackupStatus =
        CloudBackupStatus(
            lastSuccess = stores.backupState.lastSuccess(vaultId),
            lastError = stores.backupState.lastError(vaultId),
            running = vaultId in pending,
        )

    /** True when any vault already has a successful copy on record, which the reminder uses. */
    fun hasAnyBackup(): Boolean = stores.backupState.hasAnySuccess()

    /** Forgets every vault's state, so a new connection does not show the previous "last copy". */
    fun reset() {
        stores.backupState.clearAll()
        mutableStatus.value = CloudBackupStatus(null, null, running = false)
    }

    private fun enqueue(
        vault: KnownVault,
        read: () -> ByteArray,
        force: Boolean,
    ) {
        val vaultId = vaultIdOf(vault.location.value)
        if (!pending.add(vaultId)) return
        executor.execute {
            try {
                runTask(vault, vaultId, read, force)
            } finally {
                pending.remove(vaultId)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun runTask(
        vault: KnownVault,
        vaultId: String,
        read: () -> ByteArray,
        force: Boolean,
    ) {
        mutableStatus.update { it.copy(running = true) }
        val state = stores.backupState
        val runner =
            CloudBackupRunner(
                loadSetup = { stores.credentials.load()?.let(S3Setup::fromValues) },
                consentAccepted = { stores.consent.accepted() },
                state = state,
                backendFactory = backendFactory,
                clock = clock,
            )
        try {
            runner.run(vaultId, vault.name, read, force)
        } catch (failure: IOException) {
            state.recordError(vaultId, BackupError.Unavailable, clock.instant())
        } catch (failure: RuntimeException) {
            // A failure here must not end the worker thread: the app's uncaught-exception handler locks the vault.
            state.recordError(vaultId, BackupError.Unavailable, clock.instant())
        } finally {
            mutableStatus.value =
                CloudBackupStatus(state.lastSuccess(vaultId), state.lastError(vaultId), running = false)
        }
    }

    private companion object {
        const val THREAD_NAME = "keynexa-cloud-backup"
    }
}
