package com.shopmanager.app.ui.materials

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialUnit
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.GlassIconButton
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.GlassAlertDialog

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
    val catalog by viewModel.catalog.collectAsState()
    val message by viewModel.message.collectAsState()
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

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    // PERF: same remember-keyed fix as MaterialsScreen — skip re-filtering
    // the whole catalog on every unrelated recomposition (dialogs opening,
    // the snackbar message clearing), only on an actual search/catalog
    // change.
    val filtered = remember(search, catalog) {
        if (search.isBlank()) catalog else catalog.filter { it.name.contains(search, ignoreCase = true) }
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
            TopAppBar(
                title = { Text("اختر مادة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // BUG FIXED: two issues here. (1) Icons.Default.ArrowBack
                    // always points left, which is backwards for a back
                    // button in this app's RTL Arabic layout — swapped for
                    // the AutoMirrored version so it flips to point right,
                    // matching the reading/navigation direction. (2) the
                    // button only had `start` padding (space from the
                    // screen edge) with nothing on the `end` side, so it
                    // sat glued right up against the title with no room to
                    // breathe — added `end` padding to match the same fix
                    // already applied on the Settings screen's back button.
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                        size = 36.dp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = BrandOnGradient,
                    navigationIconContentColor = BrandOnGradient
                ),
                // طلب "تعميم ستايل الزجاج": highlight = false + baseAlpha = 0.72f
                // — راجع الشرح بـ DashboardScreen.kt.
                modifier = Modifier.liquidGlassSurface(
                    RectangleShape,
                    highlight = false,
                    baseAlpha = 0.72f
                )
            )
        },
        // FIX: adding a new catalog name used to be a permanently-visible
        // text field + button squeezed in above the list, competing with
        // search for the top of the screen. It's now a floating "+" button
        // at the bottom - same pattern as MaterialsScreen's own FAB - that
        // opens a small, focused dialog just for typing the one new name.
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("مادة جديدة", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "اضغط على اسم المادة لإدخال الكمية، أو أضف اسمًا جديدًا من الزر بالأسفل",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            AppTextField(
                value = search,
                onValueChange = { search = it },
                label = "بحث",
                placeholder = "بحث بالقائمة...",
                showLabel = false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (catalog.isEmpty()) "القائمة فاضية، أضف أول مادة من الزر بالأسفل" else "لا توجد نتائج",
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
                            onEdit = { editTarget = item },
                            onDelete = { deleteTarget = item }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
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
        QuantityEntryDialog(
            materialName = item.name,
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

@Composable
private fun CatalogRow(item: MaterialCatalogItem, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = remember(item.name) { avatarColorFor(item.name) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "catalogRowScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
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
            // FEATURE ADDED ("تعديل المواد الثابتة بعد إضافتها"): a separate
            // pencil button opens the rename dialog — kept apart from
            // `onClick` (which still picks the item to log a shortage
            // quantity, this row's main action) so renaming isn't hidden
            // behind the same tap.
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل الاسم") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف من القائمة") }
        }
    }
}

/**
 * Focused dialog opened by the floating "+" button, just for typing one new
 * standing-list name. Enter on the keyboard saves too, so adding several
 * names in a row (type, Enter, type, Enter...) doesn't need reaching for
 * the button each time.
 */
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, unit: String, notes: String) -> Unit
) {
    // FIX: same free-typed-decimal issue as MaterialEditDialog (see its
    // comment) - this is the dialog actually used every time a new
    // shortage is added from the catalog, so it needed the identical fix:
    // a whole-number stepper instead of a text field that could take
    // "1.5" for a كيلو entry, plus the two new نص كيلو / ربع كيلو units.
    var quantity by remember { mutableStateOf(1) }
    var unit by remember { mutableStateOf(MaterialUnit.KG) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(materialName) },
        text = {
            Column {
                Text(
                    "الكمية المطلوبة", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                QuantityStepper(
                    value = quantity,
                    unitLabel = unit.label,
                    enabled = !isSaving,
                    onValueChange = { quantity = it.coerceAtLeast(1) }
                )
                Text(
                    "الوحدة", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                UnitPicker(selected = unit, enabled = !isSaving, onSelected = { unit = it })
                AppTextField(
                    value = notes, onValueChange = { notes = it }, enabled = !isSaving,
                    label = "ملاحظة (اختياري)",
                    singleLine = false, minLines = 1, maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("حفظ")
                }
            }
        },
        dismissButton = { TextButton(enabled = !isSaving, shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}
