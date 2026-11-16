package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User

enum class ActivityType { ExpenseAdded, ExpenseChanged, Payment, Reminder, SyncFailure }
enum class AmountDirection { Owed, Received, Settled, Neutral }
enum class ActivityFilter(val label: String) { All("All"), NeedsYou("Needs you"), Expenses("Expenses"), Payments("Payments") }

data class ActivityEvent(
    val id: String,
    val type: ActivityType,
    val actor: User?,
    val actorColourIndex: Int,
    val description: String,
    val group: DashboardGroup,
    val occurredAt: Long,
    val amountCents: Long? = null,
    val currency: String = "DKK",
    val direction: AmountDirection = AmountDirection.Neutral,
    val unread: Boolean = false,
    val payment: OutstandingPayment? = null,
    val syncStatus: SyncStatus? = null,
    val expenseTitle: String? = null
)

data class ActivityData(
    val userId: String,
    val events: List<ActivityEvent>? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val sync: SyncPresentation = SyncPresentation()
)

fun interface SyncRetryActions {
    suspend fun retry(event: ActivityEvent): SyncStatus
}

fun ActivityEvent.canPay(userId: String): Boolean = type == ActivityType.Reminder && payment?.let {
    it.groupId == group.group.id && it.transfer.debtorId == userId &&
    it.transfer.currency == "DKK" && it.transfer.amountCents > 0
} == true

fun ActivityEvent.needsAction(userId: String, state: InlineActionState): Boolean = when {
    canPay(userId) -> state !is InlineActionState.Done
    type == ActivityType.SyncFailure -> syncStatus != SyncStatus.SYNCED &&
    !(state is InlineActionState.Done && state.syncStatus == SyncStatus.SYNCED)
    else -> false
}

fun filterActivities(
    events: List<ActivityEvent>,
    userId: String,
    filter: ActivityFilter,
    actions: Map<String, InlineActionState>
): List<ActivityEvent> = events.distinctBy { it.id }.filter {
    when (filter) {
        ActivityFilter.All -> true
        ActivityFilter.NeedsYou -> it.needsAction(userId, actions[it.id] ?: InlineActionState.Idle)
        ActivityFilter.Expenses -> it.type in listOf(
            ActivityType.ExpenseAdded,
            ActivityType.ExpenseChanged,
            ActivityType.SyncFailure
        )
        ActivityFilter.Payments -> it.type == ActivityType.Payment || it.type == ActivityType.Reminder
    }
}.sortedByDescending { it.occurredAt }
