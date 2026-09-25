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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.DeleteIconButton
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.GradientIconButton
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
import com.shopmanager.app.ui.theme.LocalSemanticColors

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
    onSavePricesRequestHandled: () -> Unit = {},
    // BUG FIXED ("ترابط بين الديون والملاحظات" كان يغطي الأشخاص فقط): see
    // MainActivity's pendingMaterialHighlight and NotesScreen's onOpenLink —
    // a material-linked note now hands its linked material's name through
    // here instead of just opening this screen with an empty search, so
    // tapping the note lands directly on that one item instead of the full
    // list.
    initialSearchQuery: String? = null,
    onInitialSearchConsumed: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val message by viewModel.message.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    // REDESIGN ("شريط البحث لازم يكون زر في الشريط العلوي يتوسع بواجهة لحالة
    // اثناء البحث"): the search field used to sit permanently above the
    // list; it now lives as a small icon button in MaterialsHeader that
    // expands the header's own title row into a focused search field when
    // tapped, instead of taking up its own row on the page at all times.
    var isSearching by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var deleteTarget by remember { mutableStateOf<Material?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showShareChoice by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brandColor = LocalBrandGradientColors.current.first().toArgb()

    // Consumed once (onInitialSearchConsumed clears it back to null in
    // MainActivity) so navigating away and back to this tab later, or a
    // recomposition for an unrelated reason, doesn't keep re-forcing the
    // search box back to this value if the person has since cleared it
    // themselves. Also lands on the main list tab (0), not الأسعار — a
    // material-linked note is always about the shortage-list entry, never
    // its price.
    LaunchedEffect(initialSearchQuery) {
        if (!initialSearchQuery.isNullOrBlank()) {
            search = initialSearchQuery
            tab = 0
            onInitialSearchConsumed()
        }
    }

    // REDESIGN: the "مادة جديدة" quick-add action moved out to
    // FloatingBottomNav's shared `quickAction` slot beside the nav pill
    // (wired up in MainActivity), so this screen no longer needs its own
    // FAB or a clearance value just for it.

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) searchFocusRequester.requestFocus()
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
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            MaterialsHeader(
                tab = tab,
                onTabChange = { tab = it },
                showClearAll = tab == 0 && state.materials.isNotEmpty(),
                onClearAll = { showClearAllConfirm = true },
                onShare = { showShareChoice = true },
                onOpenDrawer = onOpenDrawer,
                isSearching = isSearching,
                onSearchToggle = { isSearching = it },
                searchQuery = search,
                onSearchQueryChange = { search = it },
                searchFocusRequester = searchFocusRequester
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
                // REDESIGN ("زر اضافة مادة جديدة يجب ان يكون زر عريض مكان
                // شريط البحث للي شلته"): the permanent search field that
                // used to sit here moved into MaterialsHeader's own search
                // icon (see topBar above); this space is now a wide,
                // prominent "مادة جديدة" button instead, using the
                // `onAddNew` callback MainActivity already wires to the
                // catalog-picker screen.
                Column(Modifier.fillMaxSize()) {
                    Surface(
                        onClick = onAddNew,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("مادة جديدة", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
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
                        onDelete = { deleteTarget = it },
                        onToggleImportant = { viewModel.setImportant(it, !it.important) },
                        onReordered = { viewModel.reorderMaterials(it.map(Material::id)) }
                    )
                }
            } else {
                PricesList(catalogItems = catalog, prices = state.prices, edited = editedPrices)
            }
        }
        }
    }

    editingMaterial?.let { m ->
        var isSavingMaterial by remember { mutableStateOf(false) }
        MaterialEditDialog(
            initial = m,
            isSaving = isSavingMaterial,
            onDismiss = { if (!isSavingMaterial) editingMaterial = null },
            onSave = { name, qty, unit, notes ->
                isSavingMaterial = true
                viewModel.updateMaterial(m.id, name, qty, unit, notes) { success ->
                    isSavingMaterial = false
                    if (success) editingMaterial = null
                }
            }
        )
    }

    deleteTarget?.let { m ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${m.name}\"؟") },
            confirmButton = {
                // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
                // PersonEditDialog.kt's doc comment.
                TextButton(shape = RectangleShape, onClick = { viewModel.deleteMaterial(m.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget = null }) { Text("إلغاء") } }
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
                TextButton(shape = RectangleShape, onClick = {
                    viewModel.deleteAllMaterials()
                    showClearAllConfirm = false
                }) { Text("حذف الكل") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { showClearAllConfirm = false }) { Text("إلغاء") } }
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
    onShare: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    isSearching: Boolean = false,
    onSearchToggle: (Boolean) -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchFocusRequester: FocusRequester? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            // UNIFIED ON CLAUDE'S DESIGN ("عدل التصميم بشكل جذري ليصبح متل
            // كلود"): this used to sit on its own boxed liquidGlassSurface
            // panel with rounded bottom corners — the same "boxed card"
            // header look DashboardHeader's own note already moved away
            // from for Home. Removed here too, so every tab now sits
            // directly on the app's plain background like Claude's own
            // screens (Home *and* Settings) do — no screen has a separate
            // toned panel behind its title any more.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // REDESIGN ("شريط البحث لازم يكون زر في الشريط العلوي يتوسع بواجهة
        // لحالة اثناء البحث"): tapping the search icon below swaps this
        // whole title row for a focused search field instead of opening a
        // second bar or keeping a permanently-visible field elsewhere on
        // the page — the header itself "becomes" the search UI while
        // active, then reverts to the normal title + tabs when closed.
        if (isSearching) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GradientIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "إغلاق البحث",
                    onClick = { onSearchToggle(false); onSearchQueryChange("") }
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .let { if (searchFocusRequester != null) it.focusRequester(searchFocusRequester) else it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = BrandOnGradient),
                    cursorBrush = SolidColor(BrandOnGradient),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "بحث عن مادة...",
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandOnGradient.copy(alpha = 0.5f)
                            )
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    GradientIconButton(icon = Icons.Default.Clear, contentDescription = "مسح", onClick = { onSearchQueryChange("") })
                }
            }
        } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // BUG FIXED ("الأيقونة فوق الكلمة"): the hamburger used to be a
            // floating overlay from MainActivity, pinned to this exact
            // top-right (RTL) corner — the same corner the title text
            // below already occupies — so the two visually collided. It's
            // now a real leading element in this header's own Row, same
            // fix as DashboardHeader/DebtsScreen.
            GradientIconButton(icon = Icons.Default.Menu, contentDescription = "القائمة", onClick = onOpenDrawer)
            Spacer(Modifier.width(10.dp))
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
            //
            // REDESIGN: a search icon now sits in this same row (المواد tab
            // only — الأسعار has its own always-editable list where a
            // header search field would compete with per-row price inputs).
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (tab == 0) {
                    GradientIconButton(icon = Icons.Default.Search, contentDescription = "بحث", onClick = { onSearchToggle(true) })
                }
                if (showClearAll) {
                    GradientIconButton(icon = Icons.Default.DeleteSweep, contentDescription = "مسح كل المواد", onClick = onClearAll)
                }
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
    // UNIFIED ON CLAUDE'S DESIGN: this control used to float on top of its
    // own boxed brand-gradient header panel, which is why its track/thumb
    // were hardcoded translucent-white overlays (readable against any
    // colored backdrop) and the selected label read off the brand
    // gradient's own start color instead of colorScheme.primary. Now that
    // MaterialsHeader sits directly on the app's plain background like
    // every other screen, this follows the same theme-aware tokens the
    // rest of the app already uses for a segmented control (see the
    // now-removed color-mode picker in SettingsScreen.kt for the same
    // pattern): a tonal surfaceContainerHigh track, a flat `surface` thumb,
    // and colorScheme.primary (Claude's own terracotta accent — vivid in
    // both themes, not the pale primary80-style tone the old comment here
    // was guarding against) for the selected label.
    val selectedLabelColor = MaterialTheme.colorScheme.primary
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
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
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(11.dp), clip = false)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(11.dp))
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
    onDelete: (Material) -> Unit,
    onToggleImportant: (Material) -> Unit,
    onReordered: (List<Material>) -> Unit
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

    // FEATURE ADDED ("ترتيب المواد بالضغط المطول وتحريكها"): a local copy
    // that the drag gesture below reorders live, in real time, as the
    // finger moves — synced back to the real `materials` list on every
    // change from Firestore *except* while a drag is actually in progress,
    // so an incoming snapshot (e.g. from another device) never yanks the
    // row out from under the finger mid-drag. Reordering only makes sense
    // against the full, unfiltered list, so it's disabled entirely while
    // searching (see `canReorder` below) — dragging a filtered subset
    // would silently scramble the position of every hidden item too.
    var orderedItems by remember { mutableStateOf(materials) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(materials) {
        if (draggingId == null) orderedItems = materials
    }
    val canReorder = !searching
    // Real per-row height in px (row height + the 8.dp spacedBy gap),
    // measured live via onSizeChanged below — used to decide, as the
    // finger moves, when it's dragged far enough past a neighbor to swap
    // places with it.
    val itemHeightsPx = remember { mutableStateMapOf<String, Int>() }
    var dragOffset by remember { mutableStateOf(0f) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(orderedItems, key = { _, m -> m.id }) { _, m ->
            val isDragging = m.id == draggingId
            Box(
                Modifier
                    .fillMaxWidth()
                    // Items not currently being dragged still animate into
                    // their new slot when a drag (or a delete/search
                    // change) shifts them — skipped for the dragged item
                    // itself so its own manual `dragOffset` below (which
                    // already tracks the finger exactly) isn't fought by a
                    // second, competing placement animation.
                    .then(if (!isDragging) Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = MotionSpecs.reorderSpring(),
                        fadeOutSpec = MotionSpecs.listItemFadeOut()
                    ) else Modifier)
                    .onSizeChanged { itemHeightsPx[m.id] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .then(
                        if (canReorder) {
                            Modifier.pointerInput(m.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = m.id
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        if (draggingId != null) onReordered(orderedItems)
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragOffset += delta.y
                                        val step = (itemHeightsPx[m.id] ?: 0) + 8.dp.toPx()
                                        if (step > 0f) {
                                            val currentIndex = orderedItems.indexOfFirst { it.id == m.id }
                                            if (dragOffset > step / 2 && currentIndex < orderedItems.lastIndex) {
                                                orderedItems = orderedItems.toMutableList().apply {
                                                    add(currentIndex + 1, removeAt(currentIndex))
                                                }
                                                dragOffset -= step
                                            } else if (dragOffset < -step / 2 && currentIndex > 0) {
                                                orderedItems = orderedItems.toMutableList().apply {
                                                    add(currentIndex - 1, removeAt(currentIndex))
                                                }
                                                dragOffset += step
                                            }
                                        }
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                MaterialRow(
                    material = m,
                    onEdit = { onEdit(m) },
                    onDelete = { onDelete(m) },
                    onToggleImportant = { onToggleImportant(m) },
                    isDragging = isDragging
                )
            }
        }
    }
}

@Composable
private fun MaterialRow(
    material: Material,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleImportant: () -> Unit,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatarColor = remember(material.name) { avatarColorFor(material.name) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else if (isDragging) 1.03f else 1f,
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
        // FEATURE ADDED ("نجمة الأهمية"): a starred material gets a subtle
        // amber border so it also reads as important at a glance, not only
        // through the filled star icon.
        // BUG FIXED ("كل الكروت شكلها متل بعض"): this used to be a raw
        // hardcoded hex (0xFFFFA000) instead of this app's own WarningAmber
        // (see Color.kt) — cosmetically almost the same shade, but now that
        // the CLAUDE palette's default warm terracotta theme is actually
        // rendering (see Theme.kt), that palette's own outlineVariant tone
        // is itself warm/tan, not neutral grey the way it was under the old
        // Material default scheme. Against a warm border, the *un*starred
        // card's border and the starred card's amber border read as nearly
        // the same color — exactly the "every card looks the same" effect.
        // Dropping the ordinary border's alpha further keeps it visibly
        // receding into the card regardless of theme, so the starred
        // amber border actually stands out again as the one visual signal
        // it's meant to be.
        border = BorderStroke(
            if (material.important) 1.5.dp else 1.dp,
            if (material.important) LocalSemanticColors.current.warning.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (isDragging) 6.dp else 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FEATURE ADDED ("نجمة بجانب كل مادة جديدة... تنقل لأول اشي"):
            // tapping the star marks/unmarks the material as very
            // important; marking it also pins it to the top of the list
            // (see MaterialsViewModel.setImportant).
            IconButton(onClick = onToggleImportant) {
                Icon(
                    if (material.important) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (material.important) "إلغاء الأهمية" else "وضع كهامة جداً",
                    tint = if (material.important) LocalSemanticColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(material.name, fontWeight = FontWeight.Medium)
                if (material.notes.isNotBlank()) {
                    Text(
                        material.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            // REDESIGN (reference screenshot: the shortage list's quantity
            // shows as a small rounded, color-coded badge instead of a
            // plain "الكمية المطلوبة: X" text line) — same PillBadge the
            // dashboard's own shortage-list rows now use, so a material's
            // quantity reads identically whether it's seen from Home or
            // from this full list.
            com.shopmanager.app.ui.common.PillBadge(
                text = material.quantityLabel(),
                color = com.shopmanager.app.ui.common.pillColorForQuantity(material.quantity)
            )
            Spacer(Modifier.width(4.dp))
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
    // NOTE: this closure is still used per-row below (`hasPrice`), where
    // the `edited[item.name]` read is already scoped to that one row's own
    // slot in the LazyColumn — cheap, and unrelated to the perf fix below.
    val effectiveOf: (MaterialCatalogItem) -> Double? = { item ->
        edited[item.name]?.toDoubleOrNull() ?: prices[item.name]
    }

    // "بدل هذا الارتفاع": used to also add the full-width save button's own
    // height on top of the pill clearance here, which is what left that
    // tall dead strip below the button in the old layout. The save action
    // no longer lives in this list at all (see the class doc comment
    // above), so the list only needs to clear the pill itself now.
    // BUG FIXED ("آخر صف بيلزق بالشريط"): the flat 16.dp part of that was
    // noticeably thinner than المواد tab's own 32.dp+ clearance right next
    // to it (same pill, same screen) — thin enough for the last price row
    // to sit close enough to the pill's transparent side margins to peek
    // through instead of clearing it. Matched to the same 32.dp.
    val bottomClearance = LocalFloatingBottomNavHeight.current + 32.dp
    Column(Modifier.fillMaxSize()) {
        // PERF FIX ("تقطيع" while typing a price): pricedCount/totalValue
        // used to be computed directly in *this* function with
        // `remember(catalogItems, prices, edited.toMap())` — `edited` is a
        // SnapshotStateMap, so `.toMap()` (a) allocates a brand-new full
        // copy of the whole map on every single recomposition just to use
        // as a remember key, and worse, (b) makes reading it right here,
        // inside PricesList's own body, the thing that subscribes
        // PricesList itself to every change in that map. Since every
        // keystroke in *any* row's price field is a write into `edited`,
        // every keystroke was recomposing the whole PricesList function —
        // including re-running the entire `items(filtered) { ... }` block
        // below and re-walking every visible row — just to update two
        // numbers in the summary card. That's the actual cause of the
        // stutter while entering prices: it scales with catalog size and
        // showed up on every device, weak or strong, since it's a
        // per-keystroke cost, not a one-time layout cost.
        // PricesSummarySection (below) reads `edited` itself, through a
        // `derivedStateOf`, so the recomposition that a price edit
        // triggers is scoped to that small summary composable alone —
        // the LazyColumn and its rows are never touched by it.
        PricesSummarySection(
            catalogItems = catalogItems,
            prices = prices,
            edited = edited,
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
 * PERF FIX wrapper (see the long comment at its call site in [PricesList]):
 * isolates the `edited` SnapshotStateMap read behind a `derivedStateOf` so
 * that typing into a price field only ever recomposes this small
 * composable, never [PricesList] itself (and therefore never its
 * LazyColumn). `derivedStateOf` re-runs its block on every `edited` write,
 * but only actually invalidates *readers* of [pricedCount]/[totalValue]
 * (i.e. just this function) when the computed count/sum genuinely changes
 * — cheap either way, and never touches the row list.
 */
@Composable
private fun PricesSummarySection(
    catalogItems: List<MaterialCatalogItem>,
    prices: Map<String, Double>,
    edited: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val pricedCount by remember(catalogItems, prices) {
        derivedStateOf {
            catalogItems.count { (edited[it.name]?.toDoubleOrNull() ?: prices[it.name]) != null }
        }
    }
    val totalValue by remember(catalogItems, prices) {
        derivedStateOf {
            catalogItems.sumOf { (edited[it.name]?.toDoubleOrNull() ?: prices[it.name]) ?: 0.0 }
        }
    }
    PricesSummaryCard(
        pricedCount = pricedCount,
        totalCount = catalogItems.size,
        totalValue = totalValue,
        currency = currency,
        modifier = modifier
    )
}

/**
 * Compact totals strip for the الأسعار tab: how many catalog items already
 * have a price set (edits in progress count too — see [PricesSummarySection])
 * and the running total value of the whole catalog at current prices. Uses
 * the same solid card language as the rest of the app (no glass dependency)
 * so it reads correctly in every color mode.
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
