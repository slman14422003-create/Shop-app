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
import com.shopmanager.app.ui.common.AnimatedAmount
import com.shopmanager.app.ui.common.pressScale
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
import com.shopmanager.app.ui.common.Text
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
            AnimatedAmount(
                amount = if (loading) 0.0 else totalAmount,
                suffix = " $currency",
                style = appAmountStyle(AppAmountHeroSize),
                format = { nf.format(it) }
            )
            Spacer(Modifier.height(AppSpaceLg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpace10)) {
                HeroFigure(Modifier.weight(1f), "متأخر", overdueAmount, " $currency", nf, semantic.danger)
                HeroFigure(Modifier.weight(1f), "مسدّد هالأسبوع", paidThisWeek, " $currency", nf, semantic.success)
            }
        }
    }
}

@Composable
internal fun HeroFigure(modifier: Modifier, label: String, amount: Double, suffix: String, nf: NumberFormat, color: Color) {
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
        AnimatedAmount(
            amount = amount,
            suffix = suffix,
            style = appAmountStyle(AppAmountRowSize),
            color = color,
            format = { nf.format(it) }
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
internal fun HomeActionTile(
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
