package com.example.getyourmoneyupnotyourfunnyup.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel

sealed interface HomeAction {
    data class Filter(val filter: GroupFilter) : HomeAction
    data class Period(val period: SpendPeriod) : HomeAction
    data class Slice(val groupId: String?) : HomeAction
    data class Ask(val paymentId: String) : HomeAction
    data class Confirm(val paymentId: String) : HomeAction
    data object TogglePayments : HomeAction
    data object ClosePayments : HomeAction
    data object Cancel : HomeAction
    data object AskAll : HomeAction
    data object ConfirmAll : HomeAction
}

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenActivity: () -> Unit,
    onOpenGroups: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection = rememberConnectionState()
    val now = rememberPresentationTime()
    LifecycleResumeEffect(viewModel) {
        viewModel.retryObservation()
        onPauseOrDispose { }
    }
    LaunchedEffect(connection, viewModel) {
        if (connection == ConnectionState.Online) viewModel.retryObservation()
    }
    val shown = (state as? HomeUiState.Success)?.let {
        it.copy(data = it.data.copy(nowMillis = now, sync = it.data.sync.copy(connection = connection)))
    } ?: state
    HomeScreen(shown, { action ->
            when (action) {
                is HomeAction.Filter -> viewModel.selectFilter(action.filter)
                is HomeAction.Period -> viewModel.selectPeriod(action.period)
                is HomeAction.Slice -> viewModel.selectSlice(action.groupId)
                is HomeAction.Ask -> viewModel.askPayment(action.paymentId)
                is HomeAction.Confirm -> viewModel.confirmPayment(action.paymentId)
                HomeAction.TogglePayments -> viewModel.togglePayments()
                HomeAction.ClosePayments -> viewModel.closePayments()
                HomeAction.Cancel -> viewModel.cancelPayment()
                HomeAction.AskAll -> viewModel.askAll()
                HomeAction.ConfirmAll -> viewModel.confirmAll()
            }
        }, onOpenActivity, onOpenGroups, onCreateGroup, onJoinGroup, onOpenGroup, modifier)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenGroups: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalContentColor provides AppColors.Ink900) {
        when (state) {
            HomeUiState.Loading -> Box(
                modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AppColors.Green700) }
            is HomeUiState.Error -> Box(
                modifier.fillMaxSize().padding(AppSpacing.Space16),
                contentAlignment = Alignment.Center
            ) { Text(state.message, style = AppTypography.Body, color = AppColors.Ink600) }
            is HomeUiState.Success -> HomeContent(
                state,
                onAction,
                onOpenActivity,
                onOpenGroups,
                onCreateGroup,
                onJoinGroup,
                onOpenGroup,
                modifier
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onAction: (HomeAction) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenGroups: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier
) {
    val data = state.data
    if (data.groups.isEmpty()) {
        EmptyHomeContent(state, onOpenActivity, onCreateGroup, onJoinGroup, modifier)
        return
    }
    val groups = remember(data.groups, state.filter) { filterGroups(data.groups, state.filter) }
    LazyColumn(
        modifier.fillMaxSize().background(AppColors.White),
        contentPadding = PaddingValues(
            start = AppSpacing.Space16,
            end = AppSpacing.Space16,
            top = AppSpacing.Space20,
            bottom = AppSpacing.Space24
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
    ) {
        item("header") { HomeHeader(data, onOpenActivity) }
        state.sourceError?.let { message ->
            item("source-error") {
                Text(message, style = AppTypography.Caption, color = AppColors.Ink600)
            }
        }
        if (data.displaySync.hasFeedback) item("sync-feedback") { SyncFeedback(data.displaySync) }
            item("summary") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = AppSpacing.Space12),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
                ) {
                    BalanceHero(state, { onAction(HomeAction.TogglePayments) }, Modifier.weight(1f))
                    Surface(
                        Modifier.weight(1f).heightIn(min = 212.dp),
                        shape = AppShapes.HeroCard,
                        border = BorderStroke(1.dp, AppColors.Line),
                        color = AppColors.White
                    ) {
                        PaidOutChart(
                            data,
                            state.period,
                            state.selectedGroupId,
                            { onAction(HomeAction.Period(it)) },
                            { onAction(HomeAction.Slice(it)) },
                            onOpenGroup,
                            Modifier.padding(horizontal = 14.dp, vertical = AppSpacing.Space8)
                        )
                    }
                }
            }
            if (state.paymentsExpanded && state.hasFinancialSummary) item("payments") { PaymentsDue(
                    state,
                    onAction,
                    Modifier.padding(bottom = AppSpacing.Space12)
            ) }
            item("group-heading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your groups", Modifier.weight(1f), style = AppTypography.Section, color = AppColors.Ink900)
                    TextButton(onClick = onCreateGroup) { Text(
                            "New group",
                            style = AppTypography.ButtonInline,
                            color = AppColors.Green700
                    ) }
                }
            }
            item("group-filter") {
                Row(
                    Modifier.fillMaxWidth().background(AppColors.Surface, AppShapes.SegmentedBar).padding(AppSpacing.Space4),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)
                ) {
                    GroupFilter.entries.forEach { filter ->
                        Surface(
                            Modifier.weight(1f),
                            shape = AppShapes.Chip,
                            color = if (state.filter == filter) AppColors.White else AppColors.Surface
                        ) {
                            TextButton(onClick = { onAction(HomeAction.Filter(filter)) }) {
                                Text(
                                    filter.label,
                                    style = AppTypography.ButtonInline,
                                    color = if (state.filter == filter) AppColors.Ink900 else AppColors.Ink600
                                )
                            }
                        }
                    }
                }
            }
            items(groups.take(6), key = { "group-${it.group.id}" }) { group ->
                GroupRow(group, state, { onOpenGroup(group.group.id) })
            }
            if (groups.isEmpty()) item("no-favourites") {
                Text(
                    "No favourites yet. Star a group to pin it here.",
                    Modifier.fillMaxWidth().background(AppColors.SurfaceSoft, AppShapes.ListCard).padding(AppSpacing.Space20),
                    style = AppTypography.Body,
                    color = AppColors.Ink600
                )
            }
            if (groups.size > 6) item("see-all") {
                DashboardButton(
                    "See all ${groups.size} groups →",
                    onOpenGroups,
                    Modifier.fillMaxWidth().heightIn(min = AppTouchTargets.SecondaryButtonHeight),
                    AppColors.White,
                    AppColors.Green700,
                    outlined = true
                )
            }
    }
}

