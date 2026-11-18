package com.example.getyourmoneyupnotyourfunnyup.ui.screens.balances

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.ui.components.DashboardIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.DashboardSymbol
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SyncFeedback
import com.example.getyourmoneyupnotyourfunnyup.ui.components.rememberConnectionState
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncPresentation
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import kotlin.math.absoluteValue

/** Ready for the future group hub. Parent navigation owns system insets and ViewModel lifetime. */
@Composable
fun BalanceRoute(viewModel: BalanceViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection = rememberConnectionState()
    BalanceScreen(state, onBack, viewModel::retry, modifier, connection)
}

@Composable
fun BalanceScreen(
    state: BalanceUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    connection: ConnectionState = ConnectionState.Unknown
) {
    Column(modifier.fillMaxSize().background(AppColors.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.Space8), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(AppTouchTargets.MinTouchTarget)) {
                DashboardSymbol(DashboardIcon.Arrow, Modifier.rotate(180f), description = "Back")
            }
            Text("Balances", style = AppTypography.TopBarTitle, color = AppColors.Ink900)
        }
        when (state) {
            BalanceUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Green700, modifier = Modifier.semantics { contentDescription = "Loading balances" })
            }
            is BalanceUiState.Error -> Column(
                Modifier.padding(AppSpacing.Space16), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space16)
            ) {
                Text(state.message, style = AppTypography.Body, color = AppColors.Ink600)
                if (state.canRetry) Button(
                    onClick = onRetry, shape = AppShapes.SecondaryButton,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green700, contentColor = AppColors.White),
                    modifier = Modifier.heightIn(min = AppTouchTargets.SecondaryButtonHeight)
                ) { Text("Try again") }
            }
            is BalanceUiState.UnsupportedCurrency -> Text(
                "Balances currently support DKK. This group uses ${state.currency}.",
                Modifier.padding(AppSpacing.Space16), style = AppTypography.Body, color = AppColors.Ink600
            )
            is BalanceUiState.Success -> BalanceContent(state, connection)
        }
    }
}

@Composable
private fun BalanceContent(state: BalanceUiState.Success, connection: ConnectionState) {
    val maximum = remember(state.members) { state.members.maxOfOrNull { it.amountCents.toDouble().absoluteValue } ?: 0.0 }
    val sync = SyncPresentation(
        connection = connection,
        pendingCount = state.pendingCount,
        failureMessage = if (state.hasFailedSync) "Some group changes couldn't sync yet. Balances include locally saved records." else null
    )
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(AppSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space16)
    ) {
        item("group") { Text(state.groupName, style = AppTypography.ScreenTitle, color = AppColors.Ink900) }
        if (sync.hasFeedback) item("sync") { SyncFeedback(sync) }
        item("heading") { Text("Member balances", style = AppTypography.Section, color = AppColors.Ink900) }
        if (!state.hasLedgerEntries) item("empty-ledger") {
            Text("No expenses or completed repayments yet. Balances are zero.", style = AppTypography.Body, color = AppColors.Ink600)
        } else if (state.members.all { it.amountCents == 0L }) item("settled") {
            Surface(shape = AppShapes.Chip, color = AppColors.Green100) {
                Text("Everyone is all square", Modifier.padding(AppSpacing.Space12), style = AppTypography.Body, color = AppColors.Green700)
            }
        }
        item("chart") {
            Surface(shape = AppShapes.ListCard, color = AppColors.White, border = BorderStroke(1.dp, AppColors.Line)) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.Space12), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Owes", style = AppTypography.Label, color = AppColors.Ink600)
                        Text("Is owed", style = AppTypography.Label, color = AppColors.Ink600)
                    }
                    state.members.forEach { member -> BalanceRow(member, maximum) }
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(member: BalanceMember, maximum: Double) {
    val formatted = balanceAmount(member.amountCents)
    val amountColour = when {
        member.amountCents < 0L -> AppColors.Red700
        member.amountCents > 0L -> AppColors.Green700
        else -> AppColors.Ink600
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep long amounts and large text readable instead of squeezing the money into a fixed box.
        val stacked = maxWidth < 320.dp || fontScale > 1.3f || formatted.length > 13
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                    MemberName(member, Modifier.weight(1f))
                    Text(formatted, Modifier.weight(1f), style = AppTypography.CaptionStrong, color = amountColour, textAlign = TextAlign.End)
                }
                BalanceBar(member, maximum, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                MemberName(member, Modifier.width(64.dp))
                BalanceBar(member, maximum, Modifier.weight(1f))
                Text(formatted, Modifier.width(104.dp), style = AppTypography.CaptionStrong, color = amountColour, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun MemberName(member: BalanceMember, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(member.name, style = AppTypography.CaptionStrong, color = AppColors.Ink900, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (member.isYou) Text("You", style = AppTypography.Label, color = AppColors.Green700)
        else if (member.isFormerMember) Text("Former member", style = AppTypography.Label, color = AppColors.Ink600)
    }
}

@Composable
private fun BalanceBar(member: BalanceMember, maximum: Double, modifier: Modifier = Modifier) {
    val description = when {
        member.amountCents < 0L -> "Owes ${balanceAmount(member.amountCents).removePrefix("−")}"
        member.amountCents > 0L -> "Is owed ${balanceAmount(member.amountCents).removePrefix("+")}"
        else -> "Settled, 0,00 kr"
    }
    Canvas(modifier.height(32.dp).testTag("balance-bar-${member.userId}").semantics {
        contentDescription = "Balance bar for ${member.name}"
        stateDescription = description
    }) {
        val centre = size.width / 2f
        drawLine(AppColors.InputBorder, Offset(centre, 0f), Offset(centre, size.height), strokeWidth = 1.dp.toPx())
        if (maximum > 0.0 && member.amountCents != 0L) {
            val width = (member.amountCents.toDouble().absoluteValue / maximum).coerceIn(0.0, 1.0).toFloat() * centre
            drawRoundRect(
                color = if (member.amountCents < 0) AppColors.Red300 else AppColors.GreenBar,
                topLeft = Offset(if (member.amountCents < 0) centre - width else centre, 4.dp.toPx()),
                size = Size(width, size.height - 8.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}

/** The shared formatter's abs(Long.MIN_VALUE) overflows; truncate safely for that boundary only. */
internal fun balanceAmount(cents: Long): String = if (cents == Long.MIN_VALUE) {
    val whole = cents / 100 * 100
    val decimals = (cents % 100).absoluteValue.toString().padStart(2, '0')
    formatDanishAmountFromCents(whole, includeSuffix = false).dropLast(2) + decimals + " kr"
} else {
    formatDanishAmountFromCents(cents, includePlusSign = true)
}

@Preview(showBackground = true, widthDp = 412, heightDp = 720, name = "S8b Balances light")
@Preview(showBackground = true, widthDp = 412, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "S8b Balances dark")
@Composable
private fun BalancePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        BalanceScreen(
            BalanceUiState.Success("Weekend trip", listOf(
                BalanceMember("you", "Ramin", -12550L, isYou = true),
                BalanceMember("mads", "Mads", 12550L),
                BalanceMember("ida", "Ida", 0L)
            ), hasLedgerEntries = true, pendingCount = 1, hasFailedSync = false), {}, {}
        )
    }
}
