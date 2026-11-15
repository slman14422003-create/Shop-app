package com.shopmanager.app.ui.debts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.ui.common.AppBorderWidth
import com.shopmanager.app.ui.common.AppAmountHeroSize
import com.shopmanager.app.ui.common.AppAmountRowSize
import com.shopmanager.app.ui.common.AppCaptionLargeSize
import com.shopmanager.app.ui.common.AppCardLargeRadius
import com.shopmanager.app.ui.common.AppColorPillButton
import com.shopmanager.app.ui.common.AppMinTouchTarget
import com.shopmanager.app.ui.common.AppPillChip
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.Formatters
import com.shopmanager.app.ui.common.appAccent
import com.shopmanager.app.ui.common.appAmountStyle
import com.shopmanager.app.ui.common.appBorderColor
import com.shopmanager.app.ui.common.appOnAccent
import com.shopmanager.app.ui.common.tabular
import com.shopmanager.app.ui.theme.LocalSemanticColors
import com.shopmanager.app.util.DateFormats
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

enum class QuickAddMode { DEBT, PAY }

data class QuickAddRequest(val mode: QuickAddMode, val personId: String? = null)

private const val MAX_DIGITS = 12

private fun todayString(): String = DateFormats.get("yyyy-MM-dd", Locale.US).format(Date())

private fun keyLabel(key: String): String =
    key.map { Formatters.number((it - '0').toDouble()) }.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    viewModel: DebtsViewModel,
    request: QuickAddRequest,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val semantic = LocalSemanticColors.current
    val currency = AppSettingsState.currencySymbol
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    var mode by remember { mutableStateOf(request.mode) }
    var selectedId by remember { mutableStateOf(request.personId) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val recents = remember(state.persons, state.debts) { recentPersons(state.persons, state.debts) }
    val notes = remember(state.debts) { recentNotes(state.debts) }
    val selected = state.persons.firstOrNull { it.id == selectedId }
    val candidates = if (mode == QuickAddMode.PAY) recents.filter { it.amount > 0 } else recents
    val chips = remember(candidates, selected) {
        val base = candidates.take(6)
        if (selected != null && base.none { it.id == selected.id } && (mode == QuickAddMode.DEBT || selected.amount > 0)) {
            listOf(selected) + base.take(5)
        } else base
    }

    val amount = digits.toDoubleOrNull() ?: 0.0
    val before = if (creating) 0.0 else selected?.amount ?: 0.0
    val after = if (mode == QuickAddMode.DEBT) before + amount else (before - amount).coerceAtLeast(0.0)
    val accent = if (mode == QuickAddMode.DEBT) appAccent() else semantic.success
    val canSave = !saving && when (mode) {
        QuickAddMode.DEBT -> if (creating) newName.isNotBlank() else selected != null && amount > 0
        QuickAddMode.PAY -> selected != null && selected.amount > 0 && amount > 0
    }
    val hasTarget = creating || selected != null

    fun press(key: String) {
        if (digits.isEmpty() && key.all { it == '0' }) return
        if (digits.length + key.length > MAX_DIGITS) return
        digits += key
    }

    fun save() {
        if (!canSave) return
        val date = todayString()
        when (mode) {
            QuickAddMode.DEBT -> if (creating) {
                saving = true
                viewModel.savePerson(null, newName.trim(), amount, date, note.trim(), "") { ok ->
                    saving = false
                    if (ok) close()
                }
            } else {
                viewModel.addOrUpdateDebt(null, selected!!.id, amount, date, note.trim())
                close()
            }
            QuickAddMode.PAY -> {
                val person = selected!!
                if (amount >= person.amount - 0.000001) viewModel.markPersonAsPaid(person)
                else viewModel.payPartial(person, amount)
                close()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = AppCardLargeRadius, topEnd = AppCardLargeRadius)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = AppScreenPadding, end = AppScreenPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeSwitch(
                mode = mode,
                debtColor = appAccent(),
                payColor = semantic.success,
                onModeChange = { next ->
                    mode = next
                    if (next == QuickAddMode.PAY) {
                        creating = false
                        if (selected != null && selected.amount <= 0) selectedId = null
                    }
                }
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mode == QuickAddMode.DEBT) {
                    AppPillChip(
                        label = "+ جديد",
                        selected = creating,
                        onClick = { creating = !creating; if (creating) selectedId = null },
                        selectedColor = accent,
                        selectedContentColor = appOnAccent()
                    )
                }
                chips.forEach { person ->
                    AppPillChip(
                        label = person.name,
                        selected = !creating && person.id == selectedId,
                        onClick = { creating = false; selectedId = person.id },
                        selectedColor = accent,
                        selectedContentColor = appOnAccent()
                    )
                }
            }

            if (creating) {
                AppTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = "اسم العميل",
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                    Text(
                        if (digits.isEmpty()) Formatters.number(0.0) else Formatters.number(amount),
                        style = appAmountStyle(AppAmountHeroSize),
                        color = if (digits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        currency,
                        style = appAmountStyle(AppAmountRowSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (hasTarget) "الرصيد بعد العملية: ${Formatters.number(after)} $currency" else "اختر عميلاً",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = AppCaptionLargeSize).tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !hasTarget -> MaterialTheme.colorScheme.onSurfaceVariant
                        mode == QuickAddMode.PAY || after <= 0.0 -> semantic.success
                        else -> semantic.danger
                    },
                    textAlign = TextAlign.Center
                )
            }

            if (mode == QuickAddMode.DEBT && notes.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    notes.forEach { text ->
                        AppPillChip(
                            label = text,
                            selected = note == text,
                            onClick = { note = if (note == text) "" else text }
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("000", "0", "back")
                    ).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                NumKey(
                                    modifier = Modifier.weight(1f),
                                    onClick = { if (key == "back") digits = digits.dropLast(1) else press(key) }
                                ) {
                                    if (key == "back") {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Text(
                                            keyLabel(key),
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp).tabular(),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AppColorPillButton(
                label = "حفظ",
                onClick = { save() },
                containerColor = accent,
                contentColor = appOnAccent(),
                enabled = canSave,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ModeSwitch(
    mode: QuickAddMode,
    debtColor: androidx.compose.ui.graphics.Color,
    payColor: androidx.compose.ui.graphics.Color,
    onModeChange: (QuickAddMode) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        listOf(
            Triple(QuickAddMode.DEBT, "دين جديد", debtColor),
            Triple(QuickAddMode.PAY, "تسديد", payColor)
        ).forEach { (value, label, color) ->
            val active = mode == value
            Box(
                Modifier
                    .weight(1f)
                    .height(AppMinTouchTarget)
                    .clip(CircleShape)
                    .background(if (active) color else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onModeChange(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (active) appOnAccent() else cs.onSurface
                )
            }
        }
    }
}

@Composable
private fun NumKey(
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
