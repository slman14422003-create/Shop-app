package com.salman.herbalencyclopedia.data.repository

import android.content.Context
import com.salman.herbalencyclopedia.BuildConfig
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.salman.herbalencyclopedia.data.ai.AiConfig
import com.salman.herbalencyclopedia.data.ai.TrainedExample
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import com.salman.herbalencyclopedia.ui.theme.PerformanceMode
import com.salman.herbalencyclopedia.ui.theme.ThemePalette
import com.salman.herbalencyclopedia.ui.theme.recommendedPerformanceMode
import com.salman.herbalencyclopedia.ui.util.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "herbal_prefs")

/** فاصل داخلي لتشفير أزواج (مرادف/حالة مدرَّبة) داخل قيمة نصية واحدة، لأن
 *  DataStore يخزّن Set<String> فقط دون بنية key-value متداخلة. حرف تحكم
 *  غير مرئي وشبه مستحيل ورودُه ضمن نص عربي طبيعي يكتبه المطوّر. */
private const val AI_ENTRY_SEP = "\u241F"

/** لقطة كاملة من كاش الموسوعة المحلي (راجع [PreferencesRepository.loadCachedCatalog]). */
data class CatalogSnapshot(
    val herbs: List<Herb>,
    val categories: List<Category>,
    val blends: List<Blend>,
    /** وقت آخر مزامنة ناجحة مع Firestore بالمللي ثانية (epoch). */
    val lastSyncAt: Long
)

