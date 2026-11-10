package com.example.getyourmoneyupnotyourfunnyup.model

/*
 * Read-side queries over a [LedgerSnapshot]. Pure functions, shared by every ViewModel and covered by
 * unit tests, so balances shown on Home, Groups and the group screen always agree.
 */

/** Groups the signed-in user is a member of. */
fun LedgerSnapshot.myGroups(): List<Group> =
    groups.filter { group -> group.members.any { it.userId == currentUserId } }

fun LedgerSnapshot.memberIds(groupId: String): List<String> =
    group(groupId)?.members?.map { it.userId }.orEmpty()

fun LedgerSnapshot.activeExpenses(groupId: String): List<Expense> =
    expenses.filter { it.groupId == groupId && !it.isDeleted }

fun LedgerSnapshot.groupSettlements(groupId: String): List<Settlement> =
    settlements.filter { it.groupId == groupId }

/** Net balance per person in a group (members plus anyone who still appears in the ledger). */
fun LedgerSnapshot.netBalances(groupId: String): Map<String, Long> =
    DebtCalculator.netBalances(memberIds(groupId), activeExpenses(groupId), groupSettlements(groupId))

/** The fewest payments that clear every balance in the group. */
fun LedgerSnapshot.settlePlan(groupId: String): List<DebtTransfer> =
    DebtCalculator.simplify(netBalances(groupId), group(groupId)?.currency ?: "DKK")

/** The signed-in user's net balance in a group (negative = owes). */
fun LedgerSnapshot.myNet(groupId: String): Long = netBalances(groupId)[currentUserId] ?: 0L

/** Most recent thing that happened in a group. */
fun LedgerSnapshot.lastActivityAt(groupId: String): Long {
    val group = group(groupId) ?: return 0L
    val expenseTimes = expenses.filter { it.groupId == groupId }.map { it.createdAt }
    val settlementTimes = groupSettlements(groupId).map { it.createdAt }
    return (expenseTimes + settlementTimes + group.createdAt).max()
}

/** Expenses in a group that are saved on the phone but not yet confirmed by the backend. */
fun LedgerSnapshot.unsyncedExpenseCount(groupId: String): Int =
    expenses.count { it.groupId == groupId && it.syncStatus != SyncStatus.SYNCED }

/** A debt the signed-in user has to pay, from the group's settle-up plan. */
data class MyDebt(
    val groupId: String,
    val creditorId: String,
    val amountCents: Long,
    val since: Long
)

/** Everything the signed-in user owes, biggest first. */
fun LedgerSnapshot.myDebts(): List<MyDebt> = myGroups().flatMap { group ->
    val since = activeExpenses(group.id)
        .filter { it.payerId != currentUserId }
        .maxOfOrNull { it.createdAt } ?: group.createdAt
    settlePlan(group.id)
        .filter { it.debtorId == currentUserId }
        .map { MyDebt(group.id, it.creditorId, it.amountCents, since) }
}.sortedByDescending { it.amountCents }

/** Sum of positive balances across groups (money owed to the user). */
fun LedgerSnapshot.totalOwedToMe(): Long = myGroups().sumOf { maxOf(0L, myNet(it.id)) }

/** What the user paid out per group in a period: expenses they paid plus completed payments they made. */
fun LedgerSnapshot.paidOutByGroup(period: Period, now: Long): Map<String, Long> {
    val totals = LinkedHashMap<String, Long>()
    expenses.filter { !it.isDeleted && it.payerId == currentUserId && DateLabels.isInPeriod(it.createdAt, period, now) }
        .forEach { totals[it.groupId] = (totals[it.groupId] ?: 0L) + it.amountCents }
    settlements.filter {
        it.debtorId == currentUserId && it.status == SettlementStatus.COMPLETED &&
            DateLabels.isInPeriod(it.createdAt, period, now)
    }.forEach { totals[it.groupId] = (totals[it.groupId] ?: 0L) + it.amountCents }
    return totals.filterValues { it > 0 }.entries.sortedByDescending { it.value }
        .associateTo(LinkedHashMap()) { it.key to it.value }
}

