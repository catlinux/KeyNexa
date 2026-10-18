package io.github.catlinux.keynexa.app.ui.theme

import io.github.catlinux.keynexa.app.settings.ThemeMode

/**
 * Whether the app paints dark: the chosen mode, or what the device says when the mode follows the system.
 */
fun resolveDarkTheme(
    mode: ThemeMode,
    systemDark: Boolean,
): Boolean =
    when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
