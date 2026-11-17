package com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BottomNavBar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.DashboardIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.DashboardSymbol
import com.example.getyourmoneyupnotyourfunnyup.ui.components.OfflineBanner
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SyncStatusStrip
import com.example.getyourmoneyupnotyourfunnyup.ui.components.rememberConnectionState
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.AuthBanner
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.HiFiLabeledTextField
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppSpacing
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTouchTargets
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileViewModel

sealed interface ProfileAction {
    data object StartEdit : ProfileAction
    data object CancelEdit : ProfileAction
    data object SaveEdit : ProfileAction
    data class DraftName(val value: String) : ProfileAction
    data class DraftEmail(val value: String) : ProfileAction
    data class DraftPhone(val value: String) : ProfileAction
    data object ToggleAlcohol : ProfileAction
    data object ToggleMeat : ProfileAction
    data object ToggleNewExpenses : ProfileAction
    data object ToggleChangedExpenses : ProfileAction
    data object TogglePaymentReminders : ProfileAction
    data object ToggleSyncUpdates : ProfileAction
    data object ChangePassword : ProfileAction
    data object Logout : ProfileAction
}

/**
 * Full-screen S7 Profile & settings view including the 5-tab bottom navigation bar,
 * matching the standalone `Profile & settings` and `Edit profile` boards in `docs/HiFi.html`.
 */
