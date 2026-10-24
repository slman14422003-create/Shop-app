package com.salman.herbalencyclopedia.data.ai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * قاموس مرادفات عربي عام، مبني مسبقاً من مصدرين حرّين ومفتوحين ومُدمَجين
 * بالكامل مع التطبيق — بنفس فلسفة [ArabicLexicon] بالضبط: بلا إنترنت، بلا
 * مفتاح API، وبلا أي تكلفة أو اشتراك أو حد استخدام:
 *
 * 1) **Rabih Dictionary** — قاموس مرادفات عربي عام.
 * 2) **Arabic WordNet v4** — الترجمة العربية لـ Open English WordNet؛
 *    تُستخرج منه فقط مجموعات الكلمات التي تقع ضمن نفس "synset" (أي تُعتبر
 *    مترادفة معنوياً)، بما فيها بعض المرادفات الإنجليزية الشائعة (مثل
 *    "thyme" لكلمة "زعتر") مما يساعد سيمو والبحث على فهم الاسم الأجنبي أو
 *    العلمي أيضاً.
 *
 * ## لماذا قاعدة بيانات SQLite غير مضغوطة، مُدمَجة كملف أصول عادي؟
 * كانت أول نسخة تقرأ ملف JSON مضغوط (~330 ألف مدخل) وتُفكّه بالكامل كسلسلة
 * نصية ~20 م.ب في الذاكرة ثم تبنيه ككائن JSONObject كامل ثم تحوّله لخريطة
 * HashMap ضخمة — عدة نسخ متزامنة من نفس البيانات في الذاكرة دفعة واحدة، وقد
 * ترمي `OutOfMemoryError` على أجهزة محدودة الرام (وهذا الخطأ تحديداً لا
 * يرثه `Exception` في كوتلن فيفلت من أي `catch (_: Exception)`).
 *
 * لإزالة أي احتمال فشل من هذا النوع نهائياً، القاموس الآن أبسط ما يمكن:
 * **ملف SQLite واحد غير مضغوط إطلاقاً** (`assets/lexicon/ar_synonyms.db`،
 * ~19 م.ب، مُستثنى أيضاً من ضغط الـ APK نفسه عبر `noCompress` في
 * `build.gradle.kts`) يُنسخ بايتات-لبايت (بلا أي فكّ ضغط، بلا أي معالجة،
 * `InputStream.copyTo` مباشرة) إلى تخزين التطبيق الداخلي مرة واحدة فقط، ثم
 * يُفتَح كقاعدة بيانات حقيقية. كل استعلام بعدها (`SELECT ... WHERE word = ?`)
 * يمرّ عبر فهرس المفتاح الأساسي مباشرة (أقل من مللي ثانية)، بلا أي بنية
 * بيانات ضخمة مقيمة في الذاكرة طوال عمر التطبيق. النتيجة: نسخ بايتات بسيط
 * لا يحتاج فكّ أي شيء، واستحالة عملية لتكرار عطل الذاكرة القديم.
 *
 * ## القاموس لم يعد "يُحمَّل" بمعنى قد يفشل بصمت — بل جزء أساسي مُدمَج
 * أي فشل حقيقي (نادر جداً بعد هذا التبسيط: مساحة تخزين ممتلئة تماماً، أو
 * تلف فعلي بالملف) لا يبقى غامضاً بعد الآن: [preload] يُسجّل الخطأ الكامل
 * في Logcat تحت الوسم "DictionaryLexicon"، ويُتاح عبر [lastError] لعرضه في
 * واجهة أدوات المطور بدل مؤشر تحميل عالق للأبد بلا تفسير — و[loadAttempted]
 * يميّز "لا يزال يعمل" عن "انتهى (نجاحاً أو فشلاً)" كي لا تلتبس الحالتان.
 * حتى في حال الفشل، يستمر كل من [HerbAssistant] وشاشات البحث بالعمل بالضبط
 * كما كانا قبل إضافة هذا القاموس، فقط بلا توسيع مرادفات إضافي — لا يصل أي
 * استثناء للواجهة ولا تتعطّل أي ميزة أخرى.
 *
 * يُحمَّل مرة واحدة عند إقلاع التطبيق ([preload]، يُستدعى من
 * `HerbalApp.onCreate`) على خيط IO في الخلفية دون أي تأخير محسوس على بدء
 * التطبيق.
 */
object DictionaryLexicon {

    private const val TAG = "DictionaryLexicon"

    private const val ASSET_PATH = "lexicon/ar_synonyms.db"

