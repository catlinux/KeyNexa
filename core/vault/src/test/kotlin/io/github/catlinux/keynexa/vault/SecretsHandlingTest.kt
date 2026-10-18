package io.github.catlinux.keynexa.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretsHandlingTest {
    @Test
    fun fieldValueNeverPrintsItsContent() {
        val text = FieldValue("correct-horse-battery-staple", protected = true).toString()

        assertFalse(text.contains("horse"))
        assertTrue(text.contains("length=28"))
    }

    @Test
    fun fieldValuesCompareByContent() {
        assertEquals(FieldValue("a", true), FieldValue("a", true))
        assertNotEquals(FieldValue("a", true), FieldValue("a", false))
        assertNotEquals(FieldValue("a"), FieldValue("b"))
    }

    @Test
    fun binaryNeverPrintsItsContentAndComparesByContent() {
        val binary = Binary(byteArrayOf(1, 2, 3), protectedInMemory = true)

        assertEquals("Binary(size=3, protectedInMemory=true)", binary.toString())
        assertEquals(binary, Binary(byteArrayOf(1, 2, 3), protectedInMemory = true))
        assertNotEquals(binary, Binary(byteArrayOf(1, 2, 4), protectedInMemory = true))
    }

    @Test
    fun credentialsNeedAtLeastOneSecret() {
        assertThrows(IllegalArgumentException::class.java) { Credentials.of() }
    }

    @Test
    fun credentialsNeverPrintTheirSecrets() {
        val text = Credentials.of(password = "hunter2".toCharArray(), keyFile = byteArrayOf(9, 9)).toString()

        assertFalse(text.contains("hunter2"))
        assertEquals("Credentials(password=true, keyFile=true)", text)
    }

    @Test
    fun copiesAreIndependentOfTheStoredSecrets() {
        val credentials = Credentials.of(password = "secret".toCharArray())

        credentials.passwordCopy()!!.fill('x')

        assertArrayEquals("secret".toCharArray(), credentials.passwordCopy())
    }

    @Test
    fun wipingOverwritesTheOriginalArrays() {
        val password = "secret".toCharArray()
        val keyFile = byteArrayOf(1, 2, 3)
        val credentials = Credentials.of(password, keyFile)

        credentials.wipe()

        assertTrue(password.all { it == '\u0000' })
        assertTrue(keyFile.all { it == 0.toByte() })
        assertFalse(credentials.hasPassword)
        assertFalse(credentials.hasKeyFile)
        assertThrows(IllegalStateException::class.java) { credentials.passwordCopy() }
        assertThrows(IllegalStateException::class.java) { credentials.keyFileCopy() }
    }

    @Test
    fun closingWipes() {
        val password = "secret".toCharArray()

        Credentials.of(password).use { }

        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun aKeyFileAloneIsEnough() {
        val credentials = Credentials.of(keyFile = byteArrayOf(5))

        assertFalse(credentials.hasPassword)
        assertNull(credentials.passwordCopy())
        assertTrue(credentials.hasKeyFile)
    }

    @Test
    fun argon2ReportsItsMemoryInMebibytes() {
        val kdf =
            Kdf.Argon2(
                variant = Argon2Variant.Argon2id,
                memoryBytes = 64L * 1024 * 1024,
                iterations = 10,
                parallelism = 2,
                version = 0x13,
            )

        assertEquals(64L, kdf.memoryMiB)
    }
}
