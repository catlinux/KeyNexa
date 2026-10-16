package io.github.catlinux.keynexa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.catlinux.keynexa.app.settings.ThemeMode

/**
 * Whether the app is painting the dark theme. Screens use it to pick a container colour (for example the primary
 * band in light, the primary container in dark) without asking the system again.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

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

private fun Long.toColor() = Color(this)

private fun Palette.toLightScheme(): ColorScheme =
    lightColorScheme(
        primary = primary.toColor(),
        onPrimary = onPrimary.toColor(),
        primaryContainer = primaryContainer.toColor(),
        onPrimaryContainer = onPrimaryContainer.toColor(),
        secondary = secondary.toColor(),
        onSecondary = onSecondary.toColor(),
        secondaryContainer = secondaryContainer.toColor(),
        onSecondaryContainer = onSecondaryContainer.toColor(),
        background = background.toColor(),
        onBackground = onBackground.toColor(),
        surface = surface.toColor(),
        onSurface = onSurface.toColor(),
        surfaceVariant = surfaceVariant.toColor(),
        onSurfaceVariant = onSurfaceVariant.toColor(),
        error = error.toColor(),
        onError = onError.toColor(),
        errorContainer = errorContainer.toColor(),
        onErrorContainer = onErrorContainer.toColor(),
        outline = outline.toColor(),
    )

private fun Palette.toDarkScheme(): ColorScheme =
    darkColorScheme(
        primary = primary.toColor(),
        onPrimary = onPrimary.toColor(),
        primaryContainer = primaryContainer.toColor(),
        onPrimaryContainer = onPrimaryContainer.toColor(),
        secondary = secondary.toColor(),
        onSecondary = onSecondary.toColor(),
        secondaryContainer = secondaryContainer.toColor(),
        onSecondaryContainer = onSecondaryContainer.toColor(),
        background = background.toColor(),
        onBackground = onBackground.toColor(),
        surface = surface.toColor(),
        onSurface = onSurface.toColor(),
        surfaceVariant = surfaceVariant.toColor(),
        onSurfaceVariant = onSurfaceVariant.toColor(),
        error = error.toColor(),
        onError = onError.toColor(),
        errorContainer = errorContainer.toColor(),
        onErrorContainer = onErrorContainer.toColor(),
        outline = outline.toColor(),
    )

/**
 * KeyNexa's Material 3 theme. The colours are the fixed brand palette, not the wallpaper-derived dynamic colours:
 * a password manager must look the same, and keep its checked contrast, on every device.
 */
@Composable
fun KeyNexaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkPalette.toDarkScheme() else LightPalette.toLightScheme()
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colors, typography = KeyNexaTypography, content = content)
    }
}
