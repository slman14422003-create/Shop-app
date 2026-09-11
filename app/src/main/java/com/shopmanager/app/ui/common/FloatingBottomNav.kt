package com.shopmanager.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One item in [FloatingBottomNav]. */
data class BottomNavItem(val icon: ImageVector, val label: String)

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
 * "الشريط السفلي العائم" (One UI 8.5-style floating bottom nav): a single
 * glass capsule that floats above the content with clear margin on every
 * side, instead of the previous edge-to-edge [androidx.compose.material3.NavigationBar]
 * that sat flush against the screen's bottom edge.
 *
 * Reuses the same [liquidGlassSurface] brand-gradient glass treatment as
 * every header in the app (see LiquidGlass.kt) — including its floating
 * drop shadow — so the top header and this bottom bar read as one
 * cohesive glass design language rather than two different styles.
 *
 * ICON-ONLY REDESIGN: every item shows just its icon (no label under it)
 * — a single rounded highlight still slides between segments with a
 * spring as the selection changes, same motion as before, just without
 * the text. This isn't only a style choice: with a label under every one
 * of the 4 tabs, the pill was wide enough that on the one screen where
 * BOTH [quickAction] and [secondaryAction] show at once (المواد والأسعار's
 * الأسعار tab — "مادة جديدة" + "حفظ الأسعار") the combined row (pill +
 * both circular buttons) no longer fit most phone screens. The outer Box
 * here only centers the Row, it never scrolls or shrinks it, so the
 * overflow didn't get clipped/scrolled into view — it was pushed
 * off-screen entirely, and since [secondaryAction] is the Row's LAST
 * child (which lands on the far LEFT in this app's forced-RTL layout —
 * see its own doc comment below), it was consistently the one that
 * silently disappeared off the left edge. Icon-only items are narrow
 * enough that the full row (even with both buttons showing) comfortably
 * fits within a normal screen width again.
 */
