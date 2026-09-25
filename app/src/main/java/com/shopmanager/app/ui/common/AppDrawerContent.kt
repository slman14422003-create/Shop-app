package com.shopmanager.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp

/**
 * "قائمة جانبية بدل الشريط السفلي": the side drawer that replaced the old
 * floating bottom tab bar (see FloatingBottomNav.kt / MainActivity's
 * hamburger button). Laid out like Claude's own app drawer — a plain
 * icon+label row per destination, no card background, generous row
 * height — with الإعدادات pinned below a divider at the bottom instead of
 * scrolling with the other items, since it's a destination outside the
 * main tabs rather than one of them.
 *
 * BUG FIXED ("المنيو الجانبية ما فيها خيار الشاشة الرئيسية — إذا بدي ارجع
 * لازم اطلع من التطبيق"): the drawer used to list only the three
 * secondary tabs (Debts/Materials/Notes) and relied on the hardware/back
 * gesture — which simply exits the app instead of returning to it — to get
 * back to Home, since Home had no entry of its own anywhere in this list.
 * الشاشة الرئيسية is now the first row here (page 0, same PAGE_DASHBOARD the
 * pager already uses), so returning to the home screen is always a single
 * tap away like every other destination instead of an app relaunch.
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
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight()) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Text(
                "إدارة المحل",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            drawerNavItems.forEach { item ->
                DrawerRow(
                    icon = item.icon,
                    label = item.label,
                    selected = item.page == selectedPage,
                    onClick = { onSelectPage(item.page) }
                )
            }
            // Pushes الإعدادات (and its divider) to the bottom of the
            // sheet regardless of how tall the items above are.
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            DrawerRow(
                icon = Icons.Default.Settings,
                label = "الإعدادات",
                selected = false,
                onClick = onOpenSettings
            )
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}
