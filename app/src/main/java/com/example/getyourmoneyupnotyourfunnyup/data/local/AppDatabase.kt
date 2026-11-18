package com.example.getyourmoneyupnotyourfunnyup.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.getyourmoneyupnotyourfunnyup.data.local.dao.ExpenseDao
import com.example.getyourmoneyupnotyourfunnyup.data.local.dao.GroupDao
import com.example.getyourmoneyupnotyourfunnyup.data.local.dao.SettlementDao
import com.example.getyourmoneyupnotyourfunnyup.data.local.dao.SyncQueueDao
import com.example.getyourmoneyupnotyourfunnyup.data.local.dao.UserDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart

/**
 * Local SQLite Database Manager for "Get Your Money Up Not Your Funny Up".
 *
 * Implements the Single Source of Truth (SSOT) for offline resilience:
 * - Enforces foreign keys (`PRAGMA foreign_keys = ON`) and Write-Ahead Logging (WAL).
 * - Creates the 7 core tables (`users`, `groups`, `group_members`, `expenses`,
 *   `expense_splits`, `settlements`, `sync_queue`) and performance indexes.
 * - Exposes reactive table invalidation streams (`observeTables`) for Compose UI flows.
 */
class AppDatabase internal constructor(
    context: Context,
    databaseName: String? = DatabaseContract.DATABASE_NAME
) : SQLiteOpenHelper(
    context.applicationContext,
    databaseName,
    null,
    DatabaseContract.DATABASE_VERSION
) {

    private val tableInvalidationFlow = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)

    val userDao: UserDao by lazy { UserDao(this) }
    val groupDao: GroupDao by lazy { GroupDao(this) }
    val expenseDao: ExpenseDao by lazy { ExpenseDao(this) }
    val settlementDao: SettlementDao by lazy { SettlementDao(this) }
    val syncQueueDao: SyncQueueDao by lazy { SyncQueueDao(this) }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        if (!db.isReadOnly && databaseName != null) {
            db.enableWriteAheadLogging()
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for (statement in DatabaseContract.ALL_CREATE_STATEMENTS) {
                db.execSQL(statement.trimIndent())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val users = DatabaseContract.UsersTable
        if (oldVersion < 2) {
            // Some early v1 databases already included count; inspect rather than suppress SQL errors.
            val hasCount = db.rawQuery("PRAGMA table_info(${users.TABLE_NAME})", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == users.COL_COUNT) found = true
                }
                found
            }
            if (!hasCount) {
                db.execSQL("ALTER TABLE ${users.TABLE_NAME} ADD COLUMN ${users.COL_COUNT} INTEGER NOT NULL DEFAULT 0")
            }
        }
        if (oldVersion < 3) {
            // SQLiteOpenHelper wraps onUpgrade in a transaction. No tables or records are replaced.
            db.execSQL("ALTER TABLE ${users.TABLE_NAME} ADD COLUMN ${users.COL_PHONE_NUMBER} TEXT")
            for (column in listOf(users.COL_EXCLUDE_ALCOHOL, users.COL_EXCLUDE_MEAT)) {
                db.execSQL("ALTER TABLE ${users.TABLE_NAME} ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
            }
            for (column in listOf(
                users.COL_NOTIFY_NEW_EXPENSES, users.COL_NOTIFY_CHANGED_EXPENSES,
                users.COL_NOTIFY_PAYMENT_REMINDERS, users.COL_NOTIFY_SYNC_UPDATES
            )) {
                db.execSQL("ALTER TABLE ${users.TABLE_NAME} ADD COLUMN $column INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE ${users.TABLE_NAME} SET $column = ${users.COL_NOTIFICATIONS_ENABLED}")
            }
        }
        if (oldVersion < 3) {
            val table = DatabaseContract.UsersTable.TABLE_NAME
            val alterations = listOf(
                "ALTER TABLE $table ADD COLUMN ${DatabaseContract.UsersTable.COL_PHONE_NUMBER} TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE $table ADD COLUMN ${DatabaseContract.UsersTable.COL_EXCLUDE_ALCOHOL} INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE $table ADD COLUMN ${DatabaseContract.UsersTable.COL_EXCLUDE_MEAT} INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE $table ADD COLUMN ${DatabaseContract.UsersTable.COL_NOTIFY_NEW_EXPENSES} INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE $table ADD COLUMN ${DatabaseContract.UsersTable.COL_NOTIFY_CHANGED_EXPENSES} INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE $table ADD COLUMN ${DatabaseContract.UsersTable.COL_NOTIFY_PAYMENT_REMINDERS} INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE $table ADD COLUMN ${DatabaseContract.UsersTable.COL_NOTIFY_SYNC_UPDATES} INTEGER NOT NULL DEFAULT 1"
            )
            for (sql in alterations) {
                runCatching { db.execSQL(sql) }
            }
        }
        if (oldVersion < 3) {
            // Person 2: group colour, favourite star, invited members and phone lookup.
            val additions = listOf(
                "${DatabaseContract.UsersTable.TABLE_NAME} ADD COLUMN ${DatabaseContract.UsersTable.COL_PHONE} TEXT",
                "${DatabaseContract.GroupsTable.TABLE_NAME} ADD COLUMN ${DatabaseContract.GroupsTable.COL_COLOUR_INDEX} INTEGER NOT NULL DEFAULT 0",
                "${DatabaseContract.GroupsTable.TABLE_NAME} ADD COLUMN ${DatabaseContract.GroupsTable.COL_IS_FAVOURITE} INTEGER NOT NULL DEFAULT 0",
                "${DatabaseContract.GroupMembersTable.TABLE_NAME} ADD COLUMN ${DatabaseContract.GroupMembersTable.COL_INVITED} INTEGER NOT NULL DEFAULT 0"
            )
            for (addition in additions) {
                runCatching { db.execSQL("ALTER TABLE $addition") }
            }
        }
        onCreate(db)
    }

    /**
     * Emits an initial signal and re-emits whenever any of the specified [tableNames] change.
     */
    fun observeTables(vararg tableNames: String): Flow<Set<String>> {
        val watchedSet = tableNames.toSet()
        return tableInvalidationFlow
            .filter { changed -> changed.any { it in watchedSet } }
            .onStart { emit(watchedSet) }
    }

    /**
     * Notifies active observers that one or more tables have been modified.
     */
    fun notifyTableChanged(vararg tableNames: String) {
        tableInvalidationFlow.tryEmit(tableNames.toSet())
    }

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context).also { instance = it }
            }

        /**
         * Creates an isolated in-memory database instance for instrumentation/reliability tests.
         */
        fun createInMemory(context: Context): AppDatabase =
            AppDatabase(context, databaseName = null)
    }
}
