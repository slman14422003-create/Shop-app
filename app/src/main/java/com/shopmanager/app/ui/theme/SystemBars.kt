package com.shopmanager.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Paints the status bar (and nav bar) in the app's own color instead of
 * leaving the system default bare/black bar, and switches icon contrast
 * automatically for light vs dark theme.
 */
@Composable
fun SetSystemBarsColor(
    statusBarColor: Color,
    navigationBarColor: Color,
    statusBarDarkIcons: Boolean,
    navigationBarDarkIcons: Boolean
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return

    SideEffect {
        val window = activity.window
        window.statusBarColor = statusBarColor.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = statusBarDarkIcons
        controller.isAppearanceLightNavigationBars = navigationBarDarkIcons
    }
}
