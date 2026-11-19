package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.repository.GroupRepository
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.balances.BalanceRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Account-scoped groups repository and flows used by [AppShell]. Everything is read from the local
 * database for [user], so groups, members and the Home dashboard keep working offline.
 */
internal class GroupsSession(database: AppDatabase, private val user: User) : ViewModel() {
    val repository = GroupRepository(database, user)
    val groupsFlow = repository.observeGroups()

    init {
        viewModelScope.launch {
            repository.syncRemoteUsers()
        }
    }

    fun groupHeader(groupId: String): Flow<GroupHeader?> = groupsFlow.map { groups ->
        groups.firstOrNull { it.group.id == groupId }?.let { g ->
            val creator = g.group.members.firstOrNull { it.userId == g.group.createdBy }
            GroupHeader(
                name = g.group.name,
                description = g.group.description,
                colourIndex = g.colourIndex,
                memberCount = g.group.members.size,
                createdBy = if (g.group.createdBy == user.id) "you" else creator?.displayName?.ifBlank { null } ?: "a member"
            )
        }
    }
}

internal data class GroupHeader(
    val name: String,
    val description: String?,
    val colourIndex: Int,
    val memberCount: Int,
    val createdBy: String
)

private enum class GroupSection(val label: String) { Activity("Activity"), Balances("Balances"), Members("Members") }

/** S8 group hub: header + segmented tabs. The Members tab is [GroupMembersContent]. */
@Composable
internal fun GroupDetailRoute(
    session: GroupsSession,
    groupId: String,
    membersVm: GroupMembersViewModel,
    balancesVm: BalanceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddExpense: () -> Unit = {}
) {
    val header by remember(groupId) { session.groupHeader(groupId) }.collectAsStateWithLifecycle(null)
    val membersState by membersVm.state.collectAsStateWithLifecycle()
    var section by rememberSaveable(groupId) { mutableStateOf(GroupSection.Members) }
    val onMembersAction = rememberMembersActions(membersVm)

    val scroll = rememberScrollState()
    // Balances has its own lazy list, which needs a bounded height rather than a scrolling parent.
    val balancesOpen = section == GroupSection.Balances
    Column(modifier.fillMaxSize().background(AppColors.White)
        .then(if (balancesOpen) Modifier else Modifier.verticalScroll(scroll))) {
        BackTopBar(header?.name ?: "Group", onBack)
        Column(
            Modifier.then(if (balancesOpen) Modifier.weight(1f) else Modifier)
                .padding(start = AppSpacing.Space16, end = AppSpacing.Space16, bottom = AppSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            header?.let { h ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(56.dp).background(AppColors.groupColorAt(h.colourIndex), AppShapes.AvatarTile),
                        contentAlignment = Alignment.Center
                    ) { Text(h.name.take(1).uppercase(), style = AppTypography.ScreenTitle, color = AppColors.Ink900) }
                    Column(Modifier.weight(1f)) {
                        Text(h.name, Modifier.semantics { heading() }, style = AppTypography.ScreenTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${h.memberCount} member${if (h.memberCount == 1) "" else "s"} · Created by ${h.createdBy}",
                            style = AppTypography.Caption, color = AppColors.Ink600
                        )
                    }
                }
                h.description?.let { Text(it, style = AppTypography.Body, color = AppColors.Ink600) }
            }
            Row(
                Modifier.fillMaxWidth().background(AppColors.Surface, AppShapes.SegmentedBar).padding(AppSpacing.Space4),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)
            ) {
                GroupSection.entries.forEach { s ->
                    val on = s == section
                    Surface(
                        onClick = { section = s },
                        modifier = Modifier.weight(1f).heightIn(min = AppTouchTargets.MinTouchTarget).semantics { selected = on },
                        shape = AppShapes.Chip,
                        color = if (on) AppColors.White else AppColors.Surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(s.label, style = AppTypography.ButtonInline, color = if (on) AppColors.Ink900 else AppColors.Ink600)
                        }
                    }
                }
            }
            when (section) {
                GroupSection.Members -> when (val state = membersState) {
                    is GroupMembersUiState.Success -> GroupMembersContent(state, onMembersAction)
                    is GroupMembersUiState.Error -> Text(state.message, style = AppTypography.Body, color = AppColors.Ink600)
                    GroupMembersUiState.Loading -> CircularProgressIndicator(color = AppColors.Green700)
                }
                GroupSection.Activity -> {
                    // Opens the scan flow for this group (scan a receipt or type the expense in).
                    DashboardButton("+ Add expense", onAddExpense, Modifier.fillMaxWidth())
                    SectionPlaceholder("Expenses and payments for this group will show up here.")
                }
                GroupSection.Balances -> BalanceRoute(balancesVm, onBack, modifier = Modifier.weight(1f), showHeader = false)
            }
        }
    }
}

@Composable
private fun rememberMembersActions(vm: GroupMembersViewModel): (MembersAction) -> Unit = remember(vm) {
    { action ->
        when (action) {
            is MembersAction.AskRemove -> vm.askRemove(action.userId)
            MembersAction.Keep -> vm.keepMember()
            is MembersAction.ConfirmRemove -> vm.confirmRemove(action.userId)
            MembersAction.OpenAdd -> vm.openAddMember()
            MembersAction.CloseAdd -> vm.closeAddMember()
            is MembersAction.Query -> vm.setAddQuery(action.value)
            MembersAction.LookUp -> vm.lookUpMember()
            MembersAction.Invite -> vm.sendInvite()
            is MembersAction.AddSuggestion -> vm.addSuggestion(action.contact)
        }
    }
}

@Composable
private fun SectionPlaceholder(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().background(AppColors.SurfaceSoft, AppShapes.ListCard).padding(AppSpacing.Space20),
        style = AppTypography.Body,
        color = AppColors.Ink600,
        textAlign = TextAlign.Center
    )
}
