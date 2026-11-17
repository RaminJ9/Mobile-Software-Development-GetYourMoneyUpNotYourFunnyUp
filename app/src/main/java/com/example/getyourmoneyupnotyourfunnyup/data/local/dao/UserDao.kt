package com.example.getyourmoneyupnotyourfunnyup.data.local.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.UsersTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class UserDao(private val database: AppDatabase) {

    fun observeUserById(userId: String): Flow<UserEntity?> =
        database.observeTables(UsersTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getUserById(userId) }
        }

    fun observeAllUsers(): Flow<List<UserEntity>> =
        database.observeTables(UsersTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getAllUsers() }
        }

    fun upsertUser(user: UserEntity) {
        val db = database.writableDatabase
        db.insertWithOnConflict(
            UsersTable.TABLE_NAME,
            null,
            user.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        database.notifyTableChanged(UsersTable.TABLE_NAME)
    }

    fun getUserById(userId: String): UserEntity? {
        val db = database.readableDatabase
        db.query(
            UsersTable.TABLE_NAME,
            null,
            "${UsersTable.COL_ID} = ?",
            arrayOf(userId),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toUserEntity() else null
        }
    }

    fun getUserByEmail(email: String): UserEntity? {
        val db = database.readableDatabase
        db.query(
            UsersTable.TABLE_NAME,
            null,
            "${UsersTable.COL_EMAIL} = ?",
            arrayOf(email),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toUserEntity() else null
        }
    }

    fun getUserByPhone(phoneNumber: String): UserEntity? {
        val db = database.readableDatabase
        db.query(
            UsersTable.TABLE_NAME,
            null,
            "${UsersTable.COL_PHONE_NUMBER} = ?",
            arrayOf(phoneNumber),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toUserEntity() else null
        }
    }

    fun getAllUsers(): List<UserEntity> {
        val db = database.readableDatabase
        val users = mutableListOf<UserEntity>()
        db.query(
            UsersTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "${UsersTable.COL_DISPLAY_NAME} ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                users.add(cursor.toUserEntity())
            }
        }
        return users
    }

    fun updateUserCount(userId: String, count: Int, status: SyncStatus) {
        val db = database.writableDatabase
        val values = ContentValues().apply {
            put(UsersTable.COL_COUNT, count)
            put(UsersTable.COL_SYNC_STATUS, Converters.fromSyncStatus(status))
            put(UsersTable.COL_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(
            UsersTable.TABLE_NAME,
            values,
            "${UsersTable.COL_ID} = ?",
            arrayOf(userId)
        )
        database.notifyTableChanged(UsersTable.TABLE_NAME)
    }

    fun updateSyncStatus(userId: String, status: SyncStatus) {
        val db = database.writableDatabase
        val values = ContentValues().apply {
            put(UsersTable.COL_SYNC_STATUS, Converters.fromSyncStatus(status))
            put(UsersTable.COL_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(
            UsersTable.TABLE_NAME,
            values,
            "${UsersTable.COL_ID} = ?",
            arrayOf(userId)
        )
        database.notifyTableChanged(UsersTable.TABLE_NAME)
    }

    private fun UserEntity.toContentValues(): ContentValues = ContentValues().apply {
        put(UsersTable.COL_ID, id)
        put(UsersTable.COL_EMAIL, email)
        put(UsersTable.COL_DISPLAY_NAME, displayName)
        put(UsersTable.COL_PHONE_NUMBER, phoneNumber)
        put(UsersTable.COL_PHOTO_URL, photoUrl)
        put(UsersTable.COL_COUNT, count)
        put(UsersTable.COL_NOTIFICATIONS_ENABLED, if (notificationsEnabled) 1 else 0)
        put(UsersTable.COL_EXCLUDE_ALCOHOL, if (excludeAlcohol) 1 else 0)
        put(UsersTable.COL_EXCLUDE_MEAT, if (excludeMeat) 1 else 0)
        put(UsersTable.COL_NOTIFY_NEW_EXPENSES, if (notifyNewExpenses) 1 else 0)
        put(UsersTable.COL_NOTIFY_CHANGED_EXPENSES, if (notifyChangedExpenses) 1 else 0)
        put(UsersTable.COL_NOTIFY_PAYMENT_REMINDERS, if (notifyPaymentReminders) 1 else 0)
        put(UsersTable.COL_NOTIFY_SYNC_UPDATES, if (notifySyncUpdates) 1 else 0)
        put(UsersTable.COL_SYNC_STATUS, Converters.fromSyncStatus(syncStatus))
        put(UsersTable.COL_UPDATED_AT, updatedAt)
    }

    private fun Cursor.toUserEntity(): UserEntity {
        val photoIdx = getColumnIndexOrThrow(UsersTable.COL_PHOTO_URL)
        val countIdx = getColumnIndex(UsersTable.COL_COUNT)
        val phoneIdx = getColumnIndex(UsersTable.COL_PHONE_NUMBER)
        val alcIdx = getColumnIndex(UsersTable.COL_EXCLUDE_ALCOHOL)
        val meatIdx = getColumnIndex(UsersTable.COL_EXCLUDE_MEAT)
        val newExpIdx = getColumnIndex(UsersTable.COL_NOTIFY_NEW_EXPENSES)
        val chgExpIdx = getColumnIndex(UsersTable.COL_NOTIFY_CHANGED_EXPENSES)
        val remIdx = getColumnIndex(UsersTable.COL_NOTIFY_PAYMENT_REMINDERS)
        val syncIdx = getColumnIndex(UsersTable.COL_NOTIFY_SYNC_UPDATES)
        val notifEnabled = getInt(getColumnIndexOrThrow(UsersTable.COL_NOTIFICATIONS_ENABLED)) != 0

        return UserEntity(
            id = getString(getColumnIndexOrThrow(UsersTable.COL_ID)),
            email = getString(getColumnIndexOrThrow(UsersTable.COL_EMAIL)),
            displayName = getString(getColumnIndexOrThrow(UsersTable.COL_DISPLAY_NAME)),
            phoneNumber = if (phoneIdx >= 0 && !isNull(phoneIdx)) getString(phoneIdx) else "",
            photoUrl = if (isNull(photoIdx)) null else getString(photoIdx),
            count = if (countIdx >= 0 && !isNull(countIdx)) getInt(countIdx) else 0,
            notificationsEnabled = notifEnabled,
            excludeAlcohol = if (alcIdx >= 0 && !isNull(alcIdx)) getInt(alcIdx) != 0 else false,
            excludeMeat = if (meatIdx >= 0 && !isNull(meatIdx)) getInt(meatIdx) != 0 else false,
            notifyNewExpenses = if (newExpIdx >= 0 && !isNull(newExpIdx)) getInt(newExpIdx) != 0 else notifEnabled,
            notifyChangedExpenses = if (chgExpIdx >= 0 && !isNull(chgExpIdx)) getInt(chgExpIdx) != 0 else notifEnabled,
            notifyPaymentReminders = if (remIdx >= 0 && !isNull(remIdx)) getInt(remIdx) != 0 else notifEnabled,
            notifySyncUpdates = if (syncIdx >= 0 && !isNull(syncIdx)) getInt(syncIdx) != 0 else notifEnabled,
            syncStatus = Converters.toSyncStatus(getString(getColumnIndexOrThrow(UsersTable.COL_SYNC_STATUS))),
            updatedAt = getLong(getColumnIndexOrThrow(UsersTable.COL_UPDATED_AT))
        )
    }
}
