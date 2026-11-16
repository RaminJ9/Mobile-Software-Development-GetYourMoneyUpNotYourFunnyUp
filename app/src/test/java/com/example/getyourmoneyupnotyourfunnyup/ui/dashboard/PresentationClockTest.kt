package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PresentationClockTest {
    @Test fun clockCrossesMidnightWithoutWaitingForANewDataSnapshot() = runTest {
        val initial = Calendar.getInstance().apply {
            clear(); set(2026, Calendar.NOVEMBER, 30, 23, 59, 59); set(Calendar.MILLISECOND, 900)
        }.timeInMillis
        val values = async { presentationClock { initial + testScheduler.currentTime }.take(2).toList() }.await()
        assertEquals(initial + 100, values.last())
        assertEquals("Yesterday", activityDay(initial, values.last()))
        assertNotEquals(periodBounds(SpendPeriod.Month, values.first()), periodBounds(SpendPeriod.Month, values.last()))
    }

    @Test fun resumingCollectionReadsTheCurrentTimeImmediately() = runTest {
        var now = 100L
        val clock = presentationClock { now }
        assertEquals(100L, clock.first())
        now = 86400100L
        assertEquals(now, clock.first())
    }
}
