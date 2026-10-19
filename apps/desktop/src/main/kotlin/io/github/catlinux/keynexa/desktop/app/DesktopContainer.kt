package io.github.catlinux.keynexa.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.catlinux.keynexa.desktop.ClipboardTimer
import io.github.catlinux.keynexa.desktop.DesktopCloudBackup
import io.github.catlinux.keynexa.desktop.DesktopCloudStores
import io.github.catlinux.keynexa.desktop.DesktopCloudSync
import io.github.catlinux.keynexa.desktop.DesktopDirs
import io.github.catlinux.keynexa.desktop.DesktopSettings
import io.github.catlinux.keynexa.desktop.DesktopSettingsStore
import io.github.catlinux.keynexa.desktop.DesktopStores
import io.github.catlinux.keynexa.desktop.FileStoragePort
import io.github.catlinux.keynexa.desktop.HelloSigner
import io.github.catlinux.keynexa.desktop.QuickUnlock
import io.github.catlinux.keynexa.desktop.SystemEvents
import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.session.KnownVaults
import io.github.catlinux.keynexa.session.SessionSyncLocal
import io.github.catlinux.keynexa.session.VaultReaders
import io.github.catlinux.keynexa.session.VaultSession
import io.github.catlinux.keynexa.sync.ConflictMerger
import io.github.catlinux.keynexa.sync.KeePassMerger
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.SyncLocal
import io.github.catlinux.keynexa.sync.VaultReader
import io.github.catlinux.keynexa.vault.VaultLocation
import io.github.catlinux.keynexa.windows.DpapiSecretBox
import io.github.catlinux.keynexa.windows.WindowsCaptureGuard
import io.github.catlinux.keynexa.windows.WindowsClipboard
import io.github.catlinux.keynexa.windows.WindowsDirs
import io.github.catlinux.keynexa.windows.WindowsHello
import io.github.catlinux.keynexa.windows.WindowsSystemEvents
import java.nio.file.Files
import java.util.concurrent.Executors

/** Builds and holds the desktop app's long-lived objects. Private data lives under the Windows data folder. */
class DesktopContainer {
    /** Where the private data goes; the folder is created here, on first use. */
    val dirs: DesktopDirs = WindowsDirs()

    private val storage = FileStoragePort()
    private val engine = KotpassKdbxEngine()

    /** The vaults the user added to the app, kept in the private data folder. */
    val knownVaults: KnownVaults

    /** The one vault session of the app; nothing is open until the unlock screen asks for it. */
    val session: VaultSession

    /**
     * The clipboard every copy of a secret goes through: it marks them so Windows keeps them out of the clipboard
     * history and the cloud clipboard, and clears them after the time the settings ask for.
     */
    val clipboard: ClipboardTimer

    /** What the user chose, read by the interface; [updateSettings] is the only way to change it. */
    var settings by mutableStateOf(DesktopSettings())
        private set

    /** The Windows session and power watch, closed when the app leaves. */
    val systemEvents: SystemEvents = WindowsSystemEvents()

    /** Asks Windows to keep the window out of screen captures. */
    val captureGuard = WindowsCaptureGuard()

    /** Windows Hello, behind the quick unlock of ADR-0014. */
    val hello = WindowsHello()

    /** The native handle of the main window, so the Hello prompt can be brought in front of it. */
    @Volatile
    var windowHandle: Long? = null

    /** The quick-unlock policy: it holds the vault components, encrypted, only in memory. */
    val quickUnlock =
        QuickUnlock(signer = HelloSigner { hello.sign(it, windowHandle) }, clock = System::currentTimeMillis)

    /** The cloud queue: it copies a vault to the configured account after a save and on demand. */
    val cloud: DesktopCloudBackup

    /** The cloud synchronisation queue: it reconciles a vault with its object in the account, on the same triggers. */
    val cloudSync: DesktopCloudSync

    private val settingsStore: DesktopSettingsStore

    /** Stores what [change] answers and publishes it, so the interface follows the new settings. */
    fun updateSettings(change: (DesktopSettings) -> DesktopSettings) {
        val updated = change(settings)
        settingsStore.save(updated)
        settings = updated
    }

    /** Reads a vault's encrypted bytes, for the cloud copy the settings ask for. */
    fun readVault(location: VaultLocation): ByteArray = storage.read(location)

    /** The open vault as the cloud reconciler sees it; the window's triggers pass it to [cloudSync]. */
    fun syncLocal(): SyncLocal = SessionSyncLocal(session, storage)

    init {
        Files.createDirectories(dirs.data)
        val stores = DesktopStores(dirs)
        knownVaults = stores.knownVaults
        settingsStore = stores.settings
        settings = settingsStore.load()
        clipboard =
            ClipboardTimer(
                WindowsClipboard(),
                clearAfterMillis = { clipboardClearMillis(settings.clipboardClearSeconds) },
            )
        cloud =
            DesktopCloudBackup(
                stores = DesktopCloudStores(dirs, DpapiSecretBox()),
            )
        cloudSync = DesktopCloudSync(stores = cloud.stores)
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
                recovery = SaveRecovery(storage, engine, stores.backups, stores.journal),
                saver = SafeSaver(storage, engine, stores.backups, stores.journal, merger = keeper),
                backups = stores.backups,
                vaultMerger = keeper,
                conflicts = ConflictMerger(storage, engine, keeper, stores.mergedCopies),
                executor = worker,
                now = System::currentTimeMillis,
                afterSave = {
                    cloud.onSaved(it) { storage.read(it.location) }
                    cloudSync.trigger(it, syncLocal())
                },
            )
    }
}
