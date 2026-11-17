package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.ActivityEntry
import com.example.getyourmoneyupnotyourfunnyup.model.ActivityEntryType
import com.example.getyourmoneyupnotyourfunnyup.model.DebtTransfer
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.MemberBalance
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyDirection
import com.example.getyourmoneyupnotyourfunnyup.model.Person
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.model.activityFeed
import com.example.getyourmoneyupnotyourfunnyup.model.failedSyncActivityId
import com.example.getyourmoneyupnotyourfunnyup.model.lastActivityAt
import com.example.getyourmoneyupnotyourfunnyup.model.myDebts
import com.example.getyourmoneyupnotyourfunnyup.model.myGroups
import com.example.getyourmoneyupnotyourfunnyup.model.myNet
import com.example.getyourmoneyupnotyourfunnyup.model.unsyncedExpenseCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Supplies the Home and Activity screens from the local-first [LedgerRepository]: read-only flows of
 * [HomeData] / [ActivityData], and the settlement and retry actions those screens call.
 *
 * The mapping lives in the pure functions [homeData] and [activityData] so it can be unit tested.
 */
class LedgerDashboardSource(
    private val ledger: LedgerRepository,
    private val waitMs: Long = WAIT_MS,
) : SettlementActions, SyncRetryActions {

    val home: Flow<HomeData> = ledger.snapshot.map { homeData(it, ledger.now()) }

    val activity: Flow<ActivityData> = flow {
        // A failed upload stays in this collector's feed after "Try now" so it can show "Synced".
        val failures = mutableSetOf<String>()
        ledger.snapshot.collect { snapshot ->
            failures += snapshot.expenses.filter { it.syncStatus == SyncStatus.FAILED }.map { it.id }
            emit(activityData(snapshot, ledger.now(), failures.toSet()))
        }
    }

    /** Guards "is this payment already under way?" + "start it" so two screens can't pay twice. */
    private val recordLock = Mutex()

    /** "Mark all as read" in Activity, saved so Home's bell dot clears too. */
    fun markAllActivityRead() {
        val snapshot = ledger.snapshot.value
        ledger.markActivityRead(snapshot.activityFeed(ledger.now()).map { it.id })
    }

    /**
     * Pays [payment] once. Revalidates it against the latest ledger first, and if the same payment is
     * already being made (for example confirmed on Home, then in Activity) it follows that payment
     * instead of starting a second one.
     *
     * Returns the payment's sync status: SYNCED once uploaded, or a pending status while offline (the
     * payment is still recorded on the phone and uploads on reconnect).
     */
    override suspend fun recordSettlement(payment: OutstandingPayment): SyncStatus {
        val settlementId = recordLock.withLock {
            val snapshot = ledger.snapshot.value
            check(payment.transfer.debtorId == snapshot.currentUserId) { "This payment belongs to another account." }
            snapshot.settlements.firstOrNull { it.isUnderWayFor(payment) }?.id ?: run {
                check(payment in snapshot.outstandingPayments()) { "This balance changed. Check the latest payment." }
                ledger.payDebt(payment.groupId, payment.transfer.creditorId, payment.transfer.amountCents)
            }
        }
        withTimeoutOrNull(waitMs) {
            combine(ledger.snapshot, ledger.isOnline) { s, online -> s.settlements.firstOrNull { it.id == settlementId } to online }
                .first { (settlement, online) ->
                    settlement == null ||
                        settlement.status == SettlementStatus.COMPLETED && (settlement.syncStatus == SyncStatus.SYNCED || !online)
                }
        }
        val settlement = ledger.snapshot.value.settlements.firstOrNull { it.id == settlementId }
            ?: error("Couldn't record this payment. Try again.")
        // Still processing after the wait: report it, and a new attempt will follow this same payment.
        check(settlement.status == SettlementStatus.COMPLETED) { "This payment is still processing. Check again in a moment." }
        return settlement.syncStatus
    }

    /**
     * "Try now" for a failed upload. Only the user's own expense in the event's group can be retried, and
     * it is matched by id, never by title or amount. Returns SYNCED once uploaded, or a pending status
     * while offline (it then uploads automatically on reconnect).
     */
    override suspend fun retry(event: ActivityEvent): SyncStatus {
        val snapshot = ledger.snapshot.value
        val expense = snapshot.expenses.firstOrNull { failedSyncActivityId(it.id) == event.id }
        check(
            expense != null && !expense.isDeleted && expense.createdBy == snapshot.currentUserId &&
                expense.groupId == event.group.group.id,
        ) { "This change can't be retried from here." }
        if (expense.syncStatus == SyncStatus.FAILED) ledger.retrySync(expense.id)
        withTimeoutOrNull(waitMs) {
            combine(ledger.snapshot, ledger.isOnline) { s, online -> s.expenses.firstOrNull { it.id == expense.id } to online }
                .first { (current, online) -> current == null || current.syncStatus == SyncStatus.SYNCED || !online }
        }
        return ledger.snapshot.value.expenses.firstOrNull { it.id == expense.id }?.syncStatus
            ?: error("This expense was removed.")
    }

    private fun Settlement.isUnderWayFor(payment: OutstandingPayment) =
        status == SettlementStatus.PENDING && groupId == payment.groupId &&
            debtorId == payment.transfer.debtorId && creditorId == payment.transfer.creditorId &&
            amountCents == payment.transfer.amountCents

    companion object {
        /** How long a Pay or Try now waits for the upload before reporting "pending" instead. */
        const val WAIT_MS = 10_000L

        /** Our repository uploads queued changes on reconnect, so the offline banner may promise it. */
        const val OFFLINE_NOTE = "Anything you add syncs when you're back."
    }
}