@Composable
private fun BalanceHero(state: HomeUiState.Success, onToggle: () -> Unit, modifier: Modifier) {
    val available = state.hasFinancialSummary
    val owes = state.oweCents
    val owed = state.owedCents
    val owing = available && owes > 0
    Surface(
        modifier.heightIn(min = 212.dp),
        shape = AppShapes.HeroCard,
        color = if (!available) AppColors.Surface else if (owing) AppColors.Red100 else AppColors.Green100
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = AppSpacing.Space16),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
        ) {
            if (!available) {
                Text("Balance unavailable", style = AppTypography.Section, color = AppColors.Ink900)
                Text("Waiting for balance information", style = AppTypography.Caption, color = AppColors.Ink600)
            } else if (owing) {
                Text("You owe", style = AppTypography.CaptionStrong, color = AppColors.Red800)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
                    Text(
                        formatDanishAmountFromCents(owes, false),
                        Modifier.weight(1f, fill = false),
                        style = AppTypography.Amount,
                        color = AppColors.Red700
                    )
                    Text("kr", style = AppTypography.ItemTitle, color = AppColors.Red700)
                }
                val count = state.owingGroupCount
                Text("$count ${if (count == 1) "group" else "groups"}", style = AppTypography.Caption, color = AppColors.Red800)
                DashboardButton(
                    if (state.paymentsExpanded) "Hide ⌃" else "Pay now ⌄",
                    onToggle,
                    Modifier.fillMaxWidth(),
                    AppColors.Red700,
                    AppColors.White
                )
            } else {
                Box(
                    Modifier.size(44.dp).background(AppColors.White, AppShapes.Circle),
                    contentAlignment = Alignment.Center
                ) { DashboardSymbol(DashboardIcon.Check, colour = AppColors.Green700) }
                Text("All paid up", style = AppTypography.ScreenTitle, color = AppColors.Green700)
                Text("You don't owe anyone right now. Nice work.", style = AppTypography.Caption, color = AppColors.Green600)
                Text(
                    "${formatDanishAmountFromCents(owed, includePlusSign = true)} owed to you",
                    style = AppTypography.CaptionStrong,
                    color = AppColors.Green700
                )
            }
        }
    }
}

