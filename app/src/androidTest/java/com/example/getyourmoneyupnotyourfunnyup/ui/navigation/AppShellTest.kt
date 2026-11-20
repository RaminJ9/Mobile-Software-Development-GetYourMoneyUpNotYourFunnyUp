package com.example.getyourmoneyupnotyourfunnyup.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.*
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
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
    private lateinit var database: AppDatabase
    private val visible = mutableStateOf(true)
    @Before fun setup() { database = AppDatabase.createInMemory(InstrumentationRegistry.getInstrumentation().targetContext) }
    @After fun cleanup() {
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        database.close()
    }

    private fun seedGroup(withExpense: Boolean) {
        database.userDao.upsertUser(UserEntity("u", user.email, user.displayName))
        database.userDao.upsertUser(UserEntity("friend", "ida@test", "Ida"))
        database.groupDao.upsertGroupWithMembers(
            GroupEntity("g", "Trip", currency = "DKK", createdBy = "u"),
            listOf(GroupMemberEntity("g", "u"), GroupMemberEntity("g", "friend")))
        if (withExpense) database.expenseDao.upsertExpenseWithSplits(
            ExpenseEntity("e", "g", "Food", 10000, currency = "DKK", payerId = "friend", createdBy = "friend"),
            listOf(ExpenseSplitEntity("s1", "e", "u", 5000), ExpenseSplitEntity("s2", "e", "friend", 5000)))
    }
    private fun openBalances() {
        compose.onNodeWithContentDescription("Groups tab").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Trip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Trip").performClick()
        compose.onNodeWithText("Balances").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Member balances").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun groupBalancesIsReachableWithNoExpensesAndBackReturnsToGroups() {
        seedGroup(withExpense = false)
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            if (visible.value) AppShell(user, {}, database = database, currentAccountId = { user.id })
        } }
        openBalances()
        compose.onNodeWithText("No expenses or completed repayments yet. Balances are zero.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No payments needed. Everyone is settled.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Groups tab").assertIsSelected()
    }
    @Test fun virtualPaymentCanBeMadeThroughGroupNavigationAndSurvivesReopening() {
        seedGroup(withExpense = true)
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            if (visible.value) AppShell(user, {}, database = database, currentAccountId = { user.id })
        } }
        openBalances()
        compose.onNodeWithText("Pay 50,00 kr").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Paid \u00b7 settled").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Paid \u00b7 settled").performScrollTo().assertIsDisplayed()
        assertEquals(1, database.settlementDao.getSettlementsForGroup("g").size)
        assertEquals(SyncStatus.PENDING_INSERT, database.settlementDao.getSettlementsForGroup("g").single().syncStatus)
        assertEquals(1, database.syncQueueDao.getAllPending().size)
        compose.onNodeWithContentDescription("Back").performClick()
        openBalances()
        compose.onNodeWithText("No payments needed. Everyone is settled.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pay 50,00 kr").assertDoesNotExist()
    }

    @Test fun savedExpenseCanBeOpenedFromGroupActivityAndDismissedBackToLedger() {
        seedGroup(withExpense = true)
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme {
            if (visible.value) AppShell(user, {}, database = database, currentAccountId = { user.id })
        } }
        compose.onNodeWithContentDescription("Groups tab").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Trip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Trip").performClick()
        compose.onNodeWithText("Activity").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("expense-e").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("expense-e").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Expense details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Member shares").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ramin (You)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithTag("expense-e").assertExists()
        assertEquals(10000L, database.expenseDao.getExpenseById("e")!!.amountCents)
        assertTrue(database.syncQueueDao.getAllPending().isEmpty())
    }

    @Test fun startsOnHomeAndExposesGroupsAndRemainingUnavailableDestinations() {
        compose.setContent { GetYourMoneyUpNotYourFunnyUpTheme { if (visible.value) AppShell(user, {}, database = database, currentAccountId = { user.id }) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Hi, Ramin").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Home tab").assertIsSelected()
        compose.onNodeWithText("Hi, Ramin").assertExists()
        compose.onNodeWithText("No groups yet").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Groups tab").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Search groups").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search groups").assertExists()
        compose.onNodeWithContentDescription("Scan tab").performClick()
        compose.onNodeWithText("Receipt scanning is not connected yet.").assertExists()
        compose.onNodeWithContentDescription("Activity tab").performClick()
        compose.onNodeWithText("No activity yet").assertExists()
        compose.onNodeWithContentDescription("Profile tab").performClick()
        compose.onNodeWithText(user.email).assertExists()
    }

    @Test fun logoutRemovesShellAndAnotherSessionStartsOnHome() {
        val signedIn = mutableStateOf(true)
        var loggedOut = false
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                if (visible.value && signedIn.value) AppShell(user, { loggedOut = true; signedIn.value = false }, database = database, currentAccountId = { user.id })
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
