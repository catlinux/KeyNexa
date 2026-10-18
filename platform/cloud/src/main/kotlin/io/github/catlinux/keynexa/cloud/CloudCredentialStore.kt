package io.github.catlinux.keynexa.cloud

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals and opens a small blob of bytes; [open] answers null when the blob cannot be opened. */
interface SecretBox {
    fun seal(plain: ByteArray): ByteArray

    /** The plain bytes, or null when the key is different, the blob was altered or its format is invalid. */
    fun open(sealed: ByteArray): ByteArray?
}

/** An [SecretBox] over AES-256-GCM: a version byte, a random 12-byte IV and the ciphertext with its 128-bit tag. */
class AesGcmSecretBox(
    private val key: SecretKey,
) : SecretBox {
    override fun seal(plain: ByteArray): ByteArray {
        // The cipher picks the IV itself: the Android Keystore refuses an IV given by the caller.
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV length" }
        val encrypted = cipher.doFinal(plain)
        return ByteArray(HEADER_BYTES + encrypted.size).also { sealed ->
            sealed[0] = VERSION
            System.arraycopy(iv, 0, sealed, VERSION_BYTES, IV_BYTES)
            System.arraycopy(encrypted, 0, sealed, HEADER_BYTES, encrypted.size)
        }
    }

    @Suppress("SwallowedException")
    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size < HEADER_BYTES + TAG_BYTES || sealed[0] != VERSION) return null
        return try {
            val iv = sealed.copyOfRange(VERSION_BYTES, HEADER_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(sealed, HEADER_BYTES, sealed.size - HEADER_BYTES)
        } catch (failure: GeneralSecurityException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val VERSION_BYTES = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val HEADER_BYTES = VERSION_BYTES + IV_BYTES
    }
}

/**
 * The cloud credentials on disk, always sealed.
 *
 * [save] serializes the values with `Properties`, seals them and replaces the file atomically (a temporary file in
 * the same directory and a move), so a crash never leaves a half-written credentials file. [load] never throws for
 * corrupt or unreadable content: it answers null. [clear] overwrites the file with zeros before deleting it.
 */
class CloudCredentialStore(
    private val file: File,
    private val box: SecretBox,
) {
    fun save(values: Map<String, String>) {
        val sealed = box.seal(serialize(values))
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}$TEMPORARY_SUFFIX")
        try {
            temporary.writeBytes(sealed)
            replace(temporary)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun load(): Map<String, String>? {
        if (!file.isFile) return null
        val sealed =
            try {
                file.readBytes()
            } catch (failure: IOException) {
                return null
            }
        return parse(box.open(sealed) ?: return null)
    }

    fun clear() {
        if (!file.exists()) return
        val length = file.length()
        if (length > 0) file.writeBytes(ByteArray(length.toInt()))
        file.delete()
    }

    private fun serialize(values: Map<String, String>): ByteArray {
        val properties = Properties()
        values.forEach { (key, value) -> properties.setProperty(key, value) }
        val out = ByteArrayOutputStream()
        properties.store(out, null)
        return out.toByteArray()
    }

    @Suppress("SwallowedException")
    private fun parse(plain: ByteArray): Map<String, String>? =
        try {
            val properties = Properties()
            properties.load(ByteArrayInputStream(plain))
            properties.stringPropertyNames().associateWith { properties.getProperty(it) }
        } catch (failure: IllegalArgumentException) {
            null
        } catch (failure: IOException) {
            null
        }

    @Suppress("SwallowedException")
    private fun replace(temporary: File) {
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val TEMPORARY_SUFFIX = ".tmp"
    }
}
