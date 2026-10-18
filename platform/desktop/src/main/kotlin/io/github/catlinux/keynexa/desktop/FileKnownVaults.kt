package io.github.catlinux.keynexa.desktop

import io.github.catlinux.keynexa.platform.writeAtomically
import io.github.catlinux.keynexa.session.KnownVault
import io.github.catlinux.keynexa.session.KnownVaults
import io.github.catlinux.keynexa.session.KnownVaultsCodec
import io.github.catlinux.keynexa.vault.VaultLocation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** [KnownVaults] in one UTF-8 text file, written atomically; a missing or unreadable file reads as an empty list. */
class FileKnownVaults(
    private val file: Path,
) : KnownVaults {
    override fun all(): List<KnownVault> =
        try {
            KnownVaultsCodec.decode(Files.readString(file, Charsets.UTF_8))
        } catch (failure: IOException) {
            emptyList()
        }

    override fun add(vault: KnownVault) = write(all().filterNot { it.location == vault.location } + vault)

    override fun remove(location: VaultLocation) = write(all().filterNot { it.location == location })

    private fun write(vaults: List<KnownVault>) {
        writeAtomically(file.toFile(), KnownVaultsCodec.encode(vaults).toByteArray(Charsets.UTF_8))
    }
}
