package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.getyourmoneyupnotyourfunnyup.appContainer
import com.example.getyourmoneyupnotyourfunnyup.data.repository.SyncEvent
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BottomNavBar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.MainTab
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.AuthRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.group.GroupRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.CreateGroupRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.GroupsRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile.ProfileRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.scan.ScanRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthMode
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SessionViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel

/** App entry point: session, navigation, bottom bar and the "synced" snackbar. */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val session = appViewModel { SessionViewModel(it.authRepository, it.ledgerRepository) }
    val navigator: AppNavigator = viewModel(
        factory = viewModelFactory {
            initializer { AppNavigator(if (session.startSignedIn) Destination.Home else Destination.Login) }
        },
    )
    val ledger = LocalContext.current.appContainer.ledgerRepository
    val snackbar = remember { SnackbarHostState() }
    val currentDestination = navigator.current.destination
    // The scan confirmation shows its own sync state, so the snackbar is only used elsewhere.
    val showSyncSnackbar by rememberUpdatedState(
        currentDestination != Destination.Login && currentDestination != Destination.Register &&
            currentDestination !is Destination.Scan,
    )

    // Tell the user when a queued change reaches the server (Reliability: visible sync feedback).
    LaunchedEffect(ledger) {
        ledger.syncEvents.collect { event ->
            if (event is SyncEvent.Synced && showSyncSnackbar && ledger.snapshot.value.notificationPrefs.syncUpdates) {
                val message = event.firstTitle?.let { "Synced · “$it” is saved for everyone" }
                    ?: "Synced · your changes are saved for everyone"
                snackbar.showSnackbar(message)
            }
        }
    }

    Box(modifier.fillMaxSize().background(AppColors.White)) {
        AppNavHost(navigator) { destination ->
            when (destination) {
                Destination.Login, Destination.Register -> AuthRoute(
                    mode = if (destination == Destination.Login) AuthMode.Login else AuthMode.Register,
                    onAuthenticated = { user, phone ->
                        session.onAuthenticated(user, phone)
                        navigator.resetTo(Destination.Home)
                    },
                    onSwitchMode = {
                        if (destination == Destination.Login) navigator.navigate(Destination.Register) else navigator.pop()
                    },
                )
                Destination.CreateGroup -> CreateGroupRoute(
                    onBack = { navigator.pop() },
                    onCreated = { navigator.replace(Destination.GroupDetail(it)) },
                )
                is Destination.Scan -> ScanRoute(initialGroupId = destination.groupId, onClose = { navigator.pop() })
                else -> MainScaffold(navigator, destination) {
                    when (destination) {
                        Destination.Home -> HomeRoute(
                            onOpenGroup = { navigator.navigate(Destination.GroupDetail(it)) },
                            onSeeAllGroups = { navigator.selectTab(MainTab.Groups) },
                            onCreateGroup = { navigator.navigate(Destination.CreateGroup) },
                            onOpenActivity = { navigator.selectTab(MainTab.Activity) },
                        )
                        Destination.Groups -> GroupsRoute(
                            onOpenGroup = { navigator.navigate(Destination.GroupDetail(it)) },
                            onCreateGroup = { navigator.navigate(Destination.CreateGroup) },
                        )
                        Destination.Activity -> ActivityRoute()
                        Destination.Profile -> ProfileRoute(
                            onLogout = {
                                session.signOut()
                                navigator.resetTo(Destination.Login)
                            },
                        )
                        is Destination.GroupDetail -> GroupRoute(
                            groupId = destination.groupId,
                            onBack = { navigator.pop() },
                            onAddExpense = { navigator.navigate(Destination.Scan(it)) },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (currentDestination.tab != null) 96.dp else 16.dp),
        ) { data ->
            Snackbar(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                containerColor = AppColors.Ink900,
                contentColor = AppColors.White,
            ) { Text(data.visuals.message, style = jakarta(14, FontWeight.SemiBold)) }
        }
    }
}

/** Screen with the bottom navigation bar. */
@Composable
private fun MainScaffold(navigator: AppNavigator, destination: Destination, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        BottomNavBar(
            selected = destination.tab,
            onSelect = navigator::selectTab,
            onScan = { navigator.navigate(Destination.Scan((destination as? Destination.GroupDetail)?.groupId)) },
        )
    }
}

/**
 * Shows the top of the back stack. Each entry gets its own ViewModelStore and saveable state, and
 * system Back pops the stack.
 */
@Composable
private fun AppNavHost(navigator: AppNavigator, content: @Composable (Destination) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val entry = navigator.current
    val knownKeys = remember { mutableSetOf<String>() }
    val keys = navigator.backStack.map { it.key }
    LaunchedEffect(keys) {
        (knownKeys - keys.toSet()).forEach(holder::removeState)
        knownKeys.retainAll(keys.toSet())
        knownKeys.addAll(keys)
    }
    BackHandler(enabled = navigator.backStack.size > 1) { navigator.pop() }
    holder.SaveableStateProvider(entry.key) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
            content(entry.destination)
        }
    }
}
