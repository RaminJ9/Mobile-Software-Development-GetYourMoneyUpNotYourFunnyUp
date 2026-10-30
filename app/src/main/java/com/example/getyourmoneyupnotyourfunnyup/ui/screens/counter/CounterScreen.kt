package com.example.getyourmoneyupnotyourfunnyup.ui.screens.counter

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme

/**
 * Counter Screen: Displays the authenticated user's profile and counter, and persists
 * counter increments against `/users/{uid}` in Cloud Firestore & local SQLite.
 */
@Composable
fun CounterScreen(
    modifier: Modifier = Modifier,
    user: User,
    statusMessage: String,
    onIncrementClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val syncLabel = when (user.syncStatus) {
        SyncStatus.SYNCED -> "Synced / Success"
        SyncStatus.PENDING_INSERT,
        SyncStatus.PENDING_UPDATE,
        SyncStatus.PENDING_DELETE -> "Pending / Syncing..."
        SyncStatus.FAILED -> "Failed / Offline (Saved locally)"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hello ${user.displayName}!",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Count: ${user.count}",
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sync State: $syncLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (statusMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onIncrementClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Click Me")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}

@Preview(showBackground = true, name = "Counter Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Counter Dark"
)
@Composable
private fun CounterScreenPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface {
            CounterScreen(
                user = User(
                    id = "usr_123",
                    email = "alex@example.com",
                    displayName = "Alex",
                    count = 12,
                    syncStatus = SyncStatus.SYNCED
                ),
                statusMessage = "Saved to /users/usr_123",
                onIncrementClick = {},
                onLogoutClick = {}
            )
        }
    }
}
