package com.example.getyourmoneyupnotyourfunnyup.model

data class NotificationSettings(
    val expenseAlerts: Boolean = true,
    val paymentReminders: Boolean = true
)

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val count: Int = 0,
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
)
