package com.shopmanager.app.ui.materials

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialUnit
import com.shopmanager.app.ui.common.ActionIconButton
import com.shopmanager.app.ui.common.MotionSpecs

@Composable
fun MaterialEditDialog(
    initial: Material?,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Double, unit: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // FIX: quantity used to be a free-typed decimal field, which is why
    // "1.5" (or any other decimal) could end up on a كيلو shortage instead
    // of a plain whole count. Quantity is now always a whole number - you
    // pick the size in the unit row below (كيلو / نص كيلو / ربع كيلو /
    // لوقية / نص لوقية / ربع لوقية) and this is just "how many of that
    // size", so typing/typo'd decimals can't happen anymore.
    var quantity by remember {
        mutableStateOf((initial?.quantity?.toInt() ?: 1).coerceAtLeast(1))
    }
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

                Text(
                    "الكمية المطلوبة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
                QuantityStepper(
                    value = quantity,
                    unitLabel = unit.label,
                    onValueChange = { quantity = it.coerceAtLeast(1) }
                )

                Text(
                    "الوحدة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                UnitPicker(selected = unit, onSelected = { unit = it })

                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "يرجى إدخال اسم المادة"
                    quantity <= 0 -> error = "يرجى إدخال كمية صحيحة"
                    else -> onSave(name.trim(), quantity.toDouble(), unit.label)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/**
 * Whole-number [-] value [+] stepper for the required quantity, labeled with
 * the currently-selected unit (e.g. "2 نص كيلو") so it's always clear what's
 * being counted. The number itself can also be tapped and typed directly -
 * non-digit characters are filtered out as they're typed, so there is no
 * path to a decimal value.
 */
@Composable
fun QuantityStepper(value: Int, unitLabel: String, onValueChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionIconButton(
            icon = Icons.Default.Remove,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "إنقاص",
            onClick = { if (value > 1) onValueChange(value - 1) }
        )

        OutlinedTextField(
            value = value.toString(),
            onValueChange = { raw ->
                val digitsOnly = raw.filter { it.isDigit() }
                onValueChange(digitsOnly.toIntOrNull() ?: 0)
            },
            modifier = Modifier.width(84.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small
        )

        ActionIconButton(
            icon = Icons.Default.Add,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "زيادة",
            onClick = { onValueChange(value + 1) }
        )

        Text(
            unitLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Custom pill-style segmented picker for the fixed weight units of a spice
 * shop (كيلو / نص كيلو / ربع كيلو / لوقية / نص لوقية / ربع لوقية).
 * Deliberately not a system Spinner/DropdownMenu or a free-text field -
 * matches the app's own rounded shapes and brand color, with a smoothly
 * animated selection pill.
 *
 * FIX: with the two new نص كيلو / ربع كيلو units this is now six options,
 * not four - laid out as two rows of three (كيلو-family on top, لوقية-family
 * below) instead of one cramped six-wide row, so every label still has room
 * to breathe on a phone screen.
 */
@Composable
fun UnitPicker(selected: MaterialUnit, onSelected: (MaterialUnit) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf(MaterialUnit.KG, MaterialUnit.HALF_KG, MaterialUnit.QUARTER_KG),
        listOf(MaterialUnit.OKE, MaterialUnit.HALF_OKE, MaterialUnit.QUARTER_OKE)
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { option ->
                    val isSelected = option == selected
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        animationSpec = MotionSpecs.quickSpring(), label = "unitPillBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = MotionSpecs.quickSpring(), label = "unitPillText"
                    )
                    val verticalPad by animateDpAsState(
                        targetValue = if (isSelected) 10.dp else 8.dp,
                        animationSpec = MotionSpecs.quickSpring(), label = "unitPillPad"
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
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
