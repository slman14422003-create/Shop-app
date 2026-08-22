package com.shopmanager.app.ui.materials

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
import com.shopmanager.app.data.materials.Material

@Composable
fun MaterialEditDialog(
    initial: Material?,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Double, unit: String, minQuantity: Double) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var quantity by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "كغ") }
    var minQuantity by remember { mutableStateOf(initial?.minQuantity?.takeIf { it > 0 }?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة مادة" else "تعديل المادة") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم المادة") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it },
                    label = { Text("الكمية") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it },
                    label = { Text("الوحدة") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = minQuantity, onValueChange = { minQuantity = it },
                    label = { Text("حد التنبيه لنفاد الكمية (اختياري)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = quantity.trim().toDoubleOrNull()
                val minQ = minQuantity.trim().toDoubleOrNull() ?: 0.0
                when {
                    name.isBlank() -> error = "يرجى إدخال اسم المادة"
                    q == null || q <= 0 -> error = "يرجى إدخال كمية صحيحة"
                    else -> onSave(name.trim(), q, unit.ifBlank { "كغ" }, minQ)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