@Composable
fun ProfileAndSettingsView(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: AppTab = AppTab.Profile,
    onSelectTab: (AppTab) -> Unit = {},
    hasUnreadActivity: Boolean = false
) {
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
            ProfileScreen(
                state = state,
                onAction = onAction
            )
        }
        BottomNavBar(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            hasUnreadActivity = hasUnreadActivity
        )
    }
}

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection = rememberConnectionState()
    val shown = (state as? ProfileUiState.Success)?.copy(
        isOffline = connection == ConnectionState.Offline
    ) ?: state

    ProfileScreen(
        state = shown,
        onAction = { action ->
            when (action) {
                ProfileAction.StartEdit -> viewModel.startEdit()
                ProfileAction.CancelEdit -> viewModel.cancelEdit()
                ProfileAction.SaveEdit -> viewModel.saveEdit()
                is ProfileAction.DraftName -> viewModel.onDraftNameChange(action.value)
                is ProfileAction.DraftEmail -> viewModel.onDraftEmailChange(action.value)
                is ProfileAction.DraftPhone -> viewModel.onDraftPhoneChange(action.value)
                ProfileAction.ToggleAlcohol -> viewModel.toggleExcludeAlcohol()
                ProfileAction.ToggleMeat -> viewModel.toggleExcludeMeat()
                ProfileAction.ToggleNewExpenses -> viewModel.toggleNewExpenses()
                ProfileAction.ToggleChangedExpenses -> viewModel.toggleChangedExpenses()
                ProfileAction.TogglePaymentReminders -> viewModel.togglePaymentReminders()
                ProfileAction.ToggleSyncUpdates -> viewModel.toggleSyncUpdates()
                ProfileAction.ChangePassword -> viewModel.requestPasswordChange()
                ProfileAction.Logout -> onLogout()
            }
        },
        modifier = modifier
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalContentColor provides AppColors.Ink900) {
        when (state) {
            ProfileUiState.Loading -> Box(
                modifier = modifier.fillMaxSize().background(AppColors.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Green700)
            }

            is ProfileUiState.Error -> Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(AppColors.White)
                    .padding(AppSpacing.Space16),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = AppTypography.Body,
                    color = AppColors.Ink600
                )
            }

            is ProfileUiState.Success -> ProfileContent(
                state = state,
                onAction = onAction,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState.Success,
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val user = state.user

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = AppSpacing.ScreenHorizontalPadding,
                    end = AppSpacing.ScreenHorizontalPadding,
                    top = 28.dp,
                    bottom = AppSpacing.ScreenBottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space24)
        ) {
            if (state.isOffline) {
                OfflineBanner(savedAtLabel = null, modifier = Modifier.fillMaxWidth())
            }

            if (user.syncStatus != SyncStatus.SYNCED) {
                SyncStatusStrip(
                    status = user.syncStatus,
                    modifier = Modifier.fillMaxWidth(),
                    automaticRetryAvailable = true
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                AuthBanner(message = state.errorMessage, isError = true)
            } else if (!state.infoMessage.isNullOrBlank()) {
                AuthBanner(message = state.infoMessage, isError = false)
            }

            // S7 · Profile & settings header OR S7-edit · Edit profile form
            if (!state.isEditing) {
                ProfileHeaderSection(
                    initial = state.initial,
                    name = user.displayName,
                    email = user.email,
                    phone = user.phoneNumber,
                    onEditClick = { onAction(ProfileAction.StartEdit) }
                )
            } else {
                EditProfileSection(
                    initial = state.initial,
                    draftName = state.draftName,
                    draftEmail = state.draftEmail,
                    draftPhone = state.draftPhone,
                    onNameChange = { onAction(ProfileAction.DraftName(it)) },
                    onEmailChange = { onAction(ProfileAction.DraftEmail(it)) },
                    onPhoneChange = { onAction(ProfileAction.DraftPhone(it)) },
                    onCancelClick = { onAction(ProfileAction.CancelEdit) },
                    onSaveClick = { onAction(ProfileAction.SaveEdit) }
                )
            }

            // Section: "Never charge me for" (Dietary & Item Rules)
            DietaryRulesSection(
                excludeAlcohol = user.dietaryRules.excludeAlcohol,
                excludeMeat = user.dietaryRules.excludeMeat,
                onToggleAlcohol = { onAction(ProfileAction.ToggleAlcohol) },
                onToggleMeat = { onAction(ProfileAction.ToggleMeat) }
            )

            // Section: "Notify me about" (4 Notification Switches)
            NotificationsSection(
                newExpenses = user.notificationSettings.newExpenses,
                changedExpenses = user.notificationSettings.changedExpenses,
                paymentReminders = user.notificationSettings.paymentReminders,
                syncUpdates = user.notificationSettings.syncUpdates,
                onToggleNewExpenses = { onAction(ProfileAction.ToggleNewExpenses) },
                onToggleChangedExpenses = { onAction(ProfileAction.ToggleChangedExpenses) },
                onTogglePaymentReminders = { onAction(ProfileAction.TogglePaymentReminders) },
                onToggleSyncUpdates = { onAction(ProfileAction.ToggleSyncUpdates) }
            )

            // Section: "Account" (Change password & Log out)
            AccountSection(
                onChangePasswordClick = { onAction(ProfileAction.ChangePassword) },
                onLogoutClick = { onAction(ProfileAction.Logout) }
            )
        }

        // Floating "Saved" status pill (`docs/HiFi.html` lines 306-311)
        AnimatedVisibility(
            visible = state.showSavedToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.Space20)
        ) {
            Surface(
                modifier = Modifier.shadow(
                    elevation = 10.dp,
                    shape = AppShapes.Pill
                ),
                shape = AppShapes.Pill,
                color = AppColors.Ink900,
                contentColor = AppColors.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.Space16, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
                ) {
                    DashboardSymbol(
                        icon = DashboardIcon.Check,
                        modifier = Modifier.size(16.dp),
                        colour = AppColors.Green300
                    )
                    Text(
                        text = "Saved",
                        style = AppTypography.BodyStrong,
                        color = AppColors.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    initial: String,
    name: String,
    email: String,
    phone: String,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space16)
    ) {
        // "You" avatar is always Green300 (#8FD6AA), 72dp circle
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(AppColors.Green300, AppShapes.Circle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = AppTypography.Amount,
                color = AppColors.Ink900
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = AppTypography.ScreenTitle.copy(fontSize = 22.sp),
                color = AppColors.Ink900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = email,
                style = AppTypography.BodyStrong.copy(fontWeight = FontWeight.Normal),
                color = AppColors.Ink600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (phone.isNotBlank()) {
                Text(
                    text = phone,
                    style = AppTypography.BodyStrong.copy(fontWeight = FontWeight.Normal),
                    color = AppColors.Ink600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Button(
            onClick = onEditClick,
            modifier = Modifier.height(AppTouchTargets.InlineButtonHeight),
            shape = AppShapes.InlineButton,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.White,
                contentColor = AppColors.Ink900
            ),
            border = BorderStroke(1.dp, AppColors.Line),
            contentPadding = PaddingValues(horizontal = AppSpacing.Space16, vertical = 0.dp)
        ) {
            Text(
                text = "Edit",
                style = AppTypography.ButtonInline,
                color = AppColors.Ink900
            )
        }
    }
}

@Composable
private fun DietaryRulesSection(
    excludeAlcohol: Boolean,
    excludeMeat: Boolean,
    onToggleAlcohol: () -> Unit,
    onToggleMeat: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
            Text(
                text = "Never charge me for",
                style = AppTypography.Subsection,
                color = AppColors.Ink900
            )
            Text(
                text = "When someone scans a receipt, these items are taken off your share automatically. They can still add you back.",
                style = AppTypography.BodyStrong.copy(
                    fontWeight = FontWeight.Normal,
                    lineHeight = 21.sp
                ),
                color = AppColors.Ink600
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.ListCard,
            color = AppColors.White,
            border = BorderStroke(1.dp, AppColors.LineSoft)
        ) {
            Column {
                DietaryRuleRow(
                    label = "Alcohol",
                    subtitle = "Beer, wine, spirits",
                    checked = excludeAlcohol,
                    iconPath = "M8 22h8 M12 15v7 M6 3h12l-1 7a5 5 0 0 1-10 0z",
                    onToggle = onToggleAlcohol
                )
                HorizontalDivider(thickness = 1.dp, color = AppColors.LineSoft)
                DietaryRuleRow(
                    label = "Meat",
                    subtitle = "For vegetarians",
                    checked = excludeMeat,
                    iconPath = "M15.5 4.5a6 6 0 0 1 0 8.5l-5 5a3 3 0 1 1-4.2-4.2l5-5a6 6 0 0 1 4.2-4.3z M15 7.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 1 0 0-3z",
                    onToggle = onToggleMeat
                )
            }
        }
    }
}

@Composable
private fun DietaryRuleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    iconPath: String,
    onToggle: () -> Unit
) {
    val badgeBg by animateColorAsState(
        targetValue = if (checked) AppColors.Lavender50 else AppColors.Surface,
        animationSpec = tween(200),
        label = "dietBadgeBg"
    )
    val badgeTint by animateColorAsState(
        targetValue = if (checked) AppColors.Lavender800 else AppColors.Ink600,
        animationSpec = tween(200),
        label = "dietBadgeTint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = AppSpacing.Space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = badgeBg,
                    shape = AppShapes.Chip
                ),
            contentAlignment = Alignment.Center
        ) {
            ProfileVectorIcon(
                path = iconPath,
                tint = badgeTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = AppTypography.ItemTitle,
                color = AppColors.Ink900
            )
            Text(
                text = subtitle,
                style = AppTypography.Caption,
                color = AppColors.Ink600
            )
        }

        HiFiSwitch(
            checked = checked,
            label = label,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun NotificationsSection(
    newExpenses: Boolean,
    changedExpenses: Boolean,
    paymentReminders: Boolean,
    syncUpdates: Boolean,
    onToggleNewExpenses: () -> Unit,
    onToggleChangedExpenses: () -> Unit,
    onTogglePaymentReminders: () -> Unit,
    onToggleSyncUpdates: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Notify me about",
            style = AppTypography.Subsection,
            color = AppColors.Ink900
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.ListCard,
            color = AppColors.White,
            border = BorderStroke(1.dp, AppColors.LineSoft)
        ) {
            Column {
                NotificationSettingRow(
                    label = "New expenses",
                    subtitle = "When someone adds an expense you’re part of",
                    checked = newExpenses,
                    onToggle = onToggleNewExpenses
                )
                HorizontalDivider(thickness = 1.dp, color = AppColors.LineSoft)
                NotificationSettingRow(
                    label = "Changed expenses",
                    subtitle = "When an expense you’re in is edited or removed",
                    checked = changedExpenses,
                    onToggle = onToggleChangedExpenses
                )
                HorizontalDivider(thickness = 1.dp, color = AppColors.LineSoft)
                NotificationSettingRow(
                    label = "Payment reminders",
                    subtitle = "When a friend reminds you to pay",
                    checked = paymentReminders,
                    onToggle = onTogglePaymentReminders
                )
                HorizontalDivider(thickness = 1.dp, color = AppColors.LineSoft)
                NotificationSettingRow(
                    label = "Sync updates",
                    subtitle = "When an offline expense finishes syncing",
                    checked = syncUpdates,
                    onToggle = onToggleSyncUpdates
                )
            }
        }
    }
}

