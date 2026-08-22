package com.shopmanager.app.ui.debts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
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

/**
 * BUG FIXED: this dialog used to have no loading/disabled state at all while
 * savePerson() was in flight (it does a name-uniqueness query, then the
 * write - both real network round trips). Nothing stopped repeated taps, and
 * there was zero visual feedback, so the dialog looked "stuck" even though
 * it had already saved - and repeated taps could race and create duplicate
 * customers. Now the confirm button disables and shows a spinner the moment
 * saving starts, until the parent screen confirms success or failure.
 */
@Composable
fun PersonEditDialog(
    initial: Person?,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, date: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(initial?.date?.ifBlank { today() } ?: today()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (initial == null) "عميل جديد" else "تعديل العميل") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, enabled = !isSaving,
                    label = { Text("اسم العميل") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, enabled = !isSaving,
                    label = { Text("المبلغ (ل.س)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it }, enabled = !isSaving,
                    label = { Text("التاريخ (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val amountValue = amount.trim().toDoubleOrNull()
                    when {
                        name.isBlank() -> error = "الرجاء إدخال اسم العميل"
                        amountValue == null || amountValue < 0 -> error = "الرجاء إدخال مبلغ صحيح"
                        date.isBlank() -> error = "الرجاء اختيار التاريخ"
                        else -> { error = null; onSave(name.trim(), amountValue, date) }
                    }
                }
            ) {
                if (isSaving) {
                    Row {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        Text("  جارِ الحفظ...")
                    }
                } else {
                    Text("حفظ")
                }
            }
        },
        dismissButton = { TextButton(enabled = !isSaving, onClick = onDismiss) { Text("إلغاء") } }
    )
}
