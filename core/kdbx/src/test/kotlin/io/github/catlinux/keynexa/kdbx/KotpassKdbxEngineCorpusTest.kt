package io.github.catlinux.keynexa.kdbx

import io.github.catlinux.keynexa.vault.Argon2Variant
import io.github.catlinux.keynexa.vault.Cipher
import io.github.catlinux.keynexa.vault.Credentials
import io.github.catlinux.keynexa.vault.Entry
import io.github.catlinux.keynexa.vault.FormatVersion
import io.github.catlinux.keynexa.vault.Group
import io.github.catlinux.keynexa.vault.Kdf
import io.github.catlinux.keynexa.vault.ProtectableField
import io.github.catlinux.keynexa.vault.VariantValue
import io.github.catlinux.keynexa.vault.Vault
import io.github.catlinux.keynexa.vault.VaultFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

/** AC1: every corpus vault opens with its own credentials and has the content documented in docs/TEST_VAULTS.md. */
class KotpassKdbxEngineCorpusTest {
    private val engine = KotpassKdbxEngine()

    @Test
    fun everyVaultOpensAndHasTheDocumentedFormat() {
        for (vault in TestVaults.standard) {
            withCredentials(vault) { credentials ->
                val decoded = engine.decode(vault.file.readBytes(), credentials)
                assertEquals(
                    "inspect and decode disagree on ${vault.fileName}",
                    decoded.format,
                    engine.inspect(vault.file.readBytes()),
                )
                assertDocumentedFormat(vault.fileName, decoded.format)
            }
        }
    }

    @Test
    fun richVaultHasTheDocumentedContent() {
        assertRichContent(decode("rich-aes-argon2d.kdbx"))
    }

    @Test
    fun richKdbx31VaultHasTheSameContentWithoutPublicCustomData() {
        val vault = decode("rich-kdbx31-aes-aeskdf.kdbx")

        assertEquals(Kdf.AesKdf(600000), vault.format.kdf)
        assertTrue("3.1 must not carry public custom data", vault.format.publicCustomData.isEmpty())
        assertEquals(null, vault.meta.settingsChanged)
        assertRichContent(vault)
    }

    @Test
    fun richTagsVaultHasTheDocumentedTags() {
        val vault = decode("rich-aes-argon2d-tags.kdbx")

        assertEquals(listOf("web"), vault.root.group("Internet").tags)
        assertEquals(
            listOf("personal"),
            vault.root
                .group("Internet")
                .entry("Example mail")
                .tags,
        )
        assertEquals(
            listOf("shopping"),
            vault.root
                .group("Internet")
                .group("Shopping")
                .entry("Example shop")
                .tags,
        )
        assertTrue("other groups must stay untagged", vault.root.tags.isEmpty())
        assertRichContent(vault)
    }

    @Test
    fun kpxcVaultHasTheDocumentedContent() {
        val vault = decode("kpxc-aeskdf.kdbx")

        assertKpxcContent(vault)
        assertTrue("the KeePassXC vault has no custom icons", vault.meta.customIcons.isEmpty())
        assertTrue("the KeePassXC vault has no public custom data", vault.format.publicCustomData.isEmpty())
    }

    @Test
    fun kpxcTagsTotpVaultHasTheTagAndTheOtpField() {
        val vault = decode("kpxc-argon2d-tags-totp.kdbx")
        val entry = vault.root.group("Internet").entry("Example mail")

        assertEquals(listOf("personal"), entry.tags)
        val otp = entry.fields["otp"]
        assertNotNull(otp)
        assertEquals(TOTP_URI, otp?.value)
        assertTrue("the otp field must be protected", otp?.protected == true)
        assertKpxcContent(vault)
    }

    @Test
    fun kpxcEmptyVaultHasNoEntriesAndNoSubgroups() {
        val vault = decode("kpxc-empty.kdbx")

        assertEquals(listOf("Contraseñas"), listOf(vault.root.name))
        assertTrue("the empty vault has no subgroups", vault.root.groups.isEmpty())
        assertTrue("the empty vault has no entries", vault.root.entries.isEmpty())
        assertTrue("the empty vault has no attachments", vault.binaries.isEmpty())
    }

