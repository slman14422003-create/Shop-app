package com.shopmanager.app.ui.common

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

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
 *
 * BUG FIXED ("ما بدي الأنيميشن/اللمعة هون" + "لازم يكون فيه نفس لون
 * التطبيق مع تدرج جميل"): this was the one surface in the app that opted
 * back into `liquidGlassSurface`'s animated diagonal sheen sweep
 * (`sheen = true, animated = true`) — every other glass panel (headers,
 * bottom nav) had that shine animation removed already (see `animated`'s
 * own doc in LiquidGlass.kt) because it read as restless chrome, but the
 * dialog kept it. Both are now off, so the dialog is a calm, static glass
 * panel like everywhere else — no sweeping highlight, no looping motion.
 *
 * The fill itself also used to be a flat, colorless `resolvedContainer`
 * tone painted twice as a "gradient" with identical start/end stops — i.e.
 * no gradient at all, and no relationship to the app's actual brand color.
 * It's now a real two-stop vertical gradient blended toward the app's own
 * primary/tertiary theme colors (whatever palette — or dynamic color —
 * the person has selected in Settings, same source every other
 * brand-tinted surface reads from), softened by lerping into the neutral
 * container tone rather than using the vivid header colors outright, so
 * the dialog reads as unmistakably "this app's color" while staying dark
 * enough/neutral enough for the existing onSurface/onSurfaceVariant text
 * to stay legible on top of it.
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
        // BUG FIXED ("لازم يكون تصميم شفاف وضبابي مو لون باهت" — must be a
        // transparent/blurry design, not a dull/pale color): this dialog's
        // panel used to be a fully OPAQUE gradient (no alpha at all) sitting
        // in front of a plain dark scrim — i.e. not glass at all, just a
        // solid pastel card, which is exactly what read as "flat pale
        // color" in the screenshot. Two real fixes, not just a tint tweak:
        // (1) the panel fill below now carries actual alpha so the layers
        // behind it can show through, and (2) on Android 12+ (API 31,
        // `FLAG_BLUR_BEHIND`) the dialog's own *window* is told to blur
        // whatever is genuinely behind it — the real screen content, not a
        // fake approximation — which is the one place in this app where
        // that's honestly possible (a modal dialog, unlike the always-on
        // headers in LiquidGlass.kt, always sits on top of a full frame of
        // real content). Older API levels fall back to the plain dim scrim
        // Dialog already provided, just with the now-translucent panel on
        // top of it — still reads as "less opaque", just without the blur.
        val view = LocalView.current
        val density = LocalDensity.current
        LaunchedEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radiusPx = with(density) { 56.dp.toPx() }.toInt().coerceAtLeast(1)
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = radiusPx }
                window.setDimAmount(0.30f)
            }
        }

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

        // The "نفس لون التطبيق مع تدرج جميل" gradient: the neutral dialog
        // tone lerped toward this app's own primary/tertiary colors (not
        // the separate, always-vivid header BrandGradient — that's tuned
        // for white text and would fight this dialog's existing dark-theme
        // text colors) so it visibly carries the app's current palette
        // while staying dark/neutral enough for onSurface text on top.
        // BUG FIXED (same "شفاف وضبابي" request): these two stops used to be
        // blended entirely from fully-opaque colors (resolvedContainer,
        // primary, tertiary all alpha = 1), so the resulting gradient was
        // itself 100% opaque no matter what — a solid card, not glass. The
        // `.copy(alpha = ...)` below is what actually lets the real,
        // now-blurred content behind the dialog (see the window-blur fix
        // above) show through the panel, which is what makes it read as a
        // pane of frosted glass instead of a flat painted rectangle.
        val gradientTop = androidx.compose.ui.graphics.lerp(
            resolvedContainer, MaterialTheme.colorScheme.primary, 0.24f
        ).copy(alpha = 0.68f)
        val gradientBottom = androidx.compose.ui.graphics.lerp(
            resolvedContainer, MaterialTheme.colorScheme.tertiary, 0.16f
        ).copy(alpha = 0.55f)

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
                        listOf(gradientTop, gradientBottom)
                    ),
                    elevation = 24.dp,
                    sheen = false,
                    animated = false
                )
        ) {
            Column {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                    icon?.let {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides resolvedTitleColor) { it() }
                        }
                    }
                    title?.let {
                        Box(
                            Modifier.fillMaxWidth().padding(top = if (icon != null) 10.dp else 0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // BUG FIXED ("لا يزال غير منسق"): headlineSmall
                            // (this app's type scale runs it ~24sp+bold) was
                            // oversized for a modal title — "عميل جديد"
                            // force-wrapped onto two lines even though the
                            // dialog is plenty wide for it on one, which is
                            // what read as sloppy/unbalanced. iOS alert
                            // titles are a compact, semibold single line;
                            // titleLarge (~22sp, and never forced bold in
                            // this app's type scale) undersells the "title"
                            // read on its own, so weight is bumped
                            // explicitly instead of relying on the style.
                            ProvideTextStyle(
                                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                            ) {
                                CompositionLocalProvider(LocalContentColor provides resolvedTitleColor) { it() }
                            }
                        }
                    }
                    text?.let {
                        Box(Modifier.fillMaxWidth().padding(top = if (title != null) 10.dp else 0.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                CompositionLocalProvider(LocalContentColor provides resolvedTextColor) { it() }
                            }
                        }
                    }
                }
                // BUG FIXED ("لا يزال غير منسق"): the button row used to be
                // small TextButtons squeezed into a `wrapContentWidth(End)`
                // cluster — fine for Material, but next to nothing like an
                // iOS 26 sheet, where the action row is a full-width strip
                // split evenly by a hairline divider. A thin top divider
                // now separates the button strip from the content above it
                // (its own subtle "glass rim", matching the panel's outer
                // border), and each button sits in an equal-width half
                // (single button = full width) instead of floating at one
                // corner.
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f), thickness = 1.dp)
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dismissButton != null) {
                        DialogButtonCell(Modifier.weight(1f).fillMaxHeight()) {
                            dismissButton()
                        }
                        VerticalDivider(color = Color.White.copy(alpha = 0.14f), thickness = 1.dp)
                    }
                    DialogButtonCell(Modifier.weight(1f).fillMaxHeight()) {
                        confirmButton()
                    }
                }
            }
        }
    }
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

