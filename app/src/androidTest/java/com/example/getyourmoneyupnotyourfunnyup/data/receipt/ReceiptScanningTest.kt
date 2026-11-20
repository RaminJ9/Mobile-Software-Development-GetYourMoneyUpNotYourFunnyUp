package com.example.getyourmoneyupnotyourfunnyup.data.receipt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItemKind
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real on-device reading: a receipt is drawn into an image, stored by [ReceiptPhotoStore] and read by
 * ML Kit through [MlKitReceiptScanner]. No network is used.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptScanningTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ReceiptPhotoStore(context)

    @Test
    fun aPrintedReceipt_isReadIntoItemsAndATotal() = runBlocking {
        val source = writeImage("receipt.png", drawReceipt(), Bitmap.CompressFormat.PNG)
        val photo = store.save(Uri.fromFile(source).toString())
        try {
            val receipt = MlKitReceiptScanner(context).read(photo)

            assertEquals("Netto", receipt.merchant)
            assertEquals(21_195L, receipt.totalCents)
            assertEquals(listOf(5_995L, 600L, 4_500L, 2_200L, 7_900L), receipt.items.map { it.priceCents })
            assertEquals("The items add up to the printed total", receipt.totalCents, receipt.items.sumOf { it.priceCents })
            assertTrue("Tuborg is alcohol", receipt.items[0].isAlcohol)
            assertEquals(ReceiptItemKind.DEPOSIT, receipt.items[1].kind)
            assertTrue("Oksekød is meat", receipt.items[2].isMeat)
        } finally {
            store.delete(photo)
            source.delete()
        }
    }

    @Test
    fun aTiltedPhoto_stillGivesEveryItemItsOwnPriceAndFindsTheTotal() = runBlocking {
        // Hand-held photos are rarely straight. Without straightening the rows, prices slid one item down
        // and the total was read as an item.
        for (degrees in listOf(4f, -6f)) {
            val source = writeImage("tilted.png", tilted(drawReceipt(), degrees), Bitmap.CompressFormat.PNG)
            val photo = store.save(Uri.fromFile(source).toString())
            try {
                val receipt = MlKitReceiptScanner(context).read(photo)
                assertEquals("tilted $degrees°", listOf(5_995L, 600L, 4_500L, 2_200L, 7_900L), receipt.items.map { it.priceCents })
                assertEquals("tilted $degrees°", 21_195L, receipt.totalCents)
            } finally {
                store.delete(photo)
                source.delete()
            }
        }
    }

    @Test
    fun aSmallPhoto_isEnlargedForReading_butStoredAsItIs() = runBlocking {
        // Pictures from the web or a chat are often only 400 pixels wide. Enlarged, the recognizer read
        // 89 % of the lines on the 10-receipt accuracy test instead of 75 %.
        val receipt = drawReceipt()
        val small = Bitmap.createScaledBitmap(receipt, 300, receipt.height * 300 / receipt.width, true)
        val source = writeImage("small.jpg", small, Bitmap.CompressFormat.JPEG)
        val photo = store.save(Uri.fromFile(source).toString())
        try {
            val scanner = MlKitReceiptScanner(context)
            val image = scanner.inputImage(photo)
            assertEquals(MlKitReceiptScanner.MIN_LONG_SIDE, maxOf(image.width, image.height))
            assertEquals("Same shape", small.width.toFloat() / small.height, image.width.toFloat() / image.height, 0.01f)
            val stored = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(Uri.parse(photo).path, stored)
            assertEquals("The stored photo keeps its size", small.width, stored.outWidth)

            val read = scanner.read(photo)
            assertEquals(listOf(5_995L, 600L, 4_500L, 2_200L, 7_900L), read.items.map { it.priceCents })
            assertEquals(21_195L, read.totalCents)
        } finally {
            store.delete(photo)
            source.delete()
        }
    }

    @Test
    fun aCameraSizedPhoto_isReadAsItIs() = runBlocking {
        val source = writeImage("large.jpg", Bitmap.createBitmap(1920, 2560, Bitmap.Config.ARGB_8888), Bitmap.CompressFormat.JPEG)
        val photo = store.save(Uri.fromFile(source).toString())
        try {
            val image = MlKitReceiptScanner(context).inputImage(photo)
            assertEquals(1920 to 2560, image.width to image.height)
        } finally {
            store.delete(photo)
            source.delete()
        }
    }

    @Test
    fun aSidewaysPhoto_isStoredUpright() = runBlocking {
        // A camera JPEG that says "rotate 90°" in its EXIF data.
        val source = writeImage("sideways.jpg", Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888), Bitmap.CompressFormat.JPEG)
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val photo = store.save(Uri.fromFile(source).toString())
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(Uri.parse(photo).path, bounds)
            assertEquals(200, bounds.outWidth)
            assertEquals(400, bounds.outHeight)
        } finally {
            store.delete(photo)
            source.delete()
        }
    }

    @Test
    fun deleting_onlyRemovesTheStoresOwnPhotos() {
        val elsewhere = writeImage("not-ours.png", Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), Bitmap.CompressFormat.PNG)
        store.delete(Uri.fromFile(elsewhere).toString())
        assertTrue(elsewhere.exists())
        elsewhere.delete()
    }

    /** [receipt] photographed at an angle, on a white table. */
    private fun tilted(receipt: Bitmap, degrees: Float): Bitmap {
        val photo = Bitmap.createBitmap(receipt.width + 200, receipt.height + 200, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawColor(Color.WHITE)
            rotate(degrees, photo.width / 2f, photo.height / 2f)
            drawBitmap(receipt, 100f, 100f, null)
        }
        return photo
    }

    private fun writeImage(name: String, bitmap: Bitmap, format: Bitmap.CompressFormat): File =
        File(context.cacheDir, name).apply { outputStream().use { bitmap.compress(format, 95, it) } }

    /** A plain shop receipt: names on the left, prices right-aligned, then the total. */
    private fun drawReceipt(): Bitmap {
        val width = 900
        val bitmap = Bitmap.createBitmap(width, 1100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.MONOSPACE
        }
        var y = 120f
        fun centred(text: String) {
            canvas.drawText(text, (width - paint.measureText(text)) / 2, y, paint)
            y += 70
        }
        fun row(name: String, price: String) {
            canvas.drawText(name, 60f, y, paint)
            canvas.drawText(price, width - 60 - paint.measureText(price), y, paint)
            y += 70
        }
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = 56f
        centred("NETTO")
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 40f
        centred("Sanderumvej 12, Odense")
        y += 40
        row("TUBORG GRØN 6-PAK", "59,95")
        row("PANT A", "6,00")
        row("HAKKET OKSEKØD", "45,00")
        row("RUGBRØD", "22,00")
        row("MERLOT RØDVIN", "79,00")
        y += 30
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        row("TOTAL", "211,95")
        paint.typeface = Typeface.MONOSPACE
        row("DANKORT", "211,95")
        return bitmap
    }
}
