package com.shopmanager.app.ui.materials

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.DeleteIconButton
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.GradientIconButton
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.ShareFormatDialog
import com.shopmanager.app.ui.common.GlassAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shopmanager.app.ui.theme.LocalBrandGradientColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
    viewModel: MaterialsViewModel = viewModel(),
    onAddNew: () -> Unit = {},
    // "دمج زر حفظ الأسعار مع الشريط السفلي": these four let MainActivity
    // show/drive a "حفظ الأسعار" action beside the floating nav pill
    // (FloatingBottomNav's `secondaryAction`, opposite side from the
    // existing "+" — see MainActivity) instead of this screen drawing its
    // own full-width save button. Same request/handled pattern already
    // used for "عميل جديد" (see DebtsScreen's addPersonRequested): tapping
    // the pill's button just raises `savePricesRequested`, this screen
    // watches it and performs the actual save, then reports it handled.
    onPricesTabActiveChanged: (Boolean) -> Unit = {},
    onPricesChangedCountChanged: (Int) -> Unit = {},
    savePricesRequested: Boolean = false,
    onSavePricesRequestHandled: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val message by viewModel.message.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var deleteTarget by remember { mutableStateOf<Material?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showShareChoice by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brandColor = LocalBrandGradientColors.current.first().toArgb()

    // REDESIGN: the "مادة جديدة" quick-add action moved out to
    // FloatingBottomNav's shared `quickAction` slot beside the nav pill
    // (wired up in MainActivity), so this screen no longer needs its own
    // FAB or a clearance value just for it.

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    // PERF: this used to re-run the .filter{} scan over the whole
    // materials list on *every* recomposition of this screen — including
    // ones triggered by completely unrelated state (a dialog opening, the
    // snackbar message clearing, etc.), not just an actual `search` or
    // `state.materials` change. `remember` keyed on the two inputs this
    // computation actually depends on makes it skip that rescan unless
    // one of them genuinely changed.
    val filtered = remember(search, state.materials) {
        if (search.isBlank()) state.materials
        else state.materials.filter { it.name.contains(search, ignoreCase = true) }
    }

    // MOVED UP from PricesList: this in-progress price-edits buffer used to
    // live entirely inside PricesList, private to that tab, since only its
    // own full-width save button ever read it. Now that saving is
    // triggered externally (from the floating pill's merged button — see
    // `savePricesRequested` above), this screen needs to reach the same
    // buffer to actually perform the save, so it's hoisted here and passed
    // down to PricesList instead of PricesList creating its own.
    val editedPrices = remember(catalog) { mutableStateMapOf<String, String>() }
    val pricesChangedCount = editedPrices.count { (name, value) ->
        val parsed = value.toDoubleOrNull()
        parsed != null && parsed != state.prices[name]
    }
    LaunchedEffect(tab) { onPricesTabActiveChanged(tab == 1) }
    LaunchedEffect(pricesChangedCount) { onPricesChangedCountChanged(pricesChangedCount) }
    LaunchedEffect(savePricesRequested) {
        if (savePricesRequested) {
            editedPrices.forEach { (name, value) -> value.toDoubleOrNull()?.let { viewModel.setPrice(name, it) } }
            editedPrices.clear()
            onSavePricesRequestHandled()
        }
    }

    Scaffold(
        // Edge-to-edge: the status bar is transparent (see
        // SetSystemBarsColor/MainActivity) and this screen's own
        // MaterialsHeader below draws its liquid-glass panel all the way
        // up to the true top of the window and pads its *content* down
        // manually — so this Scaffold must not also reserve top space
        // itself, or the header would get pushed down a second time
        // leaving a plain gap above it. Bottom/horizontal safe-area insets
        // (gesture nav bar, cutouts) are kept as-is.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            MaterialsHeader(
                tab = tab,
                onTabChange = { tab = it },
                showClearAll = tab == 0 && state.materials.isNotEmpty(),
                onClearAll = { showClearAllConfirm = true },
                onShare = { showShareChoice = true }
            )
        }
        // REDESIGN: no `floatingActionButton` slot here anymore — "مادة
        // جديدة" now lives beside the floating nav pill (see
        // FloatingBottomNav's quickAction, wired up in MainActivity) so it
        // reads as one attached unit with the pill instead of a separate
        // button floating off on its own over the list.
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
        Crossfade(targetState = tab, label = "materialsTab") { selectedTab ->
            if (selectedTab == 0) {
                // Kept deliberately minimal: just the search field above the
                // list, and materials below it - no extra banners competing
                // for attention. The "مادة جديدة" action lives only in the
                // floating button at the bottom of the screen.
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        placeholder = { Text("بحث عن مادة...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    )
                    // NEW: sums the fixed catalog price of every shortage
                    // material currently shown (looked up from state.prices,
                    // the same map the "الأسعار" tab now edits against the
                    // catalog — see PricesList below), so the person can see
                    // the total cost of restocking without leaving this tab.
                    // Hidden entirely until at least one shown material
                    // actually has a price set, so an all-unpriced list
                    // doesn't show a misleading "0".
                    val shortageTotal = remember(filtered, state.prices) {
                        filtered.sumOf { state.prices[it.name] ?: 0.0 }
                    }
                    if (shortageTotal > 0.0) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "إجمالي أسعار النواقص",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${Formatters.number(shortageTotal)} ${AppSettingsState.currencySymbol}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    MaterialsList(
                        materials = filtered,
                        searching = search.isNotBlank(),
                        onEdit = { editingMaterial = it },
                        onDelete = { deleteTarget = it }
                    )
                }
            } else {
                PricesList(catalogItems = catalog, prices = state.prices, edited = editedPrices)
            }
        }
        }
    }

    editingMaterial?.let { m ->
        MaterialEditDialog(
            initial = m,
            onDismiss = { editingMaterial = null },
            onSave = { name, qty, unit ->
                viewModel.updateMaterial(m.id, name, qty, unit)
                editingMaterial = null
            }
        )
    }

    deleteTarget?.let { m ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${m.name}\"؟") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMaterial(m.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }

    if (showShareChoice) {
        ShareFormatDialog(
            onDismiss = { showShareChoice = false },
            onPickImage = {
                // PERF: bitmap/Canvas drawing moved off the main thread —
                // with a long materials list this Canvas work is real,
                // measurable CPU time, and running it straight in the
                // onClick previously blocked the UI thread and dropped
                // frames right as the share sheet was trying to animate
                // in. Generation runs on Dispatchers.Default; only the
                // final startActivity hop is back on Main.
                scope.launch {
                    val uri = withContext(Dispatchers.Default) {
                        MaterialsReportImage.generate(context, state.materials, state.prices, brandColor)
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "مشاركة قائمة المواد"))
                }
            },
            onPickText = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, buildMaterialsShareText(state.materials, state.prices))
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة قائمة المواد"))
            }
        )
    }

    if (showClearAllConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("مسح كل المواد") },
            text = { Text("هل أنت متأكد من حذف كل المواد المضافة (${state.materials.size})؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllMaterials()
                    showClearAllConfirm = false
                }) { Text("حذف الكل") }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("إلغاء") } }
        )
    }
}

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
private fun MaterialsHeader(
    tab: Int,
    onTabChange: (Int) -> Unit,
    showClearAll: Boolean,
    onClearAll: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            // طلب "تعميم ستايل الزجاج": highlight = false + baseAlpha = 0.72f
            // — راجع الشرح بـ DashboardScreen.kt.
            .liquidGlassSurface(
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                highlight = false,
                baseAlpha = 0.72f
            )
            // The glass panel itself (background/border above) already
            // fills this Column's full bounds, which now extend up behind
            // the transparent status bar; this only pushes the *content*
            // (title/tabs) down far enough to clear the status bar icons,
            // so there's no seam between the bar and the panel — it's one
            // continuous glass surface from the true top of the screen.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "المواد والأسعار",
                modifier = Modifier.weight(1f),
                color = BrandOnGradient,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                fontWeight = FontWeight.Bold
            )
            // "مسح الكل": only shown on the المواد tab, and only once there's
            // actually something to clear - deletes every material on the
            // list in one confirmed action (see MaterialsScreen's
            // showClearAllConfirm dialog / MaterialsViewModel.deleteAllMaterials).
            //
            // FIX (icons touching): this used to sit right next to the
            // share button with only a small fixed Spacer between them,
            // which read as the two circular buttons glued together. Both
            // now sit in their own Row with real breathing room
            // (spacedBy) between them instead of a single thin gap.
            if (showClearAll) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GradientIconButton(icon = Icons.Default.DeleteSweep, contentDescription = "مسح كل المواد", onClick = onClearAll)
                    GradientIconButton(icon = Icons.Rounded.Share, contentDescription = "مشاركة", onClick = onShare)
                }
            } else {
                GradientIconButton(icon = Icons.Rounded.Share, contentDescription = "مشاركة", onClick = onShare)
            }
        }
        Spacer(Modifier.height(16.dp))
        SegmentedTabs(
            selectedIndex = tab,
            options = listOf(
                SegmentOption("المواد", Icons.Default.Inventory2),
                SegmentOption("الأسعار", Icons.Default.Sell)
            ),
            onSelect = onTabChange
        )
    }
}

