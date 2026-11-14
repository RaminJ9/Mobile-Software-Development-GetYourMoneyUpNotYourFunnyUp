package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.model.DebtTransfer
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.MemberBalance
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User

data class DashboardGroup(
    val group: Group,
    val colourIndex: Int,
    val isFavourite: Boolean = false,
    val lastActivityAt: Long = group.updatedAt,
    val pendingExpenseCount: Int = 0
)

data class OutstandingPayment(
    val id: String,
    val groupId: String,
    val creditorName: String,
    val since: Long,
    val transfer: DebtTransfer
)

data class FinancialSummary(
    val balancesByGroup: Map<String, MemberBalance>,
    val outstandingPayments: List<OutstandingPayment>
)

data class PaidOutPayment(val settlement: Settlement, val creditorName: String)

data class HomeData(
    val user: User,
    val groups: List<DashboardGroup>,
    val finances: FinancialSummary? = null,
    val paidOutPayments: List<PaidOutPayment>? = null,
    val hasUnreadActivity: Boolean = false,
    val offlineSavedAt: Long? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val sync: SyncPresentation = SyncPresentation()
) {
    val displaySync: SyncPresentation get() = offlineSavedAt?.let {
        sync.copy(connection = com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState.Offline, savedAt = it)
    } ?: sync
}

fun interface SettlementActions {
    suspend fun recordSettlement(payment: OutstandingPayment): SyncStatus
}

sealed interface InlineActionState {
    data object Idle : InlineActionState
    data object Confirming : InlineActionState
    data object Working : InlineActionState
    data class Done(val syncStatus: SyncStatus? = null) : InlineActionState
    data class Failed(val message: String) : InlineActionState
}
