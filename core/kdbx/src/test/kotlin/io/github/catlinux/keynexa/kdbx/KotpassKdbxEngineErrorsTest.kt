package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.VaultException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Error behaviour of [KotpassKdbxEngine] on the corpus. Where the real behaviour differs from what would be ideal,
 * the test asserts the real one and a comment says why; the divergences are listed in the task report.
 */
class KotpassKdbxEngineErrorsTest {
    private val engine = KotpassKdbxEngine()

    private val rich = TestVaults.standard.first { it.fileName == RICH }
    private val kdbx31 = TestVaults.standard.first { it.fileName == RICH_KDBX31 }
    private val keyfileOnly = TestVaults.standard.first { it.fileName == KEYFILE_ONLY }

    @Test
    fun wrongPasswordIsWrongCredentials() {
        assertDecodeFails(VaultException.WrongCredentials::class.java, rich.file.readBytes(), password(CANARY))
    }

    @Test
    fun wrongPasswordOnKdbx31IsWrongCredentials() {
        // kotpass fails the KDBX 3.x AES-CBC padding check with a wrong key, so this is CryptoError.InvalidKey.
        assertDecodeFails(VaultException.WrongCredentials::class.java, kdbx31.file.readBytes(), password(CANARY))
    }

    @Test
    fun missingKeyFileIsWrongCredentials() {
        assertDecodeFails(VaultException.WrongCredentials::class.java, keyfileOnly.file.readBytes(), password(CANARY))
    }

    @Test
    fun wrongKeyFileIsWrongCredentials() {
        val wrongKeyFile =
            TestVaults.standard
                .first { it.fileName == KPXC_KEYFILE }
                .keyFile!!
                .readBytes()
        assertDecodeFails(
            VaultException.WrongCredentials::class.java,
            keyfileOnly.file.readBytes(),
            keyFile(wrongKeyFile),
        )
    }

    @Test
    fun unparseableKeyFileIsTreatedAsRawKeyData() {
        // A KDBX key file may be any bytes: kotpass hashes a key file it cannot parse, so this is a wrong key.
        val truncated = keyfileOnly.keyFile!!.readBytes().copyOf(40)
        assertDecodeFails(VaultException.WrongCredentials::class.java, keyfileOnly.file.readBytes(), keyFile(truncated))
    }

    @Test
    fun structurallyInvalidXmlKeyFileIsInvalidKeyFile() {
        assertDecodeFails(
            VaultException.InvalidKeyFile::class.java,
            keyfileOnly.file.readBytes(),
            keyFile(xmlKeyFile("3.0", null)),
        )
        assertDecodeFails(
            VaultException.InvalidKeyFile::class.java,
            keyfileOnly.file.readBytes(),
            keyFile(xmlKeyFile("2.0", "00000000")),
        )
    }

    @Test
    fun emptyAndRandomDataAreNotAVault() {
        assertDecodeFails(VaultException.NotAVault::class.java, ByteArray(0), password(CANARY))
        assertDecodeFails(VaultException.NotAVault::class.java, ByteArray(512) { (it * 7).toByte() }, password(CANARY))
    }

    @Test
    fun aCorruptedSignatureIsNotAVault() {
        assertDecodeFails(VaultException.NotAVault::class.java, flipped(rich.file.readBytes(), 0), password(CANARY))
    }

    @Test
    fun aTruncatedHeaderIsCorrupted() {
        assertDecodeFails(VaultException.Corrupted::class.java, rich.file.readBytes().copyOf(40), goodPassword(rich))
        assertDecodeFails(VaultException.Corrupted::class.java, rich.file.readBytes().copyOf(100), goodPassword(rich))
    }

    // With the right credentials a damaged KDBX 4 file must never look like a wrong password: the caller retries
    // later (it may be a partial synchronisation transfer) instead of asking again, and never overwrites it.
    @Test
    fun aTruncatedKdbx4VaultIsCorruptedWhateverTheCipher() {
        for (vault in damageableKdbx4Vaults()) {
            val data = vault.file.readBytes()
            for (cut in listOf(data.size / 2, data.size * 9 / 10, data.size - 1)) {
                assertDecodeFails(VaultException.Corrupted::class.java, data.copyOf(cut), goodPassword(vault))
            }
        }
    }

