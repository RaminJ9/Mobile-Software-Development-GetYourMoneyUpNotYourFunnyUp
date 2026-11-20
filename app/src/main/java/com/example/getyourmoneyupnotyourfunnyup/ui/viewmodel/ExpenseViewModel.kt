package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.BalanceRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ExpenseRemoval
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ReceiptRepository
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState
import com.example.getyourmoneyupnotyourfunnyup.model.BalanceCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ExpenseUiState {
    data object Loading : ExpenseUiState
    data class Error(val message: String, val canRetry: Boolean = true) : ExpenseUiState
    data class UnsupportedCurrency(val currency: String) : ExpenseUiState
    data class Success(
        val accountId: String,
        val expenses: List<Expense>,
        val names: Map<String, String>,
        val selectedId: String? = null,
        val notice: String? = null,
        val removalAvailable: Boolean = false,
        val removalAction: InlineActionState = InlineActionState.Idle,
        val settlements: List<Settlement> = emptyList()
    ) : ExpenseUiState {
        val selected: Expense? get() = expenses.firstOrNull { it.id == selectedId }
        fun name(userId: String): String = names[userId]?.takeIf { it.isNotBlank() } ?: "Member"
    }
}

/** S9: read-only saved expenses and a selected details sheet, scoped to the open group/account. */
class ExpenseViewModel(
    private val accountId: String,
    private val groupId: String,
    private val repository: BalanceRepository,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val removal: ExpenseRemoval? = null,
    private val receipts: ReceiptRepository? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow<ExpenseUiState>(ExpenseUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var removing = false
    private val removedIds = mutableSetOf<String>()
    init { observe() }

    fun open(expenseId: String) {
        val current = mutableState.value as? ExpenseUiState.Success ?: return
        if (!removing && current.expenses.any { it.id == expenseId }) mutableState.value = current.copy(selectedId = expenseId, notice = null, removalAction = InlineActionState.Idle)
    }
    fun dismiss() {
        val current = mutableState.value as? ExpenseUiState.Success ?: return
        if (!removing) mutableState.value = current.copy(selectedId = null, removalAction = InlineActionState.Idle)
    }
    fun retry() { if (observation?.isActive != true) observe() }

    suspend fun loadReceipt(expense: Expense): String? {
        val current = mutableState.value as? ExpenseUiState.Success ?: return null
        if (current.selectedId != expense.id) return null
        return receipts?.load(accountId, groupId, expense)
    }

    fun askRemoval() {
        val current = mutableState.value as? ExpenseUiState.Success ?: return
        val selected = current.selected ?: return
        if (!removing && removal != null && (selected.payerId == accountId || selected.createdBy == accountId))
            mutableState.value = current.copy(removalAction = InlineActionState.Confirming)
    }
    fun keepExpense() {
        val current = mutableState.value as? ExpenseUiState.Success ?: return
        if (!removing) mutableState.value = current.copy(removalAction = InlineActionState.Idle)
    }
    fun confirmRemoval() {
        val service = removal ?: return
        val current = mutableState.value as? ExpenseUiState.Success ?: return
        val selected = current.selected ?: return
        if (removing || current.removalAction != InlineActionState.Confirming ||
            selected.payerId != accountId && selected.createdBy != accountId) return
        removing = true
        mutableState.value = current.copy(removalAction = InlineActionState.Working)
        viewModelScope.launch {
            try {
                service.remove(accountId, groupId, selected)
                removedIds += selected.id
                val latest = mutableState.value as? ExpenseUiState.Success
                if (latest != null) mutableState.value = latest.copy(expenses = latest.expenses.filterNot { it.id == selected.id },
                    selectedId = null, removalAction = InlineActionState.Idle, notice = "Expense removed.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: SecurityException) {
                observation?.cancel()
                mutableState.value = ExpenseUiState.Error("You no longer have access to remove this expense.", canRetry = false)
            } catch (_: Exception) {
                val latest = mutableState.value as? ExpenseUiState.Success
                if (latest != null) mutableState.value = latest.copy(removalAction = InlineActionState.Failed("Couldn't remove expense. Review the latest details and try again."))
            } finally { removing = false }
        }
    }

    private fun observe() {
        mutableState.value = ExpenseUiState.Loading
        observation = viewModelScope.launch {
            try {
                repository.observeGroup(accountId, groupId).collect { ledger ->
                    if (ledger.accountId != accountId || ledger.group.id != groupId ||
                        ledger.group.members.none { it.userId == accountId }) throw SecurityException()
                    mutableState.value = if (ledger.group.currency != "DKK") {
                        ExpenseUiState.UnsupportedCurrency(ledger.group.currency)
                    } else {
                        try {
                            val expenses = withContext(calculationDispatcher) {
                                // Reuse the ledger checks: show finalized, non-negative shares that add up.
                                BalanceCalculator.calculate(groupId, ledger.group.currency,
                                    ledger.group.members.map { it.userId }, ledger.expenses, ledger.settlements)
                                ledger.expenses.filterNot { it.isDeleted }
                                    .sortedWith(compareByDescending<Expense> { it.createdAt }.thenBy { it.id })
                            }
                            val visible = expenses.filterNot { it.id in removedIds }
                            val previous = mutableState.value as? ExpenseUiState.Success
                            val selected = previous?.selectedId?.takeIf { id -> visible.any { it.id == id } }
                            val old = previous?.selected
                            val fresh = visible.firstOrNull { it.id == selected }
                            val unchanged = old != null && fresh?.copy(syncStatus = old.syncStatus, receiptLocalUri = old.receiptLocalUri) == old
                            ExpenseUiState.Success(accountId, visible, ledger.namesByUserId.toMap(), selected,
                                notice = if (previous?.selectedId != null && selected == null) "This expense is no longer available." else null,
                                removalAvailable = removal != null,
                                removalAction = if (selected != null && (removing || unchanged)) previous.removalAction else InlineActionState.Idle,
                                settlements = ledger.settlements.filter { it.status == SettlementStatus.COMPLETED }.sortedByDescending { it.createdAt })
                        } catch (_: IllegalArgumentException) {
                            ExpenseUiState.Error("Some saved expense amounts or shares are inconsistent.", canRetry = false)
                        } catch (_: ArithmeticException) {
                            ExpenseUiState.Error("The saved amounts are too large to display safely.", canRetry = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                mutableState.value = ExpenseUiState.Error("You don't have access to this group's expenses. Sign in again or return to your groups.", canRetry = false)
            } catch (_: Exception) {
                mutableState.value = ExpenseUiState.Error("Couldn't load saved expenses. Try again.")
            }
        }
    }
}
