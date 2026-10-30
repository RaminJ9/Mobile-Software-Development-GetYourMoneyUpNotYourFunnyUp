package com.example.getyourmoneyupnotyourfunnyup.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseSplitEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SettlementEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncQueueEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryOfflineFlow_persistsEntitiesAndCascadesDeletes() {
        val u1 = UserEntity(id = "u1", email = "u1@test.com", displayName = "Alice")
        val u2 = UserEntity(id = "u2", email = "u2@test.com", displayName = "Bob")
        database.userDao.upsertUser(u1)
        database.userDao.upsertUser(u2)

        val group = GroupEntity(
            id = "g1",
            name = "Ski Trip",
            description = "Alps 2026",
            createdBy = "u1",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val members = listOf(
            GroupMemberEntity(groupId = "g1", userId = "u1", role = GroupRole.ADMIN),
            GroupMemberEntity(groupId = "g1", userId = "u2", role = GroupRole.MEMBER)
        )
        database.groupDao.upsertGroupWithMembers(group, members)

        val expense = ExpenseEntity(
            id = "e1",
            groupId = "g1",
            title = "Cabin Rental",
            amountCents = 20000L,
            payerId = "u1",
            createdBy = "u1",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val splits = listOf(
            ExpenseSplitEntity(id = "s1", expenseId = "e1", userId = "u1", amountOwedCents = 10000L),
            ExpenseSplitEntity(id = "s2", expenseId = "e1", userId = "u2", amountOwedCents = 10000L)
        )
        database.expenseDao.upsertExpenseWithSplits(expense, splits)

        val settlement = SettlementEntity(
            id = "st1",
            groupId = "g1",
            debtorId = "u2",
            creditorId = "u1",
            amountCents = 10000L,
            paymentMethod = "MobilePay",
            status = SettlementStatus.COMPLETED,
            syncStatus = SyncStatus.PENDING_INSERT
        )
        database.settlementDao.upsertSettlement(settlement)

        val queueId = database.syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = SyncEntityType.EXPENSE,
                entityId = "e1",
                action = SyncAction.CREATE,
                payloadJson = """{"id":"e1"}"""
            )
        )
        assertTrue(queueId > 0)

        assertNotNull(database.groupDao.getGroupById("g1"))
        assertEquals(2, database.groupDao.getMembersForGroup("g1").size)
        assertEquals(1, database.expenseDao.getActiveExpensesForGroup("g1").size)
        assertEquals(2, database.expenseDao.getSplitsForExpense("e1").size)
        assertEquals(1, database.settlementDao.getSettlementsForGroup("g1").size)
        assertEquals(1, database.syncQueueDao.getAllPending().size)

        // Verify ON DELETE CASCADE when deleting the group
        database.groupDao.deleteGroup("g1")
        assertTrue(database.groupDao.getMembersForGroup("g1").isEmpty())
        assertTrue(database.expenseDao.getActiveExpensesForGroup("g1").isEmpty())
        assertTrue(database.expenseDao.getSplitsForExpense("e1").isEmpty())
        assertTrue(database.settlementDao.getSettlementsForGroup("g1").isEmpty())
    }
}