/**
 * BUG FIXED ("لازم لما اضغط عالمربع كله يتنفذ الامر مو بس الكلمة" — tapping
 * anywhere in the button's half of the row must trigger the action, not
 * just the word): the button row used to place [dismissButton]/
 * [confirmButton] inside a plain `Box(..., contentAlignment = Center)`.
 * That Box gives its child *loose* constraints (max size only), so a
 * [androidx.compose.material3.TextButton] — which never asked to fill —
 * just wrapped its own short label ("إلغاء", "حفظ") and centered at its own
 * small size. Everything else in that half-width, 52.dp-tall cell was dead
 * space: visually part of the button, but not clickable.
 *
 * This replaces that Box with a tiny custom [Layout] that measures its
 * single child with *tight* constraints fixed to the cell's full size
 * (`Constraints.fixed`), instead of the loose ones a Box would hand down.
 * A measured child cannot opt out of tight constraints — it must report
 * exactly that size — so the TextButton's own `Surface` (the thing that
 * actually carries the ripple/click handling) is forced to expand to fill
 * the entire cell, and only *then* centers its short text label inside
 * itself. The net effect: the same visual layout as before (short,
 * centered label), but the whole half of the row is now one real click
 * target, same as an iOS/Material full-bleed sheet action.
 */
@Composable
private fun DialogButtonCell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val forced = Constraints.fixed(constraints.maxWidth, constraints.maxHeight)
        val placeable = measurables.first().measure(forced)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(0, 0)
        }
    }
}
