package io.github.catlinux.keynexa.app.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Test

/** The grouping of a one-time code for reading. */
class TotpFormatTest {
    @Test
    fun sixDigitsAreSplitInTwoGroupsOfThree() {
        assertEquals("123 456", formatTotpCode("123456"))
    }

    @Test
    fun sevenDigitsKeepTheFirstThreeTogether() {
        assertEquals("123 4567", formatTotpCode("1234567"))
    }

    @Test
    fun eightDigitsAreSplitInTwoGroupsOfFour() {
        assertEquals("1234 5678", formatTotpCode("12345678"))
    }

    @Test
    fun anEmptyOrUnexpectedCodeIsLeftAlone() {
        assertEquals("", formatTotpCode(""))
        assertEquals("12345", formatTotpCode("12345"))
    }
}
