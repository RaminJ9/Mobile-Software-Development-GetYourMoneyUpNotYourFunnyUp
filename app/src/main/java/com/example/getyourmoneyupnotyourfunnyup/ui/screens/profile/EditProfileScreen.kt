package com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileDraft
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileUiState

/** S7-edit entry point using the shared profile presentation and save/cancel actions. */
@Composable
fun EditProfileScreen(
    user: User,
    draft: ProfileDraft,
    onAction: (ProfileAction) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    ProfileScreen(ProfileUiState.Success(user, draft, isSaving, errorMessage), onAction, onLogout, modifier)
}
