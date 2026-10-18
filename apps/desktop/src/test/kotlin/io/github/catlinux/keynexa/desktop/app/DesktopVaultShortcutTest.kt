package io.github.catlinux.keynexa.desktop.app

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The keyboard shortcuts of the open vault and how a one-time code is written out. */
class DesktopVaultShortcutTest {
    @Test
    fun mapsEveryShortcutKey() {
        assertEquals(VaultShortcut.Search, shortcutFor(ctrl = true, key = Key.F))
        assertEquals(VaultShortcut.CopyUserName, shortcutFor(ctrl = true, key = Key.B))
        assertEquals(VaultShortcut.CopyPassword, shortcutFor(ctrl = true, key = Key.C))
        assertEquals(VaultShortcut.OpenUrl, shortcutFor(ctrl = true, key = Key.U))
        assertEquals(VaultShortcut.Lock, shortcutFor(ctrl = true, key = Key.L))
    }

    @Test
    fun needsCtrlAndAKnownKey() {
        assertNull(shortcutFor(ctrl = false, key = Key.F))
        assertNull(shortcutFor(ctrl = true, key = Key.G))
        assertNull(shortcutFor(ctrl = false, key = Key.G))
    }

    @Test
    fun splitsAOneTimeCodeInHalf() {
        assertEquals("123 456", formatTotpCode("123456"))
        assertEquals("1234 5678", formatTotpCode("12345678"))
        assertEquals("7", formatTotpCode("7"))
        assertEquals("", formatTotpCode(""))
    }
}
