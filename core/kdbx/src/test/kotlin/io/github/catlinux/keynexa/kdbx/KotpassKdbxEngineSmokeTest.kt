package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Minimal smoke test of [KotpassKdbxEngine] over the whole corpus: every vault opens, its header can be read, and
 * decoding, encoding and decoding again gives the same vault. The full acceptance battery comes in another task.
 */
class KotpassKdbxEngineSmokeTest {
    private val engine = KotpassKdbxEngine()

    @Test
    fun everyVaultOpensAndSurvivesARoundTrip() {
        for (vault in TestVaults.standard) {
            val credentials = credentialsOf(vault)
            try {
                val data = vault.file.readBytes()
                val decoded = engine.decode(data, credentials)

                assertEquals(
                    "inspect and decode disagree on ${vault.fileName}",
                    decoded.format,
                    engine.inspect(data),
                )

                val rewritten = engine.encode(decoded, credentials)
                val reread = engine.decode(rewritten, credentials)

                assertEquals(
                    "round trip changed ${vault.fileName}",
                    decoded.withoutGenerator(),
                    reread.withoutGenerator(),
                )
            } finally {
                credentials.wipe()
            }
        }
    }

    @Test
    fun aWrongPasswordIsReportedAsWrongCredentials() {
        val vault = TestVaults.standard.first { it.fileName == WRONG_PASSWORD_FILE }
        val data = vault.file.readBytes()
        val credentials = Credentials.of(password = "not-the-password".toCharArray())

        try {
            assertThrows(VaultException.WrongCredentials::class.java) { engine.decode(data, credentials) }
        } finally {
            credentials.wipe()
        }
    }

    private fun credentialsOf(vault: TestVault): Credentials =
        Credentials.of(
            password = vault.password?.toCharArray(),
            keyFile = vault.keyFile?.readBytes(),
        )

    // Encoding always writes the generator as ours, so it is not part of the comparison.
    private fun Vault.withoutGenerator(): Vault = copy(meta = meta.copy(generator = ""))

    private companion object {
        const val WRONG_PASSWORD_FILE = "rich-aes-argon2d.kdbx"
    }
}
