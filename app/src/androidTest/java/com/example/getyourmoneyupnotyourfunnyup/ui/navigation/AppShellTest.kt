package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellTest {
    @get:Rule val compose = createComposeRule()
    private val user = User("u", "ramin@example.com", "Ramin")

    @Test fun startsOnHomeAndExposesHonestUnavailableDestinations() {
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { AppShell(user, {}) } }
        compose.onNodeWithContentDescription("Home tab").assertIsSelected()
        compose.onNodeWithText("Hi, Ramin").assertExists()
        compose.onNodeWithText("Home data unavailable.", substring = true).assertExists()
        compose.onNodeWithContentDescription("Groups tab").performClick()
        compose.onNodeWithText("Groups are not connected yet.").assertExists()
        compose.onNodeWithContentDescription("Scan tab").performClick()
        compose.onNodeWithText("Receipt scanning is not connected yet.").assertExists()
        compose.onNodeWithContentDescription("Activity tab").performClick()
        compose.onNodeWithText("Activity history unavailable").assertExists()
        compose.onNodeWithText("No activity yet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Profile tab").performClick()
        compose.onNodeWithText(user.email).assertExists()
    }

    @Test fun logoutRemovesShellAndAnotherSessionStartsOnHome() {
        val signedIn = mutableStateOf(true)
        var loggedOut = false
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                if (signedIn.value) AppShell(user, { loggedOut = true; signedIn.value = false })
            }
        }
        compose.onNodeWithContentDescription("Profile tab").performClick()
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(loggedOut) }
        compose.onNodeWithContentDescription("Profile tab").assertDoesNotExist()
        compose.runOnIdle { signedIn.value = true }
        compose.onNodeWithContentDescription("Home tab").assertIsSelected()
    }
}
