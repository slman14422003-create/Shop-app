package com.shopmanager.app.assistant.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.voice.VoiceInput
import com.shopmanager.app.assistant.voice.VoiceUiState

@Composable
fun VoiceDownloadDialog(voice: VoiceInput) {
    val st by voice.uiState.collectAsState()
    val settings by voice.settings.collectAsState()
    when (val s = st) {
        is VoiceUiState.NeedsModel -> {
            val size = if (s.sizeBytes > 0L) formatBytes(s.sizeBytes) else "حجم صغير"
            AlertDialog(
                onDismissRequest = { voice.dismissDownload() },
                title = { Text("تنزيل نموذج الصوت") },
                text = {
                    Text(
                        "لتشغيل الأوامر الصوتية بدون إنترنت لازم ننزّل نموذجاً مرة واحدة ($size). " +
                            if (s.onWifi) "أنت على Wi‑Fi." else if (settings.allowMobileData) "سيُستعمل اتصال الجوال." else "سيبدأ التنزيل عند توفر Wi‑Fi."
                    )
                },
                confirmButton = { TextButton(onClick = { voice.confirmDownload(settings.allowMobileData) }) { Text("تنزيل") } },
                dismissButton = {
                    Column {
                        if (!s.onWifi && !settings.allowMobileData) TextButton(onClick = { voice.confirmDownload(true) }) { Text("تنزيل بالبيانات") }
                        TextButton(onClick = { voice.dismissDownload() }) { Text("لاحقاً") }
                    }
                }
            )
        }
        else -> {}
    }
}

@Composable
fun VoiceDownloadBar(voice: VoiceInput, modifier: Modifier = Modifier) {
    val st by voice.uiState.collectAsState()
    val s = st as? VoiceUiState.Downloading ?: return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (s.totalBytes > 0L) LinearProgressIndicator(progress = { (s.doneBytes.toFloat() / s.totalBytes.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (s.totalBytes > 0L) formatBytes(s.doneBytes) + " / " + formatBytes(s.totalBytes) else "بانتظار الاتصال")
            TextButton(onClick = { voice.cancelDownload() }) { Text("إلغاء") }
        }
    }
}