@Composable
private fun PaymentsDue(state: HomeUiState.Success, onAction: (HomeAction) -> Unit, modifier: Modifier) {
    val clear = state.unpaid.isEmpty()
    Surface(
        modifier.fillMaxWidth(),
        color = if (clear) AppColors.Green50 else AppColors.Red50,
        shape = AppShapes.HeroCard,
        border = BorderStroke(1.dp, if (clear) AppColors.Green200 else AppColors.Red200)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (clear) {
                val progress = remember { Animatable(1f) }
                val close by rememberUpdatedState(onAction)
                LaunchedEffect(Unit) { progress.animateTo(0f, tween(5000)); close(HomeAction.ClosePayments) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("No payments due", Modifier.weight(1f), style = AppTypography.Section, color = AppColors.Green700)
                    IconButton(onClick = { onAction(HomeAction.ClosePayments) }) { DashboardSymbol(
                            DashboardIcon.Close,
                            description = "Close payments"
                    ) }
                }
                Text(
                    "You're all square with every group. We'll let you know when something new comes in.",
                    style = AppTypography.Caption,
                    color = AppColors.Ink600
                )
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.Green500,
                    trackColor = AppColors.Green100
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Payments due", style = AppTypography.Subsection, color = AppColors.Ink900)
                        Text(
                            "${state.owingGroupCount} groups · ${formatDanishAmountFromCents(state.oweCents)}",
                            style = AppTypography.Caption,
                            color = AppColors.Red800
                        )
                    }
                    if (!state.confirmingAll) DashboardButton(
                        "Pay all",
                        { onAction(HomeAction.AskAll) },
                        container = AppColors.Red700,
                        content = AppColors.White,
                        enabled = state.canSettle && !state.isWorking
                    )
                }
                if (state.confirmingAll) {
                    Surface(color = AppColors.White, shape = AppShapes.BigButton) {
                        Column(Modifier.padding(AppSpacing.Space12), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Pay ${formatDanishAmountFromCents(state.oweCents)} to ${state.owingGroupCount} groups?",
                                style = AppTypography.BodyStrong
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                                DashboardButton(
                                    "Cancel",
                                    { onAction(HomeAction.Cancel) },
                                    Modifier.weight(1f),
                                    AppColors.White,
                                    AppColors.Ink900,
                                    outlined = true
                                )
                                DashboardButton(
                                    "Confirm",
                                    { onAction(HomeAction.ConfirmAll) },
                                    Modifier.weight(1f),
                                    AppColors.Red700,
                                    AppColors.White
                                )
                            }
                        }
                    }
                }
            }
            state.payments.forEach { payment ->
                val action = state.paymentStates[payment.id] ?: InlineActionState.Idle
                if (!clear) {
                    val group = state.data.groups.firstOrNull { it.group.id == payment.groupId }
                    Surface(
                        color = if (action is InlineActionState.Done) AppColors.Green50 else AppColors.White,
                        shape = AppShapes.BigButton
                    ) {
                        Column(
                            Modifier.padding(AppSpacing.Space12),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
                            ) {
                                DashboardAvatar(group?.group?.name ?: "?", AppColors.groupColorAt(group?.colourIndex ?: 0))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        group?.group?.name ?: "Group unavailable",
                                        style = AppTypography.ItemTitle,
                                        color = AppColors.Ink900
                                    )
                                    Text(
                                        "To ${payment.creditorName} · Since ${relativeDate(payment.since, state.data.nowMillis).lowercase()}",
                                        style = AppTypography.Caption,
                                        color = AppColors.Ink600
                                    )
                                }
                                Text(
                                    formatDanishAmountFromCents(payment.transfer.amountCents),
                                    style = AppTypography.BodyStrong,
                                    color = if (action is InlineActionState.Done) AppColors.Green700 else AppColors.Red700
                                )
                            }
                            InlinePaymentActions(
                                action,
                                formatDanishAmountFromCents(payment.transfer.amountCents),
                                payment.creditorName,
                                { onAction(HomeAction.Ask(payment.id)) },
                                { onAction(HomeAction.Cancel) },
                                { onAction(HomeAction.Confirm(payment.id)) },
                                enabled = state.canSettle && state.hasFinancialSummary && (!state.isWorking || action == InlineActionState.Working),
                                soft = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(group: DashboardGroup, state: HomeUiState.Success, onClick: () -> Unit) {
    val shownBalance = remember(state, group.group.id) {
        state.balanceForGroup(group.group.id)
    }
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = AppShapes.ListCard,
        border = BorderStroke(1.dp, AppColors.LineSoft),
        color = AppColors.White
    ) {
        Row(
            Modifier.padding(AppSpacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
        ) {
            DashboardAvatar(group.group.name, AppColors.groupColorAt(group.colourIndex))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.group.name,
                        Modifier.weight(1f, fill = false),
                        style = AppTypography.ItemTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = AppColors.Ink900
                    )
                    if (group.isFavourite) DashboardSymbol(
                        DashboardIcon.Star,
                        Modifier.padding(start = AppSpacing.Space4).size(14.dp),
                        AppColors.StarGold,
                        "Favourite"
                    )
                }
                if (group.pendingExpenseCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)
                    ) {
                        CircularProgressIndicator(Modifier.size(13.dp), color = AppColors.Amber800, strokeWidth = 1.5.dp)
                        Text(
                            "${group.pendingExpenseCount} expense${if (group.pendingExpenseCount == 1) "" else "s"} waiting to sync",
                            style = AppTypography.Caption,
                            color = AppColors.Amber700
                        )
                    }
                } else Text(
                    "${group.group.members.size} members · Active ${relativeDate(group.lastActivityAt, state.data.nowMillis).lowercase()}",
                    style = AppTypography.Caption,
                    color = AppColors.Ink600
                )
            }
            BalanceChip(shownBalance, Modifier.widthIn(max = 126.dp))
        }
    }
}

