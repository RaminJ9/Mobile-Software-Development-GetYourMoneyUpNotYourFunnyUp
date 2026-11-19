package com.example.getyourmoneyupnotyourfunnyup.data.remote

import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.GroupMember
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

/**
 * Pure mapper converting [Group], [GroupEntity] and [GroupMemberEntity] to and from
 * Cloud Firestore `/groups/{groupId}` document maps.
 */
internal object GroupDocumentMapper {

    fun toMap(
        group: Group,
        colourIndex: Int = 0,
        invitedUserIds: Set<String> = emptySet()
    ): Map<String, Any?> = mapOf(
        "id" to group.id,
        "name" to group.name,
        "description" to group.description,
        "currency" to group.currency,
        "colourIndex" to colourIndex,
        "createdBy" to group.createdBy,
        "createdAt" to group.createdAt,
        "updatedAt" to group.updatedAt,
        "memberIds" to group.members
            .filterNot { it.userId in invitedUserIds || it.userId.startsWith(INVITE_PREFIX) }
            .map { it.userId },
        "members" to group.members.map { member ->
            memberToMap(
                member = member,
                invited = member.userId in invitedUserIds || member.userId.startsWith(INVITE_PREFIX)
            )
        }
    )

    fun toMap(
        group: GroupEntity,
        members: List<GroupMemberEntity>,
        displayNamesByUserId: Map<String, String> = emptyMap()
    ): Map<String, Any?> = mapOf(
        "id" to group.id,
        "name" to group.name,
        "description" to group.description,
        "currency" to group.currency,
        "colourIndex" to group.colourIndex,
        "createdBy" to group.createdBy,
        "createdAt" to group.createdAt,
        "updatedAt" to group.updatedAt,
        "memberIds" to members.filterNot { it.invited }.map { it.userId },
        "members" to members.map { member ->
            memberToMap(member, displayNamesByUserId[member.userId])
        }
    )

    fun memberToMap(member: GroupMember, invited: Boolean = false): Map<String, Any?> = mapOf(
        "userId" to member.userId,
        "displayName" to member.displayName.takeIf { it.isNotBlank() },
        "role" to member.role.name,
        "joinedAt" to member.joinedAt,
        "invited" to invited
    )

    fun memberToMap(member: GroupMemberEntity, displayName: String? = null): Map<String, Any?> = mapOf(
        "userId" to member.userId,
        "displayName" to displayName?.takeIf { it.isNotBlank() },
        "role" to member.role.name,
        "joinedAt" to member.joinedAt,
        "invited" to member.invited
    )

    fun fromMap(
        data: Map<String, Any?>,
        groupId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Group {
        val resolvedId = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: groupId
        val createdBy = (data["createdBy"] as? String).orEmpty()
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: nowMillis
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: createdAt
        val parsedMembers = parseMembers(data, resolvedId, createdBy, createdAt)

        return Group(
            id = resolvedId,
            name = (data["name"] as? String).orEmpty(),
            description = (data["description"] as? String)?.takeIf { it.isNotBlank() },
            currency = (data["currency"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            members = parsedMembers.map { it.first },
            syncStatus = SyncStatus.SYNCED
        )
    }

    fun entitiesFromMap(
        data: Map<String, Any?>,
        groupId: String,
        isFavourite: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<GroupEntity, List<GroupMemberEntity>> {
        val resolvedId = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: groupId
        val createdBy = (data["createdBy"] as? String).orEmpty()
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: nowMillis
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: createdAt
        val colourIndex = ((data["colourIndex"] as? Number)?.toInt() ?: 0).mod(GROUP_COLOUR_COUNT)
        val parsedMembers = parseMembers(data, resolvedId, createdBy, createdAt)

        val groupEntity = GroupEntity(
            id = resolvedId,
            name = (data["name"] as? String).orEmpty(),
            description = (data["description"] as? String)?.takeIf { it.isNotBlank() },
            currency = (data["currency"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncStatus = SyncStatus.SYNCED,
            colourIndex = colourIndex,
            isFavourite = isFavourite
        )
        return groupEntity to parsedMembers.map { it.second }
    }

    private fun parseMembers(
        data: Map<String, Any?>,
        groupId: String,
        createdBy: String,
        defaultJoinedAt: Long
    ): List<Pair<GroupMember, GroupMemberEntity>> {
        val rawMembers = data["members"] as? List<*>
        if (!rawMembers.isNullOrEmpty()) {
            val mapped = rawMembers.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val userId = (map["userId"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val displayName = (map["displayName"] as? String).orEmpty()
                val role = GroupRole.fromValue(map["role"] as? String)
                val joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: defaultJoinedAt
                val invited = (map["invited"] as? Boolean) ?: userId.startsWith(INVITE_PREFIX)
                val domain = GroupMember(
                    groupId = groupId,
                    userId = userId,
                    displayName = displayName,
                    role = role,
                    joinedAt = joinedAt
                )
                val entity = GroupMemberEntity(
                    groupId = groupId,
                    userId = userId,
                    role = role,
                    joinedAt = joinedAt,
                    invited = invited
                )
                domain to entity
            }.distinctBy { it.first.userId }
            if (mapped.isNotEmpty()) return mapped
        }

        val memberIds = (data["memberIds"] as? List<*>)
            ?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }
            ?.distinct()
            .orEmpty()
        return memberIds.mapIndexed { index, userId ->
            val role = if (userId == createdBy) GroupRole.ADMIN else GroupRole.MEMBER
            val joinedAt = defaultJoinedAt + index
            val invited = userId.startsWith(INVITE_PREFIX)
            val domain = GroupMember(
                groupId = groupId,
                userId = userId,
                displayName = "",
                role = role,
                joinedAt = joinedAt
            )
            val entity = GroupMemberEntity(
                groupId = groupId,
                userId = userId,
                role = role,
                joinedAt = joinedAt,
                invited = invited
            )
            domain to entity
        }
    }

    private const val INVITE_PREFIX = "invite:"
    private const val DEFAULT_CURRENCY = "DKK"
    private const val GROUP_COLOUR_COUNT = 9
}
