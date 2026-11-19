package com.example.getyourmoneyupnotyourfunnyup.data.receipt

import com.example.getyourmoneyupnotyourfunnyup.model.ScannedReceipt

/** Reads a receipt photo on the phone (no internet needed) and turns it into items. */
fun interface ReceiptScanner {
    /** [photo] is a file URI from [ReceiptPhotos.save]. Throws when the photo can't be read at all. */
    suspend fun read(photo: String): ScannedReceipt
}

/** Where receipt photos are kept on the phone. Photos are file URIs, as stored in `Expense.receiptLocalUri`. */
interface ReceiptPhotos {
    /**
     * Copies [source] (a photo the camera just took, or one picked from the gallery) into the app as an
     * upright JPEG of a sensible size, and returns its file URI.
     */
    suspend fun save(source: String): String

    /** Deletes a photo made by [save]; anything else is left alone. */
    fun delete(photo: String)
}
