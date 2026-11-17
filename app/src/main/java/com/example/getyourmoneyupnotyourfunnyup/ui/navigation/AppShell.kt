package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

private enum class ShellTab { Home, Groups, Scan, Activity, Profile }

/** Navigation container only. Missing teammate data is kept explicitly unavailable. */
@Composable
fun AppShell(user: User, onLogout: () -> Unit, modifier: Modifier = Modifier, profileRepository: ProfileRepository? = null) {
    // Removing this shell on logout also removes its tab selection and account-scoped ViewModels.
    key(user.id) {
        var selected by rememberSaveable { mutableStateOf(ShellTab.Home) }
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
        BackHandler(enabled = selected != ShellTab.Home) { selected = ShellTab.Home }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            // MainActivity owns system insets; this scaffold adds only the bottom navigation space.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = AppColors.White,
            bottomBar = { ShellBottomBar(selected, { selected = it }) }
        ) { padding ->
            val contentModifier = Modifier.padding(padding).fillMaxSize()
            when (selected) {
                ShellTab.Home -> Column(contentModifier) {
                    Column(Modifier.padding(AppSpacing.Space16)) {
                        Text("Home", style = AppTypography.ScreenTitleLarge, color = AppColors.Ink900)
                        Text("Hi, ${user.displayName}", style = AppTypography.Body, color = AppColors.Ink600)
                    }
                    HomeScreen(
                        state = HomeUiState.Error("Home data unavailable. Your groups and balances are not connected yet."),
                        onAction = {},
                        onOpenActivity = { selected = ShellTab.Activity },
                        onOpenGroups = { selected = ShellTab.Groups },
                        onCreateGroup = { selected = ShellTab.Groups },
                        onJoinGroup = { selected = ShellTab.Groups },
                        onOpenGroup = { selected = ShellTab.Groups },
                        modifier = Modifier.weight(1f)
                    )
                }
                ShellTab.Activity -> ActivityRoute(activityViewModel, modifier = contentModifier)
                ShellTab.Groups -> UnavailableDestination("Groups", "Groups are not connected yet.", modifier = contentModifier)
                ShellTab.Scan -> UnavailableDestination("Scan", "Receipt scanning is not connected yet.", modifier = contentModifier)
                ShellTab.Profile -> ProfileRoute(profileViewModel, onLogout, modifier = contentModifier)
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

@Composable
private fun ShellBottomBar(selected: ShellTab, onSelect: (ShellTab) -> Unit) {
    Surface(color = AppColors.White, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().height(AppTouchTargets.BottomNavHeight), verticalAlignment = Alignment.CenterVertically) {
            ShellTab.entries.forEach { tab ->
                val active = selected == tab
                val colour = if (active) AppColors.Green700 else AppColors.Ink600
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                        .semantics { contentDescription = "${tab.name} tab" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (tab == ShellTab.Scan) {
                        Box(Modifier.size(AppTouchTargets.ScanFabSize).background(AppColors.Green700, AppShapes.Circle), contentAlignment = Alignment.Center) {
                            TabIcon(tab, AppColors.White)
                        }
                    } else {
                        TabIcon(tab, colour)
                        Spacer(Modifier.height(AppSpacing.Space4))
                    }
                    Text(tab.name, style = AppTypography.Label, color = colour)
                }
            }
        }
    }
}

/** Small outline icons drawn locally, avoiding a new icon dependency. */
@Composable
private fun TabIcon(tab: ShellTab, colour: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(width = size.width * 1.8f / 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(vararg points: Float) {
            val p = Path().apply {
                moveTo(points[0] * size.width / 24, points[1] * size.height / 24)
                for (i in 2 until points.size step 2) lineTo(points[i] * size.width / 24, points[i + 1] * size.height / 24)
            }
            drawPath(p, colour, style = stroke)
        }
        when (tab) {
            ShellTab.Home -> { path(3f, 10f, 12f, 3f, 21f, 10f); path(5f, 9f, 5f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 19f, 21f, 19f, 9f) }
            ShellTab.Groups -> {
                drawCircle(colour, size.width * 3 / 24, Offset(size.width * 9 / 24, size.height * 7 / 24), style = stroke)
                path(3f, 21f, 3f, 17f, 6f, 14f, 12f, 14f, 15f, 17f, 15f, 21f)
                path(16f, 4f, 19f, 6f, 19f, 9f, 16f, 11f); path(18f, 14f, 21f, 17f, 21f, 21f)
            }
            ShellTab.Scan -> { path(3f, 8f, 7f, 8f, 9f, 5f, 15f, 5f, 17f, 8f, 21f, 8f, 21f, 20f, 3f, 20f, 3f, 8f); drawCircle(colour, size.width * 4 / 24, Offset(size.width / 2, size.height * 14 / 24), style = stroke) }
            ShellTab.Activity -> { path(5f, 3f, 19f, 3f, 19f, 21f, 5f, 21f, 5f, 3f); path(8f, 8f, 16f, 8f); path(8f, 12f, 16f, 12f); path(8f, 16f, 13f, 16f) }
            ShellTab.Profile -> { drawCircle(colour, size.width * 4 / 24, Offset(size.width / 2, size.height * 7 / 24), style = stroke); path(4f, 21f, 4f, 18f, 8f, 14f, 16f, 14f, 20f, 18f, 20f, 21f) }
        }
    }
}

@Preview(showBackground = true, name = "Shell light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Shell dark")
@Composable
private fun AppShellPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme { AppShell(User("preview", "ramin@example.com", "Ramin"), {}) }
}
