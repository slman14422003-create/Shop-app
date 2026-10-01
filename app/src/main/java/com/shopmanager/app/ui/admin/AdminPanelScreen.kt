package com.shopmanager.app.ui.admin

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.updates.AppVersionInfo
import com.shopmanager.app.data.updates.UpdateCheckResult
import com.shopmanager.app.data.updates.UpdateChecker
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSectionGap
import com.shopmanager.app.ui.common.AppSectionTitle
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.ClaudeOrangeDark
import com.shopmanager.app.ui.theme.ClaudeOrangeLight
import com.shopmanager.app.ui.theme.LocalIsDarkTheme
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * لوحة المطوّر (سرية): تُفتح فقط من زر القائمة الجانبية بعد التحقق بالبصمة
 * (أو كلمة المرور كبديل) — راجع MainActivity. تضبط رابط التحديثات الذي يقرأ منه
 * زر "تحقق من التحديثات" في الإعدادات، وتعطي قراءة سريعة لحالة المزامنة
 * ومعلومات الجهاز دون الحاجة لـ Android Studio أو logcat.
 *
 * التصميم بلغة الإعدادات نفسها: عناوين أقسام رمادية صغيرة، وصفوف مسطّحة
 * متلاصقة (أول/وسط/أخير) على surfaceContainerHigh، وأزرار حبّة بعرض كامل،
 * ورسائل الحالة كسطور رمادية صغيرة تحت المجموعة بدل بطاقات ضخمة بنصوص طويلة.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminPanelScreen(
    onBack: () -> Unit,
    debtsViewModel: DebtsViewModel? = null,
    materialsViewModel: MaterialsViewModel? = null
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val appVersion = remember { AppVersionInfo.current(context) }
    val performanceTier = LocalPerformanceTier.current
    val semantic = LocalSemanticColors.current

    var manifestUrl by remember { mutableStateOf(settings.updateManifestUrl) }
    var forceUpdateEnabled by remember { mutableStateOf(settings.forceUpdateEnabled) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    // (نجح؟، النص) — اللون يتبع النتيجة بدل الاعتماد على رموز إيموجي.
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }

    val debtsSyncError = debtsViewModel?.hasSyncError?.collectAsState(initial = false)?.value ?: false
    val materialsSyncError = materialsViewModel?.hasSyncError?.collectAsState(initial = false)?.value ?: false

    val lastCheckLabel = remember(settings.lastUpdateCheckAt) {
        val ts = settings.lastUpdateCheckAt
        if (ts == 0L) "لم يتم التحقق بعد"
        // 12-hour clock — "a" renders as ص/م in Arabic locale.
        else SimpleDateFormat("yyyy/MM/dd — h:mm a", Locale("ar")).format(java.util.Date(ts))
    }

    fun testManifestNow() {
        isTesting = true
        testResult = null
        scope.launch {
            testResult = when (val result = UpdateChecker.check(context, manifestUrl)) {
                is UpdateCheckResult.UpToDate ->
                    true to "الرابط يعمل — لا يوجد تحديث أحدث من (${result.current.name})"
                is UpdateCheckResult.UpdateAvailable ->
                    true to ("الرابط يعمل — يوجد تحديث: ${result.manifest.versionName} (كود ${result.manifest.versionCode})\n" +
                        "رابط APK: ${result.manifest.apkUrl}")
                is UpdateCheckResult.Failed -> false to "فشل: ${result.reason}"
            }
            isTesting = false
        }
    }

    fun systemInfoText(): String = buildString {
        appendLine("إدارة المحل — معلومات تشخيصية")
        appendLine("الإصدار: ${appVersion.name} (كود ${appVersion.code})")
        appendLine("الحزمة: ${context.packageName}")
        appendLine("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("نظام أندرويد: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("مستوى الأداء: ${performanceTier}")
        appendLine("تفضيل الأداء: ${settings.performanceMode}")
        appendLine("مزامنة الديون: ${if (debtsSyncError) "خطأ" else "سليمة"}")
        appendLine("مزامنة المواد: ${if (materialsSyncError) "خطأ" else "سليمة"}")
        appendLine("رابط التحديثات: ${manifestUrl.ifBlank { "غير معيّن" }}")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ScreenTopBar(
                title = "لوحة المطوّر",
                navigation = {
                    ScreenIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = AppScreenPadding, end = AppScreenPadding, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(AppSectionGap)
        ) {
            AppFootnote("هذه الشاشة مخصصة لتطوير التطبيق فقط — المستخدم العادي لا يصل إليها.")

            // ------------------------------------------------------------------
            // رابط التحديثات
            // ------------------------------------------------------------------
            AdminSection(title = "رابط التحديثات (GitHub Release)", icon = Icons.Default.Link) {
                AdminGroup(
                    { shape ->
                        SwitchRow(
                            shape = shape,
                            title = "السماح بالتحديث الإجباري",
                            subtitle = "التحديث اختياري افتراضياً (تحديث الآن / لاحقاً). يصبح إجبارياً فقط إذا وضعت العلامة [force] في وصف الإصدار على GitHub وكان هذا الخيار مفعّلاً. عند الإيقاف يُعامَل أي تحديث كاختياري.",
                            checked = forceUpdateEnabled,
                            onCheckedChange = {
                                forceUpdateEnabled = it
                                settings.forceUpdateEnabled = it
                                savedMessage = if (it) "تم تفعيل التحديث الإجباري" else "تم إيقاف التحديث الإجباري"
                            }
                        )
                    },
                    { shape ->
                        AppTextField(
                            value = manifestUrl,
                            onValueChange = { manifestUrl = it; savedMessage = null },
                            label = "رابط التحديثات",
                            placeholder = "https://...",
                            singleLine = true,
                            shape = shape,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )

                AppFootnote("آخر تحقق ناجح: $lastCheckLabel")
                AppFootnote(
                    "الرابط معبّى تلقائياً من مستودع GitHub وقت البناء على CI، فلا حاجة للصقه يدوياً. " +
                        "زر \"تحقق من التحديثات\" بالإعدادات يقرأ منه ويحمّل shop-manager-release.apk من أحدث إصدار منشور. " +
                        "يمكن استبداله برابط JSON مخصص بالشكل: " +
                        "{\"versionCode\":2,\"versionName\":\"1.1.0\",\"apkUrl\":\"...\",\"notes\":\"...\"}"
                )

                AppPillButton(
                    label = "حفظ الرابط",
                    onClick = {
                        settings.updateManifestUrl = manifestUrl
                        savedMessage = "تم الحفظ"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppPillButton(
                        label = "الرابط التلقائي",
                        tonal = true,
                        onClick = {
                            settings.resetUpdateManifestUrlToDefault()
                            manifestUrl = settings.updateManifestUrl
                            savedMessage = "تمت إعادة الرابط التلقائي"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    AppPillButton(
                        label = if (isTesting) "جارٍ الاختبار..." else "اختبار الآن",
                        tonal = true,
                        enabled = !isTesting,
                        onClick = { testManifestNow() },
                        modifier = Modifier.weight(1f)
                    )
                }

                savedMessage?.let { StatusLine(it, semantic.success) }
                testResult?.let { (ok, text) ->
                    StatusLine(text, if (ok) semantic.success else semantic.danger)
                }
            }

            // ------------------------------------------------------------------
            // حالة الخادم والمزامنة
            // ------------------------------------------------------------------
            AdminSection(title = "حالة الخادم والمزامنة", icon = Icons.Default.CloudSync) {
                AdminGroup(
                    { shape ->
                        StatusRow("مزامنة الديون", ok = !debtsSyncError, shape = shape)
                    },
                    { shape ->
                        StatusRow("مزامنة المواد", ok = !materialsSyncError, shape = shape)
                    }
                )
                AppPillButton(
                    label = "إعادة المزامنة",
                    tonal = true,
                    onClick = {
                        debtsViewModel?.refresh()
                        materialsViewModel?.refresh()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ------------------------------------------------------------------
            // معلومات النظام
            // ------------------------------------------------------------------
            AdminSection(title = "معلومات النظام", icon = Icons.Default.PhoneAndroid) {
                val rows = listOf(
                    "الإصدار" to "${appVersion.name} (كود ${appVersion.code})",
                    "الحزمة" to context.packageName,
                    "الجهاز" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "أندرويد" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "مستوى الأداء المكتشف" to performanceTier.toString(),
                    "تفضيل الأداء" to performanceModeLabel(settings.performanceMode)
                )
                AdminGroup(
                    *rows.map { (label, value) ->
                        val row: @Composable (Shape) -> Unit = { shape -> InfoRow(label, value, shape) }
                        row
                    }.toTypedArray()
                )
                AppPillButton(
                    label = "نسخ معلومات التشخيص",
                    tonal = true,
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        clipboard.setText(AnnotatedString(systemInfoText()))
                        copiedMessage = "تم نسخ المعلومات"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                copiedMessage?.let { StatusLine(it, semantic.success) }
            }

            // ------------------------------------------------------------------
            // صيانة
            // ------------------------------------------------------------------
            AdminSection(title = "صيانة", icon = Icons.Default.CleaningServices) {
                AdminGroup(
                    { shape ->
                        ActionRow(
                            shape = shape,
                            title = "مسح الذاكرة المؤقتة",
                            subtitle = "تنزيلات التحديث السابقة وصور المشاركة المؤقتة — لا يمسح بيانات الديون أو المواد.",
                            busy = isClearingCache,
                            onClick = {
                                isClearingCache = true
                                cacheClearedMessage = null
                                scope.launch {
                                    val cleared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        context.cacheDir?.deleteRecursively() ?: false
                                    }
                                    cacheClearedMessage = if (cleared) "تم مسح الذاكرة المؤقتة" else "لا يوجد شيء لمسحه"
                                    isClearingCache = false
                                }
                            }
                        )
                    }
                )
                cacheClearedMessage?.let { StatusLine(it, semantic.success) }
            }

            Spacer(Modifier.size(8.dp))
        }
    }
}

private fun performanceModeLabel(mode: PerformanceMode): String = when (mode) {
    PerformanceMode.AUTO -> "تلقائي"
    PerformanceMode.HIGH -> "مرتفع"
    PerformanceMode.LOW -> "منخفض"
}

// ============================================================================
// مكوّنات اللوحة (بنفس منطق SettingsGroup/SettingsRow دون ربطها بالإعدادات)
// ============================================================================

/** قسم: عنوان رمادي صغير بأيقونة، ثم محتوى بمسافات المجموعات الموحّدة. */
@Composable
private fun AdminSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

/** يجمع الصفوف متلاصقة: الأول فقط بزوايا علوية كبيرة والأخير فقط بزوايا سفلية كبيرة. */
@Composable
private fun AdminGroup(vararg rows: @Composable (Shape) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppGroupGap)) {
        val last = rows.lastIndex
        rows.forEachIndexed { index, row -> row(groupedRowShape(index, last)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String, shape: Shape) {
    AppRowSurface(shape = shape) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
        }
    }
}

/** صف حالة: نقطة ملوّنة + "سليمة"/"يوجد خطأ" في النهاية. */
@Composable
private fun StatusRow(label: String, ok: Boolean, shape: Shape) {
    val semantic = LocalSemanticColors.current
    val color = if (ok) semantic.success else semantic.danger
    AppRowSurface(shape = shape) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (ok) "سليمة" else "يوجد خطأ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

/** صف مفتاح: الصف كله قابل للّمس، والمفتاح للعرض فقط (نفس ألوان مفاتيح الإعدادات). */
@Composable
private fun SwitchRow(
    shape: Shape,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accent = if (LocalIsDarkTheme.current) ClaudeOrangeDark else ClaudeOrangeLight
    AppRowSurface(
        shape = shape,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(!checked)
        }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

/** صف إجراء قابل للّمس مع عنوان ووصف ومؤشر انتظار اختياري. */
@Composable
private fun ActionRow(
    shape: Shape,
    title: String,
    subtitle: String,
    busy: Boolean,
    onClick: () -> Unit
) {
    AppRowSurface(shape = shape, onClick = { if (!busy) onClick() }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(if (busy) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (busy) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** رسالة حالة صغيرة ببطاقة مسطحة ونقطة ملوّنة (نجاح/فشل). */
@Composable
private fun StatusLine(text: String, color: Color) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
