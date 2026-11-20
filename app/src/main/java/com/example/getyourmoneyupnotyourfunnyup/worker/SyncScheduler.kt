package com.example.getyourmoneyupnotyourfunnyup.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.getyourmoneyupnotyourfunnyup.data.sync.SyncRunner
import com.example.getyourmoneyupnotyourfunnyup.data.sync.SyncRunResult
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

enum class ScheduleResult { Pending, Unavailable, AccountChanged, Rejected }

class SyncScheduler(private val workManager: WorkManager, private val runner: SyncRunner) {
    suspend fun scheduleAutomatic(accountId: String): ScheduleResult = schedule(accountId, manual = false, append = true)
    suspend fun scheduleManual(accountId: String): ScheduleResult = schedule(accountId, manual = true)
    suspend fun recoverAfterRestart(accountId: String): ScheduleResult = schedule(accountId, manual = false)

    private suspend fun schedule(accountId: String, manual: Boolean, append: Boolean = false): ScheduleResult {
        when (runner.readiness(accountId)) {
            SyncRunResult.Unavailable -> return ScheduleResult.Unavailable
            SyncRunResult.AccountChanged -> return ScheduleResult.AccountChanged
            else -> Unit
        }
        return try {
            runInterruptible(Dispatchers.IO) {
                workManager.enqueueUniqueWork(workName(accountId, manual), if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request(accountId))
                    .result.get(5, TimeUnit.SECONDS)
            }
            ScheduleResult.Pending
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ScheduleResult.Rejected
        }
    }

    companion object {
        fun workName(accountId: String, manual: Boolean = false): String {
            require(accountId.isNotBlank())
            val hash = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "person5-sync-${if (manual) "manual" else "automatic"}-$hash"
        }

        fun request(accountId: String): OneTimeWorkRequest {
            require(accountId.isNotBlank())
            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(SyncWorker.AccountKey to accountId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
        }
    }
}
