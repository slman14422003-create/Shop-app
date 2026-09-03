package com.shopmanager.app.ui.common

import android.graphics.Bitmap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.drawToBitmap
import com.shopmanager.app.ui.theme.LocalGlassMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 *
 * "رقّي الديالوغ ليبين أوضح بالوضعين" (screenshots showed a nearly edge-less,
 * flat-looking panel in light mode specifically): two follow-up fixes,
 * both theme-aware rather than hardcoded to a single look:
 *   - the outer glass rim and the button-row dividers used to be a flat
 *     `Color.White` at low alpha — invisible against this dialog's own
 *     light-mode fill, which is exactly what read as "no border" in the
 *     light-mode screenshot. Both now derive from `colorScheme.onSurface`
 *     (blended with a touch of `primary` for the outer rim), so there's
 *     always a legible edge in *either* theme instead of one that only
 *     shows up in dark mode.
 *   - the brand-color blend in the fill gradient nudged up (0.42/0.30 →
 *     0.48/0.34, alpha 0.42/0.34 → 0.48/0.38) — still soft enough to keep
 *     text legible, but enough of a lift that the panel reads as clearly
 *     tinted with the app's own color rather than a nearly neutral card.
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
    // BUG FIXED ("اللمعه والشفافية ضباب مو مبينة... بدون اضدار اندرويد
    // مطلوب" — the fog isn't showing, and it must not depend on a specific
    // Android version): the previous pass used `Window.setBackgroundBlurRadius`,
    // a real OS blur — but it's API 31+ only, and evidently didn't actually
    // engage on this device either way, so lowering the panel's own alpha
    // just exposed the sharp, unblurred names underneath (exactly what the
    // follow-up screenshot showed). That approach is dropped entirely
    // rather than re-tuned, since no OS blur API works identically (or at
    // all) below API 31, and the user explicitly asked for something that
    // doesn't depend on which Android version the phone is running.
    //
    // Instead, this captures a plain screenshot of whatever's on screen
    // *before* the dialog opens — the parent screen's own view, still
    // sitting in its own window untouched by the dialog's separate
    // overlay window — then shrinks it down hard and scales it back up.
    // That down/up scale is the blur: shrinking to a few dozen pixels
    // wide throws away all the fine detail (text edges, icons), and
    // stretching it back out just smears the few remaining color blobs
    // across the full size — a real pixel blur, not a transparency trick,
    // and pure bitmap math with no OS version or hardware requirement.
    val hostView = LocalView.current
    var frostedBackdrop by remember { mutableStateOf<Bitmap?>(null) }
    // BUG FIXED ("الوضع العادي لازم يشيل تأثيرات الشفافية" — normal/non-glass
    // mode must drop the transparency effects entirely, while glass mode
    // stays exactly as it is): this used to capture and frost a screenshot
    // of whatever's behind the dialog *unconditionally* — MANUAL/CLASSIC
    // ("الوضع العادي") got the exact same blurred-backdrop screenshot and a
    // translucent fill as GLASS did, just with a slightly lighter frost (5
    // passes vs 6) and no sheen/highlight. That's still a transparency
    // effect in "normal" mode, which is exactly what was asked to be
    // removed. Now the (expensive) screenshot+blur work is skipped
    // entirely outside glass mode — frostedBackdrop simply stays null — and
    // the fill below is fully opaque for MANUAL/CLASSIC instead of
    // partially see-through. GLASS mode's own capture/frost strength (6
    // passes) is unchanged.
    val glassModeActiveForBlur = LocalGlassMode.current
    LaunchedEffect(glassModeActiveForBlur) {
        if (!glassModeActiveForBlur) {
            frostedBackdrop = null
            return@LaunchedEffect
        }
        val snapshot = runCatching { hostView.drawToBitmap() }.getOrNull()
        frostedBackdrop = snapshot?.let {
            withContext(Dispatchers.Default) { frostBitmap(it, passes = 6) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { frostedBackdrop?.recycle() }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        // usePlatformDefaultWidth must be off so this Dialog's own root can
        // fillMaxSize() (matching the screenshot it just took) instead of
        // shrink-wrapping to the panel — the panel itself still keeps its
        // own 280–400dp cap below, untouched.
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = properties.decorFitsSystemWindows
        )
    ) {
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

        // BUG FIXED ("شفافية جميلة... مع التناسق مع لوحة الالوان" — nice
        // transparency that still matches the color palette): without real
        // blur, translucency alone just reveals whatever's underneath
        // (list rows, other dialog text) at full clarity, which is what
        // read as messy in the screenshot — the fix for that is not "add
        // blur", it's leaning further into this app's own colors so the
        // panel still reads as a distinct, deliberately-tinted surface
        // rather than a clear window. The blend toward primary/tertiary is
        // stronger than the original (0.24/0.16 → 0.42/0.30) so the panel
        // is visibly "this app's color". Alpha itself is tuned separately
        // right below (it moved again after this — see that note).
        // BUG FIXED ("بحيث ما يبين اللي تحتها" — so what's underneath
        // doesn't show through): the previous pass kept this panel
        // meaningfully see-through (0.86 / 0.78 alpha) since there was no
        // real blur to soften whatever showed behind it — which is exactly
        // why the list row underneath was still legible in the screenshot.
        // Now that [liquidGlassSurface] carries its own bright droplet
        // highlight/rim (see LiquidGlass.kt), the panel itself can push
        // much closer to opaque (0.95 / 0.92) — still technically
        // translucent so it never looks like a flat painted card, but far
        // enough from `1f` that the row behind it never reads clearly.
        // On every Android version now (see the screenshot-blur capture
        // above), there's a genuinely blurred image sitting behind this
        // panel, so the fill can drop back down to real glass-level
        // translucency (0.95/0.92 → 0.62/0.55) without turning messy —
        // what shows through is a soft blur, never sharp list rows.
        // BUG FIXED ("اللمعه ما بدي ياها" — don't want the shine): this
        // panel used to get [liquidGlassSurface]'s glare unconditionally
        // "المربع لازم يكون بدون لمعة" — the glare/glow blobs (bright
        // white/tinted circles) came specifically from turning `highlight`
        // on for this panel; it stays OFF unconditionally, in every color
        // mode including GLASS, so this dialog is always a flat, calm
        // frosted panel — never the glowing-orb look. Only the fill's own
        // opacity and its (optional, subtle) sheen streak change with
        // glass mode.
        // "وضع الجلاس الشفاف الكامل": pushes this dialog a bit more
        // see-through than its resting 0.42/0.34, but not as far as an
        // earlier pass (0.26/0.18) — that low an alpha let the blurred
        // background's own colored icons show through as visible blobs of
        // color, which read as glare just as much as a highlight would.
        // "زد ... الوضوح قليلاً فقط في الوضع الزجاجي": nudged up from
        // 0.36/0.28 — the panel's own text sat a touch too faint against
        // the (now heavier) blur behind it. Still clearly more see-through
        // than the 0.42/0.34 MANUAL/CLASSIC resting value, so glass mode
        // keeps its own distinct, more-transparent character — just
        // readable rather than right at the edge of legibility.
        val glassModeActive = LocalGlassMode.current
        // BUG FIXED (see the frostedBackdrop note above): MANUAL/CLASSIC
        // ("الوضع العادي") used to sit at 0.48/0.38 alpha — still visibly
        // translucent, just without a blur behind it (since the backdrop
        // capture used to run unconditionally). Now that no backdrop is
        // captured for non-glass mode at all, the fill goes fully opaque
        // (1f/1f) to match — no transparency effect left in normal mode.
        // GLASS mode's own resting alpha (0.46/0.36) is unchanged.
        val fillAlphaTop = if (glassModeActive) 0.46f else 1f
        val fillAlphaBottom = if (glassModeActive) 0.36f else 1f
        val gradientTop = androidx.compose.ui.graphics.lerp(
            resolvedContainer, MaterialTheme.colorScheme.primary, 0.48f
        ).copy(alpha = fillAlphaTop)
        val gradientBottom = androidx.compose.ui.graphics.lerp(
            resolvedContainer, MaterialTheme.colorScheme.tertiary, 0.34f
        ).copy(alpha = fillAlphaBottom)
        // "رقّي الحواف لتبين بالوضعين": see `rimColor`'s own doc in
        // LiquidGlass.kt — a flat white rim washes out against this
        // dialog's light-mode background, which is exactly what read as
        // "edge-less" in the light-mode screenshot. Blending onSurface
        // (the theme's own "ink" — dark in light mode, light in dark mode)
        // with a touch of primary gives a rim that's always legible
        // against *this* panel's own fill, in both themes, while still
        // carrying a hint of the app's brand color rather than a plain
        // gray line.
        val dialogRimColor = androidx.compose.ui.graphics.lerp(
            MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, 0.35f
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(contentAlpha)
                    // BUG FIXED ("المربع كبير شوي" — the box is a bit too
                    // big): 400.dp on a normal ~360–400dp-wide phone left
                    // almost no side margin, reading as edge-to-edge rather
                    // than a floating card. Narrower cap (340.dp) leaves a
                    // visible gutter on both sides, same as an iOS alert.
                    .widthIn(min = 260.dp, max = 340.dp)
                    .liquidGlassSurface(
                        shape = shape,
                        baseBrush = Brush.verticalGradient(
                            listOf(gradientTop, gradientBottom)
                        ),
                        elevation = 24.dp,
                        // A gentle diagonal glide only in glass mode — no
                        // corner glow (`highlight` above), just a faint,
                        // slow streak; MANUAL/CLASSIC stay fully static.
                        sheen = glassModeActive,
                        highlight = false,
                        animated = glassModeActive,
                        rimColor = dialogRimColor
                    )
            ) {
            // BUG FIXED ("تحته كلشي مشوه مو بلور" — everything under it is
            // distorted, not blurred): this used to be a *separate*, full
            // dialog-window-sized Image drawn behind everything — since the
            // dialog window is the *whole screen* (usePlatformDefaultWidth
            // = false), that meant the blurred screenshot covered the
            // entire screen, not just the area under this panel. Outside
            // the panel that's wrong on two counts: it has no business
            // being visible there at all (that region should just be the
            // normal dim scrim), and at full screen size the same blurred
            // bitmap stretched no differently than before, so it read as a
            // warped, smeared version of the real background rather than
            // a blur. Moving the Image to be *this Box's own child* (using
            // matchParentSize, so it's sized to exactly this panel, not
            // the screen) and relying on liquidGlassSurface's own
            // `.clip(shape)` (already applied via the modifier chain above
            // — clipping applies to every child, including this Image)
            // confines the blurred image to precisely the panel's rounded
            // bounds. Everywhere outside the panel is untouched by it now.
            frostedBackdrop?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            }
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
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), thickness = 1.dp)
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dismissButton != null) {
                        DialogButtonCell(Modifier.weight(1f).fillMaxHeight()) {
                            dismissButton()
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), thickness = 1.dp)
                    }
                    DialogButtonCell(Modifier.weight(1f).fillMaxHeight()) {
                        confirmButton()
                    }
                }
            }
            }
        }
    }
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

