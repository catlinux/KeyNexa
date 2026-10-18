package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.platform.FileBackupStore
import io.github.catlinux.keynexa.platform.FileMergedCopies
import io.github.catlinux.keynexa.platform.FileSaveJournal
import java.nio.file.Path

/** Where a desktop client keeps its private data, one folder per user. */
interface DesktopDirs {
    /** The per-user private data folder; no vault, key or copy is kept outside it. */
    val data: Path
}

/** The private stores a desktop client shares, all under [DesktopDirs.data]. */
class DesktopStores(
    dirs: DesktopDirs,
) {
    val backups = FileBackupStore(dirs.data.resolve(BACKUPS).toFile())
    val journal = FileSaveJournal(dirs.data.resolve(JOURNAL).toFile())
    val mergedCopies = FileMergedCopies(dirs.data.resolve(MERGED_COPIES).toFile())
    val knownVaults = FileKnownVaults(dirs.data.resolve(KNOWN_VAULTS))

    private companion object {
        const val BACKUPS = "backups"
        const val JOURNAL = "journal"
        const val MERGED_COPIES = "merged-copies"
        const val KNOWN_VAULTS = "known-vaults.txt"
    }
}