@Composable
fun FloatingBottomNav(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // "زر الإضافة السريع": an optional circular action rendered as part of
    // this exact same row as the pill — not a separate FloatingActionButton
    // anchored to the screen's own corner (that's what used to make it
    // drift off to the side, overlap list content, and sit disconnected
    // from the nav bar; see the caller in MainActivity for the full
    // before/after). Passing null (the default — used whenever the current
    // page has nothing to "add", e.g. the Home tab) hides it with a
    // fade+scale instead of leaving an empty gap in the row. Because it's
    // a sibling of the pill inside the very same Row, it always sits
    // directly beside the pill and moves/resizes with it automatically —
    // there is no separate position to keep in sync.
    quickAction: QuickAction? = null,
    // "زر ثانوي من الجهة اليسرى": a second optional circular action, drawn
    // as the LAST child of the same Row as the pill (quickAction is the
    // FIRST child) — so in this app's forced-RTL layout it lands on the
    // opposite side from `quickAction` (right) instead of stacking on top
    // of it. Added for actions that only make sense on one particular tab
    // of one particular page (e.g. "حفظ كل الأسعار" on المواد والأسعار's
    // الأسعار tab) without disturbing `quickAction`'s own page-level
    // meaning (e.g. "مادة جديدة", still shown on the same page). Same
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

    // ROOT FIX ("الشريط العائم خلفيته بيضاء/سوداء"): this composable itself
    // was never the problem — it was always transparent outside the pill
    // (see liquidGlassSurface below, applied only to the inner Row). The
    // solid white/black block people were seeing came from *how the caller
    // places this composable*, not from anything drawn in here. When used
    // as a Scaffold `bottomBar`, Scaffold reserves that slot's full area
    // and paints its own `containerColor` (defaults to
    // colorScheme.background — flat white in light mode, near-black in
    // dark) behind it — and separately, Scaffold also shrinks the actual
    // page content to stop short of that slot, so there was never any real
    // page content behind these transparent margins either, just that flat
    // Scaffold color showing through. No amount of changing colors *in
    // this file* could fix that, because the rectangle wasn't drawn here.
    // The real fix is in MainActivity: this is no longer placed as a
    // Scaffold bottomBar at all. It's now a plain overlay, layered via
    // Modifier.align(Alignment.BottomCenter) directly on top of a NavHost
    // that fills the *entire* screen — so the margins around the pill are
    // genuinely transparent over real, live page content (the dashboard
    // list, cards, etc. scrolling underneath), never a separately-painted
    // solid rectangle. `modifier` is how MainActivity supplies that
    // alignment; merged first so callers' positioning wins before this
    // composable's own sizing/inset/margin chain runs.
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            // طلب "تعميم ستايل الزجاج": نفس روح "تصغير المربع" اللي صارت
            // بـ GlassAlertDialog (400.dp → 340.dp عرض أقصى، هامش أوضح من
            // حواف الشاشة) — الهامش الأفقي زاد شوي (28.dp → 32.dp) عشان
            // الكبسولة تبين عائمة بمسافة أوضح عن حافة الشاشة بدل ما تكون
            // شبه ملاصقة لها.
            // Narrowed from 32.dp: icon-only items already freed up most of
            // the width this was compensating for; the smaller margin
            // gives a touch more room back for the two circular buttons on
            // the one screen where both show at once (see the class doc
            // comment above).
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = quickAction != null,
                enter = fadeIn(MotionSpecs.popInSpring()) + androidx.compose.animation.scaleIn(animationSpec = MotionSpecs.popInSpring(), initialScale = 0.6f),
                exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f)
            ) {
                lastQuickAction?.let { action ->
                    QuickActionFab(action)
                }
            }
            // Every item is still a fixed-width segment (so the capsule's
            // overall width stays constant) and a single rounded highlight
            // still slides between segments with a spring
            // (`indicatorOffset` below) as the selection changes — only
            // the label under each icon is gone now. Narrowed from 62.dp
            // now that there's no label text to leave room for; this is
            // the main saving that lets the full row (pill + both
            // circular buttons) fit on screen — see the class doc comment
            // above.
            val itemWidth = 46.dp
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = MotionSpecs.tabIndicatorSpring(),
                label = "floatingNavIndicatorOffset"
            )
            Box(
                Modifier
                    // طلب "تعميم ستايل الزجاج": highlight = false + baseAlpha
                    // = 0.72f — نفس القيمة الموحّدة لكل لوحات الزجاج
                    // بالهيدرات (راجع الشرح بـ DashboardScreen.kt) عشان
                    // الشريط العلوي والسفلي يبينوا بنفس "لغة" الزجاج.
                    .liquidGlassSurface(
                        RoundedCornerShape(50),
                        highlight = false,
                        baseAlpha = 0.72f
                    )
                    .padding(6.dp)
            ) {
                // Sliding selection highlight — a single rounded segment
                // that springs from one item's position to the next,
                // instead of each item drawing its own separate highlight.
                // Height shrunk to match the icon-only item (was sized for
                // icon+label before).
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .height(44.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                )
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        FloatingNavItem(
                            item = item,
                            selected = index == selectedIndex,
                            width = itemWidth,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = secondaryAction != null,
                enter = fadeIn(MotionSpecs.popInSpring()) + androidx.compose.animation.scaleIn(animationSpec = MotionSpecs.popInSpring(), initialScale = 0.6f),
                exit = fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f)
            ) {
                lastSecondaryAction?.let { action ->
                    QuickActionFab(action)
                }
            }
        }
    }
}

/** Describes the circular quick-add button that floats beside the pill —
 * what icon it shows and what happens when it's tapped. Kept as data
 * (rather than a raw `@Composable () -> Unit`) so [FloatingBottomNav] can
 * give it one consistent glass-circle look for every page instead of each
 * caller styling its own button differently. */
data class QuickAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionFab(action: QuickAction) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "quickActionFabScale"
    )
    Box(
        Modifier
            .scale(scale)
            // Narrowed from 52.dp along with the rest of the pill (see the
            // class doc comment on FloatingBottomNav) — this button and
            // its "حفظ الأسعار" sibling being 52.dp each was a meaningful
            // chunk of why the full row used to overflow the screen when
            // both showed at once.
            .size(46.dp)
            // طلب "تعميم ستايل الزجاج": highlight = false + baseAlpha =
            // 0.72f — نفس قيمة الكبسولة المجاورة لها بالضبط.
            .liquidGlassSurface(
                CircleShape,
                elevation = 10.dp,
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


/**
 * ICON-ONLY REDESIGN: a single centered icon, no label underneath. The
 * item's [BottomNavItem.label] is kept and still used as the
 * `contentDescription` for accessibility (screen readers still announce
 * "الرئيسية", "الديون", etc.) — it's only the on-screen text that's
 * gone. Only the tint animates between selected/unselected — the sliding
 * highlight segment behind it (drawn once by the parent, see
 * [FloatingBottomNav]) is what actually communicates which tab is active.
 */
@Composable
private fun FloatingNavItem(item: BottomNavItem, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "floatingNavItemScale"
    )
    val tintAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = MotionSpecs.contentTween(),
        label = "floatingNavItemTint"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(44.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = BrandOnGradient.copy(alpha = tintAlpha),
            modifier = Modifier.size(22.dp)
        )
    }
}
