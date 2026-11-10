package com.example.getyourmoneyupnotyourfunnyup.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Reporting periods for the "Paid out" chart on Home. */
enum class Period(val short: String, val menuLabel: String, val phrase: String) {
    TODAY("Today", "Today", "today"),
    WEEK("Week", "This week", "this week"),
    MONTH("Month", "This month", "this month"),
    YEAR("Year", "This year", "this year")
}

/**
 * Human date labels used across the design: "Today", "Yesterday", "Sun 1 Nov", "12 Oct".
 * Uses java.util APIs so it works on minSdk 24 without desugaring.
 */
object DateLabels {
    private val locale: Locale = Locale.UK
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** "Tuesday 3 November" */
    fun longDate(millis: Long): String = SimpleDateFormat("EEEE d MMMM", locale).format(Date(millis))

    /** "10:12" */
    fun time(millis: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(millis))

    /** "Today", "Yesterday", "Sun 1 Nov" (within a week) or "12 Oct". */
    fun relativeDay(millis: Long, now: Long): String {
        val days = daysBetween(millis, now)
        return when {
            days <= 0 -> "Today"
            days == 1 -> "Yesterday"
            days < 7 -> SimpleDateFormat("EEE d MMM", locale).format(Date(millis))
            else -> SimpleDateFormat("d MMM", locale).format(Date(millis))
        }
    }

    /** Activity feed section: "Today", "Yesterday" or "Earlier". */
    fun section(millis: Long, now: Long): String = when (daysBetween(millis, now)) {
        0, -1 -> "Today"
        1 -> "Yesterday"
        else -> "Earlier"
    }

    /** "Since today", "Since yesterday", "Since Sun 1 Nov". */
    fun since(millis: Long, now: Long): String = when (val label = relativeDay(millis, now)) {
        "Today", "Yesterday" -> "Since ${label.lowercase(locale)}"
        else -> "Since $label"
    }

    /** True when [millis] falls inside the calendar [period] that contains [now]. */
    fun isInPeriod(millis: Long, period: Period, now: Long): Boolean {
        if (millis > now) return false
        val start = startOfDay(now).apply {
            when (period) {
                Period.TODAY -> Unit
                Period.WEEK -> {
                    firstDayOfWeek = Calendar.MONDAY
                    val offset = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                    add(Calendar.DAY_OF_MONTH, -offset)
                }
                Period.MONTH -> set(Calendar.DAY_OF_MONTH, 1)
                Period.YEAR -> set(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return millis >= start.timeInMillis
    }

    /** Whole calendar days from [from] to [to] (0 = same day). */
    fun daysBetween(from: Long, to: Long): Int {
        val a = startOfDay(from).timeInMillis
        val b = startOfDay(to).timeInMillis
        return Math.round((b - a).toDouble() / DAY_MS).toInt()
    }

    /** [now] shifted back by [days] calendar days, at [hour]:[minute]. Used to build demo data. */
    fun daysAgo(now: Long, days: Int, hour: Int, minute: Int = 0): Long =
        startOfDay(now).apply {
            add(Calendar.DAY_OF_MONTH, -days)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis.coerceAtMost(now)

    private fun startOfDay(millis: Long): Calendar = Calendar.getInstance(locale).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
