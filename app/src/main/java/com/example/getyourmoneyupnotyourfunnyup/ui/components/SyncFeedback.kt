package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectivityObserver
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncPresentation
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.dateText
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.presentationClock
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*

@Composable
fun rememberConnectionState(): ConnectionState {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { ConnectivityObserver(context).observe() }
    val state by flow.collectAsStateWithLifecycle(ConnectionState.Unknown)
    return state
}

@Composable
fun rememberPresentationTime(): Long {
    val clock = remember { presentationClock() }
    val now by clock.collectAsStateWithLifecycle(System.currentTimeMillis())
    return now
}

@Composable
fun SyncFeedback(sync: SyncPresentation, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
        if (sync.connection == ConnectionState.Offline) {
            OfflineBanner(sync.savedAt?.let { dateText(it, "HH:mm") })
        }
        sync.pendingCount?.takeIf { it > 0 }?.let { count ->
            FeedbackMessage("$count ${if (count == 1) "change" else "changes"} waiting to sync", AppColors.Amber100, AppColors.Amber800)
        }
        sync.failureMessage?.let { FeedbackMessage(it, AppColors.Red50, AppColors.Red800) }
        sync.acknowledgedRecovery?.let { FeedbackMessage(it, AppColors.Green100, AppColors.Green700) }
    }
}

@Composable
private fun FeedbackMessage(message: String, background: Color, foreground: Color) {
    Surface(color = background, shape = AppShapes.SecondaryButton) {
        Text(message, Modifier.padding(AppSpacing.Space12), style = AppTypography.Caption, color = foreground)
    }
}

@Preview(name = "Offline feedback · light")
@Preview(name = "Offline feedback · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SyncFeedbackPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        SyncFeedback(SyncPresentation(connection = ConnectionState.Offline, pendingCount = 2))
    }
}
