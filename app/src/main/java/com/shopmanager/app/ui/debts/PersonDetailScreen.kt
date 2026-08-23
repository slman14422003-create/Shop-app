package com.shopmanager.app.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.avatarColorFor
import com.shopmanager.app.ui.theme.SuccessGreen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person,
    onBack: () -> Unit,
    viewModel: DebtsViewModel = viewModel()
) {
    val debts by viewModel.debtsForPerson(person.id).collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var editingDebt by remember { mutableStateOf<Debt?>(null) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var showDeletePersonConfirm by remember { mutableStateOf(false) }
    var deleteDebtTarget by remember { mutableStateOf<String?>(null) }
    var payDebtTarget by remember { mutableStateOf<Debt?>(null) }
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    val avatarColor = remember(person.name) { avatarColorFor(person.name) }

    LaunchedEffect(message) {
        message?.let { snackbarHost.showSnackbar(it); viewModel.clearMessage() }
    }

    val total = debts.sumOf { it.amount }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(person.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = BrandOnGradient,
                    navigationIconContentColor = BrandOnGradient,
                    actionIconContentColor = BrandOnGradient
                ),
                modifier = Modifier.background(BrandGradient.brush()),
                actions = {
                    IconButton(onClick = { showDeletePersonConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف العميل")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PersonHeader(
                    name = person.name,
                    avatarColor = avatarColor,
                    total = total,
                    debtsCount = debts.size,
                    nf = nf
                )
            }

            item {
                AddDebtCard(
                    isEditing = editingDebt != null,
                    amount = amount,
                    date = date,
                    onAmountChange = { amount = it },
                    onDateChange = { date = it },
                    onCancelEdit = { editingDebt = null; amount = ""; date = today() },
                    onSubmit = {
                        val a = amount.trim().toDoubleOrNull()
                        if (a != null && a > 0 && date.isNotBlank()) {
                            viewModel.addOrUpdateDebt(editingDebt?.id, person.id, a, date)
                            amount = ""
                            date = today()
                            editingDebt = null
                        }
                    }
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("سجل الديون", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            if (debts.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PriceCheck, contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("لا يوجد ديون مسجلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(debts, key = { it.id }) { debt ->
                    DebtRow(
                        debt = debt,
                        nf = nf,
                        onEdit = {
                            editingDebt = debt
                            amount = debt.amount.toString()
                            date = debt.date
                        },
                        onDelete = { deleteDebtTarget = debt.id },
                        onMarkPaid = { payDebtTarget = debt }
                    )
                }
            }
        }
    }

    if (showDeletePersonConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePersonConfirm = false },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف \"${person.name}\" وكل ديونه؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePerson(person.id)
                    showDeletePersonConfirm = false
                    onBack()
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { showDeletePersonConfirm = false }) { Text("إلغاء") } }
        )
    }

    deleteDebtTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteDebtTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذا الدين؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDebt(id)
                    deleteDebtTarget = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteDebtTarget = null }) { Text("إلغاء") } }
        )
    }

    payDebtTarget?.let { debt ->
        AlertDialog(
            onDismissRequest = { payDebtTarget = null },
            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen) },
            title = { Text("تأكيد السداد") },
            text = { Text("هل \"${person.name}\" وفى ${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}؟ سيتم حذف هذا الدين من السجل وإرسال إشعار.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markDebtAsPaid(debt, person.name)
                    payDebtTarget = null
                }) { Text("تم السداد", color = SuccessGreen, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { payDebtTarget = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun PersonHeader(name: String, avatarColor: Color, total: Double, debtsCount: Int, nf: NumberFormat) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(BrandGradient.brush())
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("إجمالي الديون", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(
                    "${nf.format(total)} ${AppSettingsState.currencySymbol}",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("$debtsCount عملية دين مسجلة", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDebtCard(
    isEditing: Boolean,
    amount: String,
    date: String,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSubmit: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (isEditing) "تعديل الدين" else "إضافة دين جديد",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = amount, onValueChange = onAmountChange,
                    label = { Text("المبلغ (${AppSettingsState.currencySymbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = date, onValueChange = onDateChange,
                    label = { Text("التاريخ") },
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (isEditing) {
                    TextButton(onClick = onCancelEdit) { Text("إلغاء") }
                    Spacer(Modifier.width(4.dp))
                }
                Button(onClick = onSubmit, shape = MaterialTheme.shapes.medium) {
                    Text(if (isEditing) "حفظ التعديل" else "إضافة الدين")
                }
            }
        }
    }
}

@Composable
private fun DebtRow(debt: Debt, nf: NumberFormat, onEdit: () -> Unit, onDelete: () -> Unit, onMarkPaid: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mark-as-paid: a circular check button. Tapping it asks for
            // confirmation, then removes the debt and fires a "paid" notification.
            IconButton(
                onClick = onMarkPaid,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.Check, contentDescription = "تسجيل السداد", tint = SuccessGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${nf.format(debt.amount)} ${AppSettingsState.currencySymbol}",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(debt.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
        }
    }
}
