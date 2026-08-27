package com.shopmanager.app.ui.materials

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.DeleteIconButton
import com.shopmanager.app.ui.common.GradientIconButton
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(viewModel: MaterialsViewModel = viewModel(), onAddNew: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var deleteTarget by remember { mutableStateOf<Material?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

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
                onShare = {
                    val text = buildMaterialsShareText(state.materials, state.prices)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "مشاركة قائمة المواد"))
                }
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddNew,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("مادة جديدة", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                )
            }
        }
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
                    MaterialsList(
                        materials = filtered,
                        searching = search.isNotBlank(),
                        onEdit = { editingMaterial = it },
                        onDelete = { deleteTarget = it }
                    )
                }
            } else {
                PricesList(materials = state.materials, prices = state.prices, onSave = viewModel::setPrice)
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
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${m.name}\"؟") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMaterial(m.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
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
private fun MaterialsHeader(tab: Int, onTabChange: (Int) -> Unit, onShare: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlassSurface(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
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
            GradientIconButton(icon = Icons.Rounded.Share, contentDescription = "مشاركة", onClick = onShare)
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
                    color = if (selected) MaterialTheme.colorScheme.primary else BrandOnGradient,
                    style = MaterialTheme.typography.labelLarge,
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
        item { Spacer(Modifier.height(72.dp)) }
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

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onEdit
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
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

@Composable
private fun PricesList(materials: List<Material>, prices: Map<String, Double>, onSave: (String, Double) -> Unit) {
    val edited = remember(materials) { mutableStateMapOf<String, String>() }

    if (materials.isEmpty()) {
        EmptyState(icon = Icons.Default.Inventory2, text = "أضف مواد أولاً لتسعيرها")
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(materials, key = { it.id }) { m ->
                ElevatedCard(shape = MaterialTheme.shapes.medium, elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        OutlinedTextField(
                            value = edited[m.name] ?: prices[m.name]?.toString() ?: "",
                            onValueChange = { edited[m.name] = it },
                            modifier = Modifier.width(120.dp),
                            label = { Text("السعر") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
    val nf = NumberFormat.getNumberInstance(Locale("ar"))
    val sb = StringBuilder("📦 قائمة المواد\n\n")
    materials.sortedBy { it.name }.forEach { m ->
        val price = prices[m.name]
        sb.append("• ${m.name}: ${m.quantityLabel()}")
        if (price != null) sb.append(" — ${nf.format(price)} ${AppSettingsState.currencySymbol}")
        sb.append("\n")
    }
    return sb.toString()
}
