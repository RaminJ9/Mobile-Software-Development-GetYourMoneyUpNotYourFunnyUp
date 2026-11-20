package com.example.getyourmoneyupnotyourfunnyup.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.core.database.sqlite.transaction
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface ReceiptDownload { suspend fun bytes(path: String): ByteArray }

class StorageReceiptDownload : ReceiptDownload {
    override suspend fun bytes(path: String): ByteArray {
        val storage = FirebaseStorage.getInstance()
        val reference = if (path.startsWith("gs://") || path.startsWith("https://")) storage.getReferenceFromUrl(path) else storage.reference.child(path)
        require(reference.bucket == storage.reference.bucket) { "Receipt belongs to another storage bucket." }
        val task = reference.getBytes(12L * 1024 * 1024)
        return suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
    }
}

/** Downloaded images are private cache data. They never change expense financial fields or sync status. */
class ReceiptRepository(
    private val context: Context, private val database: AppDatabase, private val currentAccountId: () -> String?,
    private val download: ReceiptDownload = StorageReceiptDownload()
) {
    suspend fun load(accountId: String, groupId: String, expense: Expense): String? = withContext(Dispatchers.IO) {
        fun checkAccess() {
            if (currentAccountId() != accountId || database.groupDao.getMembersForGroup(groupId).none { it.userId == accountId }) throw SecurityException("No receipt access.")
        }
        checkAccess()
        require(expense.groupId == groupId && !expense.isDeleted)
        val path = expense.receiptRemotePath?.takeIf { it.isNotBlank() } ?: return@withContext null
        val hash = MessageDigest.getInstance("SHA-256").digest("$accountId|${expense.id}|$path".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val folder = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(folder, "$hash.img")
        val cachedBounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath, cachedBounds)
        if (file.exists() && (cachedBounds.outWidth <= 0 || cachedBounds.outHeight <= 0)) file.delete()
        if (!file.exists()) {
            val bytes = download.bytes(path)
            require(bytes.size <= 12 * 1024 * 1024) { "Receipt image is too large." }
            checkAccess()
            val temp = File.createTempFile("receipt-", ".tmp", folder)
            try {
                temp.writeBytes(bytes)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temp.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a receipt image." }
                check(temp.renameTo(file)) { "Couldn't cache receipt." }
            } finally { temp.delete() }
        }
        val uri = Uri.fromFile(file).toString()
        val saved = database.writableDatabase.transaction {
            checkAccess()
            val current = database.expenseDao.getExpenseById(expense.id)
            if (current == null || current.groupId != groupId || current.isDeleted || current.receiptRemotePath != path) false else {
                update("expenses", ContentValues().apply { put("receipt_local_uri", uri) }, "id = ?", arrayOf(expense.id)) == 1
            }
        }
        if (saved) { database.notifyTableChanged("expenses"); uri } else null
    }
}
