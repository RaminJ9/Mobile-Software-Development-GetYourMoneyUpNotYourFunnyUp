package com.example.getyourmoneyupnotyourfunnyup.data.receipt

import android.net.Uri
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ExpenseRepository
import com.example.getyourmoneyupnotyourfunnyup.worker.ReceiptUploadWorker
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Where a receipt photo is kept in Cloud Storage: one file per expense, in its group's folder. The
 * expense's `receiptStoragePath` points here, so other members can download it.
 */
fun receiptStoragePath(groupId: String, expenseId: String): String = "receipts/$groupId/$expenseId.jpg"

/** Cloud storage for receipt photos. */
interface ReceiptStorage {
    suspend fun put(path: String, photo: File)

    /** True when trying again won't help (no access, no bucket), false for network trouble. */
    fun isPermanent(error: Exception): Boolean
}

class FirebaseReceiptStorage : ReceiptStorage {
    override suspend fun put(path: String, photo: File) {
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        FirebaseStorage.getInstance().reference.child(path).putFile(Uri.fromFile(photo), metadata).await()
    }

    override fun isPermanent(error: Exception): Boolean =
        error is StorageException && error.errorCode in setOf(
            StorageException.ERROR_NOT_AUTHORIZED,
            StorageException.ERROR_BUCKET_NOT_FOUND,
            StorageException.ERROR_PROJECT_NOT_FOUND,
            StorageException.ERROR_QUOTA_EXCEEDED,
            // An upload gets "object not found" (404) when the project has no storage bucket yet.
            StorageException.ERROR_OBJECT_NOT_FOUND,
        )
}

/**
 * Uploads the receipt photo of an expense saved on this phone, then records its storage path on the
 * expense. The path goes through [ExpenseRepository], so it reaches Firestore like any other change.
 * The expense itself never waits for the photo: it is saved and synced on its own.
 */
class ReceiptUploader(
    private val database: AppDatabase,
    private val currentAccountId: () -> String?,
    private val storage: ReceiptStorage,
    private val expenses: ExpenseRepository = ExpenseRepository(database),
) {
    enum class Outcome { Uploaded, NothingToDo, TryLater, GiveUp }

    suspend fun upload(expenseId: String): Outcome {
        val expense = withContext(Dispatchers.IO) { database.expenseDao.getExpenseById(expenseId) } ?: return Outcome.NothingToDo
        if (expense.isDeleted || !expense.receiptRemotePath.isNullOrBlank()) return Outcome.NothingToDo
        val me = currentAccountId() ?: return Outcome.TryLater // signed out for now
        // Only the phone that took the photo has it, and it belongs to whoever saved the expense.
        if (expense.createdBy != me) return Outcome.NothingToDo
        val photo = expense.receiptLocalUri?.toUri()?.takeIf { it.scheme == "file" }?.path?.let(::File)
        if (photo == null || !photo.isFile) return Outcome.NothingToDo

        val path = receiptStoragePath(expense.groupId, expense.id)
        try {
            storage.put(path, photo)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return if (storage.isPermanent(error)) Outcome.GiveUp else Outcome.TryLater
        }

        // Record the path on the expense as it is now (it may have changed while uploading).
        val latest = withContext(Dispatchers.IO) {
            database.expenseDao.getExpenseById(expenseId)?.let { it to database.expenseDao.getSplitsForExpense(expenseId) }
        } ?: return Outcome.NothingToDo
        val (entity, splits) = latest
        if (entity.isDeleted) return Outcome.NothingToDo
        expenses.saveExpense(entity.toDomain(splits.map { it.toDomain() }).copy(receiptRemotePath = path))
        return Outcome.Uploaded
    }
}

/** Starts uploading a saved expense's receipt photo. */
fun interface ReceiptUploads {
    fun schedule(expenseId: String)
}

/**
 * Starts uploads for [accountId]'s receipt photos that never reached cloud storage, e.g. because
 * storage wasn't set up yet or the job gave up. Run once when the app starts; already-running uploads
 * are kept.
 */
fun ReceiptUploads.scheduleMissing(database: AppDatabase, accountId: String) {
    val e = DatabaseContract.ExpensesTable
    val missing = database.readableDatabase.query(
        e.TABLE_NAME, arrayOf(e.COL_ID),
        "${e.COL_CREATED_BY} = ? AND ${e.COL_IS_DELETED} = 0 AND ${e.COL_RECEIPT_LOCAL_URI} LIKE 'file:%' AND " +
            "(${e.COL_RECEIPT_REMOTE_PATH} IS NULL OR ${e.COL_RECEIPT_REMOTE_PATH} = '')",
        arrayOf(accountId), null, null, null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    missing.forEach(::schedule)
}

/**
 * Uploads in the background with WorkManager: waits for a connection, survives the app closing, and
 * retries with back-off. One job per expense.
 */
class WorkManagerReceiptUploads(private val workManager: WorkManager) : ReceiptUploads {
    override fun schedule(expenseId: String) {
        val request = OneTimeWorkRequestBuilder<ReceiptUploadWorker>()
            .setInputData(workDataOf(ReceiptUploadWorker.EXPENSE_ID to expenseId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("receipt-upload-$expenseId", ExistingWorkPolicy.KEEP, request)
    }
}
