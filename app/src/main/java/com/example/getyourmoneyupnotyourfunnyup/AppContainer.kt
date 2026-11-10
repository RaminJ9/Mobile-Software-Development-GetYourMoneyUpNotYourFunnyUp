package com.example.getyourmoneyupnotyourfunnyup

import android.app.Application
import android.content.Context
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.remote.ConnectivityObserver
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.InMemoryLedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** Manual dependency container: one instance per process, owned by [GetYourMoneyUpApplication]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val connectivity = ConnectivityObserver(appContext)

    val authRepository: AuthRepository by lazy {
        val database = AppDatabase.getInstance(appContext)
        AuthRepository(userDao = database.userDao, syncQueueDao = database.syncQueueDao)
    }

    val ledgerRepository: LedgerRepository by lazy {
        InMemoryLedgerRepository(
            scope = appScope,
            isOnline = connectivity.observe().stateIn(appScope, SharingStarted.Eagerly, connectivity.isOnlineNow()),
        )
    }
}

class GetYourMoneyUpApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** The process-wide [AppContainer]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as GetYourMoneyUpApplication).container
