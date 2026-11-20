package com.example.getyourmoneyupnotyourfunnyup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.getyourmoneyupnotyourfunnyup.data.sync.SyncQueueProcessor
import com.example.getyourmoneyupnotyourfunnyup.data.sync.SyncRunner
import com.example.getyourmoneyupnotyourfunnyup.data.sync.SyncRunResult

class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val runner: SyncRunner
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(context, parameters, SyncRunner(null, { null }))

    override suspend fun doWork(): Result {
        val accountId = inputData.getString(AccountKey).orEmpty()
        val outcome = runner.run(accountId)
        return when (outcome) {
            SyncRunResult.Complete -> Result.success()
            SyncRunResult.Retry -> Result.retry()
            else -> Result.failure(workDataOf("reason" to outcome.name))
        }
    }

    companion object { const val AccountKey = "account_id" }
}

class SyncWorkerFactory(processor: SyncQueueProcessor?, currentAccount: () -> String?) : WorkerFactory() {
    val runner = SyncRunner(processor, currentAccount)

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, runner) else null
}