private data class SegmentOption(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * REDESIGN ("الشريط... أعد تصميمه"): rebuilt on a tighter, more deliberate
 * grid instead of the previous plain label-only pill. Three precise
 * changes from before:
 * 1. Each segment now carries a small leading glyph (📦 for المواد, 🏷 for
 *    الأسعار) so the two tabs are told apart at a glance, not just by
 *    reading the Arabic label — the same icon/label pairing pattern used
 *    for every row lower on this screen (MaterialRow's avatar, the price
 *    row's tag icon).
 * 2. The selected thumb's own size is now driven by real measurement
 *    (`Modifier.onSizeChanged` + `animateDpAsState` for its offset), so it
 *    slides between segments as one continuous pill instead of each
 *    segment independently cross-fading its own background — a small but
 *    real "precision" difference: there is exactly one thumb, always
 *    exactly the width of its segment, always exactly aligned under it.
 * 3. Track/thumb metrics tightened (6.dp track padding, 4.dp icon-label
 *    gap, fixed 46.dp row height) so the control reads as one crisp,
 *    consistently-measured control rather than padding that happened to
 *    look right on one label length.
 */
@Composable
private fun SegmentedTabs(selectedIndex: Int, options: List<SegmentOption>, onSelect: (Int) -> Unit) {
    // BUG FIXED (unreadable selected label): see original note — the
    // selected pill is a fixed near-white glass surface in both themes,
    // so its label needs the brand gradient's (theme-stable) start color
    // rather than colorScheme.primary, which pales out in dark mode.
    val selectedLabelColor = LocalBrandGradientColors.current.first()
    var trackWidthPx by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val segmentWidth = with(density) {
        if (trackWidthPx == 0) 0.dp else (trackWidthPx / options.size).toDp()
    }
    val thumbOffset by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = MotionSpecs.quickSpring(),
        label = "segmentThumbOffset"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(4.dp)
            .onSizeChanged { trackWidthPx = it.width }
    ) {
        // The single sliding thumb: one continuous pill that moves under
        // whichever segment is selected, instead of each Box tinting its
        // own background independently.
        if (segmentWidth > 0.dp) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(11.dp), clip = false)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.86f))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
            )
        }

        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val labelColor by animateColorAsState(
                    targetValue = if (selected) selectedLabelColor else BrandOnGradient.copy(alpha = 0.9f),
                    animationSpec = MotionSpecs.quickSpring(),
                    label = "segmentLabelColor"
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(index) }
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        option.label,
                        color = labelColor,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaterialsList(
    materials: List<Material>,
    searching: Boolean,
    onEdit: (Material) -> Unit,
    onDelete: (Material) -> Unit
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
    // the FAB's own real height + a small gap — computed once below,
    // rather than a hardcoded row.
    val fabHeight = 56.dp // MaterialDesign's fixed ExtendedFloatingActionButton height
    val bottomClearance = LocalFloatingBottomNavHeight.current + fabHeight + 24.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(materials, key = { it.id }) { m ->
            MaterialRow(
                material = m,
                onEdit = { onEdit(m) },
                onDelete = { onDelete(m) },
                modifier = Modifier.animateItemPlacement(MotionSpecs.reorderSpring())
            )
        }
    }
}

