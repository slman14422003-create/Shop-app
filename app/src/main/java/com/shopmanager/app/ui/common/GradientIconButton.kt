package com.shopmanager.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The circular action-button used for icons that sit directly on top of the
 * brand gradient header (settings on the dashboard, share on the materials
 * screen, etc).
 *
 * REDESIGNED (زجاج سائل / liquid glass): this used to be a fully opaque
 * solid-white circle. It's now a thin translucent glass chip — see
 * [GlassIconButton] for the actual implementation and for why it never
 * applies a blur to its own content. The function name/signature here is
 * unchanged on purpose so every existing call site (DashboardHeader,
 * MaterialsHeader) picks up the new look automatically with no call-site
 * changes needed.
 */
@Composable
fun GradientIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    GlassIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        tint = tint
    )
}
