package com.shopmanager.app.ui.notes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.notes.NoteLinkType
import com.shopmanager.app.ui.common.ActionIconButton
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.DeleteIconButton
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.listItemEntrance
import com.shopmanager.app.ui.theme.LocalSemanticColors
import com.shopmanager.app.ui.theme.glassHairlineColor
import java.text.SimpleDateFormat
import java.util.Locale

private enum class NoteFilter(val label: String) {
    ALL("الكل"), DEBTS("الديون"), MATERIALS("المواد"), GENERAL("عام"), DONE("منجزة")
}

/**
 * "ملاحظات هامة" - the 4th main tab (see MainActivity's PAGE_NOTES). A
 * general sticky-note list, kept separate from the small per-debt/
 * per-material note fields elsewhere in the app (see PersonEditDialog's/
 * MaterialEditDialog's own "ملاحظة" fields) since those live and die with
 * one specific record, while these are their own standalone list with
 * optional reminders and an optional link to a customer or material.
 *
 * REDESIGN: brought in line with the rest of the app's "iOS 26" look -
 * same liquid-glass TopAppBar used by Debts/Materials/Dashboard (instead
 * of a plain in-column title), the same flat bordered row surface with a
 * press-scale (see PersonRow in DebtsScreen), the shared circular
 * [ActionIconButton]/[DeleteIconButton] affordances instead of a bare
 * IconButton, the same private [EmptyState] look, and the same
 * pill-height + FAB-height bottom-clearance formula every other tab with
 * a floating quick-add button already uses (this tab has one too - see
 * MainActivity's PAGE_NOTES QuickAction - so the last row now actually
 * clears it, which it previously didn't).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    persons: List<Person>,
    materials: List<Material>,
    onOpenPerson: (String) -> Unit,
    onOpenMaterials: (String) -> Unit,
    viewModel: NotesViewModel = viewModel(),
    addNoteRequested: Boolean = false,
    onAddNoteRequestHandled: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val state = viewModel.uiState.collectAsState().value
    val message = viewModel.message.collectAsState().value
    val snackbarHost = remember { SnackbarHostState() }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<ImportantNote?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ImportantNote?>(null) }
    var filter by remember { mutableStateOf(NoteFilter.ALL) }
    // FEATURE ADDED ("تحسينات بواجهة الملاحظات"): بحث بالعنوان/المحتوى/اسم
    // الجهة المرتبطة - نفس شريط البحث الموجود أصلاً بشاشة الديون
    // (DebtsScreen)، ما كان بشاشة الملاحظات إشي مشابه غير رقاقات الفلترة.
    val search = remember { mutableStateOf("") }

    // Same formula as DebtsScreen/MaterialsScreen: pill height + a fixed
    // safety margin, so the last row in the list scrolls fully clear of the
    // pill (and the shared "+" beside it — see below) with a visible gap,
    // instead of stopping close enough to peek through its transparent
    // side margins.
    val bottomSafetyMargin = 56.dp + 24.dp
    val listBottomClearance = LocalFloatingBottomNavHeight.current + bottomSafetyMargin

    // The shared "+" beside the bottom nav pill can't reach into this
    // screen's own dialog state directly - same request/handled pattern as
    // DebtsScreen's addPersonRequested (see MainActivity).
    LaunchedEffect(addNoteRequested) {
        if (addNoteRequested) {
            editingNote = null
            showEditDialog = true
            onAddNoteRequestHandled()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val filtered = remember(state.notes, filter, search.value) {
        val byFilter = when (filter) {
            NoteFilter.ALL -> state.notes.filter { !it.isDone }
            NoteFilter.DEBTS -> state.notes.filter { !it.isDone && it.linkType == NoteLinkType.PERSON }
            NoteFilter.MATERIALS -> state.notes.filter { !it.isDone && it.linkType == NoteLinkType.MATERIAL }
            NoteFilter.GENERAL -> state.notes.filter { !it.isDone && it.linkType == NoteLinkType.NONE }
            NoteFilter.DONE -> state.notes.filter { it.isDone }
        }
        if (search.value.isBlank()) byFilter
        else byFilter.filter {
            it.title.contains(search.value, ignoreCase = true) ||
                it.content.contains(search.value, ignoreCase = true) ||
                it.linkedName.contains(search.value, ignoreCase = true)
        }
    }

    Scaffold(
        // Same edge-to-edge treatment as DebtsScreen: the outer app-level
        // Scaffold in MainActivity already pads for the bottom nav/system
        // bar once, so only bottom+horizontal safe-area insets are kept
        // here to avoid double-padding a gap above the floating nav bar.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
        snackbarHost = { GlassSnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("ملاحظات هامة", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = BrandOnGradient,
                    titleContentColor = BrandOnGradient,
                    actionIconContentColor = BrandOnGradient
                ),
                // UNIFIED ON CLAUDE'S DESIGN: removed the old boxed
                // liquidGlassSurface panel (rounded bottom corners) this bar
                // used to sit on — it now sits flush on the plain background
                // like Home's own header and every Claude screen.
                // BUG FIXED ("الأيقونة فوق الكلمة"): same fix as
                // DebtsScreen/DashboardScreen/MaterialsScreen — the
                // hamburger is this bar's own `navigationIcon` now, not a
                // floating overlay from MainActivity sitting on top of the
                // title in the same corner.
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "القائمة")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // BUG FIXED ("الملاحظات ما لها نفس انسجام باقي التطبيق"): every
            // other tab with a live count (Dashboard's totals, Debts'
            // StatsRow below) opens with the same bordered flat-card "quick
            // stats" row right under the glass header before anything else
            // — Notes used to skip straight from the header into the
            // filter chips with nothing there, which is exactly what read
            // as visually thinner/less finished next to Debts. Same recipe
            // as DebtsScreen.StatsRow (flat Surface + hairline border +
            // AnimatedCounterText), just with numbers that make sense for
            // a note list.
            NotesStatsRow(
                total = state.notes.size,
                active = state.notes.count { !it.isDone },
                pinned = state.notes.count { it.isPinned }
            )

            // FEATURE ADDED ("تحسينات بواجهة الملاحظات"): نفس شريط البحث
            // الكبسولي المستخدم بشاشة الديون (DebtsScreen) - بحث بالعنوان
            // أو المحتوى أو اسم الجهة المرتبطة، مع رقاقات الفلترة تحته
            // بالضبط متل قبل.
            OutlinedTextField(
                value = search.value,
                onValueChange = { search.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث في الملاحظات...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (search.value.isNotEmpty()) {
                        IconButton(onClick = { search.value = "" }) {
                            Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) }
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (filtered.isEmpty()) {
                EmptyState(
                    icon = if (search.value.isNotBlank()) Icons.Default.Search else Icons.Default.Notes,
                    text = when {
                        search.value.isNotBlank() -> "لا توجد نتائج"
                        filter == NoteFilter.ALL -> "لا توجد ملاحظات بعد\nاضغط \"+\" لإضافة ملاحظة"
                        else -> "لا توجد ملاحظات هنا"
                    }
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp,
                        bottom = listBottomClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filtered, key = { _, note -> note.id }) { index, note ->
                        NoteRow(
                            note = note,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    placementSpec = MotionSpecs.reorderSpring(),
                                    fadeOutSpec = MotionSpecs.listItemFadeOut()
                                )
                                .listItemEntrance(index),
                            onToggleDone = { viewModel.setDone(note, !note.isDone) },
                            onTogglePinned = { viewModel.setPinned(note, !note.isPinned) },
                            onEdit = { editingNote = note; showEditDialog = true },
                            onDelete = { deleteTarget = note },
                            onOpenLink = {
                                when (note.linkType) {
                                    NoteLinkType.PERSON -> if (note.linkedId.isNotBlank()) onOpenPerson(note.linkedId)
                                    // BUG FIXED ("ترابط بين الديون والملاحظات"
                                    // كان يغطي الأشخاص فقط): tapping a
                                    // material-linked note used to just
                                    // switch to the Materials tab in general
                                    // — the person still had to search for
                                    // the exact item themselves, unlike a
                                    // person-linked note which jumps straight
                                    // to that customer's own page. Passing
                                    // the linked material's name through lets
                                    // MaterialsScreen pre-fill its own search
                                    // with it, landing right on that item.
                                    NoteLinkType.MATERIAL -> onOpenMaterials(note.linkedName)
                                    NoteLinkType.NONE -> {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        NoteEditScreen(
            initial = editingNote,
            persons = persons,
            materials = materials,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) { showEditDialog = false; editingNote = null } },
            onSave = { title, content, linkType, linkedId, linkedName, reminderAt ->
                isSaving = true
                val current = editingNote
                if (current == null) {
                    viewModel.addNote(title, content, linkType, linkedId, linkedName, reminderAt) { success ->
                        isSaving = false
                        if (success) { showEditDialog = false; editingNote = null }
                    }
                } else {
                    viewModel.updateNote(current, title, content, linkType, linkedId, linkedName, reminderAt) { success ->
                        isSaving = false
                        if (success) { showEditDialog = false; editingNote = null }
                    }
                }
            }
        )
    }

    deleteTarget?.let { note ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${note.title}\"؟") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { viewModel.deleteNote(note); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

// Same exact recipe as DebtsScreen's private StatsRow: flat bordered
// Surface (no drop shadow), three counts separated by hairline dividers,
// each counting up/down via AnimatedCounterText instead of snapping to the
// new number — the same "quick stats" card every other tab with live
// totals already opens with, so Notes now reads as one continuous design
// language with Debts instead of a plainer, unfinished-looking exception.
@Composable
private fun NotesStatsRow(total: Int, active: Int, pinned: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        // BORDERS UNIFIED WHITE — see GlassCard.kt's comment.
        border = BorderStroke(1.dp, glassHairlineColor(0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("الملاحظات", total)
            NotesVerticalDivider()
            StatItem("نشطة", active)
            NotesVerticalDivider()
            StatItem("مثبتة", pinned)
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.primary
        ) {
            AnimatedCounterText(
                targetValue = value.toDouble(),
                format = { "%.0f".format(it) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NotesVerticalDivider() {
    Box(
        Modifier
            .height(36.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// Same look as DebtsScreen's private EmptyState: 56dp outline-tinted icon,
// 12dp gap, bodyMedium onSurfaceVariant text - one shared "nothing here"
// read across every tab instead of each screen inventing its own spacing.
@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: ImportantNote,
    modifier: Modifier = Modifier,
    onToggleDone: () -> Unit,
    onTogglePinned: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLink: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Same press-scale spring as PersonRow/MaterialRow, and kept on a
    // separate outer Box from the card's own shadow for the same reason
    // documented on PersonRow (scaling a shadow-casting layer can
    // rasterize as a solid block on some low-end GPUs) - belt-and-braces
    // here too even though shadowElevation is already 0.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "noteRowScale"
    )

    Box(modifier.fillMaxWidth().scale(scale)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onEdit,
                    onLongClick = onTogglePinned
                ),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            // BORDERS UNIFIED WHITE — see GlassCard.kt's comment.
            border = BorderStroke(1.dp, glassHairlineColor(0.5f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                ActionIconButton(
                    icon = if (note.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    tint = if (note.isDone) LocalSemanticColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = "تم",
                    onClick = onToggleDone
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.isPinned) {
                            Icon(Icons.Default.PushPin, contentDescription = "مثبتة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            note.title,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (note.isDone) TextDecoration.LineThrough else null,
                            color = if (note.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (note.content.isNotBlank()) {
                        Text(
                            note.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3
                        )
                    }
                    if (note.reminderAt > 0 || note.linkType != NoteLinkType.NONE) {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (note.reminderAt > 0) {
                                InfoChip(
                                    icon = Icons.Default.Schedule,
                                    text = remember(note.reminderAt) {
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(note.reminderAt))
                                    }
                                )
                            }
                            if (note.linkType != NoteLinkType.NONE && note.linkedName.isNotBlank()) {
                                InfoChip(
                                    icon = if (note.linkType == NoteLinkType.PERSON) Icons.Default.AttachMoney else Icons.Default.Inventory2,
                                    text = note.linkedName,
                                    onClick = onOpenLink
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                DeleteIconButton(onClick = onDelete, contentDescription = "حذف الملاحظة")
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Default.ChevronLeft, contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .let { if (onClick != null) it.combinedClickable(onClick = onClick, onLongClick = {}) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
