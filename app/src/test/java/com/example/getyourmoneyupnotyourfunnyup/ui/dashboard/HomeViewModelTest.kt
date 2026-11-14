package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val payment = OutstandingPayment("p", "g", "Mads", 1, DebtTransfer("u", "m", 1234, "DKK"))
    private fun data() = HomeData(User("u", "u@test", "User"), listOf(DashboardGroup(Group("g", "Group", createdBy = "u"), 0)), FinancialSummary(mapOf("g" to MemberBalance("u", -1234, "DKK")), listOf(payment)))
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()
    private fun HomeViewModel.current() = state.value as HomeUiState.Success

    @Test fun confirmationDoesNotRecordUntilConfirmedAndDoubleTapIsIgnored() = runTest(dispatcher) {
        var calls = 0
        val result = CompletableDeferred<SyncStatus>()
        val vm = HomeViewModel(MutableStateFlow(data()), SettlementActions { calls++; result.await() })
        vm.askPayment("p")
        assertEquals(0, calls)
        vm.cancelPayment()
        vm.confirmPayment("p")
        assertEquals(0, calls)
        vm.askPayment("p")
        vm.confirmPayment("p")
        vm.confirmPayment("p")
        assertEquals(1, calls)
        assertEquals(InlineActionState.Working, vm.current().paymentStates["p"])
        result.complete(SyncStatus.PENDING_INSERT)
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_INSERT), vm.current().paymentStates["p"])
        assertTrue(vm.current().unpaid.isEmpty())
    }

    @Test fun failedOperationKeepsDebtAndCanBeRetried() = runTest(dispatcher) {
        var calls = 0
        val vm = HomeViewModel(MutableStateFlow(data()), SettlementActions { calls++; if (calls == 1) error("Not saved") else SyncStatus.SYNCED })
        vm.askPayment("p"); vm.confirmPayment("p")
        assertTrue(vm.current().paymentStates["p"] is InlineActionState.Failed)
        assertEquals(1, vm.current().unpaid.size)
        vm.askPayment("p"); vm.confirmPayment("p")
        assertEquals(2, calls)
        assertTrue(vm.current().unpaid.isEmpty())
    }

    @Test fun missingServiceDoesNotPretendPaymentWorked() = runTest(dispatcher) {
        val vm = HomeViewModel(MutableStateFlow(data()))
        vm.askAll(); vm.confirmAll(); vm.askPayment("p"); vm.confirmPayment("p")
        assertFalse(vm.current().canSettle)
        assertEquals(1, vm.current().unpaid.size)
        assertTrue(vm.current().paymentStates.isEmpty())
    }

    @Test fun payAllKeepsIndependentResultsWhenOneOperationFails() = runTest(dispatcher) {
        val other = payment.copy(id = "p2", transfer = payment.transfer.copy(creditorId = "other"))
        val input = data().copy(finances = data().finances!!.copy(balancesByGroup = mapOf("g" to MemberBalance("u", -2468, "DKK")), outstandingPayments = listOf(payment, other)))
        val vm = HomeViewModel(MutableStateFlow(input), SettlementActions { if (it.id == "p2") error("Not saved") else SyncStatus.SYNCED })
        vm.askAll(); vm.confirmAll()
        assertTrue(vm.current().paymentStates["p"] is InlineActionState.Done)
        assertTrue(vm.current().paymentStates["p2"] is InlineActionState.Failed)
        assertEquals(listOf(other), vm.current().unpaid)
    }

    @Test fun cachedSnapshotCanRenderWithOfflineMarkerWithoutAnyRemoteCall() = runTest(dispatcher) {
        val cached = data().copy(offlineSavedAt = 123L)
        val vm = HomeViewModel(MutableStateFlow(cached))
        assertEquals(cached, vm.current().data)
        assertEquals(123L, vm.current().data.offlineSavedAt)
    }

    @Test fun missingTransfersDoNotBecomeAllPaidUp() = runTest(dispatcher) {
        val input = data().copy(finances = data().finances!!.copy(outstandingPayments = emptyList()))
        val vm = HomeViewModel(MutableStateFlow(input))
        assertFalse(vm.current().hasFinancialSummary)
    }

    @Test fun refreshFailureRetainsTheLastDeliveredDashboard() = runTest(dispatcher) {
        val cached = data()
        val vm = HomeViewModel(flow { emit(cached); error("Offline") })
        assertEquals(cached, vm.current().data)
        assertNotNull(vm.current().sourceError)
    }

    @Test fun changedAmountDoesNotInheritSuccessfulStateOfOldPayment() = runTest(dispatcher) {
        val response = CompletableDeferred<SyncStatus>()
        val source = MutableStateFlow(data())
        val vm = HomeViewModel(source, SettlementActions { response.await() })
        vm.askPayment("p"); vm.confirmPayment("p")
        val nextPayment = payment.copy(transfer = payment.transfer.copy(amountCents = 2000))
        source.value = data().copy(finances = FinancialSummary(mapOf("g" to MemberBalance("u", -2000, "DKK")), listOf(nextPayment)))
        response.complete(SyncStatus.SYNCED)
        assertEquals(listOf(nextPayment), vm.current().unpaid)
        assertFalse(vm.current().paymentStates["p"] is InlineActionState.Done)
    }

    @Test fun changedTotalCancelsPayAllConfirmation() = runTest(dispatcher) {
        var calls = 0
        val source = MutableStateFlow(data())
        val vm = HomeViewModel(source, SettlementActions { calls++; SyncStatus.SYNCED })
        vm.askAll()
        val next = payment.copy(transfer = payment.transfer.copy(amountCents = 2000))
        source.value = data().copy(finances = FinancialSummary(mapOf("g" to MemberBalance("u", -2000, "DKK")), listOf(next)))
        vm.confirmAll()
        assertFalse(vm.current().confirmingAll)
        assertEquals(0, calls)
    }

    @Test fun recordedCardSurvivesAnImmediateUpdatedBalanceSnapshot() = runTest(dispatcher) {
        val source = MutableStateFlow(data())
        val vm = HomeViewModel(source, SettlementActions {
            source.value = data().copy(finances = FinancialSummary(mapOf("g" to MemberBalance("u", 0, "DKK")), emptyList()))
            SyncStatus.SYNCED
        })
        vm.togglePayments()
        vm.askPayment("p")
        vm.confirmPayment("p")
        assertTrue(vm.current().hasFinancialSummary)
        assertTrue(vm.current().unpaid.isEmpty())
        assertEquals(listOf(payment), vm.current().payments)
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), vm.current().paymentStates["p"])
        vm.closePayments()
        assertTrue(vm.current().payments.isEmpty())
    }

    @Test fun inconsistentTransfersDoNotUnderstateTheGroupBalance() = runTest(dispatcher) {
        val source = MutableStateFlow(data().copy(finances = FinancialSummary(
            mapOf("g" to MemberBalance("u", -10000, "DKK")), listOf(payment))))
        val vm = HomeViewModel(source)
        assertFalse(vm.current().hasFinancialSummary)
        assertNull(vm.current().balanceForGroup("g"))
        source.value = data()
        assertEquals(-1234L, vm.current().balanceForGroup("g"))
    }

    @Test fun confirmationCannotUseAnInconsistentRefreshedSnapshot() = runTest(dispatcher) {
        var calls = 0
        val source = MutableStateFlow(data())
        val vm = HomeViewModel(source, SettlementActions { calls++; SyncStatus.SYNCED })
        vm.askPayment("p")
        source.value = data().copy(finances = data().finances!!.copy(
            balancesByGroup = mapOf("g" to MemberBalance("u", -9999, "DKK"))))
        vm.confirmPayment("p")
        assertEquals(0, calls)
    }

    @Test fun zeroBalanceAndEmptyQueueDoNotInventASettlementAcknowledgement() = runTest(dispatcher) {
        val source = MutableStateFlow(data())
        val vm = HomeViewModel(source, SettlementActions { SyncStatus.PENDING_INSERT })
        vm.askPayment("p"); vm.confirmPayment("p")
        source.value = data().copy(finances = FinancialSummary(
            mapOf("g" to MemberBalance("u", 0, "DKK")), emptyList()), sync = SyncPresentation(pendingCount = 0))
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_INSERT), vm.current().paymentStates["p"])
    }

    @Test fun recordedButUnsyncedSettlementCannotBeRecordedTwice() = runTest(dispatcher) {
        var calls = 0
        val vm = HomeViewModel(MutableStateFlow(data()), SettlementActions { calls++; SyncStatus.FAILED })
        vm.askPayment("p"); vm.confirmPayment("p")
        vm.askPayment("p"); vm.confirmPayment("p")
        assertEquals(1, calls)
        assertEquals(InlineActionState.Done(SyncStatus.FAILED), vm.current().paymentStates["p"])
    }

    @Test fun failedReadObservationCanResumeWithoutRepeatingSettlement() = runTest(dispatcher) {
        var subscriptions = 0
        var calls = 0
        val updates = MutableStateFlow(data())
        val source = flow {
            subscriptions++
            emit(data())
            if (subscriptions == 1) error("Offline")
            emitAll(updates)
        }
        val vm = HomeViewModel(source, SettlementActions { calls++; SyncStatus.PENDING_INSERT })
        vm.askPayment("p"); vm.confirmPayment("p")
        assertNotNull(vm.current().sourceError)
        vm.retryObservation(); vm.retryObservation()
        assertEquals(2, subscriptions)
        assertNull(vm.current().sourceError)
        assertEquals(1, calls)
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_INSERT), vm.current().paymentStates["p"])
    }
}
