package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.model.User
import org.junit.Assert.*
import org.junit.Test

class SyncPresentationTest {
    @Test fun noMetadataDoesNotInventFeedbackOrTime() {
        val state = SyncPresentation()
        assertFalse(state.hasFeedback)
        assertNull(state.savedAt)
        assertNull(state.acknowledgedRecovery)
    }

    @Test fun offlineAndPendingStatesAreVisibleWithoutSuccess() {
        val state = SyncPresentation(connection = ConnectionState.Offline, pendingCount = 2)
        assertTrue(state.hasFeedback)
        assertNull(state.acknowledgedRecovery)
    }

    @Test fun legacyTimestampRemainsAnExplicitOfflineSnapshot() {
        val data = HomeData(User("u", "test@example.invalid", "Test"), emptyList(), offlineSavedAt = 123)
        assertEquals(ConnectionState.Offline, data.displaySync.connection)
        assertEquals(123L, data.displaySync.savedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePendingCountIsRejected() { SyncPresentation(pendingCount = -1) }
}
