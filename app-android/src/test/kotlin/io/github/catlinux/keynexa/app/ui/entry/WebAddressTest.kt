package io.github.catlinux.keynexa.app.ui.entry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAddressTest {
    @Test
    fun webAddressesOpen() {
        assertTrue(isWebAddress("https://example.org/login"))
        assertTrue(isWebAddress("  HTTP://example.org "))
    }

    @Test
    fun otherSchemesAndPlainTextDoNot() {
        assertFalse(isWebAddress("ssh://server.example.org"))
        assertFalse(isWebAddress("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isWebAddress("javascript:alert(1)"))
        assertFalse(isWebAddress("example.org"))
        assertFalse(isWebAddress(""))
    }
}
