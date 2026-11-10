package com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.model.ActivityType
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonSize
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ConfirmButtons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.EmptyCard
import com.example.getyourmoneyupnotyourfunnyup.ui.components.FilterPill
import com.example.getyourmoneyupnotyourfunnyup.ui.components.PersonAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.Spinner
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.StatusStrip
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.avatarColor
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityFilter
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityLocalState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AmountTone
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.FeedAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.FeedRowUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.buildActivityUiState

@Composable
fun ActivityRoute(modifier: Modifier = Modifier) {
    val viewModel = appViewModel { ActivityViewModel(it.ledgerRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ActivityScreen(state, viewModel::onAction, modifier)
}

@Composable
fun ActivityScreen(state: ActivityUiState, onAction: (ActivityAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(AppColors.White).statusBarsPadding()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", Modifier.weight(1f), style = jakarta(26, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
                if (state.hasUnread) {
                    TextButton(onClick = { onAction(ActivityAction.MarkAllRead) }) {
                        Text("Mark all as read", style = jakarta(14, FontWeight.Bold), color = AppColors.Green700)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityFilter.entries.forEach { filter ->
                FilterPill(
                    label = when (filter) {
                        ActivityFilter.All -> "All"
                        ActivityFilter.NeedsYou -> "Needs you · ${state.needsYouCount}"
                        ActivityFilter.Expenses -> "Expenses"
                        ActivityFilter.Payments -> "Payments"
                    },
                    selected = filter == state.filter,
                    onClick = { onAction(ActivityAction.SelectFilter(filter)) },
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.sections.forEach { section ->
                item(key = "section:${section.title}") {
                    Text(
                        section.title,
                        Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp),
                        style = jakarta(13, FontWeight.Bold),
                        color = AppColors.Ink600,
                    )
                }
                items(section.rows, key = { it.id }) { row -> FeedCard(row, onAction) }
            }
            if (state.sections.isEmpty()) {
                item(key = "empty") {
                    EmptyCard(
                        title = "Nothing needs you right now",
                        text = "No payments due and everything is synced.",
                        modifier = Modifier.padding(top = 24.dp),
                        background = AppColors.Green50,
                        titleColor = AppColors.Green700,
                        textColor = AppColors.Green600,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedCard(row: FeedRowUi, onAction: (ActivityAction) -> Unit) {
    val done = row.action is FeedAction.Done
    val (background, border) = when {
        done -> AppColors.Green50 to AppColors.Green200
        row.unread -> AppExtraColors.UnreadTint to AppExtraColors.UnreadBorder
        else -> AppColors.White to AppColors.LineSoft
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(18.dp))
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp)) {
                val avatar = row.avatar
                if (avatar == null) {
                    Box(Modifier.size(44.dp).background(AppColors.Red50, CircleShape))
                } else {
                    PersonAvatar(avatar.initial, avatar.paletteIndex)
                }
                val (badge, icon) = when (row.type) {
                    ActivityType.REMINDER -> AppColors.Red700 to AppIcons.Bell
                    ActivityType.PAID -> AppColors.Green700 to AppIcons.Check
                    ActivityType.FAILED -> AppColors.Red700 to AppIcons.AlertMark
                    ActivityType.ADDED -> AppColors.Green500 to AppIcons.Plus
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(3.dp, 3.dp)
                        .size(20.dp)
                        .background(AppColors.White, CircleShape)
                        .padding(2.dp)
                        .background(badge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(icon, size = 12.dp, strokeWidth = 2.6f, tint = AppColors.White) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(row.actor) }
                        append(" ")
                        append(row.text)
                    },
                    style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                    color = AppColors.Ink900,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(avatarColor(row.groupPaletteIndex), CircleShape))
                    Text("${row.groupName} · ${row.time}", style = jakarta(12, FontWeight.SemiBold), color = AppColors.Ink600)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                row.amountCents?.let { amount ->
                    Text(
                        row.amountSign + MoneyFormat.kr(amount),
                        style = jakarta(14, FontWeight.ExtraBold),
                        color = when (row.amountTone) {
                            AmountTone.Owe -> AppColors.Red700
                            AmountTone.Positive -> AppColors.Green700
                            AmountTone.Neutral -> AppColors.Ink900
                        },
                        maxLines = 1,
                    )
                }
                if (row.unread) {
                    Box(Modifier.size(8.dp).background(AppColors.Green500, CircleShape).semantics { contentDescription = "Unread" })
                }
            }
        }
        val actionModifier = Modifier.fillMaxWidth().padding(start = 56.dp)
        when (val action = row.action) {
            FeedAction.None -> Unit
            is FeedAction.Pay -> AppButton(
                text = "Pay ${MoneyFormat.kr(action.amountCents)} kr",
                onClick = { onAction(ActivityAction.AskPay(row.id)) },
                modifier = actionModifier, tone = ButtonTone.Pay, size = ButtonSize.Small,
            )
            FeedAction.Confirm -> ConfirmButtons(
                onCancel = { onAction(ActivityAction.CancelPay) },
                onConfirm = { onAction(ActivityAction.ConfirmPay(row.id)) },
                modifier = actionModifier,
            )
            is FeedAction.Working -> StatusStrip(
                action.text, AppColors.SurfaceSoft, AppColors.Ink600, actionModifier,
                leading = { Spinner(size = 14.dp, color = AppColors.Ink600, trackColor = AppColors.SwitchOff) },
            )
            is FeedAction.Done -> StatusStrip(
                action.text, AppColors.Green100, AppColors.Green700, actionModifier,
                leading = { AppIcon(AppIcons.Check, size = 16.dp, strokeWidth = 2.4f, tint = AppColors.Green700) },
            )
            FeedAction.Retry -> AppButton(
                text = "Try now", onClick = { onAction(ActivityAction.Retry(row.id)) },
                modifier = actionModifier, tone = ButtonTone.RedOutline, size = ButtonSize.Small,
            )
        }
    }
}

@Preview(name = "Activity", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ActivityPreview() {
    val now = System.currentTimeMillis()
    GetYourMoneyUpNotYourFunnyUpTheme {
        ActivityScreen(buildActivityUiState(DemoLedger.create(now), ActivityLocalState(), now), {})
    }
}
