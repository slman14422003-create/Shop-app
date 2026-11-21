package com.shopmanager.app.ui.materials

import com.shopmanager.app.ui.common.AppSpace10
import com.shopmanager.app.ui.common.tierAnimateItem
import com.shopmanager.app.ui.common.AppSpace2
import com.shopmanager.app.ui.common.AppSpace6
import com.shopmanager.app.ui.common.AppSpaceMd
import com.shopmanager.app.ui.common.AppSpaceSm
import com.shopmanager.app.ui.common.AppSpaceXs
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import com.shopmanager.app.ui.common.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.PriceChange
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.materials.linePrice
import com.shopmanager.app.data.materials.priceOf
import androidx.compose.ui.text.input.KeyboardType
import com.shopmanager.app.ui.common.rememberSmartSearchSplit
import com.shopmanager.app.data.materials.PriceHistoryStore
import androidx.compose.foundation.lazy.LazyListScope
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.common.LocalFloatingBottomNavHeight
import androidx.compose.ui.graphics.Shape
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppIconCircle
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.groupedRowShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon

/**
 * REDESIGN ("لوحة الأسعار... تصميم كامل ومحسّن"): rebuilt from a bare
 * name+field list into an actual priced overview:
 * - A summary card up top totals how many catalog items already have a
 *   price set out of the total, plus the running sum of every priced item
 *   (edited, unsaved values included) — the same "total at a glance" idea
 *   the shortage tab already has, brought over to this tab too.
 * - A search field to filter the catalog by name, matching the pattern
 *   already used on the المواد tab, since a real catalog can run long.
 * - Each row now carries a small colored tag-icon avatar (the same avatar
 *   pattern MaterialRow uses) instead of bare text, a currency suffix
 *   directly in the price field, and a "priced"/"unpriced" visual state so
 *   it's obvious at a glance which items still need a price.
 * - The "حفظ الأسعار" action itself now lives beside the floating nav pill
 *   at the bottom of the screen (see MaterialsScreen's `editedPrices`/
 *   `savePricesRequested` and FloatingBottomNav's `secondaryAction`,
 *   wired up in MainActivity) instead of a full-width button drawn here —
 *   it reads as one attached unit with the pill, the same way "مادة
 *   جديدة" already does, rather than a separate button with its own
 *   reserved strip of empty space below the list.
 */
@Composable
internal fun PricesList(
    catalogItems: List<MaterialCatalogItem>,
    materials: List<Material>,
    prices: Map<String, Double>,
    history: Map<String, List<PriceChange>>,
    edited: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    search: String,
    profitPercents: Map<String, Double> = emptyMap(),
    onSetProfit: (String, Double?) -> Unit = { _, _ -> }
) {
    var subTab by rememberSaveable { mutableStateOf(1) } // 0 = الناقصة، 1 = كل الأسعار
    var historyTarget by remember { mutableStateOf<String?>(null) }
    var calcTarget by remember { mutableStateOf<String?>(null) }

    historyTarget?.let { target ->
        PriceHistoryDialog(
            name = target,
            history = history[target.trim()].orEmpty(),
            onDismiss = { historyTarget = null }
        )
    }
    calcTarget?.let { target ->
        val base = edited[target]?.toPriceOrNull() ?: prices.priceOf(target)
        ProfitCalcSheet(
            name = target,
            basePrice = base,
            initialPercent = profitPercents[target.trim()],
            currency = AppSettingsState.currencySymbol,
            onSave = { onSetProfit(target, it) },
            onRemove = { onSetProfit(target, null) },
            onDismiss = { calcTarget = null }
        )
    }

    Column(Modifier.fillMaxSize()) {
        PricesSubTabs(
            selected = subTab,
            onSelect = { subTab = it },
            modifier = Modifier.padding(start = AppScreenPadding, end = AppScreenPadding, bottom = AppSpaceSm)
        )
        if (subTab == 0) {
            MissingPricesBody(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                catalogItems = catalogItems,
                materials = materials,
                prices = prices,
                history = history,
                edited = edited,
                search = search,
                onShowHistory = { historyTarget = it },
                onCalc = { calcTarget = it }
            )
        } else {
            AllPricesBody(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                catalogItems = catalogItems,
                materials = materials,
                prices = prices,
                history = history,
                edited = edited,
                search = search,
                onShowHistory = { historyTarget = it },
                onCalc = { calcTarget = it }
            )
        }
    }
}

