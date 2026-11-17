package com.example.getyourmoneyupnotyourfunnyup.model

/** Only the S7 fields editable here; email/password changes belong to the auth flow. */
data class ProfileChanges(
    val displayName: String,
    val phoneNumber: String?,
    val excludeAlcohol: Boolean,
    val excludeMeat: Boolean,
    val notificationSettings: NotificationSettings
) {
    companion object {
        fun fromUser(user: User) = ProfileChanges(
            user.displayName, user.phoneNumber, user.excludeAlcohol, user.excludeMeat, user.notificationSettings
        )
    }
}
