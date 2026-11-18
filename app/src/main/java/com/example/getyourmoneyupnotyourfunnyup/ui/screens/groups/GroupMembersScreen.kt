package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AddMemberPanel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupMembersUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupMembersViewModel

sealed interface MembersAction {
    data class AskRemove(val userId: String) : MembersAction
    data object Keep : MembersAction
    data class ConfirmRemove(val userId: String) : MembersAction
    data object OpenAdd : MembersAction
    data object CloseAdd : MembersAction
    data class Query(val value: String) : MembersAction
    data object LookUp : MembersAction
    data object Invite : MembersAction
    data class AddSuggestion(val contact: Contact) : MembersAction
}

/**
 * S8c · `Members` tab of the group hub. Meant to be placed under the group header's segmented
 * tabs (Activity · Balances · Members); [GroupMembersContent] is the scroll-free body for that.
 */
@Composable
fun GroupMembersRoute(viewModel: GroupMembersViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection = rememberConnectionState()
    LifecycleResumeEffect(viewModel) {
        viewModel.retryObservation()
        onPauseOrDispose { }
    }
    LaunchedEffect(connection, viewModel) {
        if (connection == ConnectionState.Online) viewModel.retryObservation()
    }
    val shown = (state as? GroupMembersUiState.Success)?.let {
        it.copy(data = it.data.copy(sync = it.data.sync.copy(connection = connection)))
    } ?: state
    GroupMembersScreen(shown, { action ->
        when (action) {
            is MembersAction.AskRemove -> viewModel.askRemove(action.userId)
            MembersAction.Keep -> viewModel.keepMember()
            is MembersAction.ConfirmRemove -> viewModel.confirmRemove(action.userId)
            MembersAction.OpenAdd -> viewModel.openAddMember()
            MembersAction.CloseAdd -> viewModel.closeAddMember()
            is MembersAction.Query -> viewModel.setAddQuery(action.value)
            MembersAction.LookUp -> viewModel.lookUpMember()
            MembersAction.Invite -> viewModel.sendInvite()
            is MembersAction.AddSuggestion -> viewModel.addSuggestion(action.contact)
        }
    }, modifier)
}

@Composable
fun GroupMembersScreen(state: GroupMembersUiState, onAction: (MembersAction) -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalContentColor provides AppColors.Ink900) {
        when (state) {
            GroupMembersUiState.Loading -> Box(modifier.fillMaxSize().background(AppColors.White), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Green700)
            }
            is GroupMembersUiState.Error -> Box(
                modifier.fillMaxSize().background(AppColors.White).padding(AppSpacing.Space16),
                contentAlignment = Alignment.Center
            ) { Text(state.message, style = AppTypography.Body, color = AppColors.Ink600) }
            is GroupMembersUiState.Success -> Column(
                modifier.fillMaxSize().background(AppColors.White).verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space20)
            ) { GroupMembersContent(state, onAction) }
        }
    }
}

/** Body of the Members tab without its own scroll container (embed inside the group hub). */
@Composable
fun GroupMembersContent(state: GroupMembersUiState.Success, onAction: (MembersAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        if (state.data.sync.hasFeedback) SyncFeedback(state.data.sync)
        state.sourceError?.let { Text(it, style = AppTypography.Caption, color = AppColors.Ink600) }
        state.message?.let {
            Text(
                it,
                Modifier.fillMaxWidth().background(AppColors.Amber100, AppShapes.SecondaryButton).padding(AppSpacing.Space12)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = AppTypography.Caption,
                color = AppColors.Amber800
            )
        }
        state.lastWrite?.takeIf { it != SyncStatus.SYNCED }?.let { SyncStatusStrip(it, Modifier.fillMaxWidth()) }
        if (state.canManage) {
            val panel = state.addPanel
            if (panel == null) DashedAddButton("Add member", { onAction(MembersAction.OpenAdd) })
            else AddMemberCard(panel, state.suggestions, onAction)
        }
        state.members.forEach { member -> MemberCard(member, state, onAction) }
    }
}

