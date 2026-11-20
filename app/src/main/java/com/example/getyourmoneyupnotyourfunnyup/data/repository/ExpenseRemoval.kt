package com.example.getyourmoneyupnotyourfunnyup.data.repository

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpensesTable as Expenses
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable as Queue
import com.example.getyourmoneyupnotyourfunnyup.data.remote.ExpenseDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SyncPayloadJson
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ExpenseRemoval {
    suspend fun remove(accountId: String, groupId: String, expected: Expense): SyncStatus
}

/** S9-confirm: authenticated, idempotent soft removal and outbox write in the same transaction. */
class LocalExpenseRemoval(private val database: AppDatabase, private val currentAccountId: () -> String?) : ExpenseRemoval {
    override suspend fun remove(accountId: String, groupId: String, expected: Expense): SyncStatus = withContext(Dispatchers.IO) {
        fun checkAccount() { if (accountId.isBlank() || currentAccountId() != accountId) throw SecurityException("Your session changed.") }
        checkAccount()
        val result = database.writableDatabase.transaction {
            checkAccount()
            val expense = requireNotNull(database.expenseDao.getExpenseById(expected.id)) { "Expense is no longer available." }
            require(expense.groupId == groupId && expected.groupId == groupId) { "This expense belongs to another group." }
            if (database.groupDao.getMembersForGroup(groupId).none { it.userId == accountId } ||
                accountId != expense.payerId && accountId != expense.createdBy) throw SecurityException("Only the payer or creator can remove this expense.")
            if (expense.isDeleted) expense.syncStatus else {
                val splits = database.expenseDao.getSplitsForExpense(expense.id)
                require(expense.toDomain(splits.map { it.toDomain() }).copy(syncStatus = expected.syncStatus, receiptLocalUri = expected.receiptLocalUri) == expected) {
                    "The expense changed. Review it before removing."
                }
                val deleted = expense.copy(isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE)
                check(update(Expenses.TABLE_NAME, ContentValues().apply {
                    put(Expenses.COL_IS_DELETED, 1); put(Expenses.COL_SYNC_STATUS, SyncStatus.PENDING_DELETE.name)
                }, "${Expenses.COL_ID} = ?", arrayOf(expense.id)) == 1)
                insertOrThrow(Queue.TABLE_NAME, null, ContentValues().apply {
                    put(Queue.COL_ENTITY_TYPE, "EXPENSE"); put(Queue.COL_ENTITY_ID, expense.id)
                    put(Queue.COL_ACTION, "DELETE")
                    put(Queue.COL_PAYLOAD_JSON, SyncPayloadJson.encode(ExpenseDocumentMapper.toMap(deleted, splits) + ("queuedBy" to accountId)))
                    put(Queue.COL_RETRY_COUNT, 0)
                })
                checkAccount()
                SyncStatus.PENDING_DELETE
            }
        }
        database.notifyTableChanged(Expenses.TABLE_NAME, Queue.TABLE_NAME)
        result
    }
}
