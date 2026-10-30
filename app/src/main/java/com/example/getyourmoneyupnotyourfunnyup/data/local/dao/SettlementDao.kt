package com.example.getyourmoneyupnotyourfunnyup.data.local.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SettlementsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SettlementEntity
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettlementDao(private val database: AppDatabase) {

    fun observeSettlementsForGroup(groupId: String): Flow<List<SettlementEntity>> =
        database.observeTables(SettlementsTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getSettlementsForGroup(groupId) }
        }

    fun upsertSettlement(settlement: SettlementEntity) {
        val db = database.writableDatabase
        db.insertWithOnConflict(
            SettlementsTable.TABLE_NAME,
            null,
            settlement.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        database.notifyTableChanged(SettlementsTable.TABLE_NAME)
    }

    fun getSettlementsForGroup(groupId: String): List<SettlementEntity> {
        val db = database.readableDatabase
        val settlements = mutableListOf<SettlementEntity>()
        db.query(
            SettlementsTable.TABLE_NAME,
            null,
            "${SettlementsTable.COL_GROUP_ID} = ?",
            arrayOf(groupId),
            null,
            null,
            "${SettlementsTable.COL_CREATED_AT} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                settlements.add(cursor.toSettlementEntity())
            }
        }
        return settlements
    }

    fun updateSettlementStatus(
        settlementId: String,
        status: SettlementStatus,
        syncStatus: SyncStatus
    ) {
        val db = database.writableDatabase
        val values = ContentValues().apply {
            put(SettlementsTable.COL_STATUS, Converters.fromSettlementStatus(status))
            put(SettlementsTable.COL_SYNC_STATUS, Converters.fromSyncStatus(syncStatus))
        }
        db.update(
            SettlementsTable.TABLE_NAME,
            values,
            "${SettlementsTable.COL_ID} = ?",
            arrayOf(settlementId)
        )
        database.notifyTableChanged(SettlementsTable.TABLE_NAME)
    }

    private fun SettlementEntity.toContentValues(): ContentValues = ContentValues().apply {
        put(SettlementsTable.COL_ID, id)
        put(SettlementsTable.COL_GROUP_ID, groupId)
        put(SettlementsTable.COL_DEBTOR_ID, debtorId)
        put(SettlementsTable.COL_CREDITOR_ID, creditorId)
        put(SettlementsTable.COL_AMOUNT_CENTS, amountCents)
        put(SettlementsTable.COL_CURRENCY, currency)
        put(SettlementsTable.COL_PAYMENT_METHOD, paymentMethod)
        put(SettlementsTable.COL_STATUS, Converters.fromSettlementStatus(status))
        put(SettlementsTable.COL_CREATED_AT, createdAt)
        put(SettlementsTable.COL_SYNC_STATUS, Converters.fromSyncStatus(syncStatus))
    }

    private fun Cursor.toSettlementEntity(): SettlementEntity = SettlementEntity(
        id = getString(getColumnIndexOrThrow(SettlementsTable.COL_ID)),
        groupId = getString(getColumnIndexOrThrow(SettlementsTable.COL_GROUP_ID)),
        debtorId = getString(getColumnIndexOrThrow(SettlementsTable.COL_DEBTOR_ID)),
        creditorId = getString(getColumnIndexOrThrow(SettlementsTable.COL_CREDITOR_ID)),
        amountCents = getLong(getColumnIndexOrThrow(SettlementsTable.COL_AMOUNT_CENTS)),
        currency = getString(getColumnIndexOrThrow(SettlementsTable.COL_CURRENCY)),
        paymentMethod = getString(getColumnIndexOrThrow(SettlementsTable.COL_PAYMENT_METHOD)),
        status = Converters.toSettlementStatus(getString(getColumnIndexOrThrow(SettlementsTable.COL_STATUS))),
        createdAt = getLong(getColumnIndexOrThrow(SettlementsTable.COL_CREATED_AT)),
        syncStatus = Converters.toSyncStatus(getString(getColumnIndexOrThrow(SettlementsTable.COL_SYNC_STATUS)))
    )
}
