package com.shopmanager.app.ui.materials

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.materials.weightSummary
import com.shopmanager.app.data.materials.linePrice
import com.shopmanager.app.data.materials.priceSummary
import androidx.compose.runtime.saveable.rememberSaveable
import com.shopmanager.app.ui.common.AppCapsuleSearchField
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.theme.LocalSemanticColors
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.shopmanager.app.ui.common.ActionSpec
import com.shopmanager.app.ui.common.sortedArabicBy
import com.shopmanager.app.ui.common.AppActionPill
import androidx.compose.material.icons.filled.Close
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.listItemEntrance

/**
 * Large-title header matching [com.shopmanager.app.ui.dashboard.DashboardScreen]'s
 * new look: bold oversized title + opaque circular share button on a flat
 * brand-gradient panel with rounded bottom corners, and a pill-shaped
 * segmented control for the المواد/الأسعار tabs instead of Material's
 * underlined [TabRow] - the underline style read as mismatched sitting
 * right below a solid gradient block. The whole thing is one continuous
 * panel so title, actions and tabs read as one cohesive header instead of
 * two stacked bars.
 */
@Composable
internal fun MaterialsHeader(
    tab: Int,
    onTogglePrices: () -> Unit,
    onShare: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onAddNew: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        ScreenTopBar(
            title = if (tab == 0) "المواد والأسعار" else "الأسعار",
            reservedIcons = 2,
            navigation = {
                ScreenIconButton(
                    icon = Icons.Default.Menu,
                    contentDescription = "القائمة",
                    onClick = onOpenDrawer
                )
            },
            actions = {
                AppActionPill(
                    height = 42.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    actions = listOf(
                        if (tab == 0) ActionSpec(Icons.Default.Sell, "الأسعار") { onTogglePrices() }
                        else ActionSpec(Icons.Default.Inventory2, "المواد") { onTogglePrices() },
                        ActionSpec(Icons.Rounded.Share, "مشاركة") { onShare() }
                    )
                )
            }
        )
        AppCapsuleSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = if (tab == 0) "ابحث عن مادة..." else "ابحث عن سعر مادة...",
            onAdd = if (tab == 0) onAddNew else null,
            modifier = Modifier.padding(start = AppScreenPadding, end = AppScreenPadding, bottom = AppSpaceSm)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MaterialsList(
    materials: List<Material>,
    prices: Map<String, Double>,
    animateSummary: Boolean,
    searching: Boolean,
    onClearAll: () -> Unit,
    onEdit: (Material) -> Unit,
    onDelete: (Material) -> Unit,
    onToggleImportant: (Material) -> Unit,
    groupOf: (String) -> String
) {
    if (materials.isEmpty()) {
        EmptyState(
            icon = if (searching) Icons.Default.SearchOff else Icons.Default.Inventory2,
            text = if (searching) "لا توجد نتائج" else "لا توجد نواقص حالياً\nاضغط \"مادة جديدة\" لإضافة أول نقص"
        )
        return
    }
    // BUG FIXED: the trailing `Spacer(height = 72.dp)` used to be a guessed
    // stand-in for "roughly the FAB's height" so the last row could clear
    // it. It never accounted for the floating nav pill sitting below the
    // FAB too, and a fixed 72dp silently drifts wrong the moment either
    // one's real size changes. Replaced with real contentPadding sized off
    // the pill's actual measured height (LocalFloatingBottomNavHeight) plus
    // a fixed safety margin, computed once below, rather than a hardcoded
    // row. "مادة جديدة" has since moved off this screen too (same shared
    // quick-add "+" beside the pill as every other tab), so this margin is
    // no longer one specific FAB's height either — kept at the same size
    // regardless, as this list's general clearance against the pill's
    // transparent side margins, matching every other list in the app.
    val bottomSafetyMargin = 56.dp + 24.dp
    val bottomClearance = LocalFloatingBottomNavHeight.current + bottomSafetyMargin

    var listTab by rememberSaveable { mutableStateOf(0) }
    val sortedMaterials = remember(materials, searching) {
        if (searching) materials else materials.sortedArabicBy { it.name }
    }
    val importantItems = remember(sortedMaterials) { sortedMaterials.filter { it.important } }
    val regularItems = remember(sortedMaterials) { sortedMaterials.filter { !it.important } }
    val visibleItems = when (listTab) {
        0 -> importantItems
        1 -> regularItems
        else -> sortedMaterials
    }
    LaunchedEffect(Unit) { if (importantItems.isEmpty()) listTab = 2 }
    val sections = remember(visibleItems, groupOf) {
        val byGroup = visibleItems.groupBy { groupOf(it.name) }
        val out = ArrayList<Pair<String, List<Material>>>()
        byGroup.keys.filter { it.isNotEmpty() }.sortedArabicBy { it }.forEach { out.add(it to byGroup.getValue(it)) }
        byGroup[""]?.let { out.add("" to it) }
        out
    }
    val showSectionHeaders = sections.any { it.first.isNotEmpty() }

    // تُحسب مرة لكل تغيّر فعلي في القائمة المعروضة أو في الأسعار.
    val weightSummary = remember(visibleItems) { visibleItems.weightSummary() }
    val priceSummary = remember(visibleItems, prices) { visibleItems.priceSummary(prices) }
    val allWeights = remember(materials) { materials.weightSummary() }
    val allPrices = remember(materials, prices) { materials.priceSummary(prices) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace2, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(AppGroupGap)
    ) {
        // ملخّص القائمة (الأوزان + إجمالي السعر) وعنوانها: أول عنصرين في القائمة
        // نفسها فيتمرّران معها. يتبعان القائمة المعروضة (يتغيّران مع البحث).
        item(key = "summary") {
            MaterialsSummaryCard(
                weights = weightSummary,
                prices = priceSummary,
                overallWeights = if (listTab == 2) null else allWeights,
                overallPrices = if (listTab == 2) null else allPrices,
                animate = animateSummary,
                modifier = Modifier.padding(bottom = AppSpace6)
            )
        }
        item(key = "listTabs") {
            MaterialsTabBar(
                selected = listTab,
                importantCount = importantItems.size,
                regularCount = regularItems.size,
                allCount = materials.size,
                onSelect = { listTab = it },
                modifier = Modifier.padding(bottom = AppSpaceXs)
            )
        }
        item(key = "listTitle") {
            AppSectionTitle(
                text = when (listTab) {
                    0 -> "المواد الهامة (${visibleItems.size})"
                    1 -> "المواد الناقصة (${visibleItems.size})"
                    else -> "كل المواد (${visibleItems.size})"
                },
                modifier = Modifier.padding(horizontal = 0.dp),
                trailing = {
                    TextButton(onClick = onClearAll) {
                        Text(
                            "مسح الكل",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }
        if (visibleItems.isEmpty()) {
            item(key = "noImportant") {
                Text(
                    if (listTab == 0) "لا توجد مواد هامة\nاضغط على النجمة بجانب أي مادة لتظهر هنا" else "لا توجد مواد هنا",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        sections.forEach { (group, list) ->
            if (showSectionHeaders) {
                item(key = "section_$group", contentType = "sectionHeader") {
                    ShortageSectionHeader(
                        name = group,
                        count = list.size,
                        modifier = Modifier.padding(top = AppSpaceSm, bottom = 2.dp)
                    )
                }
            }
            itemsIndexed(
                list,
                key = { _, m -> m.id },
                contentType = { _, _ -> "materialRow" }
            ) { rowIndex, m ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = if (LocalPerformanceTier.current == PerformanceTier.LOW) null
                            else tween(durationMillis = MotionSpecs.fadeMillis(), easing = MotionSpecs.claudeEasing),
                            placementSpec = MotionSpecs.reorderSpring(),
                            fadeOutSpec = if (LocalPerformanceTier.current == PerformanceTier.LOW) null
                            else MotionSpecs.listItemFadeOut()
                        )
                        .listItemEntrance(rowIndex)
                ) {
                    MaterialRow(
                        material = m,
                        shape = groupedRowShape(rowIndex, list.lastIndex),
                        onEdit = { onEdit(m) },
                        onDelete = { onDelete(m) },
                        onToggleImportant = { onToggleImportant(m) },
                        linePrice = m.linePrice(prices)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortageSectionHeader(name: String, count: Int, modifier: Modifier = Modifier) {
    val ungrouped = name.isEmpty()
    val accent = remember(name) { if (ungrouped) Color(0xFF8A8A8A) else avatarColorFor(name) }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (ungrouped) Icons.Default.Inventory2 else Icons.Default.Folder,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (ungrouped) "بدون مجموعة" else name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 9.dp, vertical = 2.dp)
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MaterialRow(
    material: Material,
    shape: Shape,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleImportant: () -> Unit,
    isDragging: Boolean = false,
    linePrice: Double? = null,
    modifier: Modifier = Modifier
) {
    val avatarColor = remember(material.name) { avatarColorFor(material.name) }
    // LAG FIX: قراءة الـ scale في مرحلة الرسم بدل إعادة تركيب الصف كاملاً في كل إطار.
    val scale = animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = MotionSpecs.quickSpring(),
        label = "materialRowScale"
    )
    val rowFill = MaterialTheme.colorScheme.surfaceContainerHigh
    // الصف "المهم جداً" يأخذ لمسة ذهبية خفيفة بدل الإطار الملوّن القديم.
    val fill = if (material.important) {
        LocalSemanticColors.current.warning.copy(alpha = 0.14f).compositeOver(rowFill)
    } else rowFill

    Box(modifier.fillMaxWidth().graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        Surface(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = fill,
            shadowElevation = if (isDragging) 6.dp else 0.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(start = AppSpace2, end = AppSpace10, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val gold = LocalSemanticColors.current.warning
                val ringColor = if (material.important) gold else MaterialTheme.colorScheme.outlineVariant
                Box(Modifier.width(46.dp).height(56.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .size(44.dp)
                            .border(if (material.important) 2.dp else 1.5.dp, ringColor, CircleShape)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIconCircle(color = avatarColor, size = 36.dp) {
                            Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(1.5.dp, ringColor, CircleShape)
                            .clickable(onClick = onToggleImportant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (material.important) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (material.important) "إلغاء الأهمية" else "وضع كهامة جداً",
                            tint = if (material.important) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(Modifier.width(AppSpace10))
                Column(Modifier.weight(1f)) {
                    Text(
                        material.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    // سعر هذا الصف = سعر الكيلو × الوزن (يظهر فقط إذا كانت المادة مسعّرة).
                    if (linePrice != null) {
                        Text(
                            "${Formatters.number(linePrice)} ${AppSettingsState.currencySymbol}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (material.notes.isNotBlank()) {
                        Text(
                            material.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                com.shopmanager.app.ui.common.PillBadge(
                    text = material.quantityLabel(),
                    color = com.shopmanager.app.ui.common.pillColorForQuantity(material.quantity)
                )
                Spacer(Modifier.width(AppSpaceSm))
                AppActionPill(
                    height = 36.dp,
                    actions = listOf(
                        ActionSpec(Icons.Default.Edit, "تعديل", null, onEdit),
                        ActionSpec(Icons.Default.Close, "حذف المادة", LocalSemanticColors.current.danger, onDelete)
                    )
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    AppEmptyState(icon = icon, text = text)
}
