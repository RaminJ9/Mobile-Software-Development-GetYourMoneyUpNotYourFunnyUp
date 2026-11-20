package com.example.getyourmoneyupnotyourfunnyup.data.repository

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger

/** Account-scoped, read-only financial snapshots; Home and Activity use the group ledger algorithms. */
class DashboardDataSource(private val database: AppDatabase, private val scheduleRetry: suspend (String) -> Unit = {}, private val currentAccountId: () -> String?) {
    private val watched = arrayOf("users", "groups", "group_members", "expenses", "expense_splits", "settlements", "sync_queue", "reminders", "activity_reads")
    fun observeHomeData(user: User): Flow<HomeData> = database.observeTables(*watched).map { withContext(Dispatchers.IO) { snapshot(user).first } }
    fun observeActivityData(user: User): Flow<ActivityData> = database.observeTables(*watched).map { withContext(Dispatchers.IO) { snapshot(user).second } }
    private fun checkAccount(id: String) { if (id.isBlank() || currentAccountId() != id) throw SecurityException("Your session changed. Sign in again.") }

    val settlementActions = SettlementActions { payment ->
        val account = currentAccountId() ?: throw SecurityException("Sign in again.")
        val sourceUser = withContext(Dispatchers.IO) { database.userDao.getUserById(account)?.toDomain() }
            ?: throw SecurityException("Profile is unavailable.")
        val current = withContext(Dispatchers.IO) { snapshot(sourceUser).first }
        val alreadyRecorded = withContext(Dispatchers.IO) { database.settlementDao.getSettlementById(payment.id) != null }
        require(alreadyRecorded || current.finances?.outstandingPayments?.any { it.id == payment.id && it.transfer == payment.transfer && it.groupId == payment.groupId } == true) {
            "This balance changed. Review the current payment."
        }
        LocalPaymentRepository(database = database, currentAccountId = currentAccountId)
            .pay(account, payment.groupId, payment.transfer, payment.id).syncStatus
    }

