package io.github.catlinux.keynexa.app.ui.theme

/**
 * The colour tokens of the design system (docs/DESIGN.md) as plain ARGB values, with no Compose types, so a unit
 * test can check their contrast. Every text pair reaches WCAG AA (4.5:1) and every interface edge 3:1.
 */
data class Palette(
    val background: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val onBackground: Long,
    val onSurface: Long,
    val onSurfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    val outline: Long,
)

val LightPalette =
    Palette(
        background = 0xFFF7F8FC,
        surface = 0xFFFFFFFF,
        surfaceVariant = 0xFFE6E9F2,
        onBackground = 0xFF171B26,
        onSurface = 0xFF171B26,
        onSurfaceVariant = 0xFF434A5C,
        primary = 0xFF2F4BC0,
        onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFFDCE2FF,
        onPrimaryContainer = 0xFF0A1A63,
        secondary = 0xFF0F6E6E,
        onSecondary = 0xFFFFFFFF,
        secondaryContainer = 0xFFC5EFEF,
        onSecondaryContainer = 0xFF002E2E,
        error = 0xFFB3261E,
        onError = 0xFFFFFFFF,
        errorContainer = 0xFFFBDAD7,
        onErrorContainer = 0xFF410002,
        outline = 0xFF6B7285,
    )

val DarkPalette =
    Palette(
        background = 0xFF0F121A,
        surface = 0xFF161A24,
        surfaceVariant = 0xFF2A3040,
        onBackground = 0xFFE6E9F2,
        onSurface = 0xFFE6E9F2,
        onSurfaceVariant = 0xFFBCC3D6,
        primary = 0xFFA9B8FF,
        onPrimary = 0xFF0A1A63,
        primaryContainer = 0xFF25379A,
        onPrimaryContainer = 0xFFDCE2FF,
        secondary = 0xFF7FD6D6,
        onSecondary = 0xFF003838,
        secondaryContainer = 0xFF0B5555,
        onSecondaryContainer = 0xFFC5EFEF,
        error = 0xFFF2B8B5,
        onError = 0xFF601410,
        errorContainer = 0xFF8C1D18,
        onErrorContainer = 0xFFFBDAD7,
        outline = 0xFF8A91A6,
    )
