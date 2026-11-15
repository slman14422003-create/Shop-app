package com.shopmanager.app.ui.dashboard

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.defaultMinSize
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppSectionGap
import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceLg
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import androidx.compose.foundation.Canvas
import com.shopmanager.app.data.learning.SuggestionKind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.AutoAwesome
import com.shopmanager.app.ui.common.AppSpace20
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.AppAmountHeroSize
import com.shopmanager.app.ui.common.AppAmountRowSize
import com.shopmanager.app.ui.common.AppBodySize
import com.shopmanager.app.ui.common.AppBorderWidth
import com.shopmanager.app.ui.common.AppCaptionLargeSize
import com.shopmanager.app.ui.common.AppCaptionSize
import com.shopmanager.app.ui.common.AppCardLargeRadius
import com.shopmanager.app.ui.common.AppCardRadius
import com.shopmanager.app.ui.common.AppOutlinedCard
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppTitleSize
import com.shopmanager.app.ui.common.PillBadge
import com.shopmanager.app.ui.common.appAccent
import com.shopmanager.app.ui.common.appAmountStyle
import com.shopmanager.app.ui.common.appBorderColor
import com.shopmanager.app.ui.common.appOnAccent
import com.shopmanager.app.ui.common.pillColorForQuantity
import com.shopmanager.app.ui.common.tabular
import com.shopmanager.app.ui.theme.LocalSemanticColors
import com.shopmanager.app.util.DateFormats
import java.util.Date
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.DebtActivityLog
import com.shopmanager.app.data.debts.OverdueRule
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.learning.HomeSuggestion
import com.shopmanager.app.ui.common.AppCard
import com.shopmanager.app.ui.common.AppGroupLargeRadius
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.avatarColorFor
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

internal const val DAY_MS = 86_400_000L

// ───────────────────────── data helpers (pure, run off the main thread) ─────────────────────────

internal data class OverdueEntry(
    val personId: String,
    val name: String,
    val phone: String,
    val amount: Double,
    val days: Int
)

internal data class DaySummary(
    val addedCount: Int = 0,
    val addedAmount: Double = 0.0,
    val paidCount: Int = 0,
    val paidAmount: Double = 0.0
) {
    val isEmpty: Boolean get() = addedCount == 0 && paidCount == 0
}

internal data class WeekTrend(
    val added: List<Double> = List(7) { 0.0 },
    val paid: List<Double> = List(7) { 0.0 }
) {
    val isEmpty: Boolean get() = added.all { it <= 0.0 } && paid.all { it <= 0.0 }
}

internal data class SearchHit(
    val kind: Int,
    val id: String,
    val title: String,
    val subtitle: String
) {
    companion object {
        const val PERSON = 0
        const val MATERIAL = 1
        const val NOTE = 2
    }
}

internal fun computeOverdue(
    persons: List<Person>,
    debts: List<Debt>,
    overdueDays: Int,
    now: Long
): List<OverdueEntry> =
    OverdueRule.compute(persons, debts, overdueDays, now).map {
        OverdueEntry(personId = it.personId, name = it.name, phone = it.phone, amount = it.amount, days = it.days)
    }

private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun summarizeToday(entries: List<DebtActivityLog.Entry>, now: Long): DaySummary {
    val start = startOfDay(now)
    var addedCount = 0
    var addedAmount = 0.0
    var paidCount = 0
    var paidAmount = 0.0
    for (e in entries) {
        if (e.time < start) continue
        when (e.kind) {
            DebtActivityLog.Kind.ADDED, DebtActivityLog.Kind.NEW_CUSTOMER -> {
                addedCount++
                addedAmount += abs(e.amount)
            }
            DebtActivityLog.Kind.PAID, DebtActivityLog.Kind.PAID_ALL -> {
                paidCount++
                paidAmount += abs(e.amount)
            }
            else -> Unit
        }
    }
    return DaySummary(addedCount, addedAmount, paidCount, paidAmount)
}

