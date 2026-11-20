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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

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

    private val tableRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())

    val userDao: UserDao by lazy { UserDao(this) }
    val groupDao: GroupDao by lazy { GroupDao(this) }
    val expenseDao: ExpenseDao by lazy { ExpenseDao(this) }
    val settlementDao: SettlementDao by lazy { SettlementDao(this) }
    val reminderDao by lazy { com.example.getyourmoneyupnotyourfunnyup.data.local.dao.ReminderDao(this) }
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
        // Two branches both shipped a "version 3" with different columns, so a version 3 database may be
        // missing either set. Every column is therefore added only if it is missing, which makes this safe
        // to run from any older version. SQLiteOpenHelper wraps onUpgrade in a transaction.
        val users = DatabaseContract.UsersTable
        val groups = DatabaseContract.GroupsTable
        val members = DatabaseContract.GroupMembersTable
        if (oldVersion < 4) {
            addColumnIfMissing(db, users.TABLE_NAME, users.COL_COUNT, "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, users.TABLE_NAME, users.COL_PHONE, "TEXT")
            addColumnIfMissing(db, users.TABLE_NAME, users.COL_PHONE_NUMBER, "TEXT")
            addColumnIfMissing(db, users.TABLE_NAME, users.COL_EXCLUDE_ALCOHOL, "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, users.TABLE_NAME, users.COL_EXCLUDE_MEAT, "INTEGER NOT NULL DEFAULT 0")
            for (column in listOf(
                users.COL_NOTIFY_NEW_EXPENSES, users.COL_NOTIFY_CHANGED_EXPENSES,
                users.COL_NOTIFY_PAYMENT_REMINDERS, users.COL_NOTIFY_SYNC_UPDATES
            )) {
                if (addColumnIfMissing(db, users.TABLE_NAME, column, "INTEGER NOT NULL DEFAULT 1")) {
                    // New preference columns inherit the old single on/off switch.
                    db.execSQL("UPDATE ${users.TABLE_NAME} SET $column = ${users.COL_NOTIFICATIONS_ENABLED}")
                }
            }
            addColumnIfMissing(db, groups.TABLE_NAME, groups.COL_COLOUR_INDEX, "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, groups.TABLE_NAME, groups.COL_IS_FAVOURITE, "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, members.TABLE_NAME, members.COL_INVITED, "INTEGER NOT NULL DEFAULT 0")
        }
        onCreate(db)
    }

    /** Adds [column] to [table] unless it already exists. Returns true when the column was added. */
    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String): Boolean {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }.any { it == column }
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        return !exists
    }

    /**
     * Emits an initial signal and re-emits whenever any of the specified [tableNames] change.
     */
    fun observeTables(vararg tableNames: String): Flow<Set<String>> {
        val watchedSet = tableNames.toSet()
        return tableRevisions.map { revisions -> watchedSet.associateWith { revisions[it] ?: 0L } }
            .distinctUntilChanged().map { watchedSet }
    }

    /**
     * Notifies active observers that one or more tables have been modified.
     */
    fun notifyTableChanged(vararg tableNames: String) {
        tableRevisions.update { previous -> previous + tableNames.associateWith { (previous[it] ?: 0L) + 1L } }
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
