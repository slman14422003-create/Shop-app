package com.shopmanager.app.assistant.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.voice.VoiceEngineKind
import com.shopmanager.app.assistant.voice.VoiceInput
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface

@Composable
fun VoiceSettingsSection(voice: VoiceInput, onOpenModels: () -> Unit) {
    val s by voice.settings.collectAsState()
    val active by voice.activeEngine.collectAsState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الإدخال الصوتي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        AppRowSurface(RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("محرك التعرف", style = MaterialTheme.typography.titleSmall)
                val options = listOf(
                    VoiceEngineKind.AUTO to "تلقائي",
                    VoiceEngineKind.SYSTEM to "النظام",
                    VoiceEngineKind.VOSK to "Vosk",
                    VoiceEngineKind.WHISPER to "Whisper"
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (k, label) ->
                        AppPillButton(
                            label, { voice.updateSettings(s.copy(preferred = k)) },
                            Modifier.weight(1f), tonal = s.preferred != k, height = 40.dp
                        )
                    }
                }
                active?.let {
                    Text("آخر محرك استُعمل: " + engineLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        VoiceSwitchRow("التنزيل عبر بيانات الجوال", "افتراضياً يُنزَّل النموذج على Wi‑Fi فقط", s.allowMobileData) { voice.updateSettings(s.copy(allowMobileData = it)) }
        VoiceSwitchRow("نطق الردود", "يقول المساعد \"فهمت: ...\" بصوت عربي", s.speakReplies) { voice.updateSettings(s.copy(speakReplies = it)) }
        VoiceSwitchRow("إرسال تلقائي", "يرسل الجملة بعد السكوت مباشرة", s.autoSend) { voice.updateSettings(s.copy(autoSend = it)) }
        AppPillButton("إدارة نماذج الصوت", onOpenModels, Modifier.fillMaxWidth(), tonal = true, height = 46.dp)
    }
}

internal fun engineLabel(k: VoiceEngineKind): String = when (k) {
    VoiceEngineKind.AUTO -> "تلقائي"
    VoiceEngineKind.SYSTEM -> "النظام"
    VoiceEngineKind.VOSK -> "Vosk"
    VoiceEngineKind.WHISPER -> "Whisper"
}

@Composable
internal fun VoiceSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
