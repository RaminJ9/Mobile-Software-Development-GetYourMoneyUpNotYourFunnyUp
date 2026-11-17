package com.example.getyourmoneyupnotyourfunnyup.model

import androidx.compose.runtime.Immutable

/**
 * User-level dietary and receipt item exclusion rules ("Never charge me for" in S7 Profile).
 * When someone scans a receipt, matching items are excluded from the user's share automatically
 * while still allowing manual per-item override.
 */
@Immutable
data class DietaryRules(
    val excludeAlcohol: Boolean = false,
    val excludeMeat: Boolean = false
)

/**
 * Granular notification preferences ("Notify me about" in S7 Profile).
 */
@Immutable
data class NotificationSettings(
    val expenseAlerts: Boolean = true,
    val paymentReminders: Boolean = true,
    val newExpenses: Boolean = expenseAlerts,
    val changedExpenses: Boolean = expenseAlerts,
    val syncUpdates: Boolean = true
)

@Immutable
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val phoneNumber: String = "",
    val photoUrl: String? = null,
    val count: Int = 0,
    val dietaryRules: DietaryRules = DietaryRules(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
)

