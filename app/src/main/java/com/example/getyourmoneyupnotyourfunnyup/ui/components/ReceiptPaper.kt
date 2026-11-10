package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.JetBrainsMonoFontFamily

@Immutable
data class ReceiptLine(val name: String, val price: String)

private val receiptText = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, lineHeight = 16.sp, color = AppExtraColors.ReceiptInk)

/**
 * The printed-receipt illustration shown in the camera viewfinder and the receipt viewer. When
 * [scanning] is true a green scan line sweeps over it.
 */
@Composable
fun ReceiptPaper(
    merchant: String,
    subtitle: String,
    lines: List<ReceiptLine>,
    total: String,
    modifier: Modifier = Modifier,
    scanning: Boolean = false,
    tilted: Boolean = true,
) {
    Box(
        modifier = modifier
            .width(236.dp)
            .rotate(if (tilted) -2f else 0f)
            .shadow(24.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.ReceiptPaper),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                merchant.uppercase(),
                modifier = Modifier.fillMaxWidth(),
                style = receiptText.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.em),
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                style = receiptText.copy(color = AppExtraColors.ReceiptMuted),
                textAlign = TextAlign.Center,
            )
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.name, Modifier.weight(1f), style = receiptText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line.price, style = receiptText)
                }
            }
            Canvas(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).height(1.dp)) {
                drawLine(
                    color = AppExtraColors.ReceiptRule,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                )
            }
            Row {
                Text("TOTAL", Modifier.weight(1f), style = receiptText.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
                Text(total, style = receiptText.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
            Text(
                "Tak for besøget",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                style = receiptText.copy(color = AppExtraColors.ReceiptMuted),
                textAlign = TextAlign.Center,
            )
        }
        if (scanning) ScanLine(Modifier.matchParentSize())
    }
}

@Composable
private fun ScanLine(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "scanline")
    val progress by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanlineProgress",
    )
    BoxWithConstraints(modifier) {
        val top = maxHeight * progress
        Box(
            Modifier
                .offset(y = top)
                .fillMaxWidth()
                .height(3.dp)
                .shadow(12.dp, ambientColor = AppColors.Green500, spotColor = AppColors.Green500)
                .background(AppColors.Green500),
        )
        Box(
            Modifier
                .offset(y = top - 12.dp)
                .fillMaxWidth()
                .height(27.dp)
                .background(Color(0x223FA36B)),
        )
    }
}

