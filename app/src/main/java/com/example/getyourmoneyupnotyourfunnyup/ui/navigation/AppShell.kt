package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BottomNavBar
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.ActivityData
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.HomeData
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.CreateGroupRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.GroupsRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile.ProfileRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupMembersViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Navigation container for the signed-in app: the bottom bar tabs plus the group screens pushed on
 * top of them (Create group, Group detail), which hide the bottom bar like the HiFi.
 * Missing teammate data (activity events, scanning) is kept explicitly unavailable.
 *
 * [database] defaults to the app database; the debug sandbox passes an in-memory one.
 */
@Composable
fun AppShell(
    user: User,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    profileRepository: ProfileRepository? = null,
    database: AppDatabase? = null
) {
    val applicationContext = LocalContext.current.applicationContext
    // Removing this shell on logout also removes its tab selection and account-scoped ViewModels.
    key(user.id) {
        var selected by rememberSaveable { mutableStateOf(AppTab.Home) }
        var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
        var creatingGroup by rememberSaveable { mutableStateOf(false) }
        // Bumped on every "Create group" so each visit starts with an empty form.
        var createRun by rememberSaveable { mutableIntStateOf(0) }
        val owner = remember {
            object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

        val groups: GroupsSession = viewModel(
            viewModelStoreOwner = owner,
            factory = remember(user.id, database) {
                viewModelFactory {
                    initializer { GroupsSession(database ?: AppDatabase.getInstance(applicationContext), user) }
                }
            }
        )
        val activityFactory = remember(user.id) {
            viewModelFactory {
                initializer { ActivityViewModel(flowOf(ActivityData(userId = user.id, events = null))) }
            }
        }
        val activityViewModel: ActivityViewModel = viewModel(viewModelStoreOwner = owner, factory = activityFactory)
        val profileFactory = remember(user.id, profileRepository) {
            viewModelFactory { initializer { ProfileViewModel(user, profileRepository) } }
        }
        val profileViewModel: ProfileViewModel = viewModel(viewModelStoreOwner = owner, factory = profileFactory)

        val openGroup: (String) -> Unit = { openGroupId = it }
        val createGroup: () -> Unit = { createRun++; creatingGroup = true }
        val showingOverlay = creatingGroup || openGroupId != null

        BackHandler(enabled = showingOverlay || selected != AppTab.Home) {
            when {
                creatingGroup -> creatingGroup = false
                openGroupId != null -> openGroupId = null
                else -> selected = AppTab.Home
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            // MainActivity owns system insets; this scaffold adds only the bottom navigation space.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = AppColors.White,
            bottomBar = {
                if (!showingOverlay) BottomNavBar(selectedTab = selected, onSelectTab = { selected = it })
            }
        ) { padding ->
            val contentModifier = Modifier.padding(padding).fillMaxSize()
            val groupId = openGroupId
            when {
                creatingGroup -> {
                    val createViewModel: CreateGroupViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        key = "create-group-$createRun",
                        factory = viewModelFactory {
                            initializer {
                                CreateGroupViewModel(user, groups.repository, groups.repository, groups.repository.observeKnownContacts())
                            }
                        }
                    )
                    CreateGroupRoute(
                        createViewModel,
                        onBack = { creatingGroup = false },
                        onCreated = { id ->
                            creatingGroup = false
                            selected = AppTab.Groups
                            openGroupId = id
                        },
                        modifier = contentModifier
                    )
                }
                groupId != null -> {
                    val membersViewModel: GroupMembersViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        key = "group-members-$groupId",
                        factory = viewModelFactory {
                            initializer {
                                GroupMembersViewModel(
                                    groups.repository.observeMembers(groupId),
                                    groups.repository,
                                    groups.repository,
                                    groups.repository
                                )
                            }
                        }
                    )
                    GroupDetailRoute(groups, groupId, membersViewModel, onBack = { openGroupId = null }, modifier = contentModifier)
                }
                else -> when (selected) {
                    AppTab.Home -> {
                        val homeViewModel: HomeViewModel = viewModel(
                            viewModelStoreOwner = owner,
                            factory = remember(user.id) {
                                viewModelFactory {
                                    initializer { HomeViewModel(groups.groupsFlow.map { HomeData(user, it) }) }
                                }
                            }
                        )
                        HomeRoute(
                            homeViewModel,
                            onOpenActivity = { selected = AppTab.Activity },
                            onOpenGroups = { selected = AppTab.Groups },
                            onCreateGroup = createGroup,
                            onJoinGroup = { selected = AppTab.Groups },
                            onOpenGroup = openGroup,
                            modifier = contentModifier
                        )
                    }
                    AppTab.Groups -> {
                        val groupsViewModel: GroupsViewModel = viewModel(
                            viewModelStoreOwner = owner,
                            factory = remember(user.id) {
                                viewModelFactory {
                                    initializer { GroupsViewModel(groups.repository.observeGroupsList(), groups.repository) }
                                }
                            }
                        )
                        GroupsRoute(groupsViewModel, onCreateGroup = createGroup, onOpenGroup = openGroup, modifier = contentModifier)
                    }
                    AppTab.Activity -> ActivityRoute(activityViewModel, modifier = contentModifier)
                    AppTab.Scan -> UnavailableDestination("Scan", "Receipt scanning is not connected yet.", modifier = contentModifier)
                    AppTab.Profile -> ProfileRoute(profileViewModel, onLogout, modifier = contentModifier)
                }
            }
        }
    }
}

@Composable
private fun UnavailableDestination(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(AppSpacing.Space16), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space16)) {
        Text(title, style = AppTypography.ScreenTitleLarge, color = AppColors.Ink900)
        Text(message, style = AppTypography.Body, color = AppColors.Ink600)
    }
}

@Preview(showBackground = true, name = "Shell light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Shell dark")
@Composable
private fun AppShellPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme { AppShell(User("preview", "ramin@example.com", "Ramin"), {}) }
}
