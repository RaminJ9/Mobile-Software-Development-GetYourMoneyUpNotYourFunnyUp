package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptUploads
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ScanPerson
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A group an expense can be added to, with the people it can be split between. */
data class ScanGroup(
    val id: String,
    val name: String,
    val colourIndex: Int,
    val currency: String,
    val lastActivityAt: Long,
    val members: List<ScanPerson>
)

/** What the scan flow reads and saves. */
interface ScanSource {
    /** The signed-in account's groups, with each member's name, colour and diet rules. */
    val groups: Flow<List<ScanGroup>>

    /** Sync state of a saved expense, so the confirmation can show "syncing…" → "Synced". */
    fun syncStatus(expenseId: String, groupId: String): Flow<SyncStatus?>

    /** Saves the expense on the phone and queues it for upload. Throws with a readable message if it can't. */
    suspend fun save(expense: Expense): Expense
}

/**
 * [ScanSource] over the phone's database, so scanning and saving work offline. Groups and members
 * come from [groupRepository] (the groups owner), diet rules from the cached user profiles, and saving goes
 * through [expenses], which stores the expense with its sync-queue entry in one transaction. A receipt
 * photo is then handed to [uploads], which puts it in cloud storage for the other members.
 */
class LocalScanSource(
    private val database: AppDatabase,
    private val groupRepository: GroupRepository,
    private val expenses: ExpenseRepository,
    private val uploads: ReceiptUploads? = null,
) : ScanSource {

    override val groups: Flow<List<ScanGroup>> = groupRepository.observeGroups().map { dashboardGroups ->
        withContext(Dispatchers.IO) {
            dashboardGroups.map { dashboardGroup ->
                val group = dashboardGroup.group
                ScanGroup(
                    id = group.id,
                    name = group.name,
                    colourIndex = dashboardGroup.colourIndex,
                    currency = group.currency,
                    lastActivityAt = dashboardGroup.lastActivityAt,
                    members = group.members.map { member ->
                        val user = database.userDao.getUserById(member.userId)
                        ScanPerson(
                            id = member.userId,
                            name = user?.displayName?.ifBlank { null } ?: member.displayName.ifBlank { "Member" },
                            colourIndex = GroupRepository.memberColourIndex(member.userId),
                            excludeAlcohol = user?.excludeAlcohol ?: false,
                            excludeMeat = user?.excludeMeat ?: false,
                        )
                    },
                )
            }
        }
    }

    override fun syncStatus(expenseId: String, groupId: String): Flow<SyncStatus?> =
        expenses.observeExpensesForGroup(groupId)
            .map { list -> list.firstOrNull { it.id == expenseId }?.syncStatus }
            .distinctUntilChanged()

    override suspend fun save(expense: Expense): Expense {
        withContext(Dispatchers.IO) {
            // Check against the group as it is now: someone may have been removed while the receipt was open.
            val group = database.groupDao.getGroupById(expense.groupId)
                ?: throw IllegalStateException("This group isn't on your phone any more.")
            val memberIds = database.groupDao.getMembersForGroup(group.id).map { it.userId }.toSet()
            check(expense.createdBy in memberIds) { "You're no longer a member of ${group.name}." }
            check(expense.payerId in memberIds && expense.splits.all { it.userId in memberIds }) {
                "Someone on this receipt is no longer in ${group.name}. Check who had what."
            }
            // The rules the balance calculation relies on (BalanceCalculator).
            require(expense.currency == group.currency) { "This expense must be in ${group.currency}." }
            require(expense.amountCents > 0) { "Add at least one item with a price." }
            require(expense.splits.all { it.amountOwedCents >= 0 }) { "A share can't be negative." }
            require(expense.splits.sumOf { it.amountOwedCents } == expense.amountCents) { "The shares don't add up to the total." }
            require(expense.splits.map { it.id }.toSet().size == expense.splits.size) { "Each person can only have one share." }
        }
        val saved = expenses.saveExpense(expense)
        // The expense is saved either way; the photo follows when there is a connection.
        if (saved.receiptLocalUri != null) runCatching { uploads?.schedule(saved.id) }
        return saved
    }
}
