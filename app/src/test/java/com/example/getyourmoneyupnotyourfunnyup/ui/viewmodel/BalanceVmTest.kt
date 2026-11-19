package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.repository.*
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.balances.balanceAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceVmTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private fun ledger() = BalanceLedger("you", Group("g", "Trip", currency = "DKK", createdBy = "you",
        members = listOf(GroupMember("g", "you"), GroupMember("g", "friend"))),
        mapOf("you" to "Ramin", "friend" to "Ida"), listOf(Expense("e", "g", "Food", 10000,
        currency = "DKK", payerId = "friend", createdBy = "friend", splits = listOf(
            ExpenseSplit("s1", "e", "you", 5000), ExpenseSplit("s2", "e", "friend", 5000)))), emptyList())
    private fun vm(source: MutableStateFlow<BalanceLedger>) = BalanceViewModel("you", "g", BalanceRepository { _, _ -> source }, dispatcher)
    @Test fun calculatesNamedBalancesAndUpdatesFromLocalLedger() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val model = vm(source)
        val first = model.state.value as BalanceUiState.Success
        assertEquals(-5000L, first.members.single { it.isYou }.amountCents)
        assertEquals("Ramin", first.members.single { it.isYou }.name)
        assertEquals(listOf(DebtTransfer("you", "friend", 5000, "DKK")), first.suggestions)
        source.value = ledger().copy(expenses = emptyList())
        val empty = model.state.value as BalanceUiState.Success
        assertTrue(empty.suggestions.isEmpty())
        assertFalse(empty.hasLedgerEntries); assertTrue(empty.members.all { it.amountCents == 0L })
    }
    @Test fun invalidSharesRecoverWhenCorrected() = runTest(dispatcher) {
        val good = ledger(); val source = MutableStateFlow(good.copy(expenses = listOf(good.expenses.single().copy(amountCents = 1))))
        val model = vm(source)
        assertFalse((model.state.value as BalanceUiState.Error).canRetry)
        source.value = good
        assertTrue(model.state.value is BalanceUiState.Success)
    }
    @Test fun refusesCurrencyRelabelling() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger().let { it.copy(group = it.group.copy(currency = "EUR")) })
        assertEquals(BalanceUiState.UnsupportedCurrency("EUR"), vm(source).state.value)
    }
    @Test fun accessLossClearsPreviouslyVisibleBalances() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val model = vm(source)
        source.value = ledger().copy(accountId = "another")
        assertFalse((model.state.value as BalanceUiState.Error).canRetry)
    }
    @Test fun nonMembersCannotReadBalances() = runTest(dispatcher) {
        val data = ledger(); val source = MutableStateFlow(data.copy(group = data.group.copy(members = emptyList())))
        assertTrue(vm(source).state.value is BalanceUiState.Error)
    }
    @Test fun preservesFormerMemberAndReportsUnsyncedData() = runTest(dispatcher) {
        val data = ledger(); val source = MutableStateFlow(data.copy(group = data.group.copy(
            members = listOf(GroupMember("g", "you")), syncStatus = SyncStatus.FAILED)))
        val state = vm(source).state.value as BalanceUiState.Success
        assertTrue(state.members.single { it.userId == "friend" }.isFormerMember)
        assertTrue(state.hasFailedSync); assertEquals(1, state.pendingCount)
        assertEquals(-5000L, state.members.single { it.isYou }.amountCents)
    }
    @Test fun sourceFailureCanBeRetried() = runTest(dispatcher) {
        var calls = 0
        val model = BalanceViewModel("you", "g", BalanceRepository { _, _ -> flow {
            calls++; if (calls == 1) error("unavailable") else { emit(ledger()); kotlinx.coroutines.awaitCancellation() }
        } }, dispatcher)
        assertTrue((model.state.value as BalanceUiState.Error).canRetry)
        model.retry()
        assertTrue(model.state.value is BalanceUiState.Success)
        model.retry(); assertEquals(2, calls)
    }
    @Test fun completedLocallyPendingRepaymentClearsSuggestions() = runTest(dispatcher) {
        val source = MutableStateFlow(ledger()); val model = vm(source)
        source.value = ledger().copy(settlements = listOf(Settlement(
            id = "paid", groupId = "g", debtorId = "you", creditorId = "friend",
            amountCents = 5000, currency = "DKK", paymentMethod = "Simulated", status = SettlementStatus.COMPLETED,
            syncStatus = SyncStatus.PENDING_INSERT)))
        val state = model.state.value as BalanceUiState.Success
        assertTrue(state.suggestions.isEmpty())
        assertTrue(state.members.all { it.amountCents == 0L })
        assertEquals(1, state.pendingCount)
    }
    @Test fun amountsRetainExactDanishMinorUnitsAtLongBoundaries() {
        assertEquals("−92.233.720.368.547.758,08 kr", balanceAmount(Long.MIN_VALUE))
        assertEquals("+92.233.720.368.547.758,07 kr", balanceAmount(Long.MAX_VALUE))
        assertEquals("0,00 kr", balanceAmount(0))
    }
}
