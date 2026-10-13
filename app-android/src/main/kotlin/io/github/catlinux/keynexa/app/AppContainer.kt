package io.github.catlinux.keynexa.app

import android.content.Context
import io.github.catlinux.keynexa.app.vault.KnownVaultsStore
import io.github.catlinux.keynexa.app.vault.VaultSession
import io.github.catlinux.keynexa.kdbx.KotpassKdbxEngine
import io.github.catlinux.keynexa.platform.FileBackupStore
import io.github.catlinux.keynexa.platform.FileSaveJournal
import io.github.catlinux.keynexa.platform.SafStoragePort
import io.github.catlinux.keynexa.platform.SafVaultAccess
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

    val session: VaultSession

    init {
        val privateDir = appContext.noBackupFilesDir
        val backups = FileBackupStore(File(privateDir, "backups"))
        val journal = FileSaveJournal(File(privateDir, "journal"))
        val worker =
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "keynexa-vault").apply { isDaemon = true }
            }
        session =
            VaultSession(
                storage = storage,
                engine = engine,
                reader = VaultReader(storage, engine),
                recovery = SaveRecovery(storage, engine, backups, journal),
                saver = SafeSaver(storage, engine, backups, journal, merger = KeePassMerger()),
                executor = worker,
            )
    }
}
