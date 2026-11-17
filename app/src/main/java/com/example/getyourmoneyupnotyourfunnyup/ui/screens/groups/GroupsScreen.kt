package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.relativeDate
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.GroupListFilter
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsViewModel

sealed interface GroupsAction {
    data class Query(val query: String) : GroupsAction
    data class Filter(val filter: GroupListFilter) : GroupsAction
    data class ToggleFavourite(val groupId: String) : GroupsAction
    data object DismissMessage : GroupsAction
}

/** S4 & S-filter · `Groups` tab (search, filter chips with counts, favourite stars). */
@Composable
fun GroupsRoute(
    viewModel: GroupsViewModel,
    onCreateGroup: () -> Unit,
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
    val shown = (state as? GroupsUiState.Success)?.let {
        it.copy(data = it.data.copy(nowMillis = now, sync = it.data.sync.copy(connection = connection)))
    } ?: state
    GroupsScreen(shown, { action ->
        when (action) {
            is GroupsAction.Query -> viewModel.setQuery(action.query)
            is GroupsAction.Filter -> viewModel.selectFilter(action.filter)
            is GroupsAction.ToggleFavourite -> viewModel.toggleFavourite(action.groupId)
            GroupsAction.DismissMessage -> viewModel.dismissMessage()
        }
    }, onCreateGroup, onOpenGroup, modifier)
}

@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onAction: (GroupsAction) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalContentColor provides AppColors.Ink900) {
        when (state) {
            GroupsUiState.Loading -> Box(modifier.fillMaxSize().background(AppColors.White), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Green700)
            }
            is GroupsUiState.Error -> Box(
                modifier.fillMaxSize().background(AppColors.White).padding(AppSpacing.Space16),
                contentAlignment = Alignment.Center
            ) { Text(state.message, style = AppTypography.Body, color = AppColors.Ink600) }
            is GroupsUiState.Success -> GroupsContent(state, onAction, onCreateGroup, onOpenGroup, modifier)
        }
    }
}

