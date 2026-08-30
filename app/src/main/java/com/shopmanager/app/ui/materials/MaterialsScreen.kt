package com.shopmanager.app.ui.materials

import android.content.Intent
import androidx.compose.animation.Crossfade
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
fun MaterialsScreen(viewModel: MaterialsViewModel = viewModel(), onAddNew: () -> Unit = {}) {
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

    val filtered = if (search.isBlank()) state.materials
    else state.materials.filter { it.name.contains(search, ignoreCase = true) }

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
                PricesList(catalogItems = catalog, prices = state.prices, onSave = viewModel::setPrice)
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
            .liquidGlassSurface(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
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
            options = listOf("المواد", "الأسعار"),
            onSelect = onTabChange
        )
    }
}

/**
 * iOS-style pill segmented control, redone as an actual "liquid glass"
 * thumb instead of a flat opaque-white rectangle.
 *
 * FIX: the previous selected-segment background was plain solid white
 * (`Color.White.copy(alpha = 0.95f)`) — on top of an already-bright brand
 * gradient it read as a dull, flat cutout rather than part of the glass
 * design used everywhere else (header, buttons), and it had no edge of its
 * own so it didn't look "lifted" off the panel behind it. The thumb now
 * gets the same treatment as every other glass surface in this app: a
 * soft vertical sheen (bright at the top, settling lower), a bright hairline
 * rim, and a subtle shadow so it visibly sits above the track instead of
 * blending flat into it — while staying opaque enough at the core for the
 * primary-colored label to stay fully legible.
 */
@Composable
private fun SegmentedTabs(selectedIndex: Int, options: List<String>, onSelect: (Int) -> Unit) {
    // BUG FIXED (unreadable selected label): this used to color the selected
    // segment's text with MaterialTheme.colorScheme.primary. That's correct
    // for surfaces that actually flip with the theme, but the selected
    // segment's pill background here is a fixed near-white "glass" surface
    // in BOTH light and dark mode (see the .background() below) — while
    // colorScheme.primary itself flips to a light/pale tone in dark mode
    // (meant to read against a dark background, not a white pill). Pale
    // text on a near-white pill is exactly the low-contrast, hard-to-read
    // label reported. The brand gradient's start color is deliberately the
    // same in both themes (see ShopManagerTheme/LocalBrandGradientColors),
    // so using it here keeps the selected label a consistent, legible dark
    // accent color against the white pill no matter which theme is active.
    val selectedLabelColor = LocalBrandGradientColors.current.first()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bgColor by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = MotionSpecs.quickSpring(),
                label = "segmentSelection"
            )
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (bgColor > 0.01f) {
                            Modifier.shadow(
                                elevation = (3f * bgColor).dp,
                                shape = RoundedCornerShape(11.dp),
                                clip = false
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.98f * bgColor),
                                Color.White.copy(alpha = 0.86f * bgColor)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.55f * bgColor), RoundedCornerShape(11.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) }
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) selectedLabelColor else BrandOnGradient.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                )
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

@Composable
private fun PricesList(catalogItems: List<MaterialCatalogItem>, prices: Map<String, Double>, onSave: (String, Double) -> Unit) {
    // FIX: this tab used to price whatever happened to be on the shortage
    // list (state.materials) — which meant a material's price disappeared
    // the moment it was bought and removed from the shortage list, and had
    // to be re-typed from scratch the next time it ran out. Prices now
    // belong to the shop's fixed catalog (see المواد الثابتة /
    // MaterialCatalogScreen) instead: a standing list that doesn't change
    // just because something is or isn't currently a shortage, so a price
    // set once stays set. The shortage tab (tab 0 above) still shows and
    // sums these same prices by looking them up by name from `prices`.
    val edited = remember(catalogItems) { mutableStateMapOf<String, String>() }

    if (catalogItems.isEmpty()) {
        EmptyState(icon = Icons.Default.Inventory2, text = "أضف مواد للقائمة الثابتة أولاً لتسعيرها")
        return
    }

    // BUG FIXED: this tab has no FAB, but the floating nav pill still
    // floats over it (it's shared across all 3 pager tabs) — without this,
    // the last price row stopped exactly at the screen edge, right behind
    // the pill.
    val bottomClearance = LocalFloatingBottomNavHeight.current + 8.dp
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(catalogItems, key = { it.id }) { item ->
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
                        // BUG FIXED: a long material name had no line/overflow
                        // limit, so it could wrap to 2+ lines while the price
                        // field next to it stayed a single line — the row's
                        // vertical centering then looked broken/misaligned for
                        // exactly the longer names. Ellipsis keeps every row
                        // the same height regardless of name length.
                        Text(
                            item.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        // BUG FIXED: swapped the outlined field (border +
                        // floating label breaking the border line — a
                        // distinctly Material/Android pattern) for the same
                        // borderless filled treatment as AppTextField, kept
                        // inline/unlabeled here since the row is already
                        // compact and single-line; a placeholder carries the
                        // "السعر" context instead of a caption that would add
                        // height and break the row's vertical centering.
                        TextField(
                            value = edited[item.name] ?: prices[item.name]?.toString() ?: "",
                            onValueChange = { edited[item.name] = it },
                            modifier = Modifier.width(120.dp),
                            placeholder = { Text("السعر", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
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
        }
        Button(
            onClick = {
                edited.forEach { (name, value) -> value.toDoubleOrNull()?.let { onSave(name, it) } }
                edited.clear()
            },
            // BUG FIXED ("زر الحفظ تحت الشريط السفلي"): this Column fills
            // the whole screen, but the floating bottom nav pill is drawn
            // as a separate overlay on top of it (see MainActivity/
            // LocalFloatingBottomNavHeight's own doc comment) rather than
            // reserving real layout space — so a plain 16.dp bottom padding
            // here only cleared the true screen edge, not the pill sitting
            // in front of it, and the button ended up hidden behind it.
            // Adding the pill's own measured height (bottomClearance, same
            // value already used for the list above) lifts the button
            // above it, matching the FAB's own clearance elsewhere.
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomClearance + 16.dp),
            shape = MaterialTheme.shapes.medium
        ) { Text("حفظ كل الأسعار") }
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
