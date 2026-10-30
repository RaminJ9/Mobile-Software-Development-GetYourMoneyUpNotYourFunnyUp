package com.example.getyourmoneyupnotyourfunnyup.model

enum class GroupRole {
    ADMIN,
    MEMBER;

    companion object {
        fun fromValue(value: String?): GroupRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEMBER
    }
}

data class GroupMember(
    val groupId: String,
    val userId: String,
    val displayName: String = "",
    val role: GroupRole = GroupRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis()
)

data class Group(
    val id: String,
    val name: String,
    val description: String? = null,
    val currency: String = "EUR",
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val members: List<GroupMember> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
