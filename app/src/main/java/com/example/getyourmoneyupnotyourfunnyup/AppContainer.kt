package com.example.getyourmoneyupnotyourfunnyup

import android.app.Application
import android.content.Context
import com.example.getyourmoneyupnotyourfunnyup.data.repository.InMemoryLedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectivityObserver
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.LedgerDashboardSource
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.LedgerGroupsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Manual dependency container: one instance per process, owned by [GetYourMoneyUpApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val connectivity = ConnectivityObserver(appContext)

    val ledgerRepository: LedgerRepository by lazy {
        InMemoryLedgerRepository(
            scope = appScope,
            // Same check as the Home/Activity offline banner, so the banner and syncing always agree.
            // Unknown (no connectivity service) counts as online so uploads are still attempted.
            isOnline = connectivity.observe()
                .map { it != ConnectionState.Offline }
                .stateIn(appScope, SharingStarted.Eagerly, connectivity.currentState() != ConnectionState.Offline),
        )
    }

    /** Feeds the Home and Activity screens from [ledgerRepository]. */
    val dashboardSource: LedgerDashboardSource by lazy { LedgerDashboardSource(ledgerRepository) }

    /** Feeds the Groups, Create group and Members screens from [ledgerRepository]. */
    val groupsSource: LedgerGroupsSource by lazy { LedgerGroupsSource(ledgerRepository) }
}

class GetYourMoneyUpApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** The process-wide [AppContainer]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as GetYourMoneyUpApplication).container
