package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.AppUpdateConfig
import com.salman.herbalencyclopedia.ui.UpdateCheckState
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.util.ResponsiveScreenContent
import com.salman.herbalencyclopedia.ui.util.rememberWindowSizeInfo
import kotlinx.coroutines.launch
import com.salman.herbalencyclopedia.ui.util.tr

/**
 * Lets the admin control the in-app update feature without shipping a new
 * build: which GitHub repo to watch for Releases, an optional forced
 * download link / version / release notes, and an optional "mandatory
 * update below this versionCode" threshold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUpdateScreen(
    config: AppUpdateConfig,
    testState: UpdateCheckState = UpdateCheckState.Idle,
    onBack: () -> Unit,
    onSave: (AppUpdateConfig, (Boolean, String?) -> Unit) -> Unit,
    onTestNow: (android.content.Context, AppUpdateConfig) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var repo by remember(config) { mutableStateOf(config.githubRepo) }
    var versionName by remember(config) { mutableStateOf(config.overrideVersionName ?: "") }
    var notes by remember(config) { mutableStateOf(config.releaseNotesOverride ?: "") }
    var minVersionCode by remember(config) {
        mutableStateOf(if (config.minVersionCode > 0) config.minVersionCode.toString() else "")
    }
    var useProxyFallback by remember(config) { mutableStateOf(config.useProxyFallback) }
    var customProxyBaseUrl by remember(config) { mutableStateOf(config.customProxyBaseUrl ?: "") }
    var updateSourceUrl by remember(config) { mutableStateOf(config.updateSourceUrl ?: "") }
    var saving by remember { mutableStateOf(false) }

    val msgSaved = tr("تم الحفظ")
    val msgSaveError = tr("حدث خطأ أثناء الحفظ")

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // الإعدادات كما هي مكتوبة في الحقول الآن — تُستخدم للاختبار الفوري دون
    // اشتراط حفظها في Firestore أولاً (انظر onTestNow أدناه).
    fun currentFieldsAsConfig() = AppUpdateConfig(
        enabled = enabled,
        githubRepo = repo.trim(),
        overrideVersionName = versionName.trim().ifBlank { null },
        releaseNotesOverride = notes.trim().ifBlank { null },
        minVersionCode = minVersionCode.toIntOrNull() ?: 0,
        useProxyFallback = useProxyFallback,
        customProxyBaseUrl = customProxyBaseUrl.trim().ifBlank { null },
        updateSourceUrl = updateSourceUrl.trim().ifBlank { null }
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(tr("إعدادات التحديثات")) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ResponsiveScreenContent(windowInfo = rememberWindowSizeInfo(), modifier = Modifier.padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    tr("يتحقق التطبيق تلقائياً من آخر إصدار (Release) على GitHub، ثم يفتح رابط تحميل ملف APK مباشرة عند توفر تحديث ليقوم المستخدم بتنزيله وتثبيته يدوياً."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("تفعيل التحقق من التحديثات"), fontWeight = FontWeight.SemiBold)
                            Text(
                                tr("عند الإيقاف لن يظهر أي زر تحديث للمستخدمين"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = updateSourceUrl,
                    onValueChange = { updateSourceUrl = it },
                    label = { Text(tr("رابط Worker للتحديثات (اختياري)")) },
                    placeholder = { Text("https://my-worker.example.workers.dev") },
                    supportingText = {
                        Text(
                            tr("إن تُرك فارغاً يتحقق التطبيق من GitHub مباشرة كما في الأسفل. إن مُلئ، يصبح هذا الرابط هو المصدر الوحيد: يقرأ التطبيق /api/latest منه للنسخة، ويحمّل الملف من /download — ولا يتصل بـ GitHub إطلاقاً (نفس رابط صفحة التحميل).")
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text(tr("مستودع GitHub")) },
                    placeholder = { Text("owner/repo") },
                    supportingText = { Text(tr("يُقرأ منه أحدث Release تلقائياً، مثال: slman14422003-create/Harbs — يُتجاهل عند تعبئة رابط الـ Worker أعلاه")) },
                    enabled = updateSourceUrl.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = versionName,
                    onValueChange = { versionName = it },
                    label = { Text(tr("رقم إصدار مخصّص (اختياري)")) },
                    supportingText = { Text(tr("إن تُرك فارغاً يُؤخذ رقم الإصدار من tag الإصدار على GitHub")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(tr("ملاحظات التحديث (اختياري)")) },
                    supportingText = { Text(tr("إن تُركت فارغة يظهر للمستخدم نص ثابت: \"تم تحديث الأخطاء وإدخال تحسينات جديدة.\"")) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("استخدام بروكسي عند حجب GitHub"), fontWeight = FontWeight.SemiBold)
                            Text(
                                tr("إذا فشل الوصول المباشر لـ GitHub (كما في بعض الدول)، يعيد التطبيق المحاولة عبر مرآة بروكسي تلقائياً، دون الحاجة لـ VPN"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useProxyFallback, onCheckedChange = { useProxyFallback = it })
                    }
                }
            }
            if (useProxyFallback) {
                item {
                    OutlinedTextField(
                        value = customProxyBaseUrl,
                        onValueChange = { customProxyBaseUrl = it },
                        label = { Text(tr("رابط بروكسي مخصّص (اختياري)")) },
                        placeholder = { Text("https://my-proxy.example.com/") },
                        supportingText = { Text(tr("إن تُرك فارغاً تُستخدم مرايا عامة معروفة تلقائياً كخطة بديلة")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = minVersionCode,
                    onValueChange = { v -> minVersionCode = v.filter { it.isDigit() } },
                    label = { Text(tr("حد التحديث الإجباري (اختياري)")) },
                    supportingText = { Text(tr("أي نسخة مثبّتة أقدم من رقم الإصدار الداخلي هذا يُعتبر تحديثها إجبارياً")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedButton(
                    enabled = testState != UpdateCheckState.Checking && (repo.isNotBlank() || updateSourceUrl.isNotBlank()),
                    onClick = { onTestNow(context, currentFieldsAsConfig()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (testState == UpdateCheckState.Checking) tr("جارٍ الاختبار...") else tr("تحقق الآن بهذه الإعدادات"))
                }
            }
            when (val state = testState) {
                is UpdateCheckState.Available -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr("الإعدادات تعمل: تم العثور على إصدار v${state.info.versionName}") +
                                (if (state.info.apkUrl != null) tr(" (رابط APK متوفر).") else tr(" (بدون ملف APK مرفق بالإصدار!).")),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is UpdateCheckState.UpToDate -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("الإعدادات تعمل: تم الاتصال بنجاح ولا يوجد إصدار أحدث من هذا الجهاز حالياً."), style = MaterialTheme.typography.bodySmall)
                    }
                }
                is UpdateCheckState.Error -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {}
            }
            item {
                Button(
                    enabled = !saving && (repo.isNotBlank() || updateSourceUrl.isNotBlank()),
                    onClick = {
                        saving = true
                        val newConfig = currentFieldsAsConfig()
                        onSave(newConfig) { ok, msg ->
                            saving = false
                            notify(msg ?: if (ok) msgSaved else msgSaveError)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) tr("جارٍ الحفظ...") else tr("حفظ الإعدادات"))
                }
            }
        }
        }
    }
}
