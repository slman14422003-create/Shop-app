package com.shopmanager.app.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.notes.NoteLinkType
import com.shopmanager.app.ui.common.DeleteIconButton
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
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
 */
@Composable
fun NotesScreen(
    persons: List<Person>,
    materials: List<Material>,
    onOpenPerson: (String) -> Unit,
    onOpenMaterials: () -> Unit,
    viewModel: NotesViewModel = viewModel(),
    addNoteRequested: Boolean = false,
    onAddNoteRequestHandled: () -> Unit = {}
) {
    val state = viewModel.uiState.collectAsState().value
    val message = viewModel.message.collectAsState().value
    val snackbarHost = remember { SnackbarHostState() }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<ImportantNote?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ImportantNote?>(null) }
    var filter by remember { mutableStateOf(NoteFilter.ALL) }

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

    val filtered = remember(state.notes, filter) {
        when (filter) {
            NoteFilter.ALL -> state.notes.filter { !it.isDone }
            NoteFilter.DEBTS -> state.notes.filter { !it.isDone && it.linkType == NoteLinkType.PERSON }
            NoteFilter.MATERIALS -> state.notes.filter { !it.isDone && it.linkType == NoteLinkType.MATERIAL }
            NoteFilter.GENERAL -> state.notes.filter { !it.isDone && it.linkType == NoteLinkType.NONE }
            NoteFilter.DONE -> state.notes.filter { it.isDone }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "ملاحظات هامة",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("لا توجد ملاحظات", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp,
                        bottom = LocalFloatingBottomNavHeight.current + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onToggleDone = { viewModel.setDone(note, !note.isDone) },
                            onTogglePinned = { viewModel.setPinned(note, !note.isPinned) },
                            onEdit = { editingNote = note; showEditDialog = true },
                            onDelete = { deleteTarget = note },
                            onOpenLink = {
                                when (note.linkType) {
                                    NoteLinkType.PERSON -> if (note.linkedId.isNotBlank()) onOpenPerson(note.linkedId)
                                    NoteLinkType.MATERIAL -> onOpenMaterials()
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
        NoteEditDialog(
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
                TextButton(onClick = { viewModel.deleteNote(note); deleteTarget = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: ImportantNote,
    onToggleDone: () -> Unit,
    onTogglePinned: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLink: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            IconButton(onClick = onToggleDone, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (note.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "تم",
                    tint = if (note.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
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
            DeleteIconButton(onClick = onDelete, contentDescription = "حذف الملاحظة")
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
