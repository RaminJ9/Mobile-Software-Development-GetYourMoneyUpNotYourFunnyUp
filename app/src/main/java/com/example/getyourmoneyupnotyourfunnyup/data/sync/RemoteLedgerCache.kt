package com.example.getyourmoneyupnotyourfunnyup.data.sync

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.data.remote.*
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Normalize authorized remote snapshots into SQLite; queued local edits always win until acknowledged. */
class RemoteLedgerCache(private val database: AppDatabase, private val accountId: String, private val currentAccountId: () -> String?) {
    private fun checkAccount() { if (currentAccountId() != accountId) throw SecurityException("Account changed.") }
    private fun checkGroup(groupId: String) {
        checkAccount()
        if (database.groupDao.getMembersForGroup(groupId).none { it.userId == accountId }) throw SecurityException("No group access.")
    }
    private fun ensureUsers(ids: Collection<String>, names: Map<String, String> = emptyMap()) {
        ids.distinct().forEach { id ->
            require(id.isNotBlank())
            if (database.userDao.getUserById(id) == null) database.userDao.upsertUser(UserEntity(id, "", names[id]?.takeIf { it.isNotBlank() } ?: "Member", updatedAt = 0))
        }
    }
    suspend fun group(id: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        database.writableDatabase.transaction {
            checkAccount(); require(data["id"] == null || data["id"] == id)
            val local = database.groupDao.getGroupById(id)
            if (local != null && local.syncStatus != SyncStatus.SYNCED) return@transaction
            val (group, members) = GroupDocumentMapper.entitiesFromMap(data, id, isFavourite = local?.isFavourite ?: false)
            require(group.id == id && members.any { it.userId == accountId })
            if (local != null && local.updatedAt > group.updatedAt) return@transaction
            val names = GroupDocumentMapper.fromMap(data, id).members.associate { it.userId to it.displayName }
            ensureUsers(members.map { it.userId } + group.createdBy, names)
            database.groupDao.upsertGroupWithMembers(group, members)
            checkAccount()
        }
        database.notifyTableChanged("users", "groups", "group_members")
    }
    suspend fun expense(groupId: String, id: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        database.writableDatabase.transaction {
            checkGroup(groupId); require(data["id"] == null || data["id"] == id)
            val local = database.expenseDao.getExpenseById(id)
            require(local == null || local.groupId == groupId)
            if (local != null && local.syncStatus != SyncStatus.SYNCED) return@transaction
            val path = data["receiptStoragePath"] ?: data["receiptRemotePath"]
            val (expense, splits) = ExpenseDocumentMapper.entitiesFromMap(data, id, groupId,
                receiptLocalUri = local?.receiptLocalUri?.takeIf { path == local.receiptRemotePath })
            require(expense.groupId == groupId && expense.id == id)
            ensureUsers(listOf(expense.payerId, expense.createdBy) + splits.map { it.userId })
            database.expenseDao.upsertExpenseWithSplits(expense, splits)
            checkAccount()
        }
        database.notifyTableChanged("expenses", "expense_splits", "users")
    }
    suspend fun settlement(groupId: String, id: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        database.writableDatabase.transaction {
            checkGroup(groupId); require(data["id"] == null || data["id"] == id)
            val local = database.settlementDao.getSettlementById(id)
            require(local == null || local.groupId == groupId)
            if (local != null && local.syncStatus != SyncStatus.SYNCED) return@transaction
            val remote = SettlementDocumentMapper.entityFromMap(data, id, groupId)
            require(remote.groupId == groupId && remote.id == id && remote.amountCents > 0 && remote.debtorId != remote.creditorId)
            ensureUsers(listOf(remote.debtorId, remote.creditorId))
            database.settlementDao.upsertSettlement(remote)
            checkAccount()
        }
        database.notifyTableChanged("settlements", "users")
    }
    suspend fun reminder(groupId: String, id: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        database.writableDatabase.transaction {
            checkGroup(groupId)
            val local = database.reminderDao.get(id)
            require(local == null || local.groupId == groupId)
            if (local != null && local.syncStatus != SyncStatus.SYNCED) return@transaction
            val remote = ReminderMapper.fromMap(data, id)
            require(remote.groupId == groupId)
            ensureUsers(listOf(remote.debtorId, remote.creditorId))
            database.reminderDao.save(remote)
            checkAccount()
        }
        database.notifyTableChanged("reminders", "users")
    }
    suspend fun revokeAbsentGroups(serverIds: Set<String>) = withContext(Dispatchers.IO) {
        database.writableDatabase.transaction {
            checkAccount()
            database.groupDao.getGroupsForUser(accountId).filter { it.id !in serverIds && it.syncStatus == SyncStatus.SYNCED }.forEach {
                database.groupDao.removeMember(it.id, accountId)
            }
            checkAccount()
        }
        database.notifyTableChanged("group_members")
    }
    suspend fun removed(groupId: String, collection: String, id: String) = withContext(Dispatchers.IO) {
        require(collection in setOf("expenses", "settlements", "reminders"))
        database.writableDatabase.transaction {
            checkGroup(groupId)
            if (collection == "expenses") update(collection, ContentValues().apply { put("is_deleted", 1) }, "id = ? AND group_id = ? AND sync_status = ?", arrayOf(id, groupId, "SYNCED"))
            else delete(collection, "id = ? AND group_id = ? AND sync_status = ?", arrayOf(id, groupId, "SYNCED"))
            checkAccount()
        }
        database.notifyTableChanged(collection)
    }
}
