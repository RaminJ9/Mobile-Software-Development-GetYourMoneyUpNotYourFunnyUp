package com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.model.DietaryRules
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.HiFiLabeledTextField
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppSpacing
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTouchTargets
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileUiState

/**
 * Full-screen `S7-edit · Edit profile` view (`<dc-import name="Profile" edit="{{ true }}">` in `docs/HiFi.html`).
 *
 * Renders the profile screen in editing mode with:
 * - `72dp` `Green300` avatar with `36dp` `Green700` camera badge ("Change photo") and `"Edit profile"` title
 * - `Full name`, `Email`, and `Phone number` ("Friends use this to add you to groups.") inputs (`52dp` height)
 * - 2-column `Cancel` and `Save changes` action row (`52dp` height)
 * - `"Never charge me for"` dietary rules (`Alcohol`, `Meat`), `"Notify me about"` switches, `"Account"` actions, and 5-tab `BottomNavBar`
 */
@Composable
fun EditProfileScreen(
    user: User,
    draftName: String,
    draftEmail: String,
    draftPhone: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCancelClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    infoMessage: String? = null,
    showSavedToast: Boolean = false,
    isOffline: Boolean = false,
    onToggleAlcohol: () -> Unit = {},
    onToggleMeat: () -> Unit = {},
    onToggleNewExpenses: () -> Unit = {},
    onToggleChangedExpenses: () -> Unit = {},
    onTogglePaymentReminders: () -> Unit = {},
    onToggleSyncUpdates: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    selectedTab: AppTab = AppTab.Profile,
    onSelectTab: (AppTab) -> Unit = {}
) {
    val state = ProfileUiState.Success(
        user = user,
        isEditing = true,
        draftName = draftName,
        draftEmail = draftEmail,
        draftPhone = draftPhone,
        showSavedToast = showSavedToast,
        infoMessage = infoMessage,
        errorMessage = errorMessage,
        isOffline = isOffline
    )

    ProfileAndSettingsView(
        state = state,
        onAction = { action ->
            when (action) {
                is ProfileAction.DraftName -> onNameChange(action.value)
                is ProfileAction.DraftEmail -> onEmailChange(action.value)
                is ProfileAction.DraftPhone -> onPhoneChange(action.value)
                ProfileAction.CancelEdit -> onCancelClick()
                ProfileAction.SaveEdit -> onSaveClick()
                ProfileAction.ToggleAlcohol -> onToggleAlcohol()
                ProfileAction.ToggleMeat -> onToggleMeat()
                ProfileAction.ToggleNewExpenses -> onToggleNewExpenses()
                ProfileAction.ToggleChangedExpenses -> onToggleChangedExpenses()
                ProfileAction.TogglePaymentReminders -> onTogglePaymentReminders()
                ProfileAction.ToggleSyncUpdates -> onToggleSyncUpdates()
                ProfileAction.ChangePassword -> onChangePasswordClick()
                ProfileAction.Logout -> onLogoutClick()
                ProfileAction.StartEdit -> Unit
            }
        },
        modifier = modifier,
        selectedTab = selectedTab,
        onSelectTab = onSelectTab
    )
}

/**
 * Top section of `S7-edit · Edit profile` matching `docs/HiFi.html` (lines 215–244):
 * - `72dp` `Green300` (`#8FD6AA`) circular avatar with uppercase initial + `36dp` `Green700` camera button
 * - `"Edit profile"` heading (`22sp` ExtraBold)
 * - `Full name`, `Email`, and `Phone number` (`52dp` height, `16dp` radius) fields
 * - 2-column `Cancel` and `Save changes` buttons (`52dp` height, `16dp` radius)
 */
@Composable
fun EditProfileSection(
    initial: String,
    draftName: String,
    draftEmail: String,
    draftPhone: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCancelClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    onChangePhotoClick: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space16)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space16)
        ) {
            Box(modifier = Modifier.size(72.dp)) {
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
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .size(36.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = onChangePhotoClick
                        ),
                    shape = AppShapes.Circle,
                    color = AppColors.Green700,
                    contentColor = AppColors.White,
                    border = BorderStroke(3.dp, AppColors.White)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ProfileVectorIcon(
                            path = "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z M12 9.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 1 0 0-7z",
                            tint = AppColors.White,
                            strokeWidth = 2.2f,
                            modifier = Modifier.size(16.dp),
                            contentDescription = "Change photo"
                        )
                    }
                }
            }

            Text(
                text = "Edit profile",
                style = AppTypography.ScreenTitle.copy(fontSize = 22.sp),
                color = AppColors.Ink900
            )
        }

        HiFiLabeledTextField(
            label = "Full name",
            value = draftName,
            onValueChange = onNameChange,
            placeholder = "Victor Munk",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        HiFiLabeledTextField(
            label = "Email",
            value = draftEmail,
            onValueChange = onEmailChange,
            placeholder = "victor@example.dk",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        HiFiLabeledTextField(
            label = "Phone number",
            value = draftPhone,
            onValueChange = onPhoneChange,
            placeholder = "+45 12 34 56 78",
            helperText = "Friends use this to add you to groups.",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSaveClick() }
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
        ) {
            Button(
                onClick = onCancelClick,
                modifier = Modifier
                    .weight(1f)
                    .height(AppTouchTargets.TextFieldHeight),
                shape = AppShapes.SecondaryButton,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.White,
                    contentColor = AppColors.Ink900
                ),
                border = BorderStroke(1.dp, AppColors.Line),
                contentPadding = PaddingValues(horizontal = AppSpacing.Space12, vertical = 0.dp)
            ) {
                Text(
                    text = "Cancel",
                    style = AppTypography.ButtonSecondary,
                    color = AppColors.Ink900
                )
            }

            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .weight(1f)
                    .height(AppTouchTargets.TextFieldHeight),
                shape = AppShapes.SecondaryButton,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Green700,
                    contentColor = AppColors.White
                ),
                contentPadding = PaddingValues(horizontal = AppSpacing.Space12, vertical = 0.dp)
            ) {
                Text(
                    text = "Save changes",
                    style = AppTypography.ButtonSecondary,
                    color = AppColors.White
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "S7-edit Edit Profile Light")
@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "S7-edit Edit Profile Dark"
)
@Composable
private fun EditProfileScreenPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface(color = AppColors.White) {
            EditProfileScreen(
                user = User(
                    id = "usr-victor",
                    email = "victor@example.dk",
                    displayName = "Victor Munk",
                    phoneNumber = "+45 12 34 56 78",
                    dietaryRules = DietaryRules(excludeAlcohol = true, excludeMeat = false),
                    notificationSettings = NotificationSettings(
                        newExpenses = true,
                        changedExpenses = true,
                        paymentReminders = true,
                        syncUpdates = true
                    ),
                    syncStatus = SyncStatus.SYNCED
                ),
                draftName = "Victor Munk",
                draftEmail = "victor@example.dk",
                draftPhone = "+45 12 34 56 78",
                onNameChange = {},
                onEmailChange = {},
                onPhoneChange = {},
                onCancelClick = {},
                onSaveClick = {}
            )
        }
    }
}
