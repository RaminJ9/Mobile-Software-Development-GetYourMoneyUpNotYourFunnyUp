package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.myDebts
import com.example.getyourmoneyupnotyourfunnyup.model.myNet
import com.example.getyourmoneyupnotyourfunnyup.model.settlePlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reliability QA: writes are never blocked by the network, are queued while offline and are
 * uploaded automatically once the device is back online.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryLedgerRepositoryTest {

    private val now = 1_762_800_000_000L // fixed clock for deterministic labels

    private fun TestScope.repository(online: MutableStateFlow<Boolean>) = InMemoryLedgerRepository(
        scope = backgroundScope,
        isOnline = online,
        clock = { now },
        initial = DemoLedger.create(now),
        syncDelayMs = 1_000,
        paymentDelayMs = 500,
    )

    private fun draft(groupId: String = "pad") = ExpenseDraft(
        groupId = groupId, title = "Court booking", payerId = DemoLedger.ME, amountCents = 40_000,
        shares = mapOf(DemoLedger.ME to 10_000L, "jonas" to 10_000L, "mads" to 10_000L, "emil" to 10_000L),
        hasReceipt = false,
    )

    @Test
    fun offlineExpense_isSavedLocallyAndStaysPendingUntilOnline() = runTest {
        val online = MutableStateFlow(false)
        val repo = repository(online)
        runCurrent()

        val id = repo.addExpense(draft())
        // Saved immediately — the user is never blocked by the missing connection.
        assertEquals(SyncStatus.PENDING_INSERT, repo.snapshot.value.expenses.first { it.id == id }.syncStatus)

        advanceTimeBy(60_000)
        assertEquals(SyncStatus.PENDING_INSERT, repo.snapshot.value.expenses.first { it.id == id }.syncStatus)

        online.value = true
        runCurrent()
        advanceTimeBy(1_001)
        assertEquals(SyncStatus.SYNCED, repo.snapshot.value.expenses.first { it.id == id }.syncStatus)
    }

    @Test
    fun offlineExpense_alreadyCountsInBalances() = runTest {
        val repo = repository(MutableStateFlow(false))
        val before = repo.snapshot.value.myNet("pad")
        repo.addExpense(draft())
        assertEquals(before + 30_000, repo.snapshot.value.myNet("pad"))
    }

    @Test
    fun failedUpload_isRetriedAutomaticallyWhenTheConnectionReturns() = runTest {
        val online = MutableStateFlow(false)
        val repo = repository(online)
        runCurrent()
        assertEquals(SyncStatus.FAILED, repo.snapshot.value.expenses.first { it.id == "ski-fuel" }.syncStatus)

        online.value = true
        runCurrent()
        assertEquals(SyncStatus.PENDING_INSERT, repo.snapshot.value.expenses.first { it.id == "ski-fuel" }.syncStatus)
        advanceTimeBy(1_001)
        assertEquals(SyncStatus.SYNCED, repo.snapshot.value.expenses.first { it.id == "ski-fuel" }.syncStatus)
    }

    @Test
    fun tryNow_requeuesAFailedUploadWhileOnline() = runTest {
        val repo = repository(MutableStateFlow(true))
        runCurrent()
        assertEquals(SyncStatus.FAILED, repo.snapshot.value.expenses.first { it.id == "ski-fuel" }.syncStatus)

        repo.retrySync("ski-fuel")
        advanceTimeBy(1_001)
        assertEquals(SyncStatus.SYNCED, repo.snapshot.value.expenses.first { it.id == "ski-fuel" }.syncStatus)
    }

    @Test
    fun successfulSync_emitsAnEventForTheUi() = runTest {
        val online = MutableStateFlow(true)
        val repo = repository(online)
        val events = mutableListOf<SyncEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.syncEvents.collect { events += it } }
        runCurrent()
        advanceTimeBy(1_001) // the seeded pending receipt uploads
        assertTrue(events.any { it is SyncEvent.Synced })
    }

    @Test
    fun payingADebt_isPendingThenCompletedAndClearsTheBalance() = runTest {
        val repo = repository(MutableStateFlow(false))
        val debt = repo.snapshot.value.myDebts().first { it.groupId == "ski" }
        assertEquals(28_600L, debt.amountCents)

        val id = repo.payDebt("ski", debt.creditorId, debt.amountCents)
        assertEquals(SettlementStatus.PENDING, repo.snapshot.value.settlements.first { it.id == id }.status)

        advanceTimeBy(501)
        assertEquals(SettlementStatus.COMPLETED, repo.snapshot.value.settlements.first { it.id == id }.status)
        assertEquals(0L, repo.snapshot.value.myNet("ski"))
        assertFalse(repo.snapshot.value.settlePlan("ski").any { it.debtorId == DemoLedger.ME })
    }

    @Test
    fun removingAnExpense_isASoftDeleteThatSyncsLater() = runTest {
        val repo = repository(MutableStateFlow(false))
        repo.removeExpense("kol-pizza")
        val removed = repo.snapshot.value.expenses.first { it.id == "kol-pizza" }
        assertTrue(removed.isDeleted)
        assertEquals(SyncStatus.PENDING_DELETE, removed.syncStatus)
    }

    @Test
    fun findPerson_matchesEmailPhoneAndName() = runTest {
        val repo = repository(MutableStateFlow(false))
        assertEquals("mads", repo.findPerson("MADS.H@mail.dk")?.id)
        assertEquals("jonas", repo.findPerson("22814017")?.id)
        assertEquals("ida", repo.findPerson("ida")?.id)
        assertEquals(null, repo.findPerson("anna.b@mail.dk"))
    }

    @Test
    fun createGroup_putsTheUserInAsAdminAndKeepsInvites() = runTest {
        val repo = repository(MutableStateFlow(true))
        val id = repo.createGroup("Roskilde 2027", "Tickets", 2, listOf("mads", "ida"), listOf("anna.b@mail.dk"))
        val group = repo.snapshot.value.group(id)
        assertNotNull(group)
        assertEquals(listOf(DemoLedger.ME, "mads", "ida"), group!!.members.map { it.userId })
        assertEquals(SyncStatus.PENDING_INSERT, group.syncStatus)
        assertTrue(repo.snapshot.value.invites.any { it.groupId == id && it.contact == "anna.b@mail.dk" })
    }
}
