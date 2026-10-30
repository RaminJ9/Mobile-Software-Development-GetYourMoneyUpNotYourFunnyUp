package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.GroupMember
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

data class GroupEntity(
    val id: String,
    val name: String,
    val description: String? = null,
    val currency: String = "EUR",
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
) {
    fun toDomain(members: List<GroupMember> = emptyList()): Group = Group(
        id = id,
        name = name,
        description = description,
        currency = currency,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        members = members,
        syncStatus = syncStatus
    )

    companion object {
        fun fromDomain(group: Group): GroupEntity = GroupEntity(
            id = group.id,
            name = group.name,
            description = group.description,
            currency = group.currency,
            createdBy = group.createdBy,
            createdAt = group.createdAt,
            updatedAt = group.updatedAt,
            syncStatus = group.syncStatus
        )
    }
}
