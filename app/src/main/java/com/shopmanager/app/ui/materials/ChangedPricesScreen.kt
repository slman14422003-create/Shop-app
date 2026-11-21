package com.shopmanager.app.ui.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.PriceChange
import com.shopmanager.app.data.materials.PriceHistoryStore
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.Text
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlin.math.abs

internal data class ChangedPriceItem(
    val name: String,
    val oldPrice: Double,
    val newPrice: Double,
    val percent: Double,
    val at: Long
) {
    val delta: Double get() = newPrice - oldPrice
    val sellPrice: Double get() = newPrice * (1.0 + percent / 100.0)
}

/** مواد تغيّر سعرها خلال آخر 7 أيام ولها نسبة ربح محفوظة، الأحدث أولاً. */
internal fun buildChangedPriceItems(
    history: Map<String, List<PriceChange>>,
    percents: Map<String, Double>,
    now: Long
): List<ChangedPriceItem> =
    history.mapNotNull { (name, list) ->
        val key = name.trim()
        val last = list.firstOrNull()
        val percent = percents[key]
        if (percent == null || !PriceHistoryStore.isRecent(last, now)) null
        else ChangedPriceItem(key, last!!.oldPrice ?: return@mapNotNull null, last.newPrice, percent, last.at)
    }.sortedByDescending { it.at }

@Composable
internal fun ChangedPricesOverlay(
    history: Map<String, List<PriceChange>>,
    percents: Map<String, Double>,
    onBack: () -> Unit,
    onShare: (List<ChangedPriceItem>) -> Unit
) {
    val items = remember(history, percents) { buildChangedPriceItems(history, percents, System.currentTimeMillis()) }
    val currency = AppSettingsState.currencySymbol
    val cs = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val bottomClearance = LocalFloatingBottomNavHeight.current + 32.dp
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = "الأسعار المتغيّرة",
                reservedIcons = 1,
                navigation = { ScreenIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack) },
                actions = { ScreenIconButton(Icons.Rounded.Share, "مشاركة", { onShare(items) }) }
            )
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(icon = Icons.Default.PriceChange, text = "لا توجد أسعار متغيّرة ضمن موادك")
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace2, bottom = bottomClearance),
                    verticalArrangement = Arrangement.spacedBy(AppGroupGap)
                ) {
                    itemsIndexed(items, key = { _, row -> row.name }) { index, item ->
                        val up = item.delta > 0.0
                        val accent = if (up) semantic.danger else semantic.success
                        AppRowSurface(shape = groupedRowShape(index, items.lastIndex)) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = AppSpaceMd, vertical = AppSpaceSm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpaceXs)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(Formatters.number(item.oldPrice), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                        Spacer(Modifier.width(AppSpaceXs))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(AppSpaceXs))
                                        Text(Formatters.number(item.newPrice), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(
                                        "سعر البيع الجديد: ${Formatters.number(item.sellPrice)} $currency",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSurface
                                    )
                                }
                                Spacer(Modifier.width(AppSpaceSm))
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(accent.copy(alpha = 0.14f))
                                        .padding(horizontal = AppSpaceSm, vertical = AppSpaceXs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(AppSpaceXs))
                                    Text(Formatters.number(abs(item.delta)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
