package com.shopmanager.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نظرة عامة") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "إجمالي الديون",
                        value = "${nf.format(debtsState.totalAmount)} ل.س",
                        subtitle = "${debtsState.totalPersons} عميل"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
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
                                Text("${m.quantity} ${m.unit}", color = WarningAmberColor)
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
                                Text("${nf.format(p.amount)} ل.س", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (debtsState.persons.isEmpty() && materialsState.materials.isEmpty() && !debtsState.isLoading && !materialsState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text("ابدأ بإضافة عملاء أو مواد من التبويبات بالأسفل")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
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
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
