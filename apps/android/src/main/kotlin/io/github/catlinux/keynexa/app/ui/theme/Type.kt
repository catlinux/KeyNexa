package io.github.catlinux.keynexa.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.catlinux.keynexa.app.R

private val ManropeWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

/**
 * Manrope as one variable font: each weight is the same file with its `wght` axis set. The variation API is still
 * marked experimental in Compose, but without it every weight would draw the font's default instance (ExtraLight).
 */
@OptIn(ExperimentalTextApi::class)
val Manrope: FontFamily =
    FontFamily(
        ManropeWeights.map { weight ->
            Font(
                resId = R.font.manrope,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

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
