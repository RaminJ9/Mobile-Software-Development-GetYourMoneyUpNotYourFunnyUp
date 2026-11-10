package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.DateLabels
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.Period
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.activityFeed
import com.example.getyourmoneyupnotyourfunnyup.model.lastPaymentLabel
import com.example.getyourmoneyupnotyourfunnyup.model.myDebts
import com.example.getyourmoneyupnotyourfunnyup.model.myGroups
import com.example.getyourmoneyupnotyourfunnyup.model.paidOutByGroup
import com.example.getyourmoneyupnotyourfunnyup.model.totalOwedToMe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Where a payment row is in the "Pay → Cancel / Confirm → Paying… → Paid" flow. */
enum class PayState { Idle, Confirm, Pending, Paid }

enum class HomeGroupTab(val label: String) { Recent("Recent"), Favourites("Favourites"), All("All") }

@Immutable
data class DebtRowUi(
    val groupId: String,
    val groupName: String,
    val initial: String,
    val paletteIndex: Int,
    val creditorId: String,
    val creditorName: String,
    /** "To Mads · Since Sun 1 Nov" or "Settled just now". */
    val subtitle: String,
    val amountCents: Long,
    val state: PayState,
)

@Immutable
data class PieSliceUi(val groupId: String, val paletteIndex: Int, val valueCents: Long)

@Immutable
data class SliceDetailUi(
    val groupId: String,
    val name: String,
    val paletteIndex: Int,
    val amountCents: Long,
    val lastPayment: String,
)

@Immutable
data class HomeUiState(
    val dateLabel: String,
    val firstName: String,
    val hasUnread: Boolean,
    /** Non-null while offline: "You're offline · showing saved data from 10:42. …" */
    val offlineMessage: String?,
    val hasGroups: Boolean,
    val owing: Boolean,
    val oweTotalCents: Long,
    /** "3 groups" */
    val oweGroupsLabel: String,
    val owedToYouCents: Long,
    val paymentsOpen: Boolean,
    val showPayAll: Boolean,
    val confirmingAll: Boolean,
    val debts: List<DebtRowUi>,
    val period: Period,
    val periodMenuOpen: Boolean,
    val slices: List<PieSliceUi>,
    val paidTotalCents: Long,
    val selectedSlice: SliceDetailUi?,
    val groupTab: HomeGroupTab,
    val groups: List<GroupRowUi>,
    val groupsInTab: Int,
    val showSeeAll: Boolean,
)

sealed interface HomeAction {
    data object TogglePayments : HomeAction
    data object ClosePayments : HomeAction
    data class AskPay(val groupId: String) : HomeAction
    data object AskPayAll : HomeAction
    data object CancelPay : HomeAction
    data class ConfirmPay(val groupId: String) : HomeAction
    data object ConfirmPayAll : HomeAction
    data object TogglePeriodMenu : HomeAction
    data object DismissPeriodMenu : HomeAction
    data class SelectPeriod(val period: Period) : HomeAction
    data class SelectSlice(val groupId: String?) : HomeAction
    data class SelectGroupTab(val tab: HomeGroupTab) : HomeAction
}

/** A payment made from this screen, kept so the row can show "Paying…" then "Paid". */
data class SessionPayment(val settlementId: String, val creditorId: String, val amountCents: Long)

/** Screen-local state that is not part of the ledger. */
data class HomeLocalState(
    val paymentsOpen: Boolean = false,
    /** Group id being confirmed, or [CONFIRM_ALL]. */
    val confirming: String? = null,
    val period: Period = Period.MONTH,
    val periodMenuOpen: Boolean = false,
    val selectedSliceId: String? = null,
    val groupTab: HomeGroupTab = HomeGroupTab.Recent,
    val sessionPayments: Map<String, SessionPayment> = emptyMap(),
) {
    companion object {
        const val CONFIRM_ALL = "__all__"
    }
}

private const val MAX_HOME_GROUPS = 6