@Composable
private fun GroupsContent(
    state: GroupsUiState.Success,
    onAction: (GroupsAction) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier.fillMaxSize().background(AppColors.White)) {
        Column(
            Modifier.padding(start = AppSpacing.Space16, end = AppSpacing.Space16, top = 28.dp, bottom = AppSpacing.Space12),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Groups", Modifier.weight(1f).semantics { heading() }, style = AppTypography.ScreenTitleLarge)
                Button(
                    onClick = onCreateGroup,
                    modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget),
                    shape = AppShapes.InlineButton,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green100, contentColor = AppColors.Green700),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    DashboardSymbol(DashboardIcon.Plus, Modifier.size(16.dp), AppColors.Green700)
                    Spacer(Modifier.width(6.dp))
                    Text("New group", style = AppTypography.ButtonInline)
                }
            }
            AppTextField(
                value = state.query,
                onValueChange = { onAction(GroupsAction.Query(it)) },
                placeholder = "Search groups",
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Search,
                filled = true,
                leading = { DashboardSymbol(DashboardIcon.Search, Modifier.size(20.dp), AppColors.Ink600) }
            )
            FilterChips(state, onAction)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = AppSpacing.Space16, end = AppSpacing.Space16, top = AppSpacing.Space4, bottom = AppSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
        ) {
            if (state.data.sync.hasFeedback) item("sync") { SyncFeedback(state.data.sync) }
            state.sourceError?.let { item("source-error") { Text(it, style = AppTypography.Caption, color = AppColors.Ink600) } }
            state.message?.let { message ->
                item("message") {
                    Row(
                        Modifier.fillMaxWidth().background(AppColors.Amber100, AppShapes.SecondaryButton)
                            .padding(start = AppSpacing.Space12, end = AppSpacing.Space4),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, Modifier.weight(1f), style = AppTypography.Caption, color = AppColors.Amber800)
                        IconButton(onClick = { onAction(GroupsAction.DismissMessage) }) {
                            DashboardSymbol(DashboardIcon.Close, Modifier.size(18.dp), AppColors.Amber800, "Dismiss")
                        }
                    }
                }
            }
            state.summary?.let { summary ->
                item("summary") {
                    Text(
                        summary.text,
                        Modifier.fillMaxWidth().padding(bottom = AppSpacing.Space4)
                            .background(if (summary.owes) AppColors.Red50 else AppColors.Green50, AppShapes.SecondaryButton)
                            .padding(horizontal = 14.dp, vertical = AppSpacing.Space12),
                        style = AppTypography.ButtonInline,
                        color = if (summary.owes) AppColors.Red800 else AppColors.Green700
                    )
                }
            }
            items(state.shown, key = { it.group.id }) { group ->
                GroupCard(
                    group,
                    state.balanceFor(group.group.id),
                    state.data.nowMillis,
                    state.canFavourite,
                    { onOpenGroup(group.group.id) },
                    { onAction(GroupsAction.ToggleFavourite(group.group.id)) }
                )
            }
            state.empty?.let { empty ->
                item("empty") {
                    Column(
                        Modifier.fillMaxWidth().background(AppColors.SurfaceSoft, AppShapes.ListCard).padding(vertical = 32.dp, horizontal = AppSpacing.Space20),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(empty.title, style = AppTypography.Subsection, textAlign = TextAlign.Center)
                        Text(empty.text, style = AppTypography.Body, color = AppColors.Ink600, textAlign = TextAlign.Center)
                        if (state.data.groups.isEmpty()) DashboardButton(
                            "Create a group", onCreateGroup,
                            Modifier.padding(top = AppSpacing.Space8).heightIn(min = AppTouchTargets.SecondaryButtonHeight),
                            AppColors.Green700, AppColors.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(state: GroupsUiState.Success, onAction: (GroupsAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
    ) {
        GroupListFilter.entries.forEach { filter ->
            val on = state.filter == filter
            val (fill, ink) = when (filter) {
                GroupListFilter.YouOwe -> AppColors.Red100 to AppColors.Red700
                GroupListFilter.OwedToYou -> AppColors.Green100 to AppColors.Green700
                else -> AppColors.Ink900 to AppColors.White
            }
            val moneyFilter = filter == GroupListFilter.YouOwe || filter == GroupListFilter.OwedToYou || filter == GroupListFilter.Settled
            val count = if (moneyFilter && state.data.balancesByGroup == null) null else state.counts[filter]
            Surface(
                onClick = { onAction(GroupsAction.Filter(filter)) },
                modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget).semantics {
                    selected = on
                    stateDescription = if (on) "Selected" else "Not selected"
                },
                shape = AppShapes.Pill,
                color = if (on) fill else AppColors.White,
                border = BorderStroke(1.5.dp, if (on) fill else AppColors.Line)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(filter.label, style = AppTypography.ButtonInline, color = if (on) ink else AppColors.Ink900)
                    if (count != null) Text(
                        count.toString(),
                        style = AppTypography.Label,
                        color = (if (on) ink else AppColors.Ink900).copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: DashboardGroup,
    balanceCents: Long?,
    nowMillis: Long,
    canFavourite: Boolean,
    onOpen: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = AppShapes.ListCard,
        border = BorderStroke(1.dp, AppColors.LineSoft),
        color = AppColors.White
    ) {
        Row(Modifier.padding(end = AppSpacing.Space4), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f)
                    .clickable(role = Role.Button, onClickLabel = "Open ${group.group.name}", onClick = onOpen)
                    .padding(start = AppSpacing.Space12, top = AppSpacing.Space12, bottom = AppSpacing.Space12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
            ) {
                DashboardAvatar(group.group.name, AppColors.groupColorAt(group.colourIndex))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(group.group.name, style = AppTypography.ItemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    GroupSubtitle(group, nowMillis)
                }
                BalanceChip(balanceCents, Modifier.widthIn(max = 126.dp))
            }
            val favouriteLabel = if (group.isFavourite) "Remove ${group.group.name} from favourites" else "Add ${group.group.name} to favourites"
            IconToggleButton(
                checked = group.isFavourite,
                onCheckedChange = { onToggleFavourite() },
                enabled = canFavourite,
                modifier = Modifier.size(width = 40.dp, height = AppTouchTargets.MinTouchTarget)
            ) {
                DashboardSymbol(
                    if (group.isFavourite) DashboardIcon.Star else DashboardIcon.StarOutline,
                    Modifier.size(20.dp),
                    if (group.isFavourite) AppColors.StarGold else AppColors.Ink400,
                    favouriteLabel
                )
            }
        }
    }
}

@Composable
private fun GroupSubtitle(group: DashboardGroup, nowMillis: Long) {
    if (group.pendingExpenseCount > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
            CircularProgressIndicator(Modifier.size(13.dp), color = AppColors.Amber800, strokeWidth = 1.5.dp)
            Text(
                "${group.pendingExpenseCount} expense${if (group.pendingExpenseCount == 1) "" else "s"} waiting to sync",
                style = AppTypography.Caption,
                color = AppColors.Amber700
            )
        }
    } else {
        val members = group.group.members.size
        Text(
            "$members member${if (members == 1) "" else "s"} · Active ${activeLabel(group.lastActivityAt, nowMillis)}",
            style = AppTypography.Caption,
            color = AppColors.Ink600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "today" / "yesterday" in lower case, older dates as "Sun 1 Nov" (matches the HiFi). */
internal fun activeLabel(millis: Long, nowMillis: Long): String =
    relativeDate(millis, nowMillis).let { if (it == "Today" || it == "Yesterday") it.lowercase() else it }
