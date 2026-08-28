package com.shopmanager.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
 * The capsule itself hugs its content width (its outer wrapper is
 * fillMaxWidth so the capsule is centered against the real screen edges,
 * but the pill you see is only as wide as its icons/label need), and
 * [animateContentSize] animates it growing/shrinking as the selected tab's
 * label appears/disappears — the small "morphing pill" motion that's the
 * signature of this floating-nav style: only the selected item shows its
 * label inside a filled pill, the rest are icon-only.
 */
@Composable
fun FloatingBottomNav(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
            Modifier
                .animateContentSize(animationSpec = MotionSpecs.expandSpring())
                .liquidGlassSurface(RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "floatingNavItemScale"
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.24f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (selected) 18.dp else 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = BrandOnGradient.copy(alpha = if (selected) 1f else 0.62f),
            modifier = Modifier.size(22.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(7.dp))
                Text(
                    item.label,
                    color = BrandOnGradient,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