    @Test
    fun unicodePasswordVaultOpensWithItsUnicodePassword() {
        val vault = decode("unicode-password.kdbx")

        assertRichContent(vault)
    }

    @Test
    fun keyFileVaultsOpen() {
        assertRichContent(decode("keyfile-only.kdbx"))
        assertRichContent(decode("keyfile-password-kp2.kdbx"))
        assertKpxcContent(decode("kpxc-keyfile-password.kdbx"))
    }

    private fun decode(fileName: String): Vault {
        val vault = TestVaults.standard.first { it.fileName == fileName }
        return withCredentials(vault) { credentials -> engine.decode(vault.file.readBytes(), credentials) }
    }

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

    private fun assertDocumentedFormat(
        fileName: String,
        actual: VaultFormat,
    ) {
        val expected = expectedFormats.getValue(fileName)
        assertEquals("$fileName version", expected.version, actual.version)
        assertEquals("$fileName cipher", expected.cipher, actual.cipher)
        val kdf = actual.kdf
        when (val expectedKdf = expected.kdf) {
            is ExpectedKdf.Argon2 -> {
                assertTrue("$fileName must use Argon2, was $kdf", kdf is Kdf.Argon2)
                kdf as Kdf.Argon2
                assertEquals("$fileName variant", expectedKdf.variant, kdf.variant)
                assertEquals("$fileName memory", ARGON2_MEMORY_BYTES, kdf.memoryBytes)
                assertEquals("$fileName iterations", ARGON2_ITERATIONS, kdf.iterations)
                assertEquals("$fileName parallelism", ARGON2_PARALLELISM, kdf.parallelism)
            }
            is ExpectedKdf.Aes -> {
                assertTrue("$fileName must use AES-KDF, was $kdf", kdf is Kdf.AesKdf)
                if (expectedKdf.rounds != null) {
                    assertEquals("$fileName rounds", expectedKdf.rounds, (kdf as Kdf.AesKdf).rounds)
                }
            }
        }
    }

    private fun assertRichContent(vault: Vault) {
        assertEquals("KeyNexa test vault", vault.meta.name)
        assertEquals(365, vault.meta.maintenanceHistoryDays)
        assertEquals(10, vault.meta.historyMaxItems)
        assertEquals(6291456, vault.meta.historyMaxSizeBytes)
        assertEquals(setOf(ProtectableField.Password), vault.meta.protectedFields)
        if (vault.format.version.major >= 4) {
            assertEquals(VariantValue.Text("value"), vault.format.publicCustomData["keynexa-test"])
        }

        val root = vault.root
        assertEquals("Root", root.name)
        assertEquals(49, root.icon.standardId)
        assertEquals(listOf("Internet", "Work", "Recycle Bin"), root.groups.map { it.name })
        assertEquals(root.group("Recycle Bin").id, vault.meta.recycleBinGroup)

        assertUnicodeEntry(root.entries.first { it.title != "Server" })
        assertServerEntry(vault)
        assertRichGroups(vault)
        assertCustomIcon(vault)
        assertAttachmentIsStable(TestVaults.standard.first { it.fileName == "rich-aes-argon2d.kdbx" })
    }

    private fun assertUnicodeEntry(entry: Entry) {
        assertEquals("T$UNICODE_TEXT", entry.title)
        assertTrue(
            "user name must contain the unicode text",
            entry.fields[Entry.USER_NAME]
                ?.value
                .orEmpty()
                .contains(UNICODE_TEXT),
        )
        assertTrue(
            "password must contain the unicode text",
            entry.fields[Entry.PASSWORD]
                ?.value
                .orEmpty()
                .contains(UNICODE_TEXT),
        )
        assertTrue(
            "notes must contain the unicode text",
            entry.fields[Entry.NOTES]
                ?.value
                .orEmpty()
                .contains(UNICODE_TEXT),
        )
        assertEquals("https://unicode.example.org", entry.url)
        assertEquals(true, entry.fields[Entry.PASSWORD]?.protected)
    }

