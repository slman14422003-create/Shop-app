package com.salman.herbalencyclopedia.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.BuildConfig
import com.salman.herbalencyclopedia.data.ai.local.LocalLlmEvalRunner
import com.salman.herbalencyclopedia.data.ai.local.LocalLlmManager
import com.salman.herbalencyclopedia.data.ai.local.LocalLlmModels
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.ui.theme.PerformanceMode
import com.salman.herbalencyclopedia.ui.util.tr
import kotlinx.coroutines.launch

private fun formatMb(bytes: Long): String = "%.0f".format(bytes / (1024.0 * 1024.0))

@Composable
fun LocalAiSettingsSection(performanceMode: PerformanceMode, isAdmin: Boolean, herbs: List<Herb>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by LocalLlmManager.status.collectAsState()
    val reason = remember(performanceMode, status.enabled) {
        LocalLlmManager.supportReason(context, !performanceMode.isHighQuality)
    }
    val spec = LocalLlmModels.byId(status.modelId)
    var tokenText by remember { mutableStateOf(LocalLlmManager.token()) }
    var evalRunning by remember { mutableStateOf(false) }

    SettingsSection(title = tr("الذكاء المحلي (تجريبي)")) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconBadge(icon = Icons.Filled.AutoAwesome, tint = Color(0xFF2E7D32))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(tr("إعادة صياغة الردود بدون إنترنت"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("نموذج صغير يعمل على جهازك ويعيد صياغة نقاط الموسوعة فقط، ولا يُضمَّن في التطبيق."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (reason != null) {
                Text(tr(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalLlmModels.all.forEach { m ->
                    FilterChip(
                        selected = m.id == status.modelId,
                        onClick = { LocalLlmManager.selectModel(m.id) },
                        label = { Text(m.label) }
                    )
                }
            }
            Text(
                tr("الحجم التقريبي للتنزيل") + ": " + spec.approxSizeMb + " MB",
                style = MaterialTheme.typography.bodySmall
            )
            if (spec.requiresToken) {
                Text(
                    tr("هذا النموذج يتطلب قبول ترخيص Gemma على Hugging Face ثم إدخال رمز وصول (Token) للقراءة."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it; LocalLlmManager.setToken(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Hugging Face Token") }
                )
            }
            when (status.phase) {
                LocalLlmManager.Phase.DOWNLOADING -> {
                    val fraction = if (status.totalBytes > 0) (status.downloadedBytes.toFloat() / status.totalBytes).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        formatMb(status.downloadedBytes) + " / " + formatMb(status.totalBytes) + " MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { LocalLlmManager.cancelDownload() }) { Text(tr("إلغاء التنزيل")) }
                }
                LocalLlmManager.Phase.INSTALLED -> {
                    Text(
                        tr("النموذج محمّل") + ": " + formatMb(status.installedBytes) + " MB",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = reason == null,
                            onClick = { LocalLlmManager.setEnabled(!status.enabled) }
                        ) { Text(if (status.enabled) tr("تعطيل الذكاء المحلي") else tr("تفعيل الذكاء المحلي")) }
                        OutlinedButton(onClick = { LocalLlmManager.deleteModel() }) { Text(tr("حذف النموذج")) }
                    }
                }
                LocalLlmManager.Phase.NOT_INSTALLED -> {
                    Button(
                        enabled = reason == null,
                        onClick = { LocalLlmManager.startDownload() }
                    ) { Text(tr("تفعيل الذكاء المحلي")) }
                }
            }
            status.errorCode?.let { code ->
                val message = when (code) {
                    LocalLlmManager.ERR_OFFLINE -> "لا يوجد اتصال بالإنترنت"
                    LocalLlmManager.ERR_AUTH -> "رُفض الوصول: تحقق من الرمز وقبول الترخيص على Hugging Face"
                    LocalLlmManager.ERR_SPACE -> "المساحة غير كافية على الجهاز"
                    LocalLlmManager.ERR_NETWORK -> "انقطع التنزيل، أعد المحاولة وسيُكمل من حيث توقف"
                    LocalLlmManager.ERR_SIZE -> "الملف المحمّل غير مكتمل"
                    else -> "حدث خطأ غير متوقع"
                }
                Text(tr(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (BuildConfig.HAS_ADMIN && isAdmin && status.phase == LocalLlmManager.Phase.INSTALLED) {
                OutlinedButton(
                    enabled = !evalRunning && herbs.isNotEmpty(),
                    onClick = {
                        evalRunning = true
                        scope.launch {
                            val report = LocalLlmEvalRunner.run(context, herbs)
                            evalRunning = false
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                ) { Text(if (evalRunning) tr("جارٍ الاختبار...") else tr("اختبار مقارنة النماذج (30 سؤالاً)")) }
            }
        }
    }
}
