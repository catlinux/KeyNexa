package io.github.catlinux.keynexa.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Guards the accessibility of the palette: a colour edit that drops below WCAG AA fails the build. */
class PaletteContrastTest {
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and CHANNEL_MASK) / MAX_CHANNEL
            return if (value <= LINEAR_CUTOFF) value / LINEAR_DIVISOR else ((value + OFFSET) / SCALE).pow(GAMMA)
        }
        return RED_WEIGHT * channel(RED_SHIFT) + GREEN_WEIGHT * channel(GREEN_SHIFT) + BLUE_WEIGHT * channel(0)
    }

    private fun contrast(
        first: Long,
        second: Long,
    ): Double {
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + FLARE) / (darker + FLARE)
    }

    private fun textPairs(palette: Palette) =
        mapOf(
            "onBackground/background" to (palette.onBackground to palette.background),
            "onSurface/surface" to (palette.onSurface to palette.surface),
            "onSurfaceVariant/surface" to (palette.onSurfaceVariant to palette.surface),
            "onSurfaceVariant/surfaceVariant" to (palette.onSurfaceVariant to palette.surfaceVariant),
            "onPrimary/primary" to (palette.onPrimary to palette.primary),
            "onPrimaryContainer/primaryContainer" to (palette.onPrimaryContainer to palette.primaryContainer),
            "onSecondary/secondary" to (palette.onSecondary to palette.secondary),
            "onSecondaryContainer/secondaryContainer" to (palette.onSecondaryContainer to palette.secondaryContainer),
            "onError/error" to (palette.onError to palette.error),
            "onErrorContainer/errorContainer" to (palette.onErrorContainer to palette.errorContainer),
            "primary/background" to (palette.primary to palette.background),
            "primary/surface" to (palette.primary to palette.surface),
            "secondary/surface" to (palette.secondary to palette.surface),
            "error/surface" to (palette.error to palette.surface),
        )

    private fun edgePairs(palette: Palette) =
        mapOf(
            "outline/background" to (palette.outline to palette.background),
            "outline/surface" to (palette.outline to palette.surface),
            "primary/surface (focus ring)" to (palette.primary to palette.surface),
        )

    private fun check(
        name: String,
        palette: Palette,
    ) {
        for ((pair, colors) in textPairs(palette)) {
            val ratio = contrast(colors.first, colors.second)
            assertTrue("$name text $pair is $ratio, below $TEXT_MINIMUM", ratio >= TEXT_MINIMUM)
        }
        for ((pair, colors) in edgePairs(palette)) {
            val ratio = contrast(colors.first, colors.second)
            assertTrue("$name edge $pair is $ratio, below $EDGE_MINIMUM", ratio >= EDGE_MINIMUM)
        }
    }

    @Test
    fun lightPaletteMeetsWcagAa() = check("light", LightPalette)

    @Test
    fun darkPaletteMeetsWcagAa() = check("dark", DarkPalette)

    private companion object {
        const val TEXT_MINIMUM = 4.5
        const val EDGE_MINIMUM = 3.0
        const val CHANNEL_MASK = 0xFFL
        const val MAX_CHANNEL = 255.0
        const val LINEAR_CUTOFF = 0.03928
        const val LINEAR_DIVISOR = 12.92
        const val OFFSET = 0.055
        const val SCALE = 1.055
        const val GAMMA = 2.4
        const val RED_WEIGHT = 0.2126
        const val GREEN_WEIGHT = 0.7152
        const val BLUE_WEIGHT = 0.0722
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val FLARE = 0.05
    }
}
