package com.shopmanager.app.assistant.voice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.voice.DownloadState
import com.shopmanager.app.assistant.voice.ModelInfo
import com.shopmanager.app.assistant.voice.VoiceInput
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar

@Composable
fun ModelManagementScreen(voice: VoiceInput, onBack: () -> Unit) {
    val models by voice.models.collectAsState()
    val dl by voice.download.collectAsState()
    val settings by voice.settings.collectAsState()
    LaunchedEffect(dl) { if (dl is DownloadState.Done) voice.preload(com.shopmanager.app.assistant.engine.VocabularyList(emptyList())) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "نماذج الصوت",
            navigation = { ScreenIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack) }
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "التنزيل مرة واحدة فقط ويُحفظ داخل التطبيق. المعالجة كلها على الجهاز ولا يُحفظ أي صوت.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(models, key = { it.id }) { m -> ModelRow(m, dl, voice, settings.allowMobileData) }
            item { VoiceSwitchRow("التنزيل عبر بيانات الجوال", "إيقافه يعني الانتظار حتى يتوفر Wi‑Fi", settings.allowMobileData) { voice.updateSettings(settings.copy(allowMobileData = it)) } }
        }
    }
}

@Composable
private fun ModelRow(m: ModelInfo, dl: DownloadState, voice: VoiceInput, allowMobile: Boolean) {
    val mine = when (dl) {
        is DownloadState.Running -> dl.modelId == m.id
        is DownloadState.Waiting -> dl.modelId == m.id
        is DownloadState.Verifying -> dl.modelId == m.id
        else -> false
    }
    AppRowSurface(RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall)
            val sizeText = if (m.sizeBytes > 0L) formatBytes(m.sizeBytes) else "الحجم غير معروف"
            Text(
                if (m.installed) "منزَّل • " + formatBytes(m.installedBytes) else "غير منزَّل • $sizeText",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mine) {
                when (dl) {
                    is DownloadState.Running -> {
                        val p = if (dl.totalBytes > 0L) (dl.doneBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        if (dl.totalBytes > 0L) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(formatBytes(dl.doneBytes) + (if (dl.totalBytes > 0L) " / " + formatBytes(dl.totalBytes) else "") + (if (dl.resumed) " • استئناف" else ""), style = MaterialTheme.typography.bodySmall)
                    }
                    is DownloadState.Waiting -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(if (allowMobile) "بانتظار اتصال" else "بانتظار Wi‑Fi", style = MaterialTheme.typography.bodySmall)
                    }
                    is DownloadState.Verifying -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("جاري التحقق والتثبيت", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {}
                }
                AppPillButton("إلغاء", { voice.cancelDownload() }, Modifier.fillMaxWidth(), tonal = true, height = 42.dp)
            } else {
                if (dl is DownloadState.Failed && dl.modelId == m.id) {
                    Text(dl.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (m.installed) AppPillButton("حذف", { voice.deleteModel(m.id) }, Modifier.fillMaxWidth(), tonal = true, height = 42.dp)
                else AppPillButton("تنزيل", { voice.downloadModel(m.id) }, Modifier.fillMaxWidth(), height = 42.dp)
            }
        }
    }
}

internal fun formatBytes(b: Long): String {
    if (b <= 0L) return "0"
    val mb = b / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb) else String.format(java.util.Locale.US, "%d KB", b / 1024L)
}
