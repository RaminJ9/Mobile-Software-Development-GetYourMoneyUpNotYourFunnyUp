package com.example.getyourmoneyupnotyourfunnyup.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.model.Period
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonSize
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ConfirmButtons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.EmptyCard
import com.example.getyourmoneyupnotyourfunnyup.ui.components.GroupAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.OfflineBanner
import com.example.getyourmoneyupnotyourfunnyup.ui.components.OneLine
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SegmentedTabs
import com.example.getyourmoneyupnotyourfunnyup.ui.components.Spinner
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.GroupRowCard
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.avatarColor
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.DebtRowUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeGroupTab
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeLocalState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.PayState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SliceDetailUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.buildHomeUiState

/** S3 View groups (Home). */
@Composable
fun HomeRoute(
    onOpenGroup: (String) -> Unit,
    onSeeAllGroups: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { HomeViewModel(it.ledgerRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenGroup = onOpenGroup,
        onSeeAllGroups = onSeeAllGroups,
        onCreateGroup = onCreateGroup,
        onOpenActivity = onOpenActivity,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenGroup: (String) -> Unit,
    onSeeAllGroups: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HomeHeader(state.dateLabel, state.firstName, state.hasUnread, onOpenActivity)
        state.offlineMessage?.let { OfflineBanner(it, Modifier.offset(y = (-6).dp)) }

        if (!state.hasGroups) {
            NoGroupsYet(onCreateGroup = onCreateGroup, modifier = Modifier.padding(top = 40.dp))
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.owing) {
                OweCard(state, onAction, Modifier.weight(1f))
            } else {
                AllPaidCard(state.owedToYouCents, Modifier.weight(1f))
            }
            PaidOutCard(state, onAction, onOpenGroup, Modifier.weight(1f))
        }

        AnimatedVisibility(visible = state.paymentsOpen, modifier = Modifier.offset(y = (-8).dp)) {
            PaymentsPanel(state, onAction)
        }

        GroupsSection(state, onAction, onOpenGroup, onSeeAllGroups, onCreateGroup)
    }
}

@Composable
private fun HomeHeader(date: String, firstName: String, hasUnread: Boolean, onBell: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(date, style = AppTypography.Caption, color = AppColors.Ink600)
            Text("Hi, $firstName", style = AppTypography.ScreenTitle, color = AppColors.Ink900)
        }
        Box {
            IconButton(
                onClick = onBell,
                modifier = Modifier
                    .size(44.dp)
                    .border(1.dp, AppColors.Line, CircleShape)
                    .semantics { contentDescription = if (hasUnread) "Notifications, unread" else "Notifications" },
            ) {
                AppIcon(AppIcons.Bell, size = 22.dp, tint = AppColors.Ink900)
            }
            if (hasUnread) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-9).dp, y = 8.dp)
                        .size(12.dp)
                        .background(AppColors.White, CircleShape)
                        .padding(2.dp)
                        .background(AppColors.Green500, CircleShape),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero cards
// ---------------------------------------------------------------------------------------------

@Composable
private fun OweCard(state: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 212.dp)
            .background(AppColors.Red100, RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("You owe", style = jakarta(13, FontWeight.SemiBold), color = AppColors.Red800)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                MoneyFormat.kr(state.oweTotalCents),
                style = jakarta(if (state.oweTotalCents >= 100_000_00) 24 else 30, FontWeight.ExtraBold, -0.03f),
                color = AppColors.Red700,
                maxLines = 1,
            )
            Text("kr", style = jakarta(15, FontWeight.Bold), color = AppColors.Red700, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(state.oweGroupsLabel, style = jakarta(13, FontWeight.Medium), color = AppColors.Red800)
        Spacer(Modifier.weight(1f))
        AppButton(
            onClick = { onAction(HomeAction.TogglePayments) },
            modifier = Modifier.fillMaxWidth(),
            tone = ButtonTone.Pay,
            size = ButtonSize.Medium,
        ) {
            Text(if (state.paymentsOpen) "Hide" else "Pay now", style = jakarta(15, FontWeight.Bold))
            AppIcon(
                AppIcons.ChevronDown, size = 18.dp, strokeWidth = 2.2f,
                modifier = Modifier.rotate(if (state.paymentsOpen) 180f else 0f),
            )
        }
    }
}

