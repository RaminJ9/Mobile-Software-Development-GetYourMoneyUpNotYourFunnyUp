package com.example.getyourmoneyupnotyourfunnyup.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*

@Composable
fun PaidOutChart(
    data: HomeData,
    period: SpendPeriod,
    selectedGroupId: String?,
    onPeriod: (SpendPeriod) -> Unit,
    onSelect: (String?) -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val slices = remember(data, period) { spendSlices(data, period) }
    val groups = remember(data.groups) { data.groups.associateBy { it.group.id } }
    val selected = slices.firstOrNull { it.groupId == selectedGroupId }
    val popoverOffset = with(LocalDensity.current) { 150.dp.roundToPx() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Paid out", Modifier.weight(1f), style = AppTypography.CaptionStrong, color = AppColors.Ink600)
            Box {
                TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(period.shortLabel, style = AppTypography.CaptionStrong, color = AppColors.Ink900)
                    DashboardSymbol(DashboardIcon.Chevron, Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = AppColors.White) {
                    SpendPeriod.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label, style = AppTypography.CaptionStrong) },
                            onClick = { menuOpen = false; onPeriod(item) }
                        )
                    }
                }
            }
        }
        Box(Modifier.size(140.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize().semantics {
                    contentDescription = if (data.paidOutPayments == null) "Paid out data unavailable" else "Paid out ${formatDanishAmountFromCents(slices.sumOf { it.amountCents })} ${period.caption}"
                    customActions = slices.map { slice ->
                        CustomAccessibilityAction("${groups[slice.groupId]?.group?.name}: ${formatDanishAmountFromCents(slice.amountCents)}") { onSelect(slice.groupId); true }
                    }
                }.pointerInput(slices, selectedGroupId) {
                    detectTapGestures { position ->
                        val id = hitTestSlice(position.x / size.width, position.y / size.height, slices, selectedGroupId)
                        onSelect(if (id == selectedGroupId) null else id)
                    }
                }
            ) {
                if (slices.isEmpty()) {
                    drawCircle(AppColors.Surface, radius = size.width * 62f / 140f)
                    drawCircle(AppColors.White, radius = size.width * 40f / 140f)
                }
                slices.forEach { slice ->
                    val centre = size.width / 2f
                    val outer = size.width * (if (slice.groupId == selectedGroupId) 69f else 62f) / 140f
                    val inner = size.width * 40f / 140f
                    val gap = minOf(2f, slice.sweepDegrees / 3f)
                    val start = slice.startDegrees - 90f + gap / 2
                    val sweep = slice.sweepDegrees - gap
                    val path = Path().apply {
                        arcTo(Rect(centre - outer, centre - outer, centre + outer, centre + outer), start, sweep, true)
                        arcTo(Rect(centre - inner, centre - inner, centre + inner, centre + inner), start + sweep, -sweep, false)
                        close()
                    }
                    drawPath(
                        path,
                        AppColors.groupColorAt(groups.getValue(slice.groupId).colourIndex),
                        alpha = if (selected != null && slice.groupId != selectedGroupId) 0.45f else 1f
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                Text(
                    if (data.paidOutPayments == null) "—" else formatDanishAmountFromCents(
                        slices.sumOf { it.amountCents },
                        false
                    ),
                    style = AppTypography.ItemTitle,
                    color = AppColors.Ink900
                )
                Text(
                    if (data.paidOutPayments == null) "Unavailable" else "kr ${period.caption}",
                    style = AppTypography.Tag,
                    color = AppColors.Ink600
                )
            }
            if (selected != null) {
                val group = groups.getValue(selected.groupId)
                val pointerLeft = remember(selected) {
                    val middle = Math.toRadians((selected.startDegrees + selected.sweepDegrees / 2 - 90).toDouble())
                    (70 + 69 * kotlin.math.cos(middle) + 64 - 7).toFloat().coerceIn(18f, 172f)
                }
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, popoverOffset),
                    onDismissRequest = { onSelect(null) },
                    properties = PopupProperties(focusable = true)
                ) {
                    Box(Modifier.width(204.dp).padding(top = 7.dp)) {
                        Canvas(Modifier.offset(x = pointerLeft.dp, y = (-7).dp).size(14.dp, 7.dp)) {
                            drawPath(Path().apply {
                                moveTo(0f, size.height)
                                lineTo(size.width / 2, 0f)
                                lineTo(size.width, size.height)
                                close()
                            }, AppColors.White)
                        }
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = AppShapes.ListCard,
                        color = AppColors.White,
                        shadowElevation = 12.dp
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(AppColors.groupColorAt(group.colourIndex), AppShapes.Circle))
                                Spacer(Modifier.width(AppSpacing.Space8))
                                Text(group.group.name, Modifier.weight(1f), style = AppTypography.ItemTitle)
                                IconButton(
                                    onClick = { onSelect(null) },
                                    modifier = Modifier.size(AppTouchTargets.MinTouchTarget)
                                ) {
                                    DashboardSymbol(DashboardIcon.Close, description = "Close payment details")
                                }
                            }
                            Text("Paid ${period.caption}", style = AppTypography.Caption, color = AppColors.Ink600)
                            Text(formatDanishAmountFromCents(selected.amountCents), style = AppTypography.TopBarTitle)
                            Column(Modifier.fillMaxWidth().background(AppColors.SurfaceSoft, AppShapes.Chip).padding(10.dp)) {
                                Text("Last payment", style = AppTypography.Caption, color = AppColors.Ink600)
                                Text(
                                    "${formatDanishAmountFromCents(selected.lastPayment.settlement.amountCents)} to ${selected.lastPayment.creditorName} · ${relativeDate(selected.lastPayment.settlement.createdAt, data.nowMillis)}",
                                    style = AppTypography.CaptionStrong
                                )
                            }
                            DashboardButton(
                                "Open group →",
                                { onSelect(null); onOpenGroup(group.group.id) },
                                Modifier.fillMaxWidth()
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}
