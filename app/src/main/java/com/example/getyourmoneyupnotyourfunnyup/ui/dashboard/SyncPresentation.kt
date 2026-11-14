package com.example.getyourmoneyupnotyourfunnyup.ui.dashboard

import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState

data class SyncPresentation(
    val connection: ConnectionState = ConnectionState.Unknown,
    val savedAt: Long? = null,
    val pendingCount: Int? = null,
    val failureMessage: String? = null,
    val acknowledgedRecovery: String? = null
) {
    init { require(pendingCount == null || pendingCount >= 0) }
    val hasFeedback: Boolean get() = connection == ConnectionState.Offline || (pendingCount ?: 0) > 0 ||
        failureMessage != null || acknowledgedRecovery != null
}
