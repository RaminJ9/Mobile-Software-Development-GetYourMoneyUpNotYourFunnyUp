package com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*

sealed interface ProfileAction {
    data object Edit : ProfileAction
    data object Cancel : ProfileAction
    data object Save : ProfileAction
    data class Name(val value: String) : ProfileAction
    data class Email(val value: String) : ProfileAction
    data class Phone(val value: String) : ProfileAction
    data class Toggle(val preference: ProfilePreference, val enabled: Boolean) : ProfileAction
}

@Composable
fun ProfileRoute(viewModel: ProfileViewModel, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.draft != null) { viewModel.cancel() }
    ProfileScreen(state, { action ->
        when (action) {
            ProfileAction.Edit -> viewModel.edit()
            ProfileAction.Cancel -> viewModel.cancel()
            ProfileAction.Save -> viewModel.saveDetails()
            is ProfileAction.Name -> viewModel.setName(action.value)
            is ProfileAction.Email -> viewModel.setEmail(action.value)
            is ProfileAction.Phone -> viewModel.setPhone(action.value)
            is ProfileAction.Toggle -> viewModel.toggle(action.preference, action.enabled)
        }
    }, onLogout, modifier)
}

@Composable
fun ProfileScreen(
    state: ProfileUiState.Success,
    onAction: (ProfileAction) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val user = state.user
    val draft = state.draft
    val enabled = state.canSave && !state.isSaving
    LazyColumn(
        modifier.fillMaxSize().background(AppColors.White).imePadding(),
        contentPadding = PaddingValues(AppSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space20)
    ) {
        item("title") {
            Text(if (draft == null) "Profile" else "Edit profile", style = AppTypography.ScreenTitleLarge, color = AppColors.Ink900)
        }
        item("identity") {
            Surface(shape = AppShapes.ListCard, color = AppColors.White, border = BorderStroke(1.dp, AppColors.Line)) {
                Row(Modifier.fillMaxWidth().padding(AppSpacing.Space16), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    Surface(shape = AppShapes.Circle, color = AppColors.Green300) {
                        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            Text(user.displayName.take(1).uppercase(), style = AppTypography.TopBarTitle, color = AppColors.Ink900)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
                        Text(user.displayName, style = AppTypography.ItemTitle, color = AppColors.Ink900)
                        if (draft == null) {
                            Text(user.email, style = AppTypography.Caption, color = AppColors.Ink600)
                            user.phoneNumber?.let { Text(it, style = AppTypography.Caption, color = AppColors.Ink600) }
                        }
                    }
                    if (draft == null) TextButton(onClick = { onAction(ProfileAction.Edit) }, enabled = enabled, modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget)) {
                        Text("Edit", color = if (enabled) AppColors.Green700 else AppColors.Ink600)
                    }
                }
            }
        }
        if (draft != null) {
            item("name") {
                ProfileField(draft.name, "Full name", enabled, { onAction(ProfileAction.Name(it)) })
            }
            item("email") {
                OutlinedTextField(
                    value = draft.email, onValueChange = { onAction(ProfileAction.Email(it)) }, enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    label = { Text("Email", color = AppColors.Ink600) }, supportingText = { Text("Changing email sends a verification link. Your sign-in email stays unchanged until you verify.", color = AppColors.Ink600) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = AppTouchTargets.TextFieldHeight),
                    shape = AppShapes.TextField, singleLine = true,
                    textStyle = AppTypography.Body.copy(color = AppColors.Ink900)
                )
            }
            item("phone") {
                ProfileField(draft.phone, "Phone number", enabled, { onAction(ProfileAction.Phone(it)) }, KeyboardType.Phone)
                Text("Friends use this to add you to groups.", style = AppTypography.Caption, color = AppColors.Ink600)
            }
            item("edit-actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    OutlinedButton(onClick = { onAction(ProfileAction.Cancel) }, enabled = !state.isSaving, shape = AppShapes.SecondaryButton, modifier = Modifier.weight(1f).heightIn(min = AppTouchTargets.SecondaryButtonHeight)) {
                        Text("Cancel", color = AppColors.Ink600)
                    }
                    Button(
                        onClick = { onAction(ProfileAction.Save) }, enabled = enabled, shape = AppShapes.SecondaryButton,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green700, contentColor = AppColors.White),
                        modifier = Modifier.weight(1f).heightIn(min = AppTouchTargets.SecondaryButtonHeight)
                    ) { Text(if (state.isSaving) "Saving..." else "Save changes") }
                }
            }
        } else {
            item("diet") {
                Text("Never charge me for", style = AppTypography.Section, color = AppColors.Ink900)
                Text("When someone scans a receipt, these items are taken off your share automatically. They can still add you back.", style = AppTypography.Caption, color = AppColors.Ink600)
                Spacer(Modifier.height(AppSpacing.Space12))
                SettingsCard {
                    PreferenceRow("Alcohol", "Beer, wine, spirits", user.excludeAlcohol, enabled) { onAction(ProfileAction.Toggle(ProfilePreference.Alcohol, it)) }
                    HorizontalDivider(color = AppColors.LineSoft)
                    PreferenceRow("Meat", "For vegetarians", user.excludeMeat, enabled) { onAction(ProfileAction.Toggle(ProfilePreference.Meat, it)) }
                }
            }
            item("notifications") {
                Text("Notify me about", style = AppTypography.Section, color = AppColors.Ink900)
                Spacer(Modifier.height(AppSpacing.Space12))
                SettingsCard {
                    PreferenceRow("New expenses", "When someone adds an expense you're part of", user.notificationSettings.expenseAlerts, enabled) { onAction(ProfileAction.Toggle(ProfilePreference.NewExpenses, it)) }
                    HorizontalDivider(color = AppColors.LineSoft)
                    PreferenceRow("Changed expenses", "When an expense you're in is edited or removed", user.notificationSettings.changedExpenseAlerts, enabled) { onAction(ProfileAction.Toggle(ProfilePreference.ChangedExpenses, it)) }
                    HorizontalDivider(color = AppColors.LineSoft)
                    PreferenceRow("Payment reminders", "When a friend reminds you to pay", user.notificationSettings.paymentReminders, enabled) { onAction(ProfileAction.Toggle(ProfilePreference.PaymentReminders, it)) }
                    HorizontalDivider(color = AppColors.LineSoft)
                    PreferenceRow("Sync updates", "When an offline expense finishes syncing", user.notificationSettings.syncUpdates, enabled) { onAction(ProfileAction.Toggle(ProfilePreference.SyncUpdates, it)) }
                }
            }
        }
        item("feedback") {
            val pending = user.syncStatus != SyncStatus.SYNCED
            Surface(shape = AppShapes.Chip, color = if (pending) AppColors.Amber100 else AppColors.Green100) {
                Text(
                    when {
                        state.isSaving -> "Saving on your phone..."
                        user.syncStatus == SyncStatus.FAILED -> "Saved on your phone · couldn't sync yet"
                        pending -> "Saved on your phone · waiting to sync"
                        else -> "Synced"
                    },
                    Modifier.padding(AppSpacing.Space12), style = AppTypography.Caption,
                    color = if (pending) AppColors.Amber800 else AppColors.Green700
                )
            }
            if (state.saved) Text("Saved", style = AppTypography.Caption, color = AppColors.Green700)
            state.infoMessage?.let { Text(it, style = AppTypography.Caption, color = AppColors.Green700) }
            if (!state.canSave) Text("Profile editing is unavailable.", style = AppTypography.Caption, color = AppColors.Ink600)
            state.errorMessage?.let {
                Surface(shape = AppShapes.Chip, color = AppColors.Amber100) {
                    Text(it, Modifier.padding(AppSpacing.Space12), style = AppTypography.Body, color = AppColors.Amber800)
                }
            }
        }
        item("account") {
            Text("Account", style = AppTypography.Section, color = AppColors.Ink900)
            Spacer(Modifier.height(AppSpacing.Space8))
            OutlinedButton(
                onClick = onLogout, shape = AppShapes.SecondaryButton,
                modifier = Modifier.fillMaxWidth().heightIn(min = AppTouchTargets.SecondaryButtonHeight)
            ) { Text("Log out", color = AppColors.Green700) }
        }
    }
}

