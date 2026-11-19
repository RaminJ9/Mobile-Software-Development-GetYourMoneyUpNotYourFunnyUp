package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.BalanceLedger
import com.example.getyourmoneyupnotyourfunnyup.data.repository.BalanceRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.PaymentRepository
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState
import java.util.UUID
import com.example.getyourmoneyupnotyourfunnyup.model.BalanceCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.DebtTransfer
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BalanceMember(
    val userId: String,
    val name: String,
    val amountCents: Long,
    val isYou: Boolean = false,
    val isFormerMember: Boolean = false
)

data class BalancePayment(
    val id: String,
    val transfer: DebtTransfer,
    val debtorName: String,
    val creditorName: String,
    val action: InlineActionState = InlineActionState.Idle,
    val markAsPaid: Boolean = false
)

sealed interface BalanceUiState {
    data object Loading : BalanceUiState
    data class Error(val message: String, val canRetry: Boolean = true) : BalanceUiState
    data class UnsupportedCurrency(val currency: String) : BalanceUiState
    data class Success(
        val groupName: String,
        val members: List<BalanceMember>,
        val hasLedgerEntries: Boolean,
        val pendingCount: Int,
        val hasFailedSync: Boolean,
        val suggestions: List<DebtTransfer> = emptyList(),
        val payments: List<BalancePayment> = emptyList(),
        val paymentsAvailable: Boolean = false,
        val isSaving: Boolean = false
    ) : BalanceUiState
}