@Composable
private fun MaterialRow(material: Material, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val avatarColor = remember(material.name) { avatarColorFor(material.name) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "materialRowScale"
    )

    // BUG FIXED: same black-shadow-bar issue as PersonRow in
    // DebtsScreen.kt — see the comment there. Scale moved off the
    // ElevatedCard's own modifier chain onto a plain wrapping Box so it
    // never shares a graphics layer with the card's shadow.
    Box(modifier.fillMaxWidth().scale(scale)) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onEdit
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(material.name, fontWeight = FontWeight.Medium)
                Text(
                    "الكمية المطلوبة: ${material.quantityLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
            Spacer(Modifier.width(2.dp))
            DeleteIconButton(onClick = onDelete, contentDescription = "حذف المادة")
        }
    }
    }
}

/**
 * REDESIGN ("لوحة الأسعار... تصميم كامل ومحسّن"): rebuilt from a bare
 * name+field list into an actual priced overview:
 * - A summary card up top totals how many catalog items already have a
 *   price set out of the total, plus the running sum of every priced item
 *   (edited, unsaved values included) — the same "total at a glance" idea
 *   the shortage tab already has, brought over to this tab too.
 * - A search field to filter the catalog by name, matching the pattern
 *   already used on the المواد tab, since a real catalog can run long.
 * - Each row now carries a small colored tag-icon avatar (the same avatar
 *   pattern MaterialRow uses) instead of bare text, a currency suffix
 *   directly in the price field, and a "priced"/"unpriced" visual state so
 *   it's obvious at a glance which items still need a price.
 * - The "حفظ الأسعار" action itself now lives beside the floating nav pill
 *   at the bottom of the screen (see MaterialsScreen's `editedPrices`/
 *   `savePricesRequested` and FloatingBottomNav's `secondaryAction`,
 *   wired up in MainActivity) instead of a full-width button drawn here —
 *   it reads as one attached unit with the pill, the same way "مادة
 *   جديدة" already does, rather than a separate button with its own
 *   reserved strip of empty space below the list.
 */
