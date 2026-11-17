package com.example.getyourmoneyupnotyourfunnyup.debug

import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*

internal fun sampleHome(): HomeData {
    val now = System.currentTimeMillis()
    val user = User(
        id = "sample-you",
        email = "victor@example.dk",
        displayName = "Victor Munk",
        phoneNumber = "+45 12 34 56 78",
        dietaryRules = DietaryRules(excludeAlcohol = true, excludeMeat = false)
    )
    val groups = listOf("Ski trip Åre", "Friday Bar crew", "Padel Tuesdays", "Kollegiet 4. sal")
        .mapIndexed { index, name ->
            val id = "sample-$index"
            DashboardGroup(
                Group(id, name, currency = "DKK", createdBy = user.id,
                    members = listOf(GroupMember(id, user.id), GroupMember(id, "sample-mads"))),
                colourIndex = index,
                isFavourite = index < 2,
                lastActivityAt = now - index * 86400000L,
                pendingExpenseCount = if (index == 3) 1 else 0
            )
        }
    val amounts = listOf(-28600L, -9450L, -3200L, 14500L)
    val payments = groups.take(3).mapIndexed { index, group ->
        OutstandingPayment("sample-payment-$index", group.group.id, "Mads", now - 172800000L,
            DebtTransfer(user.id, "sample-mads", -amounts[index], "DKK"))
    }
    val paid = groups.mapIndexed { index, group ->
        PaidOutPayment(Settlement("sample-paid-$index", group.group.id, user.id, "sample-mads",
            (index + 1) * 16000L, "DKK", "Simulated", createdAt = now - index * 86400000L), "Mads")
    }
    return HomeData(user, groups, FinancialSummary(groups.mapIndexed { index, group ->
        group.group.id to MemberBalance(user.id, amounts[index], "DKK")
    }.toMap(), payments), paid, hasUnreadActivity = true, nowMillis = now)
}

internal fun sampleActivity(home: HomeData): ActivityData {
    val now = home.nowMillis
    val mads = User("sample-mads", "mads@example.invalid", "Mads")
    val ski = home.groups[0]
    val bar = home.groups[1]
    return ActivityData(home.user.id, listOf(
        ActivityEvent("sample-reminder", ActivityType.Reminder, mads, 1, "reminded you to pay for the ski trip",
            ski, now, 28600, direction = AmountDirection.Owed, unread = true,
            payment = home.finances!!.outstandingPayments.first()),
        ActivityEvent("sample-expense", ActivityType.ExpenseAdded, mads, 1, "added Bar tab · your share",
            bar, now - 1800000L, 9450, direction = AmountDirection.Owed, unread = true),
        ActivityEvent("sample-failed", ActivityType.SyncFailure, null, 0, "couldn't sync yet – retrying automatically",
            ski, now - 3600000L, unread = true, syncStatus = SyncStatus.FAILED, expenseTitle = "Fuel for the drive"),
        ActivityEvent("sample-changed", ActivityType.ExpenseChanged, mads, 1, "changed Lift passes",
            ski, now - 86400000L),
        ActivityEvent("sample-paid", ActivityType.Payment, home.user, 0, "paid Mads", ski,
            now - 3 * 86400000L, 120000, direction = AmountDirection.Settled),
        ActivityEvent("sample-received", ActivityType.Payment, mads, 1, "paid you", bar,
            now - 3 * 86400000L, 6400, direction = AmountDirection.Received)
    ), now)
}
