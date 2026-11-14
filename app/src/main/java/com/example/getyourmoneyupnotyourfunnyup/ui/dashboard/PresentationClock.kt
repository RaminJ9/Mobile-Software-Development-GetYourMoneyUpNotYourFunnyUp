package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun presentationClock(nowMillis: () -> Long = System::currentTimeMillis): Flow<Long> = flow {
    while (true) {
        val now = nowMillis()
        emit(now)
        delay(60_000L - Math.floorMod(now, 60_000L))
    }
}
