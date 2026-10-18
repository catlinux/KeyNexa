package io.github.catlinux.keynexa.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private const val FONT_RESOURCE = "font/manrope.ttf"

private val ManropeWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

/** Manrope as one variable font, read from the classpath; each weight sets its `wght` axis where the API allows it. */
@OptIn(ExperimentalTextApi::class)
actual val Manrope: FontFamily =
    FontFamily(
        ManropeWeights.map { weight ->
            Font(
                resource = FONT_RESOURCE,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )
