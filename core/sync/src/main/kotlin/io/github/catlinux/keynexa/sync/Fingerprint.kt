package io.github.catlinux.keynexa.sync

import java.security.MessageDigest

/**
 * SHA-256 of a vault file's encrypted bytes. Two equal fingerprints mean byte-identical files, which is how the
 * pipeline detects external changes and verifies its own writes without decrypting again.
 */
@JvmInline
value class Fingerprint(
    val hex: String,
) {
    override fun toString(): String = "Fingerprint(${hex.take(SHORT_LENGTH)}…)"

    companion object {
        private const val SHORT_LENGTH = 12

        fun of(bytes: ByteArray): Fingerprint =
            Fingerprint(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }
}
