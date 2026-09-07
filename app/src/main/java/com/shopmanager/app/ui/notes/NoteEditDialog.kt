package com.shopmanager.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.notes.ImportantNote
import com.shopmanager.app.data.notes.NoteLinkType
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.GlassAlertDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val timeFormat get() = SimpleDateFormat("HH:mm", Locale.US)

/**
 * Add/edit dialog for a "ملاحظة هامة". Beyond the plain title+content of a
 * simple note, this is also where the two useful ties to the rest of the
 * app live:
 *  - an optional reminder date+time (plain text fields, matching the same
 *    "yyyy-MM-dd" convention [com.shopmanager.app.ui.debts.PersonEditDialog]
 *    already uses instead of a native date picker) that schedules a real
 *    local notification - see [com.shopmanager.app.data.notifications.NoteReminderWorker].
 *  - an optional link to one existing customer (persons) or shortage item
 *    (materials), so a note like "لازم أذكّر أحمد يسدد" or "اتصل بالمورد
 *    لطلب السكر" can point at the actual record it's about instead of
 *    floating disconnected from it.
 */
@Composable
fun NoteEditDialog(
    initial: ImportantNote?,
    persons: List<Person>,
    materials: List<Material>,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, linkType: NoteLinkType, linkedId: String, linkedName: String, reminderAt: Long) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var linkType by remember { mutableStateOf(initial?.linkType ?: NoteLinkType.NONE) }
    var linkedId by remember { mutableStateOf(initial?.linkedId ?: "") }
    var linkedName by remember { mutableStateOf(initial?.linkedName ?: "") }

    var reminderEnabled by remember { mutableStateOf((initial?.reminderAt ?: 0L) > 0L) }
    val initialCal = remember {
        Calendar.getInstance().apply {
            if ((initial?.reminderAt ?: 0L) > 0L) timeInMillis = initial!!.reminderAt
            else add(Calendar.HOUR_OF_DAY, 1)
        }
    }
    var reminderDate by remember { mutableStateOf(dateFormat.format(initialCal.time)) }
    var reminderTime by remember { mutableStateOf(timeFormat.format(initialCal.time)) }

    var linkMenuExpanded by remember { mutableStateOf(false) }
    var pickerMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun computeReminderMillis(): Long? {
        if (!reminderEnabled) return 0L
        val parsed = runCatching { dateFormat.parse(reminderDate) }.getOrNull() ?: return null
        val timeParts = reminderTime.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (timeParts.size != 2) return null
        val cal = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, timeParts[0])
            set(Calendar.MINUTE, timeParts[1])
            set(Calendar.SECOND, 0)
        }
        return cal.timeInMillis
    }

    GlassAlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (initial == null) "ملاحظة جديدة" else "تعديل الملاحظة") },
        text = {
            Column {
                AppTextField(
                    value = title, onValueChange = { title = it }, enabled = !isSaving,
                    label = "العنوان", modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = content, onValueChange = { content = it }, enabled = !isSaving,
                    label = "التفاصيل (اختياري)", singleLine = false, minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )

                // --- الربط بعميل/مادة ---
                Text(
                    "ربط الملاحظة (اختياري)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                Box {
                    LinkTypeRow(
                        linkType = linkType,
                        linkedName = linkedName,
                        enabled = !isSaving,
                        onClick = { linkMenuExpanded = true }
                    )
                    DropdownMenu(expanded = linkMenuExpanded, onDismissRequest = { linkMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("بدون ربط") }, onClick = {
                            linkType = NoteLinkType.NONE; linkedId = ""; linkedName = ""
                            linkMenuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("عميل (من الديون)") }, onClick = {
                            linkType = NoteLinkType.PERSON; linkedId = ""; linkedName = ""
                            linkMenuExpanded = false; pickerMenuExpanded = true
                        })
                        DropdownMenuItem(text = { Text("مادة (من النواقص)") }, onClick = {
                            linkType = NoteLinkType.MATERIAL; linkedId = ""; linkedName = ""
                            linkMenuExpanded = false; pickerMenuExpanded = true
                        })
                    }
                    // Picking "عميل"/"مادة" above immediately opens this
                    // second menu so choosing the *specific* one is a single
                    // continuous flow instead of a separate follow-up tap.
                    DropdownMenu(expanded = pickerMenuExpanded, onDismissRequest = { pickerMenuExpanded = false }) {
                        val options: List<Pair<String, String>> = when (linkType) {
                            NoteLinkType.PERSON -> persons.map { it.id to it.name }
                            NoteLinkType.MATERIAL -> materials.map { it.id to it.name }
                            NoteLinkType.NONE -> emptyList()
                        }
                        if (options.isEmpty()) {
                            DropdownMenuItem(text = { Text("لا يوجد عناصر بعد") }, onClick = { pickerMenuExpanded = false }, enabled = false)
                        }
                        options.forEach { (id, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                linkedId = id; linkedName = name; pickerMenuExpanded = false
                            })
                        }
                    }
                }

                // --- التذكير ---
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تذكير بموعد محدد", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.Switch(
                        checked = reminderEnabled,
                        enabled = !isSaving,
                        onCheckedChange = { reminderEnabled = it }
                    )
                }
                if (reminderEnabled) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = reminderDate, onValueChange = { reminderDate = it }, enabled = !isSaving,
                            label = "التاريخ (yyyy-MM-dd)", modifier = Modifier.weight(1f)
                        )
                        AppTextField(
                            value = reminderTime, onValueChange = { reminderTime = it }, enabled = !isSaving,
                            label = "الوقت (HH:mm)", modifier = Modifier.weight(1f)
                        )
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    val reminderMillis = computeReminderMillis()
                    when {
                        title.isBlank() -> error = "الرجاء إدخال عنوان للملاحظة"
                        reminderEnabled && reminderMillis == null -> error = "الرجاء إدخال تاريخ ووقت صحيحين"
                        else -> {
                            error = null
                            onSave(title.trim(), content.trim(), linkType, linkedId, linkedName, reminderMillis ?: 0L)
                        }
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

@Composable
private fun LinkTypeRow(linkType: NoteLinkType, linkedName: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (linkType) {
            NoteLinkType.PERSON -> Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            NoteLinkType.MATERIAL -> Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            NoteLinkType.NONE -> {}
        }
        Text(
            when {
                linkType == NoteLinkType.NONE -> "بدون ربط"
                linkedName.isNotBlank() -> linkedName
                linkType == NoteLinkType.PERSON -> "اختر عميلاً..."
                else -> "اختر مادة..."
            },
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
}
