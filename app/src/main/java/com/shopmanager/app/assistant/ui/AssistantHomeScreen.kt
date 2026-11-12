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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.AssistantChip
import com.shopmanager.app.assistant.AssistantViewModel
import com.shopmanager.app.data.learning.HomeSuggestion
import com.shopmanager.app.data.learning.SuggestionKind
import com.shopmanager.app.data.materials.PriceHistoryStore
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.materials.MaterialsViewModel
import kotlinx.coroutines.delay

/**
 * الشاشة الرئيسية الجديدة: الروبوت في الوسط، بطاقات بنتو لحظية (ديون/نواقص/أسعار)، أمثلة سريعة، اقتراحات استباقية،
 * سجل آخر ما نفّذه المساعد مع تراجع، وحقل كتابة أسفل الشاشة. الرئيسية الكلاسيكية تبقى متاحة من إعدادات المساعد.
 */
@Composable
fun AssistantHomeScreen(
    assistant: AssistantViewModel,
    materialsViewModel: MaterialsViewModel,
    onOpenDrawer: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenChat: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToMaterials: () -> Unit,
    onOpenNotes: () -> Unit
) {
    val data by assistant.data.collectAsState()
    val mood by assistant.mood.collectAsState()
    val thinking by assistant.thinking.collectAsState()
    val status by assistant.lastStatus.collectAsState()
    val settings by assistant.settings.collectAsState()
    val log by assistant.actionLog.collectAsState()
    val messages by assistant.messages.collectAsState()
    val undoable by assistant.undoable.collectAsState()
    val suggestions by materialsViewModel.homeSuggestions.collectAsState()
    val history by materialsViewModel.priceHistory.collectAsState()
    val draft by assistant.draft.collectAsState()

    var input by remember { mutableStateOf("") }
    LaunchedEffect(draft) { if (draft.isNotEmpty()) { input = draft; assistant.setDraft("") } }

    // لمعان الحدود بالأبيض لحظة تنفيذ المساعد لعملية.
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

    fun send(text: String) { val t = text.trim(); if (t.isNotEmpty()) { assistant.send(t); input = "" } }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = "إدارة المحل",
            navigation = { ScreenIconButton(Icons.Filled.Menu, "القائمة", onOpenDrawer) },
            actions = { ScreenIconButton(Icons.Filled.Notifications, "سجل النشاط", onOpenActivity) }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = AppScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "robot") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenChat)) {
                        RobotAvatar(mood, size = 150.dp, animate = settings.robotAnimation)
                    }
                    Text(
                        if (thinking) "أفكر…" else "أهلاً! شو بدك أعمل؟",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    TextButton(onClick = onOpenChat) { Text("المحادثة الكاملة") }
                }
            }
            if (lastReply != null) {
                item(key = "last") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (lastReply.text.isNotBlank()) Text(lastReply.text, style = MaterialTheme.typography.bodyMedium)
                            lastReply.card?.let { AssistantCardView(lastReply, it, assistant, undoable) }
                        }
                    }
                }
            }
            item(key = "bento") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BentoCard(Modifier.fillMaxWidth(), borderColor, onNavigateToDebts) {
                        Text("الديون", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Formatters.number(debtTotal)} $cur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${Formatters.number(withDebt.size.toDouble())} أشخاص عليهم دين", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BentoCard(Modifier.weight(1f), borderColor, onNavigateToMaterials) {
                            Text("النواقص", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.number(shortages.size.toDouble()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                shortages.sortedByDescending { it.updatedAt }.take(2).joinToString("، ") { it.name }.ifBlank { "ما في نواقص" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                        BentoCard(Modifier.weight(1f), borderColor, onNavigateToMaterials) {
                            Text("الأسعار", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.number(changedPrices.toDouble()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("مواد تغيّر سعرها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item(key = "chips") {
                val examples = listOf(
                    AssistantChip("ملخص اليوم"), AssistantChip("شو النواقص"), AssistantChip("مجموع الديون"),
                    AssistantChip("أكثر 5 مدينين"), AssistantChip("سجل دين", "سجل على ", false)
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEach { c ->
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clip(CircleShape).clickable { onChip(c, assistant) { input = it } }
                        ) { Text(c.label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            if (settings.proactiveSuggestions && suggestions.isNotEmpty()) {
                items(suggestions.take(2), key = { "s_" + it.kind.key }) { s ->
                    SuggestionRow(s) { cmd -> send(cmd) }
                }
            }
            item(key = "notes") {
                AppRowSurface(RoundedCornerShape(20.dp), onClick = onOpenNotes) {
                    Text("الملاحظات", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (log.isNotEmpty()) {
                item(key = "logTitle") { Text("آخر ما نفّذه المساعد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                items(log.takeLast(4).reversed(), key = { "l_" + it.id }) { e ->
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
        InputBar(input, { input = it }, { send(input) }, Modifier.padding(bottom = LocalFloatingBottomNavHeight.current))
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
