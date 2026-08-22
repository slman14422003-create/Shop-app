package com.shopmanager.app.ui.debts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.debts.Person
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
fun PersonEditDialog(
    initial: Person?,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, date: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(initial?.date?.ifBlank { today() } ?: today()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "عميل جديد" else "تعديل العميل") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم العميل") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("المبلغ (ل.س)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("التاريخ (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amountValue = amount.trim().toDoubleOrNull()
                when {
                    name.isBlank() -> error = "الرجاء إدخال اسم العميل"
                    amountValue == null || amountValue < 0 -> error = "الرجاء إدخال مبلغ صحيح"
                    date.isBlank() -> error = "الرجاء اختيار التاريخ"
                    else -> onSave(name.trim(), amountValue, date)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