/**
 * Cheap, version-independent blur: repeatedly halve the screenshot's size,
 * then stretch the tiny result back up to full size.
 *
 * BUG FIXED ("البلور ورا المربع شكله مو حلو" — the blur behind the panel
 * looked bad): the first version did this in one huge jump (straight to
 * ~6% size, then straight back up). A single giant scale like that doesn't
 * properly average enough source pixels into each destination pixel, so
 * instead of a smooth blur it came out as a hard-edged mosaic of flat
 * color blocks — visible as actual square tiles in the screenshot, not
 * blur at all. Repeated *halving* fixes this: each individual step is a
 * mild, well-sampled 2x reduction (real box-filtered averaging, not a
 * lossy jump), and those small clean averages stack across the five
 * steps into a genuinely smooth result — the same trick GPU mipmaps use
 * to blur cheaply. Scaling the final tiny bitmap back up to full size in
 * one shot is fine (that direction doesn't have the same sampling
 * problem), and is still just [Bitmap] math with no OS version or
 * hardware requirement.
 */
private fun frostBitmap(source: Bitmap, passes: Int = 5): Bitmap {
    return try {
        var current = source
        // `passes` halvings — 5 (the original, non-glass-mode strength)
        // lands around ~1/32 per dimension (~0.1% of the pixel count);
        // each extra pass roughly halves what detail is left again, so 6
        // (glass mode) is a visibly heavier, softer frost, still reached
        // gently (one mild 2x step at a time) to stay smooth instead of
        // blocky.
        repeat(passes) {
            val nextWidth = maxOf(1, current.width / 2)
            val nextHeight = maxOf(1, current.height / 2)
            val next = Bitmap.createScaledBitmap(current, nextWidth, nextHeight, true)
            if (current !== source) current.recycle()
            current = next
        }
        val blurred = Bitmap.createScaledBitmap(current, source.width, source.height, true)
        if (current !== blurred) current.recycle()
        if (source !== blurred) source.recycle()
        blurred
    } catch (t: Throwable) {
        source
    }
}

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
