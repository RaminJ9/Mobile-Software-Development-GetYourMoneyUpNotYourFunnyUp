package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.GroupMember
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole

data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val role: GroupRole = GroupRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(displayName: String = ""): GroupMember = GroupMember(
        groupId = groupId,
        userId = userId,
        displayName = displayName,
        role = role,
        joinedAt = joinedAt
    )

    companion object {
        fun fromDomain(member: GroupMember): GroupMemberEntity = GroupMemberEntity(
            groupId = member.groupId,
            userId = member.userId,
            role = member.role,
            joinedAt = member.joinedAt
        )
    }
}
