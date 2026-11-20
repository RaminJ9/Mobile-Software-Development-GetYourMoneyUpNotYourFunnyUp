package com.example.getyourmoneyupnotyourfunnyup.data.sync

import android.content.ContentValues
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpensesTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.GroupsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SettlementsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.UsersTable
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
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Abstraction over Cloud Firestore document writes so [FirestoreSyncQueueProcessor] can run
 * against Firebase in production and deterministic fakes in JVM unit tests.
 */
interface RemoteDocumentStore {
    suspend fun setDocument(
        collectionPath: String,
        documentId: String,
        data: Map<String, Any?>,
        merge: Boolean = true
    )

    suspend fun deleteDocument(
        collectionPath: String,
        documentId: String
    )
}

class FirestoreRemoteDocumentStore(
    private val firestoreProvider: () -> FirebaseFirestore = { Firebase.firestore }
) : RemoteDocumentStore {

    override suspend fun setDocument(
        collectionPath: String,
        documentId: String,
        data: Map<String, Any?>,
        merge: Boolean
    ) {
        val docRef = firestoreProvider().collection(collectionPath).document(documentId)
        val task = if (merge) docRef.set(data, SetOptions.merge()) else docRef.set(data)
        suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener {
                if (continuation.isActive) continuation.resume(Unit)
            }.addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    override suspend fun deleteDocument(
        collectionPath: String,
        documentId: String
    ) {
        val docRef = firestoreProvider().collection(collectionPath).document(documentId)
        suspendCancellableCoroutine { continuation ->
            docRef.delete().addOnSuccessListener {
                if (continuation.isActive) continuation.resume(Unit)
            }.addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}

/**
 * Local database access abstraction used by [FirestoreSyncQueueProcessor] to read queued items
 * and reconcile local entity `sync_status` upon remote acknowledgement or failure.
 */
interface SyncQueueStore {
    fun nextPendingItem(): SyncQueueEntity?
    fun nextPendingItem(accountId: String): SyncQueueEntity? = nextPendingItem()
    fun getUser(userId: String): UserEntity?
    fun getGroupWithMembers(groupId: String): Pair<GroupEntity, List<GroupMemberEntity>>?
    fun getDisplayNames(userIds: Collection<String>): Map<String, String>
    fun getExpenseWithSplits(expenseId: String): Pair<ExpenseEntity, List<ExpenseSplitEntity>>?
    fun getSettlement(settlementId: String): SettlementEntity?
    fun getReminder(reminderId: String): com.example.getyourmoneyupnotyourfunnyup.model.Reminder? = null
    fun acknowledgeItem(item: SyncQueueEntity, expectedUpdatedAt: Long? = null)
    fun recordFailure(item: SyncQueueEntity, errorMessage: String?)
}

class DatabaseSyncQueueStore(
    private val database: AppDatabase
) : SyncQueueStore {

    override fun nextPendingItem(): SyncQueueEntity? =
        database.syncQueueDao.getAllPending().firstOrNull()

    override fun nextPendingItem(accountId: String): SyncQueueEntity? = pendingItemsForAccount(accountId).firstOrNull()

    fun pendingItemsForAccount(accountId: String): List<SyncQueueEntity> = database.syncQueueDao.getAllPending().filter { item ->
        val data = runCatching { SyncPayloadJson.decodeObject(item.payloadJson) }.getOrDefault(emptyMap())
        val owner = data["queuedBy"] as? String ?: data["recordedBy"] as? String ?: when (item.entityType) {
            SyncEntityType.USER -> item.entityId
            SyncEntityType.GROUP -> database.groupDao.getGroupById(item.entityId)?.createdBy
            SyncEntityType.EXPENSE -> database.expenseDao.getExpenseById(item.entityId)?.createdBy
            SyncEntityType.SETTLEMENT -> database.settlementDao.getSettlementById(item.entityId)?.debtorId
            SyncEntityType.REMINDER -> database.reminderDao.get(item.entityId)?.creditorId
        }
        owner == accountId
    }

    override fun getUser(userId: String): UserEntity? =
        database.userDao.getUserById(userId)

    override fun getGroupWithMembers(groupId: String): Pair<GroupEntity, List<GroupMemberEntity>>? {
        val group = database.groupDao.getGroupById(groupId) ?: return null
        val members = database.groupDao.getMembersForGroup(groupId)
        return group to members
    }

    override fun getDisplayNames(userIds: Collection<String>): Map<String, String> =
        userIds.distinct().mapNotNull { id ->
            database.userDao.getUserById(id)?.displayName?.takeIf { it.isNotBlank() }?.let { id to it }
        }.toMap()

    override fun getExpenseWithSplits(expenseId: String): Pair<ExpenseEntity, List<ExpenseSplitEntity>>? {
        val expense = database.expenseDao.getExpenseById(expenseId) ?: return null
        val splits = database.expenseDao.getSplitsForExpense(expenseId)
        return expense to splits
    }

    override fun getSettlement(settlementId: String): SettlementEntity? =
        database.settlementDao.getSettlementById(settlementId)

    override fun getReminder(reminderId: String) = database.reminderDao.get(reminderId)

    override fun acknowledgeItem(item: SyncQueueEntity, expectedUpdatedAt: Long?) {
        val db = database.writableDatabase
        var changedTable: String? = null
        db.beginTransaction()
        try {
            db.delete(
                SyncQueueTable.TABLE_NAME,
                "${SyncQueueTable.COL_QUEUE_ID} = ?",
                arrayOf(item.queueId.toString())
            )
            val stillQueued = db.rawQuery(
                """
                SELECT 1 FROM ${SyncQueueTable.TABLE_NAME}
                WHERE ${SyncQueueTable.COL_ENTITY_TYPE} = ? AND ${SyncQueueTable.COL_ENTITY_ID} = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(Converters.fromSyncEntityType(item.entityType), item.entityId)
            ).use { it.moveToFirst() }

            if (!stillQueued) {
                val syncedValue = Converters.fromSyncStatus(SyncStatus.SYNCED)
                when (item.entityType) {
                    SyncEntityType.USER -> {
                        val current = database.userDao.getUserById(item.entityId)
                        if (current != null && (expectedUpdatedAt == null || current.updatedAt == expectedUpdatedAt)) {
                            db.update(
                                UsersTable.TABLE_NAME,
                                ContentValues().apply { put(UsersTable.COL_SYNC_STATUS, syncedValue) },
                                "${UsersTable.COL_ID} = ?",
                                arrayOf(item.entityId)
                            )
                            changedTable = UsersTable.TABLE_NAME
                        }
                    }
                    SyncEntityType.GROUP -> {
                        val current = database.groupDao.getGroupById(item.entityId)
                        if (current != null && (expectedUpdatedAt == null || current.updatedAt == expectedUpdatedAt)) {
                            db.update(
                                GroupsTable.TABLE_NAME,
                                ContentValues().apply { put(GroupsTable.COL_SYNC_STATUS, syncedValue) },
                                "${GroupsTable.COL_ID} = ?",
                                arrayOf(item.entityId)
                            )
                            changedTable = GroupsTable.TABLE_NAME
                        }
                    }
                    SyncEntityType.EXPENSE -> {
                        db.update(
                            ExpensesTable.TABLE_NAME,
                            ContentValues().apply { put(ExpensesTable.COL_SYNC_STATUS, syncedValue) },
                            "${ExpensesTable.COL_ID} = ?",
                            arrayOf(item.entityId)
                        )
                        changedTable = ExpensesTable.TABLE_NAME
                    }
                    SyncEntityType.REMINDER -> {
                        db.update("reminders", ContentValues().apply { put("sync_status", syncedValue) }, "id = ?", arrayOf(item.entityId))
                        changedTable = "reminders"
                    }
                    SyncEntityType.SETTLEMENT -> {
                        db.update(
                            SettlementsTable.TABLE_NAME,
                            ContentValues().apply { put(SettlementsTable.COL_SYNC_STATUS, syncedValue) },
                            "${SettlementsTable.COL_ID} = ?",
                            arrayOf(item.entityId)
                        )
                        changedTable = SettlementsTable.TABLE_NAME
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (changedTable != null) {
            database.notifyTableChanged(SyncQueueTable.TABLE_NAME, changedTable)
        } else {
            database.notifyTableChanged(SyncQueueTable.TABLE_NAME)
        }
    }

    override fun recordFailure(item: SyncQueueEntity, errorMessage: String?) {
        val db = database.writableDatabase
        val failedValue = Converters.fromSyncStatus(SyncStatus.FAILED)
        val changedTable: String
        db.beginTransaction()
        try {
            db.execSQL(
                """
                UPDATE ${SyncQueueTable.TABLE_NAME}
                SET ${SyncQueueTable.COL_RETRY_COUNT} = ${SyncQueueTable.COL_RETRY_COUNT} + 1,
                    ${SyncQueueTable.COL_LAST_ATTEMPT_AT} = ?,
                    ${SyncQueueTable.COL_ERROR_MESSAGE} = ?
                WHERE ${SyncQueueTable.COL_QUEUE_ID} = ?
                """.trimIndent(),
                arrayOf<Any?>(System.currentTimeMillis(), errorMessage, item.queueId)
            )
            changedTable = when (item.entityType) {
                SyncEntityType.USER -> {
                    db.update(
                        UsersTable.TABLE_NAME,
                        ContentValues().apply { put(UsersTable.COL_SYNC_STATUS, failedValue) },
                        "${UsersTable.COL_ID} = ?",
                        arrayOf(item.entityId)
                    )
                    UsersTable.TABLE_NAME
                }
                SyncEntityType.GROUP -> {
                    db.update(
                        GroupsTable.TABLE_NAME,
                        ContentValues().apply { put(GroupsTable.COL_SYNC_STATUS, failedValue) },
                        "${GroupsTable.COL_ID} = ?",
                        arrayOf(item.entityId)
                    )
                    GroupsTable.TABLE_NAME
                }
                SyncEntityType.EXPENSE -> {
                    db.update(
                        ExpensesTable.TABLE_NAME,
                        ContentValues().apply { put(ExpensesTable.COL_SYNC_STATUS, failedValue) },
                        "${ExpensesTable.COL_ID} = ?",
                        arrayOf(item.entityId)
                    )
                    ExpensesTable.TABLE_NAME
                }
                SyncEntityType.REMINDER -> {
                    db.update("reminders", ContentValues().apply { put("sync_status", failedValue) }, "id = ?", arrayOf(item.entityId))
                    "reminders"
                }
                SyncEntityType.SETTLEMENT -> {
                    db.update(
                        SettlementsTable.TABLE_NAME,
                        ContentValues().apply { put(SettlementsTable.COL_SYNC_STATUS, failedValue) },
                        "${SettlementsTable.COL_ID} = ?",
                        arrayOf(item.entityId)
                    )
                    SettlementsTable.TABLE_NAME
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.notifyTableChanged(SyncQueueTable.TABLE_NAME, changedTable)
    }
}

/**
 * Concrete [SyncQueueProcessor] that drains pending local `sync_queue` entries and persists
 * `USER`, `GROUP`, `EXPENSE` and `SETTLEMENT` mutations to Cloud Firestore using the document mappers.
 */
class FirestoreSyncQueueProcessor(
    private val store: SyncQueueStore,
    private val remoteStore: RemoteDocumentStore = FirestoreRemoteDocumentStore(),
    private val currentAccountId: () -> String?
) : SyncQueueProcessor {

    constructor(
        database: AppDatabase,
        currentAccountId: () -> String?,
        remoteStore: RemoteDocumentStore = FirestoreRemoteDocumentStore()
    ) : this(DatabaseSyncQueueStore(database), remoteStore, currentAccountId)

    override suspend fun processNext(accountId: String): SyncStep {
        if (accountId.isBlank() || currentAccountId() != accountId) {
            return SyncStep.NeedsAttention
        }
        val item = store.nextPendingItem(accountId) ?: return SyncStep.Empty

        return try {
            when (item.entityType) {
                SyncEntityType.USER -> syncUser(accountId, item)
                SyncEntityType.GROUP -> syncGroup(accountId, item)
                SyncEntityType.EXPENSE -> syncExpense(accountId, item)
                SyncEntityType.SETTLEMENT -> syncSettlement(accountId, item)
                SyncEntityType.REMINDER -> syncReminder(accountId, item)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            store.recordFailure(item, error.localizedMessage ?: error.message ?: "Sync failed")
            SyncStep.RetryLater
        }
    }

    private suspend fun syncUser(accountId: String, item: SyncQueueEntity): SyncStep {
        if (item.entityId != accountId) {
            return SyncStep.NeedsAttention
        }
        val localUser = store.getUser(item.entityId)
        val payloadMap = SyncPayloadJson.decodeObject(item.payloadJson)
        val documentMap = when {
            localUser != null -> UserDocumentMapper.toMap(localUser.toDomain())
            payloadMap.isNotEmpty() -> payloadMap
            else -> {
                store.acknowledgeItem(item)
                return SyncStep.Acknowledged(accountId)
            }
        }
        remoteStore.setDocument(USERS_COLLECTION, item.entityId, documentMap, merge = true)
        if (currentAccountId() != accountId) return SyncStep.NeedsAttention
        store.acknowledgeItem(item, localUser?.updatedAt)
        return SyncStep.Acknowledged(accountId)
    }

    private suspend fun syncGroup(accountId: String, item: SyncQueueEntity): SyncStep {
        if (item.action == SyncAction.DELETE) {
            remoteStore.deleteDocument(GROUPS_COLLECTION, item.entityId)
            if (currentAccountId() != accountId) return SyncStep.NeedsAttention
            store.acknowledgeItem(item)
            return SyncStep.Acknowledged(accountId)
        }

        val localGroupWithMembers = store.getGroupWithMembers(item.entityId)
        val documentMap: Map<String, Any?>
        val revision: Long?
        if (localGroupWithMembers != null) {
            val (group, members) = localGroupWithMembers
            val displayNames = store.getDisplayNames(members.map { it.userId })
            documentMap = GroupDocumentMapper.toMap(group, members, displayNames)
            revision = group.updatedAt
        } else {
            val payload = SyncPayloadJson.decodeObject(item.payloadJson)
            if (payload.containsKey("name")) {
                val (group, members) = GroupDocumentMapper.entitiesFromMap(payload, item.entityId)
                documentMap = GroupDocumentMapper.toMap(group, members)
                revision = group.updatedAt
            } else {
                store.acknowledgeItem(item)
                return SyncStep.Acknowledged(accountId)
            }
        }

        remoteStore.setDocument(GROUPS_COLLECTION, item.entityId, documentMap, merge = true)
        if (currentAccountId() != accountId) return SyncStep.NeedsAttention
        store.acknowledgeItem(item, revision)
        return SyncStep.Acknowledged(accountId)
    }

    private suspend fun syncExpense(accountId: String, item: SyncQueueEntity): SyncStep {
        val localExpenseWithSplits = store.getExpenseWithSplits(item.entityId)
        val groupId: String
        val documentMap: Map<String, Any?>
        if (localExpenseWithSplits != null) {
            val (expense, splits) = localExpenseWithSplits
            val effective = if (item.action == SyncAction.DELETE) expense.copy(isDeleted = true) else expense
            groupId = effective.groupId
            documentMap = ExpenseDocumentMapper.toMap(effective, splits)
        } else {
            val payload = SyncPayloadJson.decodeObject(item.payloadJson)
            val (expense, splits) = ExpenseDocumentMapper.entitiesFromMap(payload, item.entityId)
            val effective = if (item.action == SyncAction.DELETE) expense.copy(isDeleted = true) else expense
            if (effective.groupId.isBlank()) {
                store.acknowledgeItem(item)
                return SyncStep.Acknowledged(accountId)
            }
            groupId = effective.groupId
            documentMap = ExpenseDocumentMapper.toMap(effective, splits)
        }

        remoteStore.setDocument(
            collectionPath = "$GROUPS_COLLECTION/$groupId/$EXPENSES_SUBCOLLECTION",
            documentId = item.entityId,
            data = documentMap,
            merge = true
        )
        if (currentAccountId() != accountId) return SyncStep.NeedsAttention
        store.acknowledgeItem(item)
        return SyncStep.Acknowledged(accountId)
    }

    private suspend fun syncReminder(accountId: String, item: SyncQueueEntity): SyncStep {
        val reminder = store.getReminder(item.entityId) ?: com.example.getyourmoneyupnotyourfunnyup.data.remote.ReminderMapper.fromMap(
            SyncPayloadJson.decodeObject(item.payloadJson), item.entityId)
        if (reminder.creditorId != accountId || currentAccountId() != accountId) return SyncStep.NeedsAttention
        val members = store.getGroupWithMembers(reminder.groupId)?.second.orEmpty()
        if (members.none { it.userId == accountId } || members.none { it.userId == reminder.debtorId }) return SyncStep.NeedsAttention
        remoteStore.setDocument("$GROUPS_COLLECTION/${reminder.groupId}/reminders", reminder.id,
            com.example.getyourmoneyupnotyourfunnyup.data.remote.ReminderMapper.toMap(reminder), merge = true)
        if (currentAccountId() != accountId) return SyncStep.NeedsAttention
        store.acknowledgeItem(item)
        return SyncStep.Acknowledged(accountId)
    }

    private suspend fun syncSettlement(accountId: String, item: SyncQueueEntity): SyncStep {
        val localSettlement = store.getSettlement(item.entityId)
        val payload = SyncPayloadJson.decodeObject(item.payloadJson)
        val recordedBy = (payload["recordedBy"] as? String)?.takeIf { it.isNotBlank() } ?: accountId
        val settlement = localSettlement ?: SettlementDocumentMapper.entityFromMap(payload, item.entityId)
        if (settlement.groupId.isBlank()) {
            store.acknowledgeItem(item)
            return SyncStep.Acknowledged(accountId)
        }

        val collectionPath = "$GROUPS_COLLECTION/${settlement.groupId}/$SETTLEMENTS_SUBCOLLECTION"
        if (item.action == SyncAction.DELETE) {
            remoteStore.deleteDocument(collectionPath, item.entityId)
        } else {
            val documentMap = SettlementDocumentMapper.toMap(settlement, recordedBy = recordedBy)
            remoteStore.setDocument(collectionPath, item.entityId, documentMap, merge = true)
        }
        if (currentAccountId() != accountId) return SyncStep.NeedsAttention
        store.acknowledgeItem(item)
        return SyncStep.Acknowledged(accountId)
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val GROUPS_COLLECTION = "groups"
        const val EXPENSES_SUBCOLLECTION = "expenses"
        const val SETTLEMENTS_SUBCOLLECTION = "settlements"
    }
}
