package com.example.getyourmoneyupnotyourfunnyup.ui.screens.shell

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.DashboardDataSource
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BalanceChip
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BottomNavBar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.DashboardAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.DashboardButton
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile.ProfileRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppSpacing
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTouchTargets
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileActions
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Authenticated 5-Tab Bottom Navigation Shell (`docs/HiFi.html` & `docs/navigation_model.drawio.xml`).
 *
 * Hosts the 5 primary destinations:
 * 1. [AppTab.Home] (`S3 · Home`) — default landing screen after login or account creation
 * 2. [AppTab.Groups] (`S4 · Groups`)
 * 3. [AppTab.Scan] (`Scan · Receipt scanner`)
 * 4. [AppTab.Activity] (`S6 · Activity feed`)
 * 5. [AppTab.Profile] (`S7 / S7-edit · Profile, dietary rules, notifications & logout`)
 */
@Composable
fun AuthenticatedShell(
    initialUser: User,
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    authRepository: AuthRepository,
    dashboardDataSource: DashboardDataSource,
    onUserUpdated: (User) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val observedUser by remember(initialUser.id) {
        authRepository.observeUser(initialUser.id)
    }.collectAsState(initial = initialUser)
    val activeUser = observedUser ?: initialUser

    LaunchedEffect(activeUser) {
        onUserUpdated(activeUser)
    }

    // Real-time Firestore listener for `/users/{uid}` keeping local SQLite in sync
    DisposableEffect(activeUser.id) {
        val registration = authRepository.listenToUserDocument(
            uid = activeUser.id,
            fallbackEmail = activeUser.email,
            fallbackName = activeUser.displayName,
            onError = { /* Offline fallback handled by local SQLite SSOT */ }
        )
        onDispose {
            registration.remove()
        }
    }

    val homeViewModel = remember(activeUser.id) {
        HomeViewModel(
            source = dashboardDataSource.observeHomeData(activeUser),
            settlements = dashboardDataSource.settlementActions
        )
    }

    val activityViewModel = remember(activeUser.id) {
        ActivityViewModel(
            source = dashboardDataSource.observeActivityData(activeUser),
            settlements = dashboardDataSource.settlementActions,
            retries = dashboardDataSource.syncRetryActions
        )
    }

    val profileViewModel = remember(activeUser.id) {
        ProfileViewModel(
            userFlow = authRepository.observeUser(activeUser.id),
            actions = ProfileActions.fromAuthRepository(authRepository),
            initialUser = activeUser
        )
    }

    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val hasUnreadActivity = (homeState as? HomeUiState.Success)?.data?.hasUnreadActivity == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                AppTab.Home -> {
                    HomeRoute(
                        viewModel = homeViewModel,
                        onOpenActivity = { onSelectTab(AppTab.Activity) },
                        onOpenGroups = { onSelectTab(AppTab.Groups) },
                        onCreateGroup = { onSelectTab(AppTab.Groups) },
                        onJoinGroup = { onSelectTab(AppTab.Groups) },
                        onOpenGroup = { onSelectTab(AppTab.Groups) }
                    )
                }

                AppTab.Groups -> {
                    val groups = (homeState as? HomeUiState.Success)?.data?.groups.orEmpty()
                    GroupsTabPlaceholder(
                        groups = groups,
                        onCreateGroupClick = {}
                    )
                }

                AppTab.Scan -> {
                    ScanTabPlaceholder(
                        onBackToHome = { onSelectTab(AppTab.Home) }
                    )
                }

                AppTab.Activity -> {
                    ActivityRoute(viewModel = activityViewModel)
                }

                AppTab.Profile -> {
                    ProfileRoute(
                        viewModel = profileViewModel,
                        onLogout = onLogout
                    )
                }
            }
        }

        BottomNavBar(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            hasUnreadActivity = hasUnreadActivity
        )
    }
}

@Composable
internal fun GroupsTabPlaceholder(
    groups: List<DashboardGroup>,
    onCreateGroupClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
            .padding(
                start = AppSpacing.ScreenHorizontalPadding,
                end = AppSpacing.ScreenHorizontalPadding,
                top = 28.dp,
                bottom = AppSpacing.Space24
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space16)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Groups",
                modifier = Modifier.weight(1f),
                style = AppTypography.ScreenTitleLarge,
                color = AppColors.Ink900
            )
            DashboardButton(
                text = "+ New group",
                onClick = onCreateGroupClick,
                container = AppColors.Green100,
                content = AppColors.Green700
            )
        }

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = AppShapes.ListCard,
                    color = AppColors.SurfaceSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.Space24),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
                    ) {
                        Text(
                            text = "No groups yet",
                            style = AppTypography.Section,
                            color = AppColors.Ink900
                        )
                        Text(
                            text = "Create a group to start splitting expenses with friends.",
                            style = AppTypography.Body,
                            color = AppColors.Ink600,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
            ) {
                items(groups, key = { it.group.id }) { dashGroup ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.ListCard,
                        color = AppColors.White,
                        border = BorderStroke(1.dp, AppColors.LineSoft)
                    ) {
                        Row(
                            modifier = Modifier.padding(AppSpacing.Space12),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
                        ) {
                            DashboardAvatar(
                                name = dashGroup.group.name,
                                colour = AppColors.groupColorAt(dashGroup.colourIndex)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dashGroup.group.name,
                                    style = AppTypography.ItemTitle,
                                    color = AppColors.Ink900
                                )
                                Text(
                                    text = "${dashGroup.group.members.size} members",
                                    style = AppTypography.Caption,
                                    color = AppColors.Ink600
                                )
                            }
                            BalanceChip(balanceCents = 0L)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScanTabPlaceholder(
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
            .padding(AppSpacing.Space24),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(AppColors.Green100, AppShapes.AvatarTile),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⌗",
                style = AppTypography.Display,
                color = AppColors.Green700
            )
        }
        Text(
            text = "Snap a receipt, skip the typing",
            style = AppTypography.Display,
            color = AppColors.Ink900,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AppSpacing.Space16, bottom = AppSpacing.Space8)
        )
        Text(
            text = "Everything is read on your phone, so it works even without internet.",
            style = AppTypography.Body,
            color = AppColors.Ink600,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = AppSpacing.Space24)
        )
        DashboardButton(
            text = "Back to Home",
            onClick = onBackToHome,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppTouchTargets.PrimaryButtonHeight),
            container = AppColors.Green700,
            content = AppColors.White
        )
    }
}

@Preview(showBackground = true, name = "Authenticated Shell Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Authenticated Shell Dark"
)
@Composable
private fun AuthenticatedShellPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        val sampleUser = User(
            id = "usr-1",
            email = "victor@example.dk",
            displayName = "Victor Munk",
            phoneNumber = "+45 12 34 56 78"
        )
        val profileViewModel = remember {
            ProfileViewModel(
                userFlow = MutableStateFlow(sampleUser),
                initialUser = sampleUser
            )
        }
        Surface(color = AppColors.White) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    ProfileRoute(
                        viewModel = profileViewModel,
                        onLogout = {}
                    )
                }
                BottomNavBar(
                    selectedTab = AppTab.Profile,
                    onSelectTab = {}
                )
            }
        }
    }
}
