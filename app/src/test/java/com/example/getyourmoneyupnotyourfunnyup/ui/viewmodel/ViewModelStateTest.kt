package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.data.repository.InMemoryLedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = System.currentTimeMillis()
    private val online = MutableStateFlow(true)

    private fun TestScope.ledger() = InMemoryLedgerRepository(
        scope = backgroundScope, isOnline = online, clock = { now }, initial = DemoLedger.create(now),
        syncDelayMs = 1_000, paymentDelayMs = 500,
    )

    @Test
    fun group_heroTurnsFromOweToAllSquareAfterPaying() = runTest(mainDispatcher.dispatcher) {
        val vm = GroupViewModel(ledger(), "ski")
        assertEquals(GroupHero.Owe("Mads", 28_600L, PayState.Idle), (vm.uiState.value as GroupUiState.Content).hero)

        vm.onAction(GroupAction.AskPay)
        assertEquals(PayState.Confirm, ((vm.uiState.value as GroupUiState.Content).hero as GroupHero.Owe).state)
        vm.onAction(GroupAction.ConfirmPay)
        assertEquals(PayState.Pending, ((vm.uiState.value as GroupUiState.Content).hero as GroupHero.Owe).state)

        advanceTimeBy(501)
        runCurrent()
        val hero = (vm.uiState.value as GroupUiState.Content).hero
        assertTrue(hero is GroupHero.Settled)
        assertEquals("Paid Mads 286,00 kr just now", (hero as GroupHero.Settled).subtitle)
    }

    @Test
    fun group_failedExpenseCanBeRetried() = runTest(mainDispatcher.dispatcher) {
        val vm = GroupViewModel(ledger(), "ski")
        val failed = (vm.uiState.value as GroupUiState.Content).ledgerRows.first { it.id == "ski-fuel" }
        assertEquals(com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus.FAILED, failed.syncStatus)

        vm.onAction(GroupAction.RetrySync("ski-fuel"))
        advanceTimeBy(1_001)
        runCurrent()
        val synced = (vm.uiState.value as GroupUiState.Content).ledgerRows.first { it.id == "ski-fuel" }
        assertEquals(com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus.SYNCED, synced.syncStatus)
    }
}