@Composable
private fun AddMemberCard(panel: AddMemberPanel, suggestions: List<Contact>, onAction: (MembersAction) -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = AppShapes.BigButton,
        border = BorderStroke(1.dp, AppColors.Green200),
        color = AppColors.White
    ) {
        Column(Modifier.padding(AppSpacing.Space12), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add member", Modifier.weight(1f), style = AppTypography.Subsection)
                IconButton(onClick = { onAction(MembersAction.CloseAdd) }, modifier = Modifier.size(AppTouchTargets.MinTouchTarget)) {
                    DashboardSymbol(DashboardIcon.Close, Modifier.size(18.dp), AppColors.Ink600, "Close add member")
                }
            }
            MemberLookupField(
                panel.query, panel.lookup,
                { onAction(MembersAction.Query(it)) },
                { onAction(MembersAction.LookUp) },
                { onAction(MembersAction.Invite) },
                enabled = !panel.adding
            )
            if (panel.adding) ActionStatus("Adding…", working = true)
            if (suggestions.isNotEmpty()) {
                SectionCaption("People you've split with")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.take(4).forEach { contact ->
                        SuggestionRow(contact, { onAction(MembersAction.AddSuggestion(contact)) }, enabled = !panel.adding)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(member: GroupMemberItem, state: GroupMembersUiState.Success, onAction: (MembersAction) -> Unit) {
    val isYou = member.userId == state.data.currentUserId
    val balance = state.balanceFor(member.userId)
    val warning = state.warningFor == member.userId
    val removing = member.userId in state.removing
    Surface(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = AppShapes.BigButton,
        border = BorderStroke(1.dp, if (warning) AppColors.Amber400 else AppColors.LineSoft),
        color = AppColors.White
    ) {
        Column(Modifier.padding(horizontal = AppSpacing.Space12, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                PersonAvatar(member.displayName, member.colourIndex, isYou = isYou, invited = member.invited)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            member.displayName, Modifier.weight(1f, fill = false),
                            style = AppTypography.ItemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (member.invited) Text(
                            "invited",
                            Modifier.background(AppColors.Amber100, AppShapes.Chip).padding(horizontal = 6.dp, vertical = 2.dp),
                            style = AppTypography.Label,
                            color = AppColors.Amber800
                        )
                    }
                    Text(
                        memberSubtitle(member, state.data.currentUserId),
                        style = AppTypography.Caption,
                        color = if (member.dietaryRules.isNotEmpty() && !isYou) AppColors.Lavender800 else AppColors.Ink600,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MemberBalanceChip(balance, isYou)
                if (removing) {
                    Box(Modifier.size(40.dp, AppTouchTargets.MinTouchTarget), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.Ink600, strokeWidth = 2.dp)
                    }
                } else if (state.canRemove(member)) {
                    IconButton(
                        onClick = { onAction(MembersAction.AskRemove(member.userId)) },
                        modifier = Modifier.size(40.dp, AppTouchTargets.MinTouchTarget)
                    ) {
                        DashboardSymbol(DashboardIcon.Close, Modifier.size(18.dp), AppColors.Ink400, "Remove ${member.displayName}")
                    }
                }
            }
            if (warning) RemovalWarning(member, balance, onAction)
        }
    }
}

/** S-Warn · inline Amber card with `Keep <Name>` / `Remove anyway`. */
@Composable
private fun RemovalWarning(member: GroupMemberItem, balance: Long?, onAction: (MembersAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(AppColors.Amber100, AppShapes.InlineButton).padding(AppSpacing.Space12)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(removalWarningText(member.displayName, balance), style = AppTypography.BodyStrong, color = AppColors.Amber800)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
            DashboardButton(
                "Keep ${member.displayName}", { onAction(MembersAction.Keep) }, Modifier.weight(1f),
                AppColors.White, AppColors.Ink900
            )
            DashboardButton(
                "Remove anyway", { onAction(MembersAction.ConfirmRemove(member.userId)) }, Modifier.weight(1f),
                AppColors.Amber100, AppColors.Amber800, outlined = true, borderColour = AppColors.Amber800
            )
        }
    }
}

@Composable
private fun MemberBalanceChip(balanceCents: Long?, isYou: Boolean) {
    // Same as the HiFi member chips: "Owes" red, "Owed" green, "Settled"/unknown neutral.
    val (fill, ink) = when {
        balanceCents == null || balanceCents == 0L -> AppColors.Surface to AppColors.Ink600
        balanceCents < 0 -> AppColors.Red100 to AppColors.Red700
        else -> AppColors.Green100 to AppColors.Green700
    }
    Text(
        memberBalanceLabel(balanceCents, isYou),
        Modifier.widthIn(max = 116.dp).background(fill, AppShapes.Chip).padding(horizontal = 10.dp, vertical = 6.dp),
        style = AppTypography.Label,
        color = ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
