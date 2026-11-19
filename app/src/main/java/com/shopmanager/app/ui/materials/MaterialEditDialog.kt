package com.shopmanager.app.ui.materials

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.shopmanager.app.ui.common.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.RectangleShape
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
import com.shopmanager.app.data.materials.level
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppGroupLargeRadius
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.common.AppFormSheet
import com.shopmanager.app.ui.common.PrioritySelector
import com.shopmanager.app.ui.common.appAccent
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import com.shopmanager.app.data.performance.MotionSpecs

@Composable
fun MaterialEditDialog(
    initial: Material?,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Double, unit: String, notes: String, level: Int) -> Unit
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
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var level by remember { mutableStateOf(initial?.level ?: 0) }
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

    val levelTone = com.shopmanager.app.ui.common.priorityColor(level)
    var showNote by remember { mutableStateOf(notes.isNotBlank()) }
    AppFormSheet(
        title = if (initial == null) "إضافة نقص" else "تعديل النقص",
        subtitle = when (level) { 2 -> "مادة هامة جداً"; 1 -> "مادة هامة"; else -> "قائمة النواقص" },
        headerIcon = if (level > 0) Icons.Filled.Star else Icons.Filled.Spa,
        emphasisColor = if (level > 0) levelTone else Color.Unspecified,
        isSaving = isSaving,
        onDismiss = onDismiss,
        onConfirm = {
            when {
                name.isBlank() -> error = "يرجى إدخال اسم المادة"
                quantity <= 0 -> error = "يرجى إدخال كمية صحيحة"
                else -> { error = null; onSave((initial?.name ?: name).trim(), quantity.toDouble(), unit.label, notes.trim(), level) }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppTextField(
                value = name, onValueChange = { if (initial == null) name = it }, enabled = initial == null && !isSaving,
                label = "اسم المادة", modifier = Modifier.fillMaxWidth()
            )
            if (showQuantityStepper) {
                QuantityStepper(
                    value = quantity,
                    unitLabel = unit.label,
                    enabled = !isSaving,
                    onValueChange = { quantity = it.coerceAtLeast(1) }
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSectionTitle("الوحدة")
                UnitPicker(
                    selected = unit,
                    enabled = !isSaving,
                    onSelected = {
                        unit = it
                        if (it != MaterialUnit.KG && it != MaterialUnit.NONE) quantity = 1
                    }
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSectionTitle("الأهمية")
                PrioritySelector(level = level, onLevelChange = { level = it }, enabled = !isSaving)
            }
            NoteField(notes = notes, onChange = { notes = it }, expanded = showNote, onExpand = { showNote = true }, enabled = !isSaving)
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun NoteField(notes: String, onChange: (String) -> Unit, expanded: Boolean, onExpand: () -> Unit, enabled: Boolean) {
    if (expanded) {
        AppTextField(
            value = notes, onValueChange = onChange, enabled = enabled,
            label = "ملاحظة (اختياري)",
            singleLine = false, minLines = 1, maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = enabled, role = Role.Button, onClick = onExpand)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("إضافة ملاحظة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * عدّاد الكمية (أعداد صحيحة فقط): صفٌّ مسطّح بلغة صفوف الإعدادات، اسم الوحدة
 * المختارة في البداية (مثل "2 نص كيلو") وأزرار [-] الرقم [+] في النهاية. الرقم
 * قابل للكتابة المباشرة وتُحذف منه كل الرموز غير الرقمية، فلا طريق لقيمة عشرية.
 *
 * FIX: الكمية والوحدة لا تُدمجان أبداً في نص واحد: الحقل يحمل أرقام الكمية فقط،
 * واسم الوحدة نص منفصل غير قابل للتعديل (ويختفي لـ [MaterialUnit.NONE] لأن
 * تسميته فارغة).
 */
@Composable
fun QuantityStepper(
    value: Int,
    unitLabel: String,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppGroupLargeRadius)
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(cs.surfaceContainerHighest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepButton(
            icon = Icons.Default.Remove,
            contentDescription = "إنقاص",
            enabled = enabled && value > 1,
            filled = false,
            onClick = { onValueChange(value - 1) }
        )

        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BasicTextField(
                value = value.toString(),
                onValueChange = { raw ->
                    val digitsOnly = raw.filter { it.isDigit() }
                    onValueChange(digitsOnly.toIntOrNull() ?: 0)
                },
                modifier = Modifier.width(96.dp),
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(cs.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (unitLabel.isNotBlank()) {
                Text(
                    unitLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        StepButton(
            icon = Icons.Default.Add,
            contentDescription = "زيادة",
            enabled = enabled,
            filled = true,
            onClick = { onValueChange(value + 1) }
        )
    }
}

@Composable
private fun StepButton(icon: ImageVector, contentDescription: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = appAccent()
    val bg = if (filled) accent.copy(alpha = if (enabled) 1f else 0.4f) else cs.surface
    val tint = if (filled) Color.White else cs.onSurface.copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
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
 * منتقي الوحدة: حبّات على نمط [com.shopmanager.app.ui.common.AppChip] — المحددة
 * بلون onSurface ونص surface، وغير المحددة على تعبئة الصفوف. ثلاث حبّات في كل
 * صف (عائلة الكيلو ثم عائلة اللوقية) و"بدون" وحدها بعرض كامل لأنها نوع مختلف من
 * الاختيار (لا وحدة أصلاً).
 */
@Composable
fun UnitPicker(selected: MaterialUnit, enabled: Boolean = true, onSelected: (MaterialUnit) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        MaterialUnit.KG, MaterialUnit.HALF_KG, MaterialUnit.QUARTER_KG,
        MaterialUnit.OKE, MaterialUnit.HALF_OKE, MaterialUnit.QUARTER_OKE,
        MaterialUnit.NONE
    )
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            UnitPill(option = option, isSelected = option == selected, enabled = enabled, onSelected = onSelected)
        }
    }
}

@Composable
private fun UnitPill(option: MaterialUnit, isSelected: Boolean, enabled: Boolean = true, onSelected: (MaterialUnit) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val accent = appAccent()
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accent else cs.surfaceContainerHighest,
        animationSpec = MotionSpecs.quickSpring(), label = "unitPillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else cs.onSurface,
        animationSpec = MotionSpecs.quickSpring(), label = "unitPillText"
    )

    Box(
        modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = { onSelected(option) })
            .padding(horizontal = 18.dp),
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
