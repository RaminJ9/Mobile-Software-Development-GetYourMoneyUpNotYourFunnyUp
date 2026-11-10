package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationPrefs
import com.example.getyourmoneyupnotyourfunnyup.model.Person
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything needed to save a new expense. [shares] must add up to [amountCents]. */
data class ExpenseDraft(
    val groupId: String,
    val title: String,
    val payerId: String,
    val amountCents: Long,
    val shares: Map<String, Long>,
    val hasReceipt: Boolean
)

/** Emitted when queued changes reach the backend, so the UI can tell the user. */
sealed interface SyncEvent {
    data class Synced(val count: Int, val firstTitle: String?) : SyncEvent
}

/**
 * Local-first source of truth for groups, expenses and payments.
 *
 * Every write lands in the local snapshot immediately with a pending sync status, so the UI never
 * waits on the network. Implementations upload pending changes in the background while online and
 * retry failed uploads automatically when the connection comes back.
 */
interface LedgerRepository {
    val snapshot: StateFlow<LedgerSnapshot>
    val isOnline: StateFlow<Boolean>
    val syncEvents: SharedFlow<SyncEvent>

    fun now(): Long

    /** Puts the signed-in account's name and email on the "me" person. */
    fun signIn(name: String, email: String)

    fun toggleFavourite(groupId: String)
    fun markActivityRead(ids: Collection<String>)

    /** Creates a group with the user as admin and returns its id. */
    fun createGroup(
        name: String,
        description: String?,
        colorIndex: Int,
        memberIds: List<String>,
        invitedContacts: List<String>
    ): String

    fun deleteGroup(groupId: String)
    fun addMember(groupId: String, personId: String)
    fun removeMember(groupId: String, personId: String)
    fun invite(groupId: String, contact: String)

    /** Finds an app user by email, phone number or exact name. Null when nobody matches. */
    fun findPerson(query: String): Person?

    /** Saves an expense locally and queues it for upload. Returns the new expense id. */
    fun addExpense(draft: ExpenseDraft): String
    fun removeExpense(expenseId: String)

    /** Re-queues an expense whose upload failed ("Try now"). */
    fun retrySync(expenseId: String)

    /** The user pays [creditorId]. The payment is pending briefly, then completed. Returns its id. */
    fun payDebt(groupId: String, creditorId: String, amountCents: Long): String

    /** Records that [debtorId] paid the user outside the app ("Mark as paid"). */
    fun markAsPaid(groupId: String, debtorId: String, amountCents: Long): String
    fun sendReminder(groupId: String, debtorId: String)

    fun updateProfile(name: String, email: String, phone: String?)
    fun setDietRule(rule: DietRule, enabled: Boolean)
    fun setNotificationPrefs(prefs: NotificationPrefs)
}
