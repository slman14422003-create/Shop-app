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
import com.shopmanager.app.ui.common.AppTextField
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

    // FIX: a fixed fraction size (نص كيلو، ربع كيلو، لوقية، نص لوقية، ربع
    // لوقية) is already exactly one of itself the moment it's picked -
    // "2 نص كيلو" isn't a size anyone actually orders in, they'd just pick
    // كيلو instead. So the quantity stepper only makes sense for كيلو
    // (where "2 كيلو" is a normal amount) and بدون (a plain count with no
    // size at all, like "بيض: 6"). Switching to any of the other units
    // hides the stepper and pins quantity to 1 so no stale count "1
    // نص كيلو من 3" -> tapping "نص كيلو" would carry a leftover count.
    val showQuantityStepper = unit == MaterialUnit.KG || unit == MaterialUnit.NONE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة نقص" else "تعديل النقص") },
        text = {
            Column {
                AppTextField(
                    value = name, onValueChange = { name = it },
                    label = "اسم المادة", modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "الكمية المطلوبة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
                if (showQuantityStepper) {
                    QuantityStepper(
                        value = quantity,
                        unitLabel = unit.label,
                        onValueChange = { quantity = it.coerceAtLeast(1) }
                    )
                } else {
                    // Quantity is implicitly 1 for a fixed fraction size -
                    // nothing to step, so this just confirms what will be
                    // saved instead of showing a stepper with nothing to do.
                    Text(
                        unit.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    "الوحدة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                UnitPicker(
                    selected = unit,
                    onSelected = {
                        unit = it
                        if (it != MaterialUnit.KG && it != MaterialUnit.NONE) quantity = 1
                    }
                )

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
 *
 * FIX: quantity and unit used to be able to smear together into one string
 * ("1 نص" instead of a clean "1" next to a separately-picked "نص كيلو"). The
 * number field here only ever holds the digits of the quantity; the unit
 * word is a separate, non-editable label next to it (and disappears
 * entirely for [MaterialUnit.NONE], whose label is blank) so the two can
 * never merge into one typed value.
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

        if (unitLabel.isNotBlank()) {
            Text(
                unitLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The text shown ON the picker pill for each unit. Every weight unit shows
 * its own label as-is; [MaterialUnit.NONE] has an empty stored label (so a
 * saved quantity like "6" has nothing appended to it - see
 * [com.shopmanager.app.data.materials.quantityLabel]), but the pill itself
 * still needs something to display, hence "بدون" here instead of the blank
 * stored value.
 */
private val MaterialUnit.pickerLabel: String
    get() = if (this == MaterialUnit.NONE) "بدون" else label

/**
 * Custom pill-style segmented picker for the fixed quantity units of a
 * spice shop (كيلو / نص كيلو / ربع كيلو / لوقية / نص لوقية / ربع لوقية),
 * plus a "بدون" option for shortages that aren't measured by weight at all
 * (e.g. "بيض: 6" - just a plain count). Deliberately not a system
 * Spinner/DropdownMenu or a free-text field - matches the app's own rounded
 * shapes and brand color, with a smoothly animated selection pill.
 *
 * FIX: with the two new نص كيلو / ربع كيلو units this is six weight options,
 * not four - laid out as two rows of three (كيلو-family on top, لوقية-family
 * below) instead of one cramped six-wide row, so every label still has room
 * to breathe on a phone screen. بدون sits on its own row underneath, full
 * width, since it's a different kind of choice ("no unit at all") rather
 * than another weight size.
 */
@Composable
fun UnitPicker(selected: MaterialUnit, onSelected: (MaterialUnit) -> Unit, modifier: Modifier = Modifier) {
    val weightRows = listOf(
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
        weightRows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { option ->
                    UnitPill(option = option, isSelected = option == selected, onSelected = onSelected, modifier = Modifier.weight(1f))
                }
            }
        }
        UnitPill(option = MaterialUnit.NONE, isSelected = selected == MaterialUnit.NONE, onSelected = onSelected, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UnitPill(option: MaterialUnit, isSelected: Boolean, onSelected: (MaterialUnit) -> Unit, modifier: Modifier = Modifier) {
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
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bgColor)
            .clickable(onClick = { onSelected(option) })
            .padding(vertical = verticalPad),
        contentAlignment = Alignment.Center
    ) {
        Text(
            option.pickerLabel,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}
