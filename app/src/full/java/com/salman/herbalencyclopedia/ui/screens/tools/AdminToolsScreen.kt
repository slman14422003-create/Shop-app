package com.salman.herbalencyclopedia.ui.screens.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.ai.AiConfig
import com.salman.herbalencyclopedia.data.ai.DictionaryLexicon
import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.ai.TrainedExample
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.data.model.UnansweredQuestion
import com.salman.herbalencyclopedia.data.model.WebHerb
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.salman.herbalencyclopedia.ui.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminToolsScreen(
    categories: List<Category>,
    herbs: List<Herb>,
    blends: List<com.salman.herbalencyclopedia.data.model.Blend> = emptyList(),
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddCategory: (String, (Boolean, String?) -> Unit) -> Unit,
    onUpdateCategory: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteCategory: (String, (Boolean, String?) -> Unit) -> Unit,
    onDeleteAllHerbs: ((Boolean, String?) -> Unit) -> Unit,
    onDeleteAllData: ((Boolean, String?) -> Unit) -> Unit,
    onTestConnection: ((Boolean, String?) -> Unit) -> Unit,
    onClearFavorites: () -> Unit,
    onRestoreBackup: (String, (Boolean, String?) -> Unit) -> Unit,
    onMigrateImages: ((Boolean, String?) -> Unit) -> Unit = {},
    onUpdateSettingsClick: () -> Unit,
    // ── إعدادات "سيمو المساعد" (HerbAssistant) — تُمرَّر حيّة من
    // DataStore عبر HerbalNavGraph، وتُعدَّل هنا مباشرة كـ "أدوات مطور" ──
    aiSimilarityThreshold: Float = AiConfig.defaultSimilarityThreshold.toFloat(),
    aiSearchThreshold: Float = AiConfig.defaultSearchThreshold.toFloat(),
    aiExtraStopWords: Set<String> = emptySet(),
    onSetAiSimilarityThreshold: (Float) -> Unit = {},
    onSetAiSearchThreshold: (Float) -> Unit = {},
    onSetAiExtraStopWords: (Set<String>) -> Unit = {},
    onResetAiSettings: () -> Unit = {},
    // ═══ فصل "الحالات المدرَّبة" و"تعلّم سيمو الذاتي" في شاشتين مستقلتين ═══
    // كانتا بطاقتين ضخمتين مدمجتين ضمن قائمة "أدوات الإدارة" الطويلة نفسها
    // (راجع AiTrainingDevTools/AiSelfLearningDevTools أدناه — ما زالتا هنا
    // وتُستخدمان الآن داخل SemoTrainedCasesScreen/SemoSelfLearningScreen)،
    // فتتيه القائمة الرئيسية بينهما وبين بقية الأدوات القصيرة. الآن كل
    // واحدة زر مستقل بنفس مبدأ "إعدادات التحديثات" (AdminUpdate) — يفتح
    // شاشة كاملة خاصة بها بدل الظهور دوماً مدموجتين هنا.
    onOpenTrainedCases: () -> Unit = {},
    onOpenSemoLearning: () -> Unit = {},
    onOpenSemoWeb: () -> Unit = {},
    // ── الوضع الذكي عبر الإنترنت (نموذج Gemini المجاني من Google، بلا بطاقة
    // ائتمان) — راجع توثيق AiConfig.onlineEnabled في HerbAssistant.kt
    // وOnlineAssistant.kt لتفاصيل الآلية. ──
    aiOnlineEnabled: Boolean = AiConfig.defaultOnlineEnabled,
    aiOnlineApiKey: String = "",
    aiOnlineModel: String = AiConfig.defaultOnlineModel,
    aiOnlineBaseUrl: String = AiConfig.defaultOnlineBaseUrl,
    onSetAiOnlineEnabled: (Boolean) -> Unit = {},
    onSetAiOnlineApiKey: (String) -> Unit = {},
    onSetAiOnlineModel: (String) -> Unit = {},
    onSetAiOnlineBaseUrl: (String) -> Unit = {},
    onResetAiOnlineSettings: () -> Unit = {},
    // ── أداة إدمن جديدة بطلب صريح: "كم جهاز يستخدم الموسوعة" — راجع
    // DeviceStatsRepository وAppViewModel.refreshDeviceStats للتفاصيل
    // الكاملة. تُجلَب فقط عند الضغط على زر التحديث هنا (لا تلقائياً عند
    // فتح الشاشة) لتفادي أي قراءة إضافية غير مطلوبة فعلياً.
    deviceStatsLoading: Boolean = false,
    deviceStatsTotal: Long? = null,
    deviceStatsActive7d: Long? = null,
    deviceStatsError: String? = null,
    onRefreshDeviceStats: () -> Unit = {},
    // ── نمو الأجهزة يوماً بيوم — بطاقة إدمن جديدة، راجع AppViewModel.refreshDeviceGrowth ──
    deviceGrowthLoading: Boolean = false,
    deviceGrowth: List<Pair<String, Int>> = emptyList(),
    deviceGrowthError: String? = null,
    onRefreshDeviceGrowth: () -> Unit = {},
    // ── سجل أعطال عن بعد — بطاقة إدمن جديدة، راجع AppViewModel.refreshCrashLogs/clearCrashLogs ──
    crashLogsLoading: Boolean = false,
    crashLogs: List<com.salman.herbalencyclopedia.data.repository.CrashLog> = emptyList(),
    crashLogsError: String? = null,
    onRefreshCrashLogs: () -> Unit = {},
    onClearCrashLogs: ((Boolean, String?) -> Unit) -> Unit = { _ -> },
    // ── وضع الصيانة — بطاقة إدمن جديدة، راجع AppViewModel.saveMaintenanceConfig ──
    maintenanceEnabled: Boolean = false,
    maintenanceMessage: String = "",
    onSaveMaintenanceConfig: (Boolean, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ── نصوص مُترجَمة مُحسَّبة سلفاً داخل نطاق composable: تُستخدم لاحقاً من
    // دوال/lambdas عادية غير composable (notify، restoreLauncher، أزرار
    // المشاركة) حيث لا يمكن استدعاء tr() مباشرة لأنه composable. هذا هو
    // السبب الجذري لبقاء "أدوات الإدارة" بالعربية دوماً بغض النظر عن لغة
    // التطبيق المختارة — كانت كل هذه النصوص تُمرَّر حرفياً بلا تمريرها على tr(). ──
    val msgOpSuccess = tr("تمت العملية بنجاح")
    val msgOpError = tr("حدث خطأ، حاول مرة أخرى")
    val msgBackupRestored = tr("تمت استعادة النسخة الاحتياطية")
    val msgFileReadFailed = tr("تعذّرت قراءة الملف المحدد")
    val msgCategoryAdded = tr("تمت إضافة التصنيف")
    val msgLinkCopied = tr("تم نسخ الرابط")
    val msgFavoritesCleared = tr("تم تنظيف المفضلة")
    val msgAiReset = tr("تمت إعادة ضبط إعدادات المساعد الذكي")
    val msgAllHerbsDeleted = tr("تم حذف جميع الأعشاب")
    val msgAllDataDeleted = tr("تم حذف جميع البيانات")
    val msgCategoryDeleted = tr("تم حذف التصنيف")
    val msgCategoryUpdated = tr("تم تعديل التصنيف")
    val msgDataRefreshing = tr("جاري تحديث البيانات")
    val strBackupTitle = tr("نسخة موسوعة الأعشاب")
    val strAppShareText = tr("موسوعة الأعشاب الطبية")
    val strShareChooserTitle = tr("مشاركة")

    fun notify(ok: Boolean, message: String?) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message ?: if (ok) msgOpSuccess else msgOpError
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val json = uri?.let { runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }.getOrNull() }
        if (json != null) {
            onRestoreBackup(json) { ok, msg -> notify(ok, msg ?: if (ok) msgBackupRestored else null) }
        } else {
            notify(false, msgFileReadFailed)
        }
    }
    var categoryName by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    // ── تعديل (إعادة تسمية) تصنيف: لم تكن هذه الميزة موجودة إطلاقاً في
    // الواجهة رغم توفّرها في طبقة البيانات — كان المسؤول قادراً فقط على
    // إضافة تصنيف جديد أو حذف تصنيف قائم، لا تصحيح اسم موجود. ──
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var editingCategoryName by remember { mutableStateOf("") }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { GlassTopBar(title = { Text(tr("أدوات الإدارة")) }, navigationIcon = { GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع")) } }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(tr("إحصائيات الاستخدام"), style = MaterialTheme.typography.titleLarge) }
            item {
                DeviceStatsCard(
                    loading = deviceStatsLoading,
                    total = deviceStatsTotal,
                    active7d = deviceStatsActive7d,
                    error = deviceStatsError,
                    onRefresh = onRefreshDeviceStats
                )
            }
            item {
                DeviceGrowthCard(
                    loading = deviceGrowthLoading,
                    data = deviceGrowth,
                    error = deviceGrowthError,
                    onRefresh = onRefreshDeviceGrowth
                )
            }
            item { Text(tr("معلومات هذا البناء"), style = MaterialTheme.typography.titleLarge) }
            item { BuildInfoCard() }
            item { Text(tr("وضع الصيانة"), style = MaterialTheme.typography.titleLarge) }
            item {
                MaintenanceModeCard(
                    enabled = maintenanceEnabled,
                    message = maintenanceMessage,
                    onSave = onSaveMaintenanceConfig,
                    notify = { ok, msg -> notify(ok, msg) }
                )
            }
            item { Text(tr("سجل الأعطال"), style = MaterialTheme.typography.titleLarge) }
            item {
                CrashLogsCard(
                    loading = crashLogsLoading,
                    logs = crashLogs,
                    error = crashLogsError,
                    onRefresh = onRefreshCrashLogs,
                    onClear = { onClearCrashLogs { ok, msg -> notify(ok, msg) } }
                )
            }
            item { Text(tr("الصيانة والمزامنة"), style = MaterialTheme.typography.titleLarge) }
            item { AdminButton(Icons.Filled.Sync, "تحديث البيانات", "جلب أحدث نسخة من Firestore", { onRefresh(); notify(true, msgDataRefreshing) }) }
            item { AdminButton(Icons.Filled.NetworkCheck, "اختبار الاتصال", "التحقق من الوصول إلى البيانات", { onTestConnection { ok, msg -> notify(ok, msg) } }) }
            item { AdminButton(Icons.Filled.Backup, "نسخة احتياطية", "مشاركة JSON تشمل الأعشاب والتصنيفات", { shareFile(context, "harbs-backup.json", "application/json", backupJson(categories, herbs, blends), strBackupTitle) }) }
            item { AdminButton(Icons.Filled.Sync, "نقل الصور إلى الرفع الخارجي", "نقل الصور المضمّنة داخل Firestore إلى GitHub لتخفيف المزامنة", { notify(true, "…"); onMigrateImages { ok, msg -> notify(ok, msg ?: "") } }) }
            item { AdminButton(Icons.Filled.Restore, "استعادة نسخة", "استيراد JSON إلى Firestore", { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }) }
            item { AdminButton(Icons.Filled.TableChart, "تصدير CSV", "تصدير جميع الأعشاب كملف نصي CSV", { shareFile(context, "herbs.csv", "text/csv", csvText(herbs), "herbs.csv") }) }
            item { AdminButton(Icons.Filled.Share, "مشاركة التطبيق", "فتح مشاركة النظام", { shareApp(context, strAppShareText, strShareChooserTitle) }) }
            item { AdminButton(Icons.Filled.Link, "نسخ رابط التطبيق", "نسخ رابط المشروع إلى الحافظة", { context.getSystemService(Context.CLIPBOARD_SERVICE).let { (it as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("app", "https://github.com/")); }; notify(true, msgLinkCopied) }) }
            item { AdminButton(Icons.Filled.SystemUpdate, "إعدادات التحديثات", "تعديل مستودع ورابط وملاحظات التحديث", onUpdateSettingsClick) }
            item { Text(tr("التصنيفات"), style = MaterialTheme.typography.titleLarge) }
            item {
                OutlinedTextField(
                    categoryName, { categoryName = it }, label = { Text(tr("اسم تصنيف جديد")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(enabled = categoryName.isNotBlank(), onClick = {
                            val name = categoryName.trim()
                            onAddCategory(name) { ok, msg -> notify(ok, msg ?: if (ok) msgCategoryAdded else null) }
                            categoryName = ""
                        }) { Text(tr("إضافة")) }
                    }
                )
            }
            items(categories, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(tr("${herbs.count { it.categoryId == c.id }} عشبة")) },
                    trailingContent = {
                        Row {
                            GlassIconButton(onClick = { editingCategory = c; editingCategoryName = c.name }) {
                                Icon(Icons.Filled.Edit, tr("تعديل"))
                            }
                            GlassIconButton(onClick = { confirmAction = "category:${c.id}" }) {
                                Icon(Icons.Filled.Delete, tr("حذف"), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }
            item { Text(tr("إجراءات خطرة"), style = MaterialTheme.typography.titleLarge) }
            item { AdminButton(Icons.Filled.DeleteSweep, "مسح جميع الأعشاب", "حذف كل الأعشاب من Firestore", { confirmAction = "herbs" }, danger = true) }
            item { AdminButton(Icons.Filled.DeleteForever, "حذف كل البيانات", "حذف الأعشاب والتصنيفات", { confirmAction = "all" }, danger = true) }
            item { AdminButton(Icons.Filled.CleaningServices, "تنظيف المفضلة", "حذف المفضلة المحلية", { onClearFavorites(); notify(true, msgFavoritesCleared) }) }
            item {
                AiAssistantDevTools(
                    herbs = herbs,
                    similarityThreshold = aiSimilarityThreshold,
                    searchThreshold = aiSearchThreshold,
                    extraStopWords = aiExtraStopWords,
                    onSimilarityChange = onSetAiSimilarityThreshold,
                    onSearchThresholdChange = onSetAiSearchThreshold,
                    onExtraStopWordsChange = onSetAiExtraStopWords,
                    onReset = { onResetAiSettings(); notify(true, msgAiReset) }
                )
            }
            item { AdminButton(Icons.Filled.School, "الحالات المدرَّبة", "مرادفات وحالات (سؤال ← رد) يعلّمها المطوّر لسيمو يدوياً", onOpenTrainedCases) }
            item { AdminButton(Icons.Filled.Language, "معرفة سيمو من الإنترنت", "أعشاب بحث عنها سيمو في النت للمراجعة والاعتماد، والأسئلة التي لم يجد لها جواباً", onOpenSemoWeb) }
            item { AdminButton(Icons.Filled.AutoAwesome, "تعلّم سيمو الذاتي", "مراجعة الحالات التي جمعها سيمو تلقائياً من تقييمات المستخدمين", onOpenSemoLearning) }
            item {
                AiOnlineDevTools(
                    enabled = aiOnlineEnabled,
                    apiKey = aiOnlineApiKey,
                    model = aiOnlineModel,
                    baseUrl = aiOnlineBaseUrl,
                    onEnabledChange = onSetAiOnlineEnabled,
                    onApiKeyChange = onSetAiOnlineApiKey,
                    onModelChange = onSetAiOnlineModel,
                    onBaseUrlChange = onSetAiOnlineBaseUrl,
                    onReset = { onResetAiOnlineSettings(); notify(true, msgAiReset) }
                )
            }
        }
    }
    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(tr("تأكيد العملية")) },
            text = { Text(tr("هذا الإجراء لا يمكن التراجع عنه. هل تريد المتابعة؟")) },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        action == "herbs" -> onDeleteAllHerbs { ok, msg -> notify(ok, msg ?: if (ok) msgAllHerbsDeleted else null) }
                        action == "all" -> onDeleteAllData { ok, msg -> notify(ok, msg ?: if (ok) msgAllDataDeleted else null) }
                        action.startsWith("category:") -> onDeleteCategory(action.substringAfter(':')) { ok, msg -> notify(ok, msg ?: if (ok) msgCategoryDeleted else null) }
                    }
                    confirmAction = null
                }) { Text(tr("متابعة"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text(tr("إلغاء")) } }
        )
    }
    editingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text(tr("تعديل التصنيف")) },
            text = {
                OutlinedTextField(
                    value = editingCategoryName,
                    onValueChange = { editingCategoryName = it },
                    label = { Text(tr("اسم التصنيف")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editingCategoryName.isNotBlank(),
                    onClick = {
                        val newName = editingCategoryName.trim()
                        onUpdateCategory(category.id, newName) { ok, msg -> notify(ok, msg ?: if (ok) msgCategoryUpdated else null) }
                        editingCategory = null
                    }
                ) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { editingCategory = null }) { Text(tr("إلغاء")) } }
        )
    }
}