/**
 * Stores favorite herb IDs and the dark-mode preference locally on-device
 * (kept out of Firestore since these are per-device, not account, settings).
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorite_herb_ids")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FONT_SCALE = intPreferencesKey("font_scale")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
        // لغة واجهة التطبيق (ar/en) — راجع AppLanguage وSettingsScreen وميزة
        // الترجمة التلقائية في AppViewModel.translateHerbs/translateCategories/translateBlends.
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        // هل شاهد المستخدم شاشة ترحيب/شروط استخدام سيمو تحديداً (منفصلة عن
        // شاشة ترحيب التطبيق العامة أعلاه)؟ تُعرض مرة واحدة فقط عند أول
        // فتح لسيمو بعد التثبيت — انظر SemoIntroScreen وHerbalNavGraph.
        val SEMO_INTRO_SEEN = booleanPreferencesKey("semo_intro_seen")
        // إعدادات "مساعد المقارنة الذكي" (HerbAssistant) — قابلة للتعديل من
        // أدوات المطور (AdminToolsScreen) لضبط/"تدريب" سلوك المطابقة النصية
        // المحلية دون الحاجة لإعادة بناء التطبيق.
        val AI_SIMILARITY_THRESHOLD = floatPreferencesKey("ai_similarity_threshold")
        val AI_SEARCH_THRESHOLD = floatPreferencesKey("ai_search_threshold")
        val AI_EXTRA_STOPWORDS = stringSetPreferencesKey("ai_extra_stopwords")
        // "تدريب سيمو المخصّص": مرادفات يفهمها + حالات (سؤال↔رد) يعلّمها
        // المطوّر يدوياً من أدوات المطور، تُطبَّق حياً بلا إعادة بناء التطبيق.
        val AI_SYNONYMS = stringSetPreferencesKey("ai_synonyms")
        val AI_TRAINED_EXAMPLES = stringSetPreferencesKey("ai_trained_examples")
        val AI_TRAINED_THRESHOLD = floatPreferencesKey("ai_trained_threshold")
        // "التعلّم الذاتي": حالات يتعلّمها سيمو تلقائياً من تقييمات المستخدمين
        // (👍) على إجابات البحث الحر — منفصلة عن حالات المطوّر اليدوية أعلاه.
        val AI_AUTO_LEARNED_EXAMPLES = stringSetPreferencesKey("ai_auto_learned_examples")
        val AI_AUTO_LEARN_ENABLED = booleanPreferencesKey("ai_auto_learn_enabled")
        // الوضع الذكي عبر الإنترنت (Gemini المجاني من Google) — راجع توثيق
        // AiConfig.onlineEnabled/onlineApiKey/onlineModel في HerbAssistant.kt.
        val AI_ONLINE_ENABLED = booleanPreferencesKey("ai_online_enabled")
        val AI_ONLINE_API_KEY = stringPreferencesKey("ai_online_api_key")
        val AI_ONLINE_MODEL = stringPreferencesKey("ai_online_model")
        val AI_ONLINE_BASE_URL = stringPreferencesKey("ai_online_base_url")
        // كاش كامل لمحتوى الموسوعة (راجع توثيق loadCachedCatalog/saveCatalogCache
        // أسفل الملف لسبب وجود هذا الكاش المنفصل عن كاش Firestore الداخلي).
        val CATALOG_HERBS_JSON = stringPreferencesKey("catalog_herbs_json")
        val CATALOG_CATEGORIES_JSON = stringPreferencesKey("catalog_categories_json")
        val CATALOG_BLENDS_JSON = stringPreferencesKey("catalog_blends_json")
        val CATALOG_LAST_SYNC_AT = longPreferencesKey("catalog_last_sync_at")
        val CATALOG_VERSION = longPreferencesKey("catalog_version")
        // آخر رقم إصدار تحديث أظهر له التطبيق إشعار نظام بالفعل (راجع
        // AppViewModel.checkForUpdateSilently) — يمنع إعادة إظهار نفس إشعار
        // "تحديث متوفر" في كل مرة يُفتح فيها التطبيق طالما لم يصدر إصدار أحدث
        // منه بعد، مع بقاء التحقق التلقائي نفسه يعمل في كل مرة كما هو مطلوب.
        val LAST_NOTIFIED_UPDATE_VERSION = stringPreferencesKey("last_notified_update_version")
    }

    private fun <T> prefFlow(transform: (Preferences) -> T): Flow<T> =
        context.dataStore.data.map(transform).distinctUntilChanged().flowOn(Dispatchers.Default)

    val favoriteIds: Flow<Set<String>> = prefFlow {
        it[Keys.FAVORITES] ?: emptySet()
    }

    /** هل وافق المستخدم على شاشة الترحيب (سياسة الخصوصية + التحذير الطبي +
     *  الشروط والأحكام)؟ تُقرأ مرة واحدة فقط عند أول تشغيل بعد التثبيت -
     *  محفوظة محلياً على الجهاز (DataStore)، وليست جزءاً من حساب أو
     *  Firestore، فتُمسَح فقط إذا حذف المستخدم بيانات التطبيق أو أزاله. */
    val termsAccepted: Flow<Boolean> = prefFlow {
        it[Keys.TERMS_ACCEPTED] ?: false
    }

    /** هل شاهد المستخدم شاشة ترحيب/شروط استخدام سيمو من قبل؟ انظر [Keys.SEMO_INTRO_SEEN]. */
    val semoIntroSeen: Flow<Boolean> = prefFlow {
        it[Keys.SEMO_INTRO_SEEN] ?: false
    }

    val darkMode: Flow<Boolean?> = prefFlow {
        it[Keys.DARK_MODE]
    }

    // الافتراضي false: قبل هذا التعديل كانت الألوان الديناميكية (Material You
    // المشتقة من خلفية الجهاز) مفعّلة افتراضياً، فتتجاوز أي لوحة ألوان يختارها
    // المستخدم من الإعدادات بالكامل — كانت تغيير اللوحة يبدو بلا أي أثر لأن
    // الشرط في HerbalEncyclopediaTheme يعطي أولوية لـ dynamicColor. الآن
    // يعتمد التطبيق افتراضياً على هويته البصرية الخاصة (اللوحة المختارة)
    // ويبقى بإمكان المستخدم تفعيل الألوان الديناميكية يدوياً إن أراد.
    val dynamicColor: Flow<Boolean> = prefFlow {
        it[Keys.USE_DYNAMIC_COLOR] ?: false
    }

    val fontScale: Flow<Int> = prefFlow { it[Keys.FONT_SCALE] ?: 0 }

    val themePalette: Flow<ThemePalette> = prefFlow {
        ThemePalette.fromId(it[Keys.THEME_PALETTE])
    }

    /** وضع الأداء (عالي الجودة/اقتصادي) — يتحكم بالزجاج السائل والتمويه وثقل الحركات.
     *  إذا لم يختر المستخدم شيئاً بعد، يُستخدم وضع مقترَح تلقائياً حسب قدرة
     *  الجهاز (انظر [recommendedPerformanceMode]) بدل افتراض "أداء عالٍ" للجميع. */
    private val recommendedMode: PerformanceMode by lazy { recommendedPerformanceMode(context) }

    val performanceMode: Flow<PerformanceMode> = prefFlow {
        PerformanceMode.fromId(it[Keys.PERFORMANCE_MODE], fallback = recommendedMode)
    }

    /** لغة واجهة التطبيق الحالية — راجع [AppLanguage] و[setAppLanguage]. */
    val appLanguage: Flow<AppLanguage> = prefFlow {
        AppLanguage.fromCode(it[Keys.APP_LANGUAGE])
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        withContext(Dispatchers.IO) {
            context.getSharedPreferences(LANGUAGE_MIRROR_FILE, Context.MODE_PRIVATE)
                .edit().putString(LANGUAGE_MIRROR_KEY, language.code).apply()
        }
        context.dataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = language.code }
    }

    suspend fun syncLanguageMirror() {
        val code = withContext(Dispatchers.IO) {
            context.dataStore.data.first()[Keys.APP_LANGUAGE]
        } ?: return
        withContext(Dispatchers.IO) {
            context.getSharedPreferences(LANGUAGE_MIRROR_FILE, Context.MODE_PRIVATE)
                .edit().putString(LANGUAGE_MIRROR_KEY, code).apply()
        }
    }

    companion object {
        private const val LANGUAGE_MIRROR_FILE = "language_mirror"
        private const val LANGUAGE_MIRROR_KEY = "app_language"
        private const val CATALOG_FILE_NAME = "catalog_cache.json"

        fun getSavedLanguageCode(context: Context): String =
            context.getSharedPreferences(LANGUAGE_MIRROR_FILE, Context.MODE_PRIVATE)
                .getString(LANGUAGE_MIRROR_KEY, null) ?: AppLanguage.ARABIC.code
    }

    suspend fun toggleFavorite(herbId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES] ?: emptySet()
            prefs[Keys.FAVORITES] = if (herbId in current) current - herbId else current + herbId
        }
    }

    suspend fun getCatalogVersion(): Long? = context.dataStore.data.first()[Keys.CATALOG_VERSION]

    suspend fun setCatalogVersion(version: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.CATALOG_VERSION] = version }
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { prefs ->
            if (enabled == null) prefs.remove(Keys.DARK_MODE) else prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setFontScale(level: Int) { context.dataStore.edit { it[Keys.FONT_SCALE] = level.coerceIn(0, 4) } }
    suspend fun setThemePalette(palette: ThemePalette) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME_PALETTE] = palette.name }
    }
    suspend fun clearFavorites() { context.dataStore.edit { it[Keys.FAVORITES] = emptySet() } }

    suspend fun setPerformanceMode(mode: PerformanceMode) {
        context.dataStore.edit { prefs -> prefs[Keys.PERFORMANCE_MODE] = mode.name }
    }

    /** يُستدعى مرة واحدة فقط عند ضغط "أوافق" في شاشة الترحيب الأولى. */
    suspend fun setTermsAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.TERMS_ACCEPTED] = accepted }
    }

    /** يُستدعى مرة واحدة فقط عند ضغط "فهمت، لنبدأ" في شاشة ترحيب سيمو الأولى. */
    suspend fun setSemoIntroSeen(seen: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SEMO_INTRO_SEEN] = seen }
    }

    // ── إعدادات مساعد المقارنة الذكي (HerbAssistant) ────────────────────

    val aiSimilarityThreshold: Flow<Float> = prefFlow {
        it[Keys.AI_SIMILARITY_THRESHOLD] ?: AiConfig.defaultSimilarityThreshold.toFloat()
    }
    val aiSearchThreshold: Flow<Float> = prefFlow {
        it[Keys.AI_SEARCH_THRESHOLD] ?: AiConfig.defaultSearchThreshold.toFloat()
    }
    val aiExtraStopWords: Flow<Set<String>> = prefFlow {
        it[Keys.AI_EXTRA_STOPWORDS] ?: emptySet()
    }

    suspend fun setAiSimilarityThreshold(value: Float) {
        context.dataStore.edit { it[Keys.AI_SIMILARITY_THRESHOLD] = value }
    }
    suspend fun setAiSearchThreshold(value: Float) {
        context.dataStore.edit { it[Keys.AI_SEARCH_THRESHOLD] = value }
    }
    suspend fun setAiExtraStopWords(words: Set<String>) {
        context.dataStore.edit { it[Keys.AI_EXTRA_STOPWORDS] = words }
    }
    suspend fun resetAiSettings() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.AI_SIMILARITY_THRESHOLD)
            prefs.remove(Keys.AI_SEARCH_THRESHOLD)
            prefs.remove(Keys.AI_EXTRA_STOPWORDS)
            prefs.remove(Keys.AI_SYNONYMS)
            prefs.remove(Keys.AI_TRAINED_EXAMPLES)
            prefs.remove(Keys.AI_TRAINED_THRESHOLD)
            prefs.remove(Keys.AI_AUTO_LEARNED_EXAMPLES)
            prefs.remove(Keys.AI_AUTO_LEARN_ENABLED)
        }
    }

    // ── تدريب سيمو المخصّص: مرادفات وحالات (سؤال ← رد) يعلّمها المطوّر ───

    val aiSynonyms: Flow<Map<String, String>> = prefFlow { prefs ->
        (prefs[Keys.AI_SYNONYMS] ?: emptySet()).mapNotNull { entry ->
            val parts = entry.split(AI_ENTRY_SEP, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }

    val aiTrainedExamples: Flow<List<TrainedExample>> = prefFlow { prefs ->
        (prefs[Keys.AI_TRAINED_EXAMPLES] ?: emptySet()).mapNotNull { entry ->
            val parts = entry.split(AI_ENTRY_SEP, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) TrainedExample(parts[0], parts[1]) else null
        }
    }

    val aiTrainedThreshold: Flow<Float> = prefFlow {
        it[Keys.AI_TRAINED_THRESHOLD] ?: AiConfig.defaultTrainedThreshold.toFloat()
    }

    suspend fun setAiSynonyms(synonyms: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_SYNONYMS] = synonyms.map { (word, meaning) -> "$word$AI_ENTRY_SEP$meaning" }.toSet()
        }
    }

    suspend fun setAiTrainedExamples(examples: List<TrainedExample>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_TRAINED_EXAMPLES] = examples.map { "${it.pattern}$AI_ENTRY_SEP${it.response}" }.toSet()
        }
    }

    suspend fun setAiTrainedThreshold(value: Float) {
        context.dataStore.edit { it[Keys.AI_TRAINED_THRESHOLD] = value }
    }

    // ── التعلّم الذاتي: حالات يتعلّمها سيمو من تقييمات المستخدمين 👍/👎 ──

    val aiAutoLearnedExamples: Flow<List<TrainedExample>> = prefFlow { prefs ->
        (prefs[Keys.AI_AUTO_LEARNED_EXAMPLES] ?: emptySet()).mapNotNull { entry ->
            val parts = entry.split(AI_ENTRY_SEP, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) TrainedExample(parts[0], parts[1]) else null
        }
    }

    val aiAutoLearnEnabled: Flow<Boolean> = prefFlow {
        it[Keys.AI_AUTO_LEARN_ENABLED] ?: AiConfig.defaultAutoLearnEnabled
    }

    suspend fun setAiAutoLearnedExamples(examples: List<TrainedExample>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_AUTO_LEARNED_EXAMPLES] = examples.map { "${it.pattern}$AI_ENTRY_SEP${it.response}" }.toSet()
        }
    }

    suspend fun setAiAutoLearnEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_AUTO_LEARN_ENABLED] = enabled }
    }

    // ── الوضع الذكي عبر الإنترنت (Gemini المجاني) ────────────────────────

    // القيمة المخبوزة (BuildConfig، من app/ai.properties وقت البناء —
    // geminiApiKey/geminiBaseUrl/geminiModel الثلاثة معاً) هي الخيار
    // الاحتياطي فقط حين لا يوجد أي اختيار صريح محفوظ في DataStore. بهذا:
    // (1) نسخة full تبقى كما كانت تماماً إن ضبط المطوّر القيم يدوياً من
    // أدوات المطور (لها الأولوية دوماً)، و(2) نسخة public — التي لا تملك
    // شاشة أدوات المطور أصلاً فلم يكن ممكناً ضبط أي قيمة فيها من قبل —
    // تحصل الآن على القيم المخبوزة تلقائياً فيعمل "سيمو المتصل" دون أي
    // خطوة إضافية بعد التثبيت.
    val aiOnlineEnabled: Flow<Boolean> = prefFlow {
        it[Keys.AI_ONLINE_ENABLED]
            ?: (AiConfig.defaultOnlineEnabled || BuildConfig.BAKED_ONLINE_API_KEY.isNotBlank())
    }
    val aiOnlineApiKey: Flow<String> = prefFlow {
        it[Keys.AI_ONLINE_API_KEY] ?: BuildConfig.BAKED_ONLINE_API_KEY
    }
    val aiOnlineBaseUrl: Flow<String> = prefFlow {
        it[Keys.AI_ONLINE_BASE_URL]
            ?: BuildConfig.BAKED_ONLINE_BASE_URL.ifBlank { AiConfig.defaultOnlineBaseUrl }
    }
    val aiOnlineModel: Flow<String> = prefFlow {
        it[Keys.AI_ONLINE_MODEL]
            ?: BuildConfig.BAKED_ONLINE_MODEL.ifBlank { AiConfig.defaultOnlineModel }
    }

    suspend fun setAiOnlineEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AI_ONLINE_ENABLED] = enabled }
    }
    suspend fun setAiOnlineApiKey(key: String) {
        context.dataStore.edit { it[Keys.AI_ONLINE_API_KEY] = key }
    }
    suspend fun setAiOnlineModel(model: String) {
        context.dataStore.edit { it[Keys.AI_ONLINE_MODEL] = model.ifBlank { AiConfig.defaultOnlineModel } }
    }
    suspend fun setAiOnlineBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[Keys.AI_ONLINE_BASE_URL] = baseUrl.trim() }
    }
    suspend fun resetAiOnlineSettings() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.AI_ONLINE_ENABLED)
            prefs.remove(Keys.AI_ONLINE_API_KEY)
            prefs.remove(Keys.AI_ONLINE_BASE_URL)
            prefs.remove(Keys.AI_ONLINE_MODEL)
        }
    }

    private val catalogFile: File by lazy { File(context.filesDir, CATALOG_FILE_NAME) }

    @Volatile
    private var legacyCatalogCleared = false

    suspend fun loadCachedCatalog(): CatalogSnapshot? = withContext(Dispatchers.IO) {
        runCatching { readCatalogFile() }.getOrNull() ?: runCatching { readLegacyCatalog() }.getOrNull()
    }

    private fun readCatalogFile(): CatalogSnapshot? {
        val file = catalogFile
        if (!file.exists() || file.length() == 0L) return null
        val root = JSONObject(file.readText(Charsets.UTF_8))
        return CatalogSnapshot(
            herbs = decodeHerbs(root.getJSONArray("herbs")),
            categories = decodeCategories(root.getJSONArray("categories")),
            blends = decodeBlends(root.getJSONArray("blends")),
            lastSyncAt = root.optLong("last_sync_at", 0L)
        )
    }

    private suspend fun readLegacyCatalog(): CatalogSnapshot? {
        val prefs = context.dataStore.data.first()
        val herbsJson = prefs[Keys.CATALOG_HERBS_JSON] ?: return null
        val categoriesJson = prefs[Keys.CATALOG_CATEGORIES_JSON] ?: return null
        val blendsJson = prefs[Keys.CATALOG_BLENDS_JSON] ?: return null
        val lastSyncAt = prefs[Keys.CATALOG_LAST_SYNC_AT] ?: return null
        return CatalogSnapshot(
            herbs = decodeHerbs(JSONArray(herbsJson)),
            categories = decodeCategories(JSONArray(categoriesJson)),
            blends = decodeBlends(JSONArray(blendsJson)),
            lastSyncAt = lastSyncAt
        )
    }

    suspend fun saveCatalogCache(herbs: List<Herb>, categories: List<Category>, blends: List<Blend>) {
        withContext(Dispatchers.IO) {
            val root = JSONObject()
                .put("herbs", encodeHerbs(herbs))
                .put("categories", encodeCategories(categories))
                .put("blends", encodeBlends(blends))
                .put("last_sync_at", System.currentTimeMillis())
            val target = catalogFile
            val temp = File(target.parentFile, "$CATALOG_FILE_NAME.tmp")
            temp.writeText(root.toString(), Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        if (!legacyCatalogCleared) {
            legacyCatalogCleared = true
            context.dataStore.edit { prefs ->
                prefs.remove(Keys.CATALOG_HERBS_JSON)
                prefs.remove(Keys.CATALOG_CATEGORIES_JSON)
                prefs.remove(Keys.CATALOG_BLENDS_JSON)
                prefs.remove(Keys.CATALOG_LAST_SYNC_AT)
            }
        }
    }

    /** آخر رقم إصدار أظهر له التطبيق إشعار "تحديث متوفر" فعلياً، أو null إن لم يُظهر أي إشعار بعد. */
    suspend fun getLastNotifiedUpdateVersion(): String? =
        context.dataStore.data.first()[Keys.LAST_NOTIFIED_UPDATE_VERSION]

    /** يسجّل أن إشعار "تحديث متوفر" لهذا الإصدار بالذات أُظهر بالفعل، فلا يتكرر في الفتحات التالية للتطبيق. */
    suspend fun setLastNotifiedUpdateVersion(version: String) {
        context.dataStore.edit { it[Keys.LAST_NOTIFIED_UPDATE_VERSION] = version }
    }

    private fun encodeHerbs(herbs: List<Herb>): JSONArray {
        val array = JSONArray()
        herbs.forEach { herb ->
            array.put(
                JSONObject()
                    .put("id", herb.id)
                    .put("name", herb.name)
                    .put("category_id", herb.categoryId)
                    .put("benefits", herb.benefits)
                    .put("warnings", herb.warnings)
                    .put("harms", herb.harms)
                    .put("usage", herb.usage)
                    .put("notes", herb.notes)
                    .put("image_url", herb.imageUrl)
            )
        }
        return array
    }

    private fun decodeHerbs(array: JSONArray): List<Herb> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Herb(
                id = o.optString("id"),
                name = o.optString("name"),
                categoryId = o.optString("category_id").ifBlank { null },
                benefits = o.optString("benefits"),
                warnings = o.optString("warnings"),
                harms = o.optString("harms"),
                usage = o.optString("usage"),
                notes = o.optString("notes"),
                imageUrl = o.optString("image_url").ifBlank { null }
            )
        }
    }

    private fun encodeCategories(categories: List<Category>): JSONArray {
        val array = JSONArray()
        categories.forEach { category ->
            array.put(
                JSONObject()
                    .put("id", category.id)
                    .put("name", category.name)
                    .put("icon", category.icon)
            )
        }
        return array
    }

    private fun decodeCategories(array: JSONArray): List<Category> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Category(
                id = o.optString("id"),
                name = o.optString("name"),
                icon = o.optString("icon").ifBlank { null }
            )
        }
    }

    private fun encodeBlends(blends: List<Blend>): JSONArray {
        val array = JSONArray()
        blends.forEach { blend ->
            array.put(
                JSONObject()
                    .put("id", blend.id)
                    .put("name", blend.name)
                    .put("herb_ids", JSONArray(blend.herbIds))
                    .put("benefits", blend.benefits)
                    .put("usage", blend.usage)
                    .put("warnings", blend.warnings)
                    .put("notes", blend.notes)
                    .put("image_url", blend.imageUrl)
            )
        }
        return array
    }

    private fun decodeBlends(array: JSONArray): List<Blend> {
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val herbIdsArray = o.optJSONArray("herb_ids") ?: JSONArray()
            Blend(
                id = o.optString("id"),
                name = o.optString("name"),
                herbIds = (0 until herbIdsArray.length()).map { j -> herbIdsArray.getString(j) },
                benefits = o.optString("benefits"),
                usage = o.optString("usage"),
                warnings = o.optString("warnings"),
                notes = o.optString("notes"),
                imageUrl = o.optString("image_url").ifBlank { null }
            )
        }
    }
}
