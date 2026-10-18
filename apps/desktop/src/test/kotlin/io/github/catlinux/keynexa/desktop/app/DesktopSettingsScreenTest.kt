package io.github.catlinux.keynexa.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a choice of minutes or seconds is labelled. */
class DesktopSettingsScreenTest {
    @Test
    fun zeroAndBelowAreNever() {
        assertEquals(ChoiceLabel.Never, choiceLabel(0))
        assertEquals(ChoiceLabel.Never, choiceLabel(-1))
    }

    @Test
    fun anyOtherValueKeepsItsAmount() {
        assertEquals(ChoiceLabel.Amount(5), choiceLabel(5))
        assertEquals(ChoiceLabel.Amount(30), choiceLabel(30))
        assertEquals(ChoiceLabel.Amount(120), choiceLabel(120))
    }
}