/** Daily totals for the last 7 days (oldest first, today last). */
internal fun computeWeekTrend(entries: List<DebtActivityLog.Entry>, now: Long): WeekTrend {
    val todayStart = startOfDay(now)
    val added = DoubleArray(7)
    val paid = DoubleArray(7)
    for (e in entries) {
        val daysAgo = if (e.time >= todayStart) 0 else ((todayStart - e.time - 1) / DAY_MS).toInt() + 1
        if (daysAgo > 6) continue
        val index = 6 - daysAgo
        when (e.kind) {
            DebtActivityLog.Kind.ADDED, DebtActivityLog.Kind.NEW_CUSTOMER -> added[index] += abs(e.amount)
            DebtActivityLog.Kind.PAID, DebtActivityLog.Kind.PAID_ALL -> paid[index] += abs(e.amount)
            else -> Unit
        }
    }
    return WeekTrend(added.toList(), paid.toList())
}

// ───────────────────────── shared card pieces ─────────────────────────

/** Compact status/alert card in the same visual language as the smart suggestion card. */
@Composable
internal fun HomeAlertCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(AppGroupLargeRadius)
    AppCard(
        modifier = modifier
            .padding(horizontal = AppScreenPadding)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = AppScreenPadding, end = AppSpaceSm, top = AppSpaceMd, bottom = AppSpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AppSpaceMd))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (actionLabel != null) {
                    Spacer(Modifier.height(AppSpaceXs))
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "إخفاء",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ───────────────────────── suggestion stack ─────────────────────────

/** Swipeable stack of every strong learned suggestion, with page dots. */
@Composable
internal fun SuggestionPager(
    suggestions: List<HomeSuggestion>,
    accent: Color,
    onAccept: (HomeSuggestion) -> Unit,
    onDismiss: (HomeSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { suggestions.size }
    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            key = { page -> suggestions.getOrNull(page)?.kind?.key ?: page.toString() },
            verticalAlignment = Alignment.Top
        ) { page ->
            val s = suggestions.getOrNull(page)
            if (s != null) {
                HomeSuggestionCard(
                    suggestion = s,
                    accent = accent,
                    onAccept = { onAccept(s) },
                    onDismiss = { onDismiss(s) }
                )
            }
        }
        if (suggestions.size > 1) {
            Spacer(Modifier.height(AppSpaceSm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(suggestions.size) { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

// ───────────────────────── overdue debts ─────────────────────────

@Composable
internal fun OverdueDebtsCard(
    entries: List<OverdueEntry>,
    accent: Color,
    nf: NumberFormat,
    currency: String,
    onOpenPerson: (String) -> Unit,
    onRemind: (OverdueEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        title = "ديون متأخرة",
        color = accent,
        icon = Icons.Default.Warning
    ) {
        val shown = entries.take(3)
        shown.forEachIndexed { index, e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPerson(e.personId) }
                    .defaultMinSize(minHeight = AppMinTouchTarget)
                    .padding(vertical = AppSpace10),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(avatarColorFor(e.name)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        e.name.trim().take(1),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(AppSpaceMd))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "متأخر منذ ${nf.format(e.days)} يوم • ${nf.format(e.amount)} $currency",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (e.phone.isNotBlank()) {
                    Text(
                        "تذكير",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onRemind(e) }
                            .defaultMinSize(minWidth = AppMinTouchTarget, minHeight = AppMinTouchTarget)
                            .wrapContentSize(Alignment.Center)
                            .padding(horizontal = AppSpaceMd)
                    )
                }
            }
            if (index < shown.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        if (entries.size > shown.size) {
            Text(
                "و ${nf.format(entries.size - shown.size)} آخرين",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AppSpace10)
            )
        }
    }
}

// ───────────────────────── today summary ─────────────────────────

@Composable
internal fun TodaySummaryCard(
    summary: DaySummary,
    nf: NumberFormat,
    currency: String,
    successColor: Color,
    addedColor: Color,
    modifier: Modifier = Modifier
) {
    SectionCard(title = "ملخص اليوم", icon = Icons.Default.Today, modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = AppSpace10)) {
            SummaryStat(
                modifier = Modifier.weight(1f),
                label = "مدفوعات مستلمة",
                value = "${nf.format(summary.paidAmount)} $currency",
                count = summary.paidCount,
                nf = nf,
                color = successColor
            )
            Spacer(Modifier.width(AppSpaceMd))
            SummaryStat(
                modifier = Modifier.weight(1f),
                label = "ديون مضافة",
                value = "${nf.format(summary.addedAmount)} $currency",
                count = summary.addedCount,
                nf = nf,
                color = addedColor
            )
        }
    }
}

@Composable
private fun SummaryStat(
    modifier: Modifier,
    label: String,
    value: String,
    count: Int,
    nf: NumberFormat,
    color: Color
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpace2))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${nf.format(count)} عملية",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ───────────────────────── notes & reminders ─────────────────────────

internal data class NoteRow(val id: String, val title: String, val whenLabel: String?)

@Composable
internal fun NotesRemindersCard(
    rows: List<NoteRow>,
    accent: Color,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(title = "ملاحظات وتذكيرات", color = accent, icon = Icons.Default.StickyNote2, modifier = modifier) {
        rows.forEachIndexed { index, row ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNotes)
                    .defaultMinSize(minHeight = AppMinTouchTarget)
                    .padding(vertical = AppSpace10)
            ) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.whenLabel != null) {
                    Text(
                        row.whenLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = accent
                    )
                }
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ───────────────────────── weekly trend ─────────────────────────

@Composable
internal fun WeekTrendCard(
    trend: WeekTrend,
    addedColor: Color,
    paidColor: Color,
    modifier: Modifier = Modifier
) {
    SectionCard(title = "حركة الأسبوع", icon = Icons.Default.TrendingUp, modifier = modifier) {
        val maxValue = (trend.added + trend.paid).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        Box(Modifier.fillMaxWidth().padding(vertical = AppSectionGap)) {
            Sparkline(trend.added, addedColor, maxValue, Modifier.fillMaxWidth().height(64.dp))
            Sparkline(trend.paid, paidColor, maxValue, Modifier.fillMaxWidth().height(64.dp))
        }
        Row(Modifier.padding(bottom = AppSpaceMd), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(addedColor)
            Spacer(Modifier.width(AppSpace6))
            Text("ديون مضافة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(AppSpaceLg))
            LegendDot(paidColor)
            Spacer(Modifier.width(AppSpace6))
            Text("مدفوعات", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun Sparkline(values: List<Double>, color: Color, maxValue: Double, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2 || maxValue <= 0.0) return@Canvas
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = size.height - (v / maxValue).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ───────────────────────── home search ─────────────────────────

@Composable
internal fun HomeSearchResults(
    hits: List<SearchHit>,
    onHit: (SearchHit) -> Unit
) {
    AppCard(modifier = Modifier.padding(horizontal = AppScreenPadding)) {
        Column(Modifier.padding(horizontal = AppSpaceLg, vertical = AppSpace6)) {
            if (hits.isEmpty()) {
                Text(
                    "لا توجد نتائج",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = AppSectionGap)
                )
            }
            hits.forEachIndexed { index, hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onHit(hit) }
                        .defaultMinSize(minHeight = AppMinTouchTarget)
                        .padding(vertical = AppSpace10),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (hit.kind) {
                        SearchHit.PERSON -> Icons.Default.Person
                        SearchHit.MATERIAL -> Icons.Default.Inventory2
                        else -> Icons.Default.StickyNote2
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(AppSpaceMd))
                    Column(Modifier.weight(1f)) {
                        Text(
                            hit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hit.subtitle.isNotBlank()) {
                            Text(
                                hit.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (index < hits.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}


internal data class FollowUpEntry(
    val personId: String,
    val name: String,
    val phone: String,
    val amount: Double,
    val days: Int,
    val overdue: Boolean
)

internal fun computeFollowUps(
    persons: List<Person>,
    debts: List<Debt>,
    overdueDays: Int,
    now: Long
): List<FollowUpEntry> {
    val overdue = computeOverdue(persons, debts, overdueDays, now)
    val overdueIds = overdue.mapTo(HashSet()) { it.personId }
    val today = OverdueRule.startOfDay(now)
    val dueToday = persons
        .filter { it.amount > 0.0 && it.dueDate > 0L && it.id !in overdueIds && OverdueRule.startOfDay(it.dueDate) == today }
        .sortedByDescending { it.amount }
    return overdue.map { FollowUpEntry(it.personId, it.name, it.phone, it.amount, it.days, true) } +
        dueToday.map { FollowUpEntry(it.id, it.name, it.phone, it.amount, 0, false) }
}

@Composable
internal fun HomeSyncChip(
    label: String,
    problem: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val tint = if (problem) semantic.danger else semantic.success
    Box(
        modifier
            .heightIn(min = AppMinTouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (problem) Icons.Default.CloudOff else Icons.Default.CloudDone,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(AppSpace6))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = AppCaptionSize),
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
    }
}

@Composable
internal fun HomeHeroCard(
    totalAmount: Double,
    totalPersons: Int,
    overdueAmount: Double,
    paidThisWeek: Double,
    loading: Boolean,
    nf: NumberFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val currency = AppSettingsState.currencySymbol
    val cs = MaterialTheme.colorScheme
    AppOutlinedCard(
        modifier = modifier.padding(horizontal = AppScreenPadding),
        shape = RoundedCornerShape(AppCardLargeRadius),
        onClick = onClick
    ) {
        Column(Modifier.padding(AppSpace20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "إجمالي الديون",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = AppBodySize),
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant
                )
                Surface(shape = CircleShape, color = cs.onSurface.copy(alpha = 0.08f)) {
                    Text(
                        "$totalPersons عميل",
                        modifier = Modifier.padding(horizontal = AppSpaceMd, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = AppCaptionLargeSize),
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                }
            }
            Spacer(Modifier.height(AppSpace6))
            AnimatedCounterText(
                targetValue = totalAmount,
                format = { "${nf.format(it)} $currency" },
                style = appAmountStyle(AppAmountHeroSize),
                animate = !loading
            )
            Spacer(Modifier.height(AppSpaceLg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpace10)) {
                HeroFigure(Modifier.weight(1f), "متأخر", "${nf.format(overdueAmount)} $currency", semantic.danger)
                HeroFigure(Modifier.weight(1f), "مسدّد هالأسبوع", "${nf.format(paidThisWeek)} $currency", semantic.success)
            }
        }
    }
}

@Composable
private fun HeroFigure(modifier: Modifier, label: String, value: String, color: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = AppSpaceMd, vertical = AppSpaceMd)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = AppCaptionSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(AppSpace2))
        Text(
            value,
            style = appAmountStyle(AppAmountRowSize),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun HomeActionRow(
    onNewDebt: () -> Unit,
    onPay: () -> Unit,
    onNewShortage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = AppScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(AppSpace10)
    ) {
        HomeActionTile(Modifier.weight(1f), Icons.Default.Add, "دين جديد", appAccent(), onNewDebt)
        HomeActionTile(Modifier.weight(1f), Icons.Default.Check, "تسديد", semantic.success, onPay)
        HomeActionTile(Modifier.weight(1f), Icons.Default.ShoppingCart, "نقص مادة", semantic.warning, onNewShortage)
    }
}

@Composable
private fun HomeActionTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    AppOutlinedCard(modifier = modifier, onClick = onClick) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .padding(vertical = AppSpaceMd, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(AppSpace6))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = AppBodySize),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun FollowUpSection(
    entries: List<FollowUpEntry>,
    nf: NumberFormat,
    onOpenPerson: (String) -> Unit,
    onRemind: (FollowUpEntry) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val currency = AppSettingsState.currencySymbol
    val shown = entries.take(5)
    Column(modifier.fillMaxWidth().padding(horizontal = AppScreenPadding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpaceXs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "يحتاج متابعة اليوم",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = AppTitleSize),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entries.size > shown.size) {
                Box(
                    Modifier
                        .heightIn(min = AppMinTouchTarget)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onShowAll)
                        .padding(horizontal = AppSpaceMd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "عرض الكل",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = AppCaptionLargeSize),
                        fontWeight = FontWeight.Bold,
                        color = appAccent()
                    )
                }
            }
        }
        Spacer(Modifier.height(AppSpaceXs))
        AppOutlinedCard {
            if (shown.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(AppSpaceLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = semantic.success, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(AppSpaceMd))
                    Text(
                        "ما في شي يحتاج متابعة اليوم",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = AppBodySize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column {
                    shown.forEachIndexed { index, e ->
                        val tint = if (e.overdue) semantic.danger else semantic.warning
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPerson(e.personId) }
                                .heightIn(min = 72.dp)
                                .padding(start = AppSpaceLg, end = AppSpaceXs, top = AppSpaceSm, bottom = AppSpaceSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(avatarColorFor(e.name)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    e.name.trim().take(1),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(AppSpaceMd))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (e.overdue) "متأخر منذ ${nf.format(e.days)} يوم" else "يستحق اليوم",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = AppCaptionLargeSize),
                                    color = tint,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.width(AppSpaceSm))
                            Text(
                                "${nf.format(e.amount)} $currency",
                                style = appAmountStyle(AppAmountRowSize),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (e.phone.isNotBlank()) {
                                IconButton(onClick = { onRemind(e) }, modifier = Modifier.size(AppMinTouchTarget)) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = "تذكير",
                                        tint = appAccent(),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(AppSpaceMd))
                            }
                        }
                        if (index < shown.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShortageStrip(
    count: Int,
    names: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    AppOutlinedCard(modifier = modifier.padding(horizontal = AppScreenPadding), onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = AppSpaceLg, vertical = AppSpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(semantic.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = semantic.warning, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AppSpaceMd))
            Column(Modifier.weight(1f)) {
                Text(
                    "قائمة مشتريات السوق",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = AppBodySize),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (loading) "" else if (count > 0) names else "لا يوجد",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = AppCaptionLargeSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(AppSpaceSm))
            if (count > 0) {
                PillBadge(text = "$count", color = semantic.warning)
                Spacer(Modifier.width(AppSpaceXs))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

internal data class ActivityRow(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val timestamp: Long
)

@Composable
internal fun RecentActivityCard(
    rows: List<ActivityRow>,
    modifier: Modifier = Modifier
) {
    val df = remember { DateFormats.get("d MMM، h:mm a", Locale("ar")) }
    SectionCard(title = "آخر النشاطات", icon = Icons.Default.History, modifier = modifier) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = AppSpace6),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(32.dp).clip(MaterialTheme.shapes.small).background(row.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(row.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(AppSpace10))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        row.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (row.timestamp > 0) {
                    Text(
                        df.format(Date(row.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
internal fun TopDebtorsCard(
    topDebtors: List<Person>,
    nf: NumberFormat,
    modifier: Modifier = Modifier
) {
    SectionCard(title = "أكبر الديون", icon = Icons.Default.Groups, modifier = modifier) {
        val maxAmount = topDebtors.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
        topDebtors.forEachIndexed { index, p ->
            val barColor = avatarColorFor(p.name)
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(barColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            p.name.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(AppSpace10))
                    Text(
                        p.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${nf.format(p.amount)} ${AppSettingsState.currencySymbol}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((p.amount / maxAmount).toFloat().coerceIn(0.04f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
            }
            if (index != topDebtors.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}

@Composable
internal fun BackupStatusCard(
    lastBackupAt: Long?,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val body = if (lastBackupAt == null) "لا توجد نسخة احتياطية بعد"
    else "آخر نسخة قبل ${((System.currentTimeMillis() - lastBackupAt) / DAY_MS).toInt()} أيام"
    HomeAlertCard(
        icon = Icons.Default.Backup,
        accent = LocalSemanticColors.current.warning,
        title = "النسخ الاحتياطي",
        body = body,
        actionLabel = "فتح الإعدادات",
        onClick = onOpenSettings,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
internal fun SectionCard(
    title: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().padding(horizontal = AppScreenPadding)) {
        Row(
            Modifier.padding(horizontal = AppSpaceSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpaceSm))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(AppSpaceSm))
        AppCard {
            Column(Modifier.padding(horizontal = AppSpaceLg, vertical = AppSpaceSm), content = content)
        }
    }
}

@Composable
internal fun HomeSuggestionCard(
    suggestion: HomeSuggestion,
    accent: Color,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (suggestion.kind) {
        SuggestionKind.DUE_MATERIALS -> "\u0645\u0648\u0627\u062F \u0642\u062F \u062A\u062D\u062A\u0627\u062C\u0647\u0627"
        SuggestionKind.USUAL_CHECK -> "\u0648\u0642\u062A \u0641\u062D\u0635 \u0627\u0644\u0646\u0648\u0627\u0642\u0635"
        SuggestionKind.PRICES_REVIEW -> "\u0645\u0631\u0627\u062C\u0639\u0629 \u0627\u0644\u0623\u0633\u0639\u0627\u0631"
        SuggestionKind.STALE_MATERIALS -> "مواد غابت من فترة"
        SuggestionKind.FREQUENT_MATERIALS -> "الأكثر طلباً هذا الشهر"
        SuggestionKind.WITH_PENDING -> "غالباً بتنقص مع نواقصك"
        SuggestionKind.WEEKDAY_MATERIALS -> {
            val todayName = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")[
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
            ]
            "عادةً بتنقص يوم $todayName"
        }
    }
    val body = when (suggestion.kind) {
        SuggestionKind.DUE_MATERIALS -> suggestion.names.joinToString("\u060C ")
        SuggestionKind.USUAL_CHECK -> "\u0639\u0627\u062F\u0629 \u062A\u0631\u0627\u062C\u0639 \u0627\u0644\u0646\u0648\u0627\u0642\u0635 \u0641\u064A \u0647\u0630\u0627 \u0627\u0644\u0648\u0642\u062A"
        SuggestionKind.PRICES_REVIEW -> "\u062A\u0631\u0627\u062C\u0639 \u0627\u0644\u0623\u0633\u0639\u0627\u0631 \u0639\u0627\u062F\u0629 \u0641\u064A \u0647\u0630\u0627 \u0627\u0644\u0648\u0642\u062A"
        SuggestionKind.STALE_MATERIALS, SuggestionKind.FREQUENT_MATERIALS, SuggestionKind.WITH_PENDING,
        SuggestionKind.WEEKDAY_MATERIALS ->
            suggestion.names.joinToString("، ")
    }
    val action = when (suggestion.kind) {
        SuggestionKind.PRICES_REVIEW -> "\u0627\u0641\u062A\u062D \u0627\u0644\u0645\u0648\u0627\u062F"
        else -> "\u0627\u0641\u062D\u0635 \u0627\u0644\u0646\u0648\u0627\u0642\u0635"
    }
    val shape = RoundedCornerShape(AppGroupLargeRadius)
    AppCard(
        modifier = modifier
            .padding(horizontal = AppScreenPadding)
            .clip(shape)
            .clickable(onClick = onAccept)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = AppScreenPadding, end = AppSpaceXs, top = AppSpaceMd, bottom = AppSpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                val tw = com.shopmanager.app.ui.common.twinkleScale()
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp).graphicsLayer { scaleX = tw; scaleY = tw })
            }
            Spacer(Modifier.width(AppSpaceMd))
            Column(Modifier.weight(1f)) {
                Text(
                    "\u0627\u0642\u062A\u0631\u0627\u062D \u0630\u0643\u064A",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(AppSpaceXs))
                Text(
                    action,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(AppMinTouchTarget)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "\u0625\u062E\u0641\u0627\u0621",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}