package com.example.getyourmoneyupnotyourfunnyup.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule val compose = createComposeRule()
    private val user = User("u", "ramin@example.com", "Ramin", updatedAt = 1L)

    @Test fun preferencesAndLogoutAreAccessibleByScrolling() {
        val actions = mutableListOf<ProfileAction>()
        var loggedOut = false
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme { ProfileScreen(ProfileUiState.Success(user), { actions += it }, { loggedOut = true }) }
        }
        compose.onNodeWithText("Alcohol").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ProfileAction.Toggle(ProfilePreference.Alcohol, true), actions.last()) }
        for (label in listOf("Meat", "New expenses", "Changed expenses", "Payment reminders", "Sync updates")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(loggedOut) }
    }

    @Test fun editSupportsSaveCancelAndReadOnlyEmail() {
        val state = mutableStateOf(ProfileUiState.Success(user))
        val actions = mutableListOf<ProfileAction>()
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                ProfileScreen(state.value, { action ->
                    actions += action
                    when (action) {
                        ProfileAction.Edit -> state.value = state.value.copy(draft = ProfileDraft("Ramin", ""))
                        is ProfileAction.Name -> state.value = state.value.copy(draft = state.value.draft!!.copy(name = action.value))
                        ProfileAction.Cancel -> state.value = state.value.copy(draft = null)
                        else -> Unit
                    }
                }, {})
            }
        }
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Edit profile").assertExists()
        compose.onNodeWithText("Full name").performTextReplacement("Ramin Javed")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ProfileAction.Save, actions.last()) }
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Edit profile").assertDoesNotExist()
    }

    @Test fun pendingSaveIsNotShownAsSyncedAndErrorIsVisible() {
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                ProfileScreen(ProfileUiState.Success(user.copy(syncStatus = SyncStatus.PENDING_UPDATE), errorMessage = "Storage unavailable"), {}, {})
            }
        }
        compose.onNodeWithText("Saved on your phone · waiting to sync").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Synced").assertDoesNotExist()
        compose.onNodeWithText("Storage unavailable").performScrollTo().assertIsDisplayed()
    }
}