/** Pure mapping from ledger + local state to what Home renders. Used by the ViewModel and previews. */
fun buildHomeUiState(snapshot: LedgerSnapshot, online: Boolean, local: HomeLocalState, now: Long): HomeUiState {
    val myGroups = snapshot.myGroups()
    val openDebts = snapshot.myDebts()

    val sessionRows = local.sessionPayments.mapNotNull { (groupId, payment) ->
        val group = snapshot.group(groupId) ?: return@mapNotNull null
        val settlement = snapshot.settlements.firstOrNull { it.id == payment.settlementId } ?: return@mapNotNull null
        val paid = settlement.status == SettlementStatus.COMPLETED
        val creditor = snapshot.person(payment.creditorId)
        DebtRowUi(
            groupId = groupId, groupName = group.name, initial = group.name.take(1), paletteIndex = group.colorIndex,
            creditorId = creditor.id, creditorName = creditor.name,
            subtitle = if (paid) "Settled just now" else "To ${creditor.name} · Paying…",
            amountCents = payment.amountCents, state = if (paid) PayState.Paid else PayState.Pending,
        )
    }
    val debtRows = openDebts
        .filter { debt -> local.sessionPayments[debt.groupId]?.creditorId != debt.creditorId }
        .mapNotNull { debt ->
            val group = snapshot.group(debt.groupId) ?: return@mapNotNull null
            val creditor = snapshot.person(debt.creditorId)
            DebtRowUi(
                groupId = debt.groupId, groupName = group.name, initial = group.name.take(1),
                paletteIndex = group.colorIndex, creditorId = creditor.id, creditorName = creditor.name,
                subtitle = "To ${creditor.name} · ${DateLabels.since(debt.since, now)}",
                amountCents = debt.amountCents,
                state = if (local.confirming == debt.groupId) PayState.Confirm else PayState.Idle,
            )
        }
    val rows = (debtRows + sessionRows).sortedByDescending { it.amountCents }
    val unpaid = rows.filter { it.state != PayState.Paid }
    val owing = unpaid.isNotEmpty()

    val spend = snapshot.paidOutByGroup(local.period, now)
    val slices = spend.mapNotNull { (groupId, value) ->
        snapshot.group(groupId)?.let { PieSliceUi(groupId, it.colorIndex, value) }
    }
    val selected = local.selectedSliceId?.let { id ->
        val group = snapshot.group(id)
        val value = spend[id]
        if (group == null || value == null) null
        else SliceDetailUi(id, group.name, group.colorIndex, value, snapshot.lastPaymentLabel(id, now) ?: "No payments yet")
    }

    val allRows = myGroups.map { snapshot.groupRow(it, now) }
    val inTab = when (local.groupTab) {
        HomeGroupTab.Recent -> allRows.sortedByDescending { it.lastActiveAt }
        HomeGroupTab.Favourites -> allRows.filter { it.isFavourite }.sortedByDescending { it.lastActiveAt }
        HomeGroupTab.All -> allRows.sortedBy { it.name.lowercase() }
    }

    return HomeUiState(
        dateLabel = DateLabels.longDate(now),
        firstName = snapshot.me.name.trim().substringBefore(' ').ifEmpty { "there" },
        hasUnread = snapshot.activityFeed(now).any { it.isUnread },
        offlineMessage = if (online) null else offlineMessage(snapshot.lastSyncedAt, "Anything you add syncs when you're back."),
        hasGroups = myGroups.isNotEmpty(),
        owing = owing,
        oweTotalCents = unpaid.sumOf { it.amountCents },
        oweGroupsLabel = if (unpaid.size == 1) "1 group" else "${unpaid.size} groups",
        owedToYouCents = snapshot.totalOwedToMe(),
        paymentsOpen = local.paymentsOpen,
        showPayAll = owing && local.confirming == null && rows.all { it.state == PayState.Idle },
        confirmingAll = local.confirming == HomeLocalState.CONFIRM_ALL,
        debts = if (owing) rows else emptyList(),
        period = local.period,
        periodMenuOpen = local.periodMenuOpen,
        slices = slices,
        paidTotalCents = spend.values.sum(),
        selectedSlice = selected,
        groupTab = local.groupTab,
        groups = inTab.take(MAX_HOME_GROUPS),
        groupsInTab = inTab.size,
        showSeeAll = inTab.size > MAX_HOME_GROUPS,
    )
}

/** "You're offline · showing saved data from 10:42. <tail>" */
fun offlineMessage(lastSyncedAt: Long?, tail: String): String {
    val since = lastSyncedAt?.let { " from ${DateLabels.time(it)}" } ?: ""
    return "You're offline · showing saved data$since. $tail"
}

class HomeViewModel(private val ledger: LedgerRepository) : ViewModel() {

    private val local = MutableStateFlow(HomeLocalState())

    val uiState: StateFlow<HomeUiState> =
        combine(ledger.snapshot, ledger.isOnline, local) { snapshot, online, localState ->
            buildHomeUiState(snapshot, online, localState, ledger.now())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildHomeUiState(ledger.snapshot.value, ledger.isOnline.value, local.value, ledger.now()),
        )

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.TogglePayments -> local.update { it.copy(paymentsOpen = !it.paymentsOpen, confirming = null) }
            HomeAction.ClosePayments -> local.update { it.copy(paymentsOpen = false, confirming = null) }
            is HomeAction.AskPay -> local.update { it.copy(confirming = action.groupId) }
            HomeAction.AskPayAll -> local.update { it.copy(confirming = HomeLocalState.CONFIRM_ALL) }
            HomeAction.CancelPay -> local.update { it.copy(confirming = null) }
            is HomeAction.ConfirmPay -> pay(listOf(action.groupId))
            HomeAction.ConfirmPayAll -> pay(uiState.value.debts.filter { it.state != PayState.Paid && it.state != PayState.Pending }.map { it.groupId })
            HomeAction.TogglePeriodMenu -> local.update { it.copy(periodMenuOpen = !it.periodMenuOpen) }
            HomeAction.DismissPeriodMenu -> local.update { it.copy(periodMenuOpen = false) }
            is HomeAction.SelectPeriod -> local.update { it.copy(period = action.period, periodMenuOpen = false, selectedSliceId = null) }
            is HomeAction.SelectSlice -> local.update {
                it.copy(selectedSliceId = if (it.selectedSliceId == action.groupId) null else action.groupId, periodMenuOpen = false)
            }
            is HomeAction.SelectGroupTab -> local.update { it.copy(groupTab = action.tab) }
        }
    }

    private fun pay(groupIds: List<String>) {
        val rows = uiState.value.debts.filter { it.groupId in groupIds && (it.state == PayState.Idle || it.state == PayState.Confirm) }
        val payments = rows.associate { row ->
            row.groupId to SessionPayment(ledger.payDebt(row.groupId, row.creditorId, row.amountCents), row.creditorId, row.amountCents)
        }
        local.update { it.copy(confirming = null, sessionPayments = it.sessionPayments + payments) }
    }
}
