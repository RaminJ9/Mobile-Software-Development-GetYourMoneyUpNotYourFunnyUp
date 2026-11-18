package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
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

    @Test fun everyTabShowsItsScreen_andScanOpensFullScreen() {
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { AppShell(user, {}) } }
        compose.onNodeWithContentDescription("Home tab").assertIsSelected()
        // Home is fed by the ledger, which takes the signed-in account's name.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Hi, Ramin").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Groups tab").performClick()
        compose.onNode(hasText("Groups") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Activity tab").performClick()
        compose.onNodeWithContentDescription("Activity tab").assertIsSelected()
        compose.onAllNodesWithText("Activity").assertCountEquals(2) // tab label and screen title
        compose.onNodeWithContentDescription("Profile tab").performClick()
        compose.onNodeWithText(user.email).assertExists()

        // Scan is a full-screen flow on top, without the bottom bar; Back returns to the tab.
        compose.onNodeWithContentDescription("Scan tab").performClick()
        compose.onNodeWithContentDescription("Home tab").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithContentDescription("Profile tab").assertIsSelected()
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
        // The profile is a lazy list: rows below the fold don't exist until the list scrolls to them.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Log out"))
        compose.onNodeWithText("Log out").performClick()
        compose.runOnIdle { assertTrue(loggedOut) }
        compose.onNodeWithContentDescription("Profile tab").assertDoesNotExist()
        compose.runOnIdle { signedIn.value = true }
        compose.onNodeWithContentDescription("Home tab").assertIsSelected()
    }
}
