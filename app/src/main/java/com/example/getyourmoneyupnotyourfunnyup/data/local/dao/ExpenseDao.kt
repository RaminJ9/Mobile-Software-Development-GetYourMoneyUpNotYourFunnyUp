package com.example.getyourmoneyupnotyourfunnyup.data.local.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpenseSplitsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpensesTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseSplitEntity
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExpenseDao(private val database: AppDatabase) {

    fun observeExpensesForGroup(groupId: String): Flow<List<ExpenseEntity>> =
        database.observeTables(ExpensesTable.TABLE_NAME, ExpenseSplitsTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getActiveExpensesForGroup(groupId) }
        }

    fun observeSplitsForExpense(expenseId: String): Flow<List<ExpenseSplitEntity>> =
        database.observeTables(ExpenseSplitsTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getSplitsForExpense(expenseId) }
        }

    fun upsertExpenseWithSplits(expense: ExpenseEntity, splits: List<ExpenseSplitEntity>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                ExpensesTable.TABLE_NAME,
                null,
                expense.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.delete(
                ExpenseSplitsTable.TABLE_NAME,
                "${ExpenseSplitsTable.COL_EXPENSE_ID} = ?",
                arrayOf(expense.id)
            )
            for (split in splits) {
                db.insertWithOnConflict(
                    ExpenseSplitsTable.TABLE_NAME,
                    null,
                    split.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.notifyTableChanged(ExpensesTable.TABLE_NAME, ExpenseSplitsTable.TABLE_NAME)
    }

    fun getExpenseById(expenseId: String): ExpenseEntity? {
        val db = database.readableDatabase
        db.query(
            ExpensesTable.TABLE_NAME,
            null,
            "${ExpensesTable.COL_ID} = ?",
            arrayOf(expenseId),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toExpenseEntity() else null
        }
    }

    fun getActiveExpensesForGroup(groupId: String): List<ExpenseEntity> {
        val db = database.readableDatabase
        val expenses = mutableListOf<ExpenseEntity>()
        db.query(
            ExpensesTable.TABLE_NAME,
            null,
            "${ExpensesTable.COL_GROUP_ID} = ? AND ${ExpensesTable.COL_IS_DELETED} = 0",
            arrayOf(groupId),
            null,
            null,
            "${ExpensesTable.COL_CREATED_AT} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                expenses.add(cursor.toExpenseEntity())
            }
        }
        return expenses
    }

    fun getSplitsForExpense(expenseId: String): List<ExpenseSplitEntity> {
        val db = database.readableDatabase
        val splits = mutableListOf<ExpenseSplitEntity>()
        db.query(
            ExpenseSplitsTable.TABLE_NAME,
            null,
            "${ExpenseSplitsTable.COL_EXPENSE_ID} = ?",
            arrayOf(expenseId),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                splits.add(cursor.toExpenseSplitEntity())
            }
        }
        return splits
    }

    fun getSplitsForGroup(groupId: String): List<ExpenseSplitEntity> {
        val db = database.readableDatabase
        val splits = mutableListOf<ExpenseSplitEntity>()
        val sql = """
            SELECT s.* FROM ${ExpenseSplitsTable.TABLE_NAME} s
            INNER JOIN ${ExpensesTable.TABLE_NAME} e
                ON s.${ExpenseSplitsTable.COL_EXPENSE_ID} = e.${ExpensesTable.COL_ID}
            WHERE e.${ExpensesTable.COL_GROUP_ID} = ? AND e.${ExpensesTable.COL_IS_DELETED} = 0
        """.trimIndent()
        db.rawQuery(sql, arrayOf(groupId)).use { cursor ->
            while (cursor.moveToNext()) {
                splits.add(cursor.toExpenseSplitEntity())
            }
        }
        return splits
    }

    fun markExpenseDeleted(expenseId: String, syncStatus: SyncStatus = SyncStatus.PENDING_DELETE) {
        val db = database.writableDatabase
        val values = ContentValues().apply {
            put(ExpensesTable.COL_IS_DELETED, 1)
            put(ExpensesTable.COL_SYNC_STATUS, Converters.fromSyncStatus(syncStatus))
        }
        db.update(
            ExpensesTable.TABLE_NAME,
            values,
            "${ExpensesTable.COL_ID} = ?",
            arrayOf(expenseId)
        )
        database.notifyTableChanged(ExpensesTable.TABLE_NAME)
    }

    fun updateSyncStatus(expenseId: String, status: SyncStatus) {
        val db = database.writableDatabase
        val values = ContentValues().apply {
            put(ExpensesTable.COL_SYNC_STATUS, Converters.fromSyncStatus(status))
        }
        db.update(
            ExpensesTable.TABLE_NAME,
            values,
            "${ExpensesTable.COL_ID} = ?",
            arrayOf(expenseId)
        )
        database.notifyTableChanged(ExpensesTable.TABLE_NAME)
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

    private fun Cursor.toExpenseEntity(): ExpenseEntity {
        val notesIdx = getColumnIndexOrThrow(ExpensesTable.COL_NOTES)
        val localUriIdx = getColumnIndexOrThrow(ExpensesTable.COL_RECEIPT_LOCAL_URI)
        val remotePathIdx = getColumnIndexOrThrow(ExpensesTable.COL_RECEIPT_REMOTE_PATH)
        return ExpenseEntity(
            id = getString(getColumnIndexOrThrow(ExpensesTable.COL_ID)),
            groupId = getString(getColumnIndexOrThrow(ExpensesTable.COL_GROUP_ID)),
            title = getString(getColumnIndexOrThrow(ExpensesTable.COL_TITLE)),
            amountCents = getLong(getColumnIndexOrThrow(ExpensesTable.COL_AMOUNT_CENTS)),
            currency = getString(getColumnIndexOrThrow(ExpensesTable.COL_CURRENCY)),
            payerId = getString(getColumnIndexOrThrow(ExpensesTable.COL_PAYER_ID)),
            category = getString(getColumnIndexOrThrow(ExpensesTable.COL_CATEGORY)),
            notes = if (isNull(notesIdx)) null else getString(notesIdx),
            receiptLocalUri = if (isNull(localUriIdx)) null else getString(localUriIdx),
            receiptRemotePath = if (isNull(remotePathIdx)) null else getString(remotePathIdx),
            createdAt = getLong(getColumnIndexOrThrow(ExpensesTable.COL_CREATED_AT)),
            createdBy = getString(getColumnIndexOrThrow(ExpensesTable.COL_CREATED_BY)),
            isDeleted = getInt(getColumnIndexOrThrow(ExpensesTable.COL_IS_DELETED)) != 0,
            syncStatus = Converters.toSyncStatus(getString(getColumnIndexOrThrow(ExpensesTable.COL_SYNC_STATUS)))
        )
    }

    private fun Cursor.toExpenseSplitEntity(): ExpenseSplitEntity = ExpenseSplitEntity(
        id = getString(getColumnIndexOrThrow(ExpenseSplitsTable.COL_ID)),
        expenseId = getString(getColumnIndexOrThrow(ExpenseSplitsTable.COL_EXPENSE_ID)),
        userId = getString(getColumnIndexOrThrow(ExpenseSplitsTable.COL_USER_ID)),
        amountOwedCents = getLong(getColumnIndexOrThrow(ExpenseSplitsTable.COL_AMOUNT_OWED_CENTS)),
        shareWeight = getDouble(getColumnIndexOrThrow(ExpenseSplitsTable.COL_SHARE_WEIGHT))
    )
}
