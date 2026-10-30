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
class AppDatabase private constructor(
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
        if (oldVersion < 2) {
            runCatching {
                db.execSQL(
                    "ALTER TABLE ${DatabaseContract.UsersTable.TABLE_NAME} " +
                        "ADD COLUMN ${DatabaseContract.UsersTable.COL_COUNT} INTEGER NOT NULL DEFAULT 0"
                )
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
