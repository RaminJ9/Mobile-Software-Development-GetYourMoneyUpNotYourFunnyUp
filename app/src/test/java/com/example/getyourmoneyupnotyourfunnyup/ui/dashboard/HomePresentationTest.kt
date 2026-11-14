package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HomePresentationTest {
    private val zone = TimeZone.getTimeZone("Europe/Copenhagen")
    private fun time(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(zone).apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    @Test fun weekStartsMondayAndEndsBeforeNextMonday() {
        val bounds = periodBounds(SpendPeriod.Week, time(2026, 11, 8), zone)
        assertEquals(time(2026, 11, 2, 0), bounds.first)
        assertEquals(time(2026, 11, 9, 0) - 1, bounds.last)
    }

    @Test fun todayHandlesDaylightSavingWithoutAssumingTwentyFourHours() {
        val bounds = periodBounds(SpendPeriod.Today, time(2026, 10, 25), zone)
        assertEquals(25 * 60 * 60 * 1000L, bounds.last - bounds.first + 1)
    }

    @Test fun favouritesAreFilteredAndAllIsAlphabetical() {
        val groups = listOf(
            DashboardGroup(Group("b", "Beta", createdBy = "u"), 0, lastActivityAt = 2),
            DashboardGroup(Group("a", "Alpha", createdBy = "u"), 1, isFavourite = true, lastActivityAt = 1)
        )
        assertEquals(listOf("b", "a"), filterGroups(groups, GroupFilter.Recent).map { it.group.id })
        assertEquals(listOf("a"), filterGroups(groups, GroupFilter.Favourites).map { it.group.id })
        assertEquals(listOf("a", "b"), filterGroups(groups, GroupFilter.All).map { it.group.id })
    }

    @Test fun donutExcludesOtherUsersCurrenciesPendingAndDuplicateRecords() {
        val now = System.currentTimeMillis()
        val payment = PaidOutPayment(Settlement("p", "g", "u", "c", 12345, "DKK", "Cash", createdAt = now), "Creditor")
        val data = HomeData(User("u", "u@test", "User"), listOf(DashboardGroup(Group("g", "Group", createdBy = "u"), 0)), paidOutPayments = listOf(
            payment, payment,
            payment.copy(settlement = payment.settlement.copy(id = "other", debtorId = "someone")),
            payment.copy(settlement = payment.settlement.copy(id = "euro", currency = "EUR")),
            payment.copy(settlement = payment.settlement.copy(id = "pending", status = SettlementStatus.PENDING))
        ), nowMillis = now)
        val slices = spendSlices(data, SpendPeriod.Today)
        assertEquals(12345L, slices.single().amountCents)
        assertEquals(360f, slices.single().sweepDegrees)
        assertNull(hitTestSlice(0.5f, 0.5f, slices, null))
        assertEquals("g", hitTestSlice(0.9f, 0.5f, slices, null))
        assertNull(hitTestSlice(1f, 0.5f, slices, null))
    }

    @Test fun emptySpendDoesNotProduceInvalidSlices() {
        val data = HomeData(User("u", "u@test", "User"), emptyList(), paidOutPayments = emptyList())
        assertTrue(spendSlices(data, SpendPeriod.Month).isEmpty())
        assertNull(hitTestSlice(0.9f, 0.5f, emptyList(), null))
    }
}
