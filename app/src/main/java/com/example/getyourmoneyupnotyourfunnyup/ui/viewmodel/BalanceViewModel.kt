package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.BalanceLedger
import com.example.getyourmoneyupnotyourfunnyup.data.repository.BalanceRepository
import com.example.getyourmoneyupnotyourfunnyup.model.BalanceCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

sealed interface BalanceUiState {
    data object Loading : BalanceUiState
    data class Error(val message: String, val canRetry: Boolean = true) : BalanceUiState
    data class UnsupportedCurrency(val currency: String) : BalanceUiState
    data class Success(
        val groupName: String,
        val members: List<BalanceMember>,
        val hasLedgerEntries: Boolean,
        val pendingCount: Int,
        val hasFailedSync: Boolean
    ) : BalanceUiState
}

/** S8b state, scoped to one account and group. Dispose this ViewModel on logout/group exit. */
class BalanceViewModel(
    private val accountId: String,
    private val groupId: String,
    private val repository: BalanceRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<BalanceUiState>(BalanceUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null

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
                            present(ledger)
                        } catch (_: IllegalArgumentException) {
                            BalanceUiState.Error("Some expenses or repayments are inconsistent. Check their amounts and shares.", canRetry = false)
                        } catch (_: ArithmeticException) {
                            BalanceUiState.Error("The amounts in this group are too large to display safely.", canRetry = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                // Never retain the previous group's financial data after an access/session failure.
                mutableState.value = BalanceUiState.Error("You don't have access to this group's balances. Sign in again or return to your groups.", canRetry = false)
            } catch (_: Exception) {
                mutableState.value = BalanceUiState.Error("Couldn't load balances. Try again.")
            }
        }
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
            hasFailedSync = SyncStatus.FAILED in statuses
        )
    }
}
