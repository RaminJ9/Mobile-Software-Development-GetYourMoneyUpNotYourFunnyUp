package com.example.getyourmoneyupnotyourfunnyup.data.receipt

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.scale
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
import kotlin.math.roundToInt

/**
 * Reads receipt text with ML Kit's on-device text recognizer. The model ships inside the app, so this
 * works offline from the first launch. The recognized lines are turned into items by [ReceiptParser].
 */
class MlKitReceiptScanner(context: Context) : ReceiptScanner {
    private val appContext = context.applicationContext
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun read(photo: String): ScannedReceipt {
        val image = withContext(Dispatchers.IO) { inputImage(photo) }
        val text = recognizer.process(image).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.confidence, line.angle)
        }
        return withContext(Dispatchers.Default) { ReceiptParser.parse(lines) }
    }

    /**
     * The photo as the recognizer gets it. Small photos (screenshots, pictures from a chat or the web) are
     * enlarged first: on a 400-pixel receipt the letters are a few pixels high and the recognizer reads
     * garbage. Stored photos are already upright, so no rotation is needed. The stored photo itself is
     * not changed.
     */
    @VisibleForTesting
    internal fun inputImage(photo: String): InputImage {
        val uri = photo.toUri()
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0 || longSide >= MIN_LONG_SIDE) return InputImage.fromFilePath(appContext, uri)
        val small = resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return InputImage.fromFilePath(appContext, uri)
        val scale = MIN_LONG_SIDE.toFloat() / longSide
        val large = small.scale((small.width * scale).roundToInt(), (small.height * scale).roundToInt())
        if (large !== small) small.recycle()
        return InputImage.fromBitmap(large, 0)
    }

    companion object {
        /** Photos whose long side is shorter than this are enlarged to it before reading. */
        const val MIN_LONG_SIDE = 2400
    }
}
