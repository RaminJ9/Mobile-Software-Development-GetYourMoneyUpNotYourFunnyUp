package com.example.getyourmoneyupnotyourfunnyup.ui.screens.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.repository.GroupRepository
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.ActivityData
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.HomeData
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The 5 destinations of the authenticated bottom navigation bar (HiFi). */
enum class AppTab(val label: String, val icon: DashboardIcon) {
    Home("Home", DashboardIcon.Home),
    Groups("Groups", DashboardIcon.Users),
    Scan("Scan", DashboardIcon.ScanReceipt),
    Activity("Activity", DashboardIcon.Clock),
    Profile("Profile", DashboardIcon.Person)
}

/** Screens pushed on top of a tab (they hide the bottom bar, like the HiFi). */
private sealed interface Overlay {
    data object CreateGroup : Overlay
    data class GroupDetail(val groupId: String) : Overlay
}

/**
 * Authenticated shell: bottom navigation + per-tab screens, built on top of the existing
 * Home/Activity (Person 5) and Groups/Members (Person 2) routes. All data is read from the
 * local database for [user], so the shell works offline.
 *
 * [profileContent] is the existing profile/counter screen (with logout) owned by Person 1.
 */
@Composable
fun MainShell(
    user: User,
    profileContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    database: AppDatabase = AppDatabase.getInstance(LocalContext.current)
) {
    val session: ShellSession = viewModel(key = "session-${user.id}", factory = shellFactory { ShellSession(database, user) })
    var tab by rememberSaveable(user.id) { mutableStateOf(AppTab.Home) }
    var overlayGroup by rememberSaveable(user.id) { mutableStateOf<String?>(null) }
    var creating by rememberSaveable(user.id) { mutableStateOf(false) }
    var createRun by rememberSaveable(user.id) { mutableIntStateOf(0) }
    val overlay: Overlay? = when {
        creating -> Overlay.CreateGroup
        overlayGroup != null -> Overlay.GroupDetail(overlayGroup!!)
        else -> null
    }

    BackHandler(enabled = overlay != null || tab != AppTab.Home) {
        when {
            creating -> creating = false
            overlayGroup != null -> overlayGroup = null
            else -> tab = AppTab.Home
        }
    }

    val openGroup: (String) -> Unit = { overlayGroup = it }
    val createGroup: () -> Unit = { createRun++; creating = true }

    Column(modifier.fillMaxSize().background(AppColors.White)) {
        Box(Modifier.weight(1f)) {
            when (overlay) {
                Overlay.CreateGroup -> {
                    val vm: CreateGroupViewModel = viewModel(
                        key = "create-${user.id}-$createRun",
                        factory = shellFactory {
                            CreateGroupViewModel(user, session.repository, session.repository, session.repository.observeKnownContacts())
                        }
                    )
                    CreateGroupRoute(vm, onBack = { creating = false }, onCreated = { id ->
                        creating = false
                        tab = AppTab.Groups
                        overlayGroup = id
                    })
                }
                is Overlay.GroupDetail -> GroupDetailRoute(
                    session,
                    overlay.groupId,
                    viewModel(key = "members-${user.id}-${overlay.groupId}", factory = shellFactory {
                        GroupMembersViewModel(session.repository.observeMembers(overlay.groupId), session.repository, session.repository, session.repository)
                    }),
                    onBack = { overlayGroup = null }
                )
                null -> when (tab) {
                    AppTab.Home -> HomeRoute(
                        viewModel(key = "home-${user.id}", factory = shellFactory {
                            HomeViewModel(session.groupsFlow.map { groups -> HomeData(user, groups) })
                        }),
                        onOpenActivity = { tab = AppTab.Activity },
                        onOpenGroups = { tab = AppTab.Groups },
                        onCreateGroup = createGroup,
                        onJoinGroup = { tab = AppTab.Groups },
                        onOpenGroup = openGroup
                    )
                    AppTab.Groups -> GroupsRoute(
                        viewModel(key = "groups-${user.id}", factory = shellFactory {
                            GroupsViewModel(session.repository.observeGroupsList(), session.repository)
                        }),
                        createGroup, openGroup
                    )
                    AppTab.Scan -> ComingSoon(
                        "Snap a receipt",
                        "Receipt scanning is the next part of the app. Until then, add groups and members from the Groups tab."
                    )
                    // Activity history isn't recorded yet (database owner): `null` = unavailable, not "empty".
                    AppTab.Activity -> ActivityRoute(viewModel(key = "activity-${user.id}", factory = shellFactory {
                        ActivityViewModel(flowOf(ActivityData(user.id, events = null)))
                    }))
                    AppTab.Profile -> profileContent(Modifier.fillMaxSize())
                }
            }
        }
        if (overlay == null) BottomNavBar(tab, onSelect = { tab = it })
    }
}