    @Test
    fun aChangedHeaderByteIsCorrupted() {
        assertDecodeFails(VaultException.Corrupted::class.java, flipped(rich.file.readBytes(), 40), goodPassword(rich))
    }

    @Test
    fun aChangedContentByteIsCorruptedWhateverTheCipher() {
        for (vault in damageableKdbx4Vaults()) {
            val data = vault.file.readBytes()
            assertDecodeFails(VaultException.Corrupted::class.java, flipped(data, data.size - 1), goodPassword(vault))
            assertDecodeFails(VaultException.Corrupted::class.java, flipped(data, data.size / 2), goodPassword(vault))
        }
    }

    // KDBX 3.x has no integrity check before decryption: a wrong key and a cut file both fail the AES padding or
    // the stream start bytes, so they cannot be told apart. Callers must treat a failure with credentials that
    // opened the same vault before as damage, not as a wrong password.
    @Test
    fun aTruncatedKdbx3VaultIsIndistinguishableFromAWrongKey() {
        val data = kdbx31.file.readBytes()
        assertDecodeFails(VaultException.WrongCredentials::class.java, data.copyOf(data.size / 2), goodPassword(kdbx31))
    }

    @Test
    fun inspectRejectsDataThatIsNotAVault() {
        assertInspectFails(VaultException.NotAVault::class.java, ByteArray(0))
        assertInspectFails(VaultException.NotAVault::class.java, ByteArray(512) { (it * 7).toByte() })
        assertInspectFails(VaultException.Corrupted::class.java, rich.file.readBytes().copyOf(40))
        assertInspectFails(VaultException.NotAVault::class.java, flipped(rich.file.readBytes(), 0))
    }

    private fun damageableKdbx4Vaults() =
        listOf("rich-aes-argon2d.kdbx", "rich-chacha20-argon2id.kdbx", "kpxc-twofish-argon2d.kdbx", "kpxc-aeskdf.kdbx")
            .map { name -> TestVaults.standard.first { it.fileName == name } }

    private fun goodPassword(vault: TestVault) = Credentials.of(password = requireNotNull(vault.password).toCharArray())

    private fun assertDecodeFails(
        expected: Class<out VaultException>,
        data: ByteArray,
        credentials: Credentials,
    ): VaultException {
        val failure =
            try {
                assertThrows(expected) { engine.decode(data, credentials) }
            } finally {
                credentials.wipe()
            }
        assertNoCanaryLeak(failure)
        return failure
    }

    private fun assertInspectFails(
        expected: Class<out VaultException>,
        data: ByteArray,
    ): VaultException {
        val failure = assertThrows(expected) { engine.inspect(data) }
        assertNoCanaryLeak(failure)
        return failure
    }

    private fun assertNoCanaryLeak(failure: Throwable) {
        for (secret in listOf(CANARY, REAL_PASSWORD)) {
            assertFalse("the failure message leaks a password", failure.message.orEmpty().contains(secret))
            assertFalse("the failure text leaks a password", failure.toString().contains(secret))
        }
    }

    private fun password(value: String) = Credentials.of(password = value.toCharArray())

    private fun keyFile(data: ByteArray) = Credentials.of(keyFile = data)

    private fun flipped(
        bytes: ByteArray,
        index: Int,
    ): ByteArray = bytes.copyOf().also { it[index] = (it[index] + 1).toByte() }

    private fun xmlKeyFile(
        version: String,
        hash: String?,
    ): ByteArray {
        val hashAttribute = if (hash == null) "" else " Hash=\"$hash\""
        return """<?xml version="1.0" encoding="UTF-8"?>
            |<KeyFile>
            |    <Meta><Version>$version</Version></Meta>
            |    <Key><Data$hashAttribute>00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF</Data></Key>
            |</KeyFile>
            """.trimMargin().toByteArray()
    }

    private companion object {
        const val RICH = "rich-aes-argon2d.kdbx"
        const val RICH_KDBX31 = "rich-kdbx31-aes-aeskdf.kdbx"
        const val KEYFILE_ONLY = "keyfile-only.kdbx"
        const val KPXC_KEYFILE = "kpxc-keyfile-password.kdbx"
        const val CANARY = "canary-password-xyz"
        const val REAL_PASSWORD = "test-password-1"
    }
}
