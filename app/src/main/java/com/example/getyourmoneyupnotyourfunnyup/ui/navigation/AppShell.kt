package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile.ProfileRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.ActivityData
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import kotlinx.coroutines.flow.flowOf
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BottomNavBar


/** Navigation container only. Missing teammate data is kept explicitly unavailable. */
@Composable
fun AppShell(user: User, onLogout: () -> Unit, modifier: Modifier = Modifier, profileRepository: ProfileRepository? = null) {
    // Removing this shell on logout also removes its tab selection and account-scoped ViewModels.
    key(user.id) {
        var selected by rememberSaveable { mutableStateOf(AppTab.Home) }
        val owner = remember {
            object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
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
        BackHandler(enabled = selected != AppTab.Home) { selected = AppTab.Home }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            // MainActivity owns system insets; this scaffold adds only the bottom navigation space.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = AppColors.White,
            bottomBar = { BottomNavBar(selectedTab = selected, onSelectTab = { selected = it }) }
        ) { padding ->
            val contentModifier = Modifier.padding(padding).fillMaxSize()
            when (selected) {
                AppTab.Home -> Column(contentModifier) {
                    Column(Modifier.padding(AppSpacing.Space16)) {
                        Text("Home", style = AppTypography.ScreenTitleLarge, color = AppColors.Ink900)
                        Text("Hi, ${user.displayName}", style = AppTypography.Body, color = AppColors.Ink600)
                    }
                    HomeScreen(
                        state = HomeUiState.Error("Home data unavailable. Your groups and balances are not connected yet."),
                        onAction = {},
                        onOpenActivity = { selected = AppTab.Activity },
                        onOpenGroups = { selected = AppTab.Groups },
                        onCreateGroup = { selected = AppTab.Groups },
                        onJoinGroup = { selected = AppTab.Groups },
                        onOpenGroup = { selected = AppTab.Groups },
                        modifier = Modifier.weight(1f)
                    )
                }
                AppTab.Activity -> ActivityRoute(activityViewModel, modifier = contentModifier)
                AppTab.Groups -> UnavailableDestination("Groups", "Groups are not connected yet.", modifier = contentModifier)
                AppTab.Scan -> UnavailableDestination("Scan", "Receipt scanning is not connected yet.", modifier = contentModifier)
                AppTab.Profile -> ProfileRoute(profileViewModel, onLogout, modifier = contentModifier)
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
