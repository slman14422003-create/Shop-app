package com.salman.herbalencyclopedia

import android.app.ActivityManager
import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.salman.herbalencyclopedia.data.ai.DictionaryLexicon
import com.salman.herbalencyclopedia.data.ai.local.LocalLlmManager
import com.salman.herbalencyclopedia.data.image.DataUriFetcher
import com.salman.herbalencyclopedia.data.repository.AppContainer
import com.salman.herbalencyclopedia.data.repository.HerbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase is initialized manually with the same project configuration the
 * web app already uses (js/firebase-config.js), so the app talks to the
 * exact same "herbs" / "categories" Firestore collections — no
 * google-services.json required for this to compile and run.
 *
 * For production hardening (Play Integrity, FCM, Google Sign-In, or to lock
 * the Firebase API key to this app's package + SHA-1), register an Android
 * app for this package name in the Firebase console and swap this manual
 * setup for the generated google-services.json + the
 * com.google.gms.google-services plugin.
 */
class HerbalApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    /** نطاق حياة التطبيق الكامل، يُستخدم فقط لمهام خلفية عمرها التطبيق نفسه (مثل تحميل القاموس المحلي). */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LocalLlmManager.init(this)

        val options = FirebaseOptions.Builder()
            .setApiKey("AIzaSyAkVYaspguYs6gXAOaV7xoiesa38nqgm10")
            .setApplicationId("1:497780761661:web:95ae225c648814c0ed7654")
            .setProjectId("semoharbs")
            .setStorageBucket("semoharbs.firebasestorage.app")
            .setGcmSenderId("497780761661")
            .build()

        val firebaseApp = if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this, options)
        } else {
            FirebaseApp.getInstance()
        }

        // App Check is installed immediately after Firebase initialization so every
        // subsequent Firestore/Auth request can carry an attestation token. Release
        // builds use Play Integrity; debug builds use Firebase's debug provider.
        if (firebaseApp != null) FirebaseSecurity.install()

        // ═══ سجل أعطال عن بعد — أداة إدمن جديدة بطلب صريح ═══
        // يلتقط أي استثناء غير مُعالَج (تصادم التطبيق) ويُرسل تفاصيله
        // (رسالة الخطأ + أول 25 سطر من الـ stack trace، بلا أي بيانات
        // شخصية عن المستخدم) إلى Firestore عبر CrashLogRepository، ثم يمرّر
        // الاستثناء كما هو للمعترِض الافتراضي السابق فيُكمل مساره الطبيعي
        // (تسجيل بـ Logcat + إنهاء العملية) — هذا توثيق للعطل وليس منعاً له.
        // يُضبط هنا (لا في MainActivity) ليلتقط أي تصادم بأي مكان بالتطبيق،
        // بما فيها ما قد يحدث قبل ظهور أي Activity على الإطلاق.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                container.crashLogRepository.report(
                    throwable = throwable,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    flavor = BuildConfig.FLAVOR,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        // إعدادات كاش Firestore المحلي تُضبط هنا - مرة واحدة فقط عند بدء
        // التطبيق، قبل أي Activity أو ViewModel أو Repository (مثل
        // HerbRepository أو SemoLearningRepository، وكلاهما يستخدمان نفس
        // كائن FirebaseFirestore.getInstance() المشترك) يلمس Firestore بأي
        // شكل. Firestore يرفض تغيير الإعدادات بعد أول استخدام فعلي للكائن
        // برسالة "FirebaseFirestore has already been started..." - كانت
        // هذه الإعدادات مضبوطة سابقاً داخل init{} الخاص بـ HerbRepository
        // نفسها، وكان هذا يعمل فقط بالصدفة لأنها كانت أول من يُستخدم؛ أي
        // إعادة ترتيب لاحقة لتسلسل استدعاءات AppViewModel (كإضافة قراءة
        // كاش محلي أو مزامنة مؤجّلة) قد تجعل مستودعاً آخر يلمس Firestore
        // أولاً بلا قصد، فيفشل ضبط الإعدادات هنا. وضعها في Application.onCreate
        // يضمن الترتيب دائماً بحكم دورة حياة التطبيق نفسها، بصرف النظر عن أي
        // تعديل مستقبلي بترتيب كود الشاشات أو الـViewModel.
        if (firebaseApp != null) {
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(HerbRepository.PERSISTENT_CACHE_BYTES)
                        .build()
                )
                .build()
        }

        // ينسخ قاعدة بيانات القاموس المحلي (SQLite، مُدمَجة كجزء أساسي من
        // التطبيق) مرة واحدة إلى تخزين التطبيق الداخلي ثم يفتحها — انظر توثيق
        // DictionaryLexicon لتفصيل لماذا SQLite بدل تحميل ملف JSON ضخم بالكامل
        // في الذاكرة (كان يسبب OutOfMemoryError صامتاً على الأجهزة محدودة
        // الرام فيبقى القاموس عالقاً على "جارٍ التحميل…" للأبد). يعمل على خيط
        // IO في الخلفية فلا يؤخر بدء التطبيق، وأي فشل يُعامَل بهدوء تام كما هو
        // موثّق في DictionaryLexicon.
        applicationScope.launch { DictionaryLexicon.preload(this@HerbalApp) }
        applicationScope.launch { runCatching { container.preferencesRepository.syncLanguageMirror() } }
    }

    // بدون هذا، Coil (v2.x) لا يعرف كيف يقرأ روابط data: (base64) —
    // فيرجّع لكل AsyncImage بالتطبيق صورة فاشلة/فارغة لأي عشبة صورتها
    // مخزّنة كـ base64. Coil يستخدم هذا الـ ImageLoader تلقائياً لأن
    // Application هنا تُنفّذ ImageLoaderFactory.
    //
    // على الأجهزة منخفضة الرام: كاش الصور بالذاكرة أُنقص لنسبة أصغر من
    // الرام المتاح، والصور تُفكّ بعمق ألوان أخفض (RGB_565 بدل ARGB_8888
    // الافتراضي) — يقلّل استهلاك كل صورة بالذاكرة للنصف تقريباً بأثر بصري
    // ضئيل جداً على صور صغيرة كصور الأعشاب. على الأجهزة القوية يبقى كل شيء
    // بأعلى جودة كما كان.
    //
    // كاش القرص لم يكن مضبوطاً إطلاقاً سابقاً، فكان Coil يستخدم سقفه
    // الافتراضي (حتى ٢٪ من المساحة الحرة، وقد يصل لـ٢٥٠ ميجابايت على جهاز
    // بمساحة تخزين كبيرة) — رغم أن صور الموسوعة هنا مصدرها الأصلي أصلاً
    // base64 محلي داخل بيانات Firestore المتزامنة (انظر DataUriFetcher)،
    // فتخزينها الفكوك أيضاً على القرص كان تكراراً غير ضروري يستهلك مساحة
    // إضافية بلا فائدة حقيقية. سقف صريح صغير (٣٠ ميجابايت) يبقي إعادة
    // العرض السريع لآخر الصور المُشاهدة بلا إبطاء واجهة المستخدم، مع تفادي
    // تضخم غير محدود بمرور الوقت.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            LocalLlmManager.release()
        }
    }

    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true

        return ImageLoader.Builder(this)
            .components { add(DataUriFetcher.Factory()) }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isLowRam) 15L * 1024 * 1024 else 30L * 1024 * 1024)
                    .build()
            }
            .apply { if (isLowRam) bitmapConfig(Bitmap.Config.RGB_565) }
            .build()
    }

    companion object {
        /** Same admin UID hardcoded in the web app's firebase-config.js */
        const val ADMIN_UID = "OWssFNrZDaZfeSlrLF8ReS8O6LM2"
    }
}
