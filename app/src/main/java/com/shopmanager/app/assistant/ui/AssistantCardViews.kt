package com.shopmanager.app.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Undo
import com.shopmanager.app.assistant.AssistantCard
import com.shopmanager.app.assistant.AssistantMessage
import com.shopmanager.app.assistant.AssistantViewModel
import com.shopmanager.app.assistant.CardField
import com.shopmanager.app.assistant.engine.AmbiguityKind
import com.shopmanager.app.ui.common.AppPillButton

/** عرض البطاقات الغنية داخل فقاعة المساعد (تأكيد، نتيجة، جواب، توضيح). تُستعمل في المحادثة والرئيسية. */
@Composable
fun AssistantCardView(
    message: AssistantMessage,
    card: AssistantCard,
    assistant: AssistantViewModel,
    undoable: Set<Long>,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (card) {
            is AssistantCard.Confirm -> {
                Text(card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FieldRows(card.fields)
                card.warnings.forEach {
                    Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = AssistantWarnOrange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AppPillButton("تنفيذ", { assistant.confirm(message.id) }, Modifier.weight(1f), height = 40.dp)
                    AppPillButton("تعديل", { assistant.edit(message.id) }, Modifier.weight(1f), tonal = true, height = 40.dp)
                    AppPillButton("إلغاء", { assistant.cancel(message.id) }, Modifier.weight(1f), tonal = true, height = 40.dp)
                }
            }
            is AssistantCard.Result -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (card.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = if (card.success) AssistantSuccessGreen else AssistantWarnOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (card.details.isNotBlank()) {
                    Text(card.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val undoId = card.undoId
                if (undoId != null && undoId in undoable) {
                    AppPillButton("تراجع", { assistant.undo(undoId) }, tonal = true, height = 38.dp, icon = Icons.Filled.Undo)
                }
            }
            is AssistantCard.Answer -> {
                Text(card.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FieldRows(card.rows)
                card.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            is AssistantCard.Clarify -> {
                Text(card.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                card.options.forEach { o ->
                    AppPillButton(o.label, { assistant.choose(message.id, o) }, Modifier.fillMaxWidth(), tonal = true, height = 40.dp)
                }
                val kind = card.command.ambiguity?.kind
                if (kind == AmbiguityKind.FUZZY_PERSON || kind == AmbiguityKind.FUZZY_MATERIAL) {
                    AppPillButton("لا، غير هيك", { assistant.dismissClarify(message.id) }, Modifier.fillMaxWidth(), tonal = true, height = 40.dp)
                }
            }
        }
    }
}

@Composable
private fun FieldRows(rows: List<CardField>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { r ->
            if (r.value.isBlank()) {
                Text(
                    r.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Spacer(Modifier.height(0.dp).padding(horizontal = 4.dp))
                    Text(r.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
