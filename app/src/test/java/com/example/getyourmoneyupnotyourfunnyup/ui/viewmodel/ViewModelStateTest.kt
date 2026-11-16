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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun group_removingAMemberWithABalanceAsksFirst() = runTest(mainDispatcher.dispatcher) {
        val ledger = ledger()
        val vm = GroupViewModel(ledger, "kol")
        vm.onAction(GroupAction.SelectTab(GroupTab.Members))
        vm.onAction(GroupAction.AskRemoveMember("ida"))
        val content = vm.uiState.value as GroupUiState.Content
        assertNotNull(content.members.first { it.id == "ida" }.warning)

        vm.onAction(GroupAction.KeepMember)
        assertTrue((vm.uiState.value as GroupUiState.Content).members.none { it.warning != null })

        // Emil is settled, so removing him needs no confirmation.
        vm.onAction(GroupAction.AskRemoveMember("emil"))
        assertTrue((vm.uiState.value as GroupUiState.Content).members.none { it.id == "emil" })
    }

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

    @Test
    fun scan_savingCreatesAnExpenseThatSharesTheReceiptTotal() = runTest(mainDispatcher.dispatcher) {
        val ledger = ledger()
        val vm = ScanViewModel(ledger, "kol")
        vm.onAction(ScanAction.PermissionChecked(granted = true))
        assertEquals(ScanStep.Camera, vm.uiState.value.step)

        vm.onAction(ScanAction.Shoot)
        assertEquals(ScanStep.Reading, vm.uiState.value.step)
        advanceTimeBy(ScanViewModel.READING_MS + 1)
        runCurrent()
        assertEquals(ScanStep.Review, vm.uiState.value.step)
        assertTrue("misread price is flagged", vm.uiState.value.receiptTotalCents != vm.uiState.value.totalCents)

        val low = vm.uiState.value.items.first { it.lowConfidence }
        vm.onAction(ScanAction.ItemPriceChanged(low.id, DemoLedger.RECEIPT_CORRECT_LOW_PRICE))
        assertEquals(DemoLedger.RECEIPT_TOTAL_CENTS, vm.uiState.value.totalCents)

        vm.onAction(ScanAction.GoToSplit)
        assertEquals(ScanStep.Split, vm.uiState.value.step)
        assertNotNull("Mads's no-alcohol rule is explained", vm.uiState.value.ruleNotice)

        vm.onAction(ScanAction.Save)
        assertEquals(ScanStep.Saved, vm.uiState.value.step)
        val saved = ledger.snapshot.value.expenses.first()
        assertEquals("kol", saved.groupId)
        assertEquals(DemoLedger.RECEIPT_TOTAL_CENTS, saved.amountCents)
        assertEquals(saved.amountCents, saved.splits.sumOf { it.amountOwedCents })
        assertFalse(vm.uiState.value.saved!!.synced)

        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(vm.uiState.value.saved!!.synced)
    }

    @Test
    fun createGroup_needsANameAndReportsUnknownContacts() = runTest(mainDispatcher.dispatcher) {
        val vm = CreateGroupViewModel(ledger())
        assertFalse(vm.uiState.value.canCreate)

        vm.onAction(CreateGroupAction.QueryChanged("anna.b@mail.dk"))
        vm.onAction(CreateGroupAction.SubmitQuery)
        assertEquals("anna.b@mail.dk", vm.uiState.value.notFoundContact)
        vm.onAction(CreateGroupAction.Invite)
        assertTrue(vm.uiState.value.members.any { it.kind == MemberChipUi.Kind.Invited })

        vm.onAction(CreateGroupAction.NameChanged("Roskilde 2027"))
        vm.onAction(CreateGroupAction.Create)
        assertNotNull(vm.uiState.value.createdGroupId)
    }
}
