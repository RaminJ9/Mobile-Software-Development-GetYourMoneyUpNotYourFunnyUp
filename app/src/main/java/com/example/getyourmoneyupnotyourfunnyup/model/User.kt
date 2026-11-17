package com.example.getyourmoneyupnotyourfunnyup.model

/** The four S7 preferences. expenseAlerts retains its existing name for new expenses. */
data class NotificationSettings(
    val expenseAlerts: Boolean = true,
    val paymentReminders: Boolean = true,
    val changedExpenseAlerts: Boolean = expenseAlerts,
    val syncUpdates: Boolean = expenseAlerts || paymentReminders
)

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val count: Int = 0,
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis(),
    val phoneNumber: String? = null,
    val excludeAlcohol: Boolean = false,
    val excludeMeat: Boolean = false
)
