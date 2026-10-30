package com.example.getyourmoneyupnotyourfunnyup.data.local.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.Converters
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.GroupMembersTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.GroupsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GroupDao(private val database: AppDatabase) {

    fun observeGroupsForUser(userId: String): Flow<List<GroupEntity>> =
        database.observeTables(GroupsTable.TABLE_NAME, GroupMembersTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getGroupsForUser(userId) }
        }

    fun observeAllGroups(): Flow<List<GroupEntity>> =
        database.observeTables(GroupsTable.TABLE_NAME, GroupMembersTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getAllGroups() }
        }

    fun observeMembersForGroup(groupId: String): Flow<List<GroupMemberEntity>> =
        database.observeTables(GroupMembersTable.TABLE_NAME).map {
            withContext(Dispatchers.IO) { getMembersForGroup(groupId) }
        }

    fun upsertGroupWithMembers(group: GroupEntity, members: List<GroupMemberEntity>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                GroupsTable.TABLE_NAME,
                null,
                group.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            for (member in members) {
                db.insertWithOnConflict(
                    GroupMembersTable.TABLE_NAME,
                    null,
                    member.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.notifyTableChanged(GroupsTable.TABLE_NAME, GroupMembersTable.TABLE_NAME)
    }

    fun upsertGroup(group: GroupEntity) {
        val db = database.writableDatabase
        db.insertWithOnConflict(
            GroupsTable.TABLE_NAME,
            null,
            group.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        database.notifyTableChanged(GroupsTable.TABLE_NAME)
    }

    fun addMember(member: GroupMemberEntity) {
        val db = database.writableDatabase
        db.insertWithOnConflict(
            GroupMembersTable.TABLE_NAME,
            null,
            member.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        database.notifyTableChanged(GroupMembersTable.TABLE_NAME)
    }

    fun removeMember(groupId: String, userId: String) {
        val db = database.writableDatabase
        db.delete(
            GroupMembersTable.TABLE_NAME,
            "${GroupMembersTable.COL_GROUP_ID} = ? AND ${GroupMembersTable.COL_USER_ID} = ?",
            arrayOf(groupId, userId)
        )
        database.notifyTableChanged(GroupMembersTable.TABLE_NAME)
    }

    fun deleteGroup(groupId: String) {
        val db = database.writableDatabase
        db.delete(
            GroupsTable.TABLE_NAME,
            "${GroupsTable.COL_ID} = ?",
            arrayOf(groupId)
        )
        database.notifyTableChanged(GroupsTable.TABLE_NAME, GroupMembersTable.TABLE_NAME)
    }

    fun getGroupById(groupId: String): GroupEntity? {
        val db = database.readableDatabase
        db.query(
            GroupsTable.TABLE_NAME,
            null,
            "${GroupsTable.COL_ID} = ?",
            arrayOf(groupId),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toGroupEntity() else null
        }
    }

    fun getAllGroups(): List<GroupEntity> {
        val db = database.readableDatabase
        val groups = mutableListOf<GroupEntity>()
        db.query(
            GroupsTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "${GroupsTable.COL_UPDATED_AT} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                groups.add(cursor.toGroupEntity())
            }
        }
        return groups
    }

    fun getGroupsForUser(userId: String): List<GroupEntity> {
        val db = database.readableDatabase
        val groups = mutableListOf<GroupEntity>()
        val sql = """
            SELECT g.* FROM ${GroupsTable.TABLE_NAME} g
            INNER JOIN ${GroupMembersTable.TABLE_NAME} gm
                ON g.${GroupsTable.COL_ID} = gm.${GroupMembersTable.COL_GROUP_ID}
            WHERE gm.${GroupMembersTable.COL_USER_ID} = ?
            ORDER BY g.${GroupsTable.COL_UPDATED_AT} DESC
        """.trimIndent()
        db.rawQuery(sql, arrayOf(userId)).use { cursor ->
            while (cursor.moveToNext()) {
                groups.add(cursor.toGroupEntity())
            }
        }
        return groups
    }

    fun getMembersForGroup(groupId: String): List<GroupMemberEntity> {
        val db = database.readableDatabase
        val members = mutableListOf<GroupMemberEntity>()
        db.query(
            GroupMembersTable.TABLE_NAME,
            null,
            "${GroupMembersTable.COL_GROUP_ID} = ?",
            arrayOf(groupId),
            null,
            null,
            "${GroupMembersTable.COL_JOINED_AT} ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                members.add(cursor.toGroupMemberEntity())
            }
        }
        return members
    }

    fun updateSyncStatus(groupId: String, status: SyncStatus) {
        val db = database.writableDatabase
        val values = ContentValues().apply {
            put(GroupsTable.COL_SYNC_STATUS, Converters.fromSyncStatus(status))
            put(GroupsTable.COL_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(
            GroupsTable.TABLE_NAME,
            values,
            "${GroupsTable.COL_ID} = ?",
            arrayOf(groupId)
        )
        database.notifyTableChanged(GroupsTable.TABLE_NAME)
    }

    private fun GroupEntity.toContentValues(): ContentValues = ContentValues().apply {
        put(GroupsTable.COL_ID, id)
        put(GroupsTable.COL_NAME, name)
        put(GroupsTable.COL_DESCRIPTION, description)
        put(GroupsTable.COL_CURRENCY, currency)
        put(GroupsTable.COL_CREATED_BY, createdBy)
        put(GroupsTable.COL_CREATED_AT, createdAt)
        put(GroupsTable.COL_UPDATED_AT, updatedAt)
        put(GroupsTable.COL_SYNC_STATUS, Converters.fromSyncStatus(syncStatus))
    }

    private fun GroupMemberEntity.toContentValues(): ContentValues = ContentValues().apply {
        put(GroupMembersTable.COL_GROUP_ID, groupId)
        put(GroupMembersTable.COL_USER_ID, userId)
        put(GroupMembersTable.COL_ROLE, Converters.fromGroupRole(role))
        put(GroupMembersTable.COL_JOINED_AT, joinedAt)
    }

    private fun Cursor.toGroupEntity(): GroupEntity {
        val descIdx = getColumnIndexOrThrow(GroupsTable.COL_DESCRIPTION)
        return GroupEntity(
            id = getString(getColumnIndexOrThrow(GroupsTable.COL_ID)),
            name = getString(getColumnIndexOrThrow(GroupsTable.COL_NAME)),
            description = if (isNull(descIdx)) null else getString(descIdx),
            currency = getString(getColumnIndexOrThrow(GroupsTable.COL_CURRENCY)),
            createdBy = getString(getColumnIndexOrThrow(GroupsTable.COL_CREATED_BY)),
            createdAt = getLong(getColumnIndexOrThrow(GroupsTable.COL_CREATED_AT)),
            updatedAt = getLong(getColumnIndexOrThrow(GroupsTable.COL_UPDATED_AT)),
            syncStatus = Converters.toSyncStatus(getString(getColumnIndexOrThrow(GroupsTable.COL_SYNC_STATUS)))
        )
    }

    private fun Cursor.toGroupMemberEntity(): GroupMemberEntity = GroupMemberEntity(
        groupId = getString(getColumnIndexOrThrow(GroupMembersTable.COL_GROUP_ID)),
        userId = getString(getColumnIndexOrThrow(GroupMembersTable.COL_USER_ID)),
        role = Converters.toGroupRole(getString(getColumnIndexOrThrow(GroupMembersTable.COL_ROLE))),
        joinedAt = getLong(getColumnIndexOrThrow(GroupMembersTable.COL_JOINED_AT))
    )
}