@Composable private fun AdminButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
            },
            headlineContent = { Text(tr(title)) },
            supportingContent = { Text(tr(subtitle)) }
        )
    }
}

/**
 * بطاقة "كم جهاز يستخدم الموسوعة" — أداة إدمن جديدة بطلب صريح من المستخدم.
 * لا تُحمَّل تلقائياً عند فتح الشاشة (القيم تبدأ null)؛ فقط عند الضغط على
 * "تحديث" (راجع AppViewModel.refreshDeviceStats وDeviceStatsRepository)،
 * تفادياً لأي قراءة Firestore غير مطلوبة فعلياً في كل مرة تُفتح فيها لوحة
 * الإدارة.
 */
@Composable
private fun DeviceStatsCard(
    loading: Boolean,
    total: Long?,
    active7d: Long?,
    error: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tr("عدد الأجهزة"), style = MaterialTheme.typography.titleMedium)
                when {
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    total == null -> Text(tr("اضغط تحديث لعرض العدد"), style = MaterialTheme.typography.bodySmall)
                    else -> Text(
                        tr("الإجمالي: $total  ·  نشط آخر 7 أيام: ${active7d ?: 0}"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            } else {
                GlassIconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = tr("تحديث"))
                }
            }
        }
    }
}

/**
 * بطاقة "نمو الأجهزة" — عدد الأجهزة الجديدة يوماً بيوم لآخر 14 يوماً، رسم
 * بياني بسيط بأعمدة (Canvas خفيف بلا أي مكتبة رسوم خارجية). نفس مبدأ
 * DeviceStatsCard أعلاه: لا تُجلَب تلقائياً، فقط عند الضغط على "تحديث".
 */
