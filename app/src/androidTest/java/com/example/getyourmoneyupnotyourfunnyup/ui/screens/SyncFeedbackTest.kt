package com.example.getyourmoneyupnotyourfunnyup.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.components.InlinePaymentActions
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SyncFeedback
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityAction
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offlineWithoutTimestampMakesNoSyncPromise() {
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                ActivityScreen(ActivityUiState.Success(ActivityData("u", emptyList(),
                    sync = SyncPresentation(connection = ConnectionState.Offline))), {})
            }
        }
        compose.onNodeWithText("You're offline · showing available data.").assertExists()
        compose.onNodeWithText("Anything you add syncs", substring = true).assertDoesNotExist()
    }

    @Test fun pendingCountAndAcknowledgedRecoveryComeFromSuppliedState() {
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                SyncFeedback(SyncPresentation(pendingCount = 2, acknowledgedRecovery = "Expense synchronized after retry"))
            }
        }
        compose.onNodeWithText("2 changes waiting to sync").assertExists()
        compose.onNodeWithText("Expense synchronized after retry").assertExists()
    }

    @Test fun queuedSettlementIsNotShownAsRemotelyCompleted() {
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                InlinePaymentActions(InlineActionState.Done(SyncStatus.PENDING_INSERT), "12,34 kr", "Mads", {}, {}, {})
            }
        }
        compose.onNodeWithText("Settlement recorded · synchronization pending").assertExists()
        compose.onNodeWithText("Paid · settled with Mads").assertDoesNotExist()
    }

    @Test fun emptyNeedsYouDoesNotClaimPendingOrFailedOperationsAreSynced() {
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                ActivityScreen(ActivityUiState.Success(ActivityData("u", emptyList(), sync =
                    SyncPresentation(pendingCount = 2, failureMessage = "One change needs attention")),
                    filter = ActivityFilter.NeedsYou), {})
            }
        }
        compose.onNodeWithText("No actions in this filter.").assertExists()
        compose.onNodeWithText("2 changes waiting to sync").assertExists()
        compose.onNodeWithText("One change needs attention").assertExists()
        compose.onNodeWithText("No payments due and everything is synced.").assertDoesNotExist()
    }

    @Test fun inconsistentGroupBalanceDoesNotDisplayTheSmallerTransferTotal() {
        val group = DashboardGroup(Group("g", "Group", currency = "DKK", createdBy = "u"), 0)
        val payment = OutstandingPayment("p", "g", "Mads", 1, DebtTransfer("u", "m", 5000, "DKK"))
        val data = HomeData(User("u", "u@test", "User"), listOf(group),
            FinancialSummary(mapOf("g" to MemberBalance("u", -10000, "DKK")), listOf(payment)))
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                HomeScreen(HomeUiState.Success(data), {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Group"))
        compose.onNodeWithText("You owe 50,00 kr").assertDoesNotExist()
        compose.onNodeWithText("Group").assert(hasText("Balance unavailable"))
    }

    @Test fun aQueuedRetryStillOffersManualRetry() {
        val group = DashboardGroup(Group("g", "Group", createdBy = "u"), 0)
        val event = ActivityEvent("retry", ActivityType.SyncFailure, null, 0, "Couldn't sync", group,
            System.currentTimeMillis(), syncStatus = SyncStatus.FAILED)
        val actions = mutableListOf<ActivityAction>()
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                ActivityScreen(ActivityUiState.Success(ActivityData("u", listOf(event)),
                    actions = mapOf("retry" to InlineActionState.Done(SyncStatus.PENDING_UPDATE)),
                    canRetry = true), { actions += it })
            }
        }
        compose.onNodeWithText("Try now").performClick()
        compose.runOnIdle { assertEquals(listOf(ActivityAction.Retry("retry")), actions) }
        compose.onNodeWithText("Synced").assertDoesNotExist()
    }

    @Test fun recordedSettlementWithFailedSyncDoesNotOfferDuplicatePayment() {
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                InlinePaymentActions(InlineActionState.Done(SyncStatus.FAILED), "12,34 kr", "Mads", {}, {}, {})
            }
        }
        compose.onNodeWithText("Settlement recorded · sync needs attention").assertExists()
        compose.onNodeWithText("Pay 12,34 kr").assertDoesNotExist()
    }
}
