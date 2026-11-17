package com.example.getyourmoneyupnotyourfunnyup.data.remote

import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User

/** Shared by authentication reads/writes; pure mapping keeps legacy-profile handling testable. */
internal object UserDocumentMapper {
    fun toMap(user: User): Map<String, Any?> = with(user) {
        mapOf(
            "id" to id,
            "email" to email,
            "displayName" to displayName,
            "photoUrl" to photoUrl,
            "count" to count,
            "phoneNumber" to phoneNumber,
            "excludeAlcohol" to excludeAlcohol,
            "excludeMeat" to excludeMeat,
            "dietaryRules" to mapOf("excludeAlcohol" to excludeAlcohol, "excludeMeat" to excludeMeat),
            "notificationSettings" to mapOf(
                "expenseAlerts" to notificationSettings.expenseAlerts,
                "newExpenses" to notificationSettings.expenseAlerts,
                "changedExpenses" to notificationSettings.changedExpenseAlerts,
                "paymentReminders" to notificationSettings.paymentReminders,
                "changedExpenseAlerts" to notificationSettings.changedExpenseAlerts,
                "syncUpdates" to notificationSettings.syncUpdates
            ),
            "updatedAt" to updatedAt
        )
    }

    /** Patch only profile fields, so an upload cannot replace login email or financial data. */
    fun profileUpdateMap(user: User): Map<String, Any?> = toMap(user).filterKeys {
        it in setOf("id", "displayName", "phoneNumber", "excludeAlcohol", "excludeMeat", "dietaryRules", "notificationSettings", "updatedAt")
    }

    fun fromMap(
        data: Map<String, Any?>,
        uid: String,
        fallbackEmail: String,
        fallbackName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): User {
        val settings = data["notificationSettings"] as? Map<*, *>
        val legacyExpenses = settings?.get("expenseAlerts") as? Boolean ?: true
        val newExpenses = settings?.get("newExpenses") as? Boolean ?: legacyExpenses
        val dietary = data["dietaryRules"] as? Map<*, *>
        val reminders = settings?.get("paymentReminders") as? Boolean ?: true
        return User(
            id = data["id"] as? String ?: uid,
            email = data["email"] as? String ?: fallbackEmail,
            displayName = data["displayName"] as? String ?: fallbackName,
            photoUrl = data["photoUrl"] as? String,
            count = (data["count"] as? Number)?.toInt() ?: 0,
            notificationSettings = NotificationSettings(
                expenseAlerts = newExpenses,
                paymentReminders = reminders,
                // Older documents had just the two original preferences. Preserve their choices.
                changedExpenseAlerts = settings?.get("changedExpenseAlerts") as? Boolean ?: (settings?.get("changedExpenses") as? Boolean ?: legacyExpenses),
                syncUpdates = settings?.get("syncUpdates") as? Boolean ?: (newExpenses || reminders)
            ),
            syncStatus = SyncStatus.SYNCED,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: nowMillis,
            phoneNumber = (data["phoneNumber"] as? String)?.takeIf { it.isNotBlank() },
            excludeAlcohol = data["excludeAlcohol"] as? Boolean ?: (dietary?.get("excludeAlcohol") as? Boolean ?: false),
            excludeMeat = data["excludeMeat"] as? Boolean ?: (dietary?.get("excludeMeat") as? Boolean ?: false)
        )
    }
}
