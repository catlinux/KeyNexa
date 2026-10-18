package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.sync.BackupKind
import io.github.catlinux.keynexa.sync.Fingerprint
import io.github.catlinux.keynexa.sync.PendingSave
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** The desktop stores: every folder they use lives under [DesktopDirs.data]. */
class DesktopStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun everyStoreKeepsItsFilesUnderTheDataFolder() {
        val root = folder.newFolder("data").toPath()
        val stores =
            DesktopStores(
                object : DesktopDirs {
                    override val data: Path = root
                },
            )
        val location = VaultLocation(root.resolve("vault.kdbx").toString())
        val now = Instant.parse("2026-10-18T10:00:00Z")

        val previous = stores.backups.put(location, BackupKind.BeforeSave, byteArrayOf(1), now)
        val outgoing = stores.backups.put(location, BackupKind.UnsavedChanges, byteArrayOf(2), now)
        stores.journal.begin(
            PendingSave(
                location = location,
                previous = Fingerprint.of(byteArrayOf(1)),
                expected = Fingerprint.of(byteArrayOf(2)),
                previousCopy = previous,
                outgoingCopy = outgoing,
                startedAt = now,
            ),
        )
        stores.mergedCopies.record(location, listOf(Fingerprint.of(byteArrayOf(3))))
        stores.knownVaults.add(KnownVault("Vault", location))

        assertTrue(Files.isDirectory(root.resolve("backups")))
        assertTrue(Files.isDirectory(root.resolve("journal")))
        assertTrue(Files.isDirectory(root.resolve("merged-copies")))
        assertTrue(Files.isRegularFile(root.resolve("known-vaults.txt")))
    }
}
