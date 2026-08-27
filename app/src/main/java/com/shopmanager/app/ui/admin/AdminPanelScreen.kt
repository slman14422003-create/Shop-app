package com.shopmanager.app.ui.admin

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.updates.AppVersionInfo
import com.shopmanager.app.data.updates.UpdateCheckResult
import com.shopmanager.app.data.updates.UpdateChecker
import com.shopmanager.app.ui.common.GlassIconButton
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * لوحة المسؤول السرية: reachable only from the small hidden button on the
 * dashboard header + the 1442 PIN dialog (see DashboardScreen). Not linked
 * from anywhere else in the app's normal navigation — a regular user never
 * sees this screen or even knows the route exists. This is purely a tool
 * for whoever develops/maintains this app to configure where the in-app
 * "تحقق من التحديثات" button (Settings) checks, and to get a fast read on
 * whether the server side of things is actually working when a real user
 * reports a problem, without needing Android Studio/logcat/adb attached.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    var manifestUrl by remember { mutableStateOf(settings.updateManifestUrl) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    val debtsSyncError = debtsViewModel?.hasSyncError?.collectAsState(initial = false)?.value ?: false
    val materialsSyncError = materialsViewModel?.hasSyncError?.collectAsState(initial = false)?.value ?: false

    val lastCheckLabel = remember(settings.lastUpdateCheckAt) {
        val ts = settings.lastUpdateCheckAt
        if (ts == 0L) "لم يتم التحقق بعد"
        else SimpleDateFormat("yyyy/MM/dd — HH:mm", Locale("ar")).format(java.util.Date(ts))
    }

    fun testManifestNow() {
        isTesting = true
        testResult = null
        scope.launch {
            testResult = when (val result = UpdateChecker.check(context, manifestUrl)) {
                is UpdateCheckResult.UpToDate ->
                    "✅ الرابط يعمل — لا يوجد تحديث أحدث من (${result.current.name})"
                is UpdateCheckResult.UpdateAvailable ->
                    "✅ الرابط يعمل — يوجد تحديث: ${result.manifest.versionName} (كود ${result.manifest.versionCode})\n" +
                        "رابط APK: ${result.manifest.apkUrl}"
                is UpdateCheckResult.Failed -> "❌ فشل: ${result.reason}"
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
        topBar = {
            TopAppBar(
                title = { Text("لوحة المطوّر", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                        size = 36.dp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = BrandOnGradient,
                    navigationIconContentColor = BrandOnGradient
                ),
                modifier = Modifier.liquidGlassSurface(androidx.compose.ui.graphics.RectangleShape)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "هذه الشاشة مخصصة لتطوير التطبيق فقط — المستخدم العادي لا يصل إليها.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // رابط التحديثات
            AdminSection(title = "رابط التحديثات (JSON)", icon = Icons.Default.Link) {
                Text(
                    "الرابط الذي يقرأ منه زر \"تحقق من التحديثات\" العادي في الإعدادات. يجب أن يرجع JSON بالشكل:\n" +
                        "{\"versionCode\":2,\"versionName\":\"1.1.0\",\"apkUrl\":\"...\",\"notes\":\"...\"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manifestUrl,
                    onValueChange = { manifestUrl = it; savedMessage = null },
                    label = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "آخر تحقق ناجح: $lastCheckLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        settings.updateManifestUrl = manifestUrl
                        savedMessage = "تم الحفظ ✅"
                    }) { Text("حفظ الرابط") }
                    OutlinedButton(onClick = { testManifestNow() }, enabled = !isTesting) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("اختبار الآن")
                    }
                }
                savedMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }

            // حالة الخادم والمزامنة
            AdminSection(title = "حالة الخادم والمزامنة", icon = Icons.Default.CloudSync) {
                DiagnosticRow("مزامنة الديون", if (debtsSyncError) "❌ يوجد خطأ" else "✅ سليمة")
                DiagnosticRow("مزامنة المواد", if (materialsSyncError) "❌ يوجد خطأ" else "✅ سليمة")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        debtsViewModel?.refresh()
                        materialsViewModel?.refresh()
                    }) { Text("إعادة المزامنة") }
                }
            }

            // معلومات النظام
            AdminSection(title = "معلومات النظام", icon = Icons.Default.PhoneAndroid) {
                DiagnosticRow("الإصدار", "${appVersion.name} (كود ${appVersion.code})")
                DiagnosticRow("الحزمة", context.packageName)
                DiagnosticRow("الجهاز", "${Build.MANUFACTURER} ${Build.MODEL}")
                DiagnosticRow("أندرويد", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                DiagnosticRow("مستوى الأداء المكتشف", performanceTier.toString())
                DiagnosticRow("تفضيل الأداء", performanceModeLabel(settings.performanceMode))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(systemInfoText())) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نسخ معلومات التشخيص")
                }
            }

            // صيانة
            AdminSection(title = "صيانة", icon = Icons.Default.CleaningServices) {
                Text(
                    "مسح الملفات المؤقتة (تنزيلات التحديث السابقة، صور المشاركة المؤقتة) — لا يمسح بيانات الديون أو المواد.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    enabled = !isClearingCache,
                    onClick = {
                        isClearingCache = true
                        cacheClearedMessage = null
                        scope.launch {
                            val cleared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                context.cacheDir?.deleteRecursively() ?: false
                            }
                            cacheClearedMessage = if (cleared) "تم مسح الذاكرة المؤقتة ✅" else "لا يوجد شيء لمسحه"
                            isClearingCache = false
                        }
                    }
                ) {
                    if (isClearingCache) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("مسح الذاكرة المؤقتة")
                }
                cacheClearedMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun performanceModeLabel(mode: PerformanceMode): String = when (mode) {
    PerformanceMode.AUTO -> "تلقائي"
    PerformanceMode.HIGH -> "مرتفع"
    PerformanceMode.LOW -> "منخفض"
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdminSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
