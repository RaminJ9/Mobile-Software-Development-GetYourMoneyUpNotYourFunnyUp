package com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.getyourmoneyupnotyourfunnyup.model.DietaryRules
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileUiState

private val previewUser = User(
    id = "usr-victor",
    email = "victor@example.dk",
    displayName = "Victor Munk",
    phoneNumber = "+45 12 34 56 78",
    dietaryRules = DietaryRules(
        excludeAlcohol = true,
        excludeMeat = false
    ),
    notificationSettings = NotificationSettings(
        newExpenses = true,
        changedExpenses = true,
        paymentReminders = true,
        syncUpdates = true
    ),
    syncStatus = SyncStatus.SYNCED
)

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "S7 Profile & Settings (HiFi)")
@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "S7 Profile & Settings Dark"
)
@Composable
private fun ProfileAndSettingsViewPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface(color = AppColors.White) {
            ProfileAndSettingsView(
                state = ProfileUiState.Success(
                    user = previewUser,
                    isEditing = false
                ),
                onAction = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "S7-edit Edit Profile (HiFi)")
@Composable
private fun EditProfileViewPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface(color = AppColors.White) {
            ProfileAndSettingsView(
                state = ProfileUiState.Success(
                    user = previewUser,
                    isEditing = true
                ),
                onAction = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "S7 Profile Saved Toast")
@Composable
private fun ProfileSavedToastPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface(color = AppColors.White) {
            ProfileAndSettingsView(
                state = ProfileUiState.Success(
                    user = previewUser,
                    isEditing = false,
                    showSavedToast = true
                ),
                onAction = {}
            )
        }
    }
}
