package com.shopmanager.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.common.AnimatedCounterText
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.GradientIconButton
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.PullToRefreshContent
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.WarningAmber as WarningAmberColor
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ActivityRow(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val timestamp: Long
)

private fun timeBasedGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "سهرانين لهلق؟ 🌙"
        hour < 12 -> "صباح الخير ☀️"
        hour < 17 -> "أهلاً بك 👋"
        hour < 21 -> "مساء الخير 🌇"
        else -> "مساء النور 🌙"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    debtsViewModel: DebtsViewModel,
    materialsViewModel: MaterialsViewModel,
    onOpenSettings: () -> Unit,
    onNavigateToDebts: () -> Unit = {},
    onNavigateToMaterials: () -> Unit = {}
) {
    val debtsState by debtsViewModel.uiState.collectAsState()
    val materialsState by materialsViewModel.uiState.collectAsState()
    val debtsRefreshing by debtsViewModel.isRefreshing.collectAsState()
    val materialsRefreshing by materialsViewModel.isRefreshing.collectAsState()
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    val df = remember { SimpleDateFormat("d MMM، HH:mm", Locale("ar")) }

    // Every material in the list is, by definition, a shortage the shop
    // needs to buy - it's a live shopping list, not a stock count.
    val shortages = remember(materialsState.materials) { materialsState.materials }
    val topDebtors = remember(debtsState.persons) {
        debtsState.persons.sortedByDescending { it.amount }.take(5)
    }
    val isLoading = debtsState.isLoading || materialsState.isLoading

    val recentActivity = remember(debtsState.debts, debtsState.persons, materialsState.materials) {
        val personsById = debtsState.persons.associateBy { it.id }
        val debtRows = debtsState.debts.map { debt ->
            val personName = personsById[debt.personId]?.name ?: "عميل"
            ActivityRow(
                icon = Icons.Default.AttachMoney,
                color = avatarColorFor(personName),
                title = personName,
                subtitle = "دين جديد: ${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}",
                timestamp = debt.createdAt
            )
        }
        val materialRows = materialsState.materials.map { m ->
            ActivityRow(
                icon = Icons.Default.Spa,
                color = avatarColorFor(m.name),
                title = m.name,
                subtitle = "نقص مضاف: ${m.quantityLabel()}",
                timestamp = m.updatedAt
            )
        }
        (debtRows + materialRows).sortedByDescending { it.timestamp }.take(6)
    }

    Scaffold { padding ->
        PullToRefreshContent(
            isRefreshing = debtsRefreshing || materialsRefreshing,
            onRefresh = { debtsViewModel.refresh(); materialsViewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DashboardHeader(onOpenSettings = onOpenSettings) }

            item {
                QuickActionsRow(
                    onAddPerson = onNavigateToDebts,
                    onAddMaterial = onNavigateToMaterials
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AttachMoney,
                        accentColor = MaterialTheme.colorScheme.primary,
                        title = "إجمالي الديون",
                        valueContent = {
                            AnimatedCounterText(
                                targetValue = debtsState.totalAmount,
                                format = { "${nf.format(it)} ${AppSettingsState.currencySymbol}" }
                            )
                        },
                        subtitle = "${debtsState.totalPersons} عميل"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Inventory2,
                        accentColor = if (shortages.isNotEmpty()) WarningAmberColor else MaterialTheme.colorScheme.secondary,
                        title = "قائمة النواقص",
                        valueContent = {
                            AnimatedCounterText(
                                targetValue = shortages.size.toDouble(),
                                format = { it.toInt().toString() }
                            )
                        },
                        subtitle = if (shortages.isNotEmpty()) "بانتظار الشراء" else "لا يوجد نواقص"
                    )
                }
            }

            if (isLoading && debtsState.persons.isEmpty() && materialsState.materials.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            }

            if (shortages.isNotEmpty()) {
                item {
                    SectionCard(title = "🛒 قائمة مشتريات السوق", color = WarningAmberColor) {
                        shortages.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m.name)
                                Text(m.quantityLabel(), color = WarningAmberColor, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (recentActivity.isNotEmpty()) {
                item {
                    SectionCard(title = "🕓 آخر النشاطات", icon = Icons.Default.History) {
                        recentActivity.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(MaterialTheme.shapes.small).background(row.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(row.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(row.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (row.timestamp > 0) {
                                    Text(
                                        df.format(Date(row.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (topDebtors.isNotEmpty()) {
                item {
                    SectionCard(title = "أكبر الديون") {
                        topDebtors.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(28.dp).clip(MaterialTheme.shapes.small).background(avatarColorFor(p.name)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        p.name.firstOrNull()?.uppercase() ?: "?",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(p.name, modifier = Modifier.weight(1f))
                                Text(
                                    "${nf.format(p.amount)} ${AppSettingsState.currencySymbol}",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (debtsState.persons.isEmpty() && materialsState.materials.isEmpty() && !debtsState.isLoading && !materialsState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Storefront, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "ابدأ بإضافة عملاء أو مواد من التبويبات بالأسفل",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * Large-title header, iOS-style: a bold oversized title with a small
 * secondary greeting above it, sitting on a flat brand-gradient panel with
 * softly rounded bottom corners instead of a hard-edged bar. The settings
 * affordance is a solid opaque circular button ([GradientIconButton]) — no
 * translucency/blur — which is what was reading as dated before (a plain
 * unstyled gear glyph floating directly on the gradient with no shape of
 * its own).
 */
@Composable
private fun DashboardHeader(onOpenSettings: () -> Unit) {
    val greeting = remember { timeBasedGreeting() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(BrandGradient.brush(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    color = BrandOnGradient.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "إدارة المحل",
                    color = BrandOnGradient,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp, lineHeight = 33.sp),
                    fontWeight = FontWeight.Bold
                )
            }
            GradientIconButton(icon = Icons.Rounded.Settings, contentDescription = "الإعدادات", onClick = onOpenSettings)
        }
    }
}

@Composable
private fun QuickActionsRow(onAddPerson: () -> Unit, onAddMaterial: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PersonAdd,
            label = "عميل جديد",
            onClick = onAddPerson
        )
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Inventory2,
            label = "مادة جديدة",
            onClick = onAddMaterial
        )
    }
}

@Composable
private fun QuickActionButton(modifier: Modifier = Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "quickActionScale"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accentColor: Color,
    title: String,
    valueContent: @Composable () -> Unit,
    subtitle: String
) {
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            valueContent()
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
