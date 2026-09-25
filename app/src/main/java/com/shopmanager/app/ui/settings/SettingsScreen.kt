package com.shopmanager.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shopmanager.app.data.backup.BackupManager
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.materials.MaterialsRepository
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.data.updates.ApkDownloader
import com.shopmanager.app.data.updates.AppVersion
import com.shopmanager.app.data.updates.AppVersionInfo
import com.shopmanager.app.data.updates.DownloadState
import com.shopmanager.app.data.updates.UpdateCheckResult
import com.shopmanager.app.data.updates.UpdateChecker
import com.shopmanager.app.data.updates.UpdateManifest
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.shopmanager.app.ui.common.AppSettingsState
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.GlassIconButton
import com.shopmanager.app.ui.common.MotionSpecs
import com.shopmanager.app.ui.common.ShareFormatDialog
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.debts.DebtsViewModel
import com.shopmanager.app.data.materials.quantityLabel
import com.shopmanager.app.data.notifications.NotificationHelper
import com.shopmanager.app.data.notifications.NotificationSync
import com.shopmanager.app.ui.materials.MaterialsViewModel
import com.shopmanager.app.ui.theme.AppThemeMode
import com.shopmanager.app.ui.theme.LocalBrandGradientColors
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val CURRENCY_OPTIONS = listOf("ل.س", "$", "SAR", "AED", "TRY")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (AppThemeMode) -> Unit,
    onPerformancePreferenceChanged: (PerformanceMode) -> Unit = {},
    onRecheckDevicePerformance: () -> Unit = {},
    debtsViewModel: DebtsViewModel? = null,
    materialsViewModel: MaterialsViewModel? = null,
    onOpenHelp: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var hasPin by remember { mutableStateOf(settings.hasPin) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(settings.currencySymbol) }
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    // "المزامنة الفورية بالخلفية": مفتاح مستقل لكل جهاز (انظر RealtimeSyncService).
    var realtimeSyncEnabled by remember { mutableStateOf(settings.realtimeSyncEnabled) }
    // BUG FIXED ("notifications sometimes never arrive at all", root
    // cause): the switch above only ever reflected this app's OWN saved
    // preference — it had no idea whether Android itself was actually
    // allowed to show a notification for this app. Denying the one-time
    // permission prompt on first launch (or turning notifications off for
    // this app later from the system Settings app, or a channel getting
    // silently disabled by the OS) all leave this switch showing "on" with
    // nothing in the UI hinting that nothing will actually arrive — every
    // notification call in NotificationHelper was already silently
    // no-op'ing in exactly that case (see its hasPermission check), just
    // with zero visibility into it from here. This now reads the real
    // system-level state directly (covers both the API 33+ runtime
    // permission and the general per-app notification toggle that exists
    // on every Android version) and shows a clear warning + a direct link
    // to the system notification settings for this app whenever the two
    // disagree, instead of the switch quietly lying.
    // "اصلح ميزات الاشعارات": beyond the single app-level flag, also check
    // each notification channel's own importance — see
    // NotificationHelper.blockedChannelLabels for why the app-level check
    // alone used to miss a channel the person disabled individually.
    var blockedChannelLabels by remember { mutableStateOf(emptyList<String>()) }
    fun checkSystemNotificationsAllowed(): Boolean {
        blockedChannelLabels = NotificationHelper.blockedChannelLabels(context)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && blockedChannelLabels.isEmpty()
    }
    var systemNotificationsAllowed by remember { mutableStateOf(checkSystemNotificationsAllowed()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Re-check on every resume, not just once — this is exactly how
        // someone comes back after tapping the warning's "open settings"
        // button below and flipping the OS toggle there.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotificationsAllowed = checkSystemNotificationsAllowed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var performanceMode by remember { mutableStateOf(settings.performanceMode) }
    var recheckTick by remember { mutableStateOf(0) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showExportShareChoice by remember { mutableStateOf(false) }
    val brandColor = LocalBrandGradientColors.current.first().toArgb()

    // النسخ الاحتياطي التلقائي المحلي — silent daily local backup, restore
    // only from here or from the automatic "server unavailable" prompt.
    val scope = rememberCoroutineScope()
    val debtsRepoForBackup = remember { DebtsRepository() }
    val materialsRepoForBackup = remember { MaterialsRepository() }
    var backups by remember { mutableStateOf(BackupManager.listBackups(context)) }
    var pendingRestore by remember { mutableStateOf<BackupManager.BackupInfo?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreStatus by remember { mutableStateOf<String?>(null) }

    // FEATURE ADDED ("استعادة نسخة تم تصديرها"): export/import a REAL JSON
    // file (as opposed to the plain-text share above, which can't be read
    // back) via the system's own "Save as.../Open..." pickers — no storage
    // permission needed since the person themselves chooses the location
    // through Android's Storage Access Framework.
    var isExportingFile by remember { mutableStateOf(false) }
    var exportFileStatus by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && debtsViewModel != null && materialsViewModel != null) {
            isExportingFile = true
            exportFileStatus = null
            scope.launch {
                try {
                    val json = BackupManager.buildSnapshotJson(debtsRepoForBackup, materialsRepoForBackup)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
                        } ?: throw java.io.IOException("تعذر فتح الملف للكتابة")
                    }
                    exportFileStatus = "تم حفظ الملف بنجاح ✅ يمكنك نقله لجهاز آخر أو حفظه بمكان آمن."
                } catch (e: Exception) {
                    exportFileStatus = "تعذر التصدير: ${e.message ?: "حاول مرة أخرى"}"
                } finally {
                    isExportingFile = false
                }
            }
        }
    }
    // OpenDocument (not GetContent): keeps read access to the exact file
    // the person picked long enough for the confirmation dialog below to
    // actually read it, and doesn't require any broad storage permission.
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImportUri = uri }

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

    // المزامنة: real connectivity + "آخر مزامنة ناجحة" (see data/sync/SyncStatus.kt).
    // collectAsState(initial=...) reads the connectivity synchronously on
    // first composition instead of assuming "متصل" until the first
    // callback lands, which would otherwise flash the wrong state for a
    // frame on a device that opens this screen while already offline.
    val isOnline by com.shopmanager.app.data.sync.SyncConnectivityObserver.observe(context)
        .collectAsState(initial = true)
    var lastSyncedAt by remember { mutableStateOf(com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)) }
    var isManualSyncing by remember { mutableStateOf(false) }
    // Re-read the stored timestamp whenever a listener records a fresh
    // success (debtsSyncError/materialsSyncError flipping back to false is
    // the same signal DebtsViewModel/MaterialsViewModel already use to
    // call SyncStatusStore.recordSuccess) so this stays live without its
    // own polling loop.
    LaunchedEffect(debtsSyncError, materialsSyncError) {
        lastSyncedAt = com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
    }
    // BUG FIXED (زر "مزامنة الآن" يبدو أنه لا يعمل): the effect above is
    // the ONLY thing that ever refreshed [lastSyncedAt] on this screen —
    // and it's keyed on the sync-error flags, which stay `false` the
    // entire time everything is working normally. So on the overwhelming
    // majority of taps (nothing was actually broken, the listener was just
    // stuck in a backoff or the connection briefly flapped) the value
    // shown never changed: forceReconnect() below genuinely reconnects and
    // the listeners genuinely record a new success, but this screen kept
    // displaying whatever timestamp it first loaded with, no matter how
    // many times the button was pressed - indistinguishable from the
    // button doing nothing at all. A light poll while this screen is open
    // picks up that same already-correct signal on a timer instead of
    // waiting for an error to flip first.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            val fresh = com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
            if (fresh != lastSyncedAt) lastSyncedAt = fresh
        }
    }

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

    /** Same as [runRestore], reading the JSON from a person-picked file
     * (SAF Uri) instead of the on-device snapshot — see
     * [BackupManager.restoreFromJsonText]. */
    fun runRestoreFromUri(uri: Uri) {
        isRestoring = true
        restoreStatus = null
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw java.io.IOException("تعذر فتح الملف")
                }
                BackupManager.restoreFromJsonText(text, debtsRepoForBackup, materialsRepoForBackup)
                restoreStatus = "تمت الاستعادة من الملف بنجاح ✅"
                dismissedServerErrorBanner = true
            } catch (e: Exception) {
                restoreStatus = "تعذرت الاستعادة: ${e.message ?: "تأكد أن الملف نسخة احتياطية صالحة"}"
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
                title = { Text("الإعدادات", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
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
                )
                // UNIFIED ON CLAUDE'S DESIGN: removed the old boxed
                // liquidGlassSurface panel (rounded bottom corners) this bar
                // used to sit on — it now sits flush on the plain background
                // like Home's own header and Claude's own "Settings" screen.
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
            // REDESIGN ("شاشة الاعدادات... عدلهم" — matching the reference
            // screen's own top hero card): opens with a branded card first,
            // above every grouped option list below it.
            AppHeroCard(appVersion = appVersion, onOpenHelp = onOpenHelp)

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
                    IosOptionRow(
                        label = when (mode) {
                            AppThemeMode.SYSTEM -> "حسب النظام"
                            AppThemeMode.LIGHT -> "فاتح"
                            AppThemeMode.DARK -> "داكن"
                        },
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            settings.themeMode = mode
                            onThemeChanged(mode)
                        }
                    )
                }
                // "شيل الألوان، خليه بس ليلي/نهاري": لا وجود لأي خيار لون
                // بعد اليوم — فاتح/داكن/حسب النظام فقط. لا حاجة لعرض
                // ColorModeSection أصلاً بما إنه ما عاد له أي تأثير على
                // شكل التطبيق (راجع ShopManagerTheme في Theme.kt).
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
                            // يطابق المستمعات والخدمة الأمامية مع الإعداد فوراً.
                            NotificationSync.apply(context)
                        }
                    )
                }
                AnimatedVisibility(
                    visible = notificationsEnabled,
                    enter = fadeIn(MotionSpecs.contentTween()) + expandVertically(),
                    exit = fadeOut(MotionSpecs.contentTween()) + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("المزامنة الفورية بالخلفية")
                                Text(
                                    "تصلك إشعارات الأجهزة الأخرى لحظة حدوثها حتى والتطبيق مغلق (يظهر إشعار صامت صغير دائم). أوقفها على الهاتف الضعيف ليكتفي بفحص دوري كل 15–30 دقيقة أو أكثر",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = realtimeSyncEnabled,
                                onCheckedChange = {
                                    realtimeSyncEnabled = it
                                    settings.realtimeSyncEnabled = it
                                    NotificationSync.apply(context)
                                }
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = notificationsEnabled && !systemNotificationsAllowed,
                    enter = fadeIn(MotionSpecs.contentTween()) + expandVertically(),
                    exit = fadeOut(MotionSpecs.contentTween()) + shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
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
                                        "الإشعارات موقوفة من نظام الجهاز",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    // "اصلح ميزات الاشعارات": distinguishes the
                                    // all-or-nothing case (app-level toggle off)
                                    // from a specific channel being disabled —
                                    // see blockedChannelLabels above.
                                    if (blockedChannelLabels.isNotEmpty())
                                        "المفتاح أعلاه مفعّل، لكن نظام أندرويد يمنع تحديداً هذه الإشعارات: ${blockedChannelLabels.joinToString("، ")} — لن تصلك حتى تُفعّلها من إعدادات إشعارات التطبيق."
                                    else
                                        "المفتاح أعلاه مفعّل، لكن نظام أندرويد يمنع هذا التطبيق تحديداً من إظهار أي إشعار على هذا الجهاز — لن تصلك تنبيهات النواقص أو الديون الجديدة مهما حدث بالتطبيق حتى تُفعّلها من إعدادات الجهاز.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    context.startActivity(intent)
                                }) { Text("فتح إعدادات الإشعارات") }
                            }
                        }
                    }
                }
            }

            // الحماية (security / PIN lock) — REORGANIZED ("اعد ترتيب
            // الشاشة"): moved up next to الإشعارات so the two "protect my
            // device/data" toggles sit together near the top, ahead of the
            // more technical المزامنة/الأداء sections below.
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

            // المزامنة — new section: real connectivity + last successful
            // sync time + a manual "مزامنة الآن" retry, built on the new
            // sync helper layer (data/sync/SyncStatus.kt) instead of the
            // old sync-error banner being the only signal in the whole
            // screen about sync health.
            SettingsSection(title = "المزامنة", icon = Icons.Default.CloudDownload) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) LocalSemanticColors.current.success else MaterialTheme.colorScheme.error)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isOnline) "متصل" else "غير متصل بالإنترنت", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (lastSyncedAt != null) "آخر مزامنة ناجحة: ${formatBackupDate(lastSyncedAt!!)}"
                    else "لم تتم أي مزامنة على هذا الجهاز بعد",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        isManualSyncing = true
                        scope.launch {
                            val before = lastSyncedAt
                            com.shopmanager.app.data.sync.SyncRetry.forceReconnect()
                            // BUG FIXED: forceReconnect() only drops/reopens
                            // the connection - it returns as soon as that
                            // handshake completes, not once a listener has
                            // actually received fresh data and called
                            // SyncStatusStore.recordSuccess(). Reading the
                            // timestamp immediately after almost always read
                            // the OLD value, so the button appeared to do
                            // nothing even when the reconnect genuinely
                            // worked. Give the listeners a short window to
                            // report back in before giving up the spinner.
                            var waited = 0L
                            while (waited < 5000) {
                                val fresh = com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
                                if (fresh != before) {
                                    lastSyncedAt = fresh
                                    break
                                }
                                kotlinx.coroutines.delay(250)
                                waited += 250
                            }
                            lastSyncedAt = com.shopmanager.app.data.sync.SyncStatusStore.lastSyncedAt(context)
                            isManualSyncing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isManualSyncing
                ) {
                    if (isManualSyncing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جاري إعادة المحاولة...")
                    } else {
                        Text("مزامنة الآن")
                    }
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
                    IosOptionRow(
                        label = label,
                        selected = performanceMode == mode,
                        onClick = {
                            performanceMode = mode
                            settings.performanceMode = mode
                            onPerformancePreferenceChanged(mode)
                        }
                    )
                }
                // FEATURE ADDED: DevicePerformance.resetCachedTier already
                // existed for exactly this ("Not wired to any screen yet"
                // per its own doc comment) but had no UI hook anywhere —
                // a device misclassified on its very first launch (e.g.
                // caught mid-boot, or a borderline RAM/core reading) had
                // no way to be re-measured short of a full reinstall. This
                // only matters in "تلقائي" mode — a manual HIGH/LOW choice
                // already overrides detection outright regardless of what
                // it says.
                if (performanceMode == PerformanceMode.AUTO) {
                    Spacer(Modifier.height(6.dp))
                    // FEATURE ADDED: shows the raw signals behind the
                    // detection instead of it being an opaque decision —
                    // uses DevicePerformance.currentDeviceInfo (fresh
                    // measurement) so this stays accurate right after
                    // tapping "إعادة فحص" below, not just on first load.
                    val deviceInfo = remember(recheckTick) {
                        com.shopmanager.app.data.performance.DevicePerformance.currentDeviceInfo(context)
                    }
                    Text(
                        "الجهاز الحالي: ${deviceInfo.totalRamMb} MB رام، ${deviceInfo.cores} أنوية" +
                            (if (deviceInfo.osFlaggedLowRam) " — مصنّف من النظام كجهاز منخفض الموارد" else "") +
                            // FEATURE ADDED ("اصلاحات للاجهزة اللي فيها معالج
                            // رسوميات ضعيف"): surfaces the new GPU signal
                            // alongside RAM/cores so "kind of an OK phone but
                            // still landed on LOW" isn't a mystery anymore.
                            (if (deviceInfo.weakGpu) " — معالج رسوميات ضعيف/قديم" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        onRecheckDevicePerformance()
                        recheckTick++
                    }) { Text("إعادة فحص أداء الجهاز") }
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
                        onClick = { showExportShareChoice = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LocalSemanticColors.current.success)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تصدير نسخة احتياطية الآن")
                    }

                    // FEATURE ADDED ("استعادة نسخة تم تصديرها"): the button
                    // above only ever produces a human-readable text share —
                    // fine to read, impossible to load back into the app.
                    // This exports the actual structured JSON snapshot (same
                    // shape as the automatic on-device one below) to a real
                    // file the person chooses the location for, which the
                    // "استعادة من ملف" button further down can then read
                    // back in — on this phone or a new one.
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "أو صدّر نسخة كاملة كملف (JSON) يمكن استعادتها لاحقًا على هذا الجهاز أو جهاز آخر.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
                            exportFileLauncher.launch("shop_manager_backup_$stamp.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExportingFile
                    ) {
                        if (isExportingFile) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ الحفظ...")
                        } else {
                            Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تصدير نسخة كملف (JSON)")
                        }
                    }
                    exportFileStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // النسخ الاحتياطي التلقائي المحلي (silent local backup) — new feature
            SettingsSection(title = "النسخ الاحتياطي التلقائي", icon = Icons.Default.SettingsBackupRestore) {
                Text(
                    "يحتفظ التطبيق دائمًا بآخر نسخة كاملة من بياناتك على هذا الجهاز فقط، تُحدَّث تلقائيًا وبصمت (بدون أي إشعار) بعد كل حفظ جديد — أي نسخة أقدم تُحذف فورًا لأنها لم تعد مطلوبة. لا تُستعاد هذه النسخة إلا من هنا، أو إذا تعذّر الاتصال بالخادم.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (backups.isEmpty()) {
                    Text(
                        "لا توجد نسخة بعد — ستُنشأ تلقائيًا مع أول حفظ.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val backup = backups.first()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "آخر نسخة: ${formatBackupDate(backup.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
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

                // FEATURE ADDED ("استعادة نسخة تم تصديرها"): restores from a
                // JSON file the person picks (one exported earlier from
                // "تصدير نسخة كملف" above — on this phone or another one
                // signed into the same shop), not just the automatic
                // on-device snapshot above. Shares isRestoring/restoreStatus
                // with the on-device restore since only one restore ever
                // runs at a time either way.
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(10.dp))
                Text(
                    "أو استعد نسخة من ملف JSON تم تصديره سابقًا (من هذا الجهاز أو جهاز آخر).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRestoring
                ) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("استعادة من ملف")
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
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
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

            // حول التطبيق — تم حذف زر "دليل الاستخدام" المكرر من هنا
            // (BUG FIXED: "دليل المستخدم صار موجود مرتين") — الزر الوحيد
            // له الآن هو الزر البارز أعلى الشاشة في AppHeroCard؛ هذا القسم
            // صار مخصصًا فقط للروابط التي لا تظهر في مكان آخر.
            SettingsSection(title = "حول التطبيق", icon = Icons.Default.Info) {
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
        GlassAlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("استعادة نسخة احتياطية؟") },
            text = {
                Text(
                    "سيتم استبدال كل الديون والعملاء والمواد والأسعار الحالية بمحتوى نسخة ${formatBackupDate(backup.createdAt)}. " +
                        "لا يمكن التراجع عن هذا بعد التنفيذ."
                )
            },
            confirmButton = {
                // BUG FIXED ("بدي الضغطة بشكل مربع كامل وليس دائري"): see
                // PersonEditDialog.kt's doc comment.
                TextButton(shape = RectangleShape, onClick = {
                    val target = backup
                    pendingRestore = null
                    runRestore(target)
                }) { Text("استعادة") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { pendingRestore = null }) { Text("إلغاء") } }
        )
    }

    pendingImportUri?.let { uri ->
        GlassAlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("استعادة من ملف؟") },
            text = {
                Text(
                    "سيتم استبدال كل الديون والعملاء والمواد والأسعار الحالية بمحتوى الملف المختار. " +
                        "تأكد أنه ملف نسخة احتياطية صحيح تم تصديره من هذا التطبيق. لا يمكن التراجع عن هذا بعد التنفيذ."
                )
            },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    val target = uri
                    pendingImportUri = null
                    runRestoreFromUri(target)
                }) { Text("استعادة") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { pendingImportUri = null }) { Text("إلغاء") } }
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

    if (showExportShareChoice && debtsViewModel != null && materialsViewModel != null) {
        ShareFormatDialog(
            onDismiss = { showExportShareChoice = false },
            onPickImage = {
                // PERF: this is the largest of the three reports (debts +
                // materials combined), so moving the Canvas work off the
                // main thread matters most here — same reasoning as
                // MaterialsScreen/DebtsScreen's onPickImage.
                val debtsState = debtsViewModel.uiState.value
                val materialsState = materialsViewModel.uiState.value
                scope.launch {
                    val uri = withContext(Dispatchers.Default) {
                        WholeAppReportImage.generate(
                            context = context,
                            persons = debtsState.persons,
                            totalDebt = debtsState.totalAmount,
                            materials = materialsState.materials,
                            prices = materialsState.prices,
                            currency = currency,
                            brandColor = brandColor
                        )
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
                }
            },
            onPickText = {
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
            }
        )
    }

    pendingUpdate?.let { manifest ->
        GlassAlertDialog(
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
                    shape = RectangleShape,
                    onClick = { startDownload(manifest) }
                ) { Text("تحميل وتثبيت") }
            },
            dismissButton = {
                TextButton(enabled = !isDownloadingUpdate, shape = RectangleShape, onClick = { pendingUpdate = null }) { Text("لاحقاً") }
            }
        )
    }

    if (needsInstallPermission) {
        GlassAlertDialog(
            onDismissRequest = { needsInstallPermission = false },
            title = { Text("يلزم إذن التثبيت") },
            text = { Text("لتثبيت التحديث من داخل التطبيق، فعّل \"السماح من هذا المصدر\" لهذا التطبيق ثم عد وحاول مجدداً.") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = {
                    needsInstallPermission = false
                    context.startActivity(ApkDownloader.unknownSourcesSettingsIntent(context))
                }) { Text("فتح الإعدادات") }
            },
            dismissButton = {
                TextButton(shape = RectangleShape, onClick = {
                    needsInstallPermission = false
                    downloadedApk?.let { ApkDownloader.install(context, it) }
                }) { Text("حاول التثبيت الآن") }
            }
        )
    }
}


