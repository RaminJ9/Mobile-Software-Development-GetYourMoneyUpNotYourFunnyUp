package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.ActivityEntry
import com.example.getyourmoneyupnotyourfunnyup.model.ActivityType
import com.example.getyourmoneyupnotyourfunnyup.model.DateLabels
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyDirection
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.activityFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ActivityFilter { All, NeedsYou, Expenses, Payments }

enum class AmountTone { Owe, Positive, Neutral }

/** What the action strip under a feed item shows. */
sealed interface FeedAction {
    data object None : FeedAction
    data class Pay(val amountCents: Long) : FeedAction
    data object Confirm : FeedAction
    data class Working(val text: String) : FeedAction
    data class Done(val text: String) : FeedAction
    data object Retry : FeedAction
}

@Immutable
data class FeedRowUi(
    val id: String,
    val type: ActivityType,
    val actor: String,
    val text: String,
    val groupName: String,
    val groupPaletteIndex: Int,
    val time: String,
    /** Null for sync failures (shown with a warning tile instead of a person). */
    val avatar: AvatarUi?,
    val amountCents: Long?,
    val amountSign: String,
    val amountTone: AmountTone,
    val unread: Boolean,
    val action: FeedAction,
)

@Immutable
data class FeedSectionUi(val title: String, val rows: List<FeedRowUi>)

@Immutable
data class ActivityUiState(
    val filter: ActivityFilter,
    val needsYouCount: Int,
    val sections: List<FeedSectionUi>,
    val hasUnread: Boolean,
)

sealed interface ActivityAction {
    data class SelectFilter(val filter: ActivityFilter) : ActivityAction
    data object MarkAllRead : ActivityAction
    data class AskPay(val id: String) : ActivityAction
    data object CancelPay : ActivityAction
    data class ConfirmPay(val id: String) : ActivityAction
    data class Retry(val id: String) : ActivityAction
}

data class ActivityLocalState(
    val filter: ActivityFilter = ActivityFilter.All,
    val confirmingId: String? = null,
    /** Feed id → settlement id for payments made here. */
    val payments: Map<String, String> = emptyMap(),
    /** Failed-sync entries the user retried, kept visible so they can see them finish. */
    val retried: Map<String, ActivityEntry> = emptyMap(),
)

