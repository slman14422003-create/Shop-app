package com.shopmanager.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.updates.ApkDownloader
import com.shopmanager.app.data.updates.AppVersion
import com.shopmanager.app.data.updates.UpdateDownloadPhase
import com.shopmanager.app.data.updates.UpdateDownloadService
import com.shopmanager.app.data.updates.UpdateDownloadState
import com.shopmanager.app.data.updates.UpdateManifest
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.GlassAlertDialog
import java.io.File

/**
 * مربع التحديث الاختياري (الحالة الطبيعية): "تحديث الآن" أو "لاحقاً".
 * شاشة [ForceUpdateScreen] لا تظهر إلا إذا وُضعت علامة الإجبار في إصدار GitHub.
 */
@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    currentVersion: AppVersion,
    onLater: () -> Unit
) {
    val context = LocalContext.current
    var startedHere by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    val phase by UpdateDownloadState.phase.collectAsState()
    val isDownloading = phase is UpdateDownloadPhase.InProgress
    val percent = (phase as? UpdateDownloadPhase.InProgress)?.percent ?: 0

    fun tryInstall(file: File) {
        if (ApkDownloader.canInstallPackages(context)) {
            ApkDownloader.install(context, file)
            UpdateDownloadState.reset()
            onLater()
        } else {
            needsInstallPermission = true
        }
    }

    LaunchedEffect(Unit) {
        UpdateDownloadState.phase.collect { p ->
            // نتعامل فقط مع تنزيل بدأ من هذا المربع حتى لا يتكرر التثبيت مع شاشة الإعدادات.
            if (!startedHere) return@collect
            when (p) {
                is UpdateDownloadPhase.Done -> {
                    errorMessage = null
                    downloadedFile = p.file
                    tryInstall(p.file)
                }
                is UpdateDownloadPhase.Error -> errorMessage = p.message
                else -> Unit
            }
        }
    }

    GlassAlertDialog(
        onDismissRequest = { if (!isDownloading) onLater() },
        title = { Text("تحديث جديد متاح", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "الإصدار الجديد: ${manifest.versionName}   •   إصدارك الحالي: ${currentVersion.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val notes = manifest.notes.trim()
                if (notes.isNotBlank()) {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    Text("$percent%", style = MaterialTheme.typography.labelSmall)
                }
                errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (needsInstallPermission) {
                    Text(
                        "لإتمام التثبيت، فعّل \"السماح من هذا المصدر\" لهذا التطبيق ثم عد إلى هنا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (needsInstallPermission) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPillButton(
                        label = "حاول مرة أخرى",
                        tonal = true,
                        onClick = {
                            needsInstallPermission = false
                            downloadedFile?.let { tryInstall(it) }
                        }
                    )
                    AppPillButton(
                        label = "فتح الإعدادات",
                        onClick = { context.startActivity(ApkDownloader.unknownSourcesSettingsIntent(context)) }
                    )
                }
            } else {
                AppPillButton(
                    label = if (isDownloading) "جاري التنزيل..." else "تحديث الآن",
                    enabled = !isDownloading,
                    onClick = {
                        errorMessage = null
                        startedHere = true
                        UpdateDownloadService.start(context, manifest)
                    }
                )
            }
        },
        dismissButton = if (isDownloading) null else {
            { AppPillButton(label = "لاحقاً", tonal = true, onClick = onLater) }
        }
    )
}