@Composable
private fun AllPaidCard(owedToYouCents: Long, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 212.dp)
            .background(AppColors.Green100, RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp).background(AppColors.White, CircleShape), contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.Check, strokeWidth = 2.4f, tint = AppColors.Green700)
        }
        Text("All paid up", style = jakarta(22, FontWeight.ExtraBold, -0.02f, 25.sp), color = AppColors.Green700)
        Text(
            "You don't owe anyone right now. Nice work.",
            style = jakarta(13, FontWeight.Medium, lineHeight = 18.sp),
            color = AppColors.Green600,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "+${MoneyFormat.kr(owedToYouCents)} kr owed to you",
            style = jakarta(13, FontWeight.SemiBold),
            color = AppColors.Green700,
        )
    }
}

@Composable
private fun PaidOutCard(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .heightIn(min = 212.dp)
                .fillMaxWidth()
                .border(1.dp, AppColors.Line, RoundedCornerShape(24.dp))
                .background(AppColors.White, RoundedCornerShape(24.dp))
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paid out", Modifier.weight(1f), style = jakarta(13, FontWeight.SemiBold), color = AppColors.Ink600)
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .background(AppColors.Surface, RoundedCornerShape(16.dp))
                        .clickable { onAction(HomeAction.TogglePeriodMenu) }
                        .semantics { contentDescription = "Choose period, ${state.period.menuLabel}" }
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(state.period.short, style = jakarta(13, FontWeight.SemiBold), color = AppColors.Ink900)
                    AppIcon(AppIcons.ChevronDown, size = 16.dp, strokeWidth = 2.2f, tint = AppColors.Ink900)
                }
            }
            Box(Modifier.align(Alignment.CenterHorizontally).size(140.dp)) {
                DonutChart(
                    slices = state.slices,
                    selectedId = state.selectedSlice?.groupId,
                    onSelect = { onAction(HomeAction.SelectSlice(it)) },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    Modifier.align(Alignment.Center).width(76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(MoneyFormat.wholeKr(state.paidTotalCents), style = jakarta(16, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
                    Text(
                        "kr ${state.period.phrase}",
                        style = jakarta(11, FontWeight.SemiBold),
                        color = AppExtraColors.PieCaption,
                        textAlign = TextAlign.Center,
                    )
                }
                state.selectedSlice?.let { detail ->
                    SliceBubble(
                        detail = detail,
                        state = state,
                        onClose = { onAction(HomeAction.SelectSlice(null)) },
                        onOpenGroup = { onOpenGroup(detail.groupId) },
                    )
                }
            }
        }
        if (state.periodMenuOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(LocalDensity.current) { IntOffset((-10).dp.roundToPx(), 50.dp.roundToPx()) },
                onDismissRequest = { onAction(HomeAction.DismissPeriodMenu) },
                properties = PopupProperties(focusable = true),
            ) {
                PeriodMenu(selected = state.period, onSelect = { onAction(HomeAction.SelectPeriod(it)) })
            }
        }
    }
}