@Composable
private fun ProfileField(value: String, label: String, enabled: Boolean, onChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled, label = { Text(label, color = AppColors.Ink600) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = AppShapes.TextField,
        textStyle = AppTypography.Body.copy(color = AppColors.Ink900),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.Green500, unfocusedBorderColor = AppColors.Line),
        modifier = Modifier.fillMaxWidth().heightIn(min = AppTouchTargets.TextFieldHeight)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = AppShapes.ListCard, color = AppColors.White, border = BorderStroke(1.dp, AppColors.Line)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun PreferenceRow(label: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = AppTouchTargets.MinTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(AppSpacing.Space16),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AppTypography.ItemTitle, color = AppColors.Ink900)
            Text(subtitle, style = AppTypography.Caption, color = AppColors.Ink600)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = AppColors.White, checkedTrackColor = AppColors.Green700)
        )
    }
}

@Preview(showBackground = true, name = "Profile light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Profile dark")
@Composable
private fun ProfilePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        ProfileScreen(ProfileUiState.Success(User("preview", "ramin@example.com", "Ramin", phoneNumber = "+45 12 34 56 78")), {}, {})
    }
}

@Preview(showBackground = true, name = "Edit profile light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Edit profile dark")
@Composable
private fun EditProfilePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        ProfileScreen(ProfileUiState.Success(User("preview", "ramin@example.com", "Ramin"), draft = ProfileDraft("Ramin", "+45 12 34 56 78", "ramin@example.com")), {}, {})
    }
}