/** "1.200,00 kr to Mads · Sun 1 Nov" — the user's latest payment in a group, if any. */
fun LedgerSnapshot.lastPaymentLabel(groupId: String, now: Long): String? {
    val expense = expenses.filter { it.groupId == groupId && !it.isDeleted && it.payerId == currentUserId }
        .maxByOrNull { it.createdAt }
    val settlement = settlements.filter {
        it.groupId == groupId && it.debtorId == currentUserId && it.status == SettlementStatus.COMPLETED
    }.maxByOrNull { it.createdAt }
    return when {
        settlement != null && (expense == null || settlement.createdAt >= expense.createdAt) ->
            "${MoneyFormat.kr(settlement.amountCents)} kr to ${person(settlement.creditorId).name} · " +
                DateLabels.relativeDay(settlement.createdAt, now)
        expense != null ->
            "${MoneyFormat.kr(expense.amountCents)} kr · ${expense.title} · " +
                DateLabels.relativeDay(expense.createdAt, now)
        else -> null
    }
}

enum class ActivityType { REMINDER, ADDED, FAILED, PAID }

enum class MoneyDirection { OUT, IN, SETTLE, NONE }

/** One row in the Activity tab. */
data class ActivityEntry(
    val id: String,
    val type: ActivityType,
    val actorId: String?,
    val actorLabel: String,
    val text: String,
    val groupId: String,
    val createdAt: Long,
    val amountCents: Long?,
    val direction: MoneyDirection,
    val isUnread: Boolean,
    /** Who to pay for a reminder (the creditor), null otherwise. */
    val payCreditorId: String? = null,
    /** The expense to retry for a failed sync, null otherwise. */
    val expenseId: String? = null
)

private const val UNREAD_WINDOW_MS = 24L * 60 * 60 * 1000

/** The signed-in user's activity feed, newest first. */
fun LedgerSnapshot.activityFeed(now: Long): List<ActivityEntry> {
    val mine = myGroups().map { it.id }.toSet()
    val entries = mutableListOf<ActivityEntry>()
    fun unread(id: String, at: Long) = now - at < UNREAD_WINDOW_MS && id !in readActivityIds

    reminders.filter { it.toId == currentUserId && it.groupId in mine }.forEach { reminder ->
        val id = "rem:${reminder.id}"
        val groupName = group(reminder.groupId)?.name ?: "the group"
        val owed = settlePlan(reminder.groupId)
            .firstOrNull { it.debtorId == currentUserId && it.creditorId == reminder.fromId }?.amountCents
        entries += ActivityEntry(
            id = id, type = ActivityType.REMINDER, actorId = reminder.fromId,
            actorLabel = person(reminder.fromId).name, text = "reminded you to pay for $groupName",
            groupId = reminder.groupId, createdAt = reminder.createdAt, amountCents = owed,
            direction = MoneyDirection.OUT, isUnread = unread(id, reminder.createdAt),
            payCreditorId = reminder.fromId
        )
    }
    expenses.filter { it.groupId in mine && !it.isDeleted }.forEach { expense ->
        val myShare = expense.splits.firstOrNull { it.userId == currentUserId }?.amountOwedCents ?: 0L
        if (expense.payerId != currentUserId && myShare > 0) {
            val id = "exp:${expense.id}"
            entries += ActivityEntry(
                id = id, type = ActivityType.ADDED, actorId = expense.payerId,
                actorLabel = person(expense.payerId).name, text = "added “${expense.title}” · your share",
                groupId = expense.groupId, createdAt = expense.createdAt, amountCents = myShare,
                direction = MoneyDirection.OUT, isUnread = unread(id, expense.createdAt)
            )
        }
        if (expense.payerId == currentUserId && expense.syncStatus == SyncStatus.FAILED) {
            val id = "fail:${expense.id}"
            entries += ActivityEntry(
                id = id, type = ActivityType.FAILED, actorId = null, actorLabel = expense.title,
                text = "couldn’t sync yet – retrying automatically", groupId = expense.groupId,
                createdAt = expense.createdAt, amountCents = null, direction = MoneyDirection.NONE,
                isUnread = id !in readActivityIds, expenseId = expense.id
            )
        }
    }
    settlements.filter {
        it.groupId in mine && it.status == SettlementStatus.COMPLETED &&
            (it.debtorId == currentUserId || it.creditorId == currentUserId)
    }.forEach { settlement ->
        val id = "set:${settlement.id}"
        val iPaid = settlement.debtorId == currentUserId
        entries += ActivityEntry(
            id = id, type = ActivityType.PAID,
            actorId = settlement.debtorId,
            actorLabel = if (iPaid) "You" else person(settlement.debtorId).name,
            text = if (iPaid) "paid ${person(settlement.creditorId).name}" else "paid you",
            groupId = settlement.groupId, createdAt = settlement.createdAt, amountCents = settlement.amountCents,
            direction = if (iPaid) MoneyDirection.SETTLE else MoneyDirection.IN,
            isUnread = unread(id, settlement.createdAt)
        )
    }
    return entries.sortedByDescending { it.createdAt }
}

