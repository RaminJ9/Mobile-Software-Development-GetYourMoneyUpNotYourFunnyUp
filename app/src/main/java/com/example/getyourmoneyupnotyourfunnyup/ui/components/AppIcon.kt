package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An outline icon drawn on a 24×24 grid, using the exact SVG path data from the hi-fi design
 * ("outline, 1.8 stroke, rounded caps").
 *
 * @property paths SVG path strings.
 * @property filled true for icons that are drawn as solid shapes (e.g. the "more" dots).
 */
@Immutable
class AppIconSpec(val paths: List<String>, val filled: Boolean = false)

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

/** Icon set used by the app. Path data is copied from `docs/HiFi.html`. */
object AppIcons {
    val Bell = AppIconSpec(listOf("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0"))
    val CloudOff = AppIconSpec(
        listOf(
            "m2 2 20 20",
            "M5.8 5.8A7 7 0 0 0 7 19h10a4.5 4.5 0 0 0 2.6-.8",
            "M21.5 15.5A4.5 4.5 0 0 0 17.5 10h-.5a7 7 0 0 0-6.3-4",
        ),
    )
    val ChevronDown = AppIconSpec(listOf("m6 9 6 6 6-6"))
    val ChevronRight = AppIconSpec(listOf("m9 6 6 6-6 6"))
    val Close = AppIconSpec(listOf("M18 6 6 18M6 6l12 12"))
    val ArrowRight = AppIconSpec(listOf("M5 12h14M13 6l6 6-6 6"))
    val ArrowLeft = AppIconSpec(listOf("M19 12H5M11 18l-6-6 6-6"))
    val Check = AppIconSpec(listOf("M20 6 9 17l-5-5"))
    val Sync = AppIconSpec(
        listOf(
            "M21 12a9 9 0 0 1-15.5 6.2L3 16",
            "M3 12a9 9 0 0 1 15.5-6.2L21 8",
            "M21 3v5h-5M3 21v-5h5",
        ),
    )
    val Star = AppIconSpec(listOf("m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"))
    val Home = AppIconSpec(listOf("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"))
    val Users = AppIconSpec(
        listOf(
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            circle(9f, 7f, 4f),
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M16 3.13a4 4 0 0 1 0 7.75",
        ),
    )
    val Scan = AppIconSpec(
        listOf(
            "M3 7V5a2 2 0 0 1 2-2h2",
            "M17 3h2a2 2 0 0 1 2 2v2",
            "M21 17v2a2 2 0 0 1-2 2h-2",
            "M7 21H5a2 2 0 0 1-2-2v-2",
            "M7 9h10M7 12.5h10M7 16h6",
        ),
    )
    val Clock = AppIconSpec(listOf(circle(12f, 12f, 9f), "M12 7.5V12l3 2"))
    val User = AppIconSpec(listOf(circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0"))
    val More = AppIconSpec(
        listOf(circle(5f, 12f, 1.8f), circle(12f, 12f, 1.8f), circle(19f, 12f, 1.8f)),
        filled = true,
    )
    val Swap = AppIconSpec(listOf("M7 7h11l-3-3M17 17H6l3 3"))
    val Receipt = AppIconSpec(listOf("M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2z", "M9 8h6M9 12h6"))
    val Card = AppIconSpec(listOf(roundRect(3f, 6f, 18f, 13f, 3f), "M3 10h18M7 15h3"))
    val AlertCircle = AppIconSpec(listOf(circle(12f, 12f, 9f), "M12 8v4M12 16h.01"))
    val AlertMark = AppIconSpec(listOf("M12 8v4M12 16h.01"))
    val Plus = AppIconSpec(listOf("M12 5v14M5 12h14"))
    val Trash = AppIconSpec(listOf("M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"))
    val Camera = AppIconSpec(
        listOf(
            "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z",
            circle(12f, 13f, 3.5f),
        ),
    )
    val Flash = AppIconSpec(listOf("M13 2 4 14h7l-1 8 9-12h-7z"))
    val Image = AppIconSpec(listOf(roundRect(3f, 3f, 18f, 18f, 3f), circle(9f, 9f, 2f), "m21 15-5-5L5 21"))
    val Pencil = AppIconSpec(listOf("M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"))
    val Warning = AppIconSpec(
        listOf(
            "M12 9v4M12 17h.01",
            "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
        ),
    )
    val Phone = AppIconSpec(listOf(roundRect(5f, 2f, 14f, 20f, 3f), "M11 18h2"))
    val Eye = AppIconSpec(listOf("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f)))
    val Search = AppIconSpec(listOf(circle(11f, 11f, 7f), "m20 20-3.5-3.5"))
    val Glass = AppIconSpec(listOf("M8 22h8M12 15v7M6 3h12l-1 7a5 5 0 0 1-10 0z"))
    val Meat = AppIconSpec(
        listOf(
            "M15.5 4.5a6 6 0 0 1 0 8.5l-5 5a3 3 0 1 1-4.2-4.2l5-5a6 6 0 0 1 4.2-4.3z",
            circle(15f, 9f, 1.5f),
        ),
    )
    val Logout = AppIconSpec(listOf("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"))
}

/**
 * Draws an [AppIconSpec].
 *
 * @param fill optional fill painted under the stroke (used for the active bottom-nav icon and stars).
 * @param contentDescription null for decorative icons (the usual case: buttons carry the label).
 */
@Composable
fun AppIcon(
    icon: AppIconSpec,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Float = 1.8f,
    tint: Color = Color.Unspecified,
    fill: Color? = null,
    contentDescription: String? = null,
) {
    val paths: List<Path> = remember(icon) {
        icon.paths.map { PathParser().parsePathString(it).toPath() }
    }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Canvas(modifier = modifier.then(semanticsModifier).size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor, pivot = Offset.Zero) {
            paths.forEach { path ->
                if (icon.filled) {
                    drawPath(path, color = color, style = Fill)
                } else {
                    if (fill != null) drawPath(path, color = fill, style = Fill)
                    drawPath(
                        path,
                        color = color,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}