    suspend fun markRead(accountId: String, ids: Set<String>) = withContext(Dispatchers.IO) {
        checkAccount(accountId)
        database.writableDatabase.transaction {
            checkAccount(accountId)
            ids.forEach { id ->
                insertWithOnConflict("activity_reads", null, ContentValues().apply {
                    put("user_id", accountId); put("event_id", id); put("read_at", System.currentTimeMillis())
                }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
            }
            checkAccount(accountId)
        }
        database.notifyTableChanged("activity_reads")
    }

    val syncRetryActions = SyncRetryActions { event -> withContext(Dispatchers.IO) {
        val account = currentAccountId() ?: throw SecurityException("Sign in again.")
        checkAccount(account)
        val status = database.writableDatabase.transaction {
            if (database.groupDao.getMembersForGroup(event.group.group.id).none { it.userId == account }) throw SecurityException("No group access.")
            val reminder = database.reminderDao.get(event.id)
            val expense = database.expenseDao.getExpenseById(event.id)
            val type = if (reminder != null) "REMINDER" else "EXPENSE"
            val queued = database.syncQueueDao.getAllPending().firstOrNull { it.entityType.name == type && it.entityId == event.id }
                ?: throw IllegalArgumentException("This item has no pending operation.")
            if (reminder != null) {
                if (reminder.creditorId != account) throw SecurityException("Only your own reminder can be retried.")
            } else if (expense == null || (expense.createdBy != account && expense.payerId != account)) throw SecurityException("Only your own expense can be retried.")
            val next = when (queued.action.name) { "CREATE" -> SyncStatus.PENDING_INSERT; "DELETE" -> SyncStatus.PENDING_DELETE; else -> SyncStatus.PENDING_UPDATE }
            update(if (reminder != null) "reminders" else "expenses", ContentValues().apply { put("sync_status", next.name) }, "id = ?", arrayOf(event.id))
            checkAccount(account)
            next
        }
        database.notifyTableChanged("expenses", "reminders", "sync_queue")
        scheduleRetry(account)
        status
    } }

    private fun snapshot(initial: User): Pair<HomeData, ActivityData> {
        checkAccount(initial.id)
        val result = database.readableDatabase.transaction(exclusive = false) {
            val user = database.userDao.getUserById(initial.id)?.toDomain() ?: initial
            val users = database.userDao.getAllUsers().associate { it.id to it.toDomain() }
            fun name(id: String) = users[id]?.displayName?.takeIf { it.isNotBlank() } ?: "Member"
            val readIds = mutableSetOf<String>()
            rawQuery("SELECT event_id FROM activity_reads WHERE user_id = ?", arrayOf(user.id)).use { c -> while (c.moveToNext()) readIds += c.getString(0) }
            val balances = mutableMapOf<String, MemberBalance>()
            val payments = mutableListOf<OutstandingPayment>()
            val history = mutableListOf<PaidOutPayment>()
            val events = mutableListOf<ActivityEvent>()
            val receiptStatuses = mutableMapOf<String, SyncStatus>()
            var valid = true
            val groups = database.groupDao.getGroupsForUser(user.id).map { entity ->
                val members = database.groupDao.getMembersForGroup(entity.id).map { it.toDomain(name(it.userId)) }
                val splitRows = database.expenseDao.getSplitsForGroup(entity.id).groupBy { it.expenseId }
                val expenses = database.expenseDao.getActiveExpensesForGroup(entity.id).map { it.toDomain(splitRows[it.id].orEmpty().map { share -> share.toDomain() }) }
                val settlements = database.settlementDao.getSettlementsForGroup(entity.id).map { it.toDomain() }
                val group = DashboardGroup(entity.toDomain(members), entity.colourIndex, entity.isFavourite,
                    (listOf(entity.updatedAt) + expenses.map { it.createdAt } + settlements.map { it.createdAt }).maxOrNull() ?: entity.updatedAt,
                    expenses.count { it.syncStatus != SyncStatus.SYNCED })
                if (entity.currency == "DKK") {
                    try {
                        val net = BalanceCalculator.calculate(entity.id, entity.currency, members.map { it.userId }, expenses, settlements)
                        val own = net.firstOrNull { it.userId == user.id } ?: MemberBalance(user.id, 0, "DKK")
                        if (own.netBalanceCents == Long.MIN_VALUE) valid = false
                        balances[entity.id] = own
                        SettlementCalculator.suggest(net).filter { it.debtorId == user.id }.forEach { transfer ->
                            payments += OutstandingPayment(SettlementReference.id(entity.id, transfer, expenses, settlements), entity.id,
                                name(transfer.creditorId), expenses.minOfOrNull { it.createdAt } ?: entity.updatedAt, transfer)
                        }
                    } catch (_: IllegalArgumentException) { valid = false
                    } catch (_: ArithmeticException) { valid = false }
                } else valid = false
                expenses.forEach { expense ->
                    val share = expense.splits.firstOrNull { it.userId == user.id }?.amountOwedCents
                    val failed = expense.syncStatus == SyncStatus.FAILED
                    val id = if (failed) expense.id else "expense-${expense.id}"
                    events += ActivityEvent(id, if (failed) ActivityType.SyncFailure else ActivityType.ExpenseAdded,
                        users[expense.payerId], group.colourIndex,
                        if (failed) "couldn't sync this expense" else "added ${expense.title}", group, expense.createdAt,
                        amountCents = if (expense.currency == "DKK") share else null,
                        direction = if (share != null && expense.payerId != user.id) AmountDirection.Owed else AmountDirection.Neutral,
                        unread = share != null && user.notificationSettings.expenseAlerts && expense.createdBy != user.id && id !in readIds, syncStatus = expense.syncStatus, expenseTitle = expense.title)
                }
                settlements.filter { it.status == SettlementStatus.COMPLETED && it.currency == "DKK" }.forEach { payment ->
                    receiptStatuses[payment.id] = payment.syncStatus
                    if (payment.debtorId == user.id) history += PaidOutPayment(payment, name(payment.creditorId))
                    val id = "payment-${payment.id}"
                    events += ActivityEvent(id, ActivityType.Payment, users[payment.debtorId], group.colourIndex,
                        "paid ${if (payment.creditorId == user.id) "you" else name(payment.creditorId)}", group, payment.createdAt,
                        amountCents = payment.amountCents, direction = when (user.id) {
                            payment.debtorId -> AmountDirection.Settled; payment.creditorId -> AmountDirection.Received; else -> AmountDirection.Neutral
                        }, unread = payment.creditorId == user.id && id !in readIds, syncStatus = payment.syncStatus)
                }
                database.reminderDao.forGroup(entity.id).filter { it.creditorId == user.id || it.debtorId == user.id }.forEach { r ->
                    val incoming = r.debtorId == user.id
                    if (!incoming || user.notificationSettings.paymentReminders) {
                        val payment = OutstandingPayment(r.obligationId, r.groupId, name(r.creditorId), r.createdAt,
                            DebtTransfer(r.debtorId, r.creditorId, r.amountCents, r.currency))
                        val failed = !incoming && r.syncStatus == SyncStatus.FAILED
                        events += ActivityEvent(r.id, if (failed) ActivityType.SyncFailure else ActivityType.Reminder,
                            users[r.creditorId], group.colourIndex,
                            if (incoming) "reminded you to pay" else if (failed) "couldn't sync this reminder" else "reminded ${name(r.debtorId)} to pay",
                            group, r.createdAt, r.amountCents, direction = if (receiptStatuses.containsKey(r.obligationId)) AmountDirection.Settled
                                else if (incoming && payments.any { it.id == r.obligationId }) AmountDirection.Owed else AmountDirection.Neutral,
                            unread = incoming && r.id !in readIds, payment = if (incoming) payment else null, syncStatus = r.syncStatus,
                            paymentAvailable = incoming && payments.any { it.id == r.obligationId })
                    }
                }
                group
            }
            val outgoingTotal = payments.fold(BigInteger.ZERO) { sum, p -> sum + BigInteger.valueOf(p.transfer.amountCents) }
            val incomingTotal = balances.values.fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.netBalanceCents.coerceAtLeast(0)) }
            if (outgoingTotal.bitLength() > 63 || incomingTotal.bitLength() > 63) valid = false
            val sync = SyncPresentation(pendingCount = com.example.getyourmoneyupnotyourfunnyup.data.sync.DatabaseSyncQueueStore(database).pendingItemsForAccount(user.id).size)
            val home = HomeData(user, groups, if (valid) FinancialSummary(balances, payments) else null,
                paidOutPayments = history, hasUnreadActivity = events.any { it.unread }, sync = sync)
            checkAccount(initial.id)
            home to ActivityData(user.id, events.sortedByDescending { it.occurredAt }, sync = sync, paymentStatuses = receiptStatuses)
        }
        checkAccount(initial.id)
        return result
    }
}
