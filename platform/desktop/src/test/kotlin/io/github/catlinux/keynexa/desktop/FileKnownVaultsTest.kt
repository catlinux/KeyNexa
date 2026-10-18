package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.vault.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

/** The known-vaults file: missing reads as empty, and add, replace and remove survive a new instance. */
class FileKnownVaultsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: Path get() = folder.root.toPath().resolve("known-vaults.txt")

    private fun store() = FileKnownVaults(file)

    @Test
    fun readsAsEmptyWhenTheFileIsMissing() {
        assertEquals(emptyList<KnownVault>(), store().all())
    }

    @Test
    fun addReplaceAndRemoveRoundTrip() {
        val first = KnownVault("First", VaultLocation("/vaults/first.kdbx"))
        val second = KnownVault("Second", VaultLocation("/vaults/second.kdbx"))

        store().add(first)
        store().add(second)
        assertEquals(listOf(first, second), store().all())

        val renamed = KnownVault("Renamed", first.location)
        store().add(renamed)
        assertEquals(listOf(second, renamed), store().all())

        store().remove(first.location)
        assertEquals(listOf(second), store().all())
    }

    @Test
    fun theListSurvivesANewInstance() {
        val vault = KnownVault("Vault", VaultLocation("/vaults/vault.kdbx"))
        store().add(vault)

        assertEquals(listOf(vault), FileKnownVaults(file).all())
    }
}
