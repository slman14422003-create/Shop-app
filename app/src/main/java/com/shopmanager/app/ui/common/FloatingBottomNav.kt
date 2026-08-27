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
 * The capsule's width hugs its content (not fillMaxWidth), and
 * [animateContentSize] animates it growing/shrinking as the selected tab's
 * label appears/disappears — the small "morphing pill" motion that's the
 * signature of this floating-nav style: only the selected item shows its
 * label inside a filled pill, the rest are icon-only.
 */
@Composable
fun FloatingBottomNav(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Box(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .animateContentSize()
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
