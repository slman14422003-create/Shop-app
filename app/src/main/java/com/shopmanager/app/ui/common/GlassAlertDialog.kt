package com.shopmanager.app.ui.common

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * "iOS 26 / زجاج سائل" replacement for [androidx.compose.material3.AlertDialog].
 *
 * BUG FIXED (الطلب: "اعد تصميم الاطار... Ios 26 بالزجاج السائل"): every
 * dialog in the app ("عميل جديد", "إضافة نقص", confirm/cancel prompts,
 * etc.) used the stock Material3 AlertDialog — a flat, square-cornered,
 * opaque-gray card with an instant no-animation appearance. That is the one
 * surface in the whole app that never got the "زجاج سائل" treatment already
 * applied to headers/bottom nav ([liquidGlassSurface]/[FloatingBottomNav]),
 * so it's exactly what read as "still Android" in the screenshot. This is a
 * drop-in replacement with the *same* call signature as AlertDialog's most
 * commonly used parameters (so existing call sites only need their function
 * name changed), rebuilt as:
 *   - a large 28.dp fully-rounded corner radius (iOS sheet radius, not
 *     Material's 24.dp/12.dp squircle),
 *   - the same layered [liquidGlassSurface] used everywhere else in the app
 *     (frosted highlight + drifting sheen + glass-rim border) instead of a
 *     flat fill, painted over the *tonal* surface color (not the brand
 *     gradient) so it reads as frosted glass sitting above content rather
 *     than a colorful banner,
 *   - a spring "pop" scale-and-fade entrance ([MotionSpecs.popInSpring])
 *     instead of Compose's default instant swap, matching the springy feel
 *     used for every other on-top-of-content surface in the app,
 *   - text/buttons kept as plain Composable slots so callers that pass
 *     [androidx.compose.material3.TextButton]/[androidx.compose.material3.Text]
 *     already work unchanged.
 *
 * Deliberately built on the *base* surface tone (surfaceContainerHigh),
 * never surfaceContainerHighest — [AppTextField]'s fill is
 * surfaceContainerHighest specifically so it stays visibly lighter than
 * whatever dialog background sits behind it (see the bug note on
 * [AppTextField]); using that same tone here would silently reintroduce the
 * "invisible field" bug this file's sibling already fixed once.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        // Springy pop-in instead of Dialog's default hard cut — kicked off
        // once on entry (Dialog hosts this composable in its own window, so
        // there's no risk of re-triggering on unrelated recomposition).
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.86f,
            animationSpec = MotionSpecs.popInSpring(),
            label = "glassDialogScale"
        )
        val contentAlpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(180),
            label = "glassDialogAlpha"
        )

        val resolvedContainer = if (containerColor.isSpecified()) containerColor
        else MaterialTheme.colorScheme.surfaceContainerHigh
        val resolvedTitleColor = if (titleContentColor.isSpecified()) titleContentColor
        else MaterialTheme.colorScheme.onSurface
        val resolvedTextColor = if (textContentColor.isSpecified()) textContentColor
        else MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .alpha(contentAlpha)
                .widthIn(min = 280.dp, max = 400.dp)
                .liquidGlassSurface(
                    shape = shape,
                    baseBrush = Brush.verticalGradient(
                        listOf(resolvedContainer, resolvedContainer)
                    ),
                    elevation = 24.dp,
                    sheen = true
                )
        ) {
            Column(Modifier.padding(24.dp)) {
                icon?.let {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides resolvedTitleColor) { it() }
                    }
                }
                title?.let {
                    Box(
                        Modifier.fillMaxWidth().padding(top = if (icon != null) 12.dp else 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            CompositionLocalProvider(LocalContentColor provides resolvedTitleColor) { it() }
                        }
                    }
                }
                text?.let {
                    Box(Modifier.padding(top = if (title != null) 12.dp else 0.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            CompositionLocalProvider(LocalContentColor provides resolvedTextColor) { it() }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp).wrapContentWidth(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified
