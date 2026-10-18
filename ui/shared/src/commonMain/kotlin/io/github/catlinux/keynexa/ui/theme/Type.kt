package io.github.catlinux.keynexa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Manrope, one variable file per weight; each target loads the font in its own way. */
expect val Manrope: FontFamily

private fun TextStyle.inManrope(): TextStyle = copy(fontFamily = Manrope)

/** The Material 3 type scale set in Manrope; sizes and line heights stay the Material defaults. */
val KeyNexaTypography: Typography =
    Typography().let { base ->
        Typography(
            displayLarge = base.displayLarge.inManrope(),
            displayMedium = base.displayMedium.inManrope(),
            displaySmall = base.displaySmall.inManrope(),
            headlineLarge = base.headlineLarge.inManrope().copy(fontWeight = FontWeight.ExtraBold),
            headlineMedium = base.headlineMedium.inManrope().copy(fontWeight = FontWeight.ExtraBold),
            headlineSmall = base.headlineSmall.inManrope().copy(fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.inManrope().copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.inManrope().copy(fontWeight = FontWeight.Bold),
            titleSmall = base.titleSmall.inManrope().copy(fontWeight = FontWeight.Bold),
            bodyLarge = base.bodyLarge.inManrope(),
            bodyMedium = base.bodyMedium.inManrope(),
            bodySmall = base.bodySmall.inManrope(),
            labelLarge = base.labelLarge.inManrope().copy(fontWeight = FontWeight.Bold),
            labelMedium = base.labelMedium.inManrope().copy(fontWeight = FontWeight.SemiBold),
            labelSmall = base.labelSmall.inManrope().copy(fontWeight = FontWeight.SemiBold),
        )
    }
