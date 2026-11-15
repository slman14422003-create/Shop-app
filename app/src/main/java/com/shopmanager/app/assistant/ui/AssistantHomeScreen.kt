package com.shopmanager.app.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.shopmanager.app.ui.common.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.assistant.AssistantCard
import com.shopmanager.app.assistant.AssistantMessage
import com.shopmanager.app.assistant.AssistantViewModel
import com.shopmanager.app.data.learning.HomeSuggestion
import com.shopmanager.app.data.learning.SuggestionKind
import com.shopmanager.app.data.materials.PriceHistoryStore
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.materials.MaterialsViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

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
    onOpenChat: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToMaterials: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onNewDebt: () -> Unit,
    onNewMaterial: () -> Unit
) {
    val data by assistant.data.collectAsStateWithLifecycle()
    val settings by assistant.settings.collectAsStateWithLifecycle()
    val log by assistant.actionLog.collectAsStateWithLifecycle()
    val messages by assistant.messages.collectAsStateWithLifecycle()
    val undoable by assistant.undoable.collectAsStateWithLifecycle()
    val suggestions by materialsViewModel.homeSuggestions.collectAsStateWithLifecycle()
    val history by materialsViewModel.priceHistory.collectAsStateWithLifecycle()
    val draft by assistant.draft.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
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
    val debtTotalState = com.shopmanager.app.ui.common.rememberComputed(withDebt) { withDebt.sumOf { it.amount } }
    val debtTotal = debtTotalState.value ?: 0.0
    val shortages = data.materials
    val changedPricesState = com.shopmanager.app.ui.common.rememberComputed(history) {
        val since = System.currentTimeMillis() - PriceHistoryStore.RECENT_WINDOW_MS
        history.values.count { l -> l.any { it.at >= since } }
    }
    val changedPrices = changedPricesState.value ?: 0

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
    val greeting = remember { homeGreeting() }
    val top = remember(withDebt) { withDebt.sortedByDescending { it.amount }.take(3) }

    fun send(text: String) { val t = text.trim(); if (t.isNotEmpty()) { assistant.send(t); input = "" } }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = "إدارة المحل",
            navigation = { ScreenIconButton(Icons.Filled.Menu, "القائمة", onOpenDrawer) }
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AppScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (shownReply != null) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (shownReply.text.isNotBlank()) Text(shownReply.text, style = MaterialTheme.typography.bodyMedium)
                        shownReply.card?.let { AssistantCardView(shownReply, it, assistant, undoable) }
                    }
                }
            }

            Staggered(0) {
                Surface(
                    onClick = onNavigateToDebts,
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.5.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(greeting, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("إجمالي الديون", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom) {
                                AnimatedCounterText(debtTotal, { Formatters.number(it) }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, animate = debtTotalState.value != null)
                                Text(" $cur", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "${Formatters.number(withDebt.size.toDouble())} عميل",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Staggered(1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeAction("دين جديد", Icons.Filled.Add, Modifier.weight(1f), true, onNewDebt)
                    HomeAction("مادة جديدة", Icons.Filled.Inventory2, Modifier.weight(1f), false, onNewMaterial)
                }
            }

            Staggered(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Modifier.weight(1f), borderColor, onNavigateToMaterials) {
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
                    StatCard(Modifier.weight(1f), borderColor, onNavigateToMaterials) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Sell, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("  الأسعار", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedCounterText(changedPrices.toDouble(), { Formatters.number(it) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, animate = changedPricesState.value != null)
                        Text("مواد تغيّر سعرها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            if (top.isNotEmpty()) Staggered(3) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppGroupGap)) {
                    Text("أكبر الديون", Modifier.padding(horizontal = 4.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    top.forEachIndexed { i, p ->
                        AppRowSurface(groupedRowShape(i, top.lastIndex), onClick = { onOpenPerson(p.id) }) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(avatarColorFor(p.name)), contentAlignment = Alignment.Center) {
                                    Text(p.name.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                                Text(p.name, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${Formatters.number(p.amount)} $cur", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Staggered(4) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppGroupGap)) {
                    LinkRow(Icons.Filled.Description, "الملاحظات", groupedRowShape(0, 1), onOpenNotes)
                    LinkRow(Icons.Filled.Forum, "محادثة المساعد", groupedRowShape(1, 1), onOpenChat)
                }
            }

            if (settings.proactiveSuggestions) {
                suggestions.take(1).forEach { s -> Staggered(5) { SuggestionRow(s) { cmd -> send(cmd) } } }
            }
            Spacer(Modifier.height(4.dp))
        }
        UndoBarView(assistant)
        InputBar(input, { input = it }, { send(input) }, Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun StatCard(modifier: Modifier, border: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
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
private fun LinkRow(icon: ImageVector, label: String, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    AppRowSurface(shape, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleSmall)
        }
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
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (command != null && names.isNotEmpty()) {
                AppPillButton("أضفها", { onAsk(command) }, tonal = true, height = 36.dp)
            }
        }
    }
}

private var homeEntrancePlayed = false

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

@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    val low = LocalPerformanceTier.current == PerformanceTier.LOW
    val skip = low || homeEntrancePlayed
    val p = remember { Animatable(if (skip) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!skip) {
            delay(index * 50L)
            p.animateTo(1f, tween(380, easing = MotionSpecs.claudeEasing))
            homeEntrancePlayed = true
        }
    }
    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 28f }) { content() }
}

@Composable
private fun HomeAction(label: String, icon: ImageVector, modifier: Modifier, primary: Boolean, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        color = bg
    ) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = fg)
        }
    }
}
