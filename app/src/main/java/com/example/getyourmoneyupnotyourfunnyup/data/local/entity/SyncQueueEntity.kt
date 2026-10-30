package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

enum class SyncEntityType {
    USER,
    GROUP,
    EXPENSE,
    SETTLEMENT;

    companion object {
        fun fromValue(value: String?): SyncEntityType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXPENSE
    }
}

enum class SyncAction {
    CREATE,
    UPDATE,
    DELETE;

    companion object {
        fun fromValue(value: String?): SyncAction =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CREATE
    }
}

data class SyncQueueEntity(
    val queueId: Long = 0L,
    val entityType: SyncEntityType,
    val entityId: String,
    val action: SyncAction,
    val payloadJson: String,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null
)
