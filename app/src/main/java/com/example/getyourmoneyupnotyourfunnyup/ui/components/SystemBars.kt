package com.example.getyourmoneyupnotyourfunnyup.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Switches the status-bar icons to light while a dark screen (the camera) is showing, and restores
 * them when it leaves. The rest of the app is light with dark icons.
 */
@Composable
fun DarkStatusBarBackground(isDark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(isDark) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !isDark
        onDispose {
            if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
