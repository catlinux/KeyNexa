@file:Suppress("MagicNumber")

package io.github.catlinux.keynexa.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.catlinux.keynexa.ui.text.SharedText
import io.github.catlinux.keynexa.ui.text.sharedText
import io.github.catlinux.keynexa.ui.theme.LocalDarkTheme

private const val VIEWBOX = 100f

/**
 * The KeyNexa brand: a hexagon split in two blues with an inner ring, a shield with a keyhole and a small node at
 * the top right, drawn from the design mock-ups with the theme's own colours (never copied from another app).
 */
@Suppress("LongMethod")
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val strong = colors.primary
    val soft = if (dark) colors.primaryContainer else colors.secondaryContainer
    val cut = colors.background
    val description = sharedText(SharedText.BrandMarkDescription)
    Canvas(modifier = modifier.size(size).semantics { contentDescription = description }) {
        val width = this.size.width
        val height = this.size.height

        fun x(fraction: Float) = fraction / VIEWBOX * width

        fun y(fraction: Float) = fraction / VIEWBOX * height

        drawPath(
            Path().apply {
                moveTo(x(46f), y(16f))
                lineTo(x(78.9f), y(35f))
                lineTo(x(78.9f), y(73f))
                lineTo(x(46f), y(92f))
                lineTo(x(13.1f), y(73f))
                lineTo(x(13.1f), y(35f))
                close()
            },
            soft,
        )
        drawPath(
            Path().apply {
                moveTo(x(46f), y(16f))
                lineTo(x(46f), y(92f))
                lineTo(x(13.1f), y(73f))
                lineTo(x(13.1f), y(35f))
                close()
            },
            strong,
        )
        drawPath(
            Path().apply {
                moveTo(x(46f), y(30f))
                lineTo(x(66.8f), y(42f))
                lineTo(x(66.8f), y(66f))
                lineTo(x(46f), y(78f))
                lineTo(x(25.2f), y(66f))
                lineTo(x(25.2f), y(42f))
                close()
            },
            cut,
            style = Stroke(width = 3f / VIEWBOX * width),
        )
        drawPath(
            Path().apply {
                moveTo(x(46f), y(40f))
                lineTo(x(58f), y(45f))
                lineTo(x(58f), y(54f))
                cubicTo(x(58f), y(62f), x(53f), y(67f), x(46f), y(70f))
                cubicTo(x(39f), y(67f), x(34f), y(62f), x(34f), y(54f))
                lineTo(x(34f), y(45f))
                close()
            },
            cut,
        )
        drawCircle(strong, radius = 3.5f / VIEWBOX * width, center = Offset(x(46f), y(52f)))
        drawRoundRect(
            color = strong,
            topLeft = Offset(x(44.5f), y(54f)),
            size = Size(3f / VIEWBOX * width, 7f / VIEWBOX * height),
            cornerRadius = CornerRadius(1.2f / VIEWBOX * width),
        )
        drawPath(
            Path().apply {
                moveTo(x(84f), y(7f))
                lineTo(x(91.8f), y(11.5f))
                lineTo(x(91.8f), y(20.5f))
                lineTo(x(84f), y(25f))
                lineTo(x(76.2f), y(20.5f))
                lineTo(x(76.2f), y(11.5f))
                close()
            },
            strong,
        )
    }
}
