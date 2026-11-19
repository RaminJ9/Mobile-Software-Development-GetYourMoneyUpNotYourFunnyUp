package com.example.getyourmoneyupnotyourfunnyup.data.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpenseSplitsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpensesTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseSplitEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.data.remote.ExpenseDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SyncPayloadJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first repository for creating, updating, deleting and caching [Expense] records
 * and their [ExpenseSplit] rows.
 *
 * Every on-device mutation commits to local SQLite together with a `sync_queue` entry in one
 * transaction, then triggers [onEnqueued] so the remote sync processor pushes it to Cloud Firestore.
 */
class ExpenseRepository(
    private val database: AppDatabase,
    private val onEnqueued: (() -> Unit)? = null
) {

    fun observeExpensesForGroup(groupId: String): Flow<List<Expense>> =
        database.observeTables(ExpensesTable.TABLE_NAME, ExpenseSplitsTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) {
                val splitsByExpense = database.expenseDao.getSplitsForGroup(groupId).groupBy { it.expenseId }
                database.expenseDao.getActiveExpensesForGroup(groupId).map { entity ->
                    entity.toDomain(splitsByExpense[entity.id].orEmpty().map { it.toDomain() })
                }
            }
        }

    suspend fun saveExpense(
        expense: Expense,
        splits: List<ExpenseSplit> = expense.splits
    ): Expense = withContext(Dispatchers.IO) {
        require(expense.id.isNotBlank() && expense.groupId.isNotBlank()) { "Expense ID and group ID are required." }
        require(expense.title.isNotBlank()) { "Enter an expense title." }
        require(expense.amountCents >= 0L) { "Expense amount cannot be negative." }

        val existing = database.expenseDao.getExpenseById(expense.id)
        val isCreate = existing == null || existing.syncStatus == SyncStatus.PENDING_INSERT
        val nextStatus = if (isCreate) SyncStatus.PENDING_INSERT else SyncStatus.PENDING_UPDATE
        val action = if (isCreate) SyncAction.CREATE else SyncAction.UPDATE

        val normalizedSplits = splits.map { split ->
            if (split.expenseId == expense.id) split else split.copy(expenseId = expense.id)
        }
        val toSave = expense.copy(
            splits = normalizedSplits,
            syncStatus = nextStatus
        )
        val entity = ExpenseEntity.fromDomain(toSave)
        val splitEntities = normalizedSplits.map(ExpenseSplitEntity::fromDomain)
        val payloadJson = SyncPayloadJson.encode(ExpenseDocumentMapper.toMap(entity, splitEntities))

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                ExpensesTable.TABLE_NAME,
                null,
                entity.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.delete(
                ExpenseSplitsTable.TABLE_NAME,
                "${ExpenseSplitsTable.COL_EXPENSE_ID} = ?",
                arrayOf(entity.id)
            )
            for (split in splitEntities) {
                db.insertWithOnConflict(
                    ExpenseSplitsTable.TABLE_NAME,
                    null,
                    split.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            val queueId = db.insertOrThrow(
                SyncQueueTable.TABLE_NAME,
                null,
                ContentValues().apply {
                    put(SyncQueueTable.COL_ENTITY_TYPE, Converters.fromSyncEntityType(SyncEntityType.EXPENSE))
                    put(SyncQueueTable.COL_ENTITY_ID, entity.id)
                    put(SyncQueueTable.COL_ACTION, Converters.fromSyncAction(action))
                    put(SyncQueueTable.COL_PAYLOAD_JSON, payloadJson)
                    put(SyncQueueTable.COL_RETRY_COUNT, 0)
                }
            )
            check(queueId != -1L) { "Couldn't queue expense for syncing." }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        database.notifyTableChanged(
            ExpensesTable.TABLE_NAME,
            ExpenseSplitsTable.TABLE_NAME,
            SyncQueueTable.TABLE_NAME
        )
        onEnqueued?.invoke()
        toSave
    }

    suspend fun deleteExpense(
        expenseId: String,
        actorUserId: String
    ): SyncStatus = withContext(Dispatchers.IO) {
        val existing = requireNotNull(database.expenseDao.getExpenseById(expenseId)) {
            "This expense is no longer available."
        }
        if (actorUserId != existing.payerId && actorUserId != existing.createdBy) {
            throw SecurityException("Only the payer or creator can remove this expense.")
        }
        val splits = database.expenseDao.getSplitsForExpense(expenseId)
        val deletedEntity = existing.copy(
            isDeleted = true,
            syncStatus = SyncStatus.PENDING_DELETE
        )
        val payloadJson = SyncPayloadJson.encode(ExpenseDocumentMapper.toMap(deletedEntity, splits))

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.update(
                ExpensesTable.TABLE_NAME,
                ContentValues().apply {
                    put(ExpensesTable.COL_IS_DELETED, 1)
                    put(ExpensesTable.COL_SYNC_STATUS, Converters.fromSyncStatus(SyncStatus.PENDING_DELETE))
                },
                "${ExpensesTable.COL_ID} = ?",
                arrayOf(expenseId)
            )
            val queueId = db.insertOrThrow(
                SyncQueueTable.TABLE_NAME,
                null,
                ContentValues().apply {
                    put(SyncQueueTable.COL_ENTITY_TYPE, Converters.fromSyncEntityType(SyncEntityType.EXPENSE))
                    put(SyncQueueTable.COL_ENTITY_ID, expenseId)
                    put(SyncQueueTable.COL_ACTION, Converters.fromSyncAction(SyncAction.DELETE))
                    put(SyncQueueTable.COL_PAYLOAD_JSON, payloadJson)
                    put(SyncQueueTable.COL_RETRY_COUNT, 0)
                }
            )
            check(queueId != -1L) { "Couldn't queue expense deletion for syncing." }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        database.notifyTableChanged(ExpensesTable.TABLE_NAME, SyncQueueTable.TABLE_NAME)
        onEnqueued?.invoke()
        SyncStatus.PENDING_DELETE
    }

    suspend fun cacheRemoteExpense(
        data: Map<String, Any?>,
        expenseId: String,
        fallbackGroupId: String = ""
    ): Expense = withContext(Dispatchers.IO) {
        val local = database.expenseDao.getExpenseById(expenseId)
        if (local != null && local.syncStatus != SyncStatus.SYNCED) {
            val localSplits = database.expenseDao.getSplitsForExpense(expenseId).map { it.toDomain() }
            return@withContext local.toDomain(localSplits)
        }
        val (remoteEntity, remoteSplits) = ExpenseDocumentMapper.entitiesFromMap(
            data = data,
            expenseId = expenseId,
            fallbackGroupId = fallbackGroupId,
            receiptLocalUri = local?.receiptLocalUri
        )
        database.expenseDao.upsertExpenseWithSplits(remoteEntity, remoteSplits)
        remoteEntity.toDomain(remoteSplits.map { it.toDomain() })
    }

    private fun ExpenseEntity.toContentValues(): ContentValues = ContentValues().apply {
        put(ExpensesTable.COL_ID, id)
        put(ExpensesTable.COL_GROUP_ID, groupId)
        put(ExpensesTable.COL_TITLE, title)
        put(ExpensesTable.COL_AMOUNT_CENTS, amountCents)
        put(ExpensesTable.COL_CURRENCY, currency)
        put(ExpensesTable.COL_PAYER_ID, payerId)
        put(ExpensesTable.COL_CATEGORY, category)
        put(ExpensesTable.COL_NOTES, notes)
        put(ExpensesTable.COL_RECEIPT_LOCAL_URI, receiptLocalUri)
        put(ExpensesTable.COL_RECEIPT_REMOTE_PATH, receiptRemotePath)
        put(ExpensesTable.COL_CREATED_AT, createdAt)
        put(ExpensesTable.COL_CREATED_BY, createdBy)
        put(ExpensesTable.COL_IS_DELETED, if (isDeleted) 1 else 0)
        put(ExpensesTable.COL_SYNC_STATUS, Converters.fromSyncStatus(syncStatus))
    }

    private fun ExpenseSplitEntity.toContentValues(): ContentValues = ContentValues().apply {
        put(ExpenseSplitsTable.COL_ID, id)
        put(ExpenseSplitsTable.COL_EXPENSE_ID, expenseId)
        put(ExpenseSplitsTable.COL_USER_ID, userId)
        put(ExpenseSplitsTable.COL_AMOUNT_OWED_CENTS, amountOwedCents)
        put(ExpenseSplitsTable.COL_SHARE_WEIGHT, shareWeight)
    }
}
