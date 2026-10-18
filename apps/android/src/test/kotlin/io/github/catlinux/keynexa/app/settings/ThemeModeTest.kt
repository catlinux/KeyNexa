package io.github.catlinux.keynexa.app.settings

import io.github.catlinux.keynexa.app.ui.theme.resolveDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** The theme choice: how it becomes light or dark, and how a stored value is read back. */
class ThemeModeTest {
    @Test
    fun automaticFollowsTheSystem() {
        assertEquals(true, resolveDarkTheme(ThemeMode.System, systemDark = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.System, systemDark = false))
    }

    @Test
    fun lightIsAlwaysLight() {
        assertEquals(false, resolveDarkTheme(ThemeMode.Light, systemDark = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.Light, systemDark = false))
    }

    @Test
    fun darkIsAlwaysDark() {
        assertEquals(true, resolveDarkTheme(ThemeMode.Dark, systemDark = true))
        assertEquals(true, resolveDarkTheme(ThemeMode.Dark, systemDark = false))
    }

    @Test
    fun aStoredValueIsReadByName() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.Light, ThemeMode.fromStored("Light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStored("Dark"))
    }

    @Test
    fun anUnknownStoredValueFallsBackToAutomatic() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored("Solarized"))
        assertEquals(ThemeMode.System, ThemeMode.fromStored(""))
    }
}
