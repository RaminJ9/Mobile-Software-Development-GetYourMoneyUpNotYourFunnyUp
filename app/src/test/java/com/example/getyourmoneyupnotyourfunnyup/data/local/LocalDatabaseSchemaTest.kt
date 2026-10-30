package com.example.getyourmoneyupnotyourfunnyup.data.local

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDatabaseSchemaTest {

    @Test
    fun databaseContract_containsAllRequiredTablesAndIndexes() {
        val statements = DatabaseContract.ALL_CREATE_STATEMENTS.joinToString("\n")
        assertTrue(statements.contains("CREATE TABLE IF NOT EXISTS users"))
        assertTrue(statements.contains("CREATE TABLE IF NOT EXISTS groups"))
        assertTrue(statements.contains("CREATE TABLE IF NOT EXISTS group_members"))
        assertTrue(statements.contains("CREATE TABLE IF NOT EXISTS expenses"))
        assertTrue(statements.contains("CREATE TABLE IF NOT EXISTS expense_splits"))
        assertTrue(statements.contains("CREATE TABLE IF NOT EXISTS settlements"))
        assertTrue(statements.contains("CREATE TABLE IF NOT EXISTS sync_queue"))
        assertTrue(statements.contains("ON DELETE CASCADE"))
    }

    @Test
    fun converters_roundTripAllEnums() {
        for (status in SyncStatus.entries) {
            assertEquals(status, Converters.toSyncStatus(Converters.fromSyncStatus(status)))
        }
        for (role in GroupRole.entries) {
            assertEquals(role, Converters.toGroupRole(Converters.fromGroupRole(role)))
        }
        for (status in SettlementStatus.entries) {
            assertEquals(status, Converters.toSettlementStatus(Converters.fromSettlementStatus(status)))
        }
        for (type in SyncEntityType.entries) {
            assertEquals(type, Converters.toSyncEntityType(Converters.fromSyncEntityType(type)))
        }
        for (action in SyncAction.entries) {
            assertEquals(action, Converters.toSyncAction(Converters.fromSyncAction(action)))
        }
    }

    @Test
    fun entities_roundTripToDomainPreservesOfflineSyncStatus() {
        val userEntity = UserEntity(
            id = "usr_1",
            email = "alex@example.com",
            displayName = "Alex",
            count = 7,
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val userDomain = userEntity.toDomain()
        assertEquals(7, userDomain.count)
        assertEquals(SyncStatus.PENDING_INSERT, userDomain.syncStatus)
        assertEquals(userEntity, UserEntity.fromDomain(userDomain))

        val memberEntity = GroupMemberEntity(
            groupId = "grp_1",
            userId = "usr_1",
            role = GroupRole.ADMIN
        )
        val groupEntity = GroupEntity(
            id = "grp_1",
            name = "Weekend Trip",
            description = "Copenhagen",
            createdBy = "usr_1",
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        val groupDomain = groupEntity.toDomain(listOf(memberEntity.toDomain("Alex")))
        assertEquals("Weekend Trip", groupDomain.name)
        assertEquals(1, groupDomain.members.size)
        assertEquals(SyncStatus.PENDING_UPDATE, groupDomain.syncStatus)

        val splitEntity = ExpenseSplitEntity(
            id = "spl_1",
            expenseId = "exp_1",
            userId = "usr_1",
            amountOwedCents = 2500L,
            shareWeight = 1.0
        )
        val expenseEntity = ExpenseEntity(
            id = "exp_1",
            groupId = "grp_1",
            title = "Dinner",
            amountCents = 5000L,
            payerId = "usr_1",
            createdBy = "usr_1",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val expenseDomain = expenseEntity.toDomain(listOf(splitEntity.toDomain()))
        assertEquals(5000L, expenseDomain.amountCents)
        assertEquals(2500L, expenseDomain.splits.first().amountOwedCents)
        assertEquals(SyncStatus.PENDING_INSERT, expenseDomain.syncStatus)

        val settlementEntity = SettlementEntity(
            id = "stl_1",
            groupId = "grp_1",
            debtorId = "usr_2",
            creditorId = "usr_1",
            amountCents = 2500L,
            paymentMethod = "MobilePay",
            status = SettlementStatus.PENDING,
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val settlementDomain = settlementEntity.toDomain()
        assertEquals(SettlementStatus.PENDING, settlementDomain.status)
        assertEquals(SyncStatus.PENDING_INSERT, settlementDomain.syncStatus)

        val queueItem = SyncQueueEntity(
            entityType = SyncEntityType.EXPENSE,
            entityId = "exp_1",
            action = SyncAction.CREATE,
            payloadJson = """{"id":"exp_1"}"""
        )
        assertEquals(0, queueItem.retryCount)
    }
}
