package io.github.catlinux.keynexa.app.security

import java.io.File
import java.security.MessageDigest

/** What the Keystore key encrypted: the initialisation vector and the ciphertext. */
class BiometricBlob(
    val iv: ByteArray,
    val cipherText: ByteArray,
)

/**
 * The private files of the biometric unlock, one set per vault, in a directory excluded from backup: the encrypted
 * blob (ADR-0006), the moment the master password was last typed, and whether the user declined the offer. Holds
 * nothing readable without the biometric-bound Keystore key. The clock is injected so the rules can be tested.
 */
@Suppress("TooManyFunctions")
class BiometricStore(
    private val dir: File,
    private val now: () -> Long,
) {
    fun has(alias: String): Boolean = blobFile(alias).isFile

    fun save(
        alias: String,
        blob: BiometricBlob,
    ) {
        require(blob.iv.size in 1..MAX_IV_SIZE) { "Invalid IV size" }
        dir.mkdirs()
        blobFile(alias).writeBytes(byteArrayOf(blob.iv.size.toByte()) + blob.iv + blob.cipherText)
    }

    /** The stored blob, or null when there is none or the file is malformed. */
    fun load(alias: String): BiometricBlob? {
        val bytes = blobFile(alias).takeIf { it.isFile }?.readBytes() ?: return null
        val ivSize = bytes.firstOrNull()?.toInt() ?: return null
        if (ivSize !in 1..MAX_IV_SIZE || bytes.size <= 1 + ivSize) return null
        return BiometricBlob(bytes.copyOfRange(1, 1 + ivSize), bytes.copyOfRange(1 + ivSize, bytes.size))
    }

    /** Deletes the blob and the timestamp; the "declined" mark stays. */
    fun delete(alias: String) {
        blobFile(alias).delete()
        typedFile(alias).delete()
    }

    /** Records that the master password was typed just now. */
    fun markPasswordTyped(alias: String) {
        dir.mkdirs()
        typedFile(alias).writeText(now().toString())
    }

    /** True when the password must be typed again: never recorded, or more than [maxDays] days ago. */
    fun passwordDue(
        alias: String,
        maxDays: Int,
    ): Boolean {
        val typedAt =
            typedFile(alias)
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.toLongOrNull() ?: return true
        val elapsed = now() - typedAt
        return elapsed < 0 || elapsed >= maxDays * MILLIS_PER_DAY
    }

    fun markDeclined(alias: String) {
        dir.mkdirs()
        declinedFile(alias).writeText("1")
    }

    fun declined(alias: String): Boolean = declinedFile(alias).isFile

    fun clearDeclined(alias: String) {
        declinedFile(alias).delete()
    }

    private fun blobFile(alias: String) = File(dir, "$alias.blob")

    private fun typedFile(alias: String) = File(dir, "$alias.typed")

    private fun declinedFile(alias: String) = File(dir, "$alias.declined")

    companion object {
        private const val MAX_IV_SIZE = 32
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        private const val ALIAS_PREFIX = "keynexa.bio."
        private const val ALIAS_HEX_LENGTH = 32

        /** The Keystore alias and file name for a vault: derived from a hash of its location, never the location. */
        fun aliasFor(location: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(location.toByteArray(Charsets.UTF_8))
            return ALIAS_PREFIX + digest.joinToString("") { "%02x".format(it) }.take(ALIAS_HEX_LENGTH)
        }
    }
}