@Composable
private fun PeriodMenu(selected: Period, onSelect: (Period) -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .background(AppColors.White, RoundedCornerShape(16.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Period.entries.forEach { period ->
            val isSelected = period == selected
            Text(
                period.menuLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(if (isSelected) AppColors.Green100 else AppColors.White, RoundedCornerShape(10.dp))
                    .clickable { onSelect(period) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = jakarta(14, if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = if (isSelected) AppColors.Green700 else AppColors.Ink900,
            )
        }
    }
}

@Composable
private fun SliceBubble(
    detail: SliceDetailUi,
    state: HomeUiState,
    onClose: () -> Unit,
    onOpenGroup: () -> Unit,
) {
    val density = LocalDensity.current
    val tailX = remember(detail.groupId, state.slices) { bubbleTailX(state.slices, detail.groupId) }
    Popup(
        alignment = Alignment.TopStart,
        offset = with(density) { IntOffset((-64).dp.roundToPx(), 150.dp.roundToPx()) },
        onDismissRequest = onClose,
    ) {
        Box(Modifier.width(204.dp).padding(top = 6.dp)) {
            Box(
                Modifier
                    .offset(x = tailX.dp, y = (-6).dp)
                    .size(14.dp)
                    .rotate(45f)
                    .shadow(6.dp, RoundedCornerShape(3.dp))
                    .background(AppColors.White, RoundedCornerShape(3.dp)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(20.dp))
                    .background(AppColors.White, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).background(avatarColor(detail.paletteIndex), CircleShape))
                    OneLine(detail.name, jakarta(14, FontWeight.Bold), AppColors.Ink900, Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(28.dp)
                            .offset(x = 6.dp, y = (-6).dp)
                            .clickable(onClick = onClose)
                            .semantics { contentDescription = "Close" },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcons.Close, size = 16.dp, strokeWidth = 2f, tint = AppColors.Ink600)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Paid ${state.period.phrase}", style = jakarta(12, FontWeight.Medium), color = AppColors.Ink600)
                    Text("${MoneyFormat.kr(detail.amountCents)} kr", style = jakarta(20, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.SurfaceSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Last payment", style = jakarta(12, FontWeight.Medium), color = AppColors.Ink600)
                    Text(detail.lastPayment, style = jakarta(13, FontWeight.SemiBold), color = AppColors.Ink900)
                }
                AppButton(
                    text = "Open group",
                    onClick = onOpenGroup,
                    modifier = Modifier.fillMaxWidth(),
                    tone = ButtonTone.Soft,
                    size = ButtonSize.Compact,
                    trailingIcon = AppIcons.ArrowRight,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Payments due
// ---------------------------------------------------------------------------------------------

@Composable
private fun PaymentsPanel(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    val owing = state.owing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (owing) AppColors.Red50 else AppColors.Green50, RoundedCornerShape(24.dp))
            .border(1.dp, if (owing) AppColors.Red200 else AppColors.Green200, RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (owing) {
            Row(Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Payments due", style = jakarta(16, FontWeight.ExtraBold), color = AppColors.Ink900)
                    Text(
                        "${state.oweGroupsLabel} · ${MoneyFormat.kr(state.oweTotalCents)} kr",
                        style = jakarta(13, FontWeight.Medium),
                        color = AppColors.Red800,
                    )
                }
                if (state.showPayAll) {
                    AppButton(
                        text = "Pay all",
                        onClick = { onAction(HomeAction.AskPayAll) },
                        tone = ButtonTone.Pay,
                        size = ButtonSize.Small,
                        contentPadding = PaddingValues(horizontal = 18.dp),
                    )
                }
            }
            if (state.confirmingAll) {
                Column(
                    Modifier.fillMaxWidth().background(AppColors.White, RoundedCornerShape(18.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Pay ${MoneyFormat.kr(state.oweTotalCents)} kr to ${state.oweGroupsLabel}?",
                        style = jakarta(14, FontWeight.SemiBold, lineHeight = 20.sp),
                        color = AppColors.Ink900,
                    )
                    ConfirmButtons(onCancel = { onAction(HomeAction.CancelPay) }, onConfirm = { onAction(HomeAction.ConfirmPayAll) })
                }
            }
            state.debts.forEach { debt -> DebtCard(debt, onAction) }
        } else {
            NoPaymentsDue(onClose = { onAction(HomeAction.ClosePayments) })
        }
    }
}

@Composable
private fun DebtCard(debt: DebtRowUi, onAction: (HomeAction) -> Unit) {
    val paid = debt.state == PayState.Paid
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (paid) AppColors.Green50 else AppColors.White, RoundedCornerShape(18.dp))
            .border(1.dp, if (paid) AppColors.Green200 else AppColors.White, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupAvatar(debt.initial, debt.paletteIndex)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OneLine(debt.groupName, AppTypography.ItemTitle, AppColors.Ink900)
                Text(debt.subtitle, style = AppTypography.Caption, color = AppColors.Ink600)
            }
            Text(
                "${MoneyFormat.kr(debt.amountCents)} kr",
                style = jakarta(16, FontWeight.ExtraBold),
                color = if (paid) AppColors.Green700 else AppColors.Red700,
                maxLines = 1,
            )
        }
        when (debt.state) {
            PayState.Idle -> AppButton(
                text = "Pay ${MoneyFormat.kr(debt.amountCents)} kr",
                onClick = { onAction(HomeAction.AskPay(debt.groupId)) },
                modifier = Modifier.fillMaxWidth(),
                tone = ButtonTone.PaySoft,
                size = ButtonSize.Small,
            )
            PayState.Confirm -> ConfirmButtons(
                onCancel = { onAction(HomeAction.CancelPay) },
                onConfirm = { onAction(HomeAction.ConfirmPay(debt.groupId)) },
            )
            PayState.Pending -> StatusStrip(
                text = "Paying…",
                background = AppColors.SurfaceSoft,
                color = AppColors.Ink600,
                leading = { Spinner(size = 16.dp, color = AppColors.Ink600, trackColor = AppColors.SwitchOff) },
            )
            PayState.Paid -> StatusStrip(
                text = "Paid · settled with ${debt.creditorName}",
                background = AppColors.Green100,
                color = AppColors.Green700,
                leading = { AppIcon(AppIcons.Check, size = 18.dp, strokeWidth = 2.4f, tint = AppColors.Green700) },
            )
        }
    }
}

@Composable
internal fun StatusStrip(
    text: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(background, RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, style = jakarta(14, FontWeight.Bold), color = color)
    }
}

/** Shown in the dropdown once everything is paid; closes itself after five seconds. */
@Composable
private fun NoPaymentsDue(onClose: () -> Unit) {
    val drain = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        drain.animateTo(0f, tween(durationMillis = 5_000, easing = LinearEasing))
        onClose()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(AppColors.White, RoundedCornerShape(18.dp)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(56.dp).background(AppColors.Green100, CircleShape), contentAlignment = Alignment.Center) {
                AppIcon(AppIcons.Check, size = 28.dp, strokeWidth = 2.4f, tint = AppColors.Green700)
            }
            Text("No payments due", style = jakarta(17, FontWeight.ExtraBold), color = AppColors.Ink900)
            Text(
                "You're all square with every group. We'll let you know when something new comes in.",
                style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                color = AppColors.Ink600,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).semantics { contentDescription = "Close" },
        ) {
            AppIcon(AppIcons.Close, size = 18.dp, strokeWidth = 2.2f, tint = AppColors.Ink600)
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(5.dp)
                .background(AppColors.Green50, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(drain.value)
                    .height(5.dp)
                    .background(AppColors.Green500, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Your groups
// ---------------------------------------------------------------------------------------------

@Composable
private fun GroupsSection(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenGroup: (String) -> Unit,
    onSeeAllGroups: () -> Unit,
    onCreateGroup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your groups", Modifier.weight(1f), style = AppTypography.Section, color = AppColors.Ink900)
            TextButton(onClick = onCreateGroup) {
                Text("New group", style = jakarta(14, FontWeight.Bold), color = AppColors.Green700)
            }
        }
        SegmentedTabs(
            labels = HomeGroupTab.entries.map { it.label },
            selectedIndex = state.groupTab.ordinal,
            onSelect = { onAction(HomeAction.SelectGroupTab(HomeGroupTab.entries[it])) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.groups.forEach { row ->
                GroupRowCard(row = row, onClick = { onOpenGroup(row.groupId) })
            }
            if (state.groups.isEmpty()) {
                EmptyCard(title = null, text = "No favourites yet. Star a group to pin it here.")
            }
            if (state.showSeeAll) {
                AppButton(
                    text = "See all ${state.groupsInTab} groups",
                    onClick = onSeeAllGroups,
                    modifier = Modifier.fillMaxWidth(),
                    tone = ButtonTone.Secondary,
                    size = ButtonSize.Medium,
                    trailingIcon = AppIcons.ArrowRight,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// S-empty: No groups yet
// ---------------------------------------------------------------------------------------------

@Composable
private fun NoGroupsYet(onCreateGroup: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(200.dp, 160.dp)) {
            Dot(20, 34, 92, AppColors.Green100)
            Dot(86, 18, 72, AppColors.Lavender50)
            Dot(110, 80, 64, Color(0xFFFDEBD9))
            Box(
                Modifier.offset(46.dp, 60.dp).size(40.dp).background(AppColors.White, CircleShape).padding(4.dp)
                    .background(AppColors.Green300, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("V", style = jakarta(15, FontWeight.ExtraBold), color = AppColors.Ink900) }
            DashedDot(104, 36, Color(0xFFB7A3E3))
            DashedDot(124, 94, Color(0xFFEBB98A))
        }
        Text("No groups yet", style = jakarta(22, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
        Text(
            "Make a group for your flat, a trip or Friday bar. Add an expense and we'll keep track of who owes what.",
            style = jakarta(15, FontWeight.Medium, lineHeight = 23.sp),
            color = AppColors.Ink600,
            textAlign = TextAlign.Center,
        )
        AppButton(
            text = "Create a group",
            onClick = onCreateGroup,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            leadingIcon = AppIcons.Plus,
        )
    }
}

@Composable
private fun Dot(x: Int, y: Int, size: Int, color: Color) {
    Box(Modifier.offset(x.dp, y.dp).size(size.dp).background(color, CircleShape))
}

@Composable
private fun DashedDot(x: Int, y: Int, color: Color) {
    Box(
        Modifier.offset(x.dp, y.dp).size(36.dp).background(AppColors.White, CircleShape)
            .border(2.dp, color, CircleShape),
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun previewState(local: HomeLocalState = HomeLocalState(), online: Boolean = true): HomeUiState {
    val now = System.currentTimeMillis()
    return buildHomeUiState(DemoLedger.create(now), online, local, now)
}

@Preview(name = "Home", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        HomeScreen(previewState(), {}, {}, {}, {}, {})
    }
}

@Preview(name = "Home – payments due", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun HomePaymentsPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        HomeScreen(previewState(HomeLocalState(paymentsOpen = true)), {}, {}, {}, {}, {})
    }
}

@Preview(name = "Home – offline", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun HomeOfflinePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        HomeScreen(previewState(online = false), {}, {}, {}, {}, {})
    }
}
