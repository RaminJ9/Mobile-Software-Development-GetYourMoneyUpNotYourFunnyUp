package com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileDraft
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileUiState

private val previewUser = User("preview", "victor@example.dk", "Victor Munk", phoneNumber = "+45 12 34 56 78", excludeAlcohol = true)

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "S7 Profile HiFi")
@Preview(showBackground = true, widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "S7 Profile dark")
@Composable
private fun SettingsPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme { ProfileScreen(ProfileUiState.Success(previewUser), {}, {}) }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "S7-edit Profile")
@Preview(showBackground = true, widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "S7-edit Profile dark")
@Composable
private fun EditPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme { EditProfileScreen(previewUser, ProfileDraft(previewUser.displayName, previewUser.phoneNumber.orEmpty()), {}, {}) }
}
