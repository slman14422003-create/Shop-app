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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
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
 * iOS 26 REDESIGN: every item is a fixed-width segment showing icon +
 * label at all times (a real iOS tab bar never hides a tab's label), and
 * a single rounded highlight slides between segments with a spring as the
 * selection changes, instead of the whole capsule growing/shrinking to
 * fit a label that only the selected tab used to show.
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
    quickAction: QuickAction? = null
) {
    // Keeps rendering the last non-null action while its own exit
    // animation plays, so switching to a page with no action (Home) fades
    // the button away instead of yanking it off-screen the instant
    // `quickAction` turns null.
    var lastQuickAction by remember { mutableStateOf<QuickAction?>(null) }
    LaunchedEffect(quickAction) { if (quickAction != null) lastQuickAction = quickAction }

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
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            // iOS 26 REDESIGN: real iOS tab bars keep every item's icon +
            // label visible all the time — only the tint changes on
            // selection — instead of collapsing unselected items down to a
            // bare icon and morphing the whole capsule's width as the
            // selection changes (the previous "only the selected item gets
            // a label" pill). Each item is now a fixed-width segment, so
            // the capsule's overall width is constant, and a single
            // rounded highlight slides between segments with a spring
            // (see `indicatorOffset` below) — the signature iOS 26 tab-bar
            // motion — instead of the bar itself resizing.
            val itemWidth = 66.dp
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = MotionSpecs.tabIndicatorSpring(),
                label = "floatingNavIndicatorOffset"
            )
            Box(
                Modifier
                    .liquidGlassSurface(RoundedCornerShape(50))
                    .padding(6.dp)
            ) {
                // Sliding selection highlight — a single rounded segment
                // that springs from one item's position to the next,
                // instead of each item drawing its own separate highlight.
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .height(56.dp)
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
            .size(52.dp)
            .liquidGlassSurface(CircleShape, elevation = 10.dp)
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
            modifier = Modifier.size(24.dp)
        )
    }
}


/**
 * iOS 26 REDESIGN: a real iOS tab-bar item — icon stacked above its label,
 * both always visible (not just for the selected tab; the previous
 * design only showed a label next to the selected icon and hid the rest,
 * which reads as an Android/Material bottom-nav pattern, not iOS). Only
 * the tint animates between selected/unselected — the sliding highlight
 * segment behind it (drawn once by the parent, see [FloatingBottomNav])
 * is what actually communicates which tab is active.
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

    Column(
        modifier = Modifier
            .width(width)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = BrandOnGradient.copy(alpha = tintAlpha),
            modifier = Modifier.size(22.dp)
        )
        Text(
            item.label,
            color = BrandOnGradient.copy(alpha = tintAlpha),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
