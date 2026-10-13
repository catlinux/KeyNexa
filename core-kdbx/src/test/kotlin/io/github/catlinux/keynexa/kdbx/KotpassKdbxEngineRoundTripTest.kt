package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Cipher
import io.github.catlinux.keynexa.vault.Compression
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FormatVersion
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultException
import io.github.catlinux.keynexa.vault.VaultFormat
import io.github.catlinux.keynexa.vault.VaultMeta
import io.github.catlinux.keynexa.vault.XmlElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

/** AC2 (unit part): what the engine writes back can be read again without losing the documented content. */
class KotpassKdbxEngineRoundTripTest {
    private val engine = KotpassKdbxEngine()

    @Test
    fun everyVaultSurvivesARoundTrip() {
        for (vault in TestVaults.standard) {
            withCredentials(vault) { credentials ->
                val original = engine.decode(vault.file.readBytes(), credentials)
                val rewritten = engine.encode(original, credentials)

                assertEquals(
                    "inspect of ${vault.fileName}'s rewrite",
                    original.format,
                    engine.inspect(rewritten),
                )
                assertEquals(
                    "round trip changed ${vault.fileName}",
                    original.withoutGenerator(),
                    engine.decode(rewritten, credentials).withoutGenerator(),
                )
            }
        }
    }

    @Test
    fun everyCipherAndKdfCombinationSurvivesARoundTrip() {
        val file = richVault()
        withCredentials(file) { credentials ->
            val source = engine.decode(file.file.readBytes(), credentials)

            for (version in listOf(FormatVersion(4, 0), FormatVersion(4, 1))) {
                for (cipher in listOf(Cipher.Aes256, Cipher.ChaCha20, Cipher.Twofish256)) {
                    for (kdf in listOf(aesKdf(), argon2(Argon2Variant.Argon2d), argon2(Argon2Variant.Argon2id))) {
                        assertFormatRoundTrip(source, version, cipher, kdf, credentials)
                    }
                }
            }
            assertFormatRoundTrip(source, FormatVersion(3, 1), Cipher.Aes256, aesKdf(), credentials)
        }
    }

    @Test
    fun encodeWithDifferentCredentialsUsesTheNewOnes() {
        val file = richVault()
        val newCredentials = Credentials.of(password = NEW_PASSWORD.toCharArray())

        try {
            val data =
                withCredentials(file) { old ->
                    engine.encode(engine.decode(file.file.readBytes(), old), newCredentials)
                }

            engine.decode(data, newCredentials)
            withCredentials(file) { old ->
                assertThrows(VaultException.WrongCredentials::class.java) { engine.decode(data, old) }
            }
        } finally {
            newCredentials.wipe()
        }
    }

    @Test
    fun encodingTwiceProducesDifferentFilesWithTheSameVault() {
        val file = richVault()
        withCredentials(file) { credentials ->
            val vault = engine.decode(file.file.readBytes(), credentials)
            val first = engine.encode(vault, credentials)
            val second = engine.encode(vault, credentials)

            assertFalse("fresh salts and seeds must change the bytes", first.contentEquals(second))
            assertEquals(vault.withoutGenerator(), engine.decode(first, credentials).withoutGenerator())
            assertEquals(
                engine.decode(first, credentials).withoutGenerator(),
                engine.decode(second, credentials).withoutGenerator(),
            )
        }
    }

    @Test
    fun unknownElementsSurviveARoundTrip() {
        val unknown = XmlElement("FutureElement", mapOf("flag" to "yes"), "text")
        val vault =
            Vault(
                format = newFormat(FormatVersion(4, 1), Cipher.Aes256, aesKdf()),
                meta = VaultMeta(name = "hand-built").copy(unknownElements = listOf(unknown)),
                root =
                    Group(
                        id = UUID.randomUUID(),
                        name = "Root",
                        entries = listOf(Entry(id = UUID.randomUUID(), unknownElements = listOf(unknown))),
                        unknownElements = listOf(unknown),
                    ),
                unknownElements = listOf(unknown),
                unknownRootElements = listOf(unknown),
            )
        val credentials = Credentials.of(password = NEW_PASSWORD.toCharArray())

        try {
            val decoded = engine.decode(engine.encode(vault, credentials), credentials)

            assertEquals(listOf(unknown), decoded.unknownElements)
            assertEquals(listOf(unknown), decoded.unknownRootElements)
            assertEquals(listOf(unknown), decoded.meta.unknownElements)
            assertEquals(listOf(unknown), decoded.root.unknownElements)
            assertEquals(
                listOf(unknown),
                decoded.root.entries
                    .single()
                    .unknownElements,
            )
        } finally {
            credentials.wipe()
        }
    }

    private fun assertFormatRoundTrip(
        source: Vault,
        version: FormatVersion,
        cipher: Cipher,
        kdf: Kdf,
        credentials: Credentials,
    ) {
        val expected = source.copy(format = newFormat(version, cipher, kdf))
        val actual = engine.decode(engine.encode(expected, credentials), credentials)

        assertEquals(
            "round trip changed the content with $version $cipher $kdf",
            withoutVersionSpecificMetadata(expected.withoutGenerator()),
            withoutVersionSpecificMetadata(actual.withoutGenerator()),
        )
        assertEquals(expected.format, actual.format)
    }

    // KDBX 3.x has no SettingsChanged element, so it cannot survive a downgrade from 4.x.
    private fun withoutVersionSpecificMetadata(vault: Vault): Vault =
        if (vault.format.version.major >= 4) vault else vault.copy(meta = vault.meta.copy(settingsChanged = null))

    private fun newFormat(
        version: FormatVersion,
        cipher: Cipher,
        kdf: Kdf,
    ) = VaultFormat(
        version = version,
        cipher = cipher,
        kdf = kdf,
        compression = Compression.Gzip,
    )

    private fun aesKdf() = Kdf.AesKdf(1000)

    private fun argon2(variant: Argon2Variant) =
        Kdf.Argon2(
            variant = variant,
            memoryBytes = 8L * 1024 * 1024,
            iterations = 2,
            parallelism = 2,
            version = 19,
        )

    private fun richVault() = TestVaults.standard.first { it.fileName == "rich-aes-argon2d.kdbx" }

    private fun Vault.withoutGenerator(): Vault = copy(meta = meta.copy(generator = ""))

    private fun <T> withCredentials(
        vault: TestVault,
        block: (Credentials) -> T,
    ): T {
        val credentials =
            Credentials.of(
                password = vault.password?.toCharArray(),
                keyFile = vault.keyFile?.readBytes(),
            )
        try {
            return block(credentials)
        } finally {
            credentials.wipe()
        }
    }

    private companion object {
        const val NEW_PASSWORD = "a-brand-new-password"
    }
}
