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
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.MlKitReceiptScanner
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptPhotoStore
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ExpenseRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LocalScanSource
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectivityObserver
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LocalBalanceRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LocalPaymentRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LocalExpenseRemoval
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LocalReminderRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.DashboardDataSource
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ReceiptRepository
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.BalanceViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ExpenseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BottomNavBar
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.CreateGroupRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.GroupsRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile.ProfileRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.scan.ScanRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupMembersViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.flow.map

/**
 * Navigation container for the signed-in app: the bottom bar tabs plus the screens pushed on top of
 * them (Create group, Group detail, Scan), which hide the bottom bar like the HiFi.
 * Financial snapshots and reminders adapt the shared ledger.
 *
 * [database] defaults to the app database; the debug sandbox passes an in-memory one.
 */
@Composable
fun AppShell(
    user: User,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    profileRepository: ProfileRepository? = null,
    database: AppDatabase? = null,
    currentAccountId: () -> String? = { Firebase.auth.currentUser?.uid },
    cloudReadError: String? = null
) {
    val applicationContext = LocalContext.current.applicationContext
    val localDatabase = remember(database, applicationContext) { database ?: AppDatabase.getInstance(applicationContext) }
    val accountProvider = rememberUpdatedState(currentAccountId)
    // Removing this shell on logout also removes its tab selection and account-scoped ViewModels.
    key(user.id) {
        var selected by rememberSaveable { mutableStateOf(AppTab.Home) }
        var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
        var creatingGroup by rememberSaveable { mutableStateOf(false) }
        // Bumped on every "Create group" so each visit starts with an empty form.
        var createRun by rememberSaveable { mutableIntStateOf(0) }
        // The scan flow is full screen on top of the current screen, optionally for one group.
        var scanning by rememberSaveable { mutableStateOf(false) }
        var scanGroupId by rememberSaveable { mutableStateOf<String?>(null) }
        val owner = remember {
            object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

        val groups: GroupsSession = viewModel(
            viewModelStoreOwner = owner,
            factory = remember(user.id, database) {
                viewModelFactory {
                    initializer { GroupsSession(localDatabase, user) }
                }
            }
        )
        val dashboard = remember(user.id, localDatabase) { DashboardDataSource(localDatabase, scheduleRetry = { id ->
            (applicationContext as? com.example.getyourmoneyupnotyourfunnyup.MoneyApplication)?.syncScheduler?.scheduleManual(id)
        }) { accountProvider.value() } }
        val activityFactory = remember(user.id, dashboard) {
            viewModelFactory {
                initializer { ActivityViewModel(dashboard.observeActivityData(user), dashboard.settlementActions, dashboard.syncRetryActions,
                    saveRead = { dashboard.markRead(user.id, it) }) }
            }
        }
        val activityViewModel: ActivityViewModel = viewModel(viewModelStoreOwner = owner, factory = activityFactory)
        val profileFactory = remember(user.id, profileRepository) {
            viewModelFactory { initializer { ProfileViewModel(user, profileRepository) } }
        }
        val profileViewModel: ProfileViewModel = viewModel(viewModelStoreOwner = owner, factory = profileFactory)

        val openGroup: (String) -> Unit = { openGroupId = it }
        val createGroup: () -> Unit = { createRun++; creatingGroup = true }
        val startScan: (String?) -> Unit = { scanGroupId = it; scanning = true }
        val showingOverlay = creatingGroup || openGroupId != null || scanning

        BackHandler(enabled = showingOverlay || selected != AppTab.Home) {
            when {
                scanning -> scanning = false
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
                if (!showingOverlay) BottomNavBar(
                    selectedTab = selected,
                    onSelectTab = { if (it == AppTab.Scan) startScan(null) else selected = it }
                )
            }
        ) { padding ->
            val contentModifier = Modifier.padding(padding).fillMaxSize()
            val groupId = openGroupId
            when {
                scanning -> {
                    // Scoped to this visit: leaving the flow clears its ViewModel, which deletes an unsaved photo.
                    val scanOwner = remember {
                        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
                    }
                    DisposableEffect(scanOwner) { onDispose { scanOwner.viewModelStore.clear() } }
                    val photoStore = remember(applicationContext) { ReceiptPhotoStore(applicationContext) }
                    val scanViewModel: ScanViewModel = viewModel(
                        viewModelStoreOwner = scanOwner,
                        factory = viewModelFactory {
                            initializer {
                                ScanViewModel(
                                    source = LocalScanSource(localDatabase, groups.repository, ExpenseRepository(localDatabase)),
                                    meId = user.id,
                                    initialGroupId = scanGroupId,
                                    scanner = MlKitReceiptScanner(applicationContext),
                                    photos = photoStore,
                                    online = ConnectivityObserver(applicationContext).observe().map { it != ConnectionState.Offline },
                                )
                            }
                        }
                    )
                    ScanRoute(
                        scanViewModel,
                        photoStore,
                        onClose = { scanning = false },
                        // MainActivity already keeps the app clear of the system bars.
                        modifier = contentModifier.consumeWindowInsets(WindowInsets.systemBars)
                    )
                }
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
                    // Financial observations and pending UI actions end when this group is closed.
                    val balanceOwner = remember(groupId) {
                        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
                    }
                    DisposableEffect(balanceOwner) { onDispose { balanceOwner.viewModelStore.clear() } }
                    val balancesViewModel: BalanceViewModel = viewModel(
                        viewModelStoreOwner = balanceOwner,
                        factory = remember(groupId, localDatabase, user.id) {
                            viewModelFactory {
                                initializer {
                                    BalanceViewModel(
                                        user.id, groupId,
                                        LocalBalanceRepository(localDatabase) { accountProvider.value() },
                                        paymentRepository = LocalPaymentRepository(
                                            database = localDatabase, currentAccountId = { accountProvider.value() }),
                                        reminderRepository = LocalReminderRepository(localDatabase) { accountProvider.value() }
                                    )
                                }
                            }
                        }
                    )
                    val expensesViewModel: ExpenseViewModel = viewModel(
                        viewModelStoreOwner = balanceOwner,
                        factory = remember(groupId, localDatabase, user.id) {
                            viewModelFactory {
                                initializer { ExpenseViewModel(user.id, groupId,
                                    LocalBalanceRepository(localDatabase) { accountProvider.value() },
                                    removal = LocalExpenseRemoval(localDatabase) { accountProvider.value() },
                                    receipts = ReceiptRepository(applicationContext, localDatabase, { accountProvider.value() })) }
                            }
                        }
                    )
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
                    GroupDetailRoute(groups, groupId, membersViewModel, balancesViewModel, expensesViewModel,
                        onBack = { openGroupId = null }, onAddExpense = { startScan(groupId) }, modifier = contentModifier)
                }
                else -> when (selected) {
                    AppTab.Home -> {
                        val homeViewModel: HomeViewModel = viewModel(
                            viewModelStoreOwner = owner,
                            factory = remember(user.id) {
                                viewModelFactory {
                                    initializer { HomeViewModel(dashboard.observeHomeData(user), dashboard.settlementActions) }
                                }
                            }
                        )
                        Column(contentModifier) {
                            cloudReadError?.let { Text(it, Modifier.padding(AppSpacing.Space16), style = AppTypography.Caption, color = AppColors.Ink600) }
                            HomeRoute(
                            homeViewModel,
                            onOpenActivity = { selected = AppTab.Activity },
                            onOpenGroups = { selected = AppTab.Groups },
                            onCreateGroup = createGroup,
                            onJoinGroup = { selected = AppTab.Groups },
                            onOpenGroup = openGroup,
                            modifier = Modifier.weight(1f)
                        )
                        }
                    }
                    AppTab.Groups -> {
                        val groupsViewModel: GroupsViewModel = viewModel(
                            viewModelStoreOwner = owner,
                            factory = remember(user.id) {
                                viewModelFactory {
                                    initializer { GroupsViewModel(groups.repository.observeGroupsList(dashboard.observeHomeData(user).map { home -> home.finances?.balancesByGroup?.mapValues { it.value.netBalanceCents } }), groups.repository) }
                                }
                            }
                        )
                        GroupsRoute(groupsViewModel, onCreateGroup = createGroup, onOpenGroup = openGroup, modifier = contentModifier)
                    }
                    AppTab.Activity -> ActivityRoute(activityViewModel, modifier = contentModifier)
                    // Scan opens as a full-screen flow above (see startScan), never as a tab.
                    AppTab.Scan -> Unit
                    AppTab.Profile -> ProfileRoute(profileViewModel, onLogout, modifier = contentModifier)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Shell light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Shell dark")
@Composable
private fun AppShellPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme { AppShell(User("preview", "ramin@example.com", "Ramin"), {}) }
}
