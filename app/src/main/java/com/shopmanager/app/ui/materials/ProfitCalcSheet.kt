package com.shopmanager.app.ui.materials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppSheet
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.Text

internal fun String.toPercentOrNull(): Double? = replace(',', '.').toPriceOrNull()

@Composable
internal fun ProfitCalcSheet(
    name: String,
    basePrice: Double?,
    initialPercent: Double?,
    currency: String,
    onSave: (Double) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialPercent?.toEditText() ?: "") }
    val percent = text.toPercentOrNull()
    val cs = MaterialTheme.colorScheme
    AppSheet(title = "نسبة الربح", onDismiss = onDismiss) { close ->
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
        AppTextField(
            value = text,
            onValueChange = { v -> text = v.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '٫' } },
            label = "نسبة الربح %",
            placeholder = "0",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        if (basePrice == null) {
            Text("أدخل سعر المادة أولاً", style = MaterialTheme.typography.bodyMedium, color = cs.error)
        } else if (percent != null) {
            val result = basePrice + basePrice * percent / 100.0
            Text(
                "${Formatters.number(basePrice)} + ${Formatters.number(percent)}% = ${Formatters.number(result)} $currency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppPillButton(
                label = "حفظ",
                onClick = { percent?.let { onSave(it); close() } },
                modifier = Modifier.weight(1f),
                enabled = basePrice != null && percent != null
            )
            if (initialPercent != null) {
                AppPillButton(
                    label = "إزالة",
                    onClick = { onRemove(); close() },
                    modifier = Modifier.weight(1f),
                    tonal = true
                )
            }
        }
    }
}
