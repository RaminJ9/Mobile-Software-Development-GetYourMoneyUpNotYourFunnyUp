package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.repository.*
import com.example.getyourmoneyupnotyourfunnyup.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseVmTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private fun ledger() = BalanceLedger("you", Group("g", "Trip", currency = "DKK", createdBy = "you",
        members = listOf(GroupMember("g", "you"), GroupMember("g", "ida"))), mapOf("you" to "Ramin", "ida" to "Ida"),
        listOf(Expense("e", "g", "Dinner", 10000, currency = "DKK", payerId = "ida", createdBy = "ida",
            splits = listOf(ExpenseSplit("s", "e", "you", 10000)))), emptyList())
    private fun vm(source: MutableStateFlow<BalanceLedger>) = ExpenseViewModel("you", "g", BalanceRepository { _, _ -> source }, dispatcher)
    private fun ExpenseViewModel.success() = state.value as ExpenseUiState.Success

    @Test fun selectionShowsLatestSavedExpenseAndDismissDoesNotMutateLedger() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val model = vm(source)
        model.open("unknown"); assertNull(model.success().selected)
        model.open("e")
        assertEquals("Ida", model.success().name(model.success().selected!!.payerId))
        source.value = source.value.copy(expenses = source.value.expenses.map { it.copy(syncStatus = SyncStatus.FAILED, receiptLocalUri = "content://receipt") })
        assertEquals(SyncStatus.FAILED, model.success().selected!!.syncStatus)
        assertEquals("content://receipt", model.success().selected!!.receiptLocalUri)
        model.dismiss(); assertNull(model.success().selected)
        assertEquals(1, source.value.expenses.size)
    }
    @Test fun removedExpenseClosesSheetAndDeletedRowsAreNotShown() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val model = vm(source); model.open("e")
        source.value = source.value.copy(expenses = source.value.expenses.map { it.copy(isDeleted = true) })
        assertNull(model.success().selected); assertTrue(model.success().expenses.isEmpty())
        assertEquals("This expense is no longer available.", model.success().notice)
    }
    @Test fun invalidSharesAndUnsupportedCurrencyNeverShowMisleadingDanishTotals() = runTest(dispatcher) {
        val data = ledger(); val source = MutableStateFlow(data.copy(expenses = data.expenses.map { it.copy(amountCents = 1) }))
        val model = vm(source)
        assertFalse((model.state.value as ExpenseUiState.Error).canRetry)
        source.value = data; model.open("e"); assertNotNull(model.success().selected)
        source.value = data.copy(group = data.group.copy(currency = "EUR"))
        assertEquals(ExpenseUiState.UnsupportedCurrency("EUR"), model.state.value)
    }
    @Test fun lostAccessRemovesSelectedExpenseAndCannotBeRestoredByLaterData() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val model = vm(source); model.open("e")
        source.value = source.value.copy(group = source.value.group.copy(members = emptyList()))
        assertFalse((model.state.value as ExpenseUiState.Error).canRetry)
        source.value = ledger(); assertTrue(model.state.value is ExpenseUiState.Error)
    }
    @Test fun sourceFailureCanBeRetried() = runTest(dispatcher) {
        var calls = 0
        val model = ExpenseViewModel("you", "g", BalanceRepository { _, _ -> flow {
            calls++; if (calls == 1) error("read failed") else emit(ledger())
        } }, dispatcher)
        assertTrue((model.state.value as ExpenseUiState.Error).canRetry)
        model.retry(); assertTrue(model.state.value is ExpenseUiState.Success)
    }
    @Test fun ownerMustConfirmAndRepeatedTapsDoNotDuplicateRemoval() = runTest(dispatcher) {
        val original = ledger()
        val source = MutableStateFlow(original.copy(expenses = original.expenses.map { it.copy(createdBy = "you") }))
        val gate = kotlinx.coroutines.CompletableDeferred<SyncStatus>()
        var calls = 0
        val model = ExpenseViewModel("you", "g", BalanceRepository { _, _ -> source }, dispatcher,
            ExpenseRemoval { _, _, _ -> calls++; gate.await() })
        model.open("e"); model.confirmRemoval(); assertEquals(0, calls)
        model.askRemoval(); model.keepExpense(); model.confirmRemoval(); assertEquals(0, calls)
        model.askRemoval(); model.confirmRemoval(); model.confirmRemoval(); model.dismiss()
        assertEquals(1, calls); assertEquals("e", model.success().selectedId)
        gate.complete(SyncStatus.PENDING_DELETE)
        assertTrue(model.success().expenses.isEmpty()); assertNull(model.success().selected)
        assertEquals("Expense removed.", model.success().notice)
    }
    @Test fun nonOwnerCannotConfirmRemovalAndFailureKeepsExpense() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); var calls = 0
        val model = ExpenseViewModel("you", "g", BalanceRepository { _, _ -> source }, dispatcher,
            ExpenseRemoval { _, _, _ -> calls++; error("offline database failure") })
        model.open("e"); model.askRemoval(); model.confirmRemoval(); assertEquals(0, calls)
        source.value = source.value.copy(expenses = source.value.expenses.map { it.copy(createdBy = "you") })
        model.askRemoval(); model.confirmRemoval()
        assertEquals(1, calls); assertNotNull(model.success().selected)
        assertTrue(model.success().removalAction is com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState.Failed)
    }

    @Test fun groupHistoryIncludesOnlyCompletedVirtualPayments() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger().copy(settlements = listOf(
            Settlement("paid", "g", "you", "ida", 10000, "DKK", "Simulated", syncStatus = SyncStatus.PENDING_INSERT),
            Settlement("failed", "g", "you", "ida", 10000, "DKK", "Simulated", status = SettlementStatus.FAILED))))
        val model = vm(source)
        assertEquals(listOf("paid"), model.success().settlements.map { it.id })
        assertEquals(SyncStatus.PENDING_INSERT, model.success().settlements.single().syncStatus)
    }

}
