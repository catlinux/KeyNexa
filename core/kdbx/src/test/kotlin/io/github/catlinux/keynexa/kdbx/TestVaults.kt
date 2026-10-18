package io.github.catlinux.keynexa.kdbx

import java.io.File

/** One fictitious vault of the `testdata/` corpus and the credentials that open it (see docs/TEST_VAULTS.md). */
data class TestVault(
    val fileName: String,
    val password: String?,
    val keyFileName: String? = null,
) {
    val file: File get() = TestVaults.dir.resolve(fileName)
    val keyFile: File? get() = keyFileName?.let { TestVaults.dir.resolve(it) }
}

object TestVaults {
    private const val PASSWORD = "test-password-1"
    private const val UNICODE_PASSWORD = "pässwörd-日本語-😀"

    /** `testdata/` at the repository root, found by walking up from the module's working directory. */
    val dir: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "testdata") }
            .first { it.isDirectory }

    /** Every vault of the corpus except the 1 GiB Argon2 one, which is measured separately. */
    val standard: List<TestVault> =
        listOf(
            "rich-aes-argon2d.kdbx",
            "rich-aes-argon2id.kdbx",
            "rich-chacha20-argon2d.kdbx",
            "rich-chacha20-argon2id.kdbx",
            "rich-aes-aeskdf.kdbx",
            "rich-chacha20-aeskdf.kdbx",
            "rich-kdbx31-aes-aeskdf.kdbx",
            "rich-aes-argon2d-tags.kdbx",
            "kpxc-aeskdf.kdbx",
            "kpxc-empty.kdbx",
            "kpxc-twofish-argon2d.kdbx",
            "kpxc-chacha20-argon2id.kdbx",
            "kpxc-argon2d-tags-totp.kdbx",
        ).map { TestVault(it, PASSWORD) } +
            listOf(
                TestVault("kpxc-keyfile-password.kdbx", PASSWORD, "kpxc-keyfile.keyx"),
                TestVault("keyfile-only.kdbx", null, "keyfile-only.key"),
                TestVault("keyfile-password-kp2.kdbx", PASSWORD, "keyfile-only.key"),
                TestVault("unicode-password.kdbx", UNICODE_PASSWORD),
            )

    val argon2OneGiB = TestVault("argon2-1gib.kdbx", PASSWORD)
}
