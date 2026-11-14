package com.shopmanager.app.ui.common

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.shopmanager.app.data.performance.MotionSpecs

private data class MenuEntry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val page: Int?, val id: String = "")

private val menuEntries = listOf(
    MenuEntry(Icons.Default.Home, "الشاشة الرئيسية", 0),
    MenuEntry(Icons.Default.AttachMoney, "الديون", 1),
    MenuEntry(Icons.Default.Inventory2, "المواد والأسعار", 2),
    MenuEntry(Icons.Default.Forum, "الدردشة المحلية", 3),
    MenuEntry(Icons.Default.StickyNote2, "الملاحظات", null, "notes"),
    MenuEntry(Icons.Default.AutoAwesome, "إيجاد المواد الناقصة", null, "finder")
)

/**
 * قائمة منسدلة بأسلوب الآيفون: تنبثق تحت زر القائمة مباشرة من زاويته (تكبير + تلاشٍ
 * من نقطة الزر)، بطاقة بزوايا مدوّرة وصفوف مفصولة بخطوط رفيعة، علامة ✓ للصفحة الحالية.
 * الإعدادات ولوحة المسؤول انتقلا إلى واجهة زر الإحصائيات.
 */
@Composable
fun AppMenuPopup(
    visible: Boolean,
    selectedPage: Int,
    onDismiss: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onOpenMissingFinder: () -> Unit,
    onOpenNotes: () -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState) return

    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)
    val yOffset = topInset + with(density) { 56.dp.roundToPx() }
    val xOffset = with(density) { 10.dp.roundToPx() }
    val dismiss = rememberUpdatedState(onDismiss)

    val transition = updateTransition(state, label = "menuPopup")
    val tier = LocalPerformanceTier.current
    // الأداء العالي: انبثاق نابض + بلور؛ المتوازن: تلاشٍ وتكبير خفيف بلا بلور؛ المنخفض: ظهور فوري.
    val rich = tier == PerformanceTier.STANDARD
    val glass = rich && blurEnabled(strongOnly = true)
    val progress = transition.animateFloat(
        transitionSpec = {
            when {
                tier == PerformanceTier.LOW -> snap()
                targetState && rich -> spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                targetState -> tween(180, easing = MotionSpecs.claudeEasing)
                else -> tween(if (rich) 170 else 110, easing = MotionSpecs.standardEasing)
            }
        },
        label = "menuProgress"
    ) { if (it) 1f else 0f }
    val shape = RoundedCornerShape(AppGroupLargeRadius + 2.dp)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(xOffset, yOffset),
        onDismissRequest = { dismiss.value() },
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier
                .width(264.dp)
                .graphicsLayer {
                    val p = progress.value
                    val from = if (rich) 0.5f else 0.9f
                    val s = from + (1f - from) * p
                    scaleX = s
                    scaleY = s
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            shape = shape,
            color = if (glass) Color.Transparent else cs.surfaceContainerHigh,
            shadowElevation = if (tier == PerformanceTier.LOW) 0.dp else 12.dp,
            border = if (tier == PerformanceTier.LOW) BorderStroke(0.5.dp, cs.outlineVariant) else null
        ) {
            Box {
            if (glass) {
                BlurBackdrop(
                    Modifier.matchParentSize().clip(shape),
                    tint = cs.surfaceContainerHigh.copy(alpha = 0.62f),
                    radius = 14f
                )
            }
            Column(Modifier.clip(shape)) {
                menuEntries.forEachIndexed { index, e ->
                    if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = cs.outlineVariant.copy(alpha = 0.6f))
                    val selected = e.page != null && e.page == selectedPage
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable(role = Role.Button) {
                                when {
                                    e.page != null -> onSelectPage(e.page)
                                    e.id == "notes" -> onOpenNotes()
                                    else -> onOpenMissingFinder()
                                }
                            }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (selected) Icon(Icons.Default.Check, null, tint = cs.onSurface, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            e.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = cs.onSurface
                        )
                        if (e.id == "finder") {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(cs.onSurface)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cs.surface)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(e.icon, null, tint = cs.onSurface, modifier = Modifier.size(22.dp))
                    }
                }
            }
            }
        }
    }
}
