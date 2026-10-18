package io.github.catlinux.keynexa.desktop.app

import io.github.catlinux.keynexa.desktop.ClipboardTimer
import io.github.catlinux.keynexa.desktop.DesktopDirs
import io.github.catlinux.keynexa.desktop.DesktopStores
import io.github.catlinux.keynexa.desktop.FileStoragePort
import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.session.KnownVaults
import io.github.catlinux.keynexa.session.VaultReaders
import io.github.catlinux.keynexa.session.VaultSession
import io.github.catlinux.keynexa.sync.ConflictMerger
import io.github.catlinux.keynexa.sync.KeePassMerger
import io.github.catlinux.keynexa.sync.SafeSaver
import io.github.catlinux.keynexa.sync.SaveRecovery
import io.github.catlinux.keynexa.sync.VaultReader
import io.github.catlinux.keynexa.windows.WindowsClipboard
import io.github.catlinux.keynexa.windows.WindowsDirs
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
     * history and the cloud clipboard, and clears them after half a minute.
     */
    val clipboard: ClipboardTimer = ClipboardTimer(WindowsClipboard())

    init {
        Files.createDirectories(dirs.data)
        val stores = DesktopStores(dirs)
        knownVaults = stores.knownVaults
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
            )
    }
}
