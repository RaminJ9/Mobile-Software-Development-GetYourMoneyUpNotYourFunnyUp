package com.example.getyourmoneyupnotyourfunnyup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.getyourmoneyupnotyourfunnyup.MoneyApplication
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.FirebaseReceiptStorage
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptUploader
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptUploader.Outcome
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Uploads one expense's receipt photo (see [ReceiptUploader]). Started by `WorkManagerReceiptUploads`
 * with a network constraint; WorkManager's default factory creates it, since `SyncWorkerFactory`
 * only makes sync workers.
 */
class ReceiptUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val expenseId = inputData.getString(EXPENSE_ID) ?: return Result.failure()
        val account = { Firebase.auth.currentUser?.uid }
        val outcome = ReceiptUploader(AppDatabase.getInstance(applicationContext), account, FirebaseReceiptStorage()).upload(expenseId)
        Log.i(TAG, "Receipt photo for expense $expenseId: $outcome (attempt ${runAttemptCount + 1})")
        return when (outcome) {
            Outcome.Uploaded -> {
                // The new storage path is queued; sync it now rather than at the next app start.
                account()?.let { (applicationContext as? MoneyApplication)?.syncScheduler?.scheduleAutomatic(it) }
                Result.success()
            }
            Outcome.NothingToDo -> Result.success()
            Outcome.TryLater -> if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
            // No access to the storage bucket: the photo stays on this phone; the expense is unaffected.
            Outcome.GiveUp -> Result.failure()
        }
    }

    companion object {
        const val EXPENSE_ID = "expenseId"
        private const val TAG = "ReceiptUpload"
        private const val MAX_ATTEMPTS = 10
    }
}
