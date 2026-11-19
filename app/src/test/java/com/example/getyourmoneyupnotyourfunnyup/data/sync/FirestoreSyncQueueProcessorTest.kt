package com.example.getyourmoneyupnotyourfunnyup.data.sync

import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseSplitEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SettlementEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncQueueEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.data.remote.ExpenseDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.GroupDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SettlementDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SyncPayloadJson
import com.example.getyourmoneyupnotyourfunnyup.data.remote.UserDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreSyncQueueProcessorTest {

    private class FakeRemoteDocumentStore : RemoteDocumentStore {
        val documents = linkedMapOf<String, Map<String, Any?>>()
        val deletedPaths = mutableListOf<String>()
        var failNextWith: Exception? = null

        override suspend fun setDocument(
            collectionPath: String,
            documentId: String,
            data: Map<String, Any?>,
            merge: Boolean
        ) {
            failNextWith?.let { error ->
                failNextWith = null
                throw error
            }
            val key = "$collectionPath/$documentId"
            val existing = if (merge) documents[key].orEmpty() else emptyMap()
            documents[key] = existing + data
        }

        override suspend fun deleteDocument(collectionPath: String, documentId: String) {
            failNextWith?.let { error ->
                failNextWith = null
                throw error
            }
            val key = "$collectionPath/$documentId"
            documents.remove(key)
            deletedPaths += key
        }
    }

    private class InMemorySyncQueueStore : SyncQueueStore {
        val queue = mutableListOf<SyncQueueEntity>()
        val users = linkedMapOf<String, UserEntity>()
        val groups = linkedMapOf<String, Pair<GroupEntity, List<GroupMemberEntity>>>()
        val expenses = linkedMapOf<String, Pair<ExpenseEntity, List<ExpenseSplitEntity>>>()
        val settlements = linkedMapOf<String, SettlementEntity>()

        override fun nextPendingItem(): SyncQueueEntity? = queue.minByOrNull { it.queueId }

        override fun getUser(userId: String): UserEntity? = users[userId]

        override fun getGroupWithMembers(groupId: String): Pair<GroupEntity, List<GroupMemberEntity>>? =
            groups[groupId]

        override fun getDisplayNames(userIds: Collection<String>): Map<String, String> =
            userIds.mapNotNull { id -> users[id]?.displayName?.let { id to it } }.toMap()

        override fun getExpenseWithSplits(expenseId: String): Pair<ExpenseEntity, List<ExpenseSplitEntity>>? =
            expenses[expenseId]

        override fun getSettlement(settlementId: String): SettlementEntity? = settlements[settlementId]

        override fun acknowledgeItem(item: SyncQueueEntity, expectedUpdatedAt: Long?) {
            queue.removeAll { it.queueId == item.queueId }
            val stillQueued = queue.any { it.entityType == item.entityType && it.entityId == item.entityId }
            if (stillQueued) return
            when (item.entityType) {
                SyncEntityType.USER -> {
                    val user = users[item.entityId]
                    if (user != null && (expectedUpdatedAt == null || user.updatedAt == expectedUpdatedAt)) {
                        users[item.entityId] = user.copy(syncStatus = SyncStatus.SYNCED)
                    }
                }
                SyncEntityType.GROUP -> {
                    val pair = groups[item.entityId]
                    if (pair != null && (expectedUpdatedAt == null || pair.first.updatedAt == expectedUpdatedAt)) {
                        groups[item.entityId] = pair.first.copy(syncStatus = SyncStatus.SYNCED) to pair.second
                    }
                }
                SyncEntityType.EXPENSE -> {
                    val pair = expenses[item.entityId]
                    if (pair != null) {
                        expenses[item.entityId] = pair.first.copy(syncStatus = SyncStatus.SYNCED) to pair.second
                    }
                }
                SyncEntityType.SETTLEMENT -> {
                    val settlement = settlements[item.entityId]
                    if (settlement != null) {
                        settlements[item.entityId] = settlement.copy(syncStatus = SyncStatus.SYNCED)
                    }
                }
            }
        }

        override fun recordFailure(item: SyncQueueEntity, errorMessage: String?) {
            val idx = queue.indexOfFirst { it.queueId == item.queueId }
            if (idx >= 0) {
                val existing = queue[idx]
                queue[idx] = existing.copy(
                    retryCount = existing.retryCount + 1,
                    errorMessage = errorMessage
                )
            }
            when (item.entityType) {
                SyncEntityType.USER -> users[item.entityId]?.let {
                    users[item.entityId] = it.copy(syncStatus = SyncStatus.FAILED)
                }
                SyncEntityType.GROUP -> groups[item.entityId]?.let { (g, m) ->
                    groups[item.entityId] = g.copy(syncStatus = SyncStatus.FAILED) to m
                }
                SyncEntityType.EXPENSE -> expenses[item.entityId]?.let { (e, s) ->
                    expenses[item.entityId] = e.copy(syncStatus = SyncStatus.FAILED) to s
                }
                SyncEntityType.SETTLEMENT -> settlements[item.entityId]?.let {
                    settlements[item.entityId] = it.copy(syncStatus = SyncStatus.FAILED)
                }
            }
        }
    }

    @Test
    fun drainsUserGroupExpenseAndSettlementToFirestoreAndMarksAllSynced() = runTest {
        val store = InMemorySyncQueueStore()
        val remote = FakeRemoteDocumentStore()
        val accountId = "u_victor"

        val user = UserEntity(
            id = accountId,
            email = "victor@mail.dk",
            displayName = "Victor",
            phoneNumber = "+4511223344",
            excludeAlcohol = true,
            syncStatus = SyncStatus.PENDING_UPDATE,
            updatedAt = 100L
        )
        store.users[accountId] = user
        store.users["u_mads"] = UserEntity("u_mads", "mads@mail.dk", "Mads")

        val group = GroupEntity(
            id = "g1",
            name = "Roskilde",
            description = "Festival",
            currency = "DKK",
            createdBy = accountId,
            createdAt = 200L,
            updatedAt = 210L,
            syncStatus = SyncStatus.PENDING_INSERT,
            colourIndex = 2
        )
        val members = listOf(
            GroupMemberEntity("g1", accountId, GroupRole.ADMIN, 200L),
            GroupMemberEntity("g1", "u_mads", GroupRole.MEMBER, 201L)
        )
        store.groups["g1"] = group to members

        val expense = ExpenseEntity(
            id = "e1",
            groupId = "g1",
            title = "Pizza",
            amountCents = 18000L,
            currency = "DKK",
            payerId = accountId,
            createdAt = 300L,
            createdBy = accountId,
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val splits = listOf(
            ExpenseSplitEntity("s1", "e1", accountId, 9000L),
            ExpenseSplitEntity("s2", "e1", "u_mads", 9000L)
        )
        store.expenses["e1"] = expense to splits

        val settlement = SettlementEntity(
            id = "stl1",
            groupId = "g1",
            debtorId = "u_mads",
            creditorId = accountId,
            amountCents = 9000L,
            currency = "DKK",
            paymentMethod = "MobilePay",
            status = SettlementStatus.COMPLETED,
            createdAt = 400L,
            syncStatus = SyncStatus.PENDING_INSERT
        )
        store.settlements["stl1"] = settlement

        store.queue += listOf(
            SyncQueueEntity(
                queueId = 1L,
                entityType = SyncEntityType.USER,
                entityId = accountId,
                action = SyncAction.UPDATE,
                payloadJson = SyncPayloadJson.encode(UserDocumentMapper.profileUpdateMap(user.toDomain()))
            ),
            SyncQueueEntity(
                queueId = 2L,
                entityType = SyncEntityType.GROUP,
                entityId = "g1",
                action = SyncAction.CREATE,
                payloadJson = SyncPayloadJson.encode(GroupDocumentMapper.toMap(group, members))
            ),
            SyncQueueEntity(
                queueId = 3L,
                entityType = SyncEntityType.EXPENSE,
                entityId = "e1",
                action = SyncAction.CREATE,
                payloadJson = SyncPayloadJson.encode(ExpenseDocumentMapper.toMap(expense, splits))
            ),
            SyncQueueEntity(
                queueId = 4L,
                entityType = SyncEntityType.SETTLEMENT,
                entityId = "stl1",
                action = SyncAction.CREATE,
                payloadJson = SyncPayloadJson.encode(SettlementDocumentMapper.toMap(settlement, recordedBy = accountId))
            )
        )

        val processor = FirestoreSyncQueueProcessor(store, remote) { accountId }
        val runner = SyncRunner(processor, { accountId })

        assertEquals(SyncRunResult.Complete, runner.run(accountId))
        assertTrue(store.queue.isEmpty())

        // All local entities marked SYNCED
        assertEquals(SyncStatus.SYNCED, store.users[accountId]!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, store.groups["g1"]!!.first.syncStatus)
        assertEquals(SyncStatus.SYNCED, store.expenses["e1"]!!.first.syncStatus)
        assertEquals(SyncStatus.SYNCED, store.settlements["stl1"]!!.syncStatus)

        // All remote documents written to their expected Firestore paths
        assertEquals("Victor", remote.documents["users/$accountId"]?.get("displayName"))
        assertEquals("Roskilde", remote.documents["groups/g1"]?.get("name"))
        assertEquals(listOf(accountId, "u_mads"), remote.documents["groups/g1"]?.get("memberIds"))
        assertEquals("Pizza", remote.documents["groups/g1/expenses/e1"]?.get("title"))
        assertEquals(18000L, remote.documents["groups/g1/expenses/e1"]?.get("amountCents"))
        assertEquals(9000L, remote.documents["groups/g1/settlements/stl1"]?.get("amountCents"))
        assertEquals(accountId, remote.documents["groups/g1/settlements/stl1"]?.get("recordedBy"))
    }

    @Test
    fun reliability_networkFailureMarksEntityFailedAndRetriesSuccessfullyOnNextRun() = runTest {
        val store = InMemorySyncQueueStore()
        val remote = FakeRemoteDocumentStore()
        val accountId = "u_victor"

        val expense = ExpenseEntity(
            id = "e_offline",
            groupId = "g1",
            title = "Offline Coffee",
            amountCents = 4500L,
            currency = "DKK",
            payerId = accountId,
            createdBy = accountId,
            syncStatus = SyncStatus.PENDING_INSERT
        )
        val splits = listOf(ExpenseSplitEntity("s1", "e_offline", accountId, 4500L))
        store.expenses["e_offline"] = expense to splits
        store.queue += SyncQueueEntity(
            queueId = 10L,
            entityType = SyncEntityType.EXPENSE,
            entityId = "e_offline",
            action = SyncAction.CREATE,
            payloadJson = SyncPayloadJson.encode(ExpenseDocumentMapper.toMap(expense, splits))
        )

        val processor = FirestoreSyncQueueProcessor(store, remote) { accountId }
        val runner = SyncRunner(processor, { accountId })

        // First attempt fails due to offline / network error
        remote.failNextWith = IllegalStateException("Network unavailable")
        assertEquals(SyncRunResult.Retry, runner.run(accountId))
        assertEquals(SyncStatus.FAILED, store.expenses["e_offline"]!!.first.syncStatus)
        assertEquals(1, store.queue.single().retryCount)
        assertEquals("Network unavailable", store.queue.single().errorMessage)
        assertFalse(remote.documents.containsKey("groups/g1/expenses/e_offline"))

        // Second attempt succeeds when connection is restored
        assertEquals(SyncRunResult.Complete, runner.run(accountId))
        assertTrue(store.queue.isEmpty())
        assertEquals(SyncStatus.SYNCED, store.expenses["e_offline"]!!.first.syncStatus)
        assertEquals("Offline Coffee", remote.documents["groups/g1/expenses/e_offline"]?.get("title"))
    }
}