/**
 * iOS 26 REDESIGN ("عدل شاشة الاعدادات بتصميم جميل"): the old wrapper drew
 * every group as its own [ElevatedCard] — a raised, shadowed white/dark
 * rectangle with the group's icon+title *inside* it as its own row. Real
 * iOS Settings never puts a shadowed card around each group: it's a flat,
 * borderless "grouped inset list" — a small caps-style gray label floats
 * *above* a plain rounded surface, and that surface has no elevation of
 * its own at all. Kept the exact same signature (title, icon, content)
 * so every one of this screen's ~10 call sites needed zero changes — only
 * how the group itself is drawn changed.
 *
 * The icon now sits inside a small colored rounded-square "badge" before
 * the label, the way iOS Settings badges each group's icon (a colored
 * square rather than a bare tinted glyph) — and the whole group fades +
 * rises in on first composition instead of just snapping into place.
 */
/**
 * iOS 26 REDESIGN: replaces a plain [RadioButton] + label row with the
 * way iOS itself shows a single-choice list — the whole row is tappable,
 * the selected option gets a trailing checkmark instead of a filled
 * circle on the leading edge, and there's no visible "control" at all on
 * the unselected rows (just the label) the way a Material RadioButton
 * always draws its empty ring.
 */
@Composable
private fun IosOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(MotionSpecs.popInSpring()) + scaleIn(MotionSpecs.popInSpring(), initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

// REDESIGN ("شاشة الاعدادات... عدلهم" — matching the reference screen's own
// top card): the reference opens with a branded card — name, a short
// description, then one centered full-width pill button — sitting above
// every grouped option list, not buried as just another list item near the
// bottom. Same shape here: "إدارة المحل" name/description/version replace
// the reference's own app name/tagline, and "دليل الاستخدام" (the user's
// guide) fills the same slot as the reference's own action button. Uses the
// same borderless `surfaceContainer` card language as SettingsSection below
// it, just with its own centered layout instead of a left-aligned list.
@Composable
private fun AppHeroCard(appVersion: AppVersion, onOpenHelp: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "إدارة المحل",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "تطبيق واحد لإدارة الديون والمواد والأسعار، مبني خصيصًا لمحلك ويعمل حتى بدون اتصال دائم بالإنترنت.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "الإصدار ${appVersion.name} — تطوير سلمان",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenHelp,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("دليل الاستخدام")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MotionSpecs.contentTween()) + expandVertically(MotionSpecs.expandSpring())
    ) {
        // Claude-app style: no colored badge box above the group — just a
        // plain, single-color outline icon in front of a quiet label, the
        // same weight as the rows inside it, then a softly-rounded flat
        // card with no border seam (Claude's Settings groups sit directly
        // on the dark/cream background with only a faint tonal lift, not a
        // hairline outline).
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(start = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(Modifier.padding(16.dp), content = content)
            }
        }
    }
}

@Composable
private fun CurrencyPickerDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var custom by remember { mutableStateOf(current.takeIf { it !in CURRENCY_OPTIONS } ?: "") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر العملة") },
        text = {
            Column {
                CURRENCY_OPTIONS.forEach { option ->
                    IosOptionRow(label = option, selected = current == option, onClick = { onSelect(option) })
                }
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = "عملة أخرى",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = custom.isNotBlank(),
                shape = RectangleShape,
                onClick = { onSelect(custom.trim()) }
            ) { Text("استخدام") }
        },
        dismissButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين رمز PIN") },
        text = {
            Column {
                AppTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = "رمز من 4 إلى 6 أرقام",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = "تأكيد الرمز",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(shape = RectangleShape, onClick = {
                when {
                    pin.length < 4 -> error = "الرمز لازم يكون 4 أرقام على الأقل"
                    pin != confirm -> error = "الرمزان غير متطابقين"
                    else -> onConfirm(pin)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun formatBackupDate(timestampMillis: Long): String =
    runCatching {
        // 12-hour clock (was HH:mm/24h) — "a" renders as ص/م in Arabic locale.
        SimpleDateFormat("yyyy/MM/dd — h:mm a", Locale("ar")).format(java.util.Date(timestampMillis))
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
