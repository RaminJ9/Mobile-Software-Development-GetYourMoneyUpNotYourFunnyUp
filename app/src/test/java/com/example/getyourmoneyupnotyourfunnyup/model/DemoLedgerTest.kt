package com.example.getyourmoneyupnotyourfunnyup.model

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo data is derived (not hard-coded), so check it still tells the story in the hi-fi. */
class DemoLedgerTest {
    private val now = System.currentTimeMillis()
    private val ledger = DemoLedger.create(now)

    @Test
    fun everyExpenseSplitsExactly() {
        ledger.expenses.forEach { e -> assertEquals(e.title, e.amountCents, e.splits.sumOf { it.amountOwedCents }) }
    }

    @Test
    fun everyGroupBalancesToZero() {
        ledger.groups.forEach { g -> assertEquals(g.name, 0L, ledger.netBalances(g.id).values.sum()) }
    }

    @Test
    fun userOwesInThreeGroupsAndIsOwedInTwo() {
        val debts = ledger.myDebts().associate { ledger.group(it.groupId)!!.name to it.amountCents }
        assertEquals(mapOf("Ski trip Åre" to 28_600L, "Friday Bar crew" to 9_450L, "Padel Tuesdays" to 3_200L), debts)
        assertEquals(14_500L, ledger.myNet("kol"))
        assertEquals(6_000L, ledger.myNet("mob"))
        assertEquals(20_500L, ledger.totalOwedToMe())
    }

    @Test
    fun kollegietSettlesWithThreePayments() {
        val plan = ledger.settlePlan("kol").map { Triple(it.debtorId, it.creditorId, it.amountCents) }
        assertEquals(
            listOf(Triple("ida", DemoLedger.ME, 8_500L), Triple("jonas", DemoLedger.ME, 6_000L), Triple("mads", "sofie", 4_000L)),
            plan,
        )
    }

    @Test
    fun feedHasAnUnpaidReminderAndAFailedSync() {
        val feed = ledger.activityFeed(now)
        assertTrue(feed.any { it.type == ActivityType.REMINDER && it.amountCents == 28_600L })
        assertTrue(feed.any { it.type == ActivityType.FAILED && it.expenseId == "ski-fuel" })
    }
}