@Composable
private fun PricesList(
    catalogItems: List<MaterialCatalogItem>,
    prices: Map<String, Double>,
    edited: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>
) {
    // FIX: this tab used to price whatever happened to be on the shortage
    // list (state.materials) — which meant a material's price disappeared
    // the moment it was bought and removed from the shortage list, and had
    // to be re-typed from scratch the next time it ran out. Prices now
    // belong to the shop's fixed catalog (see المواد الثابتة /
    // MaterialCatalogScreen) instead: a standing list that doesn't change
    // just because something is or isn't currently a shortage, so a price
    // set once stays set. The shortage tab (tab 0 above) still shows and
    // sums these same prices by looking them up by name from `prices`.
    // NOTE: `edited` is now passed in from MaterialsScreen (see its own
    // comment) rather than created here, so the externally-triggered save
    // action can reach the same in-progress buffer this list is writing
    // into.
    var search by remember { mutableStateOf("") }

    if (catalogItems.isEmpty()) {
        EmptyState(icon = Icons.Default.Inventory2, text = "أضف مواد للقائمة الثابتة أولاً لتسعيرها")
        return
    }

    val currency = AppSettingsState.currencySymbol
    // PERF: same fix as the shortage tab above — skip the rescan unless
    // `search` or `catalogItems` actually changed, instead of re-filtering
    // on every keystroke edit to an unrelated price field in the list
    // below (every `edited[...]` write recomposes this whole composable).
    val filtered = remember(search, catalogItems) {
        if (search.isBlank()) catalogItems
        else catalogItems.filter { it.name.contains(search, ignoreCase = true) }
    }

    // Effective value per item: the in-progress edit if there is one,
    // otherwise the already-saved price — this is what both the summary
    // card and the save button below count against, so "احفظ" always
    // reflects exactly what's on screen right now, unsaved edits included.
    val effectiveOf: (MaterialCatalogItem) -> Double? = { item ->
        edited[item.name]?.toDoubleOrNull() ?: prices[item.name]
    }
    val pricedCount = remember(catalogItems, prices, edited.toMap()) {
        catalogItems.count { effectiveOf(it) != null }
    }
    val totalValue = remember(catalogItems, prices, edited.toMap()) {
        catalogItems.sumOf { effectiveOf(it) ?: 0.0 }
    }

    // "بدل هذا الارتفاع": used to also add the full-width save button's own
    // height on top of the pill clearance here, which is what left that
    // tall dead strip below the button in the old layout. The save action
    // no longer lives in this list at all (see the class doc comment
    // above), so the list only needs to clear the pill itself now.
    val bottomClearance = LocalFloatingBottomNavHeight.current + 16.dp
    Column(Modifier.fillMaxSize()) {
        PricesSummaryCard(
            pricedCount = pricedCount,
            totalCount = catalogItems.size,
            totalValue = totalValue,
            currency = currency,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("بحث عن مادة...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        )
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Default.SearchOff, text = "لا توجد نتائج")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomClearance),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    PriceRow(
                        item = item,
                        currency = currency,
                        // Kept as a raw `toString()` (not the Arabic-locale
                        // Formatters.number used for display elsewhere) —
                        // this is the actual editable field value, and it
                        // has to stay something `toDoubleOrNull()` can
                        // parse back on save if the person edits it further
                        // without clearing it first.
                        value = edited[item.name] ?: prices[item.name]?.toString() ?: "",
                        hasPrice = effectiveOf(item) != null,
                        onValueChange = { edited[item.name] = it }
                    )
                }
            }
        }
    }
}

