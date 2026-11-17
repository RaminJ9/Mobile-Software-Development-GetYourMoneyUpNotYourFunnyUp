package com.example.getyourmoneyupnotyourfunnyup.data.remote

import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import org.junit.Assert.*
import org.junit.Test

class UserProfileMappingTest {
    private val user = User(
        id = "u", email = "ramin@example.com", displayName = "Ramin",
        photoUrl = "content://profile/photo", count = 7, updatedAt = 123L,
        phoneNumber = "+4512345678", excludeAlcohol = true, excludeMeat = true
    )

    @Test fun everyNotificationCombinationSurvivesLocalAndRemoteRoundTrips() {
        for (bits in 0..15) {
            val settings = NotificationSettings(
                expenseAlerts = bits and 1 != 0,
                paymentReminders = bits and 2 != 0,
                changedExpenseAlerts = bits and 4 != 0,
                syncUpdates = bits and 8 != 0
            )
            val original = user.copy(notificationSettings = settings)
            val entity = UserEntity.fromDomain(original)
            assertEquals("Local combination $bits", original, entity.toDomain())
            assertEquals(bits != 0, entity.notificationsEnabled)
            assertEquals("Remote combination $bits", original,
                UserDocumentMapper.fromMap(UserDocumentMapper.toMap(original), "u", "fallback", "Fallback"))
        }
    }

    @Test fun localMappingKeepsPendingStatusAndDietaryRules() {
        val pending = user.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        assertEquals(pending, UserEntity.fromDomain(pending).toDomain())
        val noRules = pending.copy(excludeAlcohol = false, excludeMeat = false, phoneNumber = null)
        assertEquals(noRules, UserEntity.fromDomain(noRules).toDomain())
    }

    @Test fun legacyRemotePreferencesKeepIndependentOriginalChoices() {
        for (expenses in listOf(false, true)) for (reminders in listOf(false, true)) {
            val oldDocument = mapOf<String, Any?>(
                "displayName" to "Existing user", "count" to 9L,
                "notificationSettings" to mapOf("expenseAlerts" to expenses, "paymentReminders" to reminders)
            )
            val loaded = UserDocumentMapper.fromMap(oldDocument, "u", "old@example.com", "Fallback", 42L)
            assertEquals(NotificationSettings(expenses, reminders, expenses, expenses || reminders), loaded.notificationSettings)
            assertNull(loaded.phoneNumber)
            assertFalse(loaded.excludeAlcohol)
            assertFalse(loaded.excludeMeat)
            assertEquals(9, loaded.count)
            assertEquals("old@example.com", loaded.email)
            assertEquals(42L, loaded.updatedAt)
        }
    }

    @Test fun missingOrMalformedOptionalFieldsUseSafeDefaults() {
        val loaded = UserDocumentMapper.fromMap(mapOf("notificationSettings" to "invalid", "phoneNumber" to 123), "u", "email", "Name", 1L)
        assertEquals(NotificationSettings(), loaded.notificationSettings)
        assertEquals("u", loaded.id)
        assertEquals("Name", loaded.displayName)
        assertNull(loaded.phoneNumber)
        assertFalse(loaded.excludeAlcohol)
        assertFalse(loaded.excludeMeat)
    }

    @Test fun oldEntityCallersRetainTheirGlobalNotificationChoice() {
        val disabled = UserEntity("u", "email", "Name", notificationsEnabled = false).toDomain()
        assertEquals(NotificationSettings(false, false, false, false), disabled.notificationSettings)
        val enabled = UserEntity("u", "email", "Name", notificationsEnabled = true).toDomain()
        assertEquals(NotificationSettings(true, true, true, true), enabled.notificationSettings)
    }
    @Test fun incomingTeamDocumentKeepsDietAndIndependentNotificationChoices() {
        val document = mapOf<String, Any?>(
            "phoneNumber" to "",
            "dietaryRules" to mapOf("excludeAlcohol" to true, "excludeMeat" to true),
            "notificationSettings" to mapOf("expenseAlerts" to true, "newExpenses" to false, "changedExpenses" to true, "paymentReminders" to false, "syncUpdates" to false)
        )
        val loaded = UserDocumentMapper.fromMap(document, "u", "email", "Name")
        assertTrue(loaded.excludeAlcohol)
        assertTrue(loaded.excludeMeat)
        assertNull(loaded.phoneNumber)
        assertEquals(NotificationSettings(false, false, true, false), loaded.notificationSettings)
        val written = UserDocumentMapper.toMap(loaded)
        assertEquals(document["dietaryRules"], written["dietaryRules"])
        assertEquals(false, (written["notificationSettings"] as Map<*, *>)["newExpenses"])
    }

}
