package com.example.getyourmoneyupnotyourfunnyup.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Human date labels used across the design: "Today", "Yesterday", "Sun 1 Nov", "12 Oct".
 * Uses java.util APIs so it works on minSdk 24 without desugaring.
 */
object DateLabels {
    private val locale: Locale = Locale.UK
    private const val DAY_MS = 24L * 60 * 60 * 1000

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
