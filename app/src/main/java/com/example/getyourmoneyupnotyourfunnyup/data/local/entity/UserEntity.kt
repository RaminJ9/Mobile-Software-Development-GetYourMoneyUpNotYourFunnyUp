package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User

data class UserEntity(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val count: Int = 0,
    val notificationsEnabled: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): User = User(
        id = id,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl,
        count = count,
        notificationSettings = NotificationSettings(
            expenseAlerts = notificationsEnabled,
            paymentReminders = notificationsEnabled
        ),
        syncStatus = syncStatus,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(user: User): UserEntity = UserEntity(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            count = user.count,
            notificationsEnabled = user.notificationSettings.expenseAlerts || user.notificationSettings.paymentReminders,
            syncStatus = user.syncStatus,
            updatedAt = user.updatedAt
        )
    }
}
