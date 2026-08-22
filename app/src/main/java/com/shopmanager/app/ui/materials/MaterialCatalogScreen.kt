package com.shopmanager.app.ui.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialUnit
import com.shopmanager.app.ui.common.avatarColorFor

/**
 * Standalone screen for the shop's fixed spice list ("مبدأ الجرد"): pick a
 * name from a standing catalog instead of typing it every time, then enter
 * the counted weight. New names can be added to the catalog inline the
 * first time they're needed, and removed later if no longer stocked. Saving
 * one item keeps you on this screen so you can go through several in a row,
 * like a real stocktaking pass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialCatalogScreen(viewModel: MaterialsViewModel, onBack: () -> Unit) {
    val catalog by viewModel.catalog.collectAsState()
    val message by viewModel.message.collectAsState()
    var search by remember { mutableStateOf("") }
    var newItemName by remember { mutableStateOf("") }
    var isAddingCatalogItem by remember { mutableStateOf(false) }
    var pickedItem by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    val filtered = if (search.isBlank()) catalog else catalog.filter { it.name.contains(search, ignoreCase = true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("اختر مادة") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "اضغط على اسم المادة لإدخال الكمية، أو أضف اسمًا جديدًا للقائمة الثابتة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("بحث بالقائمة...") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("مادة جديدة للقائمة الثابتة...") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    enabled = newItemName.isNotBlank() && !isAddingCatalogItem,
                    onClick = {
                        isAddingCatalogItem = true
                        viewModel.addCatalogItem(newItemName.trim()) { success ->
                            isAddingCatalogItem = false
                            if (success) newItemName = ""
                        }
                    }
                ) {
                    if (isAddingCatalogItem) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "إضافة للقائمة")
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (catalog.isEmpty()) "القائمة فاضية، أضف أول مادة من الأعلى" else "لا توجد نتائج",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        CatalogRow(
                            item = item,
                            onClick = { pickedItem = item },
                            onDelete = { deleteTarget = item }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    pickedItem?.let { item ->
        QuantityEntryDialog(
            materialName = item.name,
            onDismiss = { pickedItem = null },
            onSave = { quantity, unit, minQuantity ->
                viewModel.addMaterial(item.name, quantity, unit, minQuantity)
                pickedItem = null
            }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف من القائمة الثابتة") },
            text = { Text("هل تريد حذف \"${item.name}\" من القائمة الثابتة؟ (هذا لا يحذف أي كمية مخزنة سابقًا)") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCatalogItem(item.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun CatalogRow(item: MaterialCatalogItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val color = remember(item.name) { avatarColorFor(item.name) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(item.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف من القائمة") }
        }
    }
}

@Composable
private fun QuantityEntryDialog(
    materialName: String,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, unit: String, minQuantity: Double) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(MaterialUnit.KG) }
    var minQuantity by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(materialName) },
        text = {
            Column {
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it },
                    label = { Text("الكمية المعدودة") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "الوحدة", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
                UnitPicker(selected = unit, onSelected = { unit = it })
                OutlinedTextField(
                    value = minQuantity, onValueChange = { minQuantity = it },
                    label = { Text("حد التنبيه لنفاد الكمية (اختياري)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = quantity.trim().toDoubleOrNull()
                val minQ = minQuantity.trim().toDoubleOrNull() ?: 0.0
                if (q == null || q <= 0) {
                    error = "أدخل كمية صحيحة"
                } else {
                    onSave(q, unit.label, minQ)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
