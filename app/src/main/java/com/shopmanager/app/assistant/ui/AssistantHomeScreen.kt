package com.shopmanager.app.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.Icon
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.AssistantCard
import com.shopmanager.app.assistant.AssistantChip
import com.shopmanager.app.assistant.AssistantMessage
import com.shopmanager.app.assistant.AssistantViewModel
import com.shopmanager.app.data.learning.HomeSuggestion
import com.shopmanager.app.data.learning.SuggestionKind
import com.shopmanager.app.data.materials.PriceHistoryStore
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppSheet
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.materials.MaterialsViewModel
import kotlinx.coroutines.delay

private const val REPLY_VISIBLE_MS = 30_000L

private fun autoHides(m: AssistantMessage): Boolean {
    val c = m.card
    return c == null || c is AssistantCard.Result || c is AssistantCard.Answer
}

@Composable
fun AssistantHomeScreen(
    assistant: AssistantViewModel,
    materialsViewModel: MaterialsViewModel,
    onOpenDrawer: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenChat: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToMaterials: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAdmin: () -> Unit = {}
) {
    val data by assistant.data.collectAsState()
    val mood by assistant.mood.collectAsState()
    val settings by assistant.settings.collectAsState()
    val log by assistant.actionLog.collectAsState()
    val messages by assistant.messages.collectAsState()
    val undoable by assistant.undoable.collectAsState()
    val suggestions by materialsViewModel.homeSuggestions.collectAsState()
    val history by materialsViewModel.priceHistory.collectAsState()
    val draft by assistant.draft.collectAsState()

    var input by remember { mutableStateOf("") }
    var showStats by remember { mutableStateOf(false) }
    LaunchedEffect(draft) { if (draft.isNotEmpty()) { input = draft; assistant.setDraft("") } }

    val initialLog = remember { log.size }
    var seenLog by remember { mutableIntStateOf(log.size) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(log.size) {
        if (log.size > seenLog && log.size > initialLog - 1) { flash = true; delay(900); flash = false }
        seenLog = log.size
    }
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    val borderColor by animateColorAsState(
        if (flash) Color.White else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = tween(if (low) 0 else 280), label = "flash"
    )

    val cur = data.currency
    val withDebt = data.persons.filter { it.amount > 0 }
    val debtTotal = withDebt.sumOf { it.amount }
    val shortages = data.materials
    val changedPrices = remember(history) {
        val since = System.currentTimeMillis() - PriceHistoryStore.RECENT_WINDOW_MS
        history.values.count { l -> l.any { it.at >= since } }
    }

    val lastReply = messages.lastOrNull()?.takeIf { !it.fromUser }
    val replyKey = lastReply?.id to lastReply?.time
    var expired by remember(replyKey) {
        mutableStateOf(lastReply != null && autoHides(lastReply) && System.currentTimeMillis() - lastReply.time >= REPLY_VISIBLE_MS)
    }
    LaunchedEffect(replyKey) {
        if (lastReply != null && autoHides(lastReply) && !expired) {
            val left = REPLY_VISIBLE_MS - (System.currentTimeMillis() - lastReply.time)
            if (left > 0) delay(left)
            expired = true
        }
    }
    val shownReply = lastReply?.takeIf { !expired && (it.text.isNotBlank() || it.card != null) }

    fun send(text: String) { val t = text.trim(); if (t.isNotEmpty()) { assistant.send(t); input = "" } }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = "إدارة المحل",
            navigation = { ScreenIconButton(Icons.Filled.Menu, "القائمة", onOpenDrawer) },
            actions = {
                ScreenIconButton(Icons.Filled.Notifications, "سجل النشاط", onOpenActivity)
                ScreenIconButton(Icons.Filled.Dashboard, "الإحصائيات", { showStats = true })
            },
            reservedIcons = 2
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AppScreenPadding, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenChat)) {
                RobotAvatar(mood, size = 190.dp, animate = settings.robotAnimation)
            }
            if (shownReply != null) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shownReply.text.isNotBlank()) Text(shownReply.text, style = MaterialTheme.typography.bodyMedium)
                        shownReply.card?.let { AssistantCardView(shownReply, it, assistant, undoable) }
                    }
                }
            }
        }
        UndoBarView(assistant)
        VoiceStatusArea(assistant) { input = it }
        InputBar(input, { input = it }, { send(input) }, Modifier.windowInsetsPadding(WindowInsets.navigationBars), leading = { MicButton(assistant) })
    }

    if (showStats) {
        AppSheet(title = "الإحصائيات", onDismiss = { showStats = false }) {
            BentoCard(Modifier.fillMaxWidth(), borderColor, { showStats = false; onNavigateToDebts() }) {
                Text("الديون", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${Formatters.number(debtTotal)} $cur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${Formatters.number(withDebt.size.toDouble())} أشخاص عليهم دين", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BentoCard(Modifier.weight(1f), borderColor, { showStats = false; onNavigateToMaterials() }) {
                    Text("النواقص", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.number(shortages.size.toDouble()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        shortages.sortedByDescending { it.updatedAt }.take(2).joinToString("، ") { it.name }.ifBlank { "ما في نواقص" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                    )
                }
                BentoCard(Modifier.weight(1f), borderColor, { showStats = false; onNavigateToMaterials() }) {
                    Text("الأسعار", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.number(changedPrices.toDouble()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("مواد تغيّر سعرها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val examples = listOf(
                AssistantChip("ملخص اليوم"), AssistantChip("شو النواقص"), AssistantChip("مجموع الديون"),
                AssistantChip("أكثر 5 مدينين"), AssistantChip("سجل دين", "سجل على ", false)
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.forEach { c ->
                    Surface(
                        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clip(CircleShape).clickable { showStats = false; onChip(c, assistant) { input = it } }
                    ) { Text(c.label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge) }
                }
            }
            if (settings.proactiveSuggestions) {
                suggestions.take(2).forEach { s -> SuggestionRow(s) { cmd -> showStats = false; send(cmd) } }
            }
            AppRowSurface(RoundedCornerShape(20.dp), onClick = { showStats = false; onOpenNotes() }) {
                Text("الملاحظات", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(AppGroupGap)) {
                AppRowSurface(groupedRowShape(0, 1), onClick = { showStats = false; onOpenSettings() }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, null, modifier = Modifier.size(22.dp))
                        Text("الإعدادات", Modifier.padding(start = 14.dp).weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                }
                AppRowSurface(groupedRowShape(1, 1), onClick = { showStats = false; onOpenAdmin() }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AdminPanelSettings, null, modifier = Modifier.size(22.dp))
                        Text("لوحة المسؤول", Modifier.padding(start = 14.dp).weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (log.isNotEmpty()) {
                Text("آخر ما نفّذه المساعد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                log.takeLast(4).reversed().forEach { e ->
                    AppRowSurface(RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            if (assistant.canUndoLog(e) && !e.text.startsWith("تراجع")) {
                                TextButton(onClick = { assistant.undoFromLog(e) }) { Text("تراجع") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoCard(modifier: Modifier, border: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun SuggestionRow(s: HomeSuggestion, onAsk: (String) -> Unit) {
    val names = s.names.take(3)
    val (title, command) = when (s.kind) {
        SuggestionKind.PRICES_REVIEW -> "حان وقت مراجعة الأسعار" to null
        SuggestionKind.USUAL_CHECK -> "تفقّد المعتاد: ${names.joinToString("، ")}" to null
        SuggestionKind.STALE_MATERIALS -> "مواد تأخرت عن عادتها: ${names.joinToString("، ")}" to null
        else -> "مواد تتكرر في النواقص: ${names.joinToString("، ")}" to "ضيف ${names.joinToString(" و")} للنواقص"
    }
    AppRowSurface(RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💡 $title", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (command != null && names.isNotEmpty()) {
                AppPillButton("أضفها", { onAsk(command) }, tonal = true, height = 36.dp)
            }
        }
    }
}
