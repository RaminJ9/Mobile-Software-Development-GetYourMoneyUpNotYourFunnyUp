package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(
        val data: HomeData,
        val filter: GroupFilter = GroupFilter.Recent,
        val period: SpendPeriod = SpendPeriod.Month,
        val selectedGroupId: String? = null,
        val paymentsExpanded: Boolean = false,
        val confirmingAll: Boolean = false,
        val paymentStates: Map<String, InlineActionState> = emptyMap(),
        val recordedPayments: Map<String, OutstandingPayment> = emptyMap(),
        val canSettle: Boolean = false,
        val sourceError: String? = null
    ) : HomeUiState {
        val outstanding: List<OutstandingPayment> = data.finances?.outstandingPayments.orEmpty().filter {
            it.transfer.debtorId == data.user.id && it.transfer.currency == "DKK" && it.transfer.amountCents > 0
        }.distinctBy { it.id }
        // Biggest first, so a card that was just paid keeps its place instead of jumping to the end.
        val payments: List<OutstandingPayment> = (outstanding + recordedPayments.values).distinctBy { it.id }
            .sortedByDescending { it.transfer.amountCents }
        val unpaid: List<OutstandingPayment> = outstanding.filter { paymentStates[it.id] !is InlineActionState.Done }
        val isWorking: Boolean = paymentStates.values.any { it == InlineActionState.Working }
        val oweCents: Long = unpaid.sumOf { it.transfer.amountCents }
        val owingGroupCount: Int = unpaid.map { it.groupId }.distinct().size
        val owedCents: Long = data.groups.sumOf { group ->
            data.finances?.balancesByGroup?.get(group.group.id)?.takeIf {
                it.currency == "DKK" && it.userId == data.user.id
            }?.netBalanceCents?.coerceAtLeast(0L) ?: 0L
        }
        val hasFinancialSummary: Boolean = data.finances?.let { finances ->
            finances.outstandingPayments.all {
                it.transfer.currency == "DKK" && it.transfer.debtorId == data.user.id &&
                it.transfer.amountCents > 0 && data.groups.any { group -> group.group.id == it.groupId }
            } && data.groups.all { group ->
                val balance = finances.balancesByGroup[group.group.id]
                balance != null && balance.currency == "DKK" && balance.userId == data.user.id &&
                outstanding.filter { it.groupId == group.group.id }.sumOf { it.transfer.amountCents } == maxOf(
                    0L,
                    -balance.netBalanceCents
                )
            }
        } == true

        fun balanceForGroup(groupId: String): Long? {
            if (!hasFinancialSummary) return null
            val balance = data.finances?.balancesByGroup?.get(groupId)?.netBalanceCents ?: return null
            return if (balance < 0 && payments.any { it.groupId == groupId }) {
                -unpaid.filter { it.groupId == groupId }.sumOf { it.transfer.amountCents }
            } else balance
        }
    }
}

