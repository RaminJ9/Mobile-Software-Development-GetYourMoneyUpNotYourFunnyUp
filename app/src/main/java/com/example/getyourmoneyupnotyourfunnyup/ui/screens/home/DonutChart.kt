package com.example.getyourmoneyupnotyourfunnyup.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.avatarColor
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.PieSliceUi
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

// Geometry from the hi-fi (a 140 × 140 viewBox).
private const val VIEW = 140f
private const val CENTER = 70f
private const val OUTER = 62f
private const val OUTER_SELECTED = 69f
private const val INNER = 40f
private const val GAP = 0.035f
private const val FULL = (2 * PI).toFloat()

/** Start/end angle of each slice in radians, measured clockwise from 12 o'clock. */
private fun angles(slices: List<PieSliceUi>): List<Pair<Float, Float>> {
    val sum = slices.sumOf { it.valueCents }.toFloat()
    if (sum <= 0f) return emptyList()
    var acc = 0f
    return slices.map { slice ->
        val start = acc / sum * FULL + GAP
        acc += slice.valueCents
        val end = acc / sum * FULL - GAP
        start to maxOf(start, end)
    }
}

/**
 * "Paid out" donut. Each slice is a group in its group colour; tapping one selects it (the slice
 * grows and the others fade), tapping it again or the hole clears the selection.
 */
@Composable
fun DonutChart(
    slices: List<PieSliceUi>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (slices.isEmpty()) "Nothing paid out in this period"
    else "Paid out by group: " + slices.joinToString { MoneyFormat.kr(it.valueCents) + " kr" }
    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(slices, selectedId) {
                detectTapGestures { tap ->
                    val scale = size.width / VIEW
                    val dx = tap.x / scale - CENTER
                    val dy = tap.y / scale - CENTER
                    val distance = hypot(dx, dy)
                    if (distance < INNER || distance > OUTER_SELECTED + 4f) return@detectTapGestures
                    var angle = atan2(dx, -dy)
                    if (angle < 0) angle += FULL
                    val hit = angles(slices).indexOfFirst { (start, end) -> angle >= start - GAP && angle <= end + GAP }
                    if (hit >= 0) onSelect(slices[hit].groupId)
                }
            },
    ) {
        val scale = size.width / VIEW
        if (slices.isEmpty()) {
            val radius = (OUTER + INNER) / 2 * scale
            drawCircle(AppColors.Surface, radius, style = Stroke(width = (OUTER - INNER) * scale))
            return@Canvas
        }
        angles(slices).forEachIndexed { index, (start, end) ->
            val slice = slices[index]
            val isSelected = slice.groupId == selectedId
            val outer = (if (isSelected) OUTER_SELECTED else OUTER) * scale
            val inner = INNER * scale
            val c = CENTER * scale
            val startDeg = Math.toDegrees(start.toDouble()).toFloat() - 90f
            val sweepDeg = Math.toDegrees((end - start).toDouble()).toFloat()
            val path = Path().apply {
                arcTo(Rect(Offset(c, c), outer), startDeg, sweepDeg, forceMoveTo = true)
                arcTo(Rect(Offset(c, c), inner), startDeg + sweepDeg, -sweepDeg, forceMoveTo = false)
                close()
            }
            drawPath(
                path,
                color = avatarColor(slice.paletteIndex),
                alpha = if (selectedId != null && !isSelected) 0.45f else 1f,
            )
        }
    }
}

/** x (dp) of the speech-bubble tail so it points at the middle of the selected slice. */
fun bubbleTailX(slices: List<PieSliceUi>, selectedId: String): Float {
    val index = slices.indexOfFirst { it.groupId == selectedId }
    if (index < 0) return 95f
    val (start, end) = angles(slices)[index]
    val mid = (start + end) / 2
    val midX = CENTER + OUTER_SELECTED * sin(mid)
    // The bubble starts 64 dp left of the chart; the tail is 14 dp wide.
    return (midX + 64f - 7f).coerceIn(18f, 204f - 32f)
}
