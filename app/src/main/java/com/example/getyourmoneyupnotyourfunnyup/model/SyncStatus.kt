package com.example.getyourmoneyupnotyourfunnyup.model

/**
 * Represents the synchronization state of a local record relative to Cloud Firestore.
 * Satisfies the Reliability QA requirement for visual sync state feedback.
 */
enum class SyncStatus {
    SYNCED,
    PENDING_INSERT,
    PENDING_UPDATE,
    PENDING_DELETE,
    FAILED;

    companion object {
        fun fromValue(value: String?): SyncStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYNCED
    }
}
