package com.example.getyourmoneyupnotyourfunnyup

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.sync.FirestoreSyncQueueProcessor
import com.example.getyourmoneyupnotyourfunnyup.worker.SyncScheduler
import com.example.getyourmoneyupnotyourfunnyup.worker.SyncWorkerFactory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MoneyApplication : Application(), Configuration.Provider {
    private val factory by lazy {
        val account = { Firebase.auth.currentUser?.uid }
        SyncWorkerFactory(FirestoreSyncQueueProcessor(AppDatabase.getInstance(this), currentAccountId = account), account)
    }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(factory).build()
    val syncScheduler: SyncScheduler by lazy { SyncScheduler(WorkManager.getInstance(this), factory.runner) }
}