@Composable
private fun PricesSubTabs(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(AppSpaceXs),
        horizontalArrangement = Arrangement.spacedBy(AppSpaceXs)
    ) {
        MaterialsTabButton(
            label = "الناقصة",
            selected = selected == 0,
            leadingStar = false,
            starTint = Color.Unspecified,
            onClick = { onSelect(0) },
            modifier = Modifier.weight(1f)
        )
        MaterialsTabButton(
            label = "كل الأسعار",
            selected = selected == 1,
            leadingStar = false,
            starTint = Color.Unspecified,
            onClick = { onSelect(1) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MissingPricesBody(
    modifier: Modifier,
    catalogItems: List<MaterialCatalogItem>,
    materials: List<Material>,
    prices: Map<String, Double>,
    history: Map<String, List<PriceChange>>,
    edited: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    search: String,
    onShowHistory: (String) -> Unit,
    onCalc: (String) -> Unit
) {
    val currency = AppSettingsState.currencySymbol
    // الاعتماد على الأسعار المحفوظة فقط: الصف لا يختفي أثناء الكتابة، ويخرج من القائمة بعد الحفظ.
    val missing = remember(materials, catalogItems, prices) {
        val catalogByName = catalogItems.associateBy { it.name.trim() }
        materials
            .distinctBy { it.name.trim() }
            .filter { prices.priceOf(it.name) == null }
            .map { m -> catalogByName[m.name.trim()] ?: MaterialCatalogItem(id = m.name, name = m.name) }
    }
    val split = rememberSmartSearchSplit(missing, search) { it.name }
    val filtered = split.all
    val shortageByName = remember(materials) { materials.groupBy { it.name.trim() } }
    val bottomClearance = LocalFloatingBottomNavHeight.current + 32.dp

    if (filtered.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            if (search.isNotBlank() && missing.isNotEmpty()) EmptyState(icon = Icons.Default.SearchOff, text = "لا توجد نتائج")
            else EmptyState(icon = Icons.Default.Sell, text = "لا توجد مواد ناقصة السعر")
        }
        return
    }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace2, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(AppGroupGap)
    ) {
        item(key = "priceHint") {
            AppFootnote(
                "السعر المكتوب هو سعر الكيلو، ويُضرب تلقائياً بوزن المادة في قائمة النواقص (نص كيلو، ربع كيلو، لوقية…)",
                modifier = Modifier.padding(bottom = AppSpaceSm)
            )
        }
        priceRows(filtered, "n_", currency, prices, edited, history, shortageByName, true, onCalc, onShowHistory)
    }
}

@Composable
private fun AllPricesBody(
    modifier: Modifier,
    catalogItems: List<MaterialCatalogItem>,
    materials: List<Material>,
    prices: Map<String, Double>,
    history: Map<String, List<PriceChange>>,
    edited: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    search: String,
    onShowHistory: (String) -> Unit,
    onCalc: (String) -> Unit
) {
    if (catalogItems.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Default.Inventory2, text = "أضف مواد للقائمة الثابتة أولاً لتسعيرها")
        }
        return
    }

    val currency = AppSettingsState.currencySymbol
    val searching = search.isNotBlank()
    val split = rememberSmartSearchSplit(catalogItems, search) { it.name }
    val filtered = split.all

    // صفوف النواقص مجمّعة باسم المادة (مقصوص) لربطها بعنصر الكتالوج نفسه.
    val shortageByName = remember(materials) { materials.groupBy { it.name.trim() } }

    val recentlyEdited = remember(filtered, history, searching) {
        if (searching) emptyList()
        else {
            val now = System.currentTimeMillis()
            filtered.mapNotNull { c ->
                val last = history[c.name.trim()]?.firstOrNull() ?: return@mapNotNull null
                if (now - last.at in 0..PriceHistoryStore.RECENT_WINDOW_MS) c to last.at else null
            }.sortedByDescending { it.second }.map { it.first }
        }
    }
    val recentIds = remember(recentlyEdited) { recentlyEdited.mapTo(HashSet()) { it.id } }
    val inShortage = remember(filtered, shortageByName, recentIds, searching) {
        if (searching) emptyList()
        else filtered.filter { it.id !in recentIds && shortageByName.containsKey(it.name.trim()) }
    }
    val others = remember(filtered, shortageByName, recentIds, searching) {
        if (searching) emptyList()
        else filtered.filter { it.id !in recentIds && !shortageByName.containsKey(it.name.trim()) }
    }

    val bottomClearance = LocalFloatingBottomNavHeight.current + 32.dp
    Column(modifier) {
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Default.SearchOff, text = "لا توجد نتائج")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = AppSpace2, bottom = bottomClearance),
                verticalArrangement = Arrangement.spacedBy(AppGroupGap)
            ) {
                item(key = "priceHint") {
                    AppFootnote(
                        "السعر المكتوب هو سعر الكيلو، ويُضرب تلقائياً بوزن المادة في قائمة النواقص (نص كيلو، ربع كيلو، لوقية…)",
                        modifier = Modifier.padding(bottom = AppSpaceSm)
                    )
                }
                if (searching) {
                    if (split.matches.isNotEmpty()) {
                        item(key = "titleMatches") {
                            AppSectionTitle(text = "المطابقة (${split.matches.size})", modifier = Modifier.padding(bottom = AppSpace2))
                        }
                        priceRows(split.matches, "m_", currency, prices, edited, history, shortageByName, true, onCalc, onShowHistory)
                    }
                    if (split.suggestions.isNotEmpty()) {
                        item(key = "titleSuggestions") {
                            AppSectionTitle(
                                text = "اقتراحات (${split.suggestions.size})",
                                modifier = Modifier.padding(top = if (split.matches.isEmpty()) 0.dp else 10.dp, bottom = AppSpace2)
                            )
                        }
                        priceRows(split.suggestions, "x_", currency, prices, edited, history, shortageByName, true, onCalc, onShowHistory)
                    }
                } else {
                    if (recentlyEdited.isNotEmpty()) {
                        item(key = "titleRecent") {
                            AppSectionTitle(text = "عُدّلت أسعارها مؤخراً (${recentlyEdited.size})", modifier = Modifier.padding(bottom = AppSpace2))
                        }
                        priceRows(recentlyEdited, "r_", currency, prices, edited, history, shortageByName, true, onCalc, onShowHistory)
                    }
                    if (inShortage.isNotEmpty()) {
                        item(key = "titleShortage") {
                            AppSectionTitle(
                                text = "في قائمة النواقص (${inShortage.size})",
                                modifier = Modifier.padding(top = if (recentlyEdited.isEmpty()) 0.dp else 10.dp, bottom = AppSpace2)
                            )
                        }
                        priceRows(inShortage, "s_", currency, prices, edited, history, shortageByName, true, onCalc, onShowHistory)
                    }
                    if (others.isNotEmpty()) {
                        item(key = "titleOthers") {
                            val first = recentlyEdited.isEmpty() && inShortage.isEmpty()
                            AppSectionTitle(
                                text = if (first) "القائمة الثابتة (${others.size})" else "بقية القائمة (${others.size})",
                                modifier = Modifier.padding(top = if (first) 0.dp else 10.dp, bottom = AppSpace2)
                            )
                        }
                        priceRows(others, "o_", currency, prices, edited, history, shortageByName, false, onCalc, onShowHistory)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.priceRows(
    list: List<MaterialCatalogItem>,
    keyPrefix: String,
    currency: String,
    prices: Map<String, Double>,
    edited: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    history: Map<String, List<PriceChange>>,
    shortageByName: Map<String, List<Material>>,
    showShortage: Boolean,
    onCalc: (String) -> Unit,
    onShowHistory: (String) -> Unit
) {
    itemsIndexed(list, key = { _, c -> keyPrefix + c.id }, contentType = { _, _ -> "priceRow" }) { rowIndex, item ->
androidx.compose.foundation.layout.Box(Modifier.then(tierAnimateItem())) {
        val effective = edited[item.name]?.toPriceOrNull() ?: prices.priceOf(item.name)
        PriceRow(
            shape = groupedRowShape(rowIndex, list.lastIndex),
            item = item,
            currency = currency,
            // قيمة الحقل نص قابل للتحليل مجدداً (بلا ".0" ولا 1.1E7).
            value = edited[item.name] ?: prices[item.name]?.toEditText() ?: "",
            effectivePrice = effective,
            history = history[item.name.trim()].orEmpty(),
            onShowHistory = { onShowHistory(item.name) },
            onCalc = { onCalc(item.name) },
            shortageRows = if (showShortage) shortageByName[item.name.trim()].orEmpty() else emptyList(),
            onValueChange = { edited[item.name] = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '٫' } }
        )
    }
}
}

/**
 * صف كتالوج في تبويب الأسعار: أيقونة، الاسم، (إن كانت المادة في النواقص) سطر الحساب
 * "كميتها = سعرها"، وحقل سعر الكيلو مضغوط الارتفاع.
 */
@Composable
internal fun PriceRow(
    shape: Shape,
    item: MaterialCatalogItem,
    currency: String,
    value: String,
    effectivePrice: Double?,
    history: List<PriceChange>,
    onShowHistory: () -> Unit,
    shortageRows: List<Material>,
    onCalc: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val avatarColor = remember(item.name) { avatarColorFor(item.name) }
    val hasPrice = effectivePrice != null
    val cs = MaterialTheme.colorScheme
    AppRowSurface(shape = shape) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AppSpaceMd, vertical = AppSpace6),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconCircle(
                color = if (hasPrice) avatarColor else avatarColor.copy(alpha = 0.35f),
                size = 36.dp
            ) {
                Icon(
                    Icons.Default.Sell,
                    contentDescription = null,
                    tint = if (hasPrice) Color.White else avatarColor,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(AppSpace10))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (history.isNotEmpty()) {
                    PriceChangeBadge(history = history, onClick = onShowHistory)
                }
                if (shortageRows.isNotEmpty()) {
                    val qty = shortageRows.joinToString(" + ") { it.quantityLabel() }
                    val total = if (effectivePrice == null) null
                    else shortageRows.sumOf { it.linePrice(effectivePrice) ?: 0.0 }
                    Text(
                        if (total == null) "$qty — بدون سعر" else "$qty = ${Formatters.number(total)} $currency",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(AppSpaceSm))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHighest)
                    .clickable(role = Role.Button, onClick = onCalc),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Calculate, contentDescription = "حاسبة الربح", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(AppSpaceSm))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(138.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(cs.onSurface),
                decorationBox = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceContainerHighest)
                            .padding(horizontal = AppSpaceMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text("0", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant.copy(alpha = 0.9f))
                            }
                            inner()
                        }
                        Spacer(Modifier.width(AppSpaceXs))
                        Text(currency, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

/** تنظيف رقم السعر المكتوب: أرقام عربية→لاتينية، فاصلة عشرية عربية→نقطة، حذف فواصل الآلاف والمسافات. */
internal fun String.toPriceOrNull(): Double? {
    val cleaned = buildString {
        for (c in this@toPriceOrNull) {
            when (c) {
                in '٠'..'٩' -> append('0' + (c - '٠'))
                in '۰'..'۹' -> append('0' + (c - '۰'))
                '٫' -> append('.')
                ',', '٬', ' ' -> Unit
                else -> append(c)
            }
        }
    }
    val v = cleaned.toDoubleOrNull() ?: return null
    return if (v.isFinite() && v >= 0.0) v else null
}

/** نص قابل للتحرير لسعر محفوظ: بلا ".0" ولا صيغة علمية (1.1E7). */
internal fun Double.toEditText(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString()
    else java.math.BigDecimal(this).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
