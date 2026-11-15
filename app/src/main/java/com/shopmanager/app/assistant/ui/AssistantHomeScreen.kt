package com.shopmanager.app.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.avatarColorFor
import java.util.Calendar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    onOpenAdmin: () -> Unit = {},
    onNewDebt: () -> Unit = {},
    onPayDebt: () -> Unit = {}
) {
    val data by assistant.data.collectAsStateWithLifecycle()
    val mood by assistant.mood.collectAsStateWithLifecycle()
    val voiceLevel = rememberVoiceLevelState(assistant)
    val settings by assistant.settings.collectAsStateWithLifecycle()
    val log by assistant.actionLog.collectAsStateWithLifecycle()
    val messages by assistant.messages.collectAsStateWithLifecycle()
    val undoable by assistant.undoable.collectAsStateWithLifecycle()
    val suggestions by materialsViewModel.homeSuggestions.collectAsStateWithLifecycle()
    val history by materialsViewModel.priceHistory.collectAsStateWithLifecycle()
    val draft by assistant.draft.collectAsStateWithLifecycle()

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
    val withDebt = remember(data.persons) { data.persons.filter { it.amount > 0 } }
    val debtTotal = remember(withDebt) { withDebt.sumOf { it.amount } }
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
        var pokes by remember { mutableIntStateOf(0) }
        val greeting = remember(lastReply?.id) { homeGreeting() }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AppScreenPadding, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- بطاقة الهيرو: الروبوت + التحية/الرد + إجمالي الديون ----
            Staggered(0) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.5.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pokes++; onOpenChat() }
                        ) {
                            RobotAvatar(mood, size = 150.dp, animate = settings.robotAnimation, levelState = voiceLevel, pokeKey = pokes)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (shownReply != null) {
                            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (shownReply.text.isNotBlank()) Text(shownReply.text, style = MaterialTheme.typography.bodyMedium)
                                    shownReply.card?.let { AssistantCardView(shownReply, it, assistant, undoable) }
                                }
                            }
                        } else {
                            Text(greeting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Text("اكتب أو قُل أمرك وأنا أنفّذه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            onClick = onNavigateToDebts,
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text("إجمالي الديون", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        AnimatedCounterText(debtTotal, { Formatters.number(it) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                        Text(" $cur", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    "${Formatters.number(withDebt.size.toDouble())} عميل",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ---- إجراءات سريعة ----
            Staggered(1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeAction("دين جديد", Icons.Filled.Add, Modifier.weight(1f), onNewDebt)
                    HomeAction("تسديد", Icons.Filled.Payments, Modifier.weight(1f), onPayDebt)
                }
            }

            // ---- إحصائيات النواقص والأسعار ----
            Staggered(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BentoCard(Modifier.weight(1f), borderColor, onNavigateToMaterials) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Inventory2, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("  النواقص", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedCounterText(shortages.size.toDouble(), { Formatters.number(it) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            shortages.sortedByDescending { it.updatedAt }.take(2).joinToString("، ") { it.name }.ifBlank { "ما في نواقص" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    BentoCard(Modifier.weight(1f), borderColor, onNavigateToMaterials) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Sell, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("  الأسعار", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedCounterText(changedPrices.toDouble(), { Formatters.number(it) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("مواد تغيّر سعرها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            // ---- أكثر المدينين (شريط قابل للتمرير) ----
            val top = remember(withDebt) { withDebt.sortedByDescending { it.amount }.take(8) }
            if (top.isNotEmpty()) Staggered(3) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أكثر المدينين", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        top.forEach { p ->
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = onNavigateToDebts) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(avatarColorFor(p.name)), contentAlignment = Alignment.Center) {
                                        Text(p.name.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    }
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                        Text("${Formatters.number(p.amount)} $cur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (settings.proactiveSuggestions) {
                suggestions.take(1).forEach { s -> Staggered(4) { SuggestionRow(s) { cmd -> send(cmd) } } }
            }
            Spacer(Modifier.height(4.dp))
        }
        UndoBarView(assistant)
        VoiceStatusArea(assistant) { input = it }
        InputBar(input, { input = it }, { send(input) }, Modifier.windowInsetsPadding(WindowInsets.navigationBars), leading = { MicButton(assistant) })
    }

    if (showStats) {
        AppSheet(title = "المزيد", onDismiss = { showStats = false }) {
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


private fun homeGreeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "سهرانين لهلق؟"
        h < 12 -> "صباح الخير"
        h < 17 -> "أهلاً بك"
        h < 21 -> "مساء الخير"
        else -> "مساء النور"
    }
}

/** ظهور متدرّج (تلاشٍ + انزلاق) لكل قسم؛ بلا حركة على الأجهزة الضعيفة. */
@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    val p = remember { Animatable(if (low) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!low) { delay(index * 70L); p.animateTo(1f, tween(480, easing = MotionSpecs.claudeEasing)) }
    }
    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 40f }) { content() }
}

@Composable
private fun HomeAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