/**
 * Compact totals strip for the الأسعار tab: how many catalog items already
 * have a price set (edits in progress count too — see `pricedCount` in
 * [PricesList]) and the running total value of the whole catalog at
 * current prices. Uses the same solid card language as the rest of the
 * app (no glass dependency) so it reads correctly in every color mode.
 */
@Composable
private fun PricesSummaryCard(
    pricedCount: Int,
    totalCount: Int,
    totalValue: Double,
    currency: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "المسعّرة: $pricedCount من $totalCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "إجمالي قيمة القائمة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                "${Formatters.number(totalValue)} $currency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** One catalog row in the priced-list — a colored tag-avatar (dimmed when
 * the item still has no price, at full strength once it does), the item
 * name, and an inline price field with a currency suffix. */
@Composable
private fun PriceRow(
    item: MaterialCatalogItem,
    currency: String,
    value: String,
    hasPrice: Boolean,
    onValueChange: (String) -> Unit
) {
    val avatarColor = remember(item.name) { avatarColorFor(item.name) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (hasPrice) avatarColor else avatarColor.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Sell, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            // BUG FIXED: a long material name had no line/overflow limit, so
            // it could wrap to 2+ lines while the price field next to it
            // stayed a single line — the row's vertical centering then
            // looked broken/misaligned for exactly the longer names.
            // Ellipsis keeps every row the same height regardless of name
            // length.
            Text(
                item.name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            // BUG FIXED: swapped the outlined field (border + floating
            // label breaking the border line — a distinctly Material/
            // Android pattern) for the same borderless filled treatment as
            // AppTextField; a trailing currency suffix now sits inside the
            // field itself so "السعر بالليرة/بالدولار" never needs a
            // separate caption that would add height and break the row's
            // vertical centering.
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(128.dp),
                placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                suffix = { Text(currency, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun buildMaterialsShareText(materials: List<Material>, prices: Map<String, Double>): String {
    val currency = AppSettingsState.currencySymbol
    val sb = StringBuilder("📦 قائمة المواد والأسعار\n\n")
    materials.sortedBy { it.name }.forEach { m ->
        sb.append("• ${m.name}: ${m.quantityLabel()}")
        prices[m.name]?.let { sb.append(" — ${Formatters.number(it)} $currency") }
        sb.append("\n")
    }
    sb.append("\nالإجمالي: ${materials.size} مادة")
    return sb.toString()
}
