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
 * "زجاجي بالكامل" (full glass, edge-to-edge): the status bar is always
 * fully transparent — paired with
 * `WindowCompat.setDecorFitsSystemWindows(window, false)` in MainActivity,
 * this lets whatever the app itself draws behind it (a screen's own
 * liquid-glass header, or the splash gradient) show straight through,
 * instead of a separately-painted flat OS strip sitting on top of it.
 * That flat strip meeting the glass surface's glossy top edge was exactly
 * what used to read as a hard dividing line right under the status bar
 * icons. The nav bar keeps its own solid color (callers pass whatever fits
 * that screen), since only the *top* bar needs to disappear into the
 * glass design here. Icon contrast (light vs dark glyphs) is still fully
 * controllable per screen for both bars.
 */
@Composable
fun SetSystemBarsColor(
    navigationBarColor: Color,
    statusBarDarkIcons: Boolean,
    navigationBarDarkIcons: Boolean
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return

    SideEffect {
        val window = activity.window
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = statusBarDarkIcons
        controller.isAppearanceLightNavigationBars = navigationBarDarkIcons
    }
}
