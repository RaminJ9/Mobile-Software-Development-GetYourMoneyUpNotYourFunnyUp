package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.DietaryRules
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User

data class UserEntity(
    val id: String,
    val email: String,
    val displayName: String,
    val phoneNumber: String = "",
    val photoUrl: String? = null,
    val count: Int = 0,
    val notificationsEnabled: Boolean = true,
    val excludeAlcohol: Boolean = false,
    val excludeMeat: Boolean = false,
    val notifyNewExpenses: Boolean = notificationsEnabled,
    val notifyChangedExpenses: Boolean = notificationsEnabled,
    val notifyPaymentReminders: Boolean = notificationsEnabled,
    val notifySyncUpdates: Boolean = notificationsEnabled,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): User = User(
        id = id,
        email = email,
        displayName = displayName,
        phoneNumber = phoneNumber,
        photoUrl = photoUrl,
        count = count,
        dietaryRules = DietaryRules(
            excludeAlcohol = excludeAlcohol,
            excludeMeat = excludeMeat
        ),
        notificationSettings = NotificationSettings(
            expenseAlerts = notifyNewExpenses || notifyChangedExpenses,
            paymentReminders = notifyPaymentReminders,
            newExpenses = notifyNewExpenses,
            changedExpenses = notifyChangedExpenses,
            syncUpdates = notifySyncUpdates
        ),
        syncStatus = syncStatus,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(user: User): UserEntity {
            val notif = user.notificationSettings
            val anyEnabled = notif.newExpenses ||
                notif.changedExpenses ||
                notif.paymentReminders ||
                notif.syncUpdates
            return UserEntity(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                phoneNumber = user.phoneNumber,
                photoUrl = user.photoUrl,
                count = user.count,
                notificationsEnabled = anyEnabled,
                excludeAlcohol = user.dietaryRules.excludeAlcohol,
                excludeMeat = user.dietaryRules.excludeMeat,
                notifyNewExpenses = notif.newExpenses,
                notifyChangedExpenses = notif.changedExpenses,
                notifyPaymentReminders = notif.paymentReminders,
                notifySyncUpdates = notif.syncUpdates,
                syncStatus = user.syncStatus,
                updatedAt = user.updatedAt
            )
        }
    }
}

