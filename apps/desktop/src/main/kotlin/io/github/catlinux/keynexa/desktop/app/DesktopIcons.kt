@file:Suppress("MagicNumber")

package io.github.catlinux.keynexa.desktop.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The 24 dp grid the icons are drawn on, and the line they are drawn with. */
private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 2f

/** `Icon` tints the paths, so the stroke they are built with only has to be opaque. */
private val STROKE = SolidColor(Color.Black)

/** Two overlapping sheets: the same action as `entry_detail_copy_field`. */
internal val CopyIcon: ImageVector by lazy {
    icon(
        name = "Copy",
        paths =
            listOf(
                "M9 9h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z",
                "M5 15V6a2 2 0 0 1 2-2h8",
            ),
    )
}

/** An open eye: the action that shows a secret. */
internal val EyeIcon: ImageVector by lazy {
    icon(
        name = "Eye",
        paths =
            listOf(
                "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z",
                "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
            ),
    )
}

/** The eye with a slash: the action that hides a secret again. */
internal val EyeOffIcon: ImageVector by lazy {
    icon(
        name = "EyeOff",
        paths =
            listOf(
                "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z",
                "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
                "M3 3l18 18",
            ),
    )
}

/** An arrow leaving a frame: the action that opens an address in the browser. */
internal val OpenInNewIcon: ImageVector by lazy {
    icon(
        name = "OpenInNew",
        paths =
            listOf(
                "M14 4h6v6",
                "M20 4 10 14",
                "M19 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5",
            ),
    )
}

/** A bin: the action that moves the entry to the recycle bin. */
internal val TrashIcon: ImageVector by lazy {
    icon(
        name = "Trash",
        paths =
            listOf(
                "M4 7h16",
                "M10 11v6M14 11v6",
                "M6 7l1 13h10l1-13",
                "M9 7V4h6v3",
            ),
    )
}

/** One icon of the panel, on the 24 dp grid, drawn with round strokes and no fill. */
internal fun icon(
    name: String,
    paths: List<String>,
): ImageVector {
    val builder =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        )
    paths.forEach { path ->
        builder.addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = null,
            stroke = STROKE,
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