    private fun assertServerEntry(vault: Vault) {
        val server = vault.root.entry("Server")

        assertEquals(
            listOf("ApiKey", "Environment", "Notes", "Password", "Title", "URL", "UserName"),
            server.fields.keys.toList(),
        )
        assertEquals("production", server.fields["Environment"]?.value)
        assertEquals("FAKE-KEY-0123456789", server.fields["ApiKey"]?.value)
        assertEquals(true, server.fields["ApiKey"]?.protected)
        assertEquals("multi\nline\nnotes", server.fields[Entry.NOTES]?.value)
        assertEquals(listOf("random.bin", "readme.txt"), server.attachments.map { it.name })

        val readme = requireNotNull(vault.binaries[server.attachments.first { it.name == "readme.txt" }.id])
        assertEquals("Ficticious attachment.\n", String(readme.content))

        val random = requireNotNull(vault.binaries[server.attachments.first { it.name == "random.bin" }.id])
        assertEquals(65536, random.content.size)
        assertEquals(
            sha256Hex(random.content),
            server.attachments
                .first { it.name == "random.bin" }
                .id.hex,
        )
        assertEquals(
            sha256Hex(readme.content),
            server.attachments
                .first { it.name == "readme.txt" }
                .id.hex,
        )
    }

    private fun assertRichGroups(vault: Vault) {
        val internet = vault.root.group("Internet")
        assertEquals(1, internet.icon.standardId)
        assertEquals("Example mail", internet.entries.first().title)
        assertEquals(19, internet.entry("Example mail").icon.standardId)
        val changing = internet.entry("Changing password")
        assertEquals("v4-password", changing.fields[Entry.PASSWORD]?.value)
        assertEquals(
            listOf("v1-password", "v2-password", "v3-password"),
            changing.history.map { it.fields[Entry.PASSWORD]?.value },
        )
        assertEquals(
            "v3",
            changing.history
                .last()
                .fields[Entry.NOTES]
                ?.value,
        )

        val intranet = vault.root.group("Work").entry("Intranet")
        assertEquals(true, intranet.times.expires)
        assertEquals(Instant.parse("2020-01-01T00:00:00Z"), intranet.times.expiry)
        assertEquals(
            "Deleted entry",
            vault.root
                .group("Recycle Bin")
                .entries
                .single()
                .title,
        )
    }

    private fun assertCustomIcon(vault: Vault) {
        val customIcon =
            vault.root
                .group("Internet")
                .group("Shopping")
                .entry("Example shop")
                .icon.custom
        assertNotNull("the Example shop entry uses a custom icon", customIcon)
        val data = requireNotNull(vault.meta.customIcons[customIcon]).data
        assertArrayEquals(PNG_SIGNATURE, data.copyOf(8))
        assertEquals("icon width", 16, readIntBigEndian(data, 16))
        assertEquals("icon height", 16, readIntBigEndian(data, 20))
    }

    private fun assertAttachmentIsStable(vault: TestVault) {
        withCredentials(vault) { credentials ->
            val first = engine.decode(vault.file.readBytes(), credentials)
            val second = engine.decode(vault.file.readBytes(), credentials)
            for ((id, binary) in first.binaries) {
                assertArrayEquals(
                    "attachment $id differs between decodes",
                    binary.content,
                    second.binaries.getValue(id).content,
                )
            }
        }
    }

    private fun assertKpxcContent(vault: Vault) {
        assertEquals("Contraseñas", vault.root.name)
        assertEquals(listOf("Internet", "Work", "Papelera"), vault.root.groups.map { it.name })

        val server = vault.root.entry("Server")
        assertEquals("Server notes", server.fields[Entry.NOTES]?.value)
        assertEquals(2, server.history.size)
        assertEquals(listOf("random.bin", "readme.txt"), server.attachments.map { it.name })
        assertEquals(
            listOf("v1-password", "v2-password", "v3-password"),
            vault.root
                .group("Internet")
                .entry("Changing password")
                .history
                .map { it.fields[Entry.PASSWORD]?.value },
        )
        assertEquals(
            "Deleted entry",
            vault.root
                .group("Papelera")
                .entries
                .single()
                .title,
        )
        assertEquals(
            "",
            vault.root
                .group("Work")
                .entry("Intranet")
                .fields[Entry.NOTES]
                ?.value,
        )
    }

