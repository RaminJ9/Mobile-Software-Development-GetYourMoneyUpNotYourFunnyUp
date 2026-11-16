package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ActivityPresentationTest {
    private val group = DashboardGroup(Group("g", "Group", createdBy = "u"), 0)
    private fun event(type: ActivityType, time: Long) = ActivityEvent(type.name, type, null, 0, "Description", group, time)

    @Test fun expenseAndPaymentFiltersIncludeExactlyTheDocumentedTypes() {
        val events = ActivityType.entries.mapIndexed { index, type -> event(type, index.toLong()) }
        assertEquals(listOf(ActivityType.SyncFailure, ActivityType.ExpenseChanged, ActivityType.ExpenseAdded), filterActivities(events, "u", ActivityFilter.Expenses, emptyMap()).map { it.type })
        assertEquals(listOf(ActivityType.Reminder, ActivityType.Payment), filterActivities(events, "u", ActivityFilter.Payments, emptyMap()).map { it.type })
    }

    @Test fun needsYouDoesNotTreatUnreadExpensesAsRequiredActions() {
        val expense = event(ActivityType.ExpenseAdded, 1).copy(unread = true)
        val payment = OutstandingPayment("p", "g", "Mads", 1, DebtTransfer("u", "m", 100, "DKK"))
        val reminder = event(ActivityType.Reminder, 2).copy(payment = payment)
        val failure = event(ActivityType.SyncFailure, 3).copy(syncStatus = SyncStatus.FAILED)
        val events = listOf(expense, reminder, failure)
        assertEquals(listOf(failure, reminder), filterActivities(events, "u", ActivityFilter.NeedsYou, emptyMap()))
        assertEquals(listOf(failure), filterActivities(events, "u", ActivityFilter.NeedsYou, mapOf(reminder.id to InlineActionState.Done())))
        assertEquals(listOf(failure), filterActivities(events, "someone", ActivityFilter.NeedsYou, emptyMap()))
    }

    @Test fun duplicateEventIdsDoNotProduceDuplicateCards() {
        val expense = event(ActivityType.ExpenseAdded, 1)
        assertEquals(listOf(expense), filterActivities(listOf(expense, expense), "u", ActivityFilter.All, emptyMap()))
    }

    @Test fun dateSectionsUseCalendarDaysAcrossMonthBoundary() {
        val now = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 1, 12, 0) }
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val earlier = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -2) }
        assertEquals("Today", activityDay(now.timeInMillis, now.timeInMillis))
        assertEquals("Yesterday", activityDay(yesterday.timeInMillis, now.timeInMillis))
        assertEquals("Earlier", activityDay(earlier.timeInMillis, now.timeInMillis))
    }
}
