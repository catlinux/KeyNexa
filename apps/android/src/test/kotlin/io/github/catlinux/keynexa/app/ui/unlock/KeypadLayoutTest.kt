package io.github.catlinux.keynexa.app.ui.unlock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCENTS = "áéíóúàèìòùäëïöüâêîôûñç"
private const val MAX_ROW_KEYS = 10

/** The keypad layout model: every character is reachable and no row grows past ten keys. */
class KeypadLayoutTest {
    @Test
    fun everyPrintableAsciiCharacterCanBeTyped() {
        for (expected in ' '..'~') {
            assertCanType(expected)
        }
    }

    @Test
    fun everyAccentAndItsUpperCaseCanBeTyped() {
        ACCENTS.forEach { accent ->
            assertCanType(accent)
            assertCanType(accent.uppercaseChar())
        }
    }

    @Test
    fun shiftCyclesAndOnceFallsBackAfterOneCharacter() {
        val state = KeypadState()
        val buffer = PasswordBuffer()

        assertEquals(ShiftState.Off, state.shift)
        state.press(KeypadKey.Shift, buffer)
        assertEquals(ShiftState.Once, state.shift)
        state.press(KeypadKey.Shift, buffer)
        assertEquals(ShiftState.Locked, state.shift)
        state.press(KeypadKey.Shift, buffer)
        assertEquals(ShiftState.Off, state.shift)

        state.press(KeypadKey.Shift, buffer)
        state.press(KeypadKey.Character('A'), buffer)

        assertEquals(ShiftState.Off, state.shift)
        assertEquals(1, buffer.length)
    }

    @Test
    fun doneReturnsTrueAndLeavesTheBufferAlone() {
        val state = KeypadState()
        val buffer = PasswordBuffer()
        state.press(KeypadKey.Character('a'), buffer)

        val done = state.press(KeypadKey.Done, buffer)

        assertTrue(done)
        assertEquals(1, buffer.length)
        assertArrayEquals(charArrayOf('a'), buffer.copyOut())
    }

    @Test
    fun pageSwitchesAndKeepsTheShiftState() {
        val state = KeypadState()
        val buffer = PasswordBuffer()
        state.press(KeypadKey.Shift, buffer)

        state.press(KeypadKey.Page(KeypadPage.Accents), buffer)

        assertEquals(KeypadPage.Accents, state.page)
        assertEquals(ShiftState.Once, state.shift)
    }

    @Test
    fun lettersAreShownInUpperCaseWhenShiftIsOn() {
        val lower = KeypadLayout.rows(KeypadPage.Letters, ShiftState.Off).flatten()
        val upper = KeypadLayout.rows(KeypadPage.Letters, ShiftState.Locked).flatten()

        assertTrue(lower.contains(KeypadKey.Character('a')))
        assertTrue(upper.contains(KeypadKey.Character('A')))
        assertFalse(upper.contains(KeypadKey.Character('a')))
    }

    @Test
    fun noRowHasMoreThanTenKeys() {
        for (page in KeypadPage.entries) {
            for (shift in ShiftState.entries) {
                KeypadLayout.rows(page, shift).forEach { row ->
                    assertTrue("$page/$shift has a row of ${row.size} keys", row.size <= MAX_ROW_KEYS)
                }
            }
        }
    }

    /** Types [expected] from a fresh state, on the page and with the shift that show it. */
    private fun assertCanType(expected: Char) {
        val state = KeypadState()
        val buffer = PasswordBuffer()

        assertTrue("no key types '$expected'", typeCharacter(state, buffer, expected))
        assertEquals(1, buffer.length)
        assertArrayEquals(charArrayOf(expected), buffer.copyOut())
    }

    /** Finds a key for [expected] on any page and shift, and presses it on a fresh state. */
    private fun typeCharacter(
        state: KeypadState,
        buffer: PasswordBuffer,
        expected: Char,
    ): Boolean {
        for (page in KeypadPage.entries) {
            for (shift in ShiftState.entries) {
                val key =
                    KeypadLayout
                        .rows(page, shift)
                        .flatten()
                        .filterIsInstance<KeypadKey.Character>()
                        .firstOrNull { it.char == expected } ?: continue
                state.press(KeypadKey.Page(page), buffer)
                setShift(state, buffer, shift)
                state.press(key, buffer)
                return true
            }
        }
        return false
    }

    private fun setShift(
        state: KeypadState,
        buffer: PasswordBuffer,
        target: ShiftState,
    ) {
        while (state.shift != target) {
            state.press(KeypadKey.Shift, buffer)
        }
    }
}
