package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.totp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The TOTP core, against a real corpus vault: a KeePassXC `otp` field becomes a usable configuration. */
class TotpCorpusTest {
    private val engine = KotpassKdbxEngine()

    @Test
    fun theKpxcTotpVaultYieldsAUsableConfiguration() {
        val file = TestVaults.standard.first { it.fileName == "kpxc-argon2d-tags-totp.kdbx" }
        val credentials = Credentials.of(password = requireNotNull(file.password).toCharArray())
        val vault =
            try {
                engine.decode(file.file.readBytes(), credentials)
            } finally {
                credentials.wipe()
            }

        val config = requireNotNull(entry(vault, "Internet", "Example mail").totp())

        assertEquals(6, config.digits)
        assertEquals(30, config.period)
    }

    private fun entry(
        vault: Vault,
        groupName: String,
        title: String,
    ): Entry {
        val group = vault.root.groups.first { it.name == groupName }
        return group.entries.first { it.title == title }
    }
}