@Composable
private fun EmptyGroupIllustration(name: String) {
    Box(Modifier.size(200.dp, 160.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(AppColors.Green100, 46.dp.toPx(), Offset(66.dp.toPx(), 80.dp.toPx()))
            drawCircle(AppColors.Lavender50, 36.dp.toPx(), Offset(122.dp.toPx(), 54.dp.toPx()))
            drawCircle(AppColors.EmptyGroupPeach, 32.dp.toPx(), Offset(142.dp.toPx(), 112.dp.toPx()))
            drawCircle(AppColors.White, 20.dp.toPx(), Offset(66.dp.toPx(), 80.dp.toPx()))
            drawCircle(AppColors.Green300, 16.dp.toPx(), Offset(66.dp.toPx(), 80.dp.toPx()))
            val dashed = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            drawCircle(AppColors.White, 18.dp.toPx(), Offset(122.dp.toPx(), 54.dp.toPx()))
            drawCircle(AppColors.InviteLavender, 17.dp.toPx(), Offset(122.dp.toPx(), 54.dp.toPx()), style = dashed)
            drawCircle(AppColors.White, 18.dp.toPx(), Offset(142.dp.toPx(), 112.dp.toPx()))
            drawCircle(AppColors.InvitePeach, 17.dp.toPx(), Offset(142.dp.toPx(), 112.dp.toPx()), style = dashed)
        }
        Box(Modifier.offset(46.dp, 60.dp).size(40.dp), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = AppTypography.ItemTitle, color = AppColors.Ink900)
        }
    }
}

@Composable
private fun HomeHeader(data: HomeData, onOpenActivity: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = AppSpacing.Space8, bottom = AppSpacing.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(dateText(data.nowMillis, "EEEE d MMMM"), style = AppTypography.Caption, color = AppColors.Ink600)
            Text(
                "Hi, ${data.user.displayName.substringBefore(' ')}",
                style = AppTypography.ScreenTitle,
                color = AppColors.Ink900
            )
        }
        Surface(shape = AppShapes.Circle, border = BorderStroke(1.dp, AppColors.Line), color = AppColors.White) {
            IconButton(onClick = onOpenActivity, modifier = Modifier.size(AppTouchTargets.MinTouchTarget)) {
                Box {
                    DashboardSymbol(
                        DashboardIcon.Bell,
                        description = if (data.hasUnreadActivity) "Activity, unread notifications" else "Activity"
                    )
                    if (data.hasUnreadActivity) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(
                            AppColors.Green500,
                            AppShapes.Circle
                    ))
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeContent(
    state: HomeUiState.Success,
    onOpenActivity: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    modifier: Modifier
) {
    val data = state.data
    Column(
        modifier.fillMaxSize().background(AppColors.White).padding(
            start = AppSpacing.Space16,
            end = AppSpacing.Space16,
            top = AppSpacing.Space20,
            bottom = AppSpacing.Space24
        )
    ) {
        HomeHeader(data, onOpenActivity)
        state.sourceError?.let { Text(it, style = AppTypography.Caption, color = AppColors.Ink600) }
        if (data.displaySync.hasFeedback) SyncFeedback(data.displaySync)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.Space12),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
            ) {
                EmptyGroupIllustration(data.user.displayName)
                Text("No groups yet", style = AppTypography.ScreenTitleLarge, color = AppColors.Ink900)
                Text(
                    "Make a group for your flat, a trip or Friday bar. Add an expense and we'll keep track of who owes what.",
                    style = AppTypography.Body,
                    color = AppColors.Ink600,
                    textAlign = TextAlign.Center
                )
                DashboardButton(
                    "Create a group",
                    onCreateGroup,
                    Modifier.fillMaxWidth().heightIn(min = AppTouchTargets.PrimaryButtonHeight),
                    AppColors.Green700,
                    AppColors.White
                )
                TextButton(onClick = onJoinGroup) { Text(
                        "Join with an invite link",
                        style = AppTypography.ButtonSecondary,
                        color = AppColors.Green700
                ) }
            }
        }
    }
}