@Composable
private fun NotificationSettingRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = AppSpacing.Space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = AppTypography.ItemTitle,
                color = AppColors.Ink900
            )
            Text(
                text = subtitle,
                style = AppTypography.Caption,
                color = AppColors.Ink600
            )
        }

        HiFiSwitch(
            checked = checked,
            label = label,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun AccountSection(
    onChangePasswordClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Account",
            style = AppTypography.Subsection,
            color = AppColors.Ink900
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.ListCard,
            color = AppColors.White,
            border = BorderStroke(1.dp, AppColors.LineSoft)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppTouchTargets.PrimaryButtonHeight)
                        .clickable(role = Role.Button, onClick = onChangePasswordClick)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
                ) {
                    Text(
                        text = "Change password",
                        modifier = Modifier.weight(1f),
                        style = AppTypography.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.Ink900
                    )
                    ProfileVectorIcon(
                        path = "m9 6 6 6-6 6",
                        tint = AppColors.Ink600,
                        strokeWidth = 2f,
                        modifier = Modifier.size(18.dp)
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = AppColors.LineSoft)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppTouchTargets.PrimaryButtonHeight)
                        .clickable(role = Role.Button, onClick = onLogoutClick)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
                ) {
                    Text(
                        text = "Log out",
                        modifier = Modifier.weight(1f),
                        style = AppTypography.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.Ink900
                    )
                    ProfileVectorIcon(
                        path = "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9",
                        tint = AppColors.Ink600,
                        strokeWidth = 2f,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom animated pill switch matching `docs/HiFi.html` (`52dp` × `32dp` track, `26dp` white knob,
 * `Green700` `#1E6B45` when active, `SwitchOff` `#C9D1CC` when inactive).
 */
@Composable
private fun HiFiSwitch(
    checked: Boolean,
    label: String,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) AppColors.Green700 else AppColors.SwitchOff,
        animationSpec = tween(durationMillis = 200),
        label = "switchTrackColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "switchThumbOffset"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = AppTouchTargets.MinTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = onCheckedChange
            )
            .semantics {
                contentDescription = label
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(32.dp)
                .background(
                    color = trackColor,
                    shape = AppShapes.SecondaryButton
                )
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(26.dp)
                    .shadow(elevation = 2.dp, shape = AppShapes.Circle)
                    .background(AppColors.White, AppShapes.Circle)
            )
        }
    }
}

@Composable
internal fun ProfileVectorIcon(
    path: String,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.8f,
    contentDescription: String? = null
) {
    val vector = remember(path, tint, strokeWidth) {
        ImageVector.Builder(path, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(tint),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ).build()
    }
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
