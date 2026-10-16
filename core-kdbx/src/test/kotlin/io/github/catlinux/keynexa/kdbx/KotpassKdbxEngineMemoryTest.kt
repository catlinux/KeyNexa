package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Cipher
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.FormatVersion
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultFormat
import io.github.catlinux.keynexa.vault.VaultMeta
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** An OutOfMemoryError during the key derivation becomes a normal failure, never an Error that crashes the app. */
class KotpassKdbxEngineMemoryTest {
    private val engine = KotpassKdbxEngine { throw OutOfMemoryError("injected") }

    @Test
    fun anOutOfMemoryWhileDecodingIsANotEnoughMemoryFailure() {
        val credentials = Credentials.of(password = "test-password".toCharArray())

        val failure =
            assertThrows(VaultException.NotEnoughMemory::class.java) {
                engine.decode(byteArrayOf(1, 2, 3), credentials)
            }

        assertTrue(failure.cause is OutOfMemoryError)
    }

    @Test
    fun anOutOfMemoryWhileEncodingIsANotEnoughMemoryFailure() {
        val credentials = Credentials.of(password = "test-password".toCharArray())

        val failure =
            assertThrows(VaultException.NotEnoughMemory::class.java) {
                engine.encode(minimalVault(), credentials)
            }

        assertTrue(failure.cause is OutOfMemoryError)
    }

    private fun minimalVault(): Vault =
        Vault(
            format = VaultFormat(FormatVersion(4, 1), Cipher.Aes256, Kdf.AesKdf(1_000L)),
            meta = VaultMeta(name = "test"),
            root = Group(id = UUID.randomUUID(), name = "Root"),
        )
}
