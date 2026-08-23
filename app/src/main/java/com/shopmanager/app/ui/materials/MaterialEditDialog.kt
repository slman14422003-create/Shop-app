package com.shopmanager.app.ui.materials

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialUnit

@Composable
fun MaterialEditDialog(
    initial: Material?,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Double, unit: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var quantity by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(MaterialUnit.fromLabel(initial?.unit ?: MaterialUnit.KG.label)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة نقص" else "تعديل النقص") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم المادة") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it },
                    label = { Text("الكمية المطلوبة") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Text(
                    "الوحدة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
                UnitPicker(selected = unit, onSelected = { unit = it })

                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = quantity.trim().toDoubleOrNull()
                when {
                    name.isBlank() -> error = "يرجى إدخال اسم المادة"
                    q == null || q <= 0 -> error = "يرجى إدخال كمية صحيحة"
                    else -> onSave(name.trim(), q, unit.label)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/**
 * Custom pill-style segmented picker for the fixed weight units of a spice
 * shop (كغ / لوقية / نص لوقية). Deliberately not a system Spinner/DropdownMenu
 * or a free-text field - matches the app's own rounded shapes and brand
 * color, with a smoothly animated selection pill.
 */
@Composable
fun UnitPicker(selected: MaterialUnit, onSelected: (MaterialUnit) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MaterialUnit.entries.forEach { option ->
            val isSelected = option == selected
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                animationSpec = spring(), label = "unitPillBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(), label = "unitPillText"
            )
            val verticalPad by animateDpAsState(
                targetValue = if (isSelected) 10.dp else 8.dp,
                animationSpec = spring(), label = "unitPillPad"
            )

            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bgColor)
                    .clickable(onClick = { onSelected(option) })
                    .padding(vertical = verticalPad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
