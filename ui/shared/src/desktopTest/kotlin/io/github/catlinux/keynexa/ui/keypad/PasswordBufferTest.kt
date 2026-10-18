package io.github.catlinux.keynexa.ui.keypad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The password buffer: order, deletion, wiping, and what it must never reveal. */
class PasswordBufferTest {
    @Test
    fun appendKeepsTheOrderAndGrowsPastTheInitialCapacity() {
        val buffer = PasswordBuffer(initialCapacity = 2)

        "abcdef".forEach { buffer.append(it) }

        assertEquals("abcdef".length, buffer.length)
        assertFalse(buffer.isEmpty())
        assertArrayEquals("abcdef".toCharArray(), buffer.copyOut())
    }

    @Test
    fun deleteLastRemovesTheLastCharacterAndWipesItsSlot() {
        val buffer = PasswordBuffer()
        "ab".forEach { buffer.append(it) }

        buffer.deleteLast()

        assertEquals(1, buffer.length)
        assertArrayEquals(charArrayOf('a'), buffer.copyOut())
        assertTrue(backingArray(buffer)[1] == Char(0))
    }

    @Test
    fun deleteLastOnEmptyDoesNothing() {
        val buffer = PasswordBuffer()

        buffer.deleteLast()

        assertEquals(0, buffer.length)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun clearWipesTheWholeBackingArray() {
        val buffer = PasswordBuffer(initialCapacity = 2)
        // Grow past the initial capacity, so the array that is checked is the one in use.
        "abc".forEach { buffer.append(it) }

        buffer.clear()

        assertEquals(0, buffer.length)
        assertTrue(backingArray(buffer).all { it == Char(0) })
    }

    @Test
    fun growthWipesTheArrayThatIsLeftBehind() {
        val buffer = PasswordBuffer(initialCapacity = 2)
        "ab".forEach { buffer.append(it) }
        val old = backingArray(buffer)

        buffer.append('c')

        assertNotSame(old, backingArray(buffer))
        assertTrue(old.all { it == Char(0) })
        assertArrayEquals("abc".toCharArray(), buffer.copyOut())
    }

    @Test
    fun toStringDoesNotRevealTheCharacters() {
        val buffer = PasswordBuffer()
        "xyz".forEach { buffer.append(it) }

        val text = buffer.toString()

        assertFalse(text.contains('x'))
        assertFalse(text.contains('y'))
        assertFalse(text.contains('z'))
    }

    /** Reads the private backing array, so a test can prove that it is wiped. */
    private fun backingArray(buffer: PasswordBuffer): CharArray {
        val field = PasswordBuffer::class.java.getDeclaredField("chars")
        field.isAccessible = true
        return field.get(buffer) as CharArray
    }
}
