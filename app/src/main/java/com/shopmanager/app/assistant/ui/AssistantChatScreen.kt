package com.shopmanager.app.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.AssistantChip
import com.shopmanager.app.assistant.AssistantMessage
import com.shopmanager.app.assistant.AssistantQueries
import com.shopmanager.app.assistant.AssistantViewModel
import com.shopmanager.app.assistant.RobotMood
import com.shopmanager.app.ui.common.AppSheet
import com.shopmanager.app.ui.theme.LocalIsDarkTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** شاشة المحادثة الكاملة مع المساعد المحلي. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssistantChatScreen(
    assistant: AssistantViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val messages by assistant.messages.collectAsState()
    val mood by assistant.mood.collectAsState()
    val voiceLevel = rememberVoiceLevelState(assistant)
    val assistantData by assistant.data.collectAsState()
    val thinking by assistant.thinking.collectAsState()
    val settings by assistant.settings.collectAsState()
    val undoable by assistant.undoable.collectAsState()
    val draft by assistant.draft.collectAsState()

    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(messages, query, searching) {
        if (searching && query.isNotBlank()) messages.filter { it.text.contains(query.trim(), ignoreCase = true) || (it.card as? com.shopmanager.app.assistant.AssistantCard.Result)?.details?.contains(query.trim()) == true } else messages
    }
    val pinned by assistant.pinned.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // "تعديل" أو اقتراح غير مكتمل يملأ الحقل.
    LaunchedEffect(draft) {
        if (draft.isNotEmpty()) { input = draft; assistant.setDraft("") }
    }
    LaunchedEffect(messages.size, thinking) {
        val extra = if (thinking) 1 else 0
        val last = messages.size + extra - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    fun submit() {
        val t = input.trim()
        if (t.isNotEmpty()) { assistant.send(t); input = "" }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding().navigationBarsPadding()) {
        // ---- الترويسة
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
            RobotAvatar(mood, size = 44.dp, animate = settings.robotAnimation, levelState = voiceLevel)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("المساعد المحلي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (thinking) "يفكر…" else "جاهز",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (thinking) AssistantWarnOrange else AssistantSuccessGreen
                )
            }
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, contentDescription = "بحث") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "المزيد") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("ما يستطيع المساعد فعله") }, onClick = { menuOpen = false; showHelp = true })
                    DropdownMenuItem(text = { Text("وضع القائمة الطويلة (صوتي)") }, onClick = { menuOpen = false; assistant.startListMode() })
                    DropdownMenuItem(text = { Text(if (settings.dryRun) "إيقاف وضع التجربة" else "وضع التجربة بدون تنفيذ") }, onClick = { menuOpen = false; assistant.updateSettings(settings.copy(dryRun = !settings.dryRun)) })
                    DropdownMenuItem(text = { Text("إعدادات المساعد") }, onClick = { menuOpen = false; onOpenSettings() })
                    DropdownMenuItem(text = { Text("مسح السجل") }, onClick = { menuOpen = false; confirmClear = true })
                }
            }
        }

        if (searching) OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text("ابحث في المحادثة") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        if (settings.dryRun) Text(
            "وضع التجربة: لن يُنفَّذ أي شيء", Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium, color = AssistantWarnOrange
        )

        // ---- الرسائل
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty() && !thinking) {
                EmptyState(mood, settings.robotAnimation, voiceLevel, assistantData) { assistant.send(it) }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { m ->
                        val index = messages.indexOf(m)
                        val prev = messages.getOrNull(index - 1)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (prev == null || !sameDay(prev.time, m.time)) DateDivider(m.time)
                            Bubble(m, assistant, undoable, pinned = m.text in pinned, onPin = { assistant.pin(m.text) }) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("message", m.text))
                                Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                            }
                            if (!m.fromUser && m.chips.isNotEmpty() && index == messages.lastIndex) {
                                ChipsRow(m.chips) { chip -> onChip(chip, assistant) { input = it } }
                            }
                        }
                    }
                    if (thinking) item(key = "thinking") { ThinkingDots() }
                }
            }
        }

        UndoBarView(assistant)
        VoiceStatusArea(assistant) { input = it }
        QuickButtonsRow(assistant) { input = it }
        InputBar(input, { input = it }, ::submit, leading = { MicButton(assistant) })
    }

    if (showHelp) HelpSheet(onDismiss = { showHelp = false }) { text ->
        showHelp = false
        input = text
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل") },
            text = { Text("سيُحذف سجل المحادثة من هذا الجهاز فقط. الديون والبيانات لا تتأثر.") },
            confirmButton = { TextButton(onClick = { assistant.clearHistory(); confirmClear = false }) { Text("مسح") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }
}

internal fun onChip(chip: AssistantChip, assistant: AssistantViewModel, fill: (String) -> Unit) {
    if (chip.sendNow) assistant.send(chip.text) else fill(chip.text)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: AssistantMessage, assistant: AssistantViewModel, undoable: Set<Long>, pinned: Boolean = false, onPin: () -> Unit = {}, onCopy: () -> Unit) {
    val dark = LocalIsDarkTheme.current
    val userBg = if (dark) Color.White else MaterialTheme.colorScheme.inverseSurface
    val userFg = if (dark) Color.Black else MaterialTheme.colorScheme.inverseOnSurface
    val bg = if (m.fromUser) userBg else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (m.fromUser) userFg else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.Start else Arrangement.End) {
        // في RTL يبدأ Start من اليمين: فقاعتي على اليمين وفقاعة المساعد على اليسار.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bg,
            contentColor = fg,
            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = {}, onLongClick = onCopy)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!m.fromUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RobotAvatar(RobotMood.READY, size = 22.dp, animate = false)
                        Text("المساعد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyLarge)
                if (m.fromUser && m.text.isNotBlank()) Text(
                    if (pinned) "★ مثبّت" else "☆ تثبيت",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { onPin() }
                )
                m.card?.let { AssistantCardView(m, it, assistant, undoable) }
            }
        }
    }
}

