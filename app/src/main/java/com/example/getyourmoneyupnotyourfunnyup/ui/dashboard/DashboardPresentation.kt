package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.hypot

enum class GroupFilter(val label: String) { Recent("Recent"), Favourites("Favourites"), All("All") }

enum class SpendPeriod(val label: String, val shortLabel: String, val caption: String) {
    Today("Today", "Today", "today"),
    Week("This week", "Week", "this week"),
    Month("This month", "Month", "this month"),
    Year("This year", "Year", "this year")
}

data class SpendSlice(
    val groupId: String,
    val amountCents: Long,
    val lastPayment: PaidOutPayment,
    val startDegrees: Float,
    val sweepDegrees: Float
)

fun filterGroups(groups: List<DashboardGroup>, filter: GroupFilter): List<DashboardGroup> =
when (filter) {
    GroupFilter.Recent -> groups.sortedByDescending { it.lastActivityAt }
    GroupFilter.Favourites -> groups.filter { it.isFavourite }.sortedByDescending { it.lastActivityAt }
    GroupFilter.All -> groups.sortedBy { it.group.name.lowercase(Locale.ROOT) }
}

fun periodBounds(
    period: SpendPeriod,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): LongRange {
    val start = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        when (period) {
            SpendPeriod.Today -> Unit
            SpendPeriod.Week -> add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
            SpendPeriod.Month -> set(Calendar.DAY_OF_MONTH, 1)
            SpendPeriod.Year -> { set(Calendar.DAY_OF_YEAR, 1) }
        }
    }
    val end = (start.clone() as Calendar).apply {
        when (period) {
            SpendPeriod.Today -> add(Calendar.DAY_OF_MONTH, 1)
            SpendPeriod.Week -> add(Calendar.DAY_OF_MONTH, 7)
            SpendPeriod.Month -> add(Calendar.MONTH, 1)
            SpendPeriod.Year -> add(Calendar.YEAR, 1)
        }
    }
    return start.timeInMillis until end.timeInMillis
}

fun spendSlices(data: HomeData, period: SpendPeriod): List<SpendSlice> {
    val bounds = periodBounds(period, data.nowMillis)
    val groupIds = data.groups.map { it.group.id }.toSet()
    val payments = data.paidOutPayments.orEmpty().filter {
        val payment = it.settlement
        payment.debtorId == data.user.id && payment.currency == "DKK" &&
        payment.status == SettlementStatus.COMPLETED && payment.amountCents > 0 &&
        payment.groupId in groupIds && payment.createdAt in bounds && payment.createdAt <= data.nowMillis
    }.distinctBy { it.settlement.id }
    val byGroup = payments.groupBy { it.settlement.groupId }
    val total = payments.sumOf { it.settlement.amountCents }.toDouble()
    var start = 0f
    return byGroup.map { (id, entries) ->
        val amount = entries.sumOf { it.settlement.amountCents }
        val sweep = (amount / total * 360.0).toFloat()
        SpendSlice(id, amount, entries.maxBy { it.settlement.createdAt }, start, sweep).also { start += sweep }
    }
}

fun hitTestSlice(x: Float, y: Float, slices: List<SpendSlice>, selectedGroupId: String?): String? {
    val radius = hypot(x - 0.5f, y - 0.5f)
    val angle = ((Math.toDegrees(atan2((y - 0.5f).toDouble(), (x - 0.5f).toDouble())) + 450) % 360).toFloat()
    return slices.firstOrNull {
        val outer = if (it.groupId == selectedGroupId) 69f / 140f else 62f / 140f
        val halfGap = minOf(2f, it.sweepDegrees / 3f) / 2f
        radius in (40f / 140f)..outer && angle >= it.startDegrees + halfGap &&
        angle <= it.startDegrees + it.sweepDegrees - halfGap
    }?.groupId
}

fun dateText(millis: Long, pattern: String): String =
SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(millis))

fun activityDay(millis: Long, nowMillis: Long): String {
    if (millis in periodBounds(SpendPeriod.Today, nowMillis)) return "Today"
    val yesterday = Calendar.getInstance().apply { timeInMillis = nowMillis; add(Calendar.DAY_OF_MONTH, -1) }
    return if (millis in periodBounds(SpendPeriod.Today, yesterday.timeInMillis)) "Yesterday" else "Earlier"
}

fun relativeDate(millis: Long, nowMillis: Long): String = when (val day = activityDay(millis, nowMillis)) {
    "Earlier" -> dateText(millis, "EEE d MMM")
    else -> day
}
