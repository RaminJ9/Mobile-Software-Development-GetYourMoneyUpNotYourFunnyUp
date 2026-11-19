package com.example.getyourmoneyupnotyourfunnyup.data.repository

import androidx.core.database.sqlite.transaction
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** One consistent local ledger snapshot; monetary calculation stays in BalanceCalculator. */
data class BalanceLedger(
    val accountId: String,
    val group: Group,
    val namesByUserId: Map<String, String>,
    val expenses: List<Expense>,
    val settlements: List<Settlement>
)

fun interface BalanceRepository {
    fun observeGroup(accountId: String, groupId: String): Flow<BalanceLedger>
}

/** Read-only adapter. The caller supplies the current session UID, never a sample account. */
class LocalBalanceRepository(
    private val database: AppDatabase,
    private val currentAccountId: () -> String?
) : BalanceRepository {
    override fun observeGroup(accountId: String, groupId: String): Flow<BalanceLedger> {
        require(accountId.isNotBlank() && groupId.isNotBlank()) { "Account and group IDs are required." }
        return database.observeTables(
            DatabaseContract.UsersTable.TABLE_NAME,
            DatabaseContract.GroupsTable.TABLE_NAME,
            DatabaseContract.GroupMembersTable.TABLE_NAME,
            DatabaseContract.ExpensesTable.TABLE_NAME,
            DatabaseContract.ExpenseSplitsTable.TABLE_NAME,
            DatabaseContract.SettlementsTable.TABLE_NAME
        ).map {
            val snapshot = withContext(Dispatchers.IO) { readSnapshot(accountId, groupId) }
            checkAccount(accountId)
            snapshot
        }
    }

    private fun checkAccount(accountId: String) {
        if (currentAccountId() != accountId) throw SecurityException("Sign in again to view this group's balances.")
    }

    private fun readSnapshot(accountId: String, groupId: String): BalanceLedger {
        checkAccount(accountId)
        // Existing DAOs share this thread's transaction, so the ledger cannot be torn across edits.
        return database.readableDatabase.transaction(exclusive = false) {
            checkAccount(accountId)
            val members = database.groupDao.getMembersForGroup(groupId)
            if (members.none { it.userId == accountId }) {
                throw SecurityException("You don't have access to this group's balances.")
            }
            val group = database.groupDao.getGroupById(groupId)
                ?: throw IllegalStateException("This group isn't available on your phone.")
            val splitsByExpense = database.expenseDao.getSplitsForGroup(groupId).groupBy { it.expenseId }
            val expenses = database.expenseDao.getActiveExpensesForGroup(groupId).map { expense ->
                expense.toDomain(splitsByExpense[expense.id].orEmpty().map { it.toDomain() })
            }
            val settlements = database.settlementDao.getSettlementsForGroup(groupId).map { it.toDomain() }
            val referencedIds = buildSet {
                addAll(members.map { it.userId })
                expenses.forEach { expense -> add(expense.payerId); addAll(expense.splits.map { it.userId }) }
                settlements.forEach { add(it.debtorId); add(it.creditorId) }
            }
            val names = referencedIds.associateWith { userId ->
                database.userDao.getUserById(userId)?.displayName.orEmpty()
            }
            checkAccount(accountId)
            BalanceLedger(
                accountId = accountId,
                group = group.toDomain(members.map { it.toDomain(names[it.userId].orEmpty()) }),
                namesByUserId = names,
                expenses = expenses,
                settlements = settlements
            )
        }
    }
}