    /**
     * اسم ملف قاعدة البيانات المنسوخة داخلياً. يتضمّن رقم إصدار عمداً (رُفع
     * إلى v2 مع التحول لملف غير مضغوط، كي لا يُعاد استخدام أي نسخة v1 قديمة
     * متروكة من محاولة سابقة فاشلة على نفس الجهاز): إذا تغيّر ملف القاموس
     * المرفق مستقبلاً، يكفي رفع هذا الرقم كي تُنسخ النسخة الجديدة تلقائياً
     * على كل الأجهزة (يُنسخ الملف فقط إن لم يكن موجوداً أصلاً بهذا الاسم).
     */
    private const val DB_FILE_NAME = "ar_synonyms_v2.db"

    @Volatile
    private var db: SQLiteDatabase? = null

    @Volatile
    var isReady: Boolean = false
        private set

    /** true بمجرد انتهاء محاولة التحميل (نجاحاً أو فشلاً) — يميّز "لا يزال يعمل" عن "انتهى". */
    @Volatile
    var loadAttempted: Boolean = false
        private set

    /** رسالة الفشل الأخيرة إن فشل التحميل (فارغة إن نجح أو لم تُحاول بعد بعد). مفيدة لعرضها في أدوات المطور. */
    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var wordCount: Int = 0

    /** عدد الكلمات المحمَّلة فعلياً (مفيد لعرضه في "أدوات المطور" كتأكيد أن القاموس جاهز). */
    val loadedWordCount: Int
        get() = wordCount

    /**
     * ينسخ قاعدة البيانات من الأصول (إن لم تكن منسوخة أصلاً) ثم يفتحها.
     * آمن الاستدعاء أكثر من مرة (يتجاهل الاستدعاءات اللاحقة إن كانت جاهزة
     * أصلاً بنجاح). لا يرمي أي استثناء أو خطأ للخارج مهما حدث.
     */
    suspend fun preload(context: Context) {
        if (isReady) return
        withContext(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath(DB_FILE_NAME)
                if (!dbFile.exists() || dbFile.length() == 0L) {
                    dbFile.parentFile?.mkdirs()
                    val tmpFile = File(dbFile.parentFile, "$DB_FILE_NAME.tmp")
                    context.assets.open(ASSET_PATH).use { assetStream ->
                        tmpFile.outputStream().use { out ->
                            assetStream.copyTo(out, bufferSize = 64 * 1024)
                        }
                    }
                    if (!tmpFile.renameTo(dbFile)) {
                        tmpFile.copyTo(dbFile, overwrite = true)
                        tmpFile.delete()
                    }
                }

                val opened = SQLiteDatabase.openDatabase(
                    dbFile.path, null, SQLiteDatabase.OPEN_READONLY
                )
                opened.rawQuery("SELECT COUNT(*) FROM synonyms", null).use { cursor ->
                    if (cursor.moveToFirst()) wordCount = cursor.getInt(0)
                }
                db = opened
                lastError = null
                isReady = true
            } catch (t: Throwable) {
                // فشل هادئ ومقصود: البحث/سيمو يستمران بلا توسيع قاموس بدل تعطّل التطبيق.
                // Throwable (وليس Exception فقط) عمداً: يشمل هذا أي OutOfMemoryError
                // أو خطأ نظام آخر لا يرثه Exception في كوتلن.
                // يُسجَّل بالكامل في Logcat + يُتاح عبر lastError كي لا يبقى الفشل
                // غامضاً وراء مؤشر "جارٍ التحميل" الذي لا يميّز بين "لا يزال يعمل"
                // و"فشل فعلاً" — هذا بالضبط ما كان يصعّب تشخيص العطل سابقاً.
                Log.e(TAG, "فشل تحميل قاموس المرادفات المحلي", t)
                try { db?.close() } catch (_: Throwable) { /* تجاهل */ }
                db = null
                wordCount = 0
                lastError = "${t::class.java.simpleName}: ${t.message ?: "بلا رسالة"}"
                isReady = false
            } finally {
                loadAttempted = true
            }
        }
    }

    /** المرادفات المباشرة لكلمة واحدة مُطبَّعة مسبقاً (فارغة إن لم تُحمَّل بعد أو لم توجد). */
    fun synonymsOf(normalizedWord: String): List<String> {
        val database = db ?: return emptyList()
        if (normalizedWord.isBlank()) return emptyList()
        return try {
            database.rawQuery(
                "SELECT syns FROM synonyms WHERE word = ? LIMIT 1",
                arrayOf(normalizedWord)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).split('|').filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * يوسّع مجموعة كلمات مُطبَّعة بمرادفاتها من القاموس (بنفس نمط
     * [ArabicLexicon.expand]: إضافة فقط، لا حذف للكلمات الأصلية). يُستخدم في
     * كل من البحث الحر لسيمو ([HerbAssistant]) وشاشات البحث المباشر عن
     * الأعشاب.
     */
    fun expand(words: Set<String>): Set<String> {
        if (words.isEmpty() || db == null) return words
        val extra = words.flatMap { synonymsOf(it) }
        return if (extra.isEmpty()) words else words + extra
    }
}
