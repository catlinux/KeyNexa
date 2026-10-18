package io.github.catlinux.keynexa.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.catlinux.keynexa.ui.R

private val ManropeWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

/**
 * Manrope as one variable font: each weight is the same file with its `wght` axis set. The variation API is still
 * marked experimental in Compose, but without it every weight would draw the font's default instance (ExtraLight).
 */
@OptIn(ExperimentalTextApi::class)
actual val Manrope: FontFamily =
    FontFamily(
        ManropeWeights.map { weight ->
            Font(
                resId = R.font.manrope,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )
