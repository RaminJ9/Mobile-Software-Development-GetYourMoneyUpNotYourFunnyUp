package com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel

sealed interface ActivityAction {
    data class Filter(val filter: ActivityFilter) : ActivityAction
    data class Ask(val eventId: String) : ActivityAction
    data class Confirm(val eventId: String) : ActivityAction
    data class Retry(val eventId: String) : ActivityAction
    data object Cancel : ActivityAction
    data object MarkAllRead : ActivityAction
}

@Composable
fun ActivityRoute(viewModel: ActivityViewModel, modifier: Modifier = Modifier) {
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
    val shown = (state as? ActivityUiState.Success)?.let {
        it.copy(data = it.data.copy(nowMillis = now, sync = it.data.sync.copy(connection = connection)))
    } ?: state
    ActivityScreen(shown, { action ->
            when (action) {
                is ActivityAction.Filter -> viewModel.selectFilter(action.filter)
                is ActivityAction.Ask -> viewModel.askPayment(action.eventId)
                is ActivityAction.Confirm -> viewModel.confirmPayment(action.eventId)
                is ActivityAction.Retry -> viewModel.retry(action.eventId)
                ActivityAction.Cancel -> viewModel.cancelPayment()
                ActivityAction.MarkAllRead -> viewModel.markAllRead()
            }
        }, modifier)
}

@Composable
fun ActivityScreen(state: ActivityUiState, onAction: (ActivityAction) -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalContentColor provides AppColors.Ink900) {
        when (state) {
            ActivityUiState.Loading -> Box(
                modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AppColors.Green700) }
            is ActivityUiState.Error -> Box(
                modifier.fillMaxSize().padding(AppSpacing.Space16),
                contentAlignment = Alignment.Center
            ) { Text(state.message, style = AppTypography.Body, color = AppColors.Ink600) }
            is ActivityUiState.Success -> ActivityContent(state, onAction, modifier)
        }
    }
}

@Composable
private fun ActivityContent(state: ActivityUiState.Success, onAction: (ActivityAction) -> Unit, modifier: Modifier) {
    val shown = remember(
        state.data,
        state.filter,
        state.actions
    ) { filterActivities(state.events, state.data.userId, state.filter, state.actions) }
    val sections = remember(shown, state.data.nowMillis) { shown.groupBy { activityDay(it.occurredAt, state.data.nowMillis) } }
    Column(modifier.fillMaxSize().background(AppColors.White)) {
        Row(
            Modifier.fillMaxWidth().padding(start = AppSpacing.Space16, end = AppSpacing.Space16, top = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Activity", Modifier.weight(1f), style = AppTypography.ScreenTitleLarge, color = AppColors.Ink900)
            if (state.hasUnread) TextButton(onClick = { onAction(ActivityAction.MarkAllRead) }) { Text(
                    "Mark all as read",
                    style = AppTypography.ButtonInline,
                    color = AppColors.Green700
            ) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
        ) {
            items(ActivityFilter.entries) { filter ->
                val selected = state.filter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onAction(ActivityAction.Filter(filter)) },
                    label = { Text(
                            if (filter == ActivityFilter.NeedsYou) "Needs you · ${state.needsCount}" else filter.label,
                            style = AppTypography.ButtonInline
                    ) },
                    shape = AppShapes.Pill,
                    modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = AppColors.White,
                        labelColor = AppColors.Ink900,
                        selectedContainerColor = AppColors.Ink900,
                        selectedLabelColor = AppColors.White
                    ),
                    border = BorderStroke(1.dp, if (selected) AppColors.Ink900 else AppColors.Line)
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = AppSpacing.Space16, end = AppSpacing.Space16, bottom = AppSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state.data.sync.hasFeedback) item("sync-feedback") { SyncFeedback(state.data.sync) }
            state.sourceError?.let { message ->
                item("source-error") {
                    Text(message, style = AppTypography.Caption, color = AppColors.Ink600)
                }
            }
            if (state.data.events == null) item("unavailable") {
                Text(
                    "Activity history unavailable",
                    Modifier.padding(AppSpacing.Space20),
                    style = AppTypography.Body,
                    color = AppColors.Ink600
                )
            } else if (shown.isEmpty()) item("empty") {
                Surface(
                    Modifier.fillMaxWidth().padding(top = AppSpacing.Space24),
                    color = AppColors.Green50,
                    shape = AppShapes.ListCard
                ) {
                    Column(
                        Modifier.padding(horizontal = AppSpacing.Space20, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (state.filter == ActivityFilter.NeedsYou) "Nothing needs you right now" else "No activity yet",
                            style = AppTypography.Subsection,
                            color = AppColors.Green700
                        )
                        Text(
                            if (state.filter == ActivityFilter.NeedsYou) "No actions in this filter." else "Activity will appear here when there's something to show.",
                            style = AppTypography.Caption,
                            color = AppColors.Green600
                        )
                    }
                }
            }
            listOf("Today", "Yesterday", "Earlier").forEach { day ->
                val events = sections[day].orEmpty()
                if (events.isNotEmpty()) {
                    item("section-$day") { Text(
                            day,
                            Modifier.padding(start = AppSpacing.Space4, top = AppSpacing.Space12, bottom = 2.dp),
                            style = AppTypography.CaptionStrong,
                            color = AppColors.Ink600
                    ) }
                    items(events, key = { it.id }) { event -> ActivityCard(event, state, onAction) }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(event: ActivityEvent, state: ActivityUiState.Success, onAction: (ActivityAction) -> Unit) {
    val action = state.actionFor(event)
    val unread = event.unread && event.id !in state.readIds
    val done = action is InlineActionState.Done && action.syncStatus == SyncStatus.SYNCED
    Surface(
        Modifier.fillMaxWidth(), shape = AppShapes.BigButton,
        color = if (done) AppColors.Green50 else if (unread) AppColors.ActivityUnread else AppColors.White,
        border = BorderStroke(
            1.dp,
            if (done) AppColors.Green200 else if (unread) AppColors.ActivityUnreadBorder else AppColors.LineSoft
        )
    ) {
        Column(Modifier.padding(AppSpacing.Space12), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                EventAvatar(event, state.data.userId, done)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val actor = if (event.actor?.id == state.data.userId) "You" else event.actor?.displayName ?: event.expenseTitle.orEmpty()
                    Text(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(actor) }
                            if (actor.isNotEmpty()) append(" ")
                            append(if (event.type == ActivityType.SyncFailure && done) "synced" else event.description)
                        }, style = AppTypography.BodyStrong, color = AppColors.Ink900)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(AppColors.groupColorAt(event.group.colourIndex), AppShapes.Circle))
                        Text(
                            "${event.group.group.name} · ${if (activityDay(event.occurredAt, state.data.nowMillis) == "Earlier") dateText(event.occurredAt, "EEE d MMM") else dateText(event.occurredAt, "HH:mm")}",
                            style = AppTypography.Label,
                            color = AppColors.Ink600
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    event.amountCents?.let { amount ->
                        val colour = when {
                            done || event.direction == AmountDirection.Received || event.direction == AmountDirection.Settled -> AppColors.Green700
                            event.direction == AmountDirection.Owed -> AppColors.Red700
                            else -> AppColors.Ink900
                        }
                        Text(
                            formatDanishAmountFromCents(
                                amount,
                                false,
                                event.direction == AmountDirection.Received
                            ) + if (event.currency == "DKK") "" else " ${event.currency}",
                            style = AppTypography.ItemTitle,
                            color = colour
                        )
                    }
                    if (unread) Box(Modifier.size(8.dp).background(
                            AppColors.Green500,
                            AppShapes.Circle
                        ).semantics { contentDescription = "Unread" })
                }
            }
            if (event.canPay(state.data.userId) || event.type == ActivityType.Reminder && event.payment != null && action is InlineActionState.Done) {
                val payment = event.payment!!
                InlinePaymentActions(
                    action,
                    formatDanishAmountFromCents(payment.transfer.amountCents),
                    payment.creditorName,
                    { onAction(ActivityAction.Ask(event.id)) },
                    { onAction(ActivityAction.Cancel) },
                    { onAction(ActivityAction.Confirm(event.id)) },
                    Modifier.padding(start = 56.dp),
                    enabled = state.canSettle
                )
            }
            if (event.type == ActivityType.SyncFailure) Column(
                Modifier.padding(start = 56.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
            ) {
                when (action) {
                    InlineActionState.Working -> ActionStatus("Syncing…", working = true)
                    is InlineActionState.Done -> {
                        if (action.syncStatus == SyncStatus.SYNCED) ActionStatus("Synced")
                        else {
                            SyncStatusStrip(action.syncStatus ?: SyncStatus.PENDING_UPDATE)
                            DashboardButton("Try now", { onAction(ActivityAction.Retry(event.id)) },
                                Modifier.fillMaxWidth(), AppColors.White, AppColors.Red800, state.canRetry,
                                outlined = true, borderColour = AppColors.RetryBorder)
                        }
                    }
                    else -> {
                        if (action is InlineActionState.Failed) Text(
                            action.message,
                            style = AppTypography.Caption,
                            color = AppColors.Amber800
                        )
                        DashboardButton(
                            "Try now",
                            { onAction(ActivityAction.Retry(event.id)) },
                            Modifier.fillMaxWidth(),
                            AppColors.White,
                            AppColors.Red800,
                            state.canRetry,
                            outlined = true,
                            borderColour = AppColors.RetryBorder
                        )
                        if (!state.canRetry) Text(
                            "Sync service unavailable",
                            style = AppTypography.Caption,
                            color = AppColors.Ink600
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventAvatar(event: ActivityEvent, userId: String, done: Boolean) {
    val failed = event.type == ActivityType.SyncFailure
    val colour = if (failed) { if (done) AppColors.Green100 else AppColors.Red50 } else if (event.actor?.id == userId) AppColors.Green300 else AppColors.groupColorAt(event.actorColourIndex)
    val badgeColour: Color = if (done) AppColors.Green700 else when (event.type) {
        ActivityType.Reminder, ActivityType.SyncFailure -> AppColors.Red700
        ActivityType.Payment -> AppColors.Green700
        ActivityType.ExpenseChanged -> AppColors.Ink600
        ActivityType.ExpenseAdded -> AppColors.Green500
    }
    val icon = if (done) DashboardIcon.Check else when (event.type) {
        ActivityType.Reminder -> DashboardIcon.Bell
        ActivityType.SyncFailure -> DashboardIcon.Warning
        ActivityType.Payment -> DashboardIcon.Check
        ActivityType.ExpenseChanged -> DashboardIcon.Edit
        ActivityType.ExpenseAdded -> DashboardIcon.Plus
    }
    Box(Modifier.size(44.dp)) {
        DashboardAvatar(if (failed) "" else event.actor?.displayName.orEmpty(), colour, round = true)
        Surface(
            Modifier.size(20.dp).align(Alignment.BottomEnd).offset(3.dp, 3.dp),
            color = badgeColour,
            shape = AppShapes.Circle,
            border = BorderStroke(2.dp, AppColors.White)
        ) {
            DashboardSymbol(icon, Modifier.padding(4.dp), colour = AppColors.White)
        }
    }
}
