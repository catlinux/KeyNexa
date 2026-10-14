package io.github.catlinux.keynexa.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.catlinux.keynexa.app.nav.Navigator
import io.github.catlinux.keynexa.app.security.AutoLock
import io.github.catlinux.keynexa.app.security.BiometricKeys
import io.github.catlinux.keynexa.app.security.BiometricStore
import io.github.catlinux.keynexa.app.security.BiometricUnlock
import io.github.catlinux.keynexa.app.security.LockTimer
import io.github.catlinux.keynexa.app.settings.AppSettings
import io.github.catlinux.keynexa.app.vault.KnownVaultsStore
import io.github.catlinux.keynexa.app.vault.SessionState
import io.github.catlinux.keynexa.app.vault.VaultReaders
import io.github.catlinux.keynexa.app.vault.VaultSession
import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.platform.FileBackupStore
import io.github.catlinux.keynexa.platform.FileMergedCopies
import io.github.catlinux.keynexa.platform.FileSaveJournal
import io.github.catlinux.keynexa.platform.SafStoragePort
import io.github.catlinux.keynexa.platform.SafVaultAccess
import io.github.catlinux.keynexa.sync.ConflictMerger
import io.github.catlinux.keynexa.sync.KeePassMerger
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.VaultReader
import java.io.File
import java.util.concurrent.Executors

/** Builds and holds the app's long-lived objects. Private data goes under `noBackupFilesDir`. */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val storage = SafStoragePort(appContext)
    val access = SafVaultAccess(appContext, storage)
    val knownVaults = KnownVaultsStore(appContext)
    val engine = KotpassKdbxEngine()

    val navigator = Navigator()
    val settings = AppSettings(appContext)
    val session: VaultSession

    val biometric =
        BiometricUnlock(
            context = appContext,
            keys = BiometricKeys(appContext),
            store = BiometricStore(File(appContext.noBackupFilesDir, "biometric"), now = System::currentTimeMillis),
            passwordDays = { settings.values.biometricPasswordDays },
        )

    /** True while the app is in front; the vault is only looked at for outside changes then. */
    var visible by mutableStateOf(false)

    private val lockTimer =
        object : LockTimer {
            private val handler = Handler(Looper.getMainLooper())

            override fun start(
                delayMillis: Long,
                action: () -> Unit,
            ) {
                cancel()
                handler.postDelayed({ action() }, delayMillis)
            }

            override fun cancel() {
                handler.removeCallbacksAndMessages(null)
            }
        }

    val autoLock =
        AutoLock(
            settings = { settings.values },
            now = SystemClock::elapsedRealtime,
            timer = lockTimer,
            lock = ::lockNow,
        )

    init {
        val privateDir = appContext.noBackupFilesDir
        val backups = FileBackupStore(File(privateDir, "backups"))
        val journal = FileSaveJournal(File(privateDir, "journal"))
        val mergedCopies = FileMergedCopies(File(privateDir, "merged-copies"))
        val keeper = KeePassMerger()
        val worker =
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "keynexa-vault").apply { isDaemon = true }
            }
        session =
            VaultSession(
                storage = storage,
                engine = engine,
                readers =
                    VaultReaders(
                        patient = VaultReader(storage, engine),
                        quick = VaultReader(storage, engine, retryDelaysMillis = emptyList()),
                    ),
                recovery = SaveRecovery(storage, engine, backups, journal),
                saver = SafeSaver(storage, engine, backups, journal, merger = keeper),
                conflicts = ConflictMerger(storage, engine, keeper, mergedCopies),
                executor = worker,
                now = System::currentTimeMillis,
            )
    }

    /** Locks an open vault: clears the clipboard, wipes the credentials and returns to the home screen. */
    fun lockNow() {
        if (session.state.value !is SessionState.Open) return
        ClipboardGuard.clearNow(appContext)
        session.lock()
        navigator.reset()
    }
}
