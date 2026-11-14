package com.shopmanager.app.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.voice.ui.VoiceSettingsSection
import com.shopmanager.app.assistant.AssistantViewModel
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar

/** إعدادات المساعد: الاقتراحات الاستباقية، التأكيد الدائم، حركة الروبوت، الرئيسية الجديدة، الأسماء البديلة، مسح السجل. */
@Composable
fun AssistantSettingsScreen(assistant: AssistantViewModel, onBack: () -> Unit) {
    val s by assistant.settings.collectAsState()
    val aliases by assistant.aliases.collectAsState()
    var confirmAliases by remember { mutableStateOf(false) }
    var confirmHistory by remember { mutableStateOf(false) }
    val unheard by assistant.unheard.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "إعدادات المساعد",
            navigation = { ScreenIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack) }
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SwitchRow("الاقتراحات الاستباقية", "اقتراحات على الرئيسية مثل مواد تتكرر في النواقص", s.proactiveSuggestions) { assistant.updateSettings(s.copy(proactiveSuggestions = it)) } }
            item { SwitchRow("اطلب تأكيداً دائماً", "بطاقة تأكيد قبل أي تنفيذ. الحذف وتعديل السعر والمبالغ غير المعتادة تُؤكَّد دائماً حتى لو أطفأته", s.alwaysConfirm) { assistant.updateSettings(s.copy(alwaysConfirm = it)) } }
            item { SwitchRow("حركة الروبوت", "إيقافها يثبّت الروبوت ويوفّر الأداء", s.robotAnimation) { assistant.updateSettings(s.copy(robotAnimation = it)) } }
            item { SwitchRow("وضع التجربة", "ينفّذ المساعد كل شيء شكلياً بدون تغيير أي بيانات", s.dryRun) { assistant.updateSettings(s.copy(dryRun = it)) } }
            item { VoiceSettingsSection(assistant.voice) }
            item { SwitchRow("الرئيسية الجديدة (المساعد)", "إيقافها يعيد الشاشة الرئيسية الكلاسيكية", s.newHome) { assistant.updateSettings(s.copy(newHome = it)) } }

            item { Text("الأسماء البديلة المتعلَّمة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            if (aliases.isEmpty()) {
                item { Text("لا توجد أسماء متعلَّمة بعد. عندما تؤكد \"تقصد X؟\" يحفظ المساعد التهجئة لاستعمالها لاحقاً.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(aliases.entries.toList(), key = { it.key }) { e ->
                    AppRowSurface(RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${e.key}  ←  ${e.value}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { assistant.removeAlias(e.key) }) { Icon(Icons.Filled.Close, contentDescription = "حذف") }
                        }
                    }
                }
                item { AppPillButton("مسح كل الأسماء البديلة", { confirmAliases = true }, Modifier.fillMaxWidth(), tonal = true, height = 46.dp) }
            }
            item { Text("القاموس الشخصي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPillButton("تصدير", {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("dictionary", assistant.exportDictionary()))
                        Toast.makeText(context, "نُسخ القاموس إلى الحافظة", Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f), tonal = true, height = 46.dp)
                    AppPillButton("استيراد من الحافظة", {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val t = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        val n = if (t.isBlank()) 0 else assistant.importDictionary(t)
                        Toast.makeText(context, "تم استيراد $n اسماً", Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f), tonal = true, height = 46.dp)
                }
            }
            item { Text("جمل ما فهمها المساعد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            if (unheard.isEmpty()) {
                item { Text("لا توجد جمل بعد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(unheard.reversed().take(30), key = { it }) { t ->
                    AppRowSurface(RoundedCornerShape(18.dp)) { Text(t, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium) }
                }
                item { AppPillButton("مسح هذا السجل", { assistant.clearUnheard() }, Modifier.fillMaxWidth(), tonal = true, height = 46.dp) }
            }
            item { AppPillButton("مسح سجل المحادثة", { confirmHistory = true }, Modifier.fillMaxWidth().padding(top = 6.dp), tonal = true, height = 46.dp) }
        }
    }

    if (confirmAliases) ConfirmDialog("مسح الأسماء البديلة", "سيتوقف المساعد عن استعمال التهجئات المتعلَّمة.", { assistant.clearAliases(); confirmAliases = false }) { confirmAliases = false }
    if (confirmHistory) ConfirmDialog("مسح سجل المحادثة", "سيُحذف السجل من هذا الجهاز فقط. بياناتك لا تتأثر.", { assistant.clearHistory(); confirmHistory = false }) { confirmHistory = false }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    AppRowSurface(RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("مسح") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
