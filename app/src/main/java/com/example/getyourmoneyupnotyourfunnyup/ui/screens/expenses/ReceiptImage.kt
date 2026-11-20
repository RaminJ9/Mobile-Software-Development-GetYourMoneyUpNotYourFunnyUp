package com.example.getyourmoneyupnotyourfunnyup.ui.screens.expenses

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Prefer the local image; an authenticated loader can cache a missing cloud receipt. */
@Composable
internal fun ReceiptImage(localUri: String?, remotePath: String?, loadRemote: (suspend () -> String?)? = null) {
    val resolver = LocalContext.current.contentResolver
    var expanded by remember(localUri) { mutableStateOf(false) }
    var retry by remember(localUri, remotePath) { mutableIntStateOf(0) }
    val receipt by key(localUri, remotePath, retry) {
        produceState<Pair<Boolean, Bitmap?>>(initialValue = false to null) {
            value = true to withContext(Dispatchers.IO) {
                decodeReceipt(resolver, localUri) ?: if (!remotePath.isNullOrBlank() && loadRemote != null) {
                    try { decodeReceipt(resolver, loadRemote()) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                } else null
            }
        }
    }
    Text("Receipt photo", style = AppTypography.Section, color = AppColors.Ink900)
    val photo = receipt.second
    if (photo != null) {
        Image(photo.asImageBitmap(), "Receipt preview", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(160.dp).testTag("receipt-preview"))
        OutlinedButton(onClick = { expanded = true }, shape = AppShapes.InlineButton,
            modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget)) { Text("View receipt", color = AppColors.Green700) }
        if (expanded) Dialog(onDismissRequest = { expanded = false }) {
            Surface(shape = AppShapes.ListCard, color = AppColors.White) {
                Column(Modifier.padding(AppSpacing.Space16), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    Image(photo.asImageBitmap(), "Full receipt", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp))
                    TextButton(onClick = { expanded = false }, modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget)) { Text("Close receipt", color = AppColors.Green700) }
                }
            }
        }
    } else {
        Text(when {
            !receipt.first && (!localUri.isNullOrBlank() || !remotePath.isNullOrBlank() && loadRemote != null) -> "Loading receipt..."
            !localUri.isNullOrBlank() -> "Receipt isn't available on this phone."
            !remotePath.isNullOrBlank() -> "Receipt photo isn't saved on this phone."
            else -> "No receipt photo attached."
        }, style = AppTypography.Caption, color = AppColors.Ink600)
        if (receipt.first && !remotePath.isNullOrBlank() && loadRemote != null) OutlinedButton(
            onClick = { retry++ }, modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget), shape = AppShapes.InlineButton) {
            Text("Try loading receipt again", color = AppColors.Green700)
        }
    }
}

/** Two passes cap decoded size, avoiding a full-resolution camera bitmap in the sheet. */
internal fun decodeReceipt(resolver: ContentResolver, localUri: String?): Bitmap? {
    if (localUri.isNullOrBlank()) return null
    return try {
        val parsed = Uri.parse(localUri)
        val uri = if (parsed.scheme == null && localUri.startsWith("/")) Uri.fromFile(File(localUri)) else parsed
        if (uri.scheme !in setOf("content", "file")) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } catch (_: Exception) { null }
}