@Composable
private fun ChipsRow(chips: List<AssistantChip>, onClick: (AssistantChip) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { c ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clip(CircleShape).clickable { onClick(c) }
            ) { Text(c.label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun ThinkingDots() {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), CircleShape))
        }
    }
}

@Composable
private fun DateDivider(time: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            dayLabel(time), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(t: Long): String {
    val now = System.currentTimeMillis()
    return when {
        sameDay(t, now) -> "اليوم"
        sameDay(t, now - 86_400_000L) -> "أمس"
        else -> SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(t))
    }
}

@Composable
internal fun InputBar(value: String, onChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leading != null) leading()
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("اكتب أمرك… مثال: سجل على سامر 20 ألف") },
            shape = RoundedCornerShape(28.dp),
            maxLines = 4,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        Surface(
            onClick = onSend,
            enabled = value.isNotBlank(),
            shape = CircleShape,
            color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال",
                    tint = if (value.isNotBlank()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(mood: RobotMood, animate: Boolean, levelState: State<Float>, data: com.shopmanager.app.assistant.AssistantData, onExample: (String) -> Unit) {
    val groups = remember(data) { AssistantQueries.dynamicGroups(data) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RobotAvatar(mood, size = 110.dp, animate = animate, levelState = levelState)
        Text("أنا مساعدك المحلي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (groups.isEmpty()) "أفهم العربية بلهجاتها وأنفّذ بدون إنترنت. أضف ديونًا أو مواد لتظهر لك اقتراحات من بياناتك."
            else "أفهم العربية بلهجاتها وأنفّذ بدون إنترنت. جرّب أحد الاقتراحات:",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        groups.forEach { (group, examples) ->
            Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.forEach { ex ->
                    Surface(
                        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clip(CircleShape).clickable { onExample(ex) }
                    ) { Text(ex, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** ورقة سفلية: أمثلة لكل أمر. النقر على مثال يضعه في حقل الكتابة. */
@Composable
fun HelpSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AppSheet(title = "ما يستطيع المساعد فعله", onDismiss = onDismiss) { _ ->
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistantQueries.capabilityGroups.forEach { (group, examples) ->
                Text(group, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                examples.forEach { ex ->
                    Text(
                        "• $ex",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(ex) }.padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
