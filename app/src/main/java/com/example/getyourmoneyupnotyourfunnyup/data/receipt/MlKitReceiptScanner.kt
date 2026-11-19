package com.example.getyourmoneyupnotyourfunnyup.data.receipt

import android.content.Context
import androidx.core.net.toUri
import com.example.getyourmoneyupnotyourfunnyup.model.OcrLine
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptParser
import com.example.getyourmoneyupnotyourfunnyup.model.ScannedReceipt
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads receipt text with ML Kit's on-device text recognizer. The model ships inside the app, so this
 * works offline from the first launch. The recognized lines are turned into items by [ReceiptParser].
 */
class MlKitReceiptScanner(context: Context) : ReceiptScanner {
    private val appContext = context.applicationContext
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun read(photo: String): ScannedReceipt {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(appContext, photo.toUri()) }
        val text = recognizer.process(image).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.confidence)
        }
        return withContext(Dispatchers.Default) { ReceiptParser.parse(lines) }
    }
}
