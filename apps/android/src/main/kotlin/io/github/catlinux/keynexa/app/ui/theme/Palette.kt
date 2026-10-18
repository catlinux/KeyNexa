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
        primary = 0xFF0A3D91,
        onPrimary = 0xFFFFFFFF,
        primaryContainer = 0xFFD6E3FF,
        onPrimaryContainer = 0xFF001A43,
        secondary = 0xFF00658F,
        onSecondary = 0xFFFFFFFF,
        secondaryContainer = 0xFFC7E7FF,
        onSecondaryContainer = 0xFF001E2E,
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
        primary = 0xFFAAC7FF,
        onPrimary = 0xFF002F68,
        primaryContainer = 0xFF0A3D91,
        onPrimaryContainer = 0xFFD6E3FF,
        secondary = 0xFF84CFFF,
        onSecondary = 0xFF00344C,
        secondaryContainer = 0xFF004C6C,
        onSecondaryContainer = 0xFFC7E7FF,
        error = 0xFFF2B8B5,
        onError = 0xFF601410,
        errorContainer = 0xFF8C1D18,
        onErrorContainer = 0xFFFBDAD7,
        outline = 0xFF8A91A6,
    )