/** What Home shows, derived from the ledger. Balances are computed, never stored. */
fun homeData(snapshot: LedgerSnapshot, now: Long): HomeData {
    val groups = snapshot.myGroups()
    return HomeData(
        user = snapshot.me.toUser(),
        groups = groups.map { snapshot.dashboardGroup(it) },
        finances = FinancialSummary(
            balancesByGroup = groups.associate { it.id to MemberBalance(snapshot.currentUserId, snapshot.myNet(it.id), it.currency) },
            outstandingPayments = snapshot.outstandingPayments(),
        ),
        paidOutPayments = snapshot.settlements
            .filter { it.debtorId == snapshot.currentUserId && it.status == SettlementStatus.COMPLETED }
            .map { PaidOutPayment(it, snapshot.person(it.creditorId).name) },
        hasUnreadActivity = snapshot.activityFeed(now).any { it.isUnread },
        nowMillis = now,
        sync = SyncPresentation(savedAt = snapshot.lastSyncedAt, offlineNote = LedgerDashboardSource.OFFLINE_NOTE),
    )
}

/**
 * What Activity shows, derived from the ledger.
 *
 * @param keepFailures expenses whose "couldn't sync" card stays after a retry (see [activityFeed]).
 */
fun activityData(snapshot: LedgerSnapshot, now: Long, keepFailures: Set<String> = emptySet()): ActivityData {
    val groups = snapshot.myGroups().associate { it.id to snapshot.dashboardGroup(it) }
    val payments = snapshot.outstandingPayments()
    val events = snapshot.activityFeed(now, keepFailures).mapNotNull { entry ->
        val group = groups[entry.groupId] ?: return@mapNotNull null
        snapshot.activityEvent(entry, group, payments)
    }
    return ActivityData(
        userId = snapshot.currentUserId,
        events = events,
        nowMillis = now,
        sync = SyncPresentation(savedAt = snapshot.lastSyncedAt, offlineNote = LedgerDashboardSource.OFFLINE_NOTE),
    )
}

/**
 * What the user owes, one entry per payment in each group's settle-up plan. The id names the exact
 * obligation (group, both people and the amount), so a changed balance gets a new id.
 */
private fun LedgerSnapshot.outstandingPayments(): List<OutstandingPayment> = myDebts().map { debt ->
    OutstandingPayment(
        id = "pay:${debt.groupId}:$currentUserId:${debt.creditorId}:${debt.amountCents}",
        groupId = debt.groupId,
        creditorName = person(debt.creditorId).name,
        since = debt.since,
        transfer = DebtTransfer(currentUserId, debt.creditorId, debt.amountCents, group(debt.groupId)?.currency ?: "DKK"),
    )
}

/** Shared with the Groups tab adapter, so Home and Groups describe a group the same way. */
internal fun LedgerSnapshot.dashboardGroup(group: Group) = DashboardGroup(
    group = group,
    colourIndex = group.colorIndex,
    isFavourite = group.id in favouriteGroupIds,
    lastActivityAt = lastActivityAt(group.id),
    pendingExpenseCount = unsyncedExpenseCount(group.id),
)

internal fun Person.toUser() = User(id = id, email = contact, displayName = name)

private fun LedgerSnapshot.activityEvent(
    entry: ActivityEntry,
    group: DashboardGroup,
    payments: List<OutstandingPayment>,
): ActivityEvent {
    val actor = entry.actorId?.let { person(it) }
    val expense = entry.expenseId?.let { id -> expenses.firstOrNull { it.id == id } }
    return ActivityEvent(
        id = entry.id,
        type = when (entry.type) {
            ActivityEntryType.REMINDER -> ActivityType.Reminder
            ActivityEntryType.ADDED -> ActivityType.ExpenseAdded
            ActivityEntryType.FAILED -> ActivityType.SyncFailure
            ActivityEntryType.PAID -> ActivityType.Payment
        },
        actor = actor?.toUser(),
        actorColourIndex = actor?.paletteIndex ?: 0,
        description = entry.text,
        group = group,
        occurredAt = entry.createdAt,
        amountCents = entry.amountCents,
        currency = group.group.currency,
        direction = when (entry.direction) {
            MoneyDirection.OUT -> AmountDirection.Owed
            MoneyDirection.IN -> AmountDirection.Received
            MoneyDirection.SETTLE -> AmountDirection.Settled
            MoneyDirection.NONE -> AmountDirection.Neutral
        },
        unread = entry.isUnread,
        // Same OutstandingPayment (and id) as Home, so paying from either screen is the same payment.
        payment = entry.payCreditorId?.let { creditor ->
            payments.firstOrNull { it.groupId == entry.groupId && it.transfer.creditorId == creditor }
        },
        syncStatus = expense?.syncStatus,
        expenseTitle = expense?.title,
    )
}