@Composable
private fun DeviceGrowthCard(
    loading: Boolean,
    data: List<Pair<String, Int>>,
    error: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("نمو الأجهزة (آخر 14 يوم)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    GlassIconButton(onClick = onRefresh, size = 32.dp) {
                        Icon(Icons.Filled.Refresh, contentDescription = tr("تحديث"), modifier = Modifier.size(18.dp))
                    }
                }
            }
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                data.isEmpty() -> Text(tr("اضغط تحديث لعرض بيانات النمو"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val maxCount = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                    val barColor = MaterialTheme.colorScheme.primary
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    ) {
                        val barSpacing = 4.dp.toPx()
                        val barWidth = (size.width - barSpacing * (data.size - 1)) / data.size
                        data.forEachIndexed { index, (_, count) ->
                            val barHeight = size.height * (count.toFloat() / maxCount)
                            drawRect(
                                color = barColor,
                                topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + barSpacing), size.height - barHeight),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(data.first().first.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(data.last().first.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        tr("إجمالي الأجهزة الجديدة بهذه الفترة: ${data.sumOf { it.second }}"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * بطاقة "معلومات هذا البناء" — versionName/versionCode/النكهة/وقت التصريف
 * الفعلي (BuildConfig.BUILD_TIME_MILLIS، راجع build.gradle.kts). مفيدة
 * للتأكد من أن نسخة الاختبار الحالية على الجهاز هي فعلاً آخر بناء، خصوصاً
 * عند وجود عدة نسخ full/public على نفس الجهاز.
 */
@Composable
private fun BuildInfoCard() {
    val buildDate = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(com.salman.herbalencyclopedia.BuildConfig.BUILD_TIME_MILLIS))
    }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("النسخة ${com.salman.herbalencyclopedia.BuildConfig.VERSION_NAME} (${com.salman.herbalencyclopedia.BuildConfig.VERSION_CODE}) · ${com.salman.herbalencyclopedia.BuildConfig.FLAVOR}"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(tr("تاريخ هذا البناء: $buildDate"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * بطاقة "وضع الصيانة" — تبديل يوقف نسخة public مؤقتاً برسالة للمستخدمين،
 * راجع MaintenanceRepository وMainActivity.MaintenanceScreen. التبديل هنا
 * يُخزَّن فقط محلياً حتى الضغط على "حفظ"، فلا يُطبَّق على المستخدمين إلا
 * بعد تأكيد صريح، تفادياً لتفعيل الصيانة بالخطأ بلمسة واحدة.
 */
@Composable
private fun MaintenanceModeCard(
    enabled: Boolean,
    message: String,
    onSave: (Boolean, String, (Boolean, String?) -> Unit) -> Unit,
    notify: (Boolean, String?) -> Unit
) {
    var draftEnabled by remember(enabled) { mutableStateOf(enabled) }
    var draftMessage by remember(message) { mutableStateOf(message) }
    var saving by remember { mutableStateOf(false) }
    val msgSaved = tr("تم حفظ إعدادات الصيانة")

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("تفعيل وضع الصيانة (نسخة public فقط)"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        tr("عند التفعيل تُحجب نسخة المستخدم العادي كاملة برسالة أدناه؛ نسخة الأدمن (هذه) تبقى تعمل دوماً."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = draftEnabled, onCheckedChange = { draftEnabled = it })
            }
            OutlinedTextField(
                value = draftMessage,
                onValueChange = { draftMessage = it },
                label = { Text(tr("رسالة الصيانة")) },
                placeholder = { Text(tr("نعمل على تحسين التطبيق حالياً، عودوا خلال وقت قصير.")) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    onSave(draftEnabled, draftMessage.trim()) { ok, msg ->
                        saving = false
                        notify(ok, msg ?: if (ok) msgSaved else null)
                    }
                }
            ) { Text(if (saving) tr("جارٍ الحفظ...") else tr("حفظ")) }
        }
    }
}

/**
 * بطاقة "سجل الأعطال" — آخر الأعطال المُبلَّغ عنها تلقائياً من مُعترِض
 * الاستثناءات (راجع HerbalApp.onCreate وCrashLogRepository). لا تُجلَب
 * تلقائياً؛ فقط عند الضغط على "تحديث".
 */
@Composable
private fun CrashLogsCard(
    loading: Boolean,
    logs: List<com.salman.herbalencyclopedia.data.repository.CrashLog>,
    error: String?,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    val dateFormatter = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US) }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("آخر الأعطال (${logs.size})"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    GlassIconButton(onClick = onRefresh, size = 32.dp) {
                        Icon(Icons.Filled.Refresh, contentDescription = tr("تحديث"), modifier = Modifier.size(18.dp))
                    }
                }
            }
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                logs.isEmpty() -> Text(tr("لا توجد أعطال مسجَّلة — اضغط تحديث لجلب آخر السجل"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        logs.forEach { log ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        log.message.ifBlank { tr("(بلا رسالة)") },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                    Text(
                                        "v${log.versionName} (${log.versionCode}) · ${log.flavor} · ${log.deviceModel} · ${log.androidVersion}" +
                                            (log.createdAt?.let { " · " + dateFormatter.format(it.toDate()) } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = onClear) { Text(tr("مسح السجل"), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
/**
 * شاشة مستقلة لـ"الحالات المدرَّبة" — كانت بطاقة (AiTrainingDevTools) مدمجة
 * ضمن قائمة "أدوات الإدارة" الطويلة، فتُفتح الآن من زر خاص بها بدل الظهور
 * دوماً هناك. المحتوى نفسه بلا أي تغيير (نفس AiTrainingDevTools أدناه)،
 * فقط بشاشة وTopBar خاصين بها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemoTrainedCasesScreen(
    onBack: () -> Unit,
    synonyms: Map<String, String>,
    trainedExamples: List<TrainedExample>,
    trainedThreshold: Float,
    onSynonymsChange: (Map<String, String>) -> Unit,
    onTrainedExamplesChange: (List<TrainedExample>) -> Unit,
    onTrainedThresholdChange: (Float) -> Unit
) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { GlassTopBar(title = { Text(tr("الحالات المدرَّبة")) }, navigationIcon = { GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع")) } }) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AiTrainingDevTools(
                    synonyms = synonyms,
                    trainedExamples = trainedExamples,
                    trainedThreshold = trainedThreshold,
                    onSynonymsChange = onSynonymsChange,
                    onTrainedExamplesChange = onTrainedExamplesChange,
                    onTrainedThresholdChange = onTrainedThresholdChange
                )
            }
        }
    }
}

/**
 * شاشة مستقلة لـ"تعلّم سيمو الذاتي" — بنفس مبدأ SemoTrainedCasesScreen
 * أعلاه، تحتوي فقط بطاقة AiSelfLearningDevTools بدل دمجها ضمن قائمة أدوات
 * الإدارة الطويلة.
 */
/**
 * مراجعة المطوّر لما تعلّمه سيمو من الإنترنت:
 *  - أعشاب غير موجودة بالموسوعة بحث عنها Gemini (بحالة "غير معتمد" حتى تراجعها).
 *    "إضافة للموسوعة" تنقلها كعشبة حقيقية (بلا فئة، يمكن تعديلها لاحقاً) وتحذف السجل المؤقت.
 *  - أسماء بديلة لأعشاب موجودة (تلقائية، للحذف فقط إن كانت خاطئة).
 *  - أسئلة لم يجد لها سيمو جواباً، مرتّبة بعدد التكرار: دليلك لما ينقص الموسوعة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemoWebKnowledgeScreen(
    onBack: () -> Unit,
    webHerbs: List<WebHerb>,
    unanswered: List<UnansweredQuestion>,
    onPromote: (WebHerb) -> Unit,
    onApprove: (String) -> Unit,
    onDeleteWebHerb: (String) -> Unit,
    onDeleteUnanswered: (String) -> Unit
) {
    val newHerbs = webHerbs.filter { !it.isAliasOnly }
    val aliases = webHerbs.filter { it.isAliasOnly }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { GlassTopBar(title = { Text(tr("معرفة سيمو من الإنترنت")) }, navigationIcon = { GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع")) } }) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(tr("أعشاب تعلّمها سيمو (${newHerbs.size})"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            if (newHerbs.isEmpty()) item { Text(tr("لا توجد أعشاب بانتظار المراجعة."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(newHerbs, key = { "h_" + it.id }) { w ->
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(w.allNames.joinToString(" / ") + if (w.latinName.isNotBlank()) " (" + w.latinName + ")" else "", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(
                            (if (w.approved) tr("معتمدة") else tr("غير معتمدة")) + " • " + (if (w.grounded) tr("من بحث إنترنت حيّ") else tr("من معرفة النموذج بلا بحث")),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (w.approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (w.benefits.isNotBlank()) Text(tr("الفوائد: ") + w.benefits, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                        if (w.warnings.isNotBlank()) Text(tr("التحذيرات: ") + w.warnings, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onPromote(w) }) { Text(tr("إضافة للموسوعة")) }
                            if (!w.approved) TextButton(onClick = { onApprove(w.id) }) { Text(tr("اعتماد فقط")) }
                            TextButton(onClick = { onDeleteWebHerb(w.id) }) { Text(tr("حذف"), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            if (aliases.isNotEmpty()) {
                item { Text(tr("أسماء بديلة تعلّمها (${aliases.size})"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                items(aliases, key = { "a_" + it.id }) { w ->
                    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.allNames.joinToString(" / ") + "  ←  " + w.aliasOf, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onDeleteWebHerb(w.id) }) { Icon(Icons.Filled.Delete, tr("حذف"), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); Text(tr("أسئلة بلا جواب (${unanswered.size})"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            if (unanswered.isEmpty()) item { Text(tr("لا توجد أسئلة مسجَّلة بعد."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(unanswered, key = { "u_" + it.id }) { u ->
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.question, style = MaterialTheme.typography.bodyMedium)
                            Text("×" + u.count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteUnanswered(u.id) }) { Icon(Icons.Filled.Delete, tr("حذف"), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemoSelfLearningScreen(
    onBack: () -> Unit,
    autoLearnedExamples: List<TrainedExample>,
    autoLearnEnabled: Boolean,
    trainedExamples: List<TrainedExample>,
    onAutoLearnedExamplesChange: (List<TrainedExample>) -> Unit,
    onAutoLearnEnabledChange: (Boolean) -> Unit,
    onDemoteLearnedExample: (String) -> Unit,
    onPromoteToTrained: (TrainedExample) -> Unit
) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { GlassTopBar(title = { Text(tr("تعلّم سيمو الذاتي")) }, navigationIcon = { GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع")) } }) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AiSelfLearningDevTools(
                    autoLearnedExamples = autoLearnedExamples,
                    autoLearnEnabled = autoLearnEnabled,
                    trainedExamples = trainedExamples,
                    onAutoLearnedExamplesChange = onAutoLearnedExamplesChange,
                    onAutoLearnEnabledChange = onAutoLearnEnabledChange,
                    onDemoteLearnedExample = onDemoteLearnedExample,
                    onPromoteToTrained = onPromoteToTrained
                )
            }
        }
    }
}

/**
 * أدوات مطور لضبط "تدريب" سيمو المساعد (HerbAssistant): عتبتا
 * التشابه المستخدمتان في تجميع النقاط والبحث الحر، وكلمات إيقاف إضافية
 * لتحسين تحليل النصوص العربية الخاصة بالموسوعة، مع مساحة اختبار حيّة
 * تُظهر إجابة المساعد فوراً على أي سؤال باستخدام أعشاب حقيقية من القاعدة —
 * كل تغيير هنا يُحفظ ويُطبَّق مباشرة بلا أي حظر أو قيد إضافي على الإجابات.
 */
@Composable
private fun AiAssistantDevTools(
    herbs: List<Herb>,
    similarityThreshold: Float,
    searchThreshold: Float,
    extraStopWords: Set<String>,
    onSimilarityChange: (Float) -> Unit,
    onSearchThresholdChange: (Float) -> Unit,
    onExtraStopWordsChange: (Set<String>) -> Unit,
    onReset: () -> Unit
) {
    var stopWordsText by remember(extraStopWords) { mutableStateOf(extraStopWords.joinToString(", ")) }
    var testQuestion by remember { mutableStateOf("") }
    var testHerbIds by remember { mutableStateOf(setOf<String>()) }
    var testAnswer by remember { mutableStateOf<String?>(null) }

    // حالة قاموس المرادفات المحلي (Rabih Dictionary + Arabic WordNet، انظر
    // DictionaryLexicon) — يُحمَّل مرة واحدة في الخلفية عند إقلاع التطبيق،
    // فيُحتمل جداً أنه جاهز بالفعل عند فتح هذه الشاشة. هذا الاستقصاء
    // (كل 300ms حتى انتهاء المحاولة) مجرّد مؤشر حيّ للمطوّر أثناء الاختبار.
    // يتوقف فور *انتهاء* المحاولة (loadAttempted) سواء نجحت أم فشلت — قبلاً
    // كان يتوقف فقط عند النجاح، فكان الفشل يبدو مطابقاً بصرياً لـ"لا يزال
    // يُحمَّل" بلا أي طريقة للتمييز بينهما أو معرفة سبب الفشل الفعلي.
    var lexiconReady by remember { mutableStateOf(DictionaryLexicon.isReady) }
    var lexiconAttempted by remember { mutableStateOf(DictionaryLexicon.loadAttempted) }
    LaunchedEffect(Unit) {
        while (!lexiconAttempted) {
            kotlinx.coroutines.delay(300)
            lexiconReady = DictionaryLexicon.isReady
            lexiconAttempted = DictionaryLexicon.loadAttempted
        }
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("سيمو المساعد (تدريب/ضبط)"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(
                tr("المساعد يعمل محلياً بالكامل من بيانات الموسوعة نفسها، بلا اتصال إنترنت وبلا أي حظر أو قيد على الإجابات. عدّل العتبات هنا لتحسين دقّة \"تدريبه\" فوراً."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // مؤشر يؤكّد أن قاموس المرادفات المحلي (الذي يوسّع فهم البحث الحر
            // وشاشتَي البحث المباشر بمرادفات عامة) حُمِّل فعلاً — بثلاث حالات
            // مميَّزة بصرياً الآن بدل حالتين: "لا يزال يعمل" تختلف عن "انتهى
            // بفشل" (وتعرض سبب الفشل الفعلي من Logcat/lastError)، فلا يبقى
            // فشل حقيقي متنكّراً في هيئة تحميل عالق للأبد.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        lexiconReady -> Icons.Filled.CheckCircle
                        lexiconAttempted -> Icons.Filled.ErrorOutline
                        else -> Icons.Filled.HourglassEmpty
                    },
                    contentDescription = null,
                    tint = when {
                        lexiconReady -> MaterialTheme.colorScheme.primary
                        lexiconAttempted -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        lexiconReady ->
                            tr("قاموس المرادفات المحلي جاهز (${DictionaryLexicon.loadedWordCount} كلمة، Rabih Dictionary + Arabic WordNet)")
                        lexiconAttempted ->
                            tr("فشل تحميل قاموس المرادفات المحلي: ") + (DictionaryLexicon.lastError?.let { tr(it) } ?: tr("خطأ غير معروف"))
                        else ->
                            tr("جارٍ تحميل قاموس المرادفات المحلي…")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lexiconAttempted && !lexiconReady) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("حساسية تجميع النقاط المتشابهة: ${(similarityThreshold * 100).roundToIntPct()}%"), style = MaterialTheme.typography.labelLarge)
                Slider(value = similarityThreshold, onValueChange = onSimilarityChange, valueRange = 0.05f..0.95f)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("حساسية البحث الحر عن إجابة: ${(searchThreshold * 100).roundToIntPct()}%"), style = MaterialTheme.typography.labelLarge)
                Slider(value = searchThreshold, onValueChange = onSearchThresholdChange, valueRange = 0.02f..0.9f)
            }

            OutlinedTextField(
                value = stopWordsText,
                onValueChange = { stopWordsText = it },
                label = { Text(tr("كلمات إيقاف إضافية (مفصولة بفاصلة)")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onExtraStopWordsChange(stopWordsText.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet())
                }) { Text(tr("حفظ الكلمات")) }
                TextButton(onClick = onReset) { Text(tr("إعادة الضبط الافتراضي")) }
            }

            HorizontalDivider()

            Text(tr("اختبار حيّ"), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            LazyColumn(Modifier.heightIn(max = 130.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(herbs, key = { it.id }) { herb ->
                    FilterChip(
                        selected = herb.id in testHerbIds,
                        onClick = { testHerbIds = if (herb.id in testHerbIds) testHerbIds - herb.id else testHerbIds + herb.id },
                        label = { Text(herb.name) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            OutlinedTextField(
                value = testQuestion,
                onValueChange = { testQuestion = it },
                label = { Text(tr("جرّب سؤالاً")) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(enabled = testQuestion.isNotBlank() && testHerbIds.isNotEmpty(), onClick = {
                        val selectedHerbs = herbs.filter { it.id in testHerbIds }
                        testAnswer = HerbAssistant.answer(testQuestion, selectedHerbs)
                    }) { Text(tr("اسأل")) }
                }
            )
            if (testAnswer != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Text(testAnswer.orEmpty(), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun Float.roundToIntPct(): Int = this.roundToInt()

/**
 * أدوات مطور لـ"تطوير الفهم" الفعلي لسيمو بما يتجاوز عتبات المطابقة: يضيف
 * المطوّر هنا مرادفات (كلمات جديدة يفهمها سيمو كأنها كلمة أخرى معروفة له)
 * وحالات مدرَّبة كاملة (سؤال نموذجي + الرد المطلوب بالضبط)، فيتعلّم سيمو
 * التعامل مع صياغات أو حالات لم يغطها المنطق العام جيداً — كل ذلك يُحفظ
 * ويُطبَّق فوراً بلا إعادة بناء التطبيق.
 */
@Composable
private fun AiTrainingDevTools(
    synonyms: Map<String, String>,
    trainedExamples: List<TrainedExample>,
    trainedThreshold: Float,
    onSynonymsChange: (Map<String, String>) -> Unit,
    onTrainedExamplesChange: (List<TrainedExample>) -> Unit,
    onTrainedThresholdChange: (Float) -> Unit
) {
    var newSynonymWord by remember { mutableStateOf("") }
    var newSynonymMeaning by remember { mutableStateOf("") }
    var newPattern by remember { mutableStateOf("") }
    var newResponse by remember { mutableStateOf("") }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("تدريب سيمو المخصّص"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(
                tr("علّم سيمو كلمات ومرادفات جديدة، أو درّبه على حالات وأسئلة بعينها بردٍ تكتبه أنت بنفسك — يُستخدم فوراً في كل محادثة قادمة."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── عتبة مطابقة الحالات المدرَّبة ──
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("حساسية مطابقة الحالات المدرَّبة: ${(trainedThreshold * 100).roundToIntPct()}%"), style = MaterialTheme.typography.labelLarge)
                Slider(value = trainedThreshold, onValueChange = onTrainedThresholdChange, valueRange = 0.1f..0.95f)
                Text(
                    tr("كلما قلّت النسبة، كفى تشابه أبسط بين سؤال المستخدم والمثال المدرَّب ليُستخدم رده مباشرة."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ── مرادفات ──
            Text(tr("مرادفات (كلمات جديدة يفهمها سيمو)"), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            if (synonyms.isEmpty()) {
                Text(tr("لا توجد مرادفات مضافة بعد."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    synonyms.forEach { (word, meaning) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$word  ⇦  $meaning", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            GlassIconButton(onClick = { onSynonymsChange(synonyms - word) }, size = 32.dp) {
                                Icon(Icons.Filled.Delete, tr("حذف"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newSynonymWord,
                    onValueChange = { newSynonymWord = it },
                    label = { Text(tr("كلمة جديدة")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = newSynonymMeaning,
                    onValueChange = { newSynonymMeaning = it },
                    label = { Text(tr("تُفهم كـ")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(
                enabled = newSynonymWord.isNotBlank() && newSynonymMeaning.isNotBlank(),
                onClick = {
                    onSynonymsChange(synonyms + (newSynonymWord.trim() to newSynonymMeaning.trim()))
                    newSynonymWord = ""; newSynonymMeaning = ""
                }
            ) { Text(tr("إضافة مرادف")) }

            HorizontalDivider()

            // ── حالات مدرَّبة ──
            Text(tr("حالات مدرَّبة (سؤال ← رد مخصّص)"), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            if (trainedExamples.isEmpty()) {
                Text(tr("لا توجد حالات مدرَّبة بعد."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trainedExamples.forEach { example ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("س: ${example.pattern}"), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text(tr("ج: ${example.response}"), style = MaterialTheme.typography.bodySmall)
                                }
                                GlassIconButton(onClick = { onTrainedExamplesChange(trainedExamples - example) }, size = 32.dp) {
                                    Icon(Icons.Filled.Delete, tr("حذف"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = newPattern,
                onValueChange = { newPattern = it },
                label = { Text(tr("سؤال نموذجي")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newResponse,
                onValueChange = { newResponse = it },
                label = { Text(tr("الرد المطلوب")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            TextButton(
                enabled = newPattern.isNotBlank() && newResponse.isNotBlank(),
                onClick = {
                    onTrainedExamplesChange(trainedExamples + TrainedExample(newPattern.trim(), newResponse.trim()))
                    newPattern = ""; newResponse = ""
                }
            ) { Text(tr("إضافة حالة")) }
        }
    }
}

/**
 * أدوات مطور لمراجعة "تعلّم سيمو الذاتي": كل حالة هنا وُلدت تلقائياً من
 * إجابة بحث حر أعطاها سيمو فعلاً من بيانات الموسوعة، وقيّمها مستخدم حقيقي
 * بـ 👍 في شاشة الدردشة — أي أن المصدر بالكامل هو الموسوعة + استخدام
 * فعلي، وليس تخميناً. يمكن للمطوّر من هنا: تعطيل التعلّم الذاتي كلياً،
 * حذف حالة بعينها (لو كانت غير دقيقة)، "ترقيتها" لتصبح حالة تدريب يدوية
 * دائمة (تنتقل للقائمة المحمية في [AiTrainingDevTools])، أو مسح كل ما
 * تعلّمه سيمو والبدء من جديد.
 */
@Composable
private fun AiSelfLearningDevTools(
    autoLearnedExamples: List<TrainedExample>,
    autoLearnEnabled: Boolean,
    trainedExamples: List<TrainedExample>,
    onAutoLearnedExamplesChange: (List<TrainedExample>) -> Unit,
    onAutoLearnEnabledChange: (Boolean) -> Unit,
    onDemoteLearnedExample: (String) -> Unit,
    onPromoteToTrained: (TrainedExample) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("تعلّم سيمو الذاتي"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(
                tr("كل حالة هنا وُلدت تلقائياً من إجابة سيمو الفعلية على بيانات الموسوعة بعد أن قيّمها مستخدم بـ 👍 في الدردشة — يعتمد سيمو على الموسوعة أولاً، ثم يراكم فوقها خبرة حقيقية من استخدامه."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("تفعيل التعلّم الذاتي"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        tr("عند التعطيل، يتوقف سيمو عن حفظ أي حالات جديدة ولا يستخدم القديمة منها في الردود."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoLearnEnabled, onCheckedChange = onAutoLearnEnabledChange)
            }

            HorizontalDivider()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("الحالات المتعلَّمة (${autoLearnedExamples.size})"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                if (autoLearnedExamples.isNotEmpty()) {
                    TextButton(onClick = {
                        autoLearnedExamples.forEach { onDemoteLearnedExample(it.pattern) }
                        onAutoLearnedExamplesChange(emptyList())
                    }) { Text(tr("مسح الكل")) }
                }
            }

            if (autoLearnedExamples.isEmpty()) {
                Text(
                    tr("لم يتعلّم سيمو أي حالة بعد. ستظهر هنا تلقائياً أول مرة يُقيّم فيها مستخدم إجابة بحث حر بـ 👍."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    autoLearnedExamples.forEach { example ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(tr("س: ${example.pattern}"), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text(tr("ج: ${example.response}"), style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                                    TextButton(
                                        enabled = example !in trainedExamples,
                                        onClick = { onPromoteToTrained(example) }
                                    ) { Text(tr("ترقية لتدريب دائم")) }
                                    TextButton(onClick = {
                                        onDemoteLearnedExample(example.pattern)
                                        onAutoLearnedExamplesChange(autoLearnedExamples - example)
                                    }) {
                                        Text(tr("حذف"), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * أدوات مطور للوضع الذكي عبر الإنترنت: تفعيل/تعطيل، إدخال مفتاح Gemini API
 * (مجاني بلا بطاقة ائتمان من Google AI Studio)، اسم النموذج (افتراضياً
 * "gemini-flash-latest"، راجع توثيقه في AiConfig)، واختبار اتصال حيّ يعرض
 * رد النموذج فعلياً أو سبب الفشل. راجع توثيق [AiConfig.onlineEnabled]
 * وOnlineAssistant.kt للسياق الكامل — لماذا Gemini تحديداً (مجاني ومتاح
 * فعلياً من سوريا مباشرة دون VPN منذ رفع الحظر عنه في سبتمبر 2026) ولماذا
 * يبقى سيمو المحلي كما هو تماماً عند التعطيل أو عند انعدام الإنترنت.
 */
@Composable
private fun AiOnlineDevTools(
    enabled: Boolean,
    apiKey: String,
    model: String,
    baseUrl: String,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onReset: () -> Unit
) {
    var apiKeyText by remember(apiKey) { mutableStateOf(apiKey) }
    var modelText by remember(model) { mutableStateOf(model) }
    var baseUrlText by remember(baseUrl) { mutableStateOf(baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testFailed by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudQueue, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("الوضع الذكي عبر الإنترنت"), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Text(
                tr("يستخدم نموذج Gemini المجاني من Google (بلا بطاقة ائتمان). عند التفعيل ووجود مفتاح صالح واتصال إنترنت فعلي، تُرسَل الأسئلة التي تحتاج بحثاً فعلياً لهذا النموذج بدل البحث المحلي. بلا إنترنت أو بلا مفتاح، يبقى سيمو يعمل محلياً تماماً كما كان دون أي تغيير."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("تفعيل الوضع الذكي عبر الإنترنت"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        tr("يتطلب مفتاح Gemini API صالح أدناه."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            OutlinedTextField(
                value = apiKeyText,
                onValueChange = { apiKeyText = it },
                label = { Text(tr("مفتاح Gemini API")) },
                supportingText = { Text(tr("مجاني بلا بطاقة ائتمان من aistudio.google.com/apikey")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )
            OutlinedTextField(
                value = modelText,
                onValueChange = { modelText = it },
                label = { Text(tr("اسم النموذج")) },
                supportingText = { Text(tr("الافتراضي \"gemini-flash-latest\" — اسم مستعار يشير دوماً لأحدث نموذج Flash مجاني موصى به من Google. راجع قائمة نماذج Gemini الكاملة في ai.google.dev/gemini-api/docs/models لتغييره.")) },
                singleLine = true,
                // نفس إصلاح خانة رابط البروكسي أعلاه: تعطيل auto-correct/الأحرف
                // الكبيرة التلقائية، لأن هذه الخانة نصية حساسة لأي تعديل صامت.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = baseUrlText,
                onValueChange = { baseUrlText = it },
                label = { Text(tr("عنوان بروكسي/مرآة (اختياري)")) },
                supportingText = {
                    Text(tr("اتركه فارغاً للاتصال المباشر بـ Gemini. إن كان الاتصال المباشر محجوباً لديك رغم رفع الحظر الرسمي، ضع هنا عنوان خادم بروكسي خاص بك (مثال: Cloudflare Worker) يُعيد توجيه نفس المسار (/v1beta/models/...) لخوادم Google الحقيقية."))
                },
                placeholder = { Text("https://my-proxy.example.workers.dev") },
                singleLine = true,
                // ═══ إصلاح خلل حقيقي أُبلغ عنه: الرابط يظهر محفوظاً بالحقل لكن
                // الطلب لا يصل الخادم أبداً (سجلات Cloudflare خالية تماماً) ═══
                // السبب الأرجح: هذا الحقل لم يكن يعطّل التصحيح/الاقتراح
                // التلقائي للوحة مفاتيح أندرويد، فقد تُغيَّر أحرف بالرابط
                // (خصوصاً نطاق مثل "workers.dev") بصمت أثناء الكتابة أو حتى
                // بعد اللصق، فينتج رابط تالف يفشل تحليله محلياً (URL غير
                // صالح) قبل أي محاولة شبكة فعلية — يطابق تماماً "لا شيء يصل
                // السجل إطلاقاً". تعطيل autoCorrect/الاقتراح والأحرف الكبيرة
                // التلقائية هنا يمنع تكرار المشكلة مستقبلاً.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onApiKeyChange(apiKeyText.trim())
                    onModelChange(modelText.trim())
                    onBaseUrlChange(baseUrlText.trim())
                }) { Text(tr("حفظ")) }
                TextButton(onClick = onReset) { Text(tr("إعادة الضبط الافتراضي")) }
            }

            HorizontalDivider()

            Text(tr("اختبار حيّ"), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Button(
                enabled = !testing && apiKeyText.isNotBlank(),
                onClick = {
                    testing = true
                    testResult = null
                    testFailed = false
                    // يختبر المفتاح/النموذج المحفوظَين فعلياً في AiConfig
                    // (وليس النص المكتوب بعد قبل الضغط على "حفظ")، فيُحفَظان
                    // أولاً لضمان اختبار القيم الفعلية التي ستُستخدم لاحقاً.
                    onApiKeyChange(apiKeyText.trim())
                    onModelChange(modelText.trim())
                    onBaseUrlChange(baseUrlText.trim())
                    AiConfig.onlineApiKey = apiKeyText.trim()
                    AiConfig.onlineModel = modelText.trim().ifBlank { AiConfig.defaultOnlineModel }
                    AiConfig.onlineBaseUrl = baseUrlText.trim()
                    scope.launch {
                        val (success, message) = com.salman.herbalencyclopedia.data.ai.OnlineAssistant.testConnectionVerbose()
                        testing = false
                        testResult = message
                        testFailed = !success
                    }
                }
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(tr(if (testing) "جارٍ الاختبار…" else "اختبار الاتصال الآن"))
            }
            testResult?.let { result ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (testFailed) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Text(
                        tr(result),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testFailed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun backupJson(categories: List<Category>, herbs: List<Herb>, blends: List<com.salman.herbalencyclopedia.data.model.Blend>): String {
    val root = org.json.JSONObject()
    root.put("categories", org.json.JSONArray().apply { categories.forEach { put(org.json.JSONObject().apply { put("id",it.id); put("name",it.name); put("icon",it.icon ?: "") }) } })
    root.put("herbs", org.json.JSONArray().apply { herbs.forEach { put(org.json.JSONObject().apply { put("id",it.id); put("name",it.name); put("categoryId",it.categoryId ?: ""); put("benefits",it.benefits); put("warnings",it.warnings); put("harms",it.harms); put("usage",it.usage); put("notes",it.notes); put("imageUrl",it.imageUrl ?: "") }) } })
    root.put("blends", org.json.JSONArray().apply {
        blends.forEach {
            put(org.json.JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("herbIds", org.json.JSONArray(it.herbIds))
                put("benefits", it.benefits)
                put("usage", it.usage)
                put("warnings", it.warnings)
                put("notes", it.notes)
                put("imageUrl", it.imageUrl ?: "")
            })
        }
    })
    return root.toString(2)
}
private fun csvText(herbs: List<Herb>): String {
    fun e(s:String) = "\"" + s.replace("\"", "\"\"") + "\""
    // BOM (U+FEFF) في البداية ضروري لفتح Excel/Sheets الملف بترميز UTF-8
    // الصحيح تلقائياً؛ بدونه يعرض Excel كل النصوص العربية كرموز مشوَّهة
    // (؟؟؟ أو حروف عشوائية) رغم أن الملف نفسه سليم تماماً بترميز UTF-8 —
    // هذا سبب شكوى "تصدير CSV فيه مشكلة" الأكثر شيوعاً مع بيانات عربية.
    return "\uFEFF" + buildString { appendLine("name,category_id,benefits,warnings,harms,usage,notes,image_url"); herbs.forEach { appendLine(listOf(it.name,it.categoryId ?: "",it.benefits,it.warnings,it.harms,it.usage,it.notes,it.imageUrl ?: "").joinToString(",", transform=::e)) } }
}
private fun shareApp(context: Context, shareText: String, chooserTitle: String) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, chooserTitle)) }

/**
 * يكتب [text] إلى ملف حقيقي في cacheDir/exports (انظر res/xml/file_paths.xml)
 * ثم يشاركه عبر FileProvider كـ content:// Uri بدل مشاركته كنص خام مباشر.
 *
 * كانت النسخة الاحتياطية وCSV تُشارَكان سابقاً عبر Intent.EXTRA_TEXT (نص
 * خام) بدل ملف فعلي، وهذا يسبب مشكلتين حقيقيتين:
 * 1) قيد حجم صارم على بيانات الـIntent بين التطبيقات (Binder transaction،
 *    ~1 ميجابايت لكل التطبيق)؛ مع نمو عدد الأعشاب يفشل التصدير بصمت أو
 *    يتحطم التطبيق (TransactionTooLargeException) دون أي ملف يصل فعلياً.
 * 2) التطبيقات المستقبِلة (واتساب، الرسائل، إلخ) تتعامل مع النص كرسالة لا
 *    كملف .json/.csv، فيضيع الامتداد والترميز الصحيحين، ولا يمكن اختيار
 *    "استعادة نسخة" لاحقاً على نفس المحتوى مباشرة لأنه لم يعد ملفاً أصلاً.
 * الكتابة كملف حقيقي ومشاركته بامتداده ونوعه (MIME) الصحيحين تحل المشكلتين
 * معاً، وتسمح أيضاً بحفظه مباشرة عبر "حفظ في الجهاز" من قائمة المشاركة.
 */
private fun shareFile(context: Context, fileName: String, mimeType: String, text: String, chooserTitle: String) {
    val exportsDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    val file = java.io.File(exportsDir, fileName)
    file.writeText(text, Charsets.UTF_8)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
