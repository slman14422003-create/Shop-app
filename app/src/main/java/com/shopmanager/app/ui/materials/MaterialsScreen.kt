package com.shopmanager.app.ui.materials

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.DeleteIconButton
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
    val shortageCount = state.materials.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("المواد والأسعار", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = BrandOnGradient,
                        actionIconContentColor = BrandOnGradient
                    ),
                    modifier = Modifier.background(BrandGradient.brush()),
                    actions = {
                        IconButton(onClick = {
                            val text = buildMaterialsShareText(state.materials, state.prices)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة قائمة المواد"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة")
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("المواد") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الأسعار") })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddNew,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("مادة جديدة") }
                )
            }
        }
    ) { padding ->
        PullToRefreshContent(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = tab == 0 && shortageCount > 0,
                enter = expandVertically(MotionSpecs.expandSpring()) + fadeIn(tween(MotionSpecs.expandMillis())),
                exit = shrinkVertically(tween(MotionSpecs.collapseMillis())) + fadeOut(tween(MotionSpecs.fadeMillis()))
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$shortageCount مادة بانتظار الشراء من السوق",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Crossfade(targetState = tab, label = "materialsTab") { selectedTab ->
                if (selectedTab == 0) {
                    Column {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("بحث عن مادة...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (search.isNotEmpty()) {
                                    IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
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
                    "الكمية المطلوبة: ${material.quantity} ${material.unit}",
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
        sb.append("• ${m.name}: ${m.quantity} ${m.unit}")
        if (price != null) sb.append(" — ${nf.format(price)} ${AppSettingsState.currencySymbol}")
        sb.append("\n")
    }
    return sb.toString()
}
