package com.example.getyourmoneyupnotyourfunnyup.data.local.dao

import android.content.ContentValues
import android.database.Cursor
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.UsersTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable
import com.example.getyourmoneyupnotyourfunnyup.model.ProfileChanges
import com.example.getyourmoneyupnotyourfunnyup.data.remote.UserDocumentMapper
import org.json.JSONObject
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.phonesMatch
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
        db.beginTransaction()
        try {
            val values = user.toContentValues()
            val updated = db.update(UsersTable.TABLE_NAME, values, "${UsersTable.COL_ID} = ?", arrayOf(user.id))
            if (updated == 0) db.insertOrThrow(UsersTable.TABLE_NAME, null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.notifyTableChanged(UsersTable.TABLE_NAME)
    }

    /** Save the profile and its upload intent atomically, retaining all non-profile fields. */
    internal fun saveProfileAndEnqueue(userId: String, changes: ProfileChanges): Pair<UserEntity, Long> {
        val db = database.writableDatabase
        db.beginTransaction()
        val result: Pair<UserEntity, Long>
        try {
            val previous = requireNotNull(getUserById(userId)) { "Your profile is not available on this phone yet." }
            val next = previous.copy(
                displayName = changes.displayName,
                phoneNumber = changes.phoneNumber,
                excludeAlcohol = changes.excludeAlcohol,
                excludeMeat = changes.excludeMeat,
                notificationSettings = changes.notificationSettings,
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = maxOf(System.currentTimeMillis(), Math.addExact(previous.updatedAt, 1L))
            )
            check(db.update(UsersTable.TABLE_NAME, next.toContentValues(), "${UsersTable.COL_ID} = ?", arrayOf(userId)) == 1)
            val payload = JSONObject(UserDocumentMapper.profileUpdateMap(next.toDomain())).toString()
            val values = ContentValues().apply {
                put(SyncQueueTable.COL_ENTITY_TYPE, "USER")
                put(SyncQueueTable.COL_ENTITY_ID, userId)
                put(SyncQueueTable.COL_ACTION, "UPDATE")
                put(SyncQueueTable.COL_PAYLOAD_JSON, payload)
            }
            val queueId = db.insertOrThrow(SyncQueueTable.TABLE_NAME, null, values)
            result = next to queueId
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.notifyTableChanged(UsersTable.TABLE_NAME, SyncQueueTable.TABLE_NAME)
        return result
    }

    /** A delayed remote read cannot discard a pending local edit or roll back its revision. */
    fun cacheRemoteUser(remote: UserEntity): UserEntity {
        val db = database.writableDatabase
        db.beginTransaction()
        val result: UserEntity
        try {
            val local = getUserById(remote.id)
            result = if (local != null && (local.syncStatus != SyncStatus.SYNCED || local.updatedAt > remote.updatedAt)) {
                local
            } else {
                upsertUser(remote)
                remote
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.notifyTableChanged(UsersTable.TABLE_NAME)
        return result
    }

    /** Acknowledge only this operation; an older upload never clears a newer edit. */
    internal fun finishProfileUpload(userId: String, queueId: Long, revision: Long, error: String? = null) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val selection = "${SyncQueueTable.COL_QUEUE_ID} = ? AND ${SyncQueueTable.COL_ENTITY_TYPE} = 'USER' AND ${SyncQueueTable.COL_ENTITY_ID} = ?"
            val args = arrayOf(queueId.toString(), userId)
            val affected = if (error == null) {
                db.delete(SyncQueueTable.TABLE_NAME, selection, args)
            } else {
                db.update(SyncQueueTable.TABLE_NAME, ContentValues().apply {
                    put(SyncQueueTable.COL_ERROR_MESSAGE, error)
                    put(SyncQueueTable.COL_LAST_ATTEMPT_AT, System.currentTimeMillis())
                }, selection, args)
            }
            val current = getUserById(userId)
            if (affected > 0 && current?.updatedAt == revision) {
                val stillQueued = db.rawQuery(
                    "SELECT 1 FROM ${SyncQueueTable.TABLE_NAME} WHERE ${SyncQueueTable.COL_ENTITY_TYPE} = 'USER' AND ${SyncQueueTable.COL_ENTITY_ID} = ? LIMIT 1",
                    arrayOf(userId)
                ).use { it.moveToFirst() }
                val status = if (error != null) SyncStatus.FAILED else if (stillQueued) SyncStatus.PENDING_UPDATE else SyncStatus.SYNCED
                db.update(UsersTable.TABLE_NAME, ContentValues().apply {
                    put(UsersTable.COL_SYNC_STATUS, Converters.fromSyncStatus(status))
                }, "${UsersTable.COL_ID} = ?", arrayOf(userId))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.notifyTableChanged(UsersTable.TABLE_NAME, SyncQueueTable.TABLE_NAME)
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

    /** Phone numbers are stored normalised (`+4522814017`), so callers pass a normalised value. */
    fun getUserByPhone(phone: String): UserEntity? {
        val normalisedQuery = normalisePhone(phone)
        if (normalisedQuery.isBlank()) return null
        val db = database.readableDatabase
        db.query(
            UsersTable.TABLE_NAME,
            null,
            "${UsersTable.COL_PHONE} = ? AND ${UsersTable.COL_ID} NOT LIKE 'invite:%'",
            arrayOf(normalisedQuery),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.toUserEntity()
        }
        return getAllUsers().firstOrNull { candidate ->
            !candidate.id.startsWith("invite:") && (
                phonesMatch(candidate.phone, normalisedQuery) ||
                    phonesMatch(candidate.phoneNumber, normalisedQuery)
                )
        }
    }

    /** Case-insensitive email lookup used by "Add member" (emails are typed by hand). */
    fun getUserByEmailIgnoringCase(email: String): UserEntity? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return null
        val db = database.readableDatabase
        db.query(
            UsersTable.TABLE_NAME,
            null,
            "LOWER(TRIM(${UsersTable.COL_EMAIL})) = LOWER(TRIM(?)) AND ${UsersTable.COL_ID} NOT LIKE 'invite:%'",
            arrayOf(trimmed),
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

    /** Same phone format as `normaliseContact` in the groups UI: optional leading `+`, then digits only. */
    private fun normalisePhone(value: String): String {
        val trimmed = value.trim()
        return (if (trimmed.startsWith("+")) "+" else "") + trimmed.filter { it.isDigit() }
    }

    private fun UserEntity.toContentValues(): ContentValues = ContentValues().apply {
        put(UsersTable.COL_ID, id)
        put(UsersTable.COL_EMAIL, email)
        put(UsersTable.COL_DISPLAY_NAME, displayName)
        put(UsersTable.COL_PHOTO_URL, photoUrl)
        // COL_PHONE is the normalised lookup key used by "Add member"; COL_PHONE_NUMBER is the number as the
        // user typed it on registration or in their profile. Derive the key from it so friends can find them.
        val resolvedPhone = phoneNumber?.takeIf { it.isNotBlank() }?.let(::normalisePhone)
            ?: phone?.takeIf { it.isNotBlank() }?.let(::normalisePhone)
        put(UsersTable.COL_PHONE, resolvedPhone)
        put(UsersTable.COL_COUNT, count)
        put(UsersTable.COL_NOTIFICATIONS_ENABLED, if (notificationSettings.let {
            it.expenseAlerts || it.changedExpenseAlerts || it.paymentReminders || it.syncUpdates
        }) 1 else 0)
        put(UsersTable.COL_PHONE_NUMBER, phoneNumber.orEmpty())
        put(UsersTable.COL_EXCLUDE_ALCOHOL, if (excludeAlcohol) 1 else 0)
        put(UsersTable.COL_EXCLUDE_MEAT, if (excludeMeat) 1 else 0)
        put(UsersTable.COL_NOTIFY_NEW_EXPENSES, if (notificationSettings.expenseAlerts) 1 else 0)
        put(UsersTable.COL_NOTIFY_CHANGED_EXPENSES, if (notificationSettings.changedExpenseAlerts) 1 else 0)
        put(UsersTable.COL_NOTIFY_PAYMENT_REMINDERS, if (notificationSettings.paymentReminders) 1 else 0)
        put(UsersTable.COL_NOTIFY_SYNC_UPDATES, if (notificationSettings.syncUpdates) 1 else 0)
        put(UsersTable.COL_SYNC_STATUS, Converters.fromSyncStatus(syncStatus))
        put(UsersTable.COL_UPDATED_AT, updatedAt)
    }

    private fun Cursor.toUserEntity(): UserEntity {
        val photoIdx = getColumnIndexOrThrow(UsersTable.COL_PHOTO_URL)
        val countIdx = getColumnIndex(UsersTable.COL_COUNT)
        val phoneIdx = getColumnIndex(UsersTable.COL_PHONE)
        val phoneNumberIdx = getColumnIndexOrThrow(UsersTable.COL_PHONE_NUMBER)
        return UserEntity(
            id = getString(getColumnIndexOrThrow(UsersTable.COL_ID)),
            email = getString(getColumnIndexOrThrow(UsersTable.COL_EMAIL)),
            displayName = getString(getColumnIndexOrThrow(UsersTable.COL_DISPLAY_NAME)),
            photoUrl = if (isNull(photoIdx)) null else getString(photoIdx),
            phone = if (phoneIdx >= 0 && !isNull(phoneIdx)) getString(phoneIdx) else null,
            count = if (countIdx >= 0 && !isNull(countIdx)) getInt(countIdx) else 0,
            notificationsEnabled = getInt(getColumnIndexOrThrow(UsersTable.COL_NOTIFICATIONS_ENABLED)) != 0,
            syncStatus = Converters.toSyncStatus(getString(getColumnIndexOrThrow(UsersTable.COL_SYNC_STATUS))),
            updatedAt = getLong(getColumnIndexOrThrow(UsersTable.COL_UPDATED_AT)),
            phoneNumber = if (isNull(phoneNumberIdx)) null else getString(phoneNumberIdx).takeIf { it.isNotBlank() },
            excludeAlcohol = getInt(getColumnIndexOrThrow(UsersTable.COL_EXCLUDE_ALCOHOL)) != 0,
            excludeMeat = getInt(getColumnIndexOrThrow(UsersTable.COL_EXCLUDE_MEAT)) != 0,
            notificationSettings = NotificationSettings(
                expenseAlerts = getInt(getColumnIndexOrThrow(UsersTable.COL_NOTIFY_NEW_EXPENSES)) != 0,
                paymentReminders = getInt(getColumnIndexOrThrow(UsersTable.COL_NOTIFY_PAYMENT_REMINDERS)) != 0,
                changedExpenseAlerts = getInt(getColumnIndexOrThrow(UsersTable.COL_NOTIFY_CHANGED_EXPENSES)) != 0,
                syncUpdates = getInt(getColumnIndexOrThrow(UsersTable.COL_NOTIFY_SYNC_UPDATES)) != 0
            )
        )
    }
}
