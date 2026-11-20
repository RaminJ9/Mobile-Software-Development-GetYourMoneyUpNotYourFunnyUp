package com.example.getyourmoneyupnotyourfunnyup.data.repository

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.remote.ReminderMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SyncPayloadJson
import com.example.getyourmoneyupnotyourfunnyup.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ReminderRepository {
    suspend fun remind(accountId: String, groupId: String, transfer: DebtTransfer, obligationId: String): Reminder
}

class LocalReminderRepository(private val database: AppDatabase, private val currentAccountId: () -> String?) : ReminderRepository {
    override suspend fun remind(accountId: String, groupId: String, transfer: DebtTransfer, obligationId: String): Reminder = withContext(Dispatchers.IO) {
        fun checkAccount() { if (accountId.isBlank() || currentAccountId() != accountId || transfer.creditorId != accountId) throw SecurityException("Only the creditor can send this reminder.") }
        checkAccount()
        val result = database.writableDatabase.transaction {
            checkAccount()
            val members = database.groupDao.getMembersForGroup(groupId)
            if (members.none { it.userId == accountId } || members.none { it.userId == transfer.debtorId }) throw SecurityException("Both members must belong to this group.")
            require(database.groupDao.getGroupById(groupId)?.currency == "DKK" && transfer.currency == "DKK")
            require(database.userDao.getUserById(transfer.debtorId)?.notificationSettings?.paymentReminders == true) { "This member has payment reminders turned off." }
            val splits = database.expenseDao.getSplitsForGroup(groupId).groupBy { it.expenseId }
            val expenses = database.expenseDao.getActiveExpensesForGroup(groupId).map { it.toDomain(splits[it.id].orEmpty().map { s -> s.toDomain() }) }
            val settlements = database.settlementDao.getSettlementsForGroup(groupId).map { it.toDomain() }
            require(transfer in SettlementCalculator.suggest(BalanceCalculator.calculate(groupId, "DKK", members.map { it.userId }, expenses, settlements))) { "This balance changed." }
            require(obligationId == SettlementReference.id(groupId, transfer, expenses, settlements)) { "This reminder is out of date." }
            val id = "reminder-$obligationId"
            database.reminderDao.get(id) ?: Reminder(id, groupId, transfer.debtorId, transfer.creditorId, transfer.amountCents,
                transfer.currency, obligationId, System.currentTimeMillis()).also { reminder ->
                database.reminderDao.save(reminder)
                insertOrThrow("sync_queue", null, ContentValues().apply {
                    put("entity_type", "REMINDER"); put("entity_id", id); put("action", "CREATE")
                    put("payload_json", SyncPayloadJson.encode(ReminderMapper.toMap(reminder) + ("queuedBy" to accountId))); put("retry_count", 0)
                })
                checkAccount()
            }
        }
        database.notifyTableChanged("reminders", "sync_queue")
        result
    }
}
