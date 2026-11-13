package com.shopmanager.app.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shopmanager.app.assistant.AssistantChip
import com.shopmanager.app.assistant.AssistantViewModel
import com.shopmanager.app.assistant.UndoBar
import com.shopmanager.app.assistant.engine.MaterialRef
import com.shopmanager.app.assistant.engine.RankedSpeech
import com.shopmanager.app.assistant.voice.VoiceUiState
import com.shopmanager.app.assistant.voice.ui.VoiceDownloadBar
import com.shopmanager.app.assistant.voice.ui.VoiceDownloadDialog
import com.shopmanager.app.ui.common.AppPillButton
import kotlinx.coroutines.delay

@Composable
fun MicButton(assistant: AssistantViewModel, modifier: Modifier = Modifier) {
    val ui by assistant.voice.uiState.collectAsState()
    val context = LocalContext.current
    val listening = ui is VoiceUiState.Listening
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) assistant.startVoice()
    }
    Surface(
        onClick = {
            if (listening) assistant.stopVoice()
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) assistant.startVoice()
            else launcher.launch(Manifest.permission.RECORD_AUDIO)
        },
        shape = CircleShape,
        color = if (listening) AssistantWarnOrange else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = if (listening) "إيقاف" else "تحدّث")
        }
    }
}

@Composable
fun VoiceStatusArea(assistant: AssistantViewModel, onTypeInstead: (String) -> Unit) {
    val ui by assistant.voice.uiState.collectAsState()
    val preview by assistant.voicePreview.collectAsState()
    val listMode by assistant.listMode.collectAsState()
    val items by assistant.listItems.collectAsState()
    VoiceDownloadDialog(assistant.voice)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VoiceDownloadBar(assistant.voice)
        when (val s = ui) {
            is VoiceUiState.Listening -> StatusCard(
                if (listMode) "أنا أسمع… قُل المواد وبعد كل وحدة وقفة، وقُل \"خلصت\" بالنهاية" else "أنا أسمع…",
                s.partial,
                s.level
            ) { assistant.cancelVoice() }
            is VoiceUiState.Preparing -> StatusCard("بجهّز الصوت…", "", 0f) { assistant.cancelVoice() }
            is VoiceUiState.Processing -> StatusCard("بفهم كلامك…", "", 0f) { assistant.cancelVoice() }
            is VoiceUiState.Unavailable -> {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = AssistantWarnOrange)
                LaunchedEffect(s) { delay(4000L) }
            }
            else -> {}
        }
        if (listMode && items.isNotEmpty()) ListChips(assistant, items)
        preview?.let { PreviewBar(assistant, it, onTypeInstead) }
    }
}

@Composable
private fun StatusCard(title: String, partial: String, level: Float, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size((10 + (level * 40f).coerceIn(0f, 14f)).dp).background(AssistantWarnOrange, CircleShape))
                Text(title, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "إلغاء") }
            }
            if (partial.isNotBlank()) Text(partial, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ListChips(assistant: AssistantViewModel, items: List<String>) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { i, it ->
                Surface(
                    shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.clip(CircleShape).clickable { editing = i; text = it }
                ) { Text(it, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
            }
        }
        AppPillButton("تم، نفّذ القائمة", { assistant.finishList() }, Modifier.fillMaxWidth(), height = 40.dp)
    }
    val idx = editing
    if (idx != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("تعديل البند") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { assistant.editListItem(idx, text); editing = null }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { assistant.editListItem(idx, ""); editing = null }) { Text("حذف") } }
        )
    }
}

@Composable
private fun PreviewBar(assistant: AssistantViewModel, p: RankedSpeech, onTypeInstead: (String) -> Unit) {
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val words = remember(p.text) { p.text.split(Regex("\\s+")) }
    val weakIdx = p.weak.map { it.index }.toSet()
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("هل هذا ما قلته؟", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                words.forEachIndexed { i, w ->
                    Box {
                        val weak = i in weakIdx
                        Text(
                            buildAnnotatedString {
                                if (weak) withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = AssistantWarnOrange, fontWeight = FontWeight.Bold)) { append(w) }
                                else append(w)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable(enabled = weak) { menuFor = i }
                        )
                        DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }) {
                            val alts = p.weak.firstOrNull { it.index == i }?.alternatives.orEmpty()
                            if (alts.isEmpty()) DropdownMenuItem(text = { Text("لا بدائل") }, onClick = { menuFor = null })
                            alts.forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { menuFor = null; assistant.applyWeakAlternative(i, a) }) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AppPillButton("إرسال", { assistant.sendPreview(p.text) }, Modifier.weight(1f), height = 40.dp)
                AppPillButton("تعديل", { assistant.dismissPreview(); onTypeInstead(p.text) }, Modifier.weight(1f), tonal = true, height = 40.dp)
                AppPillButton("إلغاء", { assistant.dismissPreview() }, Modifier.weight(1f), tonal = true, height = 40.dp)
            }
        }
    }
}

@Composable
fun UndoBarView(assistant: AssistantViewModel, modifier: Modifier = Modifier) {
    val bar by assistant.undoBar.collectAsState()
    val b: UndoBar = bar ?: return
    var remaining by remember(b.undoId) { mutableStateOf(((b.expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)) }
    LaunchedEffect(b.undoId) {
        while (remaining > 0) { delay(1000L); remaining = ((b.expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0) }
    }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.inverseSurface, modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                b.label.take(90), Modifier.weight(1f).padding(vertical = 10.dp), maxLines = 2,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.inverseOnSurface
            )
            TextButton(onClick = { assistant.undo(b.undoId); assistant.dismissUndoBar() }) { Text("تراجع ($remaining)", color = AssistantSuccessGreen) }
        }
    }
}

@Composable
fun QuickButtonsRow(assistant: AssistantViewModel, onFill: (String) -> Unit) {
    val quick by assistant.quickButtons.collectAsState()
    if (quick.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        quick.forEach { c: AssistantChip ->
            Surface(
                shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clip(CircleShape).clickable { onChip(c, assistant, onFill) }
            ) { Text(c.label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun LineEditorDialog(
    lines: List<MaterialRef>,
    important: Boolean?,
    onApply: (index: Int, name: String?, qty: Double?, unit: String?) -> Unit,
    onToggleStar: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var index by remember { mutableStateOf(0) }
    var name by remember(index) { mutableStateOf(lines.getOrNull(index)?.name ?: "") }
    var qty by remember(index) { mutableStateOf(lines.getOrNull(index)?.quantity?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var unit by remember(index) { mutableStateOf(lines.getOrNull(index)?.unit ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lines.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEachIndexed { i, l ->
                        Surface(
                            shape = CircleShape,
                            color = if (i == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.clip(CircleShape).clickable { index = i }
                        ) { Text(l.name, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("المادة") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("الكمية") }, singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("الوحدة") }, singleLine = true)
                if (onToggleStar != null) TextButton(onClick = { onToggleStar(); onDismiss() }) { Text(if (important == true) "إلغاء النجمة ⭐" else "جعلها هامة ⭐") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(index, name.trim().ifEmpty { null }, qty.trim().replace('٫', '.').toDoubleOrNull(), unit.trim())
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
