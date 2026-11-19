package com.example.getyourmoneyupnotyourfunnyup.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileDbTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var database: AppDatabase? = null

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "profile_storage_test_${UUID.randomUUID()}.db"
    }

    @After fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun open(): AppDatabase = AppDatabase(context, databaseName).also { database = it }

    @Test fun version2MigrationPreservesUsersLedgerOutboxAndNotificationOptOut() {
        verifyLegacyMigration(version = 2, hasCount = true)
    }

    @Test fun version1MigrationAddsCountAndPreservesExistingData() {
        verifyLegacyMigration(version = 1, hasCount = false)
    }

    @Test fun version1WithExistingCountAlsoMigratesWithoutLosingIt() {
        verifyLegacyMigration(version = 1, hasCount = true)
    }

    private fun verifyLegacyMigration(version: Int, hasCount: Boolean) {
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.setForeignKeyConstraintsEnabled(true)
            // Frozen old users schema, independent of the new migration implementation.
            legacy.execSQL("""
                CREATE TABLE users (
                    id TEXT PRIMARY KEY NOT NULL, email TEXT NOT NULL, display_name TEXT NOT NULL,
                    photo_url TEXT, ${if (hasCount) "count INTEGER NOT NULL DEFAULT 0," else ""}
                    notifications_enabled INTEGER NOT NULL DEFAULT 1,
                    sync_status TEXT NOT NULL DEFAULT 'SYNCED', updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            DatabaseContract.ALL_CREATE_STATEMENTS.filterNot { it == DatabaseContract.UsersTable.CREATE_SQL }
                .forEach { legacy.execSQL(it.trimIndent()) }
            legacy.execSQL("INSERT INTO users (id,email,display_name,notifications_enabled,sync_status,updated_at) VALUES ('u','u@test','Existing',0,'PENDING_UPDATE',123)")
            legacy.execSQL("INSERT INTO users (id,email,display_name,notifications_enabled,updated_at) VALUES ('v','v@test','Other',1,124)")
            if (hasCount) legacy.execSQL("UPDATE users SET count = 7 WHERE id = 'u'")
            insertLedger(legacy)
            legacy.version = version
        }
        val migrated = open()
        assertEquals(4, migrated.readableDatabase.version)
        val user = migrated.userDao.getUserById("u")!!.toDomain()
        assertEquals("Existing", user.displayName)
        assertEquals("u@test", user.email)
        assertEquals(123L, user.updatedAt)
        assertEquals(if (hasCount) 7 else 0, user.count)
        assertEquals(SyncStatus.PENDING_UPDATE, user.syncStatus)
        assertEquals(NotificationSettings(false, false, false, false), user.notificationSettings)
        assertEquals(NotificationSettings(), migrated.userDao.getUserById("v")!!.toDomain().notificationSettings)
        assertNull(user.phoneNumber)
        assertFalse(user.excludeAlcohol)
        assertFalse(user.excludeMeat)
        assertLedgerPreserved(migrated)
    }

    @Test fun allFourPreferencesAndContactRulesRemainReadableOfflineAfterReopen() = runBlocking {
        val first = open()
        for (bits in 0..15) {
            val user = User(
                id = "u$bits", email = "u$bits@test", displayName = "User $bits", count = bits,
                phoneNumber = "+4512345678", excludeAlcohol = true, excludeMeat = bits % 2 == 0,
                updatedAt = 123L, syncStatus = SyncStatus.PENDING_UPDATE,
                notificationSettings = NotificationSettings(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0)
            )
            first.userDao.upsertUser(UserEntity.fromDomain(user))
        }
        first.close()
        val reopened = open()
        for (bits in 0..15) {
            // Local Flow only: no Firebase initialization or network access is needed.
            val user = reopened.userDao.observeUserById("u$bits").first()!!.toDomain()
            assertEquals(NotificationSettings(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0), user.notificationSettings)
            assertEquals("+4512345678", user.phoneNumber)
            assertTrue(user.excludeAlcohol)
            assertEquals(bits % 2 == 0, user.excludeMeat)
            assertEquals(SyncStatus.PENDING_UPDATE, user.syncStatus)
            assertEquals(bits, user.count)
        }
    }

    @Test fun updatingProfileKeepsMembershipsExpensesSplitsAndSettlements() {
        val db = open()
        val user = UserEntity("u", "u@test", "Existing", updatedAt = 123L)
        db.userDao.upsertUser(user)
        db.userDao.upsertUser(UserEntity("v", "v@test", "Other"))
        insertLedger(db.writableDatabase)
        val updated = user.toDomain().copy(
            displayName = "Updated", phoneNumber = "+4587654321", excludeAlcohol = true,
            notificationSettings = NotificationSettings(false, true, false, true)
        )
        db.userDao.upsertUser(UserEntity.fromDomain(updated))
        assertEquals(updated.copy(phone = "+4587654321"), db.userDao.getUserById("u")!!.toDomain())
        assertLedgerPreserved(db)
    }


    @Test fun incomingVersion3NotNullPhoneAcceptsMissingAndClearedNumbers() {
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { existing ->
            DatabaseContract.ALL_CREATE_STATEMENTS.forEach { statement ->
                existing.execSQL(statement.replace("phone_number TEXT,", "phone_number TEXT NOT NULL DEFAULT '',").trimIndent())
            }
            existing.version = 3
        }
        val db = open()
        val initial = UserEntity("u", "u@test", "Existing", phoneNumber = null)
        db.userDao.upsertUser(initial)
        assertNull(db.userDao.getUserById("u")!!.phoneNumber)
        db.userDao.upsertUser(initial.copy(phoneNumber = "+4512345678"))
        assertEquals("+4512345678", db.userDao.getUserById("u")!!.phone)
        db.userDao.saveProfileAndEnqueue("u", com.example.getyourmoneyupnotyourfunnyup.model.ProfileChanges.fromUser(initial.toDomain()))
        assertNull(db.userDao.getUserById("u")!!.phoneNumber)
        assertNull(db.userDao.getUserById("u")!!.phone)
        assertEquals(1, db.syncQueueDao.getAllPending().size)
    }

    private fun insertLedger(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO \"groups\" (id,name,created_by,created_at,updated_at) VALUES ('g','Existing group','u',1,1)")
        db.execSQL("INSERT INTO group_members (group_id,user_id,role,joined_at) VALUES ('g','u','ADMIN',1),('g','v','MEMBER',1)")
        db.execSQL("INSERT INTO expenses (id,group_id,title,amount_cents,payer_id,created_at,created_by) VALUES ('e','g','Dinner',100,'u',1,'u')")
        db.execSQL("INSERT INTO expense_splits (id,expense_id,user_id,amount_owed_cents) VALUES ('split','e','u',100)")
        db.execSQL("INSERT INTO settlements (id,group_id,debtor_id,creditor_id,amount_cents,payment_method,created_at) VALUES ('s','g','v','u',50,'Cash',1)")
        db.execSQL("INSERT INTO sync_queue (entity_type,entity_id,\"action\",payload_json) VALUES ('USER','u','UPDATE','{}')")
    }

    private fun assertLedgerPreserved(db: AppDatabase) {
        assertEquals("Existing group", db.groupDao.getGroupById("g")!!.name)
        assertEquals(2, db.groupDao.getMembersForGroup("g").size)
        assertEquals(100L, db.expenseDao.getExpenseById("e")!!.amountCents)
        assertEquals(100L, db.expenseDao.getSplitsForExpense("e").single().amountOwedCents)
        assertEquals(50L, db.settlementDao.getSettlementsForGroup("g").single().amountCents)
        assertEquals("{}", db.syncQueueDao.getAllPending().single().payloadJson)
    }
}
