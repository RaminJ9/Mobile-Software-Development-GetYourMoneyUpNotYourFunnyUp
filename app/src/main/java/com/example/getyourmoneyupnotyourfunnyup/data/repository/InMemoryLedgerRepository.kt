package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.GroupMember
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationPrefs
import com.example.getyourmoneyupnotyourfunnyup.model.PendingInvite
import com.example.getyourmoneyupnotyourfunnyup.model.Person
import com.example.getyourmoneyupnotyourfunnyup.model.Reminder
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * In-memory [LedgerRepository] seeded with the hi-fi demo data.
 *
 * It behaves like the real offline-first repository should: writes are applied locally at once with
 * a `PENDING_*` sync status, a background job "uploads" them after [syncDelayMs] while online, and
 * failed uploads are retried as soon as connectivity returns. The upload itself is simulated —
 * replace this class with a Room/Firestore-backed implementation of [LedgerRepository] to persist.
 */
class InMemoryLedgerRepository(
    private val scope: CoroutineScope,
    override val isOnline: StateFlow<Boolean>,
    private val clock: () -> Long = System::currentTimeMillis,
    initial: LedgerSnapshot = DemoLedger.create(clock()),
    private val syncDelayMs: Long = 1_500,
    private val paymentDelayMs: Long = 1_300,
) : LedgerRepository {

    private val state = MutableStateFlow(initial)
    override val snapshot: StateFlow<LedgerSnapshot> = state.asStateFlow()

    private val events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    override val syncEvents: SharedFlow<SyncEvent> = events.asSharedFlow()

    init {
        scope.launch {
            var wasOnline = isOnline.value
            isOnline.collect { online ->
                if (online) {
                    state.update { snapshot ->
                        // Reconnected: re-queue failed uploads, like WorkManager's CONNECTED constraint.
                        val expenses = if (!wasOnline) {
                            snapshot.expenses.map {
                                if (it.syncStatus == SyncStatus.FAILED) it.copy(syncStatus = SyncStatus.PENDING_INSERT) else it
                            }
                        } else {
                            snapshot.expenses
                        }
                        snapshot.copy(expenses = expenses, lastSyncedAt = clock())
                    }
                }
                wasOnline = online
            }
        }
        scope.launch {
            combine(state.map { it.pendingKeys() }.distinctUntilChanged(), isOnline) { keys, online -> keys to online }
                .collectLatest { (keys, online) ->
                    if (!online || keys.isEmpty()) return@collectLatest
                    delay(syncDelayMs)
                    upload(keys)
                }
        }
    }

    override fun now(): Long = clock()

    override fun signIn(name: String, email: String) = updateMe { me ->
        me.copy(name = name.ifBlank { me.name }, contact = email.ifBlank { me.contact })
    }

    override fun toggleFavourite(groupId: String) = state.update { s ->
        val favourites = if (groupId in s.favouriteGroupIds) s.favouriteGroupIds - groupId else s.favouriteGroupIds + groupId
        s.copy(favouriteGroupIds = favourites)
    }

    override fun markActivityRead(ids: Collection<String>) = state.update { it.copy(readActivityIds = it.readActivityIds + ids) }

    override fun createGroup(
        name: String,
        description: String?,
        colorIndex: Int,
        memberIds: List<String>,
        invitedContacts: List<String>
    ): String {
        val id = "g-" + UUID.randomUUID().toString()
        val now = clock()
        state.update { s ->
            val members = (listOf(s.currentUserId) + memberIds).distinct().map { userId ->
                GroupMember(
                    groupId = id, userId = userId, displayName = s.person(userId).name,
                    role = if (userId == s.currentUserId) GroupRole.ADMIN else GroupRole.MEMBER, joinedAt = now
                )
            }
            val group = Group(
                id = id, name = name.trim(), description = description?.trim()?.ifBlank { null },
                currency = DemoLedger.CURRENCY, createdBy = s.currentUserId, createdAt = now, updatedAt = now,
                members = members, syncStatus = SyncStatus.PENDING_INSERT, colorIndex = colorIndex
            )
            s.copy(groups = listOf(group) + s.groups, invites = s.invites + invitedContacts.map { PendingInvite(id, it) })
        }
        return id
    }

    override fun deleteGroup(groupId: String) = state.update { s ->
        s.copy(
            groups = s.groups.filterNot { it.id == groupId },
            expenses = s.expenses.filterNot { it.groupId == groupId },
            settlements = s.settlements.filterNot { it.groupId == groupId },
            reminders = s.reminders.filterNot { it.groupId == groupId },
            favouriteGroupIds = s.favouriteGroupIds - groupId
        )
    }

    override fun addMember(groupId: String, personId: String) = updateGroup(groupId) { group ->
        if (group.members.any { it.userId == personId }) return@updateGroup group
        val name = state.value.person(personId).name
        group.copy(
            members = group.members + GroupMember(groupId, personId, name, GroupRole.MEMBER, clock()),
            updatedAt = clock(), syncStatus = SyncStatus.PENDING_UPDATE
        )
    }

    override fun removeMember(groupId: String, personId: String) = updateGroup(groupId) { group ->
        group.copy(
            members = group.members.filterNot { it.userId == personId },
            updatedAt = clock(), syncStatus = SyncStatus.PENDING_UPDATE
        )
    }

    override fun invite(groupId: String, contact: String) =
        state.update { it.copy(invites = it.invites + PendingInvite(groupId, contact.trim())) }

    override fun findPerson(query: String): Person? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val digits = q.filter { it.isDigit() }
        return state.value.people.values.firstOrNull { person ->
            person.id != state.value.currentUserId && (
                person.contact.lowercase() == q ||
                    person.name.lowercase() == q ||
                    (digits.length >= 8 && (person.contact + (person.phone ?: "")).filter { it.isDigit() }.endsWith(digits))
                )
        }
    }

    override fun addExpense(draft: ExpenseDraft): String {
        require(draft.shares.values.sum() == draft.amountCents) { "Shares must add up to the amount" }
        val id = "e-" + UUID.randomUUID().toString()
        val now = clock()
        val expense = Expense(
            id = id, groupId = draft.groupId, title = draft.title.trim().ifBlank { "Expense" },
            amountCents = draft.amountCents, currency = DemoLedger.CURRENCY, payerId = draft.payerId,
            category = if (draft.hasReceipt) "Receipt" else "General",
            receiptLocalUri = if (draft.hasReceipt) "demo://receipt/$id" else null,
            splits = draft.shares.filterValues { it != 0L }.map { (userId, owed) ->
                ExpenseSplit("$id-$userId", id, userId, owed)
            },
            createdAt = now, createdBy = state.value.currentUserId, syncStatus = SyncStatus.PENDING_INSERT
        )
        state.update { it.copy(expenses = listOf(expense) + it.expenses) }
        return id
    }

    override fun removeExpense(expenseId: String) = updateExpense(expenseId) {
        it.copy(isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE)
    }

    override fun retrySync(expenseId: String) = updateExpense(expenseId) {
        if (it.syncStatus == SyncStatus.FAILED) it.copy(syncStatus = SyncStatus.PENDING_INSERT) else it
    }

    override fun payDebt(groupId: String, creditorId: String, amountCents: Long): String {
        val id = addSettlement(groupId, state.value.currentUserId, creditorId, amountCents, SettlementStatus.PENDING)
        scope.launch {
            delay(paymentDelayMs)
            state.update { s ->
                s.copy(settlements = s.settlements.map { if (it.id == id) it.copy(status = SettlementStatus.COMPLETED) else it })
            }
        }
        return id
    }

    override fun markAsPaid(groupId: String, debtorId: String, amountCents: Long): String =
        addSettlement(groupId, debtorId, state.value.currentUserId, amountCents, SettlementStatus.COMPLETED)

    override fun sendReminder(groupId: String, debtorId: String) = state.update { s ->
        s.copy(reminders = s.reminders + Reminder(UUID.randomUUID().toString(), groupId, s.currentUserId, debtorId, clock()))
    }

    override fun updateProfile(name: String, email: String, phone: String?) = updateMe {
        it.copy(name = name.trim().ifBlank { it.name }, contact = email.trim(), phone = phone?.trim()?.ifBlank { null })
    }

    override fun setDietRule(rule: DietRule, enabled: Boolean) = updateMe {
        it.copy(dietRules = if (enabled) it.dietRules + rule else it.dietRules - rule)
    }

    override fun setNotificationPrefs(prefs: NotificationPrefs) = state.update { it.copy(notificationPrefs = prefs) }

    private fun addSettlement(groupId: String, debtorId: String, creditorId: String, amount: Long, status: SettlementStatus): String {
        val id = "s-" + UUID.randomUUID().toString()
        val settlement = Settlement(
            id = id, groupId = groupId, debtorId = debtorId, creditorId = creditorId, amountCents = amount,
            currency = DemoLedger.CURRENCY, paymentMethod = "In app", status = status, createdAt = clock(),
            syncStatus = SyncStatus.PENDING_INSERT
        )
        state.update { it.copy(settlements = it.settlements + settlement) }
        return id
    }

    /** Simulated upload: marks every queued change as synced and tells the UI. */
    private fun upload(keys: Set<String>) {
        var firstTitle: String? = null
        state.update { s ->
            s.copy(
                expenses = s.expenses.map { e ->
                    if ("e:${e.id}" in keys && e.syncStatus.isPending()) {
                        if (firstTitle == null && !e.isDeleted) firstTitle = e.title
                        e.copy(syncStatus = SyncStatus.SYNCED)
                    } else e
                },
                settlements = s.settlements.map { if ("s:${it.id}" in keys && it.syncStatus.isPending()) it.copy(syncStatus = SyncStatus.SYNCED) else it },
                groups = s.groups.map { if ("g:${it.id}" in keys && it.syncStatus.isPending()) it.copy(syncStatus = SyncStatus.SYNCED) else it },
                lastSyncedAt = clock()
            )
        }
        events.tryEmit(SyncEvent.Synced(keys.size, firstTitle))
    }

    private fun updateMe(transform: (Person) -> Person) = state.update { s ->
        s.copy(people = s.people + (s.currentUserId to transform(s.me)))
    }

    private fun updateGroup(groupId: String, transform: (Group) -> Group) = state.update { s ->
        s.copy(groups = s.groups.map { if (it.id == groupId) transform(it) else it })
    }

    private fun updateExpense(expenseId: String, transform: (Expense) -> Expense) = state.update { s ->
        s.copy(expenses = s.expenses.map { if (it.id == expenseId) transform(it) else it })
    }

    private fun SyncStatus.isPending() =
        this == SyncStatus.PENDING_INSERT || this == SyncStatus.PENDING_UPDATE || this == SyncStatus.PENDING_DELETE

    private fun LedgerSnapshot.pendingKeys(): Set<String> =
        expenses.filter { it.syncStatus.isPending() }.map { "e:${it.id}" }.toSet() +
            settlements.filter { it.syncStatus.isPending() }.map { "s:${it.id}" } +
            groups.filter { it.syncStatus.isPending() }.map { "g:${it.id}" }
}
