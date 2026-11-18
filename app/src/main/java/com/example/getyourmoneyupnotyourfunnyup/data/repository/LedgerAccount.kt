package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationPrefs
import com.example.getyourmoneyupnotyourfunnyup.model.User

/**
 * Copies the signed-in account into the ledger's "me". The account and its profile are saved by the
 * auth and profile repositories (SQLite and Firestore); the ledger only mirrors them, so the scan
 * split uses the saved diet rules and the "Synced" message follows the saved notification settings.
 */
fun LedgerRepository.useAccount(user: User) {
    updateProfile(user.displayName, user.email, user.phoneNumber ?: user.phone)
    setDietRule(DietRule.NO_ALCOHOL, user.excludeAlcohol)
    setDietRule(DietRule.NO_MEAT, user.excludeMeat)
    val settings = user.notificationSettings
    setNotificationPrefs(
        NotificationPrefs(
            newExpenses = settings.expenseAlerts,
            changedExpenses = settings.changedExpenseAlerts,
            paymentReminders = settings.paymentReminders,
            syncUpdates = settings.syncUpdates,
        )
    )
}
