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
                    AnimatedAmount(
                        amount = p.amount,
                        suffix = " ${AppSettingsState.currencySymbol}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        format = { nf.format(it) }
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
