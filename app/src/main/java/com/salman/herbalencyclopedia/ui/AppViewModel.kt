package com.salman.herbalencyclopedia.ui

import com.salman.herbalencyclopedia.data.model.UnansweredQuestion
import com.salman.herbalencyclopedia.data.model.WebHerb
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salman.herbalencyclopedia.BuildConfig
import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.ai.HerbCategoryLookup
import com.salman.herbalencyclopedia.data.ai.TrainedExample
import com.salman.herbalencyclopedia.data.model.AppUpdateConfig
import com.salman.herbalencyclopedia.data.model.AppUpdateInfo
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.BlockedUser
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Feedback
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.data.repository.AppContainer
import com.salman.herbalencyclopedia.data.repository.HerbRepository
import com.salman.herbalencyclopedia.data.repository.PreferencesRepository
import com.salman.herbalencyclopedia.data.update.UpdateDownloadService
import com.salman.herbalencyclopedia.data.update.UpdateDownloadState
import com.salman.herbalencyclopedia.data.update.UpdateDownloadStatus
import com.salman.herbalencyclopedia.data.update.UpdateNotifier
import com.salman.herbalencyclopedia.ui.util.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import com.salman.herbalencyclopedia.data.image.GithubImageUploader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class UiState(
    val herbs: List<Herb> = emptyList(),
    val categories: List<Category> = emptyList(),
    val blends: List<Blend> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** State of an in-app "check for updates" action (see [AppViewModel.checkForUpdate]). */
sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data object UpToDate : UpdateCheckState()
    data class Available(val info: AppUpdateInfo) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

class AppViewModel(private val container: AppContainer) : ViewModel() {

    companion object {
        /** أقصى مدة انتظار لكل محاولة مزامنة من Firestore قبل اعتبارها فاشلة - راجع [syncCatalogFromServer]. */
        private const val CATALOG_SYNC_TIMEOUT_MS = 40_000L

        /**
         * عدد محاولات المزامنة التلقائية الداخلية قبل إظهار رسالة الخطأ
         * للمستخدم - راجع [syncCatalogFromServer]. كانت هناك محاولة واحدة
         * فقط، فيضطر المستخدم (خصوصاً عند أول تشغيل بلا أي كاش Firestore
         * داخلي بعد، على اتصال ضعيف) للضغط يدوياً على "حاول مرة أخرى" عدة
         * مرات حتى تصادف إحدى المحاولات نجاحاً. الآن التطبيق نفسه يكرر
         * المحاولة داخلياً (بمهلة قصيرة متزايدة بين كل محاولة) قبل أن يعرض
         * أي خطأ للمستخدم.
         */
        private const val MAX_CATALOG_SYNC_ATTEMPTS = 3

        /** المهلة بين محاولات المزامنة الداخلية المتتالية - راجع [syncCatalogFromServer]. */
        private const val CATALOG_SYNC_RETRY_DELAY_MS = 1_500L
        private const val CATALOG_FORCE_REFRESH_MS = 7L * 24 * 60 * 60 * 1000
        private const val VERSION_CHECK_TIMEOUT_MS = 8_000L
        private const val CATALOG_CACHE_SAVE_DELAY_MS = 1_500L
        private const val SYNC_START_FRACTION = 0.03f
        private const val SYNC_WEIGHT_CATEGORIES = 0.15f
        private const val SYNC_WEIGHT_HERBS = 0.62f
        private const val SYNC_WEIGHT_BLENDS = 0.15f
        private const val SYNC_MAX_BEFORE_DONE = 0.97f
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // القوائم الخام (قبل الترجمة) لآخر نسخة معروفة من الموسوعة - إما من
    // الكاش المحلي (PreferencesRepository) أو من مزامنة شبكية. تُبقى منفصلة
    // عن uiState لأن uiState يحمل النسخة *المترجَمة*، وتغيّر اللغة وحده يجب
    // أن يعيد ترجمة هذه القوائم دون طلب شبكي جديد - راجع الـ combine في init.
    private val _rawCategories = MutableStateFlow<List<Category>>(emptyList())
    private val _rawHerbs = MutableStateFlow<List<Herb>>(emptyList())
    private val _rawBlends = MutableStateFlow<List<Blend>>(emptyList())

    // المزامنة الحيّة (addSnapshotListener) صارت محصورة بجلسة الأدمن فقط -
    // راجع startAdminLiveSync/stopAdminLiveSync وتعليق init أدناه لسبب ذلك.
    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private var syncJob: Job? = null
    private var adminSyncJob: Job? = null

    // مستمع الخلطات الحيّ لجلسة الأدمن - مستقل عمداً عن adminSyncJob أعلاه
    // (راجع توثيق startAdminLiveSync لسبب هذا الفصل).
    private var blendsSyncJob: Job? = null

    val favoriteIds: StateFlow<Set<String>> = container.preferencesRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * أعشاب/أسماء بديلة تعلّمها سيمو من الإنترنت (مخزَّنة في Firestore مع كاش محلي دائم،
     * فتبقى متاحة بلا اتصال). تُمرَّر لشاشة سيمو فتُدمَج مع الموسوعة محلياً.
     */
    val webHerbs: StateFlow<List<WebHerb>> = container.semoWebHerbRepository.observeAll()
        .catch { /* انتهازي: فشل الشبكة لا يوقف سيمو */ }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun contributeWebHerb(herb: WebHerb) {
        viewModelScope.launch { container.semoWebHerbRepository.contribute(herb) }
    }

    fun logUnansweredQuestion(question: String) {
        viewModelScope.launch { container.semoWebHerbRepository.logUnanswered(question) }
    }

    // ── للمطوّر (القواعد ترفضها لغيره) ──
    val unansweredQuestions: StateFlow<List<UnansweredQuestion>> =
        container.semoWebHerbRepository.observeUnanswered()
            .catch { }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** ينقل عشبة الإنترنت إلى الموسوعة الحقيقية (بلا وسم التحذير، بلا فئة) ثم يحذف السجل المؤقت. */
    fun promoteWebHerb(web: WebHerb, onDone: (Boolean, String?) -> Unit) {
        val herb = web.copy(approved = true).toHerb()
        if (herb == null) { onDone(false, null); return }
        viewModelScope.launch {
            try {
                container.herbRepository.addHerb(herb.copy(id = ""))
                container.semoWebHerbRepository.delete(web.id)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    fun approveWebHerb(id: String) { viewModelScope.launch { container.semoWebHerbRepository.approve(id) } }
    fun deleteWebHerb(id: String) { viewModelScope.launch { container.semoWebHerbRepository.delete(id) } }
    fun deleteUnanswered(id: String) { viewModelScope.launch { container.semoWebHerbRepository.deleteUnanswered(id) } }

    /** لغة واجهة التطبيق الحالية (راجع [AppLanguage] وSettingsScreen). */
    val appLanguage: StateFlow<AppLanguage> = container.preferencesRepository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.ARABIC)

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { container.preferencesRepository.setAppLanguage(language) }
    }

    var isLoggedIn by mutableStateOf(container.authRepository.isAdmin)
        private set
    var isAdmin by mutableStateOf(container.authRepository.isAdmin)
        private set

    // ---------------------------------------------------------------------
    // Update check -> direct APK download hand-off
    // ---------------------------------------------------------------------

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    // التحميل الفعلي الآن يتم بخدمة أمامية حقيقية (UpdateDownloadService)
    // تعمل بالخلفية حتى لو أُغلقت شاشة التطبيق — راجع توثيق
    // UpdateDownloadStatus لماذا هذا التغيير ضروري. الحالة هنا مجرد
    // انعكاس مباشر لما تكتبه الخدمة، بلا أي تغيير بمنطق الواجهة.
    val downloadState: StateFlow<UpdateDownloadState> = UpdateDownloadStatus.state

    private val _updateConfig = MutableStateFlow(AppUpdateConfig())
    val updateConfigState: StateFlow<AppUpdateConfig> = _updateConfig.asStateFlow()


    private suspend fun readAppVersion(context: Context): Pair<Int, String> = withContext(Dispatchers.IO) {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = pkgInfo?.versionName ?: "0.0.0"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            (pkgInfo?.longVersionCode ?: 0L).toInt()
        } else {
            @Suppress("DEPRECATION") (pkgInfo?.versionCode ?: 0)
        }
        versionCode to versionName
    }

    /** Reads the app's own installed version and checks the configured GitHub repo for a newer release. */
    fun checkForUpdate(context: Context) {
        if (_updateState.value == UpdateCheckState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateCheckState.Checking
            val (versionCode, versionName) = readAppVersion(context)
            val result = runCatching { container.updateRepository.checkForUpdate(versionCode, versionName) }
            _updateState.value = result.fold(
                onSuccess = { info -> if (info != null) UpdateCheckState.Available(info) else UpdateCheckState.UpToDate },
                onFailure = { e -> UpdateCheckState.Error(e.localizedMessage ?: "تعذّر التحقق من التحديثات") }
            )
        }
    }

    /**
     * قدرة جديدة: تحقّق تلقائي وصامت من توفّر تحديث، يُستدعى مرة واحدة من
     * [init] أدناه — أي في كل مرة يُفتح فيها التطبيق فعلياً، بخلاف
     * [checkForUpdate] أعلاه الذي يبقى مقصوراً على ضغطة زر يدوية بشاشة
     * الإعدادات. الفرق عن الفحص اليدوي:
     * 1) لا يُشعل [UpdateCheckState.Checking] بادئ الأمر (فلا تظهر أي دائرة
     *    تحميل لمستخدم لم يطلب شيئاً)، لكنه يحدّث [_updateState] بالنتيجة
     *    النهائية إن وُجد تحديث فعلاً — فتعرضه شاشة الإعدادات فوراً لو فتحها
     *    المستخدم لاحقاً بلا حاجة لضغط "تحقق" يدوياً من جديد.
     * 2) عند العثور على تحديث، يُرسَل إشعار نظام واحد فقط لكل رقم إصدار
     *    (عبر [UpdateNotifier] و[PreferencesRepository.getLastNotifiedUpdateVersion])
     *    بدل إشعار متكرر في كل فتحة للتطبيق طالما لم يصدر إصدار أحدث بعد —
     *    نفس إصدار سبق الإعلان عنه لا يُعاد إزعاج المستخدم به.
     * 3) يستخدم [AppContainer.appContext] بدل Context من الشاشة (غير متاح
     *    هنا أصلاً، فـ[init] لا يُستدعى من أي واجهة مستخدم مباشرة).
     * أي فشل بالاتصال (لا إنترنت، خطأ خادم...) يُتجاهل بصمت هنا فقط — بلا
     * حالة [UpdateCheckState.Error] مزعجة لمستخدم لم يطلب هذا التحقق أصلاً؛
     * الفحص اليدوي يبقى يُظهر الخطأ كما كان.
     */
    private fun checkForUpdateSilently() {
        viewModelScope.launch {
            val context = container.appContext
            val (versionCode, versionName) = readAppVersion(context)
            val info = runCatching { container.updateRepository.checkForUpdate(versionCode, versionName) }
                .getOrNull() ?: return@launch

            if (_updateState.value !is UpdateCheckState.Checking) {
                _updateState.value = UpdateCheckState.Available(info)
            }

            val lastNotified = runCatching { container.preferencesRepository.getLastNotifiedUpdateVersion() }.getOrNull()
            if (lastNotified != info.versionName) {
                UpdateNotifier.notifyUpdateAvailable(context, info)
                runCatching { container.preferencesRepository.setLastNotifiedUpdateVersion(info.versionName) }
            }
        }
    }

    // Remembers the last update info so downloadUpdate()/installUpdate() (which
    // aren't passed the info again from the UI on every call) can re-use it,
    // e.g. on retry or when there's no direct .apk asset to download.
    private var lastUpdateInfo: AppUpdateInfo? = null

    /**
     * يبدأ تحميل ملف التحديث (.apk) بالخلفية عبر [UpdateDownloadService] — خدمة
     * أمامية حقيقية تستمر حتى لو أغلق المستخدم شاشة التطبيق أو خرج منه تماماً،
     * وتُظهر إشعار تقدّم بهوية التطبيق. الحالة (نسبة التحميل، الجاهزية،
     * الفشل) تصل تلقائياً عبر [downloadState] (المرتبط بـ [UpdateDownloadStatus]
     * المشتركة)، فلا حاجة لانتظار نتيجة من هنا مباشرة.
     *
     * إن لم يكن لهذا الإصدار ملف .apk مرفق (صفحة إصدار فقط)، لا شيء نحمّله
     * داخل التطبيق، فنفتح صفحة الإصدار بالمتصفح مباشرة بدلاً من ذلك، كما كان
     * سابقاً.
     */
    fun downloadUpdate(context: Context, info: AppUpdateInfo) {
        lastUpdateInfo = info
        val apkUrl = info.apkUrl
        if (apkUrl == null) {
            val opened = openInBrowser(context, info.releasePageUrl)
            UpdateDownloadStatus.update(
                if (opened) UpdateDownloadState.ReadyToInstall
                else UpdateDownloadState.Failed("تعذّر فتح رابط التحميل")
            )
            return
        }

        val candidates = container.updateRepository.downloadCandidates(
            apkUrl, info.useProxyFallback, info.customProxyBaseUrl
        )
        UpdateDownloadService.start(context, candidates, info.versionName)
    }

    /**
     * يوقف تحميل التحديث الجاري (زر "إلغاء" بشاشة الإعدادات) — يُنهي الخدمة
     * الأمامية، يقطع الاتصال الجاري فوراً، ويحذف الملف الجزئي المُنزَّل.
     */
    fun cancelDownload(context: Context) {
        UpdateDownloadService.cancel(context)
    }

    /**
     * Hands the already-downloaded .apk to the system package installer via a FileProvider
     * content:// Uri. On Android 8+ this also makes sure "install unknown apps" is allowed for
     * this app first — if not, it opens that settings screen and the user just taps the
     * install button again once they've granted it.
     *
     * الملف نفسه أصبح يأتي من [UpdateDownloadStatus] المشتركة بدل متغيّر محلي،
     * لأن التحميل الفعلي صار يتم بخدمة منفصلة (UpdateDownloadService) قد تكون
     * أنهت التحميل بينما كانت شاشة الإعدادات مغلقة.
     */
    fun installUpdate(context: Context) {
        val file = UpdateDownloadStatus.downloadedApk
        if (file == null || !file.exists()) {
            // Nothing was downloaded in-app (e.g. the release had no .apk asset) - fall back
            // to whatever link we have.
            lastUpdateInfo?.let { openInBrowser(context, it.apkUrl ?: it.releasePageUrl) }
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settingsIntent) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { UpdateDownloadStatus.update(UpdateDownloadState.Failed("تعذّر فتح مثبّت التطبيقات")) }
    }

    /** Opens a URL (the GitHub release page, when there's no .apk asset to download in-app) in the browser. */
    private fun openInBrowser(context: Context, url: String): Boolean {
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    /** Loads the current admin-editable update settings, for [AdminUpdateScreen]. */
    fun loadUpdateConfig() {
        viewModelScope.launch {
            _updateConfig.value = runCatching { container.updateRepository.fetchConfig() }
                .getOrDefault(AppUpdateConfig())
        }
    }

    /** Saves admin-editable update settings (GitHub repo, override link/notes, mandatory-update threshold). */
    fun saveUpdateConfig(config: AppUpdateConfig, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { container.updateRepository.saveConfig(config) }
                .onSuccess {
                    _updateConfig.value = config
                    onResult(true, "تم حفظ إعدادات التحديث")
                }
                .onFailure { onResult(false, it.localizedMessage ?: "حدث خطأ أثناء الحفظ") }
        }
    }

    // نتيجة زر "تحقق الآن بهذه الإعدادات" في لوحة الإدارة — منفصلة تماماً عن
    // [updateState] الخاص بشاشة المستخدم العادية، حتى لا يختلط اختبار الأدمن
    // لإعدادات لم تُحفَظ بعد مع حالة التحقق الحقيقية التي يراها المستخدمون.
    private val _adminUpdateTestState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val adminUpdateTestState: StateFlow<UpdateCheckState> = _adminUpdateTestState.asStateFlow()

    /**
     * يختبر [config] كما هو مكتوب في حقول شاشة الإدارة الآن مباشرة — قبل
     * حفظه وبصرف النظر عمّا هو محفوظ فعلياً في Firestore — فيعرف الأدمن فوراً
     * إن كانت هذه الإعدادات (المستودع، الرابط المخصّص...) تعمل فعلاً، دون
     * الحاجة للحفظ ثم الخروج لشاشة المستخدم العادية للتأكد.
     */
    fun testUpdateConfig(context: Context, config: AppUpdateConfig) {
        if (_adminUpdateTestState.value == UpdateCheckState.Checking) return
        viewModelScope.launch {
            _adminUpdateTestState.value = UpdateCheckState.Checking
            val (versionCode, versionName) = readAppVersion(context)
            val result = runCatching {
                container.updateRepository.checkForUpdate(config, versionCode, versionName)
            }
            _adminUpdateTestState.value = result.fold(
                onSuccess = { info -> if (info != null) UpdateCheckState.Available(info) else UpdateCheckState.UpToDate },
                onFailure = { e -> UpdateCheckState.Error(e.localizedMessage ?: "تعذّر التحقق من التحديثات") }
            )
        }
    }

    init {
        // قدرة جديدة: تحقّق تلقائي من توفّر تحديث في كل مرة يُفتح فيها
        // التطبيق (init{} هنا يُنفَّذ مرة واحدة فعلياً لكل فتحة، انظر توثيق
        // checkForUpdateSilently أعلاه) — بلا أي زر أو إجراء من المستخدم،
        // ومع إشعار نظام عند العثور على تحديث فعلي. يُستدعى أولاً وقبل أي
        // شيء آخر هنا لأنه مستقل تماماً عن بقية init{} (بيانات الموسوعة)
        // ولا داعي لانتظارها.
        checkForUpdateSilently()

        // ── نبضة "جهاز يستخدم التطبيق" لإحصائية الأدمن الجديدة ──
        // نسخة public فقط (BuildConfig.HAS_ADMIN == false): هذه الإحصائية
        // مقصودة لعدّ مستخدمي نسخة public الفعليين، فتسجيل نبضة من جهاز
        // المطوّر نفسه (نسخة full، سواء قبل تسجيل الدخول كأدمن أو بعده) كان
        // يُضيف مستندات زائفة تُضخّم العدد ولا تمثّل أي مستخدم حقيقي. مرة
        // واحدة فقط لكل بدء تشغيل (init{} هنا)، بلا انتظار ولا تأثير على أي
        // واجهة — أفضل جهد تماماً، أي فشل (بلا إنترنت مثلاً) يُتجاهل بصمت
        // عبر runCatching بدل تعطيل بدء التطبيق. راجع DeviceStatsRepository
        // وAdminToolsScreen (زر "تحديث" الإحصائية، متاح بنسخة full ليقرأ
        // فقط - لا يكتب) للتفاصيل الكاملة.
        if (!BuildConfig.HAS_ADMIN) {
            viewModelScope.launch {
                runCatching {
                    val uid = container.authRepository.ensureAnonymousUid()
                    uid?.let { container.deviceStatsRepository.pingDevice(it) }
                }
            }
        }

        // ── وضع الصيانة: عَلَم إدمن جديد يوقف نسخة public مؤقتاً برسالة ──
        // مستمع حي (وليس قراءة لمرة واحدة بعد الآن) ولنسخة public حصراً —
        // نسخة full (الأدمن) يجب أن تبقى قادرة على الدخول دوماً لتستطيع
        // تعطيل الصيانة لاحقاً من نفس الجهاز، فلا يجوز لهذا العلَم حجبها عن
        // نفسها. كون المستمع حياً هو ما يجعل التفعيل والتعطيل كليهما يُطبَّقان
        // فوراً سحابياً على كل الأجهزة المفتوحة حالياً بلا انتظار إعادة
        // تشغيل — راجع MaintenanceRepository.observe وMainActivity (الشاشة
        // الحاجبة).
        if (BuildConfig.FLAVOR == "public") {
            viewModelScope.launch {
                container.maintenanceRepository.observe().collect { config ->
                    maintenanceEnabled = config.enabled
                    maintenanceMessage = config.message
                }
            }
        } else {
            // نسخة full: نفس المستمع الحي، لكن لعرض الحالة الحالية بلوحة
            // الأدمن فقط (maintenanceAdminStatus*) — لا يمسّ maintenanceEnabled
            // إطلاقاً كي لا تنحجب هذه النسخة أبداً خلف MaintenanceScreen.
            viewModelScope.launch {
                container.maintenanceRepository.observe().collect { config ->
                    maintenanceAdminStatusEnabled = config.enabled
                    maintenanceAdminStatusMessage = config.message
                }
            }
        }

        viewModelScope.launch {
            var isFirstEmission = true
            var lastLanguage: AppLanguage? = null
            combine(
                _rawCategories,
                _rawHerbs,
                _rawBlends,
                container.preferencesRepository.appLanguage
            ) { categories, herbs, blends, language ->
                CatalogInput(categories, herbs, blends, language)
            }.collectLatest { input ->
                val languageChanged = lastLanguage != null && lastLanguage != input.language
                lastLanguage = input.language
                if (languageChanged) _uiState.value = _uiState.value.copy(isLoading = true)
                // اللغة غير العربية: اعرض البيانات الأصلية فوراً (بدون انتظار ترجمة كل
                // الحقول عبر الشبكة) ثم استبدلها بالمترجمة عند اكتمالها. سابقاً كانت
                // الشاشة (والسبلاش) تنتظر ترجمة كل الأعشاب قبل ظهور أي شيء.
                if (input.language != AppLanguage.ARABIC && input.herbs.isNotEmpty()) {
                    HerbCategoryLookup.categoryNames = input.categories.associate { it.id to it.name }
                    _uiState.value = _uiState.value.copy(
                        categories = input.categories,
                        herbs = input.herbs,
                        blends = input.blends,
                        isLoading = false
                    )
                }
                val translated = withContext(Dispatchers.Default) {
                    Triple(
                        translateCategories(input.categories, input.language),
                        translateHerbs(input.herbs, input.language),
                        translateBlends(input.blends, input.language)
                    )
                }
                val (categories, herbs, blends) = translated
                HerbCategoryLookup.categoryNames = categories.associate { it.id to it.name }
                _uiState.value = if (isFirstEmission) {
                    isFirstEmission = false
                    _uiState.value.copy(categories = categories, herbs = herbs, blends = blends)
                } else {
                    _uiState.value.copy(
                        categories = categories,
                        herbs = herbs,
                        blends = blends,
                        isLoading = false
                    )
                }
            }
        }

        // ── مصدر البيانات: كاش محلي فوراً، ثم مزامنة شبكية تلقائية في كل فتح ──
        // نعرض آخر نسخة محفوظة محلياً (DataStore - راجع
        // PreferencesRepository.loadCachedCatalog) فوراً بلا أي اتصال شبكي إن
        // وُجدت، ثم نزامن مع Firestore في الخلفية.
        //
        // *** إصلاح: أول تثبيت يحتاج ضغط "إعادة المحاولة" يدوياً - السبب
        // الفعلي المُبلَّغ عنه ***
        // كانت المزامنة التلقائية هنا (بلا كاش بعد) تستخدم دوماً
        // fromServer=false أي Source.DEFAULT، بينما زر "إعادة المحاولة"
        // اليدوي (AppViewModel.refresh) يستخدم fromServer=true أي
        // Source.SERVER مباشرة - وهذا الفرق الوحيد بين المسارين. عملياً كان
        // أول اتصال بـFirestore بعد التثبيت مباشرة (كاش محلي فارغ تماماً،
        // ومصادقة/اتصال لم يستقرّا بعد) يفشل مع Source.DEFAULT بينما تكرار
        // نفس الطلب لاحقاً عبر Source.SERVER (زر إعادة المحاولة) ينجح، فيبدو
        // للمستخدم أن الضغط اليدوي هو ما "شغّل" الجلب. الآن أول مزامنة على
        // الإطلاق لهذا الجهاز (cached == null) تفرض Source.SERVER مباشرة -
        // تماماً كزر إعادة المحاولة - فتنجح تلقائياً من أول تثبيت بلا أي
        // تدخل يدوي.
        //
        // *** طلب: مزامنة تلقائية في كل فتح للتطبيق، لا كل ٢٤ ساعة فقط ***
        // كانت هذه المزامنة الشبكية مشروطة بمرور أكثر من يوم منذ آخر نجاح
        // (لتوفير حصة قراءات Firestore). الآن كل فتح للتطبيق يزامن تلقائياً
        // في الخلفية (بلا مؤشر تحميل كامل الشاشة إن وُجد كاش محلي يُعرض
        // فوراً بدلاً منه) بصرف النظر عن مدة مرور آخر مزامنة، فتصل أي إضافة/
        // تعديل جديد بمجرد فتح التطبيق لا بعد يوم كامل.
        viewModelScope.launch {
            // كاش بلا أعشاب (حُفظ سابقاً بعد جلب فارغ) يُعامَل كأنه غير موجود، وإلا يبقى
            // التطبيق يعرض قائمة فارغة ولا يجلب من الخادم إلا بضغط زر التحديث يدوياً.
            val cached = runCatching { container.preferencesRepository.loadCachedCatalog() }
                .getOrNull()?.takeIf { it.herbs.isNotEmpty() }
            if (cached != null) {
                _rawCategories.value = cached.categories
                _rawHerbs.value = cached.herbs
                _rawBlends.value = cached.blends
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            } else {
                _syncProgress.value = SyncProgress(fraction = SYNC_START_FRACTION, firstRun = true)
            }
            syncJob = coroutineContext[Job]
            syncCatalogFromServer(
                showLoading = cached == null,
                fromServer = cached == null,
                skipIfUnchanged = cached != null,
                cachedSyncAt = cached?.lastSyncAt ?: 0L
            )
        }

        // المزامنة الحيّة (لحظية عبر الأجهزة) تبقى فقط أثناء جلسة الأدمن، لأنه
        // الوحيد الذي يحتاج فعلاً يرى تعديلاته وتعديلات مشرفين آخرين لحظياً
        // أثناء العمل على المحتوى - راجع startAdminLiveSync ولوغن/لوغ آوت أدناه.
        if (isAdmin) startAdminLiveSync()

        // ── مزامنة "تعلّم سيمو الذاتي" بين الأجهزة ───────────────────────
        // كانت [PreferencesRepository.aiAutoLearnedExamples] مصدراً محلياً
        // بحتاً (DataStore على هذا الجهاز فقط): أي حالة يتعلّمها سيمو من
        // تقييم 👍 هنا لا يستفيد منها أي تثبيت آخر للتطبيق إطلاقاً. هذا
        // المستمع يبقى مفتوحاً طوال حياة العملية (كمستمعي Firestore أعلاه)
        // ليدمج، عند كل تغيير محلي أو وصول شبكي جديد، القائمة المحلية مع
        // النسخة المشتركة الحيّة من SemoLearningRepository (انظر توثيقها
        // وتوثيق [HerbAssistant.mergeLearnedExamples])، ثم يحفظ الناتج
        // المدموج محلياً — فيصل تلقائياً لـ AiConfig عبر نفس مسار
        // HerbalNavGraph الحالي دون أي تعديل عليه. فشل الشبكة هنا لا يوقف
        // شيئاً: [SemoLearningRepository.observeSharedLearnedExamples] يبتلع
        // أخطاءه بنفسه ويستمر سيمو بالعمل بآخر بيانات محلية معروفة.
        viewModelScope.launch {
            combine(
                container.preferencesRepository.aiAutoLearnedExamples,
                container.semoLearningRepository.observeSharedLearnedExamples()
            ) { local, shared -> local to shared }
                .catch { /* المزامنة انتهازية فقط؛ لا تُسقِط التطبيق أو تُعطّل سيمو محلياً. */ }
                .collect { (local, shared) ->
                    val merged = HerbAssistant.mergeLearnedExamples(local, shared)
                    if (merged !== local) {
                        container.preferencesRepository.setAiAutoLearnedExamples(merged)
                    }
                }
        }

        // ── مزامنة "الحالات المدرَّبة يدوياً" بين الأجهزة (نسخة full تنشر،
        // كل الأجهزة بما فيها public تستقبل) ─────────────────────────────
        // خلافاً للتعلّم الذاتي أعلاه (دمج/إضافة فقط)، القائمة هنا منسَّقة
        // بالكامل من المطوّر عبر أدوات المطور في نسخة full، فالنسخة
        // الواردة من Firestore هي دوماً الاستبدال الكامل والنهائي (تعديل أو
        // حذف مثال من أدوات المطور يجب أن ينعكس فعلاً، لا أن يبقى الدمج
        // القديم عالقاً محلياً للأبد). في نسخة full، هذا يعيد فعلياً نفس
        // القائمة التي نشرها المطوّر للتو (لا فرق محسوس)؛ في نسخة public —
        // التي لا تملك شاشة أدوات المطور أصلاً ولذلك لم تكن هذه الحالات
        // تصلها بأي شكل من قبل — هذا هو المصدر الوحيد لها الآن. فشل الشبكة
        // لا يوقف شيئاً: يبقى سيمو يعمل بآخر قائمة محلية معروفة له.
        viewModelScope.launch {
            container.semoTrainedRepository.observeSharedTrainedExamples()
                .catch { /* المزامنة انتهازية فقط؛ لا تُسقِط التطبيق أو تُعطّل سيمو محلياً. */ }
                .collect { shared ->
                    if (shared == null) {
                        // لا نشر سابق إطلاقاً بعد. في نسخة full تحديداً
                        // (BuildConfig.HAS_ADMIN)، إن كانت توجد حالات محلية
                        // من قبل هذا التحديث، انشرها الآن كبذرة أولى — تصل
                        // تلقائياً بعدها لكل الأجهزة المتصلة الأخرى بما فيها
                        // نسخة public. لا شيء يحدث في نسخة public نفسها
                        // (لم تملك أي حالات محلية أصلاً، ولا صلاحية نشر
                        // حتى لو حاولت — محمي بـfirestore.rules).
                        if (BuildConfig.HAS_ADMIN) {
                            val local = container.preferencesRepository.aiTrainedExamples.first()
                            if (local.isNotEmpty()) {
                                container.semoTrainedRepository.publish(local)
                            }
                        }
                        return@collect
                    }
                    val local = container.preferencesRepository.aiTrainedExamples.first()
                    if (shared != local) {
                        container.preferencesRepository.setAiTrainedExamples(shared)
                    }
                }
        }
    }

private suspend fun syncCatalogFromServer(
        showLoading: Boolean,
        fromServer: Boolean = false,
        skipIfUnchanged: Boolean = false,
        cachedSyncAt: Long = 0L
    ) {
        if (showLoading) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        _syncProgress.value = _syncProgress.value.copy(
            fraction = maxOf(_syncProgress.value.fraction, SYNC_START_FRACTION),
            failed = false
        )

        herbsProgressApplied = 0f
        val remoteVersion: Long? = withTimeoutOrNull(VERSION_CHECK_TIMEOUT_MS) {
            runCatching { container.herbRepository.fetchCatalogVersion() }.getOrNull()
        }
        if (skipIfUnchanged && remoteVersion != null) {
            val localVersion = runCatching { container.preferencesRepository.getCatalogVersion() }.getOrNull()
            val fresh = System.currentTimeMillis() - cachedSyncAt < CATALOG_FORCE_REFRESH_MS
            if (localVersion != null && localVersion == remoteVersion && fresh) {
                _syncProgress.value = _syncProgress.value.copy(fraction = 1f, failed = false)
                _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                return
            }
        }

        var categories: List<Category>? = null
        var herbs: List<Herb>? = null
        var blends: List<Blend>? = null
        var lastError: Throwable? = null
        var blendsError: Throwable? = null
        val hadLocalHerbs = _rawHerbs.value.isNotEmpty()

        for (attempt in 1..MAX_CATALOG_SYNC_ATTEMPTS) {
            // كل المحاولات تستخدم نفس المصدر المطلوب. سابقاً كانت المحاولة الأخيرة
            // تتحول إلى Source.DEFAULT فتُرجع كاشاً فارغاً "كنجاح" بلا بيانات.
            val useServer = fromServer
            coroutineScope {
                val jobs = mutableListOf<Deferred<Unit>>()
                if (categories == null) jobs += async {
                    fetchPart { container.herbRepository.fetchCategories(useServer) }
                        .onSuccess { categories = it; advanceProgress(SYNC_WEIGHT_CATEGORIES) }
                        .onFailure { lastError = it }
                    Unit
                }
                if (herbs == null) jobs += async {
                    fetchPart { container.herbRepository.fetchHerbsPaged(useServer) { loaded, total -> advanceHerbsProgress(loaded, total) } }
                        .onSuccess { list ->
                            // قائمة فارغة من الكاش بلا أي بيانات محلية ليست نجاحاً حقيقياً.
                            if (list.isEmpty() && !useServer && !hadLocalHerbs) {
                                lastError = IllegalStateException("empty catalog from cache")
                            } else {
                                herbs = list
                                finishHerbsProgress()
                            }
                        }
                        .onFailure { lastError = it }
                    Unit
                }
                if (blends == null) jobs += async {
                    fetchPart { container.herbRepository.fetchBlends(useServer) }
                        .onSuccess { blends = it; advanceProgress(SYNC_WEIGHT_BLENDS) }
                        .onFailure { blendsError = it }
                    Unit
                }
                jobs.awaitAll()
            }
            if (categories != null && herbs != null && blends != null) break
            // الخلطات اختيارية: فشلها الدائم (صلاحيات) لا يستحق انتظار محاولات إضافية.
            val blendsPermanent = blendsError?.let { !HerbRepository.isTransientError(it) } == true
            if (categories != null && herbs != null && (blendsPermanent || attempt >= 2)) break
            val blockingFailure = lastError
            if (blockingFailure != null && !HerbRepository.isTransientError(blockingFailure)) break
            if (attempt < MAX_CATALOG_SYNC_ATTEMPTS) delay(CATALOG_SYNC_RETRY_DELAY_MS * attempt)
        }

        val finalCategories = categories
        val finalHerbs = herbs
        if (finalCategories != null && finalHerbs != null) {
            val finalBlends = blends ?: _rawBlends.value
            _rawCategories.value = finalCategories
            _rawHerbs.value = finalHerbs
            _rawBlends.value = finalBlends
            if (finalHerbs.isNotEmpty()) {
                runCatching {
                    container.preferencesRepository.saveCatalogCache(finalHerbs, finalCategories, finalBlends)
                    if (remoteVersion != null) container.preferencesRepository.setCatalogVersion(remoteVersion)
                }
            }
            _syncProgress.value = _syncProgress.value.copy(fraction = 1f, failed = false)
            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            if (blends == null) scheduleBlendsRetry()
            return
        }

        val hasLocalData = _rawHerbs.value.isNotEmpty()
        val message = lastError?.let { HerbRepository.describeError(it) }
            ?: "تعذّر الاتصال بالخادم خلال مدة معقولة. تحقق من اتصال الإنترنت وحاول مرة أخرى."
        _syncProgress.value = _syncProgress.value.copy(failed = !hasLocalData)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = if (hasLocalData) null else message
        )
    }

    private suspend fun <T> fetchPart(block: suspend () -> T): Result<T> {
        val result = withTimeoutOrNull(CATALOG_SYNC_TIMEOUT_MS) { runCatching { block() } }
        return result ?: Result.failure(java.util.concurrent.TimeoutException())
    }

    private var herbsProgressApplied = 0f

    private fun advanceHerbsProgress(loaded: Int, total: Int) {
        val ratio = if (total > 0) {
            (loaded.toFloat() / total).coerceIn(0f, 1f)
        } else {
            (1f - 1f / (1f + loaded / 40f)).coerceIn(0f, 0.9f)
        }
        val target = SYNC_WEIGHT_HERBS * ratio
        val delta = target - herbsProgressApplied
        if (delta > 0f) {
            herbsProgressApplied = target
            advanceProgress(delta)
        }
    }

    private fun finishHerbsProgress() {
        val delta = SYNC_WEIGHT_HERBS - herbsProgressApplied
        if (delta > 0f) {
            herbsProgressApplied = SYNC_WEIGHT_HERBS
            advanceProgress(delta)
        }
    }

    private var blendsRetryJob: Job? = null

    private fun scheduleBlendsRetry() {
        if (blendsRetryJob?.isActive == true) return
        blendsRetryJob = viewModelScope.launch {
            for (waitMs in longArrayOf(5_000L, 15_000L, 45_000L)) {
                delay(waitMs)
                val result = fetchPart { container.herbRepository.fetchBlends(true) }
                val list = result.getOrNull()
                if (list != null) {
                    _rawBlends.value = list
                    scheduleCatalogCacheSave()
                    return@launch
                }
                val error = result.exceptionOrNull()
                if (error != null && !HerbRepository.isTransientError(error)) return@launch
            }
        }
    }

    fun migrateInlineImages(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val outcome = runCatching {
                val token = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(false)?.await()?.token
                    ?: error("not signed in")
                val targets = _rawHerbs.value
                    .filter { it.imageUrl?.startsWith("data:image") == true }
                    .map { Triple("herbs", it.id, it.imageUrl.orEmpty()) } +
                    _rawBlends.value
                        .filter { it.imageUrl?.startsWith("data:image") == true }
                        .map { Triple("blends", it.id, it.imageUrl.orEmpty()) }
                var moved = 0
                var failed = 0
                for ((collection, id, dataUrl) in targets) {
                    val bytes = withContext(Dispatchers.Default) {
                        runCatching {
                            android.util.Base64.decode(dataUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                        }.getOrNull()
                    }
                    val url = bytes?.let { GithubImageUploader.upload(it, token) }
                    if (url != null) {
                        runCatching { container.herbRepository.setImageUrl(collection, id, url) }
                            .onSuccess { moved++ }
                            .onFailure { failed++ }
                    } else {
                        failed++
                    }
                }
                if (moved > 0) container.herbRepository.bumpCatalogVersion()
                Triple(targets.size, moved, failed)
            }
            outcome
                .onSuccess { (total, moved, failed) ->
                    onResult(failed == 0, "$moved / $total" + if (failed > 0) " ($failed failed)" else "")
                }
                .onFailure { onResult(false, HerbRepository.describeError(it)) }
        }
    }

    private fun advanceProgress(weight: Float) {
        val current = _syncProgress.value
        _syncProgress.value = current.copy(fraction = (current.fraction + weight).coerceAtMost(SYNC_MAX_BEFORE_DONE))
    }

    /**
     * يفتح مستمعات Firestore الحيّة (addSnapshotListener) طوال جلسة الأدمن،
     * فيرى أي تعديل - من هذا الجهاز أو جهاز/متصفح آخر لنفس الأدمن - لحظياً
     * أثناء العمل على المحتوى، بدل انتظار دورة الـ٢٤ ساعة العادية. كل نسخة
     * تصل من المستمع تُحفَظ أيضاً بالكاش المحلي فوراً (saveCatalogCache)
     * فيستفيد المستخدم العادي من أحدث بيانات فور خروج الأدمن دون انتظار.
     */
    private var catalogCacheJob: Job? = null

    private fun scheduleCatalogCacheSave() {
        catalogCacheJob?.cancel()
        catalogCacheJob = viewModelScope.launch {
            delay(CATALOG_CACHE_SAVE_DELAY_MS)
            runCatching {
                container.preferencesRepository.saveCatalogCache(
                    _rawHerbs.value,
                    _rawCategories.value,
                    _rawBlends.value
                )
            }
        }
    }

    private fun startAdminLiveSync() {
        if (adminSyncJob?.isActive == true) return
        viewModelScope.launch { runCatching { container.herbRepository.ensureVersionDoc() } }
        adminSyncJob = viewModelScope.launch {
            combine(
                container.herbRepository.observeCategories(),
                container.herbRepository.observeHerbs()
            ) { categories, herbs -> categories to herbs }
                .distinctUntilChanged()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = HerbRepository.describeError(e))
                }
                .collect { (categories, herbs) ->
                    _rawCategories.value = categories
                    _rawHerbs.value = herbs
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    scheduleCatalogCacheSave()
                }
        }
        // *** إصلاح: الخلطات كانت مدموجة (combine) هنا مع التصنيفات/الأعشاب في
        // مستمع واحد - نفس مشكلة syncCatalogFromServer أعلاه لكن للمزامنة
        // الحيّة: combine لا ينتج أي قيمة إطلاقاً قبل أن يبعث كل مصدر مرة
        // واحدة على الأقل، فإن كانت قراءة الخلطات مرفوضة دائماً (كما كانت
        // بسبب قاعدة أمان مفقودة - راجع firestore.rules)، لن يبعث
        // observeBlends() أي قيمة إطلاقاً (يعيد المحاولة بصمت للأبد بدل ذلك -
        // راجع retryWhen في HerbRepository.observeCollection)، فتبقى شاشة
        // الأدمن عالقة على "جارٍ التحميل" للأبد رغم أن التصنيفات والأعشاب
        // متاحتان فعلياً. الآن مستمع مستقل تماماً لا يُعطّل ولا يتعطّل بفشل
        // الأول: فشله يُبتلَع بصمت وتبقى آخر نسخة معروفة من الخلطات كما هي.
        blendsSyncJob = viewModelScope.launch {
            container.herbRepository.observeBlends()
                .distinctUntilChanged()
                .catch { /* يُبتلَع عمداً - راجع التعليق أعلاه */ }
                .collect { blends ->
                    _rawBlends.value = blends
                    scheduleCatalogCacheSave()
                }
        }
    }

    /** يُستدعى عند تسجيل خروج الأدمن، حتى لا يبقى مستمع حيّ مفتوحاً بلا داعٍ. */
    private fun stopAdminLiveSync() {
        adminSyncJob?.cancel()
        adminSyncJob = null
        blendsSyncJob?.cancel()
        blendsSyncJob = null
    }

    /**
     * يترجم قوائم الأعشاب/التصنيفات/الخلطات بالتوازي (كل حقل نصي طلب شبكة
     * منفصل عبر [com.salman.herbalencyclopedia.data.translate.TranslationRepository]
     * المُخزِّن مؤقتاً) عند اختيار الإنجليزية فقط؛ في الوضع العربي تُعاد
     * القوائم كما هي دون أي طلب شبكة إضافي.
     */
    private suspend fun translateHerbs(herbs: List<Herb>, language: AppLanguage): List<Herb> {
        if (language == AppLanguage.ARABIC || herbs.isEmpty()) return herbs
        val translator = container.translationRepository
        return coroutineScope {
            herbs.map { herb ->
                async {
                    herb.copy(
                        name = translator.translate(herb.name, "en"),
                        benefits = translator.translate(herb.benefits, "en"),
                        usage = translator.translate(herb.usage, "en"),
                        warnings = translator.translate(herb.warnings, "en"),
                        harms = translator.translate(herb.harms, "en"),
                        notes = translator.translate(herb.notes, "en")
                    )
                }
            }.awaitAll()
        }
    }

    private suspend fun translateCategories(categories: List<Category>, language: AppLanguage): List<Category> {
        if (language == AppLanguage.ARABIC || categories.isEmpty()) return categories
        val translator = container.translationRepository
        return coroutineScope {
            categories.map { category ->
                async { category.copy(name = translator.translate(category.name, "en")) }
            }.awaitAll()
        }
    }

    private suspend fun translateBlends(blends: List<Blend>, language: AppLanguage): List<Blend> {
        if (language == AppLanguage.ARABIC || blends.isEmpty()) return blends
        val translator = container.translationRepository
        return coroutineScope {
            blends.map { blend ->
                async {
                    blend.copy(
                        name = translator.translate(blend.name, "en"),
                        benefits = translator.translate(blend.benefits, "en"),
                        usage = translator.translate(blend.usage, "en"),
                        warnings = translator.translate(blend.warnings, "en"),
                        notes = translator.translate(blend.notes, "en")
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * يُستدعى بعد تسجيل تقييم 👍 على رد قابل للتعلّم في شاشة سيمو (انظر
     * SemoAssistantScreen.rateMessage): يرفع نفس الحالة إلى المجموعة
     * المشتركة على Firestore بلا انتظار (fire-and-forget) — الحفظ المحلي
     * عبر [HerbAssistant.recordFeedback] يحدث مستقلاً عن هذا الاستدعاء
     * ولا ينتظره، فتبقى الشاشة سريعة الاستجابة حتى بلا إنترنت.
     */
    fun contributeSemoLearning(question: String, response: String) {
        viewModelScope.launch { container.semoLearningRepository.contribute(question, response) }
    }

    /**
     * يُستدعى بعد تسجيل تقييم 👎: يزيد صوتاً سلبياً على نفس الحالة في
     * المجموعة المشتركة إن كانت قد شُوركت شبكياً أصلاً من هذا الجهاز أو
     * جهاز آخر (انظر [com.salman.herbalencyclopedia.data.repository.SemoLearningRepository.demote]
     * لسبب عدم الحذف المباشر).
     */
    fun demoteSemoLearning(question: String) {
        viewModelScope.launch { container.semoLearningRepository.demote(question) }
    }

    /**
     * يُستدعى من HerbalNavGraph.kt عند أي تعديل على "الحالات المدرَّبة
     * يدوياً" من أدوات المطور (إضافة/حذف/ترقية من التعلّم الذاتي) — يرفع
     * القائمة الكاملة الجديدة إلى المستند المشترك في Firestore بلا انتظار
     * (fire-and-forget)، فتصل تلقائياً لكل الأجهزة الأخرى المتصلة بما فيها
     * نسخة public (راجع SemoTrainedRepository وAppViewModel.init). الحفظ
     * المحلي عبر [PreferencesRepository.setAiTrainedExamples] يحدث مستقلاً
     * عن هذا الاستدعاء ولا ينتظره. لا تأثير فعلي إن استُدعيت (نظرياً) من
     * نسخة public: قواعد Firestore ترفض الكتابة من أي حساب غير المطوّر.
     */
    fun publishTrainedExamples(examples: List<TrainedExample>) {
        viewModelScope.launch { container.semoTrainedRepository.publish(examples) }
    }

    /**
     * Manual retry/refresh (e.g. "سحب للتحديث"): forces a real server round-trip
     * (fromServer = true) and updates uiState + the local catalog cache directly -
     * this is now the only path that talks to Firestore for a non-admin session
     * outside the once-per-24h automatic sync in init.
     */
    fun refresh() {
        if (syncJob?.isActive == true) return
        _syncProgress.value = _syncProgress.value.copy(failed = false)
        syncJob = viewModelScope.launch { syncCatalogFromServer(showLoading = true, fromServer = true) }
    }

    fun toggleFavorite(herbId: String) {
        viewModelScope.launch { container.preferencesRepository.toggleFavorite(herbId) }
    }

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = container.authRepository.login(email, password)
            isLoggedIn = result.success
            isAdmin = result.isAdmin
            if (result.isAdmin) startAdminLiveSync()
            onResult(result.success, result.message)
        }
    }


    fun logout() {
        container.authRepository.logout()
        isLoggedIn = false
        isAdmin = false
        stopAdminLiveSync()
    }

    // Writes below don't call refresh(): the live Firestore listener started by
    // startAdminLiveSync() (active for the whole admin session - see init{}/login())
    // picks up every change automatically (instantly from the local cache, then
    // reconciled with the server), so an extra manual fetch would just be a
    // redundant round-trip and could momentarily race with the listener.

    fun addHerb(herb: Herb, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.herbRepository.addHerb(herb)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    fun updateHerb(herb: Herb, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.herbRepository.updateHerb(herb)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    fun deleteHerb(id: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.herbRepository.deleteHerb(id)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }
    // ---------------------------------------------------------------------
    // Blends ("الخلطات") — admin-only writes, same live-sync model as herbs.
    // ---------------------------------------------------------------------

    fun addBlend(blend: Blend, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.herbRepository.addBlend(blend)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    fun updateBlend(blend: Blend, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.herbRepository.updateBlend(blend)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    fun deleteBlend(id: String, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                container.herbRepository.deleteBlend(id)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    // ---------------------------------------------------------------------
    // إحصائية "كم جهاز يستخدم الموسوعة" — أداة إدمن جديدة، مقروءة فقط عبر
    // زر تحديث يدوي في AdminToolsScreen (لا تُحمَّل تلقائياً عند فتح لوحة
    // الإدمن، فتبقى بلا أي تكلفة قراءة إضافية إلا عند طلبها فعلياً).
    // ---------------------------------------------------------------------

    var deviceStatsLoading by mutableStateOf(false)
        private set
    var deviceStatsTotal by mutableStateOf<Long?>(null)
        private set
    var deviceStatsActive7d by mutableStateOf<Long?>(null)
        private set
    var deviceStatsError by mutableStateOf<String?>(null)
        private set

    fun refreshDeviceStats() {
        viewModelScope.launch {
            deviceStatsLoading = true
            deviceStatsError = null
            runCatching {
                val total = container.deviceStatsRepository.totalDeviceCount()
                val active = container.deviceStatsRepository.activeDeviceCount(sinceDays = 7)
                total to active
            }.onSuccess { (total, active) ->
                deviceStatsTotal = total
                deviceStatsActive7d = active
            }.onFailure {
                deviceStatsError = "تعذّر جلب إحصائية الأجهزة."
            }
            deviceStatsLoading = false
        }
    }

    // ---------------------------------------------------------------------
    // نمو الأجهزة يوماً بيوم — بطاقة إدمن جديدة، راجع
    // DeviceStatsRepository.dailyNewDeviceCounts. لا تُجلَب تلقائياً (نفس
    // مبدأ deviceStats أعلاه): فقط عند الضغط على "تحديث" بالبطاقة.
    // ---------------------------------------------------------------------

    var deviceGrowthLoading by mutableStateOf(false)
        private set
    var deviceGrowth by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set
    var deviceGrowthError by mutableStateOf<String?>(null)
        private set

    fun refreshDeviceGrowth() {
        viewModelScope.launch {
            deviceGrowthLoading = true
            deviceGrowthError = null
            runCatching { container.deviceStatsRepository.dailyNewDeviceCounts(days = 14) }
                .onSuccess { deviceGrowth = it }
                .onFailure { deviceGrowthError = "تعذّر جلب بيانات نمو الأجهزة." }
            deviceGrowthLoading = false
        }
    }

    // ---------------------------------------------------------------------
    // سجل الأعطال — راجع CrashLogRepository وHerbalApp.onCreate (المُعترِض
    // الذي يُرسل الأعطال فعلياً). لا يُجلَب تلقائياً؛ فقط عند فتح البطاقة
    // بلوحة الإدمن (نفس مبدأ deviceStats).
    // ---------------------------------------------------------------------

    var crashLogsLoading by mutableStateOf(false)
        private set
    var crashLogs by mutableStateOf<List<com.salman.herbalencyclopedia.data.repository.CrashLog>>(emptyList())
        private set
    var crashLogsError by mutableStateOf<String?>(null)
        private set

    fun refreshCrashLogs() {
        viewModelScope.launch {
            crashLogsLoading = true
            crashLogsError = null
            runCatching { container.crashLogRepository.recentCrashes() }
                .onSuccess { crashLogs = it }
                .onFailure { crashLogsError = "تعذّر جلب سجل الأعطال." }
            crashLogsLoading = false
        }
    }

    fun clearCrashLogs(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching { container.crashLogRepository.clearAll() }
                .onSuccess { crashLogs = emptyList(); onResult(true, null) }
                .onFailure { onResult(false, "تعذّر مسح سجل الأعطال.") }
        }
    }

    // ---------------------------------------------------------------------
    // وضع الصيانة — راجع MaintenanceRepository. القيمتان تُقرآن مرة واحدة
    // بـinit{} (نسخة public فقط)، ويُعدِّلهما الأدمن من نسخة full عبر
    // [saveMaintenanceConfig] (يحفظ في Firestore مباشرة، لا يُطبَّق على
    // نسخة full نفسها أبداً — راجع الشرح بـMaintenanceRepository).
    // ---------------------------------------------------------------------

    var maintenanceEnabled by mutableStateOf(false)
        private set
    var maintenanceMessage by mutableStateOf("")
        private set

    // عرض فقط للوحة تحكم الأدمن (نسخة full) — الحالة الحقيقية الحالية في
    // Firestore، مستقلة تماماً عن العلَمين أعلاه (اللذين يحجبان public فقط).
    // بدونها كان مفتاح "وضع الصيانة" بلوحة الأدمن يظهر دائماً "متوقف" عند
    // فتح الشاشة بصرف النظر عن حالته الفعلية، بعد إصلاح تسرّب الحجب لنسخة
    // full (راجع saveMaintenanceConfig أدناه وinit{}).
    var maintenanceAdminStatusEnabled by mutableStateOf(false)
        private set
    var maintenanceAdminStatusMessage by mutableStateOf("")
        private set

    fun saveMaintenanceConfig(enabled: Boolean, message: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                container.maintenanceRepository.save(
                    com.salman.herbalencyclopedia.data.repository.MaintenanceConfig(enabled, message)
                )
            }.onSuccess {
                // *** الإصلاح: كانت هاتان السطران يضبطان حالة الصيانة محلياً
                // على نسخة full نفسها (التي استدعت هذه الدالة أصلاً من لوحة
                // الأدمن)، فتنحجب شاشة الأدمن فوراً خلف MaintenanceScreen في
                // MainActivity بمجرد تفعيل الصيانة — رغم أن هذا العلَم يجب أن
                // يبقى مقصوراً على نسخة public حصراً (راجع التعليق أعلاه
                // وMaintenanceRepository). الحفظ في Firestore أعلاه كافٍ
                // تماماً؛ نسخة public تستقبله عبر المستمع الحي
                // (observe/collect بـinit{}) دون أي تدخل هنا.
                onResult(true, null)
            }.onFailure {
                onResult(false, "تعذّر حفظ إعدادات الصيانة.")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Feedback ("ملاحظات المستخدمين") — anyone can send, only the admin
    // account can read (enforced by firestore.rules), surfaced in
    // AdminFeedbackScreen under Settings.
    // ---------------------------------------------------------------------

    private val _feedbackList = MutableStateFlow<List<Feedback>>(emptyList())
    val feedbackList: StateFlow<List<Feedback>> = _feedbackList.asStateFlow()

    private val _feedbackLoading = MutableStateFlow(false)
    val feedbackLoading: StateFlow<Boolean> = _feedbackLoading.asStateFlow()

    private val _feedbackError = MutableStateFlow<String?>(null)
    val feedbackError: StateFlow<String?> = _feedbackError.asStateFlow()

    private var feedbackJob: Job? = null

    /**
     * Sending is open to everyone — no admin/login check here on purpose.
     * لكنها تحتاج هوية جهاز مجهولة (Anonymous Auth) قبل الإرسال الفعلي، وإلا
     * رفضتها firestore.rules؛ راجع AuthRepository.ensureAnonymousUid وتوثيق
     * blockUser أدناه لسبب وجود هذه الخطوة.
     */
    fun submitFeedback(
        targetType: String,
        targetId: String,
        targetName: String,
        message: String,
        senderName: String?,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val senderUid = container.authRepository.ensureAnonymousUid()
                if (senderUid == null) {
                    // بلا هوية جهاز، سترفض قاعدة firestore.rules الكتابة حتماً
                    // (data.sender_uid == request.auth.uid). لو أرسلنا رغم ذلك
                    // مع كاش Firestore المحلي المفعَّل، فستُقبَل محلياً كـ"نجاح"
                    // فورياً وتبقى في طابور الانتظار، ثم تُرفَض بصمت لاحقاً عند
                    // عودة الاتصال — فيظن المستخدم أن ملاحظته وصلت وهي لم تصل
                    // إطلاقاً. الأسلم إخباره الآن بوضوح بدل هذا الفشل الصامت
                    // المؤجَّل. يحدث هذا فقط في أول محاولة إرسال من هذا الجهاز
                    // إطلاقاً بلا اتصال إنترنت (Anonymous Auth يحتاج اتصالاً
                    // لمرة واحدة فقط لإنشاء الهوية؛ بعدها تُستخدَم من الكاش
                    // المحلي حتى بلا إنترنت).
                    onDone(false, "يحتاج إرسال أول ملاحظة من هذا الجهاز اتصالاً بالإنترنت. تحقق من الاتصال وحاول مجدداً.")
                    return@launch
                }
                container.feedbackRepository.submitFeedback(targetType, targetId, targetName, message, senderName, senderUid)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    /** Starts (once) the admin-only live listener backing [feedbackList]. Safe to call every time the screen opens. */
    fun loadFeedback() {
        if (feedbackJob?.isActive == true) return
        _feedbackLoading.value = true
        feedbackJob = viewModelScope.launch {
            container.feedbackRepository.observeFeedback()
                .catch { e ->
                    _feedbackLoading.value = false
                    _feedbackError.value = HerbRepository.describeError(e)
                }
                .collect { list ->
                    _feedbackLoading.value = false
                    _feedbackError.value = null
                    _feedbackList.value = list
                }
        }
    }

    fun deleteFeedback(id: String, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                container.feedbackRepository.deleteFeedback(id)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    // ---------------------------------------------------------------------
    // حظر مُرسِلين مسيئين ("هذا الشخص يسيء أو يتلاعب") — يمنعهم من إرسال
    // ملاحظات جديدة دون التأثير على أي ملاحظة سابقة أُرسِلت. انظر
    // FeedbackRepository وfirestore.rules (مجموعة blocked_users) للآلية الكاملة.
    // ---------------------------------------------------------------------

    private val _blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    /**
     * القائمة الكاملة (لا مجرّد المعرّفات) عمداً — تُستخدَم في AdminFeedbackScreen
     * لعرض "المحظورون" كقائمة مستقلة يمكن فكّ الحظر منها حتى بعد حذف كل
     * ملاحظات الشخص المحظور (وإلا يضيع أي أثر له فيصبح الحظر دائماً بلا رجعة
     * عملياً). استخرج المعرّفات فقط عبر `.map { it.uid }.toSet()` عند الحاجة.
     */
    val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers.asStateFlow()

    private var blockedUsersJob: Job? = null

    /** Starts (once) the admin-only live listener backing [blockedUsers]. Safe to call every time the screen opens. */
    fun loadBlockedUsers() {
        if (blockedUsersJob?.isActive == true) return
        blockedUsersJob = viewModelScope.launch {
            container.feedbackRepository.observeBlockedUsers()
                .catch { /* القائمة تبقى كما هي عند أي انقطاع؛ لا حاجة لإظهار خطأ هنا */ }
                .collect { list -> _blockedUsers.value = list }
        }
    }

    fun blockUser(uid: String, senderName: String?, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                container.feedbackRepository.blockUser(uid, senderName)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    fun unblockUser(uid: String, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                container.feedbackRepository.unblockUser(uid)
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, HerbRepository.describeError(e))
            }
        }
    }

    fun addCategory(name: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch { runCatching { container.herbRepository.addCategory(name) }.onSuccess { onResult(true, null) }.onFailure { onResult(false, HerbRepository.describeError(it)) } }
    }

    fun deleteCategory(id: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch { runCatching { container.herbRepository.deleteCategory(id) }.onSuccess { onResult(true, null) }.onFailure { onResult(false, HerbRepository.describeError(it)) } }
    }

    // ── تعديل (إعادة تسمية) تصنيف موجود ──────────────────────────────────
    // كانت شاشة أدوات الإدارة (AdminToolsScreen) تسمح فقط بإضافة تصنيف جديد
    // أو حذف تصنيف موجود، رغم أن HerbRepository.updateCategory كانت موجودة
    // فعلاً في طبقة البيانات بلا أي واجهة تستدعيها إطلاقاً — فلم يكن هناك أي
    // طريق للمسؤول لتصحيح اسم تصنيف أُدخل خطأً سوى حذفه وإضافته من جديد
    // (فيفقد كل الأعشاب المرتبطة به تصنيفها). هذه الدالة تكمل الوصلة الناقصة.
    fun updateCategory(id: String, name: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch { runCatching { container.herbRepository.updateCategory(id, name) }.onSuccess { onResult(true, null) }.onFailure { onResult(false, HerbRepository.describeError(it)) } }
    }

    fun deleteAllHerbs(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch { runCatching { container.herbRepository.deleteAllHerbs() }.onSuccess { onResult(true, null) }.onFailure { onResult(false, HerbRepository.describeError(it)) } }
    }

    fun deleteAllData(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch { runCatching { container.herbRepository.deleteAllData() }.onSuccess { onResult(true, null) }.onFailure { onResult(false, HerbRepository.describeError(it)) } }
    }

    fun clearFavorites() { viewModelScope.launch { container.preferencesRepository.clearFavorites() } }

    fun restoreBackup(json: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { runCatching { container.herbRepository.restoreBackup(json) }.onSuccess { onResult(true, null) }.onFailure { onResult(false, HerbRepository.describeError(it)) } }
    }

    fun testConnection(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch { runCatching { container.herbRepository.testConnection() }.onSuccess { onResult(true, "الاتصال يعمل بشكل طبيعي") }.onFailure { onResult(false, HerbRepository.describeError(it)) } }
    }

}

data class SyncProgress(
    val fraction: Float = 0f,
    val firstRun: Boolean = false,
    val failed: Boolean = false
)

private data class CatalogInput(
    val categories: List<Category>,
    val herbs: List<Herb>,
    val blends: List<Blend>,
    val language: AppLanguage
)
