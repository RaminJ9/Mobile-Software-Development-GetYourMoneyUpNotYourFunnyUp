package com.example.getyourmoneyupnotyourfunnyup.data.local.dao

import android.content.ContentValues
import android.database.Cursor
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SyncQueueDao(private val database: AppDatabase) {

    fun observePendingQueue(): Flow<List<SyncQueueEntity>> =
        database.observeTables(SyncQueueTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getAllPending() }
        }

    fun enqueue(item: SyncQueueEntity): Long {
        val db = database.writableDatabase
        val values = ContentValues().apply {
            put(SyncQueueTable.COL_ENTITY_TYPE, Converters.fromSyncEntityType(item.entityType))
            put(SyncQueueTable.COL_ENTITY_ID, item.entityId)
            put(SyncQueueTable.COL_ACTION, Converters.fromSyncAction(item.action))
            put(SyncQueueTable.COL_PAYLOAD_JSON, item.payloadJson)
            put(SyncQueueTable.COL_RETRY_COUNT, item.retryCount)
            put(SyncQueueTable.COL_LAST_ATTEMPT_AT, item.lastAttemptAt)
            put(SyncQueueTable.COL_ERROR_MESSAGE, item.errorMessage)
        }
        val id = db.insert(SyncQueueTable.TABLE_NAME, null, values)
        database.notifyTableChanged(SyncQueueTable.TABLE_NAME)
        return id
    }

    fun getAllPending(): List<SyncQueueEntity> {
        val db = database.readableDatabase
        val list = mutableListOf<SyncQueueEntity>()
        db.query(
            SyncQueueTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "${SyncQueueTable.COL_QUEUE_ID} ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toSyncQueueEntity())
            }
        }
        return list
    }

    fun recordRetryFailure(queueId: Long, errorMessage: String?) {
        val db = database.writableDatabase
        db.execSQL(
            """
            UPDATE ${SyncQueueTable.TABLE_NAME}
            SET ${SyncQueueTable.COL_RETRY_COUNT} = ${SyncQueueTable.COL_RETRY_COUNT} + 1,
                ${SyncQueueTable.COL_LAST_ATTEMPT_AT} = ?,
                ${SyncQueueTable.COL_ERROR_MESSAGE} = ?
            WHERE ${SyncQueueTable.COL_QUEUE_ID} = ?
            """.trimIndent(),
            arrayOf<Any?>(System.currentTimeMillis(), errorMessage, queueId)
        )
        database.notifyTableChanged(SyncQueueTable.TABLE_NAME)
    }

    fun removeById(queueId: Long) {
        val db = database.writableDatabase
        db.delete(
            SyncQueueTable.TABLE_NAME,
            "${SyncQueueTable.COL_QUEUE_ID} = ?",
            arrayOf(queueId.toString())
        )
        database.notifyTableChanged(SyncQueueTable.TABLE_NAME)
    }

    private fun Cursor.toSyncQueueEntity(): SyncQueueEntity {
        val lastAttemptIdx = getColumnIndexOrThrow(SyncQueueTable.COL_LAST_ATTEMPT_AT)
        val errorIdx = getColumnIndexOrThrow(SyncQueueTable.COL_ERROR_MESSAGE)
        return SyncQueueEntity(
            queueId = getLong(getColumnIndexOrThrow(SyncQueueTable.COL_QUEUE_ID)),
            entityType = Converters.toSyncEntityType(getString(getColumnIndexOrThrow(SyncQueueTable.COL_ENTITY_TYPE))),
            entityId = getString(getColumnIndexOrThrow(SyncQueueTable.COL_ENTITY_ID)),
            action = Converters.toSyncAction(getString(getColumnIndexOrThrow(SyncQueueTable.COL_ACTION))),
            payloadJson = getString(getColumnIndexOrThrow(SyncQueueTable.COL_PAYLOAD_JSON)),
            retryCount = getInt(getColumnIndexOrThrow(SyncQueueTable.COL_RETRY_COUNT)),
            lastAttemptAt = if (isNull(lastAttemptIdx)) null else getLong(lastAttemptIdx),
            errorMessage = if (isNull(errorIdx)) null else getString(errorIdx)
        )
    }
}
