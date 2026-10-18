package io.github.catlinux.keynexa.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BiometricStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun store() = BiometricStore(folder.newFolder(), now = { clock })

    @Test
    fun savesAndLoadsTheBlob() {
        val store = store()
        store.save("a", BiometricBlob(byteArrayOf(1, 2, 3), byteArrayOf(9, 8, 7, 6)))

        val loaded = store.load("a")!!

        assertTrue(store.has("a"))
        assertArrayEquals(byteArrayOf(1, 2, 3), loaded.iv)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), loaded.cipherText)
        assertNull(store.load("b"))
    }

    @Test
    fun aDamagedFileIsNotAValidBlob() {
        val dir = folder.newFolder()
        val store = BiometricStore(dir, now = { clock })
        File(dir, "a.blob").writeBytes(byteArrayOf(12, 1, 2))

        assertNull(store.load("a"))
    }

    @Test
    fun deleteRemovesBlobAndTimestampButKeepsDeclined() {
        val store = store()
        store.save("a", BiometricBlob(ByteArray(12), ByteArray(5)))
        store.markPasswordTyped("a")
        store.markDeclined("a")

        store.delete("a")

        assertFalse(store.has("a"))
        assertTrue(store.passwordDue("a", 7))
        assertTrue(store.declined("a"))
        store.clearDeclined("a")
        assertFalse(store.declined("a"))
    }

    @Test
    fun passwordIsDueAfterTheChosenDays() {
        val store = store()
        assertTrue("never typed", store.passwordDue("a", 7))

        store.markPasswordTyped("a")
        clock += 6 * day
        assertFalse(store.passwordDue("a", 7))
        clock += day
        assertTrue(store.passwordDue("a", 7))
        assertFalse(store.passwordDue("a", 30))
    }

    @Test
    fun aClockMovedBackMakesThePasswordDue() {
        val store = store()
        store.markPasswordTyped("a")
        clock -= day

        assertTrue(store.passwordDue("a", 7))
    }

    @Test
    fun aliasDependsOnTheLocationButDoesNotContainIt() {
        val first = BiometricStore.aliasFor("content://x/tree/Claus.kdbx")

        assertEquals(first, BiometricStore.aliasFor("content://x/tree/Claus.kdbx"))
        assertNotEquals(first, BiometricStore.aliasFor("content://x/tree/Other.kdbx"))
        assertFalse(first.contains("Claus"))
    }
}
