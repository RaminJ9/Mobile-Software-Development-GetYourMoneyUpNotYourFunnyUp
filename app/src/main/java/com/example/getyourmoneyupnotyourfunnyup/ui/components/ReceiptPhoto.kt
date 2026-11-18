package com.example.getyourmoneyupnotyourfunnyup.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptPhotoStore
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** True for a photo stored on the phone; demo expenses use a `demo://` placeholder instead. */
fun isReceiptPhoto(photo: String?): Boolean = photo?.startsWith("file:") == true

/**
 * Thumbnail of a receipt photo, or the drawn [ReceiptThumb] when there is no real photo (demo data).
 */
@Composable
fun ReceiptThumbnail(photo: String?, modifier: Modifier = Modifier, width: Dp = 40.dp, height: Dp = 52.dp) {
    if (photo == null || !isReceiptPhoto(photo)) {
        ReceiptThumb(modifier, width, height)
        return
    }
    val shape = RoundedCornerShape(8.dp)
    val box = modifier.size(width, height).clip(shape).border(1.dp, AppColors.Line, shape)
    val bitmap = rememberPhotoBitmap(photo, THUMBNAIL_SIDE)
    if (bitmap == null) Box(box.background(AppColors.ReceiptPaper))
    else Image(bitmap, contentDescription = "Receipt photo", modifier = box, contentScale = ContentScale.Crop)
}

/** A receipt photo shown whole (receipt viewer, reading step). */
@Composable
fun ReceiptPhoto(photo: String, modifier: Modifier = Modifier) {
    val bitmap = rememberPhotoBitmap(photo, ReceiptPhotoStore.MAX_SIDE)
    if (bitmap == null) Box(modifier)
    else Image(bitmap, contentDescription = "Receipt photo", modifier = modifier, contentScale = ContentScale.Fit)
}

/**
 * Loads a stored receipt photo off the main thread, reduced to about [maxSide] pixels. Stored photos are
 * already upright and at most [ReceiptPhotoStore.MAX_SIDE] pixels, so no rotation or caching is needed.
 */
@Composable
private fun rememberPhotoBitmap(photo: String, maxSide: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, photo, maxSide) {
        value = withContext(Dispatchers.IO) {
            val path = photo.toUri().path ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    return bitmap
}

private const val THUMBNAIL_SIDE = 256

/** The green line that sweeps over a receipt while it is being read. */
@Composable
fun ScanLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scanline")
    val progress by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanlineProgress",
    )
    BoxWithConstraints(modifier) {
        val top = maxHeight * progress
        Box(
            Modifier
                .offset(y = top)
                .fillMaxWidth()
                .height(3.dp)
                .shadow(12.dp, ambientColor = AppColors.Green500, spotColor = AppColors.Green500)
                .background(AppColors.Green500),
        )
        Box(
            Modifier
                .offset(y = top - 12.dp)
                .fillMaxWidth()
                .height(27.dp)
                .background(AppColors.Green500.copy(alpha = 0.13f)),
        )
    }
}
