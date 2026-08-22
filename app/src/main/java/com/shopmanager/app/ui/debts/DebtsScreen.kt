package com.shopmanager.app.ui.debts

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.debts.Person
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DebtsScreen(
    onOpenPerson: (String) -> Unit,
    viewModel: DebtsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    var search by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("الديون") },
                actions = {
                    IconButton(onClick = {
                        val text = buildDebtsShareText(state.persons, state.totalAmount)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "مشاركة كشف الديون"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة عميل")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatsRow(state.totalPersons, state.totalDebts, state.totalAmount)

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث عن عميل...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true
            )

            val filtered = if (search.isBlank()) state.persons
            else state.persons.filter { it.name.contains(search, ignoreCase = true) }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (search.isBlank()) "لا يوجد عملاء" else "لا توجد نتائج")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { person ->
                        PersonRow(person, Modifier.animateItemPlacement()) { onOpenPerson(person.id) }
                        Divider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PersonEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, date ->
                viewModel.savePerson(null, name, amount, date) { success ->
                    if (success) showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun StatsRow(persons: Int, debts: Int, amount: Double) {
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("عملاء", persons.toString())
        StatItem("ديون", debts.toString())
        StatItem("ل.س", nf.format(amount))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PersonRow(person: Person, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val nf = remember { NumberFormat.getNumberInstance(Locale("ar")) }
    ListItem(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(person.name) },
        supportingContent = { Text("${nf.format(person.amount)} ل.س") },
        leadingContent = {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(person.name.firstOrNull()?.uppercase() ?: "?")
                }
            }
        }
    )
}

private fun buildDebtsShareText(persons: List<Person>, totalAmount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("ar"))
    val sb = StringBuilder("💰 كشف الديون\n\n")
    persons.sortedByDescending { it.amount }.forEach { p ->
        sb.append("• ${p.name}: ${nf.format(p.amount)} ل.س\n")
    }
    sb.append("\nالإجمالي: ${nf.format(totalAmount)} ل.س")
    return sb.toString()
}
