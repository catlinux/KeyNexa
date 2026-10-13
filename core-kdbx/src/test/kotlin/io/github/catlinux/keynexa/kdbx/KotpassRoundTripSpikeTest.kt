package io.github.catlinux.keynexa.kdbx

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.traverse
import app.keemobile.kotpass.models.Entry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spike: opens every vault with kotpass, rewrites it to `build/roundtrip/` and checks that the model survives.
 * The rewritten files are then compared with the originals through KeePassXC (see docs/TEST_VAULTS.md).
 */
class KotpassRoundTripSpikeTest {
    private val ciphers = BaseCiphers.entries + TwofishCipher
    private val outDir = File("build/roundtrip").apply { mkdirs() }

    private fun credentialsOf(vault: TestVault): Credentials {
        val keyData = vault.keyFile?.readBytes()
        val password = vault.password?.let { EncryptedValue.fromString(it) }
        return when {
            password != null && keyData != null -> Credentials.from(password, keyData)
            password != null -> Credentials.from(password)
            keyData != null -> Credentials.from(keyData)
            else -> error("${vault.fileName} has no credentials")
        }
    }

    private fun countEntries(database: KeePassDatabase): Int {
        var count = 0
        database.traverse { if (it is Entry) count++ }
        return count
    }

    @Test
    fun everyVaultOpensAndSurvivesARoundTrip() {
        for (vault in TestVaults.standard) {
            val credentials = credentialsOf(vault)
            val original = vault.file.inputStream().use { KeePassDatabase.decode(it, credentials, cipherProviders = ciphers) }
            val target = File(outDir, vault.fileName)
            target.outputStream().use { original.encode(it, cipherProviders = ciphers) }
            val reread = target.inputStream().use { KeePassDatabase.decode(it, credentials, cipherProviders = ciphers) }
            assertEquals("entry count changed in ${vault.fileName}", countEntries(original), countEntries(reread))
            println("OK ${vault.fileName}: ${original::class.simpleName} ${original.header.version}")
        }
    }
}
