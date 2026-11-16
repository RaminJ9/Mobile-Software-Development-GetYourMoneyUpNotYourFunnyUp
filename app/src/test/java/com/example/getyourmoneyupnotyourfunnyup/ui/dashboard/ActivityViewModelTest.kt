package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
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
class ActivityViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val group = DashboardGroup(Group("g", "Group", createdBy = "u"), 0)
    private val payment = OutstandingPayment("p", "g", "Mads", 1, DebtTransfer("u", "m", 1234, "DKK"))
    private val reminder = ActivityEvent("reminder", ActivityType.Reminder, User("m", "m@test", "Mads"), 1, "reminded you", group, 1, unread = true, payment = payment)
    private val failure = ActivityEvent("failure", ActivityType.SyncFailure, null, 0, "couldn't sync", group, 2, unread = true, syncStatus = SyncStatus.FAILED)
    private val expense = ActivityEvent("expense", ActivityType.ExpenseAdded, null, 0, "added an expense", group, 3, unread = true)
    private fun input() = ActivityData("u", listOf(reminder, failure, expense))
    private fun ActivityViewModel.current() = state.value as ActivityUiState.Success
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun readStateIsIndependentOfNeedsYouAndAppliesAcrossFilters() = runTest(dispatcher) {
        val vm = ActivityViewModel(MutableStateFlow(input()))
        vm.selectFilter(ActivityFilter.Payments)
        vm.markAllRead()
        assertFalse(vm.current().hasUnread)
        assertEquals(3, vm.current().readIds.size)
        assertEquals(2, vm.current().needsCount)
    }

    @Test fun retryOnlyShowsSyncedAfterActualSuccessfulResultAndIgnoresDoubleTap() = runTest(dispatcher) {
        var calls = 0
        val response = CompletableDeferred<SyncStatus>()
        val vm = ActivityViewModel(MutableStateFlow(input()), retries = SyncRetryActions { calls++; response.await() })
        vm.retry("failure"); vm.retry("failure")
        assertEquals(1, calls)
        assertEquals(InlineActionState.Working, vm.current().actionFor(failure))
        assertEquals(2, vm.current().needsCount)
        response.complete(SyncStatus.SYNCED)
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), vm.current().actionFor(failure))
        assertEquals(1, vm.current().needsCount)
    }

    @Test fun queueAcceptanceIsNotMisrepresentedAsSyncSuccess() = runTest(dispatcher) {
        val source = MutableStateFlow(input())
        val vm = ActivityViewModel(source, retries = SyncRetryActions { SyncStatus.PENDING_UPDATE })
        vm.retry("failure")
        assertEquals(2, vm.current().needsCount)
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_UPDATE), vm.current().actionFor(failure))
        val synced = failure.copy(syncStatus = SyncStatus.SYNCED)
        source.value = input().copy(events = listOf(reminder, synced, expense))
        assertEquals(1, vm.current().needsCount)
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), vm.current().actionFor(synced))
    }

    @Test fun retryFailureStaysActionableAndDoesNotHideFeed() = runTest(dispatcher) {
        val vm = ActivityViewModel(MutableStateFlow(input()), retries = SyncRetryActions { error("No connection") })
        vm.retry("failure")
        assertTrue(vm.current().actionFor(failure) is InlineActionState.Failed)
        assertEquals(3, vm.current().events.size)
        assertEquals(2, vm.current().needsCount)
    }

    @Test fun paymentRequiresConfirmationAndCancellationMakesNoCall() = runTest(dispatcher) {
        var calls = 0
        val vm = ActivityViewModel(MutableStateFlow(input()), SettlementActions { calls++; SyncStatus.PENDING_INSERT })
        vm.askPayment("reminder")
        assertEquals(0, calls)
        vm.cancelPayment(); vm.confirmPayment("reminder")
        assertEquals(0, calls)
        vm.askPayment("reminder"); vm.confirmPayment("reminder")
        assertEquals(1, calls)
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_INSERT), vm.current().actionFor(reminder))
        assertEquals(1, vm.current().needsCount)
    }

    @Test fun missingServicesLeaveActionsUnresolved() = runTest(dispatcher) {
        val vm = ActivityViewModel(MutableStateFlow(input()))
        vm.askPayment("reminder"); vm.confirmPayment("reminder"); vm.retry("failure")
        assertEquals(2, vm.current().needsCount)
        assertTrue(vm.current().actions.isEmpty())
        assertFalse(vm.current().canRetry)
        assertFalse(vm.current().canSettle)
    }

    @Test fun accountChangeResetsSessionReadStateAndIgnoresOldCompletion() = runTest(dispatcher) {
        val response = CompletableDeferred<SyncStatus>()
        val source = MutableStateFlow(input())
        val vm = ActivityViewModel(source, retries = SyncRetryActions { response.await() })
        vm.markAllRead(); vm.retry("failure")
        source.value = ActivityData("other", listOf(failure))
        response.complete(SyncStatus.SYNCED)
        assertTrue(vm.current().hasUnread)
        assertTrue(vm.current().readIds.isEmpty())
        assertTrue(vm.current().actions.isEmpty())
    }

    @Test fun changedPaymentIsNotSettledByAnOlderInFlightResult() = runTest(dispatcher) {
        val response = CompletableDeferred<SyncStatus>()
        val source = MutableStateFlow(input())
        val vm = ActivityViewModel(source, SettlementActions { response.await() })
        vm.askPayment("reminder"); vm.confirmPayment("reminder")
        val changed = reminder.copy(payment = payment.copy(transfer = payment.transfer.copy(amountCents = 9999)))
        source.value = input().copy(events = listOf(changed))
        response.complete(SyncStatus.SYNCED)
        assertEquals(1, vm.current().needsCount)
        assertFalse(vm.current().actionFor(changed) is InlineActionState.Done)
    }

    @Test fun refreshFailureRetainsTheLastDeliveredEvents() = runTest(dispatcher) {
        val cached = input()
        val vm = ActivityViewModel(flow { emit(cached); error("Offline") })
        assertEquals(cached, vm.current().data)
        assertNotNull(vm.current().sourceError)
    }

    @Test fun pendingRetryBecomesFailedAndCanBeRequestedAgain() = runTest(dispatcher) {
        var calls = 0
        val source = MutableStateFlow(input())
        val vm = ActivityViewModel(source, retries = SyncRetryActions { calls++; SyncStatus.PENDING_UPDATE })
        vm.retry("failure")
        val pending = failure.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        source.value = input().copy(events = listOf(pending))
        source.value = input()
        assertTrue(vm.current().actionFor(failure) is InlineActionState.Failed)
        vm.retry("failure")
        assertEquals(2, calls)
    }

    @Test fun repeatedPersistedFailureDoesNotLeaveQueuedFeedbackStuck() = runTest(dispatcher) {
        val source = MutableStateFlow(input())
        val vm = ActivityViewModel(source, retries = SyncRetryActions { SyncStatus.PENDING_UPDATE })
        vm.retry("failure")
        source.value = input().copy(nowMillis = input().nowMillis + 1)
        assertTrue(vm.current().actionFor(failure) is InlineActionState.Failed)
        assertEquals(2, vm.current().needsCount)
    }

    @Test fun sourceAcknowledgementWinsOverAnOlderSchedulingResult() = runTest(dispatcher) {
        val source = MutableStateFlow(input())
        val response = CompletableDeferred<SyncStatus>()
        val vm = ActivityViewModel(source, retries = SyncRetryActions { response.await() })
        vm.retry("failure")
        val synced = failure.copy(syncStatus = SyncStatus.SYNCED)
        source.value = input().copy(events = listOf(synced))
        response.complete(SyncStatus.PENDING_UPDATE)
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), vm.current().actions["failure"])
        assertEquals(0, vm.current().needsCount)
    }

    @Test fun sourceFailureWinsOverAnOlderSchedulingResult() = runTest(dispatcher) {
        val source = MutableStateFlow(input())
        val response = CompletableDeferred<SyncStatus>()
        val vm = ActivityViewModel(source, retries = SyncRetryActions { response.await() })
        vm.retry("failure")
        source.value = input().copy(events = listOf(failure.copy(syncStatus = SyncStatus.PENDING_UPDATE)))
        source.value = input()
        response.complete(SyncStatus.PENDING_UPDATE)
        assertTrue(vm.current().actionFor(failure) is InlineActionState.Failed)
    }

    @Test fun reminderMetadataIsNotAssumedToAcknowledgeASettlement() = runTest(dispatcher) {
        val source = MutableStateFlow(input())
        val vm = ActivityViewModel(source, SettlementActions { SyncStatus.PENDING_INSERT })
        vm.askPayment("reminder"); vm.confirmPayment("reminder")
        val next = reminder.copy(syncStatus = SyncStatus.SYNCED)
        source.value = input().copy(events = listOf(next), sync = SyncPresentation(pendingCount = 0))
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_INSERT), vm.current().actionFor(next))
    }

    @Test fun failedObservationCanResumeAndPreserveReadState() = runTest(dispatcher) {
        var subscriptions = 0
        val updates = MutableStateFlow(input())
        val source = flow {
            subscriptions++
            emit(input())
            if (subscriptions == 1) error("Offline")
            emitAll(updates)
        }
        val vm = ActivityViewModel(source)
        vm.markAllRead()
        vm.retryObservation(); vm.retryObservation()
        assertEquals(2, subscriptions)
        assertFalse(vm.current().hasUnread)
        assertNull(vm.current().sourceError)
    }

    @Test fun initialReadFailureCanBeRetriedWithoutAQueuedAction() = runTest(dispatcher) {
        var subscriptions = 0
        val vm = ActivityViewModel(flow {
            if (++subscriptions == 1) error("Offline")
            emit(input())
        })
        assertTrue(vm.state.value is ActivityUiState.Error)
        vm.retryObservation()
        assertEquals(3, vm.current().events.size)
        assertTrue(vm.current().actions.isEmpty())
    }

    @Test fun initialPendingSyncStateIsNotShownAsAnIdleFailure() = runTest(dispatcher) {
        val pending = failure.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        val vm = ActivityViewModel(MutableStateFlow(input().copy(events = listOf(pending))))
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_UPDATE), vm.current().actionFor(pending))
        assertEquals(1, vm.current().needsCount)
    }

    @Test fun suppliedBackgroundRetryTransitionsUpdateWithoutAnotherUiAction() = runTest(dispatcher) {
        val source = MutableStateFlow(input())
        val vm = ActivityViewModel(source, retries = SyncRetryActions { SyncStatus.PENDING_UPDATE })
        vm.retry("failure")
        source.value = input().copy(nowMillis = input().nowMillis + 1)
        assertTrue(vm.current().actionFor(failure) is InlineActionState.Failed)
        val pending = failure.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        source.value = input().copy(events = listOf(pending))
        assertEquals(InlineActionState.Done(SyncStatus.PENDING_UPDATE), vm.current().actionFor(pending))
        val synced = failure.copy(syncStatus = SyncStatus.SYNCED)
        source.value = input().copy(events = listOf(synced))
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), vm.current().actionFor(synced))
        assertEquals(0, vm.current().needsCount)
    }
}
