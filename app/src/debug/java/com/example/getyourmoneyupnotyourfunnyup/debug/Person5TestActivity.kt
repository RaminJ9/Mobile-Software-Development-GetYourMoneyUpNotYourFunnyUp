package com.example.getyourmoneyupnotyourfunnyup.debug

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.activity.ActivityRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.HomeRoute
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ActivityViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class Person5TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = ViewModelProvider(this, factory { SampleSession() })[SampleSession::class.java]
        val home = ViewModelProvider(this, factory {
            HomeViewModel(session.home, SettlementActions { session.simulate() })
        })[HomeViewModel::class.java]
        val activity = ViewModelProvider(this, factory {
            ActivityViewModel(session.activity, SettlementActions { session.simulate() },
                SyncRetryActions { session.simulate() })
        })[ActivityViewModel::class.java]
        val destination: (String) -> Unit = { name ->
            Toast.makeText(this, "Sample navigation: $name (not connected)", Toast.LENGTH_SHORT).show()
        }
        setContent {
            val homeData by session.home.collectAsState()
            GetYourMoneyUpNotYourFunnyUpTheme {
                Surface(color = AppColors.White, contentColor = AppColors.Ink900) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Surface(color = AppColors.Amber100, contentColor = AppColors.Amber800) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("DEBUG · SAMPLE DATA ONLY", style = AppTypography.Label)
                                Text("All payments, retries and sync are simulated. No data is saved or sent.",
                                    style = AppTypography.Caption)
                                Row {
                                    TextButton(onClick = { session.showActivity = false }) { Text("Home") }
                                    TextButton(onClick = { session.showActivity = true }) { Text("Activity") }
                                    TextButton(onClick = {
                                        startActivity(Intent(this@Person5TestActivity, Person5TestActivity::class.java))
                                        finish()
                                    }) { Text("Reset") }
                                }
                                Row {
                                    TextButton(onClick = { session.outcomeMenu = true }) {
                                        Text("Simulate: ${session.outcome}")
                                    }
                                    DropdownMenu(expanded = session.outcomeMenu,
                                        onDismissRequest = { session.outcomeMenu = false }) {
                                        listOf("Success", "Pending", "Failure").forEach { outcome ->
                                            DropdownMenuItem(text = { Text(outcome) }, onClick = {
                                                session.outcome = outcome
                                                session.outcomeMenu = false
                                            })
                                        }
                                    }
                                    TextButton(onClick = { session.toggleOffline() }) {
                                        Text(if (homeData.offlineSavedAt == null) "Show offline" else "Hide offline")
                                    }
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            if (session.showActivity) ActivityRoute(activity)
                            else HomeRoute(home, { session.showActivity = true },
                                { destination("Groups") }, { destination("Create group") },
                                { destination("Join group") }, { destination("Group $it") })
                        }
                    }
                }
            }
        }
    }
}

private class SampleSession : ViewModel() {
    val home = MutableStateFlow(sampleHome())
    val activity = MutableStateFlow(sampleActivity(home.value))
    var showActivity by mutableStateOf(false)
    var outcome by mutableStateOf("Success")
    var outcomeMenu by mutableStateOf(false)

    suspend fun simulate(): SyncStatus {
        val selected = outcome
        delay(800)
        return when (selected) {
            "Success" -> SyncStatus.SYNCED
            "Pending" -> SyncStatus.PENDING_INSERT
            else -> error("Simulated failure. Choose Success and try again.")
        }
    }

    fun toggleOffline() {
        home.value = home.value.copy(offlineSavedAt =
            if (home.value.offlineSavedAt == null) home.value.nowMillis else null)
    }
}

private fun factory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
