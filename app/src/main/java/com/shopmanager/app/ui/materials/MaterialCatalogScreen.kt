package com.shopmanager.app.ui.materials

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialUnit
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.GlassAlertDialog
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppCapsuleSearchField
import androidx.compose.foundation.lazy.LazyListScope
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.ActionSpec
import com.shopmanager.app.ui.common.AppActionPill
import com.shopmanager.app.ui.common.rememberSmartSearch
import com.shopmanager.app.ui.theme.LocalSemanticColors
import androidx.compose.material.icons.filled.Close
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.shopmanager.app.data.performance.listItemEntrance

/**
 * Standalone screen for picking which shortage to add: pick a name from the
 * shop's standing spice catalog instead of typing it every time, then enter
 * the quantity needed - like ticking an item off at the market. New names
 * can be added to the catalog inline the first time they're needed, and
 * removed later if no longer carried. Saving one item keeps you on this
 * screen so you can add several shortages in a row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialCatalogScreen(viewModel: MaterialsViewModel, onBack: () -> Unit) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var isAddingCatalogItem by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pickedItem by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    // FEATURE ADDED ("تعديل المواد الثابتة بعد إضافتها"): the catalog item
    // currently open for renaming, if any — same one-dialog-at-a-time
    // pattern as `pickedItem`/`deleteTarget` above.
    var editTarget by remember { mutableStateOf<MaterialCatalogItem?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val suggested by viewModel.suggestions.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    // PERF: same remember-keyed fix as MaterialsScreen — skip re-filtering
    // the whole catalog on every unrelated recomposition (dialogs opening,
    // the snackbar message clearing), only on an actual search/catalog
    // change.
    val filtered = rememberSmartSearch(catalog, search) { it.name }
    val showSuggested = search.isBlank() && suggested.isNotEmpty()
    val rest = remember(filtered, suggested, showSuggested) {
        if (showSuggested) {
            val ids = suggested.map { it.id }.toSet()
            filtered.filter { it.id !in ids }
        } else filtered
    }

    Scaffold(
        // Off-pager screen (no bottom nav bar of its own) — the outer app
        // Scaffold already reserves the real bottom/horizontal safe-area
        // space one level up in NavHost's padding, so this Scaffold's own
        // content insets are zeroed to avoid reserving that same space
        // twice. The TopAppBar below still handles the status bar inset
        // entirely on its own regardless of this setting.
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            ScreenTopBar(
                title = "اختر مادة",
                navigation = {
                    ScreenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack
                    )
                }
            )
        },
        floatingActionButton = {
            Surface(
                onClick = { showAddDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.height(56.dp)
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("مادة جديدة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppCapsuleSearchField(
                query = search,
                onQueryChange = { search = it },
                placeholder = "ابحث عن مادة...",
                modifier = Modifier.padding(start = AppScreenPadding, end = AppScreenPadding, top = 2.dp, bottom = 8.dp)
            )
            if (filtered.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Default.Inventory2,
                    text = if (catalog.isEmpty()) "القائمة فاضية، أضف أول مادة من الزر بالأسفل" else "لا توجد نتائج"
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = 2.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(AppGroupGap)
                ) {
                    if (showSuggested) {
                        item(key = "suggestedTitle") {
                            AppSectionTitle(text = "مقترحة لك (${suggested.size})", modifier = Modifier.padding(bottom = 4.dp))
                        }
                        catalogRows(suggested, "s_", true, viewModel, { pickedItem = it }, { editTarget = it }, { deleteTarget = it })
                        if (rest.isNotEmpty()) {
                            item(key = "restTitle") {
                                AppSectionTitle(text = "كل المواد (${rest.size})", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            }
                        }
                        catalogRows(rest, "r_", false, viewModel, { pickedItem = it }, { editTarget = it }, { deleteTarget = it })
                    } else {
                        item(key = "catalogTitle") {
                            AppSectionTitle(
                                text = if (search.isBlank()) "اختر مادة (${filtered.size})" else "النتائج (${filtered.size})",
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        catalogRows(filtered, "a_", false, viewModel, { pickedItem = it }, { editTarget = it }, { deleteTarget = it })
                    }
                    item(key = "catalogHint") {
                        AppFootnote(
                            "اضغط على اسم المادة لإدخال الكمية",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCatalogItemDialog(
            isSaving = isAddingCatalogItem,
            onDismiss = { showAddDialog = false },
            onSave = { name ->
                isAddingCatalogItem = true
                viewModel.addCatalogItem(name) { success ->
                    isAddingCatalogItem = false
                    if (success) showAddDialog = false
                }
            }
        )
    }

    editTarget?.let { item ->
        var isSavingRename by remember { mutableStateOf(false) }
        EditCatalogItemDialog(
            initialName = item.name,
            isSaving = isSavingRename,
            onDismiss = { if (!isSavingRename) editTarget = null },
            onSave = { newName ->
                isSavingRename = true
                viewModel.updateCatalogItem(item.id, item.name, newName) { success ->
                    isSavingRename = false
                    if (success) editTarget = null
                }
            }
        )
    }

    pickedItem?.let { item ->
        var isAddingMaterial by remember { mutableStateOf(false) }
        val learned = remember(item.name) { viewModel.suggestionFor(item.name) }
        QuantityEntryDialog(
            materialName = item.name,
            initialQuantity = learned?.first ?: 1,
            initialUnit = learned?.second ?: MaterialUnit.KG,
            isSaving = isAddingMaterial,
            onDismiss = { if (!isAddingMaterial) pickedItem = null },
            onSave = { quantity, unit, notes ->
                // Every material added here is, by the nature of this list,
                // something the shop is short on and needs to buy - so it's
                // simply added with the quantity needed, no threshold or
                // stock-level bookkeeping involved.
                isAddingMaterial = true
                viewModel.addMaterial(item.name, quantity, unit, notes) { success ->
                    isAddingMaterial = false
                    if (success) pickedItem = null
                }
            }
        )
    }

    deleteTarget?.let { item ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف من القائمة الثابتة") },
            text = { Text("هل تريد حذف \"${item.name}\" من القائمة الثابتة؟ (هذا لا يحذف أي كمية مخزنة سابقًا)") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { viewModel.deleteCatalogItem(item.id); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

private fun LazyListScope.catalogRows(
    list: List<MaterialCatalogItem>,
    keyPrefix: String,
    learned: Boolean,
    viewModel: MaterialsViewModel,
    onPick: (MaterialCatalogItem) -> Unit,
    onEdit: (MaterialCatalogItem) -> Unit,
    onDelete: (MaterialCatalogItem) -> Unit
) {
    itemsIndexed(list, key = { _, c -> keyPrefix + c.id }, contentType = { _, _ -> "catalogItem" }) { index, item ->
        Box(Modifier.listItemEntrance(index)) {
            CatalogRow(
                item = item,
                hint = if (learned) remember(item.name) { learnedHint(viewModel, item.name) } else null,
                shape = groupedRowShape(index, list.lastIndex),
                onClick = { onPick(item) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) }
            )
        }
    }
}

private fun learnedHint(viewModel: MaterialsViewModel, name: String): String? {
    val insight = viewModel.insightFor(name) ?: return null
    if (insight.added == 0) return null
    val parts = ArrayList<String>(3)
    parts.add(if (insight.added == 1) "أضفتها مرة" else "أضفتها ${insight.added} مرات")
    insight.daysSinceLast?.let { parts.add(if (it == 0) "اليوم" else "منذ $it يوم") }
    val unit = insight.unit.label
    parts.add(if (unit.isBlank()) "${insight.qty}" else "${insight.qty} $unit")
    return parts.joinToString(" • ")
}

@Composable
private fun CatalogRow(
    item: MaterialCatalogItem,
    hint: String?,
    shape: Shape,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = remember(item.name) { avatarColorFor(item.name) }
    AppRowSurface(shape = shape, onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconCircle(color = color, size = 40.dp) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            AppActionPill(
                height = 36.dp,
                actions = listOf(
                    ActionSpec(Icons.Default.Edit, "تعديل الاسم", null, onEdit),
                    ActionSpec(Icons.Default.Close, "حذف من القائمة", LocalSemanticColors.current.danger, onDelete)
                )
            )
        }
    }
}

@Composable
private fun AddCatalogItemDialog(isSaving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("مادة جديدة للقائمة الثابتة") },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "اسم المادة",
                placeholder = "اسم المادة...",
                enabled = !isSaving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { if (name.isNotBlank() && !isSaving) onSave(name.trim()) }
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !isSaving,
                shape = RectangleShape,
                onClick = { onSave(name.trim()) }
            ) {
                if (isSaving) {
                    com.shopmanager.app.ui.common.AppSpinner(size = 16.dp, color = androidx.compose.material3.LocalContentColor.current)
                } else {
                    Text("إضافة")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving, shape = RectangleShape) { Text("إلغاء") } }
    )
}

/**
 * FEATURE ADDED ("تعديل المواد الثابتة بعد إضافتها"): renames one existing
 * catalog entry in place — same shape as [AddCatalogItemDialog], pre-filled
 * with the current name, but wired to `updateCatalogItem` instead of
 * `addCatalogItem`.
 */
