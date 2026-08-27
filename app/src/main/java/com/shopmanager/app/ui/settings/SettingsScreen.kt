package com.shopmanager.app.ui.settings

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.backup.BackupManager
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.updates.ApkDownloader
import com.shopmanager.app.data.updates.AppVersionInfo
import com.shopmanager.app.data.updates.DownloadState
import com.shopmanager.app.data.updates.UpdateCheckResult
import com.shopmanager.app.data.updates.UpdateChecker
import com.shopmanager.app.data.updates.UpdateManifest
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.GlassIconButton
import com.shopmanager.app.ui.common.liquidGlassSurface
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.AppColorPalette
import com.shopmanager.app.ui.theme.AppThemeMode
import com.shopmanager.app.ui.theme.SuccessGreen
import com.shopmanager.app.ui.theme.paletteColorsFor
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val CURRENCY_OPTIONS = listOf("ل.س", "$", "SAR", "AED", "TRY")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (AppThemeMode) -> Unit,
    onColorPaletteChanged: (AppColorPalette) -> Unit = {},
    onPerformancePreferenceChanged: (PerformanceMode) -> Unit = {},
    debtsViewModel: DebtsViewModel? = null,
    materialsViewModel: MaterialsViewModel? = null,
    onOpenHelp: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var colorPalette by remember { mutableStateOf(settings.colorPalette) }
    var hasPin by remember { mutableStateOf(settings.hasPin) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(settings.currencySymbol) }
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var performanceMode by remember { mutableStateOf(settings.performanceMode) }
    var showCurrencyDialog by remember { mutableStateOf(false) }

    // النسخ الاحتياطي التلقائي المحلي — silent daily local backup, restore
    // only from here or from the automatic "server unavailable" prompt.
    val scope = rememberCoroutineScope()
    val debtsRepoForBackup = remember { DebtsRepository() }
    val materialsRepoForBackup = remember { MaterialsRepository() }
    var backups by remember { mutableStateOf(BackupManager.listBackups(context)) }
    var pendingRestore by remember { mutableStateOf<BackupManager.BackupInfo?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf<String?>(null) }

    // التحديثات (Settings → check for update, in-app download+install):
    // see data/updates/ for the actual networking. The manifest URL itself
    // is only ever set from the hidden developer panel; a normal user just
    // taps the button.
    val appVersion = remember { AppVersionInfo.current(context) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateManifest?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableStateOf(0) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }

    fun checkForUpdate() {
        isCheckingUpdate = true
        updateStatusMessage = null
        scope.launch {
            when (val result = UpdateChecker.check(context, settings.updateManifestUrl)) {
                is UpdateCheckResult.UpToDate -> updateStatusMessage = "أنت تستخدم أحدث إصدار ✅"
                is UpdateCheckResult.UpdateAvailable -> {
                    settings.lastUpdateCheckAt = System.currentTimeMillis()
                    pendingUpdate = result.manifest
                }
                is UpdateCheckResult.Failed -> updateStatusMessage = result.reason
            }
            isCheckingUpdate = false
        }
    }

    fun startDownload(manifest: UpdateManifest) {
        isDownloadingUpdate = true
        downloadPercent = 0
        scope.launch {
            when (val state = ApkDownloader.download(context, manifest.apkUrl) { percent -> downloadPercent = percent }) {
                is DownloadState.Done -> {
                    isDownloadingUpdate = false
                    if (ApkDownloader.canInstallPackages(context)) {
                        ApkDownloader.install(context, state.file)
                        pendingUpdate = null
                    } else {
                        downloadedApk = state.file
                        needsInstallPermission = true
                    }
                }
                is DownloadState.Error -> {
                    isDownloadingUpdate = false
                    updateStatusMessage = state.message
                    pendingUpdate = null
                }
                is DownloadState.InProgress -> Unit
            }
        }
    }

    val debtsSyncError = debtsViewModel?.hasSyncError?.collectAsState(initial = false)?.value ?: false
    val materialsSyncError = materialsViewModel?.hasSyncError?.collectAsState(initial = false)?.value ?: false
    var dismissedServerErrorBanner by remember { mutableStateOf(false) }

    fun runRestore(backup: BackupManager.BackupInfo) {
        isRestoring = true
        restoreStatus = null
        scope.launch {
            try {
                BackupManager.restore(backup, debtsRepoForBackup, materialsRepoForBackup)
                restoreStatus = "تمت الاستعادة بنجاح ✅"
                dismissedServerErrorBanner = true
            } catch (e: Exception) {
                restoreStatus = "تعذرت الاستعادة: ${e.message ?: "تحقق من الاتصال بالإنترنت"}"
            } finally {
                isRestoring = false
            }
        }
    }

    Scaffold(
        // This screen sits outside the main pager (no shared bottom nav
        // bar of its own), so the outer app Scaffold already reserves the
        // real bottom/horizontal safe-area space for it one level up, in
        // NavHost's own padding. Leaving this Scaffold's contentWindowInsets
        // at its default would apply that same system inset a *second*
        // time here, pushing content up with an unnecessary empty gap
        // above the true bottom edge. The TopAppBar below still handles
        // the status bar inset entirely on its own, independent of this.
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        onClick = onBack,
                        // BUG FIXED: only `start` padding (space from the
                        // screen edge) was set here — nothing separated the
                        // button from the title text sitting right after it
                        // in the navigation-icon slot, so "الإعدادات" ended
                        // up glued directly against the button. `end`
                        // padding is direction-aware, so this opens a real
                        // gap before the title in this app's forced-RTL
                        // layout without needing to special-case RTL here.
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
            // تنبيه تلقائي: يظهر فقط إذا تعذر تحميل البيانات من الخادم
            // (وليس لمجرد أن القائمة فارغة فعليًا) وتوجد نسخة محلية يمكن
            // العودة إليها. لا يوجد استرجاع صامت تلقائي أبدًا — هذا زر
            // بلمسة واحدة، ليس عملية تحدث من دون علم صاحب المحل.
            //
            // ANIMATION: pops in with a springy scale+fade (MotionSpecs.
            // popInSpring) instead of just appearing — a sudden "error"
            // card popping onto the screen instantly reads as jarring;
            // easing it in makes the same information feel considered
            // rather than alarming. Fades+shrinks back out the same way
            // when dismissed or resolved.
            AnimatedVisibility(
                visible = (debtsSyncError || materialsSyncError) && backups.isNotEmpty() && !dismissedServerErrorBanner,
                enter = fadeIn(MotionSpecs.contentTween()) + scaleIn(MotionSpecs.popInSpring(), initialScale = 0.92f) + expandVertically(),
                exit = fadeOut(MotionSpecs.contentTween()) + scaleOut(MotionSpecs.popInSpring(), targetScale = 0.92f) + shrinkVertically()
            ) {
                ElevatedCard(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "تعذّر الاتصال بالخادم",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "يمكنك استعادة آخر نسخة احتياطية محلية (${formatBackupDate(backups.first().createdAt)}) لحين عودة الاتصال.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Button(onClick = { pendingRestore = backups.first() }) { Text("استعادة الآن") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { dismissedServerErrorBanner = true }) { Text("لاحقاً") }
                        }
                    }
                }
            }

            // المظهر (appearance)
            SettingsSection(title = "المظهر", icon = Icons.Default.Palette) {
                AppThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = {
                                themeMode = mode
                                settings.themeMode = mode
                                onThemeChanged(mode)
                            }
                        )
                        Text(
                            when (mode) {
                                AppThemeMode.SYSTEM -> "حسب النظام"
                                AppThemeMode.LIGHT -> "فاتح"
                                AppThemeMode.DARK -> "داكن"
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "لوحة الألوان",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppColorPalette.entries.forEach { palette ->
                        ColorPaletteSwatch(
                            palette = palette,
                            selected = colorPalette == palette,
                            onClick = {
                                colorPalette = palette
                                settings.colorPalette = palette
                                onColorPaletteChanged(palette)
                            }
                        )
                    }
                }
            }

            // العملة (currency) — new feature
            SettingsSection(title = "العملة", icon = Icons.Default.AttachMoney) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("رمز العملة المستخدم بكل أنحاء التطبيق")
                        Text(
                            "يظهر في الديون والمواد والمشاركة والتنبيهات",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { showCurrencyDialog = true }) { Text(currency) }
                }
            }

            // الإشعارات (notifications) — new feature
            SettingsSection(title = "الإشعارات", icon = Icons.Default.Notifications) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("تنبيهات قائمة النواقص والديون الجديدة")
                        Text(
                            "أوقفها إذا كنت لا تريد إشعارات على هذا الجهاز",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            notificationsEnabled = it
                            settings.notificationsEnabled = it
                        }
                    )
                }
            }

            // الأداء (performance) — lets the person override the
            // automatic per-device detection with an explicit choice, so a
            // phone that got misclassified (or someone who just prefers a
            // snappier/more static feel) isn't stuck with it.
            SettingsSection(title = "الأداء", icon = Icons.Default.Speed) {
                Text(
                    "يتحكم بحدّة التأثيرات البصرية (التدرجات اللونية والانميشن). اختر \"تلقائي\" ليقرر التطبيق حسب قوة جهازك.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    PerformanceMode.AUTO to "تلقائي (حسب الجهاز)",
                    PerformanceMode.HIGH to "مرتفع (كل التأثيرات)",
                    PerformanceMode.LOW to "منخفض (أداء أعلى وبطارية أطول)"
                ).forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = performanceMode == mode,
                            onClick = {
                                performanceMode = mode
                                settings.performanceMode = mode
                                onPerformancePreferenceChanged(mode)
                            }
                        )
                        Text(label)
                    }
                }
            }

            // الحماية (security / PIN lock)
            SettingsSection(title = "الحماية", icon = if (hasPin) Icons.Default.Lock else Icons.Default.LockOpen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (hasPin) "قفل برمز PIN مفعّل" else "قفل برمز PIN غير مفعّل")
                        Text("يحمي فتح التطبيق برمز محلي على هذا الجهاز فقط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (hasPin) {
                        TextButton(onClick = { settings.clearPin(); hasPin = false }) { Text("إلغاء") }
                    } else {
                        TextButton(onClick = { showSetPinDialog = true }) { Text("تفعيل") }
                    }
                }
            }

            // نسخة احتياطية (backup / export) — new feature
            if (debtsViewModel != null && materialsViewModel != null) {
                SettingsSection(title = "نسخة احتياطية", icon = Icons.Default.CloudDownload) {
                    Text(
                        "أرسل نسخة نصية من كل العملاء والديون والمواد والأسعار لنفسك (واتساب، بريد، ملاحظات...) كنسخة احتياطية سريعة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val text = buildBackupText(
                                debtsState = debtsViewModel.uiState.value,
                                materialsState = materialsViewModel.uiState.value,
                                currency = currency
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - إدارة المحل")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تصدير نسخة احتياطية الآن")
                    }
                }
            }

            // النسخ الاحتياطي التلقائي المحلي (silent daily local backup) — new feature
            SettingsSection(title = "النسخ الاحتياطي التلقائي", icon = Icons.Default.SettingsBackupRestore) {
                Text(
                    "يأخذ التطبيق نسخة كاملة من بياناتك على هذا الجهاز كل يوم تلقائيًا وبصمت (بدون أي إشعار)، احتياطًا لفقدان البيانات. لا تُستعاد هذه النسخة إلا من هنا، أو إذا تعذّر الاتصال بالخادم.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (backups.isEmpty()) {
                    Text(
                        "لا توجد نسخة بعد — ستُنشأ تلقائيًا خلال أول يوم من استخدام التطبيق.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "آخر نسخة: ${formatBackupDate(backups.first().createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    backups.take(5).forEach { backup ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(formatBackupDate(backup.createdAt), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${backup.personsCount} عميل، ${backup.materialsCount} مادة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { pendingRestore = backup }, enabled = !isRestoring) {
                                Text("استعادة")
                            }
                        }
                    }
                }
                restoreStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
                AnimatedVisibility(
                    visible = isRestoring,
                    enter = fadeIn(MotionSpecs.contentTween()) + expandVertically(),
                    exit = fadeOut(MotionSpecs.contentTween()) + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            // التحديثات (updates) — checks the manifest URL configured
            // from the hidden developer panel and, if a newer version
            // exists, downloads + installs the APK from inside the app
            // itself (no external browser step), same feel as Telegram's
            // in-chat APK updates.
            SettingsSection(title = "التحديثات", icon = Icons.Default.SystemUpdate) {
                Text(
                    "الإصدار الحالي: ${appVersion.name} (${appVersion.code})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { checkForUpdate() },
                    enabled = !isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ التحقق...")
                    } else {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تحقق من التحديثات")
                    }
                }
                updateStatusMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // حول التطبيق (about) — new, a small personal touch
            SettingsSection(title = "حول التطبيق", icon = Icons.Default.Info) {
                Text("إدارة المحل — الإصدار 1.0.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "تطبيق واحد لإدارة الديون والمواد والأسعار، مبني خصيصًا لمحلك ويعمل حتى بدون اتصال دائم بالإنترنت.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "تطوير: سلمان",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("دليل الاستخدام")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("سياسة الخصوصية")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onConfirm = { pin ->
                settings.setPin(pin)
                hasPin = true
                showSetPinDialog = false
            }
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("استعادة نسخة احتياطية؟") },
            text = {
                Text(
                    "سيتم استبدال كل الديون والعملاء والمواد والأسعار الحالية بمحتوى نسخة ${formatBackupDate(backup.createdAt)}. " +
                        "لا يمكن التراجع عن هذا بعد التنفيذ."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = backup
                    pendingRestore = null
                    runRestore(target)
                }) { Text("استعادة") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("إلغاء") } }
        )
    }

    if (showCurrencyDialog) {
        CurrencyPickerDialog(
            current = currency,
            onDismiss = { showCurrencyDialog = false },
            onSelect = { selected ->
                currency = selected
                settings.currencySymbol = selected
                AppSettingsState.setCurrency(selected)
                showCurrencyDialog = false
            }
        )
    }

    pendingUpdate?.let { manifest ->
        AlertDialog(
            onDismissRequest = { if (!isDownloadingUpdate) pendingUpdate = null },
            title = { Text("يتوفر تحديث جديد 🎉") },
            text = {
                Column {
                    Text("الإصدار ${manifest.versionName} متوفر الآن (نسختك الحالية: ${appVersion.name}).")
                    if (manifest.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(manifest.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    if (isDownloadingUpdate) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { downloadPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("$downloadPercent%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDownloadingUpdate,
                    onClick = { startDownload(manifest) }
                ) { Text("تحميل وتثبيت") }
            },
            dismissButton = {
                TextButton(enabled = !isDownloadingUpdate, onClick = { pendingUpdate = null }) { Text("لاحقاً") }
            }
        )
    }

    if (needsInstallPermission) {
        AlertDialog(
            onDismissRequest = { needsInstallPermission = false },
            title = { Text("يلزم إذن التثبيت") },
            text = { Text("لتثبيت التحديث من داخل التطبيق، فعّل \"السماح من هذا المصدر\" لهذا التطبيق ثم عد وحاول مجدداً.") },
            confirmButton = {
                TextButton(onClick = {
                    needsInstallPermission = false
                    context.startActivity(ApkDownloader.unknownSourcesSettingsIntent(context))
                }) { Text("فتح الإعدادات") }
            },
            dismissButton = {
                TextButton(onClick = {
                    needsInstallPermission = false
                    downloadedApk?.let { ApkDownloader.install(context, it) }
                }) { Text("حاول التثبيت الآن") }
            }
        )
    }
}

/**
 * One tappable swatch per [AppColorPalette] — a small circle split
 * diagonally between the palette's two brand-gradient colors so the person
 * can see the actual hue pair before picking it, plus a checkmark and label
 * on the currently-selected one. Kept as a fixed circle+ring instead of a
 * RadioButton row (like the theme-mode picker above it) because color is
 * inherently visual — reading "نيلي" vs "زمردي" as text doesn't tell you
 * what either looks like, seeing the swatch does.
 */
@Composable
private fun ColorPaletteSwatch(palette: AppColorPalette, selected: Boolean, onClick: () -> Unit) {
    val colors = remember(palette) { paletteColorsFor(palette) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.88f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = MotionSpecs.pressSpring(),
        label = "paletteSwatchScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            Modifier
                .scale(scale)
                .size(38.dp)
                .shadow(if (selected) 4.dp else 0.dp, CircleShape, clip = false)
                .background(
                    Brush.linearGradient(listOf(colors.gradientStart, colors.gradientEnd)),
                    CircleShape
                )
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun CurrencyPickerDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var custom by remember { mutableStateOf(current.takeIf { it !in CURRENCY_OPTIONS } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر العملة") },
        text = {
            Column {
                CURRENCY_OPTIONS.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == option, onClick = { onSelect(option) })
                        Text(option)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text("عملة أخرى") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = custom.isNotBlank(),
                onClick = { onSelect(custom.trim()) }
            ) { Text("استخدام") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين رمز PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("رمز من 4 إلى 6 أرقام") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("تأكيد الرمز") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = "الرمز لازم يكون 4 أرقام على الأقل"
                    pin != confirm -> error = "الرمزان غير متطابقين"
                    else -> onConfirm(pin)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun formatBackupDate(timestampMillis: Long): String =
    runCatching {
        SimpleDateFormat("yyyy/MM/dd — HH:mm", Locale("ar")).format(java.util.Date(timestampMillis))
    }.getOrDefault("—")

private fun buildBackupText(
    debtsState: com.shopmanager.app.ui.debts.DebtsUiState,
    materialsState: com.shopmanager.app.ui.materials.MaterialsUiState,
    currency: String
): String {
    val nf = NumberFormat.getNumberInstance(Locale("ar"))
    val sb = StringBuilder()
    sb.append("📋 نسخة احتياطية — إدارة المحل\n")
    sb.append("=".repeat(24)).append("\n\n")

    sb.append("💰 الديون (${debtsState.persons.size} عميل، الإجمالي ${nf.format(debtsState.totalAmount)} $currency)\n")
    debtsState.persons.sortedByDescending { it.amount }.forEach { p ->
        sb.append("• ${p.name}: ${nf.format(p.amount)} $currency\n")
    }

    sb.append("\n📦 المواد (${materialsState.materials.size})\n")
    materialsState.materials.sortedBy { it.name }.forEach { m ->
        val price = materialsState.prices[m.name]
        sb.append("• ${m.name}: ${m.quantityLabel()}")
        if (price != null) sb.append(" — ${nf.format(price)} $currency")
        sb.append("\n")
    }

    sb.append("\nتم الإنشاء تلقائيًا من التطبيق.")
    return sb.toString()
}
