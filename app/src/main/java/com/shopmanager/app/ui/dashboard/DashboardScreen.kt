package com.shopmanager.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.WarningAmber as WarningAmberColor
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    debtsViewModel: DebtsViewModel,
    materialsViewModel: MaterialsViewModel,
    onOpenSettings: () -> Unit
) {
    val debtsState by debtsViewModel.uiState.collectAsState()
    val materialsState by materialsViewModel.uiState.collectAsState()
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }

    val lowStock = materialsState.materials.filter { it.minQuantity > 0 && it.quantity <= it.minQuantity }
    val topDebtors = debtsState.persons.sortedByDescending { it.amount }.take(5)

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DashboardHeader(onOpenSettings = onOpenSettings) }

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
                        value = "${nf.format(debtsState.totalAmount)} ل.س",
                        subtitle = "${debtsState.totalPersons} عميل"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Inventory2,
                        accentColor = if (lowStock.isNotEmpty()) WarningAmberColor else MaterialTheme.colorScheme.secondary,
                        title = "عدد المواد",
                        value = materialsState.materials.size.toString(),
                        subtitle = if (lowStock.isNotEmpty()) "${lowStock.size} بحاجة تجديد" else "المخزون جيد"
                    )
                }
            }

            if (lowStock.isNotEmpty()) {
                item {
                    SectionCard(title = "⚠️ مواد بحاجة لإعادة تعبئة", color = WarningAmberColor) {
                        lowStock.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m.name)
                                Text("${m.quantity} ${m.unit}", color = WarningAmberColor, fontWeight = FontWeight.Medium)
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
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(p.name)
                                Text("${nf.format(p.amount)} ل.س", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun DashboardHeader(onOpenSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                )
            )
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("أهلاً بك 👋", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text("إدارة المحل", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color.White)
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    title: String,
    value: String,
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
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    color: Color = MaterialTheme.colorScheme.primary,
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
