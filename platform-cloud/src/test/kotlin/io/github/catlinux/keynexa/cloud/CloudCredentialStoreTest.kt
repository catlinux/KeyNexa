package io.github.catlinux.keynexa.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The sealed credentials file: round trip, what the disk really holds, and every way it can fail. */
class CloudCredentialStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun valuesSurviveARoundTrip() {
        val store = store()

        store.save(VALUES)

        assertEquals(VALUES, store.load())
    }

    @Test
    fun theFileOnDiskHoldsNoPlaintext() {
        val store = store()

        store.save(VALUES)

        val raw = file().readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("api-key-secret"))
        assertFalse(raw.contains("s3-secret-value"))
    }

    @Test
    fun anotherKeyCannotOpenTheFile() {
        store().save(VALUES)

        val other = store(box = AesGcmSecretBox(key()))

        assertNull(other.load())
    }

    @Test
    fun anAlteredByteCannotBeOpened() {
        val store = store()
        store.save(VALUES)
        val bytes = file().readBytes()
        bytes[bytes.lastIndex] = (bytes[bytes.lastIndex].toInt() xor 0x01).toByte()
        file().writeBytes(bytes)

        assertNull(store.load())
    }

    @Test
    fun anEmptyOrTruncatedFileCannotBeOpened() {
        val store = store()
        file().writeBytes(ByteArray(0))
        assertNull(store.load())

        store.save(VALUES)
        file().writeBytes(file().readBytes().copyOf(TRUNCATED_SIZE))
        assertNull(store.load())
    }

    @Test
    fun aMissingFileLoadsAsNull() {
        assertNull(store().load())
    }

    @Test
    fun clearLeavesNothingToLoad() {
        val store = store()
        store.save(VALUES)

        store.clear()

        assertNull(store.load())
        assertFalse(file().exists())
    }

    @Test
    fun saveLeavesNoTemporaryFileBehind() {
        store().save(VALUES)

        val names = requireNotNull(file().parentFile).list().orEmpty()
        assertFalse(names.any { it.endsWith(".tmp") })
    }

    @Test
    fun saveReplacesThePreviousValues() {
        val store = store()
        store.save(mapOf("a" to "1"))
        store.save(mapOf("b" to "2"))

        assertEquals(mapOf("b" to "2"), store.load())
    }

    @Test
    fun unicodeEqualsSignsAndNewlinesSurvive() {
        val store = store()
        val values = mapOf("clave" to "ñ=á\nlinea2", "otra" to "=valor=")

        store.save(values)

        assertEquals(values, store.load())
    }

    private fun store(box: SecretBox = AesGcmSecretBox(key())): CloudCredentialStore = CloudCredentialStore(file(), box)

    private fun file(): File {
        val file = File(requireNotNull(temporary.root), "cloud/credentials.bin")
        file.parentFile?.mkdirs()
        return file
    }

    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private companion object {
        const val TRUNCATED_SIZE = 5
        val VALUES = mapOf("api-key-secret" to "s3-secret-value", "region" to "us-east-1")
    }
}
