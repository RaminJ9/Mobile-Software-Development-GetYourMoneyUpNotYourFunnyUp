package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*

enum class DashboardIcon(val path: String) {
    Bell("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9 M10.3 21a1.94 1.94 0 0 0 3.4 0"),
    Check("M20 6 9 17l-5-5"),
    Close("M18 6 6 18 M6 6l12 12"),
    Chevron("m6 9 6 6 6-6"),
    Arrow("M5 12h14 M13 6l6 6-6 6"),
    Retry("M21 12a9 9 0 0 1-15.5 6.2L3 16 M3 12a9 9 0 0 1 15.5-6.2L21 8 M21 3v5h-5 M3 21v-5h5"),
    Plus("M12 5v14 M5 12h14"),
    Edit("M12 20h9 M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"),
    Warning("M12 8v4 M12 16h.01"),
    Offline("m2 2 20 20 M5.8 5.8A7 7 0 0 0 7 19h10a4.5 4.5 0 0 0 2.6-.8 M21.5 15.5A4.5 4.5 0 0 0 17.5 10h-.5a7 7 0 0 0-6.3-4"),
    Star("m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"),
    StarOutline("m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"),
    Search("M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14 M20 20l-3.5-3.5"),
    Back("m15 18-6-6 6-6"),
    Info("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18 M12 8v4 M12 16h.01"),
    UserPlus("M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M8.5 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8 M19 8v6 M22 11h-6")
}

@Composable
fun DashboardSymbol(
    icon: DashboardIcon,
    modifier: Modifier = Modifier,
    colour: Color = AppColors.Ink900,
    description: String? = null
) {
    val vector = remember(icon, colour) {
        ImageVector.Builder(icon.name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(icon.path),
            fill = if (icon == DashboardIcon.Star) SolidColor(colour) else null,
            stroke = SolidColor(colour),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ).build()
    }
    Icon(vector, contentDescription = description, modifier = modifier.size(24.dp), tint = colour)
}

@Composable
fun DashboardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = AppColors.Green100,
    content: Color = AppColors.Green700,
    enabled: Boolean = true,
    outlined: Boolean = false,
    borderColour: Color = AppColors.Line
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppTouchTargets.InlineButtonHeight),
        enabled = enabled,
        shape = AppShapes.InlineButton,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        border = if (outlined) BorderStroke(1.dp, borderColour) else null,
        contentPadding = PaddingValues(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8)
    ) {
        Text(text, style = AppTypography.ButtonInline)
    }
}

@Composable
fun DashboardAvatar(
    name: String,
    colour: Color,
    modifier: Modifier = Modifier,
    round: Boolean = false
) {
    Box(
        modifier.size(AppTouchTargets.MinTouchTarget).background(colour, if (round) CircleShape else AppShapes.InlineButton),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), style = AppTypography.Section, color = AppColors.Ink900)
    }
}

@Composable
fun OfflineBanner(savedAtLabel: String?, modifier: Modifier = Modifier) {
    Surface(modifier, shape = AppShapes.SecondaryButton, color = AppColors.Surface) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardSymbol(DashboardIcon.Offline, colour = AppColors.Ink700, modifier = Modifier.size(18.dp))
            Text(
                if (savedAtLabel == null) "You're offline · showing available data."
                else "You're offline · showing saved data from $savedAtLabel.",
                style = AppTypography.CaptionStrong,
                color = AppColors.Ink700
            )
        }
    }
}

@Composable
fun SyncStatusStrip(status: SyncStatus, modifier: Modifier = Modifier, automaticRetryAvailable: Boolean = false) {
    val failed = status == SyncStatus.FAILED
    val synced = status == SyncStatus.SYNCED
    Surface(
        modifier,
        color = if (synced) AppColors.Green100 else if (failed) AppColors.Red50 else AppColors.Amber100,
        shape = AppShapes.Chip
    ) {
        Text(
            if (synced) "Synced" else if (failed && automaticRetryAvailable) "Couldn't sync yet · retrying automatically"
            else if (failed) "Couldn't sync · needs attention" else "Saved on your phone · waiting to sync",
            Modifier.padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8),
            style = AppTypography.Label,
            color = if (synced) AppColors.Green700 else if (failed) AppColors.Red800 else AppColors.Amber800
        )
    }
}

@Composable
fun InlinePaymentActions(
    state: InlineActionState,
    amount: String,
    creditor: String,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    soft: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        when (state) {
            InlineActionState.Confirming -> Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                DashboardButton("Cancel", onCancel, Modifier.weight(1f), AppColors.White, AppColors.Ink900, outlined = true)
                DashboardButton("Confirm", onConfirm, Modifier.weight(1f), AppColors.Red700, AppColors.White)
            }
            InlineActionState.Working -> ActionStatus("Paying…", working = true)
            is InlineActionState.Done -> {
                ActionStatus(when (state.syncStatus) {
                    SyncStatus.SYNCED -> "Paid · settled with $creditor"
                    SyncStatus.FAILED -> "Settlement recorded · sync needs attention"
                    null -> "Settlement recorded"
                    else -> "Settlement recorded · synchronization pending"
                })
                state.syncStatus?.takeIf { it != SyncStatus.SYNCED }?.let { SyncStatusStrip(it) }
            }
            else -> {
                if (state is InlineActionState.Failed) {
                    Text(state.message, style = AppTypography.Caption, color = AppColors.Amber800)
                }
                DashboardButton(
                    "Pay $amount", onAsk, Modifier.fillMaxWidth(),
                    if (soft) AppColors.Red100 else AppColors.Red700,
                    if (soft) AppColors.Red700 else AppColors.White, enabled
                )
                if (!enabled) Text("Settlement service unavailable", style = AppTypography.Caption, color = AppColors.Ink600)
            }
        }
    }
}

@Composable
fun ActionStatus(text: String, modifier: Modifier = Modifier, working: Boolean = false) {
    Row(
        modifier.fillMaxWidth().heightIn(min = AppTouchTargets.InlineButtonHeight)
        .background(if (working) AppColors.SurfaceSoft else AppColors.Green100, AppShapes.InlineButton)
        .padding(AppSpacing.Space8),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (working) CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.Ink600, strokeWidth = 2.dp)
        else DashboardSymbol(DashboardIcon.Check, Modifier.size(18.dp), colour = AppColors.Green700)
        Text(text, style = AppTypography.ButtonInline, color = if (working) AppColors.Ink600 else AppColors.Green700)
    }
}

@Composable
fun BalanceChip(balanceCents: Long?, modifier: Modifier = Modifier) {
    val colour = when {
        balanceCents == null -> AppColors.Ink600
        balanceCents < 0 -> AppColors.Red700
        balanceCents > 0 -> AppColors.Green700
        else -> AppColors.Ink600
    }
    val fill = when {
        balanceCents == null -> AppColors.Surface
        balanceCents < 0 -> AppColors.Red100
        balanceCents > 0 -> AppColors.Green100
        else -> AppColors.Surface
    }
    val text = when {
        balanceCents == null -> "Balance unavailable"
        balanceCents < 0 -> "You owe ${formatDanishAmountFromCents(-balanceCents, false)}"
        balanceCents > 0 -> "${formatDanishAmountFromCents(balanceCents, false, true)} to you"
        else -> "Settled"
    }
    Text(
        text,
        modifier.background(fill, AppShapes.Chip).padding(horizontal = 10.dp, vertical = 6.dp),
        style = AppTypography.Label,
        color = colour,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
