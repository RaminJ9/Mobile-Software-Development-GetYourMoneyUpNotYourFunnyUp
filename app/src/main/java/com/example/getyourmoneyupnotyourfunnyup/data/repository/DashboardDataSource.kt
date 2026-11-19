package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpenseSplitsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpensesTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.GroupMembersTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.GroupsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SettlementsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.UsersTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SettlementEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncQueueEntity
import com.example.getyourmoneyupnotyourfunnyup.data.remote.ExpenseDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SettlementDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SyncPayloadJson
import com.example.getyourmoneyupnotyourfunnyup.data.sync.SyncQueueProcessor
import com.example.getyourmoneyupnotyourfunnyup.model.DebtTransfer
import com.example.getyourmoneyupnotyourfunnyup.model.MemberBalance
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.ActivityData
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.ActivityEvent
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.ActivityType
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.AmountDirection
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.FinancialSummary
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.HomeData
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.OutstandingPayment
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.PaidOutPayment
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SettlementActions
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncPresentation
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncRetryActions
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Reactive local SQLite data source that maps [AppDatabase] tables into [HomeData] and [ActivityData]
 * for the authenticated navigation shell.
 */
class DashboardDataSource(
    private val database: AppDatabase,
    private val syncProcessor: SyncQueueProcessor? = null,
    private val currentAccountId: () -> String? = { null }
) {
    private val watchedTables = arrayOf(
        UsersTable.TABLE_NAME,
        GroupsTable.TABLE_NAME,
        GroupMembersTable.TABLE_NAME,
        ExpensesTable.TABLE_NAME,
        ExpenseSplitsTable.TABLE_NAME,
        SettlementsTable.TABLE_NAME,
        SyncQueueTable.TABLE_NAME
    )

    fun observeHomeData(initialUser: User): Flow<HomeData> =
        database.observeTables(*watchedTables).map {
            withContext(Dispatchers.IO) {
                loadHomeData(initialUser)
            }
        }

    fun observeActivityData(initialUser: User): Flow<ActivityData> =
        database.observeTables(*watchedTables).map {
            withContext(Dispatchers.IO) {
                loadActivityData(initialUser)
            }
        }

    val settlementActions: SettlementActions = SettlementActions { payment ->
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val settlementId = "stl-${UUID.randomUUID()}"
            val entity = SettlementEntity(
                id = settlementId,
                groupId = payment.groupId,
                debtorId = payment.transfer.debtorId,
                creditorId = payment.transfer.creditorId,
                amountCents = payment.transfer.amountCents,
                currency = payment.transfer.currency,
                paymentMethod = "MobilePay",
                status = SettlementStatus.COMPLETED,
                createdAt = now,
                syncStatus = SyncStatus.PENDING_INSERT
            )
            database.settlementDao.upsertSettlement(entity)
            database.syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = SyncEntityType.SETTLEMENT,
                    entityId = settlementId,
                    action = SyncAction.CREATE,
                    payloadJson = SyncPayloadJson.encode(
                        SettlementDocumentMapper.toMap(entity, recordedBy = payment.transfer.debtorId)
                    )
                )
            )
            val accountId = currentAccountId() ?: payment.transfer.debtorId
            syncProcessor?.processNext(accountId)
            database.settlementDao.getSettlementById(settlementId)?.syncStatus ?: SyncStatus.PENDING_INSERT
        }
    }

    val syncRetryActions: SyncRetryActions = SyncRetryActions { event ->
        withContext(Dispatchers.IO) {
            val expense = database.expenseDao.getExpenseById(event.id)
            if (expense != null) {
                database.expenseDao.updateSyncStatus(event.id, SyncStatus.PENDING_UPDATE)
                val alreadyQueued = database.syncQueueDao.getAllPending().any {
                    it.entityType == SyncEntityType.EXPENSE && it.entityId == event.id
                }
                if (!alreadyQueued) {
                    val splits = database.expenseDao.getSplitsForExpense(event.id)
                    database.syncQueueDao.enqueue(
                        SyncQueueEntity(
                            entityType = SyncEntityType.EXPENSE,
                            entityId = event.id,
                            action = SyncAction.UPDATE,
                            payloadJson = SyncPayloadJson.encode(ExpenseDocumentMapper.toMap(expense, splits))
                        )
                    )
                }
            }
            val accountId = currentAccountId() ?: expense?.createdBy.orEmpty()
            if (accountId.isNotBlank()) {
                syncProcessor?.processNext(accountId)
            }
            database.expenseDao.getExpenseById(event.id)?.syncStatus ?: SyncStatus.PENDING_UPDATE
        }
    }

    private fun loadHomeData(initialUser: User): HomeData {
        val now = System.currentTimeMillis()
        val user = database.userDao.getUserById(initialUser.id)?.toDomain() ?: initialUser
        val allUsersById = database.userDao.getAllUsers().associate { it.id to it.toDomain() }
        val groupEntities = database.groupDao.getGroupsForUser(user.id)

        val balancesByGroup = mutableMapOf<String, MemberBalance>()
        val outstandingPayments = mutableListOf<OutstandingPayment>()
        val paidOutPayments = mutableListOf<PaidOutPayment>()

        val dashboardGroups = groupEntities.mapIndexed { index, groupEntity ->
            val memberEntities = database.groupDao.getMembersForGroup(groupEntity.id)
            val domainMembers = memberEntities.map { member ->
                val memberName = allUsersById[member.userId]?.displayName.orEmpty()
                member.toDomain(memberName)
            }
            val domainGroup = groupEntity.toDomain(domainMembers)

            val expenses = database.expenseDao.getActiveExpensesForGroup(groupEntity.id)
            val splits = database.expenseDao.getSplitsForGroup(groupEntity.id)
            val settlements = database.settlementDao.getSettlementsForGroup(groupEntity.id)

            val paidByUser = expenses.filter { it.payerId == user.id }.sumOf { it.amountCents }
            val owedByUser = splits.filter { it.userId == user.id }.sumOf { it.amountOwedCents }
            val settledOut = settlements.filter {
                it.debtorId == user.id && it.status == SettlementStatus.COMPLETED
            }.sumOf { it.amountCents }
            val settledIn = settlements.filter {
                it.creditorId == user.id && it.status == SettlementStatus.COMPLETED
            }.sumOf { it.amountCents }

            val netBalance = paidByUser - owedByUser + settledOut - settledIn
            balancesByGroup[groupEntity.id] = MemberBalance(
                userId = user.id,
                netBalanceCents = netBalance,
                currency = "DKK"
            )

            if (netBalance < 0L) {
                val creditorId = domainMembers.firstOrNull { it.userId != user.id }?.userId ?: groupEntity.createdBy
                val creditorName = allUsersById[creditorId]?.displayName?.ifBlank { "Member" } ?: "Member"
                outstandingPayments.add(
                    OutstandingPayment(
                        id = "pay-${groupEntity.id}",
                        groupId = groupEntity.id,
                        creditorName = creditorName,
                        since = expenses.minOfOrNull { it.createdAt } ?: groupEntity.updatedAt,
                        transfer = DebtTransfer(
                            debtorId = user.id,
                            creditorId = creditorId,
                            amountCents = -netBalance,
                            currency = "DKK"
                        )
                    )
                )
            }

            settlements.filter { it.debtorId == user.id && it.status == SettlementStatus.COMPLETED }.forEach { s ->
                val creditorName = allUsersById[s.creditorId]?.displayName?.ifBlank { "Member" } ?: "Member"
                paidOutPayments.add(
                    PaidOutPayment(
                        settlement = s.toDomain().copy(currency = "DKK"),
                        creditorName = creditorName
                    )
                )
            }

            val pendingCount = expenses.count { it.syncStatus != SyncStatus.SYNCED }
            val latestActivity = listOfNotNull(
                groupEntity.updatedAt,
                expenses.maxOfOrNull { it.createdAt },
                settlements.maxOfOrNull { it.createdAt }
            ).maxOrNull() ?: groupEntity.updatedAt

            DashboardGroup(
                group = domainGroup.copy(currency = "DKK"),
                colourIndex = index,
                isFavourite = index < 2,
                lastActivityAt = latestActivity,
                pendingExpenseCount = pendingCount
            )
        }

        val pendingQueueCount = database.syncQueueDao.getAllPending().size
        return HomeData(
            user = user,
            groups = dashboardGroups,
            finances = FinancialSummary(
                balancesByGroup = balancesByGroup,
                outstandingPayments = outstandingPayments
            ),
            paidOutPayments = paidOutPayments,
            hasUnreadActivity = outstandingPayments.isNotEmpty(),
            nowMillis = now,
            sync = SyncPresentation(pendingCount = pendingQueueCount)
        )
    }

    private fun loadActivityData(initialUser: User): ActivityData {
        val home = loadHomeData(initialUser)
        val allUsersById = database.userDao.getAllUsers().associate { it.id to it.toDomain() }
        val events = mutableListOf<ActivityEvent>()

        home.groups.forEach { dashGroup ->
            val expenses = database.expenseDao.getActiveExpensesForGroup(dashGroup.group.id)
            expenses.forEach { expense ->
                val payer = allUsersById[expense.payerId]
                val splits = database.expenseDao.getSplitsForExpense(expense.id)
                val userShare = splits.firstOrNull { it.userId == home.user.id }?.amountOwedCents ?: expense.amountCents
                if (expense.syncStatus == SyncStatus.FAILED) {
                    events.add(
                        ActivityEvent(
                            id = expense.id,
                            type = ActivityType.SyncFailure,
                            actor = null,
                            actorColourIndex = dashGroup.colourIndex,
                            description = "couldn't sync yet – retrying automatically",
                            group = dashGroup,
                            occurredAt = expense.createdAt,
                            unread = true,
                            syncStatus = SyncStatus.FAILED,
                            expenseTitle = expense.title
                        )
                    )
                } else {
                    events.add(
                        ActivityEvent(
                            id = "exp-${expense.id}",
                            type = ActivityType.ExpenseAdded,
                            actor = payer,
                            actorColourIndex = dashGroup.colourIndex,
                            description = "added ${expense.title} · your share",
                            group = dashGroup,
                            occurredAt = expense.createdAt,
                            amountCents = userShare,
                            currency = "DKK",
                            direction = if (expense.payerId == home.user.id) AmountDirection.Received else AmountDirection.Owed
                        )
                    )
                }
            }

            val settlements = database.settlementDao.getSettlementsForGroup(dashGroup.group.id)
            settlements.forEach { settlement ->
                val debtor = allUsersById[settlement.debtorId]
                val creditorName = allUsersById[settlement.creditorId]?.displayName ?: "Member"
                events.add(
                    ActivityEvent(
                        id = "stl-${settlement.id}",
                        type = ActivityType.Payment,
                        actor = debtor,
                        actorColourIndex = dashGroup.colourIndex,
                        description = if (settlement.debtorId == home.user.id) "paid $creditorName" else "paid you",
                        group = dashGroup,
                        occurredAt = settlement.createdAt,
                        amountCents = settlement.amountCents,
                        currency = "DKK",
                        direction = if (settlement.debtorId == home.user.id) AmountDirection.Settled else AmountDirection.Received
                    )
                )
            }
        }

        return ActivityData(
            userId = home.user.id,
            events = events.sortedByDescending { it.occurredAt },
            nowMillis = home.nowMillis,
            sync = home.sync
        )
    }
}
