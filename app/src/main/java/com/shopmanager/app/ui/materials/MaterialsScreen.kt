package com.shopmanager.app.ui.materials

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.ui.theme.WarningAmber as WarningAmberColor
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(viewModel: MaterialsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMaterial by remember { mutableStateOf<Material?>(null) }
    var deleteTarget by remember { mutableStateOf<Material?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    val filtered = if (search.isBlank()) state.materials
    else state.materials.filter { it.name.contains(search, ignoreCase = true) }
    val lowStockCount = state.materials.count { it.minQuantity > 0 && it.quantity <= it.minQuantity }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("المواد والأسعار") },
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
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة مادة")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("المواد") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("الأسعار") })
            }

            AnimatedVisibility(
                visible = lowStockCount > 0,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
            ) {
                Surface(color = WarningAmberColor.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmberColor)
                        Spacer(Modifier.width(8.dp))
                        Text("$lowStockCount مادة وصلت لحد النفاد", color = WarningAmberColor)
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
                            singleLine = true
                        )
                        MaterialsList(
                            materials = filtered,
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

    if (showAddDialog) {
        MaterialEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, qty, unit, minQty ->
                viewModel.addMaterial(name, qty, unit, minQty)
                showAddDialog = false
            }
        )
    }

    editingMaterial?.let { m ->
        MaterialEditDialog(
            initial = m,
            onDismiss = { editingMaterial = null },
            onSave = { name, qty, unit, minQty ->
                viewModel.updateMaterial(m.id, name, qty, unit, minQty)
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
private fun MaterialsList(materials: List<Material>, onEdit: (Material) -> Unit, onDelete: (Material) -> Unit) {
    if (materials.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد مواد بعد") }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(materials, key = { it.id }) { m ->
            val isLow = m.minQuantity > 0 && m.quantity <= m.minQuantity
            ListItem(
                modifier = Modifier.animateItemPlacement(),
                headlineContent = { Text(m.name) },
                supportingContent = {
                    Text(
                        "${m.quantity} ${m.unit}" + if (isLow) " • منخفض" else "",
                        color = if (isLow) WarningAmberColor else Color.Unspecified
                    )
                },
                leadingContent = if (isLow) {
                    { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmberColor) }
                } else null,
                trailingContent = {
                    Row {
                        IconButton(onClick = { onEdit(m) }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                        IconButton(onClick = { onDelete(m) }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                    }
                }
            )
            Divider()
        }
    }
}

@Composable
private fun PricesList(materials: List<Material>, prices: Map<String, Double>, onSave: (String, Double) -> Unit) {
    val edited = remember(materials) { mutableStateMapOf<String, String>() }

    if (materials.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("أضف مواد أولاً لتسعيرها") }
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            items(materials, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.name, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = edited[m.name] ?: prices[m.name]?.toString() ?: "",
                        onValueChange = { edited[m.name] = it },
                        modifier = Modifier.width(120.dp),
                        label = { Text("السعر") },
                        singleLine = true
                    )
                }
                Divider()
            }
        }
        Button(
            onClick = {
                edited.forEach { (name, value) -> value.toDoubleOrNull()?.let { onSave(name, it) } }
                edited.clear()
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text("حفظ كل الأسعار") }
    }
}

private fun buildMaterialsShareText(materials: List<Material>, prices: Map<String, Double>): String {
    val nf = NumberFormat.getNumberInstance(Locale("ar"))
    val sb = StringBuilder("📦 قائمة المواد\n\n")
    materials.sortedBy { it.name }.forEach { m ->
        val price = prices[m.name]
        sb.append("• ${m.name}: ${m.quantity} ${m.unit}")
        if (price != null) sb.append(" — ${nf.format(price)} ل.س")
        sb.append("\n")
    }
    return sb.toString()
}