/** Account-scoped repository and flows, kept across recompositions and rotation. */
private class ShellSession(database: AppDatabase, private val user: User) : ViewModel() {
    val repository = GroupRepository(database, user)
    val groupsFlow = repository.observeGroups()

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

private data class GroupHeader(
    val name: String,
    val description: String?,
    val colourIndex: Int,
    val memberCount: Int,
    val createdBy: String
)

private enum class GroupSection(val label: String) { Activity("Activity"), Balances("Balances"), Members("Members") }

/** S8 group hub: header + segmented tabs. The Members tab is Person 2's [GroupMembersContent]. */
@Composable
private fun GroupDetailRoute(session: ShellSession, groupId: String, membersVm: GroupMembersViewModel, onBack: () -> Unit) {
    val header by remember(groupId) { session.groupHeader(groupId) }.collectAsStateWithLifecycle(null)
    val membersState by membersVm.state.collectAsStateWithLifecycle()
    var section by rememberSaveable(groupId) { mutableStateOf(GroupSection.Members) }
    val onMembersAction = rememberMembersActions(membersVm)

    Column(Modifier.fillMaxSize().background(AppColors.White).verticalScroll(rememberScrollState())) {
        BackTopBar(header?.name ?: "Group", onBack)
        Column(
            Modifier.padding(start = AppSpacing.Space16, end = AppSpacing.Space16, bottom = AppSpacing.Space24),
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
                GroupSection.Activity -> SectionPlaceholder("Expenses and payments for this group will show up here.")
                GroupSection.Balances -> SectionPlaceholder("Balances and who pays whom will show up here.")
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

@Composable
private fun ComingSoon(title: String, text: String) {
    Column(
        Modifier.fillMaxSize().background(AppColors.White).padding(AppSpacing.Space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).background(AppColors.Green100, AppShapes.AvatarTile), contentAlignment = Alignment.Center) {
            DashboardSymbol(DashboardIcon.ScanReceipt, Modifier.size(32.dp), AppColors.Green700)
        }
        Spacer(Modifier.height(AppSpacing.Space16))
        Text(title, style = AppTypography.Display, textAlign = TextAlign.Center)
        Spacer(Modifier.height(AppSpacing.Space8))
        Text(text, style = AppTypography.Body, color = AppColors.Ink600, textAlign = TextAlign.Center)
    }
}

/** 80dp bottom bar with the elevated 64dp green Scan button in the middle. */
@Composable
fun BottomNavBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(AppColors.White)) {
        HorizontalDivider(color = AppColors.LineSoft)
        Row(
            Modifier.fillMaxWidth().height(AppTouchTargets.BottomNavHeight).padding(horizontal = AppSpacing.Space8),
            verticalAlignment = Alignment.Bottom
        ) {
            AppTab.entries.forEach { item ->
                val on = item == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(role = Role.Tab, onClickLabel = item.label) { onSelect(item) }
                        .semantics { this.selected = on }
                        .padding(bottom = AppSpacing.Space12),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (item == AppTab.Scan) {
                        Box(
                            Modifier.offset(y = (-6).dp).size(AppTouchTargets.ScanFabSize)
                                .shadow(8.dp, AppShapes.Circle)
                                .background(AppColors.Green700, AppShapes.Circle)
                                .border(4.dp, AppColors.White, AppShapes.Circle),
                            contentAlignment = Alignment.Center
                        ) { DashboardSymbol(item.icon, Modifier.size(28.dp), AppColors.White, "Scan receipt") }
                    } else {
                        Box(
                            Modifier.background(if (on) AppColors.Green100 else AppColors.White, AppShapes.Chip)
                                .padding(horizontal = 14.dp, vertical = 2.dp)
                        ) { DashboardSymbol(item.icon, colour = if (on) AppColors.Green700 else AppColors.Ink600) }
                    }
                    Spacer(Modifier.height(AppSpacing.Space4))
                    Text(
                        item.label,
                        style = AppTypography.Label,
                        color = if (on || item == AppTab.Scan) AppColors.Green700 else AppColors.Ink600
                    )
                }
            }
        }
    }
}

private fun shellFactory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
