package com.example.getyourmoneyupnotyourfunnyup.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Keeps receipt photos in the app's private files, so they stay available offline and are removed with
 * the app. Photos are stored upright (EXIF rotation applied) and at most [MAX_SIDE] pixels long: plenty
 * for reading receipt text, without keeping 10+ MB camera files.
 */
class ReceiptPhotoStore(context: Context) : ReceiptPhotos {
    private val appContext = context.applicationContext
    private val photosDir = File(appContext.filesDir, "receipts")
    private val captureDir = File(appContext.cacheDir, "receipt-captures")

    /** A temporary file for the camera to write into; [save] turns it into a stored photo and deletes it. */
    fun newCaptureFile(): File = File(captureDir.apply { mkdirs() }, "${UUID.randomUUID()}.jpg")

    override suspend fun save(source: String): String = withContext(Dispatchers.IO) {
        val uri = source.toUri()
        val bitmap = decodeUpright(uri) ?: error("Couldn't open this photo")
        val target = File(photosDir.apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        try {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            bitmap.recycle()
        }
        // A camera capture is only a temporary file.
        uri.path?.let(::File)?.takeIf { uri.scheme == "file" && it.parentFile == captureDir }?.delete()
        Uri.fromFile(target).toString()
    }

    override fun delete(photo: String) {
        val uri = photo.toUri()
        val file = uri.path?.let(::File) ?: return
        if (uri.scheme == "file" && file.parentFile == photosDir) file.delete()
    }

    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = appContext.contentResolver
        // Measuring only: in this mode decodeStream always returns null, so check the stream instead.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return null
        // Decode at the smallest power-of-two reduction that still keeps at least MAX_SIDE pixels.
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { rotationDegrees(ExifInterface(it)) } ?: 0
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        if (rotation == 0 && scale == 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    private fun rotationDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    companion object {
        const val MAX_SIDE = 2560
        private const val JPEG_QUALITY = 90
    }
}
