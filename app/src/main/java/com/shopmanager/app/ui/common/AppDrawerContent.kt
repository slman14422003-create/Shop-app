package com.shopmanager.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.LocalSemanticColors

/**
 * "قائمة جانبية بدل الشريط السفلي": the side drawer that replaced the old
 * floating bottom tab bar (see FloatingBottomNav.kt / MainActivity's
 * hamburger button). Laid out like Claude's own app drawer — a row per
 * destination, no card background, generous row height — with الإعدادات
 * pinned below a divider at the bottom instead of scrolling with the other
 * items, since it's a destination outside the main tabs rather than one of
 * them.
 *
 * BUG FIXED ("المنيو الجانبية ما فيها خيار الشاشة الرئيسية — إذا بدي ارجع
 * لازم اطلع من التطبيق"): the drawer used to list only the three
 * secondary tabs (Debts/Materials/Notes) and relied on the hardware/back
 * gesture — which simply exits the app instead of returning to it — to get
 * back to Home, since Home had no entry of its own anywhere in this list.
 * الشاشة الرئيسية is now the first row here (page 0, same PAGE_DASHBOARD the
 * pager already uses), so returning to the home screen is always a single
 * tap away like every other destination instead of an app relaunch.
 *
 * REDESIGN ("اعد تصميم الرموز في قائمة المنيو بشكل عصري جميل"): each row's
 * icon used to be a bare glyph tinted a single flat color — every
 * destination looked identical apart from its outline. Every icon now sits
 * inside its own soft, color-coded circular badge (same "tinted circle
 * behind a small icon" language DashboardScreen already uses for its own
 * stat/quick-action badges), so the list reads as a set of distinct,
 * colorful destinations at a glance instead of a plain monochrome list.
 * The selected row's badge deepens to a stronger tint of that same accent
 * (see the BUG FIXED note on itemAccents/DrawerRow below for why it's a
 * tint and not a solid fill) so the current tab is unmistakable even with
 * every row now carrying color.
 *
 * MOVED ("انقل ايقونة المسؤول الى المنيو الى جانب الاعدادات"): the hidden
 * لوحة المسؤول button used to live in DashboardScreen's own header,
 * floating on the Home tab specifically. It's now anchored here instead,
 * right beside الإعدادات in the pinned bottom row — a single, predictable
 * home for both "meta" destinations regardless of which tab happens to be
 * open, rather than one of them being tab-specific. It keeps its original
 * PIN-gated behavior (see onOpenAdmin/AdminPinDialog in DashboardScreen,
 * now hoisted up to where this drawer lives) — this is only where the
 * *button* sits now, not a change to what tapping it requires.
 */
private data class DrawerNavItem(val icon: ImageVector, val label: String, val page: Int)

private val drawerNavItems = listOf(
    DrawerNavItem(Icons.Default.Home, "الشاشة الرئيسية", page = 0),
    DrawerNavItem(Icons.Default.AttachMoney, "الديون", page = 1),
    DrawerNavItem(Icons.Default.Inventory2, "المواد والأسعار", page = 2),
    DrawerNavItem(Icons.Default.Notes, "ملاحظات هامة", page = 3)
)

@Composable
fun AppDrawerContent(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit = {}
) {
    val semantic = LocalSemanticColors.current
    // One accent per destination so the badges read as distinct, colorful
    // stops rather than four copies of the same tint.
    //
    // BUG FIXED ("شوف كيف لونها ابيض بدهم تناسق اكثر" — الشاشة الرئيسية's
    // badge was unreadable when selected): this used to start with
    // `MaterialTheme.colorScheme.primary`. In this app's own palette
    // `primary` is literally the neutral text tone (near-white in dark
    // mode, near-black in light — see the "NEUTRALIZED" note in
    // Palette.kt), not a real accent color. DrawerRow below fills the
    // selected badge solid with its accent and draws the icon in white on
    // top of it — white-on-near-white, i.e. an all-but-invisible icon, and
    // visually the odd one out next to the other rows' actual colors
    // (gold/gray/gold). `semantic.success` (a real green) replaces it here
    // so every row — selected or not — carries a genuine, legible color
    // from the same muted family the rest of the app already uses for
    // status accents.
    val itemAccents = listOf(
        semantic.success,
        semantic.warning,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight()) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Text(
                "إدارة المحل",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            drawerNavItems.forEachIndexed { index, item ->
                DrawerRow(
                    icon = item.icon,
                    label = item.label,
                    accent = itemAccents[index % itemAccents.size],
                    selected = item.page == selectedPage,
                    onClick = { onSelectPage(item.page) }
                )
            }
            // Pushes الإعدادات/لوحة المسؤول (and their divider) to the
            // bottom of the sheet regardless of how tall the items above
            // are.
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawerRow(
                    icon = Icons.Default.Settings,
                    label = "الإعدادات",
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    selected = false,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
                IconBadge(
                    icon = Icons.Rounded.AdminPanelSettings,
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = "لوحة المسؤول",
                    onClick = onOpenAdmin
                )
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // BUG FIXED (see the itemAccents note above): the badge used to
        // swap to a *solid* accent fill with a white icon on selection —
        // fine for a strong color, but unreadable for any accent close to
        // white (exactly what `primary` was). The icon now always renders
        // in its own accent color at full opacity, on a softly-tinted
        // circle of that same accent; only the circle's tint strength
        // changes with selection (plus the row's own highlighted
        // background/bold label below). This can never go invisible,
        // whichever accent a row uses, and every badge — selected or not —
        // now reads as the same consistent, colorful family.
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (selected) 0.24f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** A single round icon-only badge/button — used for لوحة المسؤول beside
 * الإعدادات, where a full label row would crowd the pinned bottom row. */
@Composable
private fun IconBadge(
    icon: ImageVector,
    accent: Color,
    contentDescription: String?,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
    }
}
