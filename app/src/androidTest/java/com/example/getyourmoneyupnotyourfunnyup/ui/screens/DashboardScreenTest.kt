package com.example.getyourmoneyupnotyourfunnyup.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()
    private val group = DashboardGroup(Group("g", "Ski trip", currency = "DKK", createdBy = "u"), 1)
    private val payment = OutstandingPayment("p", "g", "Mads", 1, DebtTransfer("u", "m", 1234, "DKK"))
    private fun homeData() = HomeData(
        User("u", "u@test", "Victor"), listOf(group),
        FinancialSummary(mapOf("g" to MemberBalance("u", -1234, "DKK")), listOf(payment)),
        paidOutPayments = emptyList()
    )

    @Test fun homeBellDelegatesToActivityAndPaymentConfirmationStaysOnHome() {
        var openedActivity = false
        val state = mutableStateOf(HomeUiState.Success(homeData(), canSettle = true))
        val actions = mutableListOf<HomeAction>()
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                HomeScreen(state.value, { action ->
                    actions += action
                    when (action) {
                        HomeAction.TogglePayments -> state.value = state.value.copy(paymentsExpanded = true)
                        is HomeAction.Ask -> state.value = state.value.copy(paymentStates = mapOf(action.paymentId to InlineActionState.Confirming))
                        else -> Unit
                    }
                }, { openedActivity = true }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Activity").performClick()
        compose.runOnIdle { assertTrue(openedActivity) }
        compose.onNodeWithText("Pay now", substring = true).performClick()
        compose.onNodeWithText("Pay 12,34 kr").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performScrollTo().performClick()
        compose.onNodeWithText("Hi, Victor").assertExists()
        compose.runOnIdle { assertEquals(HomeAction.Confirm("p"), actions.last()) }
    }

    @Test fun activityExposesReadFilterAndRetryActions() {
        val event = ActivityEvent(
            "failed", ActivityType.SyncFailure, null, 0, "couldn't sync yet", group,
            System.currentTimeMillis(), unread = true, syncStatus = SyncStatus.FAILED, expenseTitle = "Fuel"
        )
        val actions = mutableListOf<ActivityAction>()
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                ActivityScreen(ActivityUiState.Success(ActivityData("u", listOf(event)), canRetry = true), { actions += it })
            }
        }
        compose.onNodeWithText("Mark all as read").performClick()
        compose.onNodeWithText("Needs you · 1").performClick()
        compose.onNodeWithText("Try now").performClick()
        compose.runOnIdle {
            assertEquals(listOf(ActivityAction.MarkAllRead, ActivityAction.Filter(ActivityFilter.NeedsYou), ActivityAction.Retry("failed")), actions)
        }
    }

    @Test fun allClearPanelRequestsCollapseAfterFiveSeconds() {
        compose.mainClock.autoAdvance = false
        var closed = false
        val data = homeData().copy(finances = FinancialSummary(mapOf("g" to MemberBalance("u", 0, "DKK")), emptyList()))
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                HomeScreen(HomeUiState.Success(data, paymentsExpanded = true), {
                    if (it == HomeAction.ClosePayments) closed = true
                }, {}, {}, {}, {}, {})
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("No payments due").assertExists()
        compose.mainClock.advanceTimeBy(5100)
        compose.runOnIdle { assertTrue(closed) }
    }
}
