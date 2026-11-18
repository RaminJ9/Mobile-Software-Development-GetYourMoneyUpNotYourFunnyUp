package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ExpenseDraft
import com.example.getyourmoneyupnotyourfunnyup.data.repository.InMemoryLedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.DebtTransfer
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.failedSyncActivityId
import com.example.getyourmoneyupnotyourfunnyup.model.myNet
import com.example.getyourmoneyupnotyourfunnyup.testing.MainDispatcherRule
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * The adapter between our ledger and the Home/Activity screens: the mapping must pass the screens'
 * own consistency checks, payments and retries must be safe (no duplicates, no stale amounts, no
 * other account's data), and offline actions must be recorded and synced later (Reliability QA).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerDashboardSourceTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = 1_762_800_000_000L // fixed clock for deterministic dates
    private val me = DemoLedger.ME
    private val demo = DemoLedger.create(now)

    private fun TestScope.ledger(online: MutableStateFlow<Boolean>) = InMemoryLedgerRepository(
        scope = backgroundScope, isOnline = online, clock = { now }, initial = DemoLedger.create(now),
        syncDelayMs = 1_000, paymentDelayMs = 500,
    )

    private fun HomeData.payment(groupId: String) = finances!!.outstandingPayments.single { it.groupId == groupId }

    private fun ActivityData.event(id: String) = events!!.single { it.id == id }

    // --- Mapping ---------------------------------------------------------------------------------

    @Test
    fun home_passesTheScreensOwnBalanceCheck() {
        val state = HomeUiState.Success(homeData(demo, now), canSettle = true)
        // If this is false, Home shows "Balance unavailable" everywhere.
        assertTrue(state.hasFinancialSummary)
        assertEquals(41_250L, state.oweCents) // 412,50 kr, as in the hi-fi
        assertEquals(3, state.owingGroupCount)
        assertEquals(20_500L, state.owedCents)
        demo.groups.forEach { assertEquals(it.name, demo.myNet(it.id), state.balanceForGroup(it.id)) }
    }

    @Test
    fun home_paymentIdsNameTheExactObligation() {
        val ski = homeData(demo, now).payment("ski")
        assertEquals("pay:ski:$me:mads:28600", ski.id)
        assertEquals(DebtTransfer(me, "mads", 28_600L, "DKK"), ski.transfer)
        assertEquals("Mads", ski.creditorName)
    }

    @Test
    fun home_paidOutCountsOnlyMyCompletedPayments() {
        val paid = homeData(demo, now).paidOutPayments!!
        assertTrue(paid.isNotEmpty())
        assertTrue(paid.all { it.settlement.debtorId == me && it.settlement.status == SettlementStatus.COMPLETED })
    }

    @Test
    fun activity_reminderCarriesTheSamePaymentAsHome() {
        val reminder = activityData(demo, now).events!!.single { it.type == ActivityType.Reminder }
        assertEquals(homeData(demo, now).payment("ski"), reminder.payment)
        assertTrue(reminder.canPay(me))
    }

    @Test
    fun activity_failedUploadIsShownAsTheUsersSyncFailure() {
        val failure = activityData(demo, now).event(failedSyncActivityId("ski-fuel"))
        assertEquals(ActivityType.SyncFailure, failure.type)
        assertEquals(SyncStatus.FAILED, failure.syncStatus)
        assertEquals("Fuel for the drive", failure.expenseTitle)
        assertNull(failure.actor)
    }

    // --- Paying ----------------------------------------------------------------------------------

    @Test
    fun pay_online_waitsForTheUploadAndClearsTheDebt() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        val payment = homeData(ledger.snapshot.value, now).payment("ski")

        assertEquals(SyncStatus.SYNCED, source.recordSettlement(payment))
        assertTrue(homeData(ledger.snapshot.value, now).finances!!.outstandingPayments.none { it.groupId == "ski" })
    }

    @Test
    fun pay_offline_isRecordedOnThePhoneAndSyncsOnReconnect() = runTest {
        val online = MutableStateFlow(false)
        val ledger = ledger(online)
        val source = LedgerDashboardSource(ledger)
        val payment = homeData(ledger.snapshot.value, now).payment("ski")

        // Never blocked by the network: the payment completes locally and is queued.
        assertEquals(SyncStatus.PENDING_INSERT, source.recordSettlement(payment))
        val settlement = ledger.snapshot.value.settlements.last()
        assertEquals(SettlementStatus.COMPLETED, settlement.status)
        assertEquals(0L, ledger.snapshot.value.myNet("ski"))

        online.value = true
        runCurrent()
        advanceTimeBy(1_001)
        assertEquals(SyncStatus.SYNCED, ledger.snapshot.value.settlements.single { it.id == settlement.id }.syncStatus)
    }

    @Test
    fun pay_twoScreensConfirmingTheSameDebtMakeOnePayment() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        val payment = homeData(ledger.snapshot.value, now).payment("ski")
        val before = ledger.snapshot.value.settlements.size

        val fromHome = async { source.recordSettlement(payment) }
        val fromActivity = async { source.recordSettlement(payment) }

        assertEquals(SyncStatus.SYNCED, fromHome.await())
        assertEquals(SyncStatus.SYNCED, fromActivity.await())
        assertEquals(before + 1, ledger.snapshot.value.settlements.size)
    }

    @Test
    fun pay_refusesAnAmountThatHasChanged() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        val stale = homeData(ledger.snapshot.value, now).payment("ski")
        // Mads adds an expense, so what the user owes changes after the screen was drawn.
        ledger.addExpense(ExpenseDraft("ski", "Pizza", "mads", 6_000, mapOf("mads" to 3_000, me to 3_000), hasReceipt = false))
        val before = ledger.snapshot.value.settlements.size

        assertFailsWith("This balance changed") { source.recordSettlement(stale) }
        assertEquals(before, ledger.snapshot.value.settlements.size)
    }

    @Test
    fun pay_refusesAnotherAccountsDebt() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        val mine = homeData(ledger.snapshot.value, now).payment("ski")
        val theirs = mine.copy(transfer = mine.transfer.copy(debtorId = "ida"))

        assertFailsWith("another account") { source.recordSettlement(theirs) }
    }

    // --- Retrying a failed upload ----------------------------------------------------------------

    @Test
    fun retry_online_uploadsTheExpense() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        val failure = activityData(ledger.snapshot.value, now).event(failedSyncActivityId("ski-fuel"))

        assertEquals(SyncStatus.SYNCED, source.retry(failure))
        assertEquals(SyncStatus.SYNCED, ledger.snapshot.value.expenses.single { it.id == "ski-fuel" }.syncStatus)
    }

    @Test
    fun retry_offline_isQueuedAndUploadsOnReconnect() = runTest {
        val online = MutableStateFlow(false)
        val ledger = ledger(online)
        val source = LedgerDashboardSource(ledger)
        val failure = activityData(ledger.snapshot.value, now).event(failedSyncActivityId("ski-fuel"))

        assertEquals(SyncStatus.PENDING_INSERT, source.retry(failure))

        online.value = true
        runCurrent()
        advanceTimeBy(1_001)
        assertEquals(SyncStatus.SYNCED, ledger.snapshot.value.expenses.single { it.id == "ski-fuel" }.syncStatus)
    }

    @Test
    fun retry_refusesEventsThatAreNotTheUsersFailedUpload() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        val failure = activityData(ledger.snapshot.value, now).event(failedSyncActivityId("ski-fuel"))

        assertFailsWith("can't be retried") { source.retry(failure.copy(id = "exp:ski-fuel")) }
        // Right expense id, but claimed for a different group.
        val otherGroup = activityData(ledger.snapshot.value, now).events!!.first { it.group.group.id != "ski" }.group
        assertFailsWith("can't be retried") { source.retry(failure.copy(group = otherGroup)) }
    }

    @Test
    fun activityFeed_keepsARetriedFailureSoItCanShowSynced() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        var latest: ActivityData? = null
        backgroundScope.launch { source.activity.collect { latest = it } }
        runCurrent()
        val id = failedSyncActivityId("ski-fuel")

        source.retry(checkNotNull(latest).event(id))
        runCurrent()

        assertEquals(SyncStatus.SYNCED, checkNotNull(latest).event(id).syncStatus)
    }

    @Test
    fun markAllActivityRead_clearsHomesBellDot() = runTest {
        val ledger = ledger(MutableStateFlow(true))
        val source = LedgerDashboardSource(ledger)
        assertTrue(homeData(ledger.snapshot.value, now).hasUnreadActivity)

        source.markAllActivityRead()

        assertFalse(homeData(ledger.snapshot.value, now).hasUnreadActivity)
    }

    // --- End to end through Mohammed's ViewModels -------------------------------------------------

    @Test
    fun homeViewModel_paysInPlaceAndShowsPaidSettled() = runTest(mainDispatcher.dispatcher) {
        val source = LedgerDashboardSource(ledger(MutableStateFlow(true)))
        val vm = HomeViewModel(source.home, source)
        val skiId = (vm.state.value as HomeUiState.Success).data.payment("ski").id

        vm.togglePayments()
        vm.askPayment(skiId)
        vm.confirmPayment(skiId)
        assertEquals(InlineActionState.Working, (vm.state.value as HomeUiState.Success).paymentStates[skiId])

        // The moment the payment completes it is shown as paid: the card never disappears in between.
        advanceTimeBy(501)
        runCurrent()
        val state = vm.state.value as HomeUiState.Success
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), state.paymentStates[skiId])
        assertEquals("The paid card stays in its place, first", skiId, state.payments.first().id)
        assertEquals(41_250L - 28_600L, state.oweCents)
    }

    @Test
    fun activityViewModel_reminderPaidOnHomeCanNoLongerBePaid() = runTest(mainDispatcher.dispatcher) {
        val source = LedgerDashboardSource(ledger(MutableStateFlow(true)))
        val activity = ActivityViewModel(source.activity, source, source)
        val home = HomeViewModel(source.home, source)
        val reminder = (activity.state.value as ActivityUiState.Success).events.single { it.type == ActivityType.Reminder }
        assertTrue(reminder.canPay(me))

        home.togglePayments()
        val paymentId = checkNotNull(reminder.payment).id
        home.askPayment(paymentId)
        home.confirmPayment(paymentId)
        advanceTimeBy(1_001)
        runCurrent()

        val after = (activity.state.value as ActivityUiState.Success).events.single { it.id == reminder.id }
        assertFalse(after.canPay(me))
    }

    @Test
    fun activityViewModel_tryNowGoesFromFailedToSynced() = runTest(mainDispatcher.dispatcher) {
        val source = LedgerDashboardSource(ledger(MutableStateFlow(true)))
        val vm = ActivityViewModel(source.activity, source, source)
        val id = failedSyncActivityId("ski-fuel")

        vm.retry(id)
        advanceTimeBy(1_001)
        runCurrent()

        val state = vm.state.value as ActivityUiState.Success
        assertEquals(InlineActionState.Done(SyncStatus.SYNCED), state.actionFor(state.events.single { it.id == id }))
        assertEquals("Only the unpaid reminder still needs the user", 1, state.needsCount)
    }

    private suspend fun assertFailsWith(messagePart: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a failure containing \"$messagePart\"")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(messagePart))
        }
    }
}