@Composable
private fun EditCatalogItemDialog(initialName: String, isSaving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("تعديل اسم المادة") },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "اسم المادة",
                placeholder = "اسم المادة...",
                enabled = !isSaving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { if (name.isNotBlank() && !isSaving) onSave(name.trim()) }
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !isSaving,
                shape = RectangleShape,
                onClick = { onSave(name.trim()) }
            ) {
                if (isSaving) {
                    com.shopmanager.app.ui.common.AppSpinner(size = 16.dp, color = androidx.compose.material3.LocalContentColor.current)
                } else {
                    Text("حفظ")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving, shape = RectangleShape) { Text("إلغاء") } }
    )
}

@Composable
private fun QuantityEntryDialog(
    materialName: String,
    initialQuantity: Int = 1,
    initialUnit: MaterialUnit = MaterialUnit.KG,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, unit: String, notes: String) -> Unit
) {
    // FIX: same free-typed-decimal issue as MaterialEditDialog (see its
    // comment) - this is the dialog actually used every time a new
    // shortage is added from the catalog, so it needed the identical fix:
    // a whole-number stepper instead of a text field that could take
    // "1.5" for a كيلو entry, plus the two new نص كيلو / ربع كيلو units.
    var quantity by remember { mutableStateOf(initialQuantity) }
    var unit by remember { mutableStateOf(initialUnit) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(materialName) },
        text = {
            Column {
                AppSectionTitle("الكمية المطلوبة", Modifier.padding(bottom = 8.dp))
                QuantityStepper(
                    value = quantity,
                    unitLabel = unit.label,
                    enabled = !isSaving,
                    onValueChange = { quantity = it.coerceAtLeast(1) }
                )
                AppSectionTitle("الوحدة", Modifier.padding(top = 18.dp, bottom = 8.dp))
                UnitPicker(selected = unit, enabled = !isSaving, onSelected = { unit = it })
                AppTextField(
                    value = notes, onValueChange = { notes = it }, enabled = !isSaving,
                    label = "ملاحظة (اختياري)",
                    singleLine = false, minLines = 1, maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 10.dp, start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                shape = RectangleShape,
                onClick = {
                    if (quantity <= 0) {
                        error = "أدخل كمية صحيحة"
                    } else {
                        error = null
                        onSave(quantity.toDouble(), unit.label, notes.trim())
                    }
                }
            ) {
                if (isSaving) {
                    com.shopmanager.app.ui.common.AppSpinner(size = 16.dp, color = androidx.compose.material3.LocalContentColor.current)
                } else {
                    Text("حفظ")
                }
            }
        },
        dismissButton = { TextButton(enabled = !isSaving, shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}