fun buildActivityUiState(snapshot: LedgerSnapshot, local: ActivityLocalState, now: Long): ActivityUiState {
    val feed = snapshot.activityFeed(now)
    val retriedStillListed = local.retried.filterKeys { id -> feed.none { it.id == id } }.values
    val entries = (feed + retriedStillListed).sortedByDescending { it.createdAt }

    fun actionFor(entry: ActivityEntry): FeedAction {
        local.payments[entry.id]?.let { settlementId ->
            val settlement = snapshot.settlements.firstOrNull { it.id == settlementId }
            return if (settlement?.status == SettlementStatus.COMPLETED) {
                FeedAction.Done("Paid · settled with ${snapshot.person(settlement.creditorId).name}")
            } else {
                FeedAction.Working("Paying…")
            }
        }
        if (entry.id in local.retried) {
            val expense = snapshot.expenses.firstOrNull { it.id == entry.expenseId }
            return when (expense?.syncStatus) {
                SyncStatus.SYNCED -> FeedAction.Done("Synced · group notified")
                SyncStatus.FAILED -> FeedAction.Retry
                else -> FeedAction.Working("Syncing…")
            }
        }
        return when {
            entry.type == ActivityType.FAILED -> FeedAction.Retry
            entry.type == ActivityType.REMINDER && (entry.amountCents ?: 0L) > 0L ->
                if (local.confirmingId == entry.id) FeedAction.Confirm else FeedAction.Pay(entry.amountCents ?: 0L)
            else -> FeedAction.None
        }
    }

    val open = entries.filter { entry ->
        val action = actionFor(entry)
        action is FeedAction.Pay || action is FeedAction.Confirm || action is FeedAction.Retry
    }
    val shown = when (local.filter) {
        ActivityFilter.All -> entries
        ActivityFilter.NeedsYou -> open
        ActivityFilter.Expenses -> entries.filter { it.type == ActivityType.ADDED || it.type == ActivityType.FAILED }
        ActivityFilter.Payments -> entries.filter { it.type == ActivityType.PAID || it.type == ActivityType.REMINDER }
    }
    val rows = shown.map { entry ->
        val group = snapshot.group(entry.groupId)
        val action = actionFor(entry)
        val paid = action is FeedAction.Done && entry.type == ActivityType.REMINDER
        FeedRowUi(
            id = entry.id,
            type = entry.type,
            actor = entry.actorLabel,
            text = entry.text,
            groupName = group?.name ?: "",
            groupPaletteIndex = group?.colorIndex ?: 0,
            time = DateLabels.relativeDay(entry.createdAt, now).let { day ->
                if (day == "Today" || day == "Yesterday") DateLabels.time(entry.createdAt) else day
            },
            avatar = entry.actorId?.let { id ->
                val person = snapshot.person(id)
                AvatarUi(person.initial, if (id == snapshot.currentUserId) -1 else person.paletteIndex)
            },
            amountCents = entry.amountCents ?: (local.payments[entry.id]?.let { id -> snapshot.settlements.firstOrNull { it.id == id }?.amountCents }),
            amountSign = if (entry.direction == MoneyDirection.IN) "+" else "",
            amountTone = when {
                paid -> AmountTone.Positive
                entry.direction == MoneyDirection.OUT -> AmountTone.Owe
                entry.direction == MoneyDirection.IN || entry.direction == MoneyDirection.SETTLE -> AmountTone.Positive
                else -> AmountTone.Neutral
            },
            unread = entry.isUnread,
            action = action,
        )
    }
    val order = listOf("Today", "Yesterday", "Earlier")
    val sections = order.mapNotNull { title ->
        val inSection = rows.filter { row -> DateLabels.section(shown.first { it.id == row.id }.createdAt, now) == title }
        if (inSection.isEmpty()) null else FeedSectionUi(title, inSection)
    }
    return ActivityUiState(
        filter = local.filter,
        needsYouCount = open.size,
        sections = sections,
        hasUnread = feed.any { it.isUnread },
    )
}

/** Activity tab: notifications, reminders, and sync results. */
class ActivityViewModel(private val ledger: LedgerRepository) : ViewModel() {
    private val local = MutableStateFlow(ActivityLocalState())

    val uiState: StateFlow<ActivityUiState> = combine(ledger.snapshot, local) { snapshot, localState ->
        buildActivityUiState(snapshot, localState, ledger.now())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, buildActivityUiState(ledger.snapshot.value, local.value, ledger.now()))

    fun onAction(action: ActivityAction) {
        when (action) {
            is ActivityAction.SelectFilter -> local.update { it.copy(filter = action.filter) }
            ActivityAction.MarkAllRead -> ledger.markActivityRead(ledger.snapshot.value.activityFeed(ledger.now()).map { it.id })
            is ActivityAction.AskPay -> {
                ledger.markActivityRead(listOf(action.id))
                local.update { it.copy(confirmingId = action.id) }
            }
            ActivityAction.CancelPay -> local.update { it.copy(confirmingId = null) }
            is ActivityAction.ConfirmPay -> {
                val entry = ledger.snapshot.value.activityFeed(ledger.now()).firstOrNull { it.id == action.id } ?: return
                val creditor = entry.payCreditorId ?: return
                val amount = entry.amountCents ?: return
                val settlementId = ledger.payDebt(entry.groupId, creditor, amount)
                local.update { it.copy(confirmingId = null, payments = it.payments + (action.id to settlementId)) }
            }
            is ActivityAction.Retry -> {
                val entry = ledger.snapshot.value.activityFeed(ledger.now()).firstOrNull { it.id == action.id }
                    ?: local.value.retried[action.id] ?: return
                ledger.markActivityRead(listOf(action.id))
                entry.expenseId?.let(ledger::retrySync)
                local.update { it.copy(retried = it.retried + (action.id to entry)) }
            }
        }
    }
}