/** S8b state, scoped to one account and group. Dispose this ViewModel on logout/group exit. */
class BalanceViewModel(
    private val accountId: String,
    private val groupId: String,
    private val repository: BalanceRepository,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val paymentRepository: PaymentRepository? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow<BalanceUiState>(BalanceUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var recording = false

    init { observe() }

    fun retry() {
        if (observation?.isActive != true) observe()
    }

    private fun observe() {
        mutableState.value = BalanceUiState.Loading
        observation = viewModelScope.launch {
            try {
                repository.observeGroup(accountId, groupId).collect { ledger ->
                    // Also reject a mismatched snapshot if a future repository adapter is miswired.
                    if (ledger.accountId != accountId || ledger.group.id != groupId ||
                        ledger.group.members.none { it.userId == accountId }) {
                        throw SecurityException("You don't have access to this group's balances.")
                    }
                    mutableState.value = if (ledger.group.currency != "DKK") {
                        BalanceUiState.UnsupportedCurrency(ledger.group.currency)
                    } else {
                        try {
                            val next = withContext(calculationDispatcher) { present(ledger) }
                            mergePayments(next, ledger, mutableState.value as? BalanceUiState.Success)
                        } catch (_: IllegalArgumentException) {
                            BalanceUiState.Error("Some expenses or repayments are inconsistent. Check their amounts and shares.", canRetry = false)
                        } catch (_: ArithmeticException) {
                            BalanceUiState.Error("The amounts in this group are too large to display safely.", canRetry = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                // Never retain the previous group's financial data after an access/session failure.
                mutableState.value = BalanceUiState.Error("You don't have access to this group's balances. Sign in again or return to your groups.", canRetry = false)
            } catch (_: Exception) {
                mutableState.value = BalanceUiState.Error("Couldn't load balances. Try again.")
            }
        }
    }

    fun askPayment(id: String) = askSettlement(id, creditorConfirmation = false)

    fun askMarkAsPaid(id: String) = askSettlement(id, creditorConfirmation = true)

    private fun askSettlement(id: String, creditorConfirmation: Boolean) {
        val current = mutableState.value as? BalanceUiState.Success ?: return
        val card = current.payments.firstOrNull { it.id == id } ?: return
        val actorId = if (creditorConfirmation) card.transfer.creditorId else card.transfer.debtorId
        if (recording || paymentRepository == null || actorId != accountId ||
            card.transfer !in current.suggestions ||
            card.action != InlineActionState.Idle && card.action !is InlineActionState.Failed) return
        mutableState.value = current.copy(payments = current.payments.map {
            when {
                it.id == id -> it.copy(action = InlineActionState.Confirming, markAsPaid = creditorConfirmation)
                it.action == InlineActionState.Confirming -> it.copy(action = InlineActionState.Idle)
                else -> it
            }
        })
    }

    fun cancelPayment(id: String) {
        val current = mutableState.value as? BalanceUiState.Success ?: return
        if (recording) return
        mutableState.value = current.copy(payments = current.payments.map {
            if (it.id == id && it.action == InlineActionState.Confirming) it.copy(action = InlineActionState.Idle) else it
        })
    }

    fun confirmPayment(id: String) {
        val service = paymentRepository ?: return
        val current = mutableState.value as? BalanceUiState.Success ?: return
        val card = current.payments.firstOrNull { it.id == id } ?: return
        val actorId = if (card.markAsPaid) card.transfer.creditorId else card.transfer.debtorId
        if (recording || card.action != InlineActionState.Confirming ||
            actorId != accountId || card.transfer !in current.suggestions) return
        recording = true
        mutableState.value = current.copy(isSaving = true, payments = current.payments.map {
            if (it.id == id) it.copy(action = InlineActionState.Working) else it
        })
        viewModelScope.launch {
            try {
                // Card ID is the stable request ID, including after a failed/ambiguous save.
                val saved = if (card.markAsPaid) service.markAsPaid(accountId, groupId, card.transfer, card.id)
                    else service.pay(accountId, groupId, card.transfer, card.id)
                val latest = mutableState.value as? BalanceUiState.Success
                if (latest != null) mutableState.value = latest.copy(payments = latest.payments.map {
                    if (it.id == id && it.action !is InlineActionState.Done) it.copy(action = InlineActionState.Done(saved.syncStatus)) else it
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                observation?.cancel()
                mutableState.value = BalanceUiState.Error("You no longer have access to make this payment. Sign in again or return to your groups.", canRetry = false)
            } catch (error: Exception) {
                val latest = mutableState.value as? BalanceUiState.Success
                val message = if (error is IllegalArgumentException) "This balance changed. Review the latest suggestions before trying again."
                    else "Couldn't save payment. Try again."
                if (latest != null) mutableState.value = latest.copy(payments = latest.payments.map {
                    // A committed receipt observed locally is stronger evidence than a late callback error.
                    if (it.id == id && it.action !is InlineActionState.Done) it.copy(action = InlineActionState.Failed(message)) else it
                })
            } finally {
                recording = false
                val latest = mutableState.value as? BalanceUiState.Success
                if (latest != null) mutableState.value = latest.copy(isSaving = false)
            }
        }
    }

    private fun mergePayments(
        next: BalanceUiState.Success,
        ledger: BalanceLedger,
        previous: BalanceUiState.Success?
    ): BalanceUiState.Success {
        val remaining = next.suggestions.toMutableList()
        val cards = previous?.payments.orEmpty().mapNotNull { card ->
            val receipt = ledger.settlements.firstOrNull {
                it.id == card.id && it.status == SettlementStatus.COMPLETED &&
                    it.debtorId == card.transfer.debtorId && it.creditorId == card.transfer.creditorId &&
                    it.amountCents == card.transfer.amountCents && it.currency == card.transfer.currency
            }
            when {
                receipt != null -> card.copy(action = InlineActionState.Done(receipt.syncStatus))
                card.action is InlineActionState.Done || card.action == InlineActionState.Working -> {
                    remaining.remove(card.transfer)
                    card
                }
                remaining.remove(card.transfer) -> card
                else -> null // Changed suggestions dismiss stale confirmations and failed attempts.
            }
        } + remaining.map { transfer ->
            BalancePayment(UUID.randomUUID().toString(), transfer,
                next.members.first { it.userId == transfer.debtorId }.name,
                next.members.first { it.userId == transfer.creditorId }.name)
        }
        return next.copy(payments = cards, paymentsAvailable = paymentRepository != null, isSaving = recording)
    }

    private fun present(ledger: BalanceLedger): BalanceUiState.Success {
        val currentMembers = ledger.group.members.map { it.userId }.toSet()
        val balances = BalanceCalculator.calculate(
            groupId, ledger.group.currency, currentMembers, ledger.expenses, ledger.settlements
        )
        val activeExpenses = ledger.expenses.filterNot { it.isDeleted }
        val statuses = listOf(ledger.group.syncStatus) + activeExpenses.map { it.syncStatus } + ledger.settlements.map { it.syncStatus }
        return BalanceUiState.Success(
            groupName = ledger.group.name,
            members = balances.map { balance ->
                val former = balance.userId !in currentMembers
                BalanceMember(
                    userId = balance.userId,
                    name = ledger.namesByUserId[balance.userId]?.takeIf { it.isNotBlank() }
                        ?: if (former) "Former member" else "Member",
                    amountCents = balance.netBalanceCents,
                    isYou = balance.userId == accountId,
                    isFormerMember = former
                )
            },
            hasLedgerEntries = activeExpenses.isNotEmpty() || ledger.settlements.any { it.status == SettlementStatus.COMPLETED },
            pendingCount = statuses.count { it != SyncStatus.SYNCED },
            hasFailedSync = SyncStatus.FAILED in statuses,
            suggestions = SettlementCalculator.suggest(balances)
        )
    }
}
