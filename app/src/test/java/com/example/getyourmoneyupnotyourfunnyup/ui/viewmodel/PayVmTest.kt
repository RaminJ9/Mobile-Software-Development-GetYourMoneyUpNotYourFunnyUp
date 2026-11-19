package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.repository.*
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PayVmTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private fun ledger() = BalanceLedger("you", Group("g", "Trip", currency = "DKK", createdBy = "you",
        members = listOf(GroupMember("g", "you"), GroupMember("g", "ida"))),
        mapOf("you" to "Ramin", "ida" to "Ida"), listOf(Expense("e", "g", "Food", 10000,
            currency = "DKK", payerId = "ida", createdBy = "ida", splits = listOf(
                ExpenseSplit("s1", "e", "you", 5000), ExpenseSplit("s2", "e", "ida", 5000)))), emptyList())
    private class Payments : PaymentRepository {
        val ids = mutableListOf<String>()
        val markedIds = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var error: Exception? = null
        var onSaved: (Settlement) -> Unit = {}
        override suspend fun markAsPaid(accountId: String, groupId: String, transfer: DebtTransfer, requestId: String): Settlement {
            markedIds += requestId
            return pay(accountId, groupId, transfer, requestId)
        }
        override suspend fun pay(accountId: String, groupId: String, transfer: DebtTransfer, requestId: String): Settlement {
            ids += requestId
            gate?.await()
            error?.let { throw it }
            return Settlement(requestId, groupId, transfer.debtorId, transfer.creditorId, transfer.amountCents,
                transfer.currency, "Simulated", syncStatus = SyncStatus.PENDING_INSERT).also(onSaved)
        }
    }
    private fun vm(source: MutableStateFlow<BalanceLedger>, payments: PaymentRepository? = null) =
        BalanceViewModel("you", "g", BalanceRepository { _, _ -> source }, dispatcher, payments)
    private fun BalanceViewModel.success() = state.value as BalanceUiState.Success

    @Test fun openingAndCancellingConfirmationNeverSaves() = runTest(dispatcher) {
        val service = Payments(); val model = vm(MutableStateFlow(ledger()), service)
        val id = model.success().payments.single().id
        model.confirmPayment(id); assertTrue(service.ids.isEmpty())
        model.askPayment(id)
        assertEquals(InlineActionState.Confirming, model.success().payments.single().action)
        model.cancelPayment(id); model.confirmPayment(id)
        assertEquals(InlineActionState.Idle, model.success().payments.single().action)
        assertTrue(service.ids.isEmpty())
    }
    @Test fun saveBlocksDoubleTapsAndKeepsPaidCardAfterLedgerRefresh() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments()
        service.gate = CompletableDeferred()
        service.onSaved = { source.value = source.value.copy(settlements = listOf(it)) }
        val model = vm(source, service); val id = model.success().payments.single().id
        model.askPayment(id); model.confirmPayment(id); model.confirmPayment(id); model.askPayment(id)
        assertTrue(model.success().isSaving)
        assertEquals(InlineActionState.Working, model.success().payments.single().action)
        assertEquals(1, service.ids.size)
        service.gate!!.complete(Unit)
        assertFalse(model.success().isSaving)
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_INSERT), model.success().payments.single().action)
        assertEquals(id, model.success().payments.single().id)
        assertTrue(model.success().suggestions.isEmpty())
        assertTrue(model.success().members.all { it.amountCents == 0L })
    }
    @Test fun failedSaveRequiresReconfirmationAndReusesRequestId() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments(); service.error = IllegalStateException("disk busy")
        val model = vm(source, service); val id = model.success().payments.single().id
        model.askPayment(id); model.confirmPayment(id)
        assertTrue(model.success().payments.single().action is InlineActionState.Failed)
        model.confirmPayment(id); assertEquals(1, service.ids.size)
        service.error = null
        model.askPayment(id); model.confirmPayment(id)
        assertEquals(listOf(id, id), service.ids)
        assertTrue(model.success().payments.single().action is InlineActionState.Done)
    }
    @Test fun changedSuggestionDismissesOldConfirmation() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments(); val model = vm(source, service)
        val id = model.success().payments.single().id
        model.askPayment(id)
        source.value = source.value.copy(expenses = emptyList())
        model.confirmPayment(id)
        assertTrue(service.ids.isEmpty()); assertTrue(model.success().payments.isEmpty())
    }
    @Test fun missingServiceAndCreditorsCannotPaySomebodyElsesDebt() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger())
        val readOnly = vm(source)
        readOnly.askPayment(readOnly.success().payments.single().id)
        assertFalse(readOnly.success().paymentsAvailable)
        assertEquals(InlineActionState.Idle, readOnly.success().payments.single().action)
        val data = ledger(); source.value = data.copy(expenses = data.expenses.map { it.copy(payerId = "you") })
        val service = Payments(); val model = vm(source, service)
        val id = model.success().payments.single().id
        model.askPayment(id); model.confirmPayment(id)
        assertEquals(InlineActionState.Idle, model.success().payments.single().action)
        assertTrue(service.ids.isEmpty())
    }
    @Test fun accessLossDuringSaveCannotRestoreFinancialScreenFromLateCallback() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments(); service.gate = CompletableDeferred()
        val model = vm(source, service); val id = model.success().payments.single().id
        model.askPayment(id); model.confirmPayment(id)
        source.value = source.value.copy(accountId = "other")
        service.gate!!.complete(Unit)
        assertTrue(model.state.value is BalanceUiState.Error)
    }
    @Test fun securityFailureStopsObservationAndRemovesFinancialData() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments(); service.error = SecurityException("logged out")
        val model = vm(source, service); val id = model.success().payments.single().id
        model.askPayment(id); model.confirmPayment(id)
        assertFalse((model.state.value as BalanceUiState.Error).canRetry)
        source.value = source.value.copy(expenses = emptyList())
        assertTrue(model.state.value is BalanceUiState.Error)
    }
    @Test fun sameTransferFromNewExpenseRemainsPayableAfterPreviousReceipt() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments()
        service.onSaved = { source.value = source.value.copy(settlements = source.value.settlements + it) }
        val model = vm(source, service); val firstId = model.success().payments.single().id
        model.askPayment(firstId); model.confirmPayment(firstId)
        val extra = ledger().expenses.single().copy(id = "e2", splits = listOf(
            ExpenseSplit("s3", "e2", "you", 5000), ExpenseSplit("s4", "e2", "ida", 5000)))
        source.value = source.value.copy(expenses = source.value.expenses + extra)
        assertEquals(2, model.success().payments.size)
        val newCard = model.success().payments.single { it.action == InlineActionState.Idle }
        assertNotEquals(firstId, newCard.id)
        model.askPayment(newCard.id); model.confirmPayment(newCard.id)
        assertEquals(2, service.ids.size)
        assertTrue(model.success().suggestions.isEmpty())
    }
    @Test fun creditorCanCancelThenConfirmReceiptWithoutDuplicateSave() = runTest(dispatcher) {
        val data = ledger()
        val source = MutableStateFlow(data.copy(expenses = data.expenses.map { it.copy(payerId = "you") }))
        val service = Payments(); service.gate = CompletableDeferred()
        service.onSaved = { source.value = source.value.copy(settlements = listOf(it)) }
        val model = vm(source, service); val id = model.success().payments.single().id
        model.askMarkAsPaid(id)
        assertTrue(model.success().payments.single().markAsPaid)
        model.cancelPayment(id); model.confirmPayment(id)
        assertTrue(service.ids.isEmpty())
        model.askMarkAsPaid(id); model.confirmPayment(id); model.confirmPayment(id)
        assertEquals(listOf(id), service.markedIds)
        assertTrue(model.success().isSaving)
        service.gate!!.complete(Unit)
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_INSERT), model.success().payments.single().action)
        assertTrue(model.success().suggestions.isEmpty())
        assertTrue(model.success().members.all { it.amountCents == 0L })
    }
    @Test fun onlyCreditorCanMarkAndRetryRetainsConfirmationId() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments(); val model = vm(source, service)
        val debtorCard = model.success().payments.single().id
        model.askMarkAsPaid(debtorCard); model.confirmPayment(debtorCard)
        assertTrue(service.markedIds.isEmpty())
        source.value = source.value.copy(expenses = source.value.expenses.map { it.copy(payerId = "you") })
        val id = model.success().payments.single().id
        service.error = IllegalStateException("disk busy")
        model.askMarkAsPaid(id); model.confirmPayment(id)
        assertTrue(model.success().payments.single().action is InlineActionState.Failed)
        service.error = null
        model.askMarkAsPaid(id); model.confirmPayment(id)
        assertEquals(listOf(id, id), service.markedIds)
        assertTrue(model.success().payments.single().action is InlineActionState.Done)
    }
    @Test fun receiptSyncUpdatesPaidCardWithoutNewPayment() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val service = Payments()
        service.onSaved = { source.value = source.value.copy(settlements = listOf(it)) }
        val model = vm(source, service); val id = model.success().payments.single().id
        model.askPayment(id); model.confirmPayment(id)
        source.value = source.value.copy(settlements = source.value.settlements.map { it.copy(syncStatus = SyncStatus.SYNCED) })
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), model.success().payments.single().action)
        assertEquals(1, service.ids.size)
    }
}