    private fun readIntBigEndian(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun sha256Hex(content: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))

    private fun Group.group(name: String): Group = groups.first { it.name == name }

    private fun Group.entry(title: String): Entry = entries.first { it.title == title }

    private sealed interface ExpectedKdf {
        data class Aes(
            val rounds: Long?,
        ) : ExpectedKdf

        data class Argon2(
            val variant: Argon2Variant,
        ) : ExpectedKdf
    }

    private data class ExpectedFormat(
        val version: FormatVersion,
        val cipher: Cipher,
        val kdf: ExpectedKdf,
    )

    private companion object {
        const val UNICODE_TEXT = "Ñandú café 日本語 😀"

        const val TOTP_URI =
            "otpauth://totp/Example%20mail:alice%40example.org?secret=JBSWY3DPEHPK3PXP&period=30&digits=6" +
                "&issuer=Example%20mail"

        const val ARGON2_MEMORY_BYTES = 67108864L
        const val ARGON2_ITERATIONS = 10L
        const val ARGON2_PARALLELISM = 2

        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        val expectedFormats =
            mapOf(
                "rich-aes-argon2d.kdbx" to format(4, 0, Cipher.Aes256, argon2(Argon2Variant.Argon2d)),
                "rich-aes-argon2id.kdbx" to format(4, 0, Cipher.Aes256, argon2(Argon2Variant.Argon2id)),
                "rich-chacha20-argon2d.kdbx" to format(4, 0, Cipher.ChaCha20, argon2(Argon2Variant.Argon2d)),
                "rich-chacha20-argon2id.kdbx" to format(4, 0, Cipher.ChaCha20, argon2(Argon2Variant.Argon2id)),
                "rich-aes-aeskdf.kdbx" to format(4, 0, Cipher.Aes256, ExpectedKdf.Aes(600000)),
                "rich-chacha20-aeskdf.kdbx" to format(4, 0, Cipher.ChaCha20, ExpectedKdf.Aes(600000)),
                "rich-kdbx31-aes-aeskdf.kdbx" to format(3, 1, Cipher.Aes256, ExpectedKdf.Aes(600000)),
                "rich-aes-argon2d-tags.kdbx" to format(4, 1, Cipher.Aes256, argon2(Argon2Variant.Argon2d)),
                "kpxc-aeskdf.kdbx" to format(4, 1, Cipher.Aes256, ExpectedKdf.Aes(null)),
                "kpxc-empty.kdbx" to format(3, 1, Cipher.Aes256, ExpectedKdf.Aes(null)),
                "kpxc-twofish-argon2d.kdbx" to format(4, 1, Cipher.Twofish256, argon2(Argon2Variant.Argon2d)),
                "kpxc-chacha20-argon2id.kdbx" to format(4, 1, Cipher.ChaCha20, argon2(Argon2Variant.Argon2id)),
                "kpxc-argon2d-tags-totp.kdbx" to format(4, 1, Cipher.Aes256, argon2(Argon2Variant.Argon2d)),
                "kpxc-keyfile-password.kdbx" to format(4, 1, Cipher.Aes256, ExpectedKdf.Aes(null)),
                "keyfile-only.kdbx" to format(4, 0, Cipher.Aes256, ExpectedKdf.Aes(600000)),
                "keyfile-password-kp2.kdbx" to format(4, 0, Cipher.Aes256, argon2(Argon2Variant.Argon2id)),
                "unicode-password.kdbx" to format(4, 0, Cipher.Aes256, argon2(Argon2Variant.Argon2id)),
            )

        fun format(
            major: Int,
            minor: Int,
            cipher: Cipher,
            kdf: ExpectedKdf,
        ) = ExpectedFormat(
            FormatVersion(major, minor),
            cipher,
            kdf,
        )

        fun argon2(variant: Argon2Variant) = ExpectedKdf.Argon2(variant)
    }
}
