package com.example.getyourmoneyupnotyourfunnyup.data.local.entity

import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User

data class UserEntity(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val phone: String? = null,
    val count: Int = 0,
    // Legacy aggregate retained for existing callers; individual preferences are authoritative.
    val notificationsEnabled: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis(),
    val phoneNumber: String? = null,
    val excludeAlcohol: Boolean = false,
    val excludeMeat: Boolean = false,
    val notificationSettings: NotificationSettings = NotificationSettings(
        expenseAlerts = notificationsEnabled,
        paymentReminders = notificationsEnabled
    )
) {
    fun toDomain(): User = User(
        id = id,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl,
        phone = phone,
        count = count,
        notificationSettings = notificationSettings,
        syncStatus = syncStatus,
        updatedAt = updatedAt,
        phoneNumber = phoneNumber,
        excludeAlcohol = excludeAlcohol,
        excludeMeat = excludeMeat
    )

    companion object {
        fun fromDomain(user: User): UserEntity = UserEntity(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            phone = user.phone,
            count = user.count,
            notificationsEnabled = user.notificationSettings.let {
                it.expenseAlerts || it.changedExpenseAlerts || it.paymentReminders || it.syncUpdates
            },
            syncStatus = user.syncStatus,
            updatedAt = user.updatedAt,
            phoneNumber = user.phoneNumber,
            excludeAlcohol = user.excludeAlcohol,
            excludeMeat = user.excludeMeat,
            notificationSettings = user.notificationSettings
        )
    }
}