class HomeViewModel(
    private val source: Flow<HomeData>,
    private val settlements: SettlementActions? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state = mutableState.asStateFlow()
    private var recording = false
    private var observation: Job? = null

    init { observe() }

    fun retryObservation() {
        val current = mutableState.value
        if (observation?.isActive != true &&
            (current is HomeUiState.Error || current is HomeUiState.Success && current.sourceError != null)) observe()
    }

    private fun observe() {
        observation = viewModelScope.launch {
            try {
                source.collect { data ->
                    val current = mutableState.value as? HomeUiState.Success
                    mutableState.value = if (current == null || current.data.user.id != data.user.id) {
                        HomeUiState.Success(data, canSettle = settlements != null)
                    } else {
                        val unchangedIds = data.finances?.outstandingPayments.orEmpty().filter { payment ->
                            current.data.finances?.outstandingPayments.orEmpty().any { it == payment }
                        }.map { it.id }.toSet()
                        val recorded = current.recordedPayments.filterValues { payment ->
                            val next = data.finances?.outstandingPayments.orEmpty().firstOrNull { it.id == payment.id }
                            next == null || next == payment
                        }
                        current.copy(
                            data = data,
                            sourceError = null,
                            confirmingAll = current.confirmingAll &&
                                current.data.finances?.outstandingPayments == data.finances?.outstandingPayments,
                            paymentStates = current.paymentStates.filterKeys { it in unchangedIds || it in recorded },
                            recordedPayments = recorded
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val current = mutableState.value as? HomeUiState.Success
                mutableState.value = current?.copy(sourceError = "Couldn't refresh. Showing the last dashboard.")
                ?: HomeUiState.Error(error.message ?: "Couldn't load your dashboard")
            }
        }
    }

    private fun update(change: (HomeUiState.Success) -> HomeUiState.Success) {
        mutableState.update { if (it is HomeUiState.Success) change(it) else it }
    }

    fun selectFilter(filter: GroupFilter) = update { it.copy(filter = filter) }
    fun selectPeriod(period: SpendPeriod) = update { it.copy(period = period, selectedGroupId = null) }
    fun selectSlice(groupId: String?) = update { it.copy(selectedGroupId = groupId) }
    fun togglePayments() = update {
        it.copy(
            paymentsExpanded = !it.paymentsExpanded,
            confirmingAll = false,
            recordedPayments = if (it.paymentsExpanded) emptyMap() else it.recordedPayments
        )
    }
    fun closePayments() = update {
        it.copy(paymentsExpanded = false, confirmingAll = false, recordedPayments = emptyMap())
    }

    fun cancelPayment() = update {
        it.copy(
            confirmingAll = false,
            paymentStates = it.paymentStates.filterValues { value -> value != InlineActionState.Confirming }
        )
    }

    fun askPayment(id: String) {
        cancelPayment()
        update {
            if (!it.canSettle || !it.hasFinancialSummary || recording || it.unpaid.none { payment -> payment.id == id }) it
            else it.copy(paymentStates = it.paymentStates + (id to InlineActionState.Confirming))
        }
    }

    fun askAll() {
        cancelPayment()
        update { if (it.canSettle && it.hasFinancialSummary && !recording && it.unpaid.isNotEmpty()) it.copy(confirmingAll = true) else it }
    }

    fun confirmPayment(id: String) {
        val current = mutableState.value as? HomeUiState.Success ?: return
        if (current.paymentStates[id] != InlineActionState.Confirming || recording || !current.hasFinancialSummary) return
        record(current.unpaid.filter { it.id == id })
    }

    fun confirmAll() {
        val current = mutableState.value as? HomeUiState.Success ?: return
        if (current.confirmingAll && !recording && current.hasFinancialSummary) record(current.unpaid)
    }

    private fun record(payments: List<OutstandingPayment>) {
        val actions = settlements ?: return
        val userId = (mutableState.value as? HomeUiState.Success)?.data?.user?.id ?: return
        recording = true
        update { it.copy(
                confirmingAll = false,
                paymentStates = it.paymentStates + payments.associate { p -> p.id to InlineActionState.Working }
        ) }
        viewModelScope.launch {
            try {
                for (payment in payments) {
                    val latest = mutableState.value as? HomeUiState.Success ?: return@launch
                    if (latest.data.user.id != userId) return@launch
                    val result = try {
                        check(latest.payments.any { it == payment }) { "This balance changed. Check the latest payment." }
                        InlineActionState.Done(actions.recordSettlement(payment))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        InlineActionState.Failed(error.message ?: "Couldn't record this payment. Try again.")
                    }
                    update {
                        val next = it.outstanding.firstOrNull { current -> current.id == payment.id }
                        if (it.data.user.id == userId && (next == payment || (next == null && result is InlineActionState.Done))) {
                            it.copy(
                                paymentStates = it.paymentStates + (payment.id to result),
                                recordedPayments = if (result is InlineActionState.Done) {
                                    it.recordedPayments + (payment.id to payment)
                                } else it.recordedPayments
                            )
                        } else it
                    }
                }
            } finally {
                recording = false
            }
        }
    }
}
