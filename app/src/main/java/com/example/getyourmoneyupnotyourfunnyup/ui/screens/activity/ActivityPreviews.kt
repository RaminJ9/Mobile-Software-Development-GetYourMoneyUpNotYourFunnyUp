package com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityUiState

private fun previewActivityData(): ActivityData {
    val now = 1793703600000L
    val ski = DashboardGroup(Group("ski", "Ski trip Åre", currency = "DKK", createdBy = "you"), 1)
    val bar = DashboardGroup(Group("bar", "Friday Bar crew", currency = "DKK", createdBy = "you"), 2)
    val mads = User("mads", "preview@example.com", "Mads")
    val sofie = User("sofie", "preview@example.com", "Sofie")
    val you = User("you", "preview@example.com", "Victor")
    val payment = OutstandingPayment("ski-mads", "ski", "Mads", now - 172800000L, DebtTransfer("you", "mads", 28600, "DKK"))
    return ActivityData("you", listOf(
            ActivityEvent(
                "reminder",
                ActivityType.Reminder,
                mads,
                1,
                "reminded you to pay for the ski trip",
                ski,
                now,
                28600,
                direction = AmountDirection.Owed,
                unread = true,
                payment = payment
            ),
            ActivityEvent(
                "expense",
                ActivityType.ExpenseAdded,
                sofie,
                3,
                "added “Bar tab” · your share",
                bar,
                now - 1800000,
                9450,
                direction = AmountDirection.Owed,
                unread = true
            ),
            ActivityEvent(
                "failed",
                ActivityType.SyncFailure,
                null,
                0,
                "couldn’t sync yet – retrying automatically",
                ski,
                now - 3600000,
                unread = true,
                syncStatus = SyncStatus.FAILED,
                expenseTitle = "Fuel for the drive"
            ),
            ActivityEvent(
                "changed",
                ActivityType.ExpenseChanged,
                mads,
                1,
                "changed “Lift passes ×6” from 3.600 to 4.320 kr",
                ski,
                now - 86400000
            ),
            ActivityEvent(
                "payment",
                ActivityType.Payment,
                you,
                0,
                "paid Mads",
                ski,
                now - 3 * 86400000,
                120000,
                direction = AmountDirection.Settled
            ),
            ActivityEvent(
                "received",
                ActivityType.Payment,
                sofie,
                3,
                "paid you",
                bar,
                now - 3 * 86400000,
                6400,
                direction = AmountDirection.Received
            )
        ), now)
}

@Composable
private fun ActivityExample(state: ActivityUiState) {
    GetYourMoneyUpNotYourFunnyUpTheme { ActivityScreen(state, {}) }
}

@Preview(name = "Activity · light", widthDp = 412, heightDp = 835)
@Preview(name = "Activity · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActivityPreview() = ActivityExample(ActivityUiState.Success(previewActivityData(), canSettle = true, canRetry = true))

@Preview(name = "Inline actions · light", widthDp = 412, heightDp = 835)
@Preview(name = "Inline actions · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActionsPreview() = ActivityExample(ActivityUiState.Success(
        previewActivityData(),
        actions = mapOf("reminder" to InlineActionState.Confirming, "failed" to InlineActionState.Working),
        canSettle = true,
        canRetry = true
))

@Preview(name = "Synced · light", widthDp = 412, heightDp = 835)
@Preview(name = "Synced · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SyncedPreview() = ActivityExample(ActivityUiState.Success(
        previewActivityData(),
        actions = mapOf(
            "reminder" to InlineActionState.Done(SyncStatus.PENDING_INSERT),
            "failed" to InlineActionState.Done(SyncStatus.SYNCED)
        )
))

@Preview(name = "Nothing needs you · light", widthDp = 412, heightDp = 835)
@Preview(name = "Nothing needs you · dark", widthDp = 412, heightDp = 835, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyPreview() = ActivityExample(ActivityUiState.Success(
        ActivityData("you", emptyList()),
        filter = ActivityFilter.NeedsYou
))
