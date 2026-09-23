package com.shopmanager.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The floating pill's actual on-screen height (including its own top/
 * bottom margins), in dp — 0.dp when it isn't showing at all.
 *
 * WHY THIS EXISTS: once [FloatingBottomNav] became a true overlay drawn on
 * top of the page (see MainActivity) instead of a Scaffold `bottomBar`
 * slot, screens underneath stopped automatically getting bottom clearance
 * for it — Scaffold used to hand that clearance out for free via its
 * `padding`. Anything a screen anchors to its own bottom edge (a
 * FloatingActionButton, a list's last row) now needs to know how tall the
 * pill floating on top of it actually is, so it can pad itself clear of it
 * instead of being covered.
 *
 * Deliberately measured at runtime (via `Modifier.onSizeChanged` on the
 * real composable in MainActivity) and threaded down through this
 * CompositionLocal, rather than hard-coded as a fixed dp guess: the pill's
 * true height depends on the device's gesture/navigation-bar inset (which
 * varies by device/OS) plus its own content padding — a guessed constant
 * would drift out of sync the moment either changes and quietly reopen
 * this exact bug. Screens that read it should still add their own small
 * extra gap on top (see DebtsScreen/MaterialsScreen) so content doesn't
 * sit flush against the pill.
 */
val LocalFloatingBottomNavHeight = compositionLocalOf { 0.dp }

/**
 * BUG FIXED ("رسالة تم الحذف/السداد تظهر تحت الشريط السفلي"): every
 * screen's own `Scaffold(snackbarHost = { SnackbarHost(snackbarHost) })`
 * places that host at the true bottom edge of the screen's content — the
 * same edge [FloatingBottomNav] floats over. Since the nav pill is drawn
 * *after* (see MainActivity's Box: NavHost first, [FloatingBottomNav]
 * layered on top of it), it visually sits in front of a plain
 * `SnackbarHost`, hiding the exact "تم الحذف"/"تم السداد" confirmation
 * the person needs to see right after deleting/settling something. Every
 * screen with a snackbar (Debts/PersonDetail/Materials/MaterialCatalog/
 * Notes) should call this instead of `SnackbarHost` directly: it pads the
 * host up by [LocalFloatingBottomNavHeight] (plus a small extra gap) so
 * it always lands above the floating pill — and by exactly 0.dp extra
 * when the pill isn't showing, so nothing shifts on screens/states where
 * there's no pill to clear.
 */
@Composable
fun GlassSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = LocalFloatingBottomNavHeight.current + 12.dp)
    )
}

/**
 * "أزرار عائمة سريعة" (floating quick-action buttons): the small circular
 * add/save buttons that float above the current tab's content, anchored to
 * opposite bottom corners — this app's tab switching itself now happens
 * through the side drawer (see AppDrawerContent.kt) opened by the
 * hamburger button in MainActivity, not through a bottom tab bar. This
 * composable used to also render that tab bar as a glass capsule; it now
 * only positions [quickAction]/[secondaryAction], each shown or hidden
 * independently with a fade+scale as the current page changes.
 */
@Composable
fun FloatingQuickActions(
    modifier: Modifier = Modifier,
    // "زر الإضافة السريع": an optional circular action anchored to the
    // screen's bottom-end corner (bottom-right in this app's forced-RTL
    // layout). Passing null (the default — used whenever the current page
    // has nothing to "add", e.g. the Home tab) hides it with a fade+scale
    // instead of leaving an empty circle behind.
    quickAction: QuickAction? = null,
    // "زر ثانوي من الجهة اليسرى": a second optional circular action,
    // anchored to the opposite corner (bottom-start) so it never overlaps
    // `quickAction` on the one screen where both show at once (المواد
    // والأسعار's الأسعار تبويب: "مادة جديدة" + "حفظ الأسعار"). Same
    // null-hides-with-fade behavior as `quickAction`.
    secondaryAction: QuickAction? = null
) {
    // Keeps rendering the last non-null action while its own exit
    // animation plays, so switching to a page with no action (Home) fades
    // the button away instead of yanking it off-screen the instant
    // `quickAction` turns null.
    var lastQuickAction by remember { mutableStateOf<QuickAction?>(null) }
    LaunchedEffect(quickAction) { if (quickAction != null) lastQuickAction = quickAction }
    var lastSecondaryAction by remember { mutableStateOf<QuickAction?>(null) }
    LaunchedEffect(secondaryAction) { if (secondaryAction != null) lastSecondaryAction = secondaryAction }

    // `modifier` is how MainActivity supplies `Modifier.align(BottomCenter)`
    // over a NavHost that fills the entire screen — this Box only reserves
    // its own bottom strip (matching the navigation-bar inset plus a
    // little margin) and never paints a background, so both corners stay
    // genuinely transparent over whichever tab is showing underneath.
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        AnimatedVisibility(
            visible = quickAction != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(MotionSpecs.popInSpring()) + androidx.compose.animation.scaleIn(animationSpec = MotionSpecs.popInSpring(), initialScale = 0.6f),
            exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f)
        ) {
            lastQuickAction?.let { action -> QuickActionFab(action, modifier = Modifier.size(52.dp)) }
        }
        AnimatedVisibility(
            visible = secondaryAction != null,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(MotionSpecs.popInSpring()) + androidx.compose.animation.scaleIn(animationSpec = MotionSpecs.popInSpring(), initialScale = 0.6f),
            exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f)
        ) {
            lastSecondaryAction?.let { action -> QuickActionFab(action, modifier = Modifier.size(52.dp)) }
        }
    }
}

/** Describes a floating circular button (quick-add, save, or the drawer's
 * hamburger trigger) — what icon it shows and what happens when it's
 * tapped. Kept as data (rather than a raw `@Composable () -> Unit`) so
 * [QuickActionFab] can give every one of them the same consistent
 * glass-circle look instead of each caller styling its own button. */
data class QuickAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

@Composable
fun QuickActionFab(action: QuickAction, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "quickActionFabScale"
    )
    Box(
        modifier
            .scale(scale)
            // طلب "تعميم ستايل الزجاج": highlight = false + baseAlpha =
            // 0.72f — نفس قيمة الكبسولة المجاورة لها بالضبط.
            .liquidGlassSurface(
                CircleShape,
                elevation = 2.dp,
                highlight = false,
                baseAlpha = 0.72f
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = action.onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            action.icon,
            contentDescription = action.contentDescription,
            tint = BrandOnGradient,
            modifier = Modifier.size(22.dp)
        )
    }
}

