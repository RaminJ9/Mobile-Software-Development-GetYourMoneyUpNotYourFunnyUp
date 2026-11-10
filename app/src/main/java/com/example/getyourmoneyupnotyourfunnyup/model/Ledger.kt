package com.example.getyourmoneyupnotyourfunnyup.model

/** Items a person never wants to be charged for when a receipt is split. */
enum class DietRule {
    NO_ALCOHOL,
    NO_MEAT
}

/**
 * Someone the user splits money with (a group member or a suggested contact).
 *
 * @param contact email or phone number used to find the person.
 * @param paletteIndex index into the shared avatar/group palette; negative means "the signed-in user".
 */
data class Person(
    val id: String,
    val name: String,
    val contact: String,
    val phone: String? = null,
    val paletteIndex: Int = 0,
    val dietRules: Set<DietRule> = emptySet()
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

/** A "please pay" nudge from a creditor to a debtor in a group. */
data class Reminder(
    val id: String,
    val groupId: String,
    val fromId: String,
    val toId: String,
    val createdAt: Long
)

/** An invite sent to a contact that does not use the app yet. */
data class PendingInvite(
    val groupId: String,
    val contact: String
)

/** The four notification switches in Profile → "Notify me about". */
data class NotificationPrefs(
    val newExpenses: Boolean = true,
    val changedExpenses: Boolean = true,
    val paymentReminders: Boolean = true,
    val syncUpdates: Boolean = true
)

/**
 * Immutable snapshot of everything the UI renders. Repositories emit a new snapshot on every change;
 * balances are never stored here — they are derived from [expenses] and [settlements]
 * (see [DebtCalculator] and `LedgerQueries.kt`).
 */
data class LedgerSnapshot(
    val currentUserId: String,
    val people: Map<String, Person>,
    val groups: List<Group>,
    val expenses: List<Expense>,
    val settlements: List<Settlement>,
    val reminders: List<Reminder> = emptyList(),
    val invites: List<PendingInvite> = emptyList(),
    val favouriteGroupIds: Set<String> = emptySet(),
    val readActivityIds: Set<String> = emptySet(),
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
    /** Last time the device successfully talked to the backend; shown in the offline banner. */
    val lastSyncedAt: Long? = null
) {
    val me: Person get() = people.getValue(currentUserId)

    fun person(id: String): Person =
        people[id] ?: Person(id = id, name = "Unknown", contact = "", paletteIndex = id.hashCode().mod(9))

    fun group(id: String): Group? = groups.firstOrNull { it.id == id }
}
