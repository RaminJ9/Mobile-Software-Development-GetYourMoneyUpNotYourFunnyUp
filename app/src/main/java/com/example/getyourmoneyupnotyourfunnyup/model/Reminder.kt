package com.example.getyourmoneyupnotyourfunnyup.model

/** Immutable in-app reminder, synchronized under a group. No SMS/email/push is sent. */
data class Reminder(
    val id: String, val groupId: String, val debtorId: String, val creditorId: String,
    val amountCents: Long, val currency: String, val obligationId: String, val createdAt: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING_INSERT
)
