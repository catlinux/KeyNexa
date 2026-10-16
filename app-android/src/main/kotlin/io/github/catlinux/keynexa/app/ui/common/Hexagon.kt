@file:Suppress("MagicNumber")

package io.github.catlinux.keynexa.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

// The vertices of a point-up hexagon centred in its box, as fractions of the box (the avatars of the mock-ups).
private val HEXAGON_X = floatArrayOf(0.5f, 0.9f, 0.9f, 0.5f, 0.1f, 0.1f)
private val HEXAGON_Y = floatArrayOf(0.05f, 0.275f, 0.725f, 0.95f, 0.725f, 0.275f)

/** The six vertices of the hexagon inside a square of [size], as (x, y) pairs. Pure and unit-tested. */
internal fun hexagonVertices(size: Float): List<Pair<Float, Float>> =
    HEXAGON_X.indices.map { index -> HEXAGON_X[index] * size to HEXAGON_Y[index] * size }

/** A point-up hexagon: the shape of the brand mark and of the avatars. */
class HexagonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path =
            Path().apply {
                moveTo(HEXAGON_X.first() * size.width, HEXAGON_Y.first() * size.height)
                for (index in 1 until HEXAGON_X.size) {
                    lineTo(HEXAGON_X[index] * size.width, HEXAGON_Y[index] * size.height)
                }
                close()
            }
        return Outline.Generic(path)
    }
}

/**
 * Initials over a hexagon: an avatar. It is decorative ([clearAndSetSemantics] leaves it out of the accessibility
 * tree), because the row's own text already names the vault or the entry.
 */
@Composable
fun HexBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(containerColor, HexagonShape())
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
