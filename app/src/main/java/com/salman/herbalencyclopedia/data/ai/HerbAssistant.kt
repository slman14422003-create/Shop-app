package com.salman.herbalencyclopedia.data.ai

import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Herb
import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * إعدادات قابلة للتعديل من "أدوات المطور" داخل التطبيق (AdminToolsScreen)
 * دون الحاجة لإعادة بناء التطبيق: عتبات التشابه وكلمات الإيقاف الإضافية.
 * القيم تُحمَّل من PreferencesRepository عند بدء التطبيق وتُطبَّق هنا مباشرة،
 * وأي تعديل من شاشة الأدوات يُحدّثها فوراً وحياً في نفس الجلسة (بلا حاجة
 * لإعادة تشغيل) لأن كل الشاشات تقرأ من هذا الكائن.
 */
object AiConfig {
    /** بُعد تجميع النقاط المتشابهة من أعشاب مختلفة في نقطة مشتركة واحدة (0..1). */
    var similarityThreshold: Double = 0.34
        set(value) { field = value.coerceIn(0.05, 0.95) }

    /** حد قبول نتيجة في البحث الحر عن سؤال المستخدم (0..1). قيمة أقل = إجابات أكثر لكن أقل دقة. */
    var searchThreshold: Double = 0.12
        set(value) { field = value.coerceIn(0.02, 0.9) }

    /** كلمات إيقاف إضافية يضيفها المطوّر (تُستبعد من التحليل، بصيغة مُطبَّعة أو خام). */
    var extraStopWords: Set<String> = emptySet()

    /**
     * مرادفات يعلّمها المطوّر لسيمو: كل مُدخل هو "كلمة جديدة → الكلمة القياسية
     * التي يجب أن يفهمها سيمو بدلاً منها" (مثال: "ينفع" → "فائدة"). تُستخدم
     * في كل عمليات تحليل النص (المقارنة والبحث الحر وأمثلة التدريب أدناه)،
     * فتوسّع فهم سيمو للعبارات المرادفة بلا حاجة لتغيير أي كود.
     */
    var synonyms: Map<String, String> = emptyMap()

    /**
     * "حالات مدرَّبة" يضيفها المطوّر يدوياً: سؤال نموذجي مع الرد المطلوب
     * بالضبط. عند سؤال المستخدم شيئاً مشابهاً بدرجة كافية لأحد هذه الأمثلة
     * (حسب [trainedMatchThreshold])، يرد سيمو بالنص المدرَّب مباشرة بدل
     * الاعتماد على المنطق العام — وهذه هي آلية "تطوير النماذج وفهم الحالات"
     * اليدوية: تعليم مباشر بلا إعادة بناء التطبيق.
     */
    var trainedExamples: List<TrainedExample> = emptyList()

    /** حد التشابه (0..1) الذي يجب أن تبلغه رسالة المستخدم مع مثال مدرَّب ليُستخدم رده مباشرة. */
    var trainedMatchThreshold: Double = 0.45
        set(value) { field = value.coerceIn(0.1, 0.95) }

    /**
     * حالات "تعلّمها سيمو بنفسه" من واقع الاستخدام: عندما يجيب سيمو بحرية
     * (بحث حر ضمن بيانات الموسوعة) ويُقيّم المستخدم الرد بـ 👍 في شاشة
     * الدردشة، تُحفَظ نقطة السؤال↔الرد هنا تلقائياً — فتصبح إجابته على أسئلة
     * مشابهة لاحقاً فورية وواثقة دون أي تدخل من المطوّر. هذا هو المعنى
     * العملي لعبارة "نموذج يعتمد على الموسوعة ويطوّر نفسه بنفسه": كل حالة
     * متعلَّمة مصدرها إجابة استُخرجت أصلاً من نصوص الموسوعة، ومصدر الثقة بها
     * هو تقييم صريح من مستخدم حقيقي، وليس تخميناً. تبقى منفصلة عن
     * [trainedExamples] (تدريب المطوّر اليدوي المحمي دوماً)، وقابلة للمراجعة
     * أو الحذف أو "الترقية" لتدريب يدوي دائم من أدوات المطور.
     */
    var autoLearnedExamples: List<TrainedExample> = emptyList()

    /** تفعيل/تعطيل التعلّم الذاتي من تقييمات المستخدمين (👍/👎) في شاشة الدردشة. */
    var autoLearnEnabled: Boolean = true

    /**
     * الوضع الذكي عبر الإنترنت (جديد): سيمو يبقى محلياً بالكامل كما كان
     * تماماً بلا أي تغيير في السلوك عند تعطيل هذا الخيار أو عند انعدام
     * اتصال الإنترنت فعلياً (راجع [OnlineAssistant] وSemoAssistantScreen —
     * الفحص يحدث *قبل* أي محاولة اتصال، فلا يوجد أي فرق محسوس محلياً سواء
     * كان هذا الخيار مفعَّلاً أم لا حين لا تتوفر شبكة). عند تفعيله وتوفر
     * مفتاح Gemini API صالح ووجود اتصال إنترنت فعلي، تُرسَل الأسئلة التي
     * تحتاج بحثاً فعلياً (لا الترحيب/الشكر/الحالات المدرَّبة، تلك تبقى فورية
     * محلياً دوماً) إلى نموذج Gemini المجاني من Google — وهو مجاني ومتاح
     * فعلياً من سوريا مباشرة دون VPN (رُفع الحظر عنه في سبتمبر 2026) خلافاً
     * لخدمات أخرى (ChatGPT) لا تزال محجوبة، وهذا سبب اختياره تحديداً. أي
     * فشل في الاتصال (انقطاع، انتهاء مهلة، مفتاح غير صالح) يعود تلقائياً
     * للبحث المحلي المعتاد دون أي رسالة خطأ للمستخدم — راجع
     * [OnlineAssistant.answer].
     */
    var onlineEnabled: Boolean = false

    /** مفتاح Gemini API (من Google AI Studio، مجاني). يُدخله المطوّر من أدوات المطور. */
    var onlineApiKey: String = ""

    /**
     * عنوان أساس بديل (بروكسي/مرآة) يُستبدَل به `https://generativelanguage.googleapis.com`
     * عند الاتصال بـ Gemini — يُترك فارغاً افتراضياً (الاتصال المباشر بخوادم
     * Google كما كان تماماً). يُستخدم فقط حين يكون الاتصال المباشر محجوباً
     * فعلياً (كما هو الحال حالياً من سوريا رغم رفع الحظر الرسمي)؛ عندها يضع
     * المطوّر هنا عنوان خادم بروكسي خاص به (Cloudflare Worker مثلاً) يُعيد
     * توجيه نفس المسار (`/v1beta/models/...`) لخوادم Google الحقيقية دون أي
     * تعديل على الجسم أو الرد، فيبقى كل منطق [OnlineAssistant] كما هو. راجع
     * التعليق أعلى [OnlineAssistant.answer] لتفاصيل بناء الرابط الكامل.
     */
    var onlineBaseUrl: String = ""

    /**
     * مُعرِّف نموذج Gemini المُستخدَم. الافتراضي "gemini-flash-latest" هو
     * اسم مستعار (alias) من Google يشير دوماً لأحدث نموذج Flash مجاني
     * موصى به، فيبقى يعمل تلقائياً حتى بعد إيقاف Google لإصدارات محدَّدة
     * (كما حدث سابقاً مع gemini-2.0-flash وgemini-2.5-flash) دون حاجة
     * لتحديث التطبيق. قابل للتغيير من أدوات المطور لاسم نموذج محدَّد إن لزم.
     */
    var onlineModel: String = "gemini-flash-latest"

    val defaultSimilarityThreshold = 0.34
    val defaultSearchThreshold = 0.12
    val defaultTrainedThreshold = 0.45
    val defaultAutoLearnEnabled = true
    val defaultOnlineEnabled = false
    val defaultOnlineModel = "gemini-flash-latest"
    val defaultOnlineBaseUrl = ""

}

/**
 * قدرة جديدة: "إدراك" سيمو لتصنيفات الموسوعة (Category). حتى الآن كان سيمو
 * يقرأ فقط حقول العشبة النصية الستة (الاسم/الفوائد/الاستخدام/التحذيرات/
 * الأضرار/الملاحظات) ولا يعرف بوجود فئات إطلاقاً رغم أنها جزء أصيل من بنية
 * الموسوعة (تُعرض لها شاشتها الخاصة CategoryHerbsScreen تماماً كالأعشاب).
 * بنفس نمط [AiConfig] (كائن مركزي حي يُحدَّث من خارج هذا الملف دون تغيير
 * توقيع أي دالة أو شاشة) تُحدِّث [com.salman.herbalencyclopedia.ui.AppViewModel]
 * هذه الخريطة (معرّف الفئة ← اسمها) كلما وصلت فئات جديدة من Firestore، فيصبح
 * اسم فئة العشبة جزءاً فعلياً من نصوص العشبة القابلة للبحث والفهرسة أدناه
 * (انظر [HerbAssistant.searchableFields] و[HerbAssistant.corpusIndexFor])
 * بلا أي حاجة لتمرير قائمة فئات عبر كل دالة/شاشة تستخدم سيمو.
 */
object HerbCategoryLookup {
    @Volatile
    var categoryNames: Map<String, String> = emptyMap()
}

/** حالة تدريب واحدة: سؤال نموذجي والرد المخصّص الذي يجب أن يعطيه سيمو له. */
data class TrainedExample(val pattern: String, val response: String)

/**
 * سيمو — المساعد الذكي للموسوعة. يعمل بالكامل داخل الجهاز، بلا اتصال
 * إنترنت، بلا مفتاح API، وبلا أي تكلفة أو إعداد. يقرأ نصوص الأعشاب الموجودة
 * أصلاً في الموسوعة (الفوائد، الاستخدام، التحذيرات، الأضرار، الملاحظات)،
 * يحلّلها، يجيب بحرية على أي سؤال، ولا يبني مقارنة منظّمة بين أكثر من عشبة
 * إلا عندما يُطلب منه ذلك صراحة (باختيار عشبتين أو ذكرهما بالاسم في السؤال).
 *
 * "تدريبه" يحدث على ثلاثة مستويات:
 * 1) يدوياً من أدوات المطور (مرادفات + حالات مدرَّبة، انظر [AiConfig]).
 * 2) قاموس عربي عام مرفق محلياً مع التطبيق ([DictionaryLexicon]، مبني من
 *    Rabih Dictionary وArabic WordNet) يوسّع فهم الكلمات والمرادفات العامة
 *    بلا أي تدخل يدوي وبلا إنترنت — تفصيل ذلك في توثيق [DictionaryLexicon].
 * 3) ذاتياً أثناء الاستخدام الفعلي: [CorpusIndex] يُبنى تلقائياً من نصوص
 *    الموسوعة نفسها ليكتشف أوزان الكلمات وعلاقاتها الضمنية دون أي تدخل
 *    يدوي، و[recordFeedback] يحوّل تقييمات المستخدمين (👍) على إجابات
 *    البحث الحر إلى حالات مدرَّبة تلقائياً — أي أن سيمو يعتمد بالكامل على
 *    بيانات الموسوعة، ثم يراكم فوقها خبرة من استخدامه الفعلي. هذه الخبرة
 *    ليست محصورة بجهاز واحد: [learningKey] و[mergeLearnedExamples] يدعمان
 *    مزامنتها بين كل الأجهزة عبر SemoLearningRepository (Firestore)، فما
 *    يتعلّمه سيمو من مستخدم على جهاز يصل تلقائياً لبقية الأجهزة، بدل أن
 *    يحتاج كل جهاز لتعلّم نفس الشيء بنفسه من الصفر.
 * لا يوجد هنا نموذج شبكة عصبية يحتاج تدريباً فعلياً؛ هذا "تعلّم" رمزي بحت
 * (إحصائي + تغذية راجعة) مناسب لتشغيل محلي بالكامل دون إنترنت أو معالجة ثقيلة.
 *
 * إضافتان جديدتان: 1) [isFollowUpQuestion] تمكّن شاشة الدردشة من "ذاكرة
 * محادثة" خفيفة — متابعة الحديث عن نفس العشبة عبر أسئلة متتالية دون تكرار
 * اسمها في كل مرة (انظر توثيقها). 2) نية "خطة" جديدة ([buildPlanAnswer])
 * تركّب رداً عملياً واحداً من عدة حقول معاً (استخدام + هدف + تحذير) بدل
 * الاكتفاء بعرض حقل واحد، لمن يسأل "كيف أبدأ" أو "أعطني روتيناً".
 */
object HerbAssistant {

    // ── أدوات معالجة نصوص عربية بسيطة ──────────────────────────────────

    private val baseStopWords = setOf(
        "من", "في", "على", "الى", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك",
        "التي", "الذي", "و", "أو", "او", "ثم", "قد", "لا", "لم", "لن", "كان",
        "يكون", "تكون", "بعض", "كل", "أي", "اي", "ما", "هل", "أن", "ان", "كما",
        "حيث", "بين", "بعد", "قبل", "عند", "أيضا", "ايضا", "جدا", "جداً",
        "يمكن", "يجب", "يفضل", "غير", "دون", "بدون", "لها", "له", "بها", "به",
        // قدرة جديدة: أدوات استفهام عامية شائعة جداً (خليجي/مصري/شامي/مغاربي)
        // لا تحمل بذاتها أي دلالة موضوعية (نظير "ما"/"هل" الفصيحة أعلاه)،
        // فإضافتها لكلمات الإيقاف تمنعها من "تخفيف" مقاييس التشابه في
        // [rawContainmentRatio]/[weightedSimilarity] لمجرد عدم مطابقتها أي
        // كلمة من نصوص الموسوعة — تماماً كما لا تُخفِّف "ما"/"هل" الفصيحتان
        // أصلاً. لا تُستخدَم [wordsOf] في فحوص النيّة (تلك تفحص qNorm الكامل
        // مباشرة) فإزالتها هنا لا يؤثر على كشف "استخدام"/"اقتراح" وغيرها.
        "كيف", "شو", "ايش", "إيش", "وش", "شنو", "ليش", "ليه", "لماذا", "علاش",
        "وين", "فين", "امتى", "متى", "قديش", "كام", "ازاي", "إزاي", "شلون",
        "كيفاش", "ايهم", "أيهم"
    )

    private val SPACES = Regex("\\s+")

    /** كلمات الإيقاف بعد التطبيع مرة واحدة (كانت تُقارَن خاماً بكلمات مُطبَّعة، وتُعاد بناؤها مع كل كلمة). */
    private val baseStopWordsNorm: Set<String> by lazy { baseStopWords.map { normalize(it) }.toSet() }
    private var stopCacheRef: Set<String>? = null
    private var stopCacheValue: Set<String> = emptySet()

    /**
     * كلمات الإيقاف الفعلية = الأساسية + إضافات المطوّر. كان هذا الـgetter يبني
     * مجموعة جديدة كاملة *مع كل كلمة* تُفحَص عند وجود كلمة إيقاف إضافية واحدة على
     * الأقل (لأنه يُستدعى داخل filter لكل كلمة)؛ الآن تُخزَّن النتيجة وتُعاد
     * بنائها فقط عند تغيّر مرجع [AiConfig.extraStopWords].
     */
    private val stopWords: Set<String>
        get() {
            val extra = AiConfig.extraStopWords
            if (extra.isEmpty()) return baseStopWordsNorm
            if (stopCacheRef === extra) return stopCacheValue
            val built = baseStopWordsNorm + extra.map { normalize(it) }
            stopCacheRef = extra
            stopCacheValue = built
            return built
        }

    /**
     * التطبيع العربي موحَّد الآن في [ArabicLexicon.normalizeText] (نفس القواعد
     * السابقة + إزالة التطويل وتحويل الأرقام العربية-الهندية لتُلتقط أعمار مثل
     * "٣ سنوات"، وتوحيد الكاف/الياء الفارسيتين). كانت هذه الدالة تُنشئ ثلاثة
     * كائنات Regex جديدة في *كل* استدعاء (تجميع النمط من الصفر)، وتُستدعى آلاف
     * المرات لكل سؤال — سبب رئيسي لبطء "التفكير".
     */
    private fun normalize(text: String): String = ArabicLexicon.normalizeText(text)

    private fun tokensOf(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(SPACES).filter { it.isNotBlank() }

    private fun wordsOf(text: String): Set<String> {
        val stops = stopWords
        val base = LinkedHashSet<String>()
        for (t in tokensOf(normalize(text))) {
            if (t.length > 1 && t !in stops) base += t
        }
        return applySynonyms(DialectNormalizer.expand(base))
    }

    /**
     * قدرة جديدة: طبقة "فهم لهجات" مدمجة بالتطبيق نفسه (لا تحتاج أي إعداد
     * يدوي من المطوّر، بعكس [AiConfig.synonyms] القابلة للتعديل من أدوات
     * المطور) — تغطي كلمات عامية شائعة جداً بالعربية الدارجة (خليجي/شامي/
     * مصري) لمفاهيم صحية عامة متكررة (جيد/سيّئ/يفيد/يؤلم...)، فيفهمها سيمو
     * فور التثبيت بلا أي تدخل. منفصلة عمداً عن [HealthTopicSynonyms] (تلك
     * مجموعات مواضيع كاملة تتوسّع فيما بينها بحثاً عن هدف صحي) وعن
     * [AiConfig.synonyms] (تلك يضيفها المطوّر يدوياً حسب حاجته) — هذه كلمات
     * مفردة عامة تُستبدل بمرادفها الفصيح القياسي واحدة تلو الأخرى، لتوسيع
     * "إدراك" سيمو للصياغات العامية بلا حصر ذلك بقوالب التحية/الشكر فقط.
     */
    private object DialectNormalizer {
        private val rawMap = mapOf(
            "دوا" to "علاج", "الدوا" to "العلاج", "دوى" to "علاج",
            "بيقطع" to "يوقف", "بيوقف" to "يوقف", "قاطع" to "موقف",
            "بينشف" to "يجفف", "منيح" to "جيد", "منيحة" to "جيدة",
            "زين" to "جيد", "كويس" to "جيد", "كويسة" to "جيدة",
            "بيهدي" to "يهدئ", "بيريح" to "يريح", "مريح" to "مهدئ",
            "وجعة" to "ألم", "توجعني" to "يؤلمني", "بيوجع" to "يؤلم",
            "بينفع" to "يفيد", "ينفع" to "يفيد", "نافع" to "مفيد",
            "مضر" to "ضار", "بيضر" to "يضر", "ضرر" to "ضار",
            "حبة" to "حبوب", "دقة" to "مسحوق", "مطحون" to "مسحوق",
            "بيسمن" to "يزيد الوزن", "بينحف" to "ينقص الوزن",
            // توسيع "فهم مصطلحات الفائدة": قدرة جديدة تغطي صياغات إضافية
            // شائعة لنفس معنى "فائدة/يفيد" لم تكن مغطاة سابقاً (فصحى أو
            // عامية)، فيفهمها سيمو حتى لو لم يذكر السائل كلمة "فائدة" أو
            // "فوائد" حرفياً — انظر أيضاً [benefitsIntentWords] أدناه حيث
            // تُستخدم صيغتها القياسية بعد هذا التطبيع.
            "يشفي" to "يفيد", "بيشفي" to "يفيد", "شافي" to "مفيد", "شافية" to "مفيدة",
            "خواص" to "فوائد", "خاصية" to "فائدة", "منافع" to "فوائد", "منفعة" to "فائدة",
            "تنفع" to "يفيد", "عالج" to "علاج",
            // قدرة جديدة: توسيع إضافي لتغطية أعراض/مصطلحات عامية شائعة جداً
            // في أسئلة صحية حقيقية (دوخة، انتفاخ، حرقة، تعب...) لم تكن
            // مغطاة سابقاً، فتُطبَّع لمرادفها الفصيح القياسي المستخدم فعلاً
            // في نصوص الموسوعة و/أو في مجموعات [HealthTopicSynonyms] أدناه.
            "دايخ" to "دوار", "دايخة" to "دوار", "دوخة" to "دوار", "بيدوخني" to "دوار",
            "نفخة" to "انتفاخ", "منفوخ" to "منتفخ", "بينفخ" to "ينفخ",
            "قرقرة" to "غازات", "بيقرقر" to "غازات",
            "حرقان" to "حرقة", "بيحرق" to "حارق",
            "تعبان" to "متعب", "تعبانة" to "متعبة", "خمول" to "كسل",
            "بيلين" to "ملين", "بيهيج" to "يحفز", "بيسهل" to "مسهل", "بيقبض" to "قابض"
        )
        private val map: Map<String, String> by lazy {
            rawMap.mapKeys { normalize(it.key) }.mapValues { normalize(it.value) }
        }
        fun apply(word: String): String = map[word] ?: word
        /**
         * إصلاح: بعض قيم القاموس عبارة من كلمتين ("يزيد الوزن")؛ كانت تُضاف كـ"كلمة"
         * واحدة تحوي مسافة فلا تُطابق أي شيء أبداً. تُقسَّم الآن إلى كلمات مستقلة.
         */
        fun expand(words: Set<String>): Set<String> {
            val out = LinkedHashSet<String>()
            for (w in words) for (p in apply(w).split(' ')) if (p.isNotBlank()) out += p
            return out
        }
    }

    /**
     * يستبدل كل كلمة بمرادفها القياسي إن وُجد في [AiConfig.synonyms] (بعد
     * تطبيع الطرفين)، بحيث تُحسب "ينفع" و"يفيد" مثلاً ككلمة واحدة أثناء أي
     * مقارنة أو بحث — هذا هو أثر "تعليم سيمو كلمات جديدة" يدوياً على أرض الواقع.
     */
    private var synCacheRef: Map<String, String>? = null
    private var synCacheTable: Map<String, String> = emptyMap()

    private fun applySynonyms(words: Set<String>): Set<String> {
        val raw = AiConfig.synonyms
        if (raw.isEmpty()) return words
        if (synCacheRef !== raw) {
            synCacheTable = raw.entries.associate { (k, v) -> normalize(k) to normalize(v) }
            synCacheRef = raw
        }
        val table = synCacheTable
        if (table.isEmpty()) return words
        val out = LinkedHashSet<String>()
        for (w in words) {
            val mapped = table[w] ?: w
            for (p in mapped.split(' ')) if (p.isNotBlank()) out += p
        }
        return out
    }

    /** يقسّم فقرة حرة إلى نقاط قصيرة قابلة للمقارنة والعرض كعناصر منفصلة. */
    /**
     * إصلاح خلل حقيقي أُبلغ عنه ("حتى الفواصل والنقط مش نظامي"): كانت أي
     * نقطة "." أو فاصلة "," تُقسِّم النص دوماً، حتى عندما تكون جزءاً من رقم
     * عشري أو جرعة فعلية — مثال حقيقي: "خذ 2.5 غرام يومياً" كانت تتحوّل إلى
     * نقطتين منفصلتين مشوَّهتين ("خذ 2" و"5 غرام يومياً")، فتضيع الجرعة
     * الحقيقية تماماً. الإصلاح: "." أو "," يُقسِّمان النص فقط عندما لا يكون
     * أي منهما محاطاً برقم مباشرة من الجهتين (عبر lookaround)، فيبقى "2.5"
     * أو "1,200" سليماً كوحدة واحدة، بينما تستمر نهاية الجملة العادية
     * بالتقسيم الصحيح كما كانت.
     */
    private val POINT_SPLIT = Regex("[،\\n؛;]|(?<!\\d)[.,](?!\\d)|\\s-\\s")
    private val splitCache = HashMap<String, List<String>>()

    @Synchronized
    private fun splitPoints(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        splitCache[text]?.let { return it }
        val result = text.split(POINT_SPLIT)
            .map { it.trim().trim('-', ' ') }
            .filter { it.length > 2 }
        if (splitCache.size > 20_000) splitCache.clear()
        splitCache[text] = result
        return result
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    private fun containsAny(normalizedText: String, terms: List<String>): Boolean =
        terms.any { normalizedText.contains(normalize(it)) }

    /**
     * إصلاح خلل حقيقي: هل يطابق [qNorm] إحدى عبارات [phrases] كـ**كلمات
     * مستقلة كاملة**، لا كسلسلة فرعية داخل كلمة أطول (بعكس [containsAny])؟
     * ويُشترط أيضاً أن يكون السؤال كله قصيراً (≤ [maxWords]) حتى لا تُخطَف
     * جملة طويلة فيها نية حقيقية (مقارنة/سؤال مفصَّل) لمجرد ورود كلمة قصيرة
     * منها بالمصادفة (مثال حقيقي أُبلغ عنه: "hi" ضمن اسم علمي لاتيني لعشبة
     * مثل Thymus أو chamomilla كانت تُخطَف كتحية عبر [containsAny] القديم).
     */
    private fun isShortExactPhraseMatch(qNorm: String, phrases: List<String>, maxWords: Int): Boolean {
        val words = tokensOf(qNorm)
        if (words.isEmpty() || words.size > maxWords) return false
        val wordSet = words.toSet()
        return phrases.any { phrase ->
            val phraseWords = tokensOf(normalize(phrase))
            phraseWords.isNotEmpty() && phraseWords.all { it in wordSet }
        }
    }

    private val pureGreetingPhrases = listOf(
        "مرحبا", "مرحباً", "مرحبتين", "اهلا", "أهلا", "هلا", "هلا بيك", "هلا فيك",
        "السلام عليكم", "hello", "hi", "hey"
    )

    /** انظر توثيق [isShortExactPhraseMatch] أعلاه — هذه أداة الاستدعاء المخصَّصة للترحيب تحديداً. */
    private fun isPureGreeting(qNorm: String): Boolean = isShortExactPhraseMatch(qNorm, pureGreetingPhrases, maxWords = 5)

    private fun herbNames(herbs: Collection<Herb>): String = herbs.joinToString(" و") { it.name }

    // ── قدرة جديدة: فهم النفي ────────────────────────────────────────────

    /**
     * أدوات النفي (مُطبَّعة). إصلاحان جوهريان هنا:
     * 1) كانت القائمة تحوي "ما" — وهي أيضاً أداة استفهام شائعة جداً ("ما فوائد ..."،
     *    "ما هي أفضل عشبة ...")، فكان أي سؤال يبدأ بـ"ما" ويذكر اسم عشبة خلال ٣
     *    كلمات يُعامَل كأن المستخدم *استبعد* تلك العشبة! حُذفت.
     * 2) كانت تحوي "إلا" بهمزتها الخام بينما كلمات السؤال مُطبَّعة ("الا") فلا
     *    تتطابق أبداً؛ تُطبَّع القائمة كلها الآن مرة واحدة.
     */
    private val negationTriggers: Set<String> by lazy {
        normSet("لا", "مو", "مب", "مش", "بدون", "غير", "عدا", "ماعدا", "إلا", "باستثناء", "استثناء", "ماابي", "ماريد", "مابدي", "بلاش", "تجنب")
    }

    /**
     * هل ذُكر [term] (عادة اسم عشبة) بصيغة منفية داخل السؤال؟ نفي "حقيقي" =
     * وجود أداة نفي ضمن ٣ كلمات قبل ورود أول كلمة من [term] مباشرة، لا نفي
     * عشوائي بأي مكان من الجملة — مثال واقعي: "اقترح عشبة للنوم بس مو
     * البابونج" ينفي "البابونج" تحديداً لا "النوم". المطابقة الآن كلمة بكلمة
     * (بأشكالها) لا بالاحتواء الحرفي — كان الاحتواء المتبادل يطابق كلمات قصيرة
     * كـ"و" مع أي اسم عشبة يحوي هذا الحرف.
     */
    private fun isNegatedMention(qNorm: String, term: String): Boolean {
        val termCore = normalize(ArabicLexicon.arabicNameCore(term))
        if (termCore.isBlank()) return false
        val words = tokensOf(qNorm)
        val firstName = tokensOf(termCore).map { ArabicLexicon.stripAl(it) }.firstOrNull { it.length >= 3 } ?: return false
        val idx = words.indexOfFirst { w -> firstName == ArabicLexicon.stripAl(w) || firstName in ArabicLexicon.formsOf(w) }
        if (idx < 0) return false
        val windowStart = (idx - 3).coerceAtLeast(0)
        return (windowStart until idx).any { words[it] in negationTriggers }
    }

    // ── قدرة جديدة: تلميحات سياقية صريحة بالسؤال (عمر/حمل/أدوية/جراحة) ─────

    private fun normSet(vararg words: String): Set<String> =
        words.map { normalize(it) }.filter { it.isNotBlank() }.toSet()

    private val childWordSet: Set<String> by lazy {
        normSet("طفل", "أطفال", "رضيع", "رضع", "صغير", "صغيرة", "ابني", "ابنتي", "بنتي", "ولدي", "طفلي", "طفلتي", "طفلة", "بيبي", "مولود", "مراهق", "مراهقة")
    }
    private val pregnancyWordSet: Set<String> by lazy {
        normSet("حامل", "حمل", "حوامل", "رضاعة", "ارضع", "مرضع", "مرضعة", "حملي")
    }
    private val medicationWordSet: Set<String> by lazy {
        normSet("دواء", "دوا", "أدوية", "ادويتي", "دوائي", "وارفارين", "مميع", "مميعات", "اسبرين", "انسولين", "كورتيزون", "كيماوي", "ليثيوم", "ديجوكسين", "باراسيتامول", "ايبوبروفين", "ميتفورمين", "ستاتين")
    }
    private val surgeryWordSet: Set<String> by lazy { normSet("جراحة", "جراحية", "تخدير") }
    private val durationWordSet: Set<String> by lazy { normSet("اسبوع", "اسبوعين", "اسابيع", "أسابيع", "شهرين", "أشهر", "شهور") }
    private val ageNumberRe = Regex("\\d+\\s*(سنه|سنوات|سنين|شهر|اشهر|شهور|يوم|ايام)")

    /** كل كلمات السؤال + أشكالها (بلا سوابق/لواحق شائعة) — أساس مطابقة الكلمات المفردة. */
    private fun formsSetOf(qNorm: String): Set<String> {
        val toks = tokensOf(qNorm)
        val out = HashSet<String>(toks.size * 4 + 4)
        for (t in toks) {
            out += t
            out.addAll(ArabicLexicon.formsOf(t))
        }
        return out
    }

    /**
     * التقاط تلميحات "سياقية" صريحة بالسؤال — تفاصيل نادراً ما تحويها الموسوعة
     * بدقة لكل حالة، فبدل تجاهلها وعرض جواب عام وكأنه يغطيها تُضاف ملاحظة
     * صريحة بنهاية الجواب. إصلاحات: (1) كان نمط العمر يبحث عن "سنة" بينما نص
     * السؤال مُطبَّع ("سنه") فلا يلتقط "عمره 5 سنة" أبداً؛ (2) لم تكن تُلتقط
     * "سنوات" ولا الأرقام العربية-الهندية؛ (3) أُضيفت تلميحات الحمل والأدوية
     * والجراحة. يُمرَّر هنا نص السؤال بعد إزالة أسماء الأعشاب (كي لا يُحسَب
     * "لسان الحمل" اسم عشبة كأنه ذكر للحمل).
     */
    private fun hintsFor(qNorm: String, includePregnancy: Boolean): List<String> {
        if (qNorm.isBlank()) return emptyList()
        val forms = formsSetOf(qNorm)
        val hints = ArrayList<String>(3)
        val hasChild = childWordSet.any { it in forms }
        val hasAge = ageNumberRe.containsMatchIn(qNorm)
        val hasDuration = durationWordSet.any { it in forms }
        if (hasChild || hasAge) {
            hints.add("📌 لاحظت إنك بتسأل عن حالة عمرية محدَّدة (طفل/عمر معيّن) — بيانات الموسوعة عامة وغير مفصَّلة حسب العمر، فاستشارة طبيب أو صيدلاني هون أهم من العادة.")
        } else if (hasDuration) {
            hints.add("📌 لاحظت إنك ذكرت مدة استخدام محدَّدة — الموسوعة لا تحدد مدة استخدام آمنة بدقة، يُفضَّل تأكيدها من مختص قبل الالتزام بها.")
        }
        if (includePregnancy && pregnancyWordSet.any { it in forms }) {
            hints.add("🤰 ذكرتَ الحمل أو الرضاعة — بعض الأعشاب لا تناسبهما، فاستشر طبيبك قبل استخدام أي عشبة خلال هذه الفترة.")
        }
        if (medicationWordSet.any { it in forms }) {
            hints.add("💊 ذكرتَ دواءً أو علاجاً — بعض الأعشاب تتداخل مع الأدوية (خصوصاً أدوية الضغط والسكر والسيولة والقلب)، فاسأل الصيدلاني أو الطبيب قبل الجمع بينهما.")
        }
        if (surgeryWordSet.any { it in forms }) {
            hints.add("🏥 ذكرتَ عملية أو تخديراً — يُنصح بإيقاف كثير من الأعشاب قبلها بمدة، فاسأل طبيبك عن ذلك.")
        }
        return hints
    }

    /** يُلحق [hintsFor] بنهاية الجواب إن وُجدت تلميحات، وإلا يعيد النص كما هو. */
    private fun withHints(text: String, qNorm: String, includePregnancy: Boolean = true): String {
        val hints = hintsFor(qNorm, includePregnancy)
        return if (hints.isEmpty()) text else text + "\n\n" + hints.joinToString("\n")
    }

    /**
     * توافقاً مع نقاط الاستدعاء الحالية في [answerDetailed]: يُلحق [hintsFor]
     * بنهاية الجواب بحسب نص السؤال الخام، بعد حذف أسماء الأعشاب المحدَّدة منه
     * أولاً (نفس معالجة [qNormForIntent]) — حتى لا يُحتسَب اسم عشبة يحوي بذاته
     * كلمة حساسة (مثال حقيقي: "لسان الحمل" يحوي كلمة "حمل") كأنه ذكر فعلي
     * للحمل من المستخدم.
     */
    private fun withContextHint(text: String, question: String, herbs: List<Herb> = emptyList()): String =
        withHints(text, qNormWithoutHerbNames(normalize(question), herbs))

    // ── حالات طوارئ/أزمات: أولوية على أي جواب عن الأعشاب ────────────────

    private val crisisPhrases: List<String> by lazy {
        listOf(
            "انتحر", "اقتل نفسي", "اقتل حالي", "اذي نفسي", "اؤذي نفسي", "ابغى اموت", "ابي اموت",
            "اريد ان اموت", "بدي اموت", "انهي حياتي", "انهاء حياتي", "اخلص من حياتي", "ما ابغى اعيش", "ما اريد اعيش"
        ).map { normalize(it) }
    }

    /** رد داعم ومختصر عند ذكر إيذاء النفس/الانتحار — يحلّ محل أي جواب آخر. */
    private fun crisisReply(qNorm: String): String? {
        if (crisisPhrases.none { qNorm.contains(it) }) return null
        return "أنا آسف جداً أنك تشعر بهذا 💚 ما تمرّ به يستحق دعماً حقيقياً، وأنا مجرد مساعد للأعشاب ولا أستطيع أن أكون هذا الدعم.\n\n" +
            "من فضلك تواصل الآن مع شخص تثق به (فرد من العائلة أو صديق قريب)، وإن كنت في خطر فوري فاتصل بخدمات الطوارئ في بلدك أو توجّه لأقرب مستشفى.\n\n" +
            "وإن رغبت أن تحكي لي ما يحدث فأنا هنا أسمعك."
    }

    private val redFlagPhrases: List<String> by lazy {
        listOf(
            "الم في الصدر", "الم بالصدر", "الم صدر", "ضيق تنفس", "ضيق في التنفس", "صعوبه في التنفس", "صعوبه تنفس",
            "اختناق", "فقدان الوعي", "فقدت الوعي", "اغماء", "نزيف شديد", "نزيف حاد", "قيء دم", "تقيا دم", "دم في البراز",
            "تسمم", "جرعه زايده", "جرعه زائده", "تورم الوجه", "تورم الشفه", "تورم الحلق", "حساسيه شديده", "نوبه صرع", "تشنجات", "جلطه", "سكته"
        ).map { normalize(it) }
    }

    /** تنبيه طوارئ يُوضَع أول الجواب عند ذكر أعراض خطيرة — لا يحلّ محله. */
    private fun redFlagNotice(qNorm: String): String? =
        if (redFlagPhrases.any { qNorm.contains(it) })
            "🚨 ما ذكرته قد يدل على حالة طبية طارئة. لا تعتمد على الأعشاب هنا — توجّه لأقرب طوارئ أو اتصل بالإسعاف فوراً."
        else null


    // ── فهرس "مفهوم تلقائياً" من نصوص الموسوعة نفسها ────────────────────

    /**
     * فهرس يُبنى تلقائياً من نصوص الموسوعة نفسها (لا يحتاج أي تدخل يدوي)
     * ليمنح سيمو فهماً أعمق من مجرد تقاطع كلمات بسيط:
     *
     * 1) وزن كل كلمة (IDF تقريبي): كلمة نادرة الورود عبر كل نقاط الموسوعة
     *    (اسم عرَض أو استخدام مميّز) تعني أكثر من كلمة شائعة جداً (كـ"يساعد")،
     *    فتُعطى وزناً أعلى عند حساب التشابه بين سؤال المستخدم ونص الموسوعة.
     * 2) علاقات ضمنية بين الكلمات: كلمتان تتكرران معاً كثيراً نسبياً داخل
     *    نفس النقطة عبر أعشاب مختلفة تُعتبر "مرتبطتين" تلقائياً (نسخة مبسّطة
     *    من مقياس PMI)، فيتوسّع فهم سؤال المستخدم بها دون أي مرادف يُضاف
     *    يدوياً من المطوّر — تعلّم توزيعي بحت من بيانات الموسوعة ذاتها.
     *
     * يُعاد بناؤه فقط عند تغيّر قائمة الأعشاب أو الخلطات المرجعة (انظر
     * [corpusIndexFor]).
     *
     * يُبنى الآن من قائمة نصوص خام مسطّحة ([fieldTexts]) بدل قائمة أعشاب
     * مباشرة، بحيث يمكن تغذيته بنصوص الأعشاب *والخلطات* معاً — وهذا هو ما
     * يجعل "فهم" سيمو (أوزان الكلمات وعلاقاتها الضمنية) يشمل كل الموسوعة
     * فعلياً بدل الاقتصار على الأعشاب وحدها.
     */
    private class CorpusIndex(fieldTexts: List<String>) {
        private val idf: Map<String, Double>
        private val formIdf: Map<String, Double>
        private val related: Map<String, List<String>>

        init {
            val points = mutableListOf<Set<String>>()
            fieldTexts.forEach { field ->
                splitPoints(field).forEach { p ->
                    val w = pointInfo(p).words
                    if (w.isNotEmpty()) points += w
                }
            }
            val docCount = points.size.coerceAtLeast(1)
            val df = mutableMapOf<String, Int>()
            points.forEach { pts -> pts.forEach { w -> df[w] = (df[w] ?: 0) + 1 } }
            idf = df.mapValues { (_, d) -> ln(docCount.toDouble() / d.toDouble() + 1.0) }

            // وزن كل "شكل" (بلا ال/واو/ضمير...) = أعلى وزن لكلمة موسوعة تنتج هذا الشكل،
            // كي تُعرَف كلمة سؤال مثل "للنوم" أنها موجودة بالموسوعة كـ"النوم".
            val fi = HashMap<String, Double>(idf.size * 3)
            for ((w, v) in idf) {
                for (f in ArabicLexicon.formsOf(w)) {
                    val cur = fi[f]
                    if (cur == null || v > cur) fi[f] = v
                }
            }
            formIdf = fi

            val coOccur = mutableMapOf<Pair<String, String>, Int>()
            points.forEach { pts ->
                val list = pts.toList()
                for (i in list.indices) for (j in i + 1 until list.size) {
                    val a = list[i]; val b = list[j]
                    val key = if (a < b) a to b else b to a
                    coOccur[key] = (coOccur[key] ?: 0) + 1
                }
            }
            val relatedMap = mutableMapOf<String, MutableList<Pair<String, Double>>>()
            coOccur.forEach { (pair, count) ->
                if (count < 2) return@forEach // تحوّطاً من تطابق عابر لمرة واحدة
                val (a, b) = pair
                val score = count.toDouble() / sqrt((df[a] ?: 1).toDouble() * (df[b] ?: 1).toDouble())
                if (score >= 0.5) {
                    relatedMap.getOrPut(a) { mutableListOf() } += b to score
                    relatedMap.getOrPut(b) { mutableListOf() } += a to score
                }
            }
            related = relatedMap.mapValues { (_, l) -> l.sortedByDescending { it.second }.take(3).map { it.first } }
        }

        fun weightOf(word: String): Double = idf[word] ?: 1.0

        fun isKnown(word: String): Boolean = idf.containsKey(word) || formIdf.containsKey(word)

        /** يوسّع كلمات السؤال بالعلاقات المكتشَفة تلقائياً (إضافة فهم ضمني، لا حذف). */
        fun expand(words: Set<String>): Set<String> =
            if (words.isEmpty()) words else words + words.flatMap { related[it].orEmpty() }
    }

    // ── تدريب سيمو الافتتاحي: أكثر من 500 صياغة محادثة جاهزة ────────────

    /**
     * "تدريب سيمو الافتتاحي" — أكثر من 500 صياغة عربية/عامية مختلفة (تحية،
     * شكر، وداع، سؤال عن الحال، هوية سيمو، قدراته، اعتذار، مجاملة، ورد
     * بسيط بنعم/لا) يتعرّف عليها سيمو مباشرة فور تثبيت التطبيق، دون أي حاجة
     * لبحث حر في الموسوعة ودون أي تدخل من المطوّر بعد التثبيت — هذا هو
     * "تدريبه على أكثر من 500 سؤال" كما طُلب، منفصل تماماً عن
     * [AiConfig.trainedExamples] (تدريب المطوّر اليدوي القابل للتعديل من
     * أدوات المطور) و[AiConfig.autoLearnedExamples] (تعلّمه الذاتي من
     * تقييمات المستخدمين). الصياغات تُبنى من حاصل ضرب (كلمات أساسية ×
     * لواحق شائعة) بدل كتابة كل صياغة يدوياً، فتغطّي مئات الاحتمالات
     * الواقعية التي يكتبها المستخدم فعلاً بأقل قدر من التكرار في الكود.
     */
    private object ConversationalSeed {
        private data class Category(
            val words: List<String>,
            val suffixes: List<String>,
            val responses: List<String>,
            /** مطابقة تامة فقط (بلا startsWith) — للعبارات القصيرة جداً القابلة للالتباس مثل "لا". */
            val exactOnly: Boolean = false
        )

        private val greeting = Category(
            words = listOf(
                "مرحبا", "مرحباً", "مرحبتين", "هلا", "هلا بيك", "هلا فيك", "أهلا", "اهلا",
                "أهلا وسهلا", "اهلا وسهلا", "اهلين", "يا هلا", "صباح الخير", "صباح النور",
                "مساء الخير", "مساء النور", "السلام عليكم", "وعليكم السلام", "هاي", "هالو",
                "hello", "hi", "hey", "يا سيمو",
                // إضافة: صياغات مصرية/مغاربية شائعة لم تكن مغطاة سابقاً.
                "ازيك", "ازيك يا سيمو", "عامل ايه", "عاملة ايه", "ايه الأخبار",
                "أهلا بيك", "أهلا وسهلا بيك", "سلامو عليكم", "واش راك", "لاباس"
            ),
            suffixes = listOf("", " سيمو", " يا سيمو", " كيف الحال", "؟", " فيك خير", " يا صديقي"),
            responses = listOf(
                "أهلاً 👋 أنا سيمو، مساعدك الذكي في عالم الأعشاب. اسألني عن أي عشبة تريدها: فوائدها، طريقة استخدامها، تحذيراتها، أو اطلب مني مقارنة بين أكثر من عشبة، وسأجيبك فوراً من بيانات الموسوعة.",
                "هلا فيك 🌿 سيمو حاضر، جاهز أساعدك بأي سؤال عن الأعشاب في الموسوعة."
            )
        )

        private val thanks = Category(
            words = listOf(
                "شكرا", "شكراً", "شكرا الك", "شكرا كتير", "يعطيك العافية", "يعطيك الف عافية",
                "تسلم", "تسلم ايدك", "تسلمي", "الله يعطيك العافية", "مشكور", "مشكورة",
                "ممنون", "ممنونة", "جزاك الله خير", "تسلملي", "الله يخليك", "يسلمو"
            ),
            suffixes = listOf("", " سيمو", " كتير", " جدا", " يا سيمو", "!"),
            responses = listOf(
                "عفواً 🌿 أنا سيمو، دائماً هنا لأي سؤال آخر عن الأعشاب.",
                "العفو 🌱 سعيد إني قدرت أساعدك، تحت أمرك بأي وقت."
            )
        )

        private val farewell = Category(
            words = listOf(
                "مع السلامة", "باي", "وداعا", "الى اللقاء", "إلى اللقاء", "تصبح على خير",
                "تصبحين على خير", "نهارك سعيد", "بشوفك", "نلتقي لاحقا", "سلام", "bye",
                "يلا باي", "يالله باي", "الله معك", "بالتوفيق"
            ),
            suffixes = listOf("", " سيمو", " يا سيمو", "!"),
            responses = listOf(
                "مع السلامة 🌿 ارجع أي وقت تحتاج فيه سيمو لسؤال عن الأعشاب.",
                "إلى اللقاء 🌱 سيمو موجود دائماً هنا لما تحتاجني."
            )
        )

        private val wellbeing = Category(
            words = listOf(
                "كيفك", "كيف حالك", "شلونك", "شو اخبارك", "اخبارك ايه",
                "كيف الصحة", "كيفك اليوم", "انت منيح", "شو مسوي", "ايش الأخبار", "شخبارك",
                // إضافة: مزيد من صياغات الحال العامية (مصري/مغاربي).
                "ازيك يا سيمو عامل ايه", "احوالك ايه", "كيف داير", "واش نتا لاباس",
                "كيفك انت", "شخبار سيمو"
            ),
            suffixes = listOf("", " سيمو", " يا سيمو", "؟"),
            responses = listOf(
                "الحمد لله تمام 🌿 أنا سيمو وجاهز دائماً، كيف أقدر أساعدك اليوم بموضوع الأعشاب؟",
                "بخير وجاهز أساعدك 🌱 شو الموضوع اللي حابب تسألني عنه؟"
            )
        )

        private val identity = Category(
            words = listOf(
                "من انت", "مين انت", "شو اسمك", "ما اسمك", "انت مين", "عرفني فيك",
                "احكيلي عنك", "مين سيمو", "who are you", "what is your name",
                // إضافة: مزيد من صياغات التعريف بالهوية.
                "عرفني بنفسك", "انت مين بالظبط", "وش انت", "ايش انت", "انت شو"
            ),
            suffixes = listOf("", "؟", " سيمو"),
            responses = listOf(
                "أنا سيمو 🌿، المساعد الذكي لموسوعة الأعشاب. أعمل بالكامل على جهازك دون إنترنت، وأجيبك من بيانات الأعشاب الموجودة في الموسوعة عن الفوائد والاستخدام والتحذيرات."
            )
        )

        private val capability = Category(
            words = listOf(
                "شو تقدر تعمل", "ماذا تستطيع ان تفعل", "ايش بتعرف تعمل", "شو بتعرف",
                "ما هي قدراتك", "كيف تساعدني", "بماذا تساعدني", "وش تسوي",
                "ماذا تفعل", "what can you do"
            ),
            suffixes = listOf("", "؟", " سيمو"),
            responses = listOf(
                "أقدر أجاوبك عن فوائد أي عشبة في الموسوعة، طريقة استخدامها، تحذيراتها وأضرارها، وأقدر كمان أقارن بين أكثر من عشبة إذا طلبت ذلك صراحة — جرّب اسألني عن اسم عشبة مباشرة."
            )
        )

        private val apology = Category(
            words = listOf(
                "اسف", "آسف", "اسفة", "آسفة", "معذرة", "سامحني", "عفوا", "سوري", "sorry",
                "معلش", "معليش"
            ),
            suffixes = listOf("", " سيمو", "!"),
            responses = listOf("ولا يهمك 🌿 لا داعي للاعتذار، خبرني كيف أقدر أساعدك.")
        )

        private val compliment = Category(
            words = listOf(
                "برافو", "احسنت", "رائع", "ممتاز", "تمام", "حلو", "perfect", "nice",
                "great job", "كفو", "روعة", "جميل"
            ),
            suffixes = listOf("", " سيمو", "!"),
            responses = listOf("شكراً لكلامك الطيب 🌿 سعيد إني أفدتك، تحت أمرك بأي سؤال ثاني.")
        )

        private val smalltalk = Category(
            words = listOf("نعم", "ايوة", "أيوة", "تمام", "اوك", "ok", "لا", "لأ", "مافي شي", "خلاص"),
            suffixes = listOf(""),
            responses = listOf("تمام 🌿 خبرني إذا حابب تسأل عن عشبة معيّنة أو أي موضوع بالموسوعة."),
            exactOnly = true
        )

        private val all = listOf(
            greeting, thanks, farewell, wellbeing, identity, capability, apology, compliment, smalltalk
        )

        /** صياغة واحدة مسطّحة: النص، ردّه، وهل مطابقتها تامة فقط (بلا استثناء). */
        private data class Flat(val phrase: String, val response: String, val exactOnly: Boolean, val norm: String)

        /** كل الصياغات مسطّحة، تُبنى مرة واحدة فقط عند أول استخدام. */
        private val flattened: List<Flat> by lazy {
            val out = mutableListOf<Flat>()
            all.forEach { cat ->
                cat.words.forEach { w ->
                    cat.suffixes.forEach { s ->
                        val phrase = (w + s).trim()
                        if (phrase.isNotBlank()) {
                            val idx = phrase.hashCode().let { if (it < 0) -it else it } % cat.responses.size
                            out += Flat(phrase, cat.responses[idx], cat.exactOnly, normalize(phrase))
                        }
                    }
                }
            }
            out
        }

        /**
         * يطابق سؤال المستخدم (بعد التطبيع) بأطول صياغة مدرَّبة مطابقة تماماً،
         * أو تكون الصياغة بداية للسؤال مع فارق طفيف جداً بعدها (٣ أحرف كحد
         * أقصى، لالتقاط علامات ترقيم أو مسافات) — بحيث لا تُخطف أسئلة حقيقية
         * عن الأعشاب تحتوي بالصدفة على جزء من عبارة تحية قصيرة. الصياغات
         * القصيرة الملتبسة (مثل "لا") تتطلب مطابقة تامة فقط.
         */
        fun match(question: String): String? {
            val qNorm = normalize(question)
            if (qNorm.isBlank()) return null
            var best: Flat? = null
            var bestLen = -1
            for (flat in flattened) {
                // كانت normalize(flat.phrase) تُعاد لكل صياغة (~1500) مع كل رسالة، وتُنشئ
                // 3 كائنات Regex في كل مرة — الآن مُخزَّنة مسبقاً. كذلك تُشترط حدّ كلمة
                // بعد الصياغة (كان "اسف" يخطف كلمة "اسفل").
                val pNorm = flat.norm
                if (pNorm.isEmpty()) continue
                val isMatch = qNorm == pNorm ||
                    (!flat.exactOnly && qNorm.startsWith(pNorm) && qNorm.length - pNorm.length <= 3 &&
                        qNorm[pNorm.length] == ' ')
                if (isMatch && pNorm.length > bestLen) {
                    best = flat
                    bestLen = pNorm.length
                }
            }
            return best?.response
        }
    }

    // ── مجموعات مرادفات مواضيع صحية شائعة (مشكلة "التنحيف") ─────────────

    /**
     * مشكلة حقيقية أُبلغ عنها: سؤال عن "التنحيف" (أو اقتراح عشبة له) كان لا
     * يُطابق أي عشبة تقريباً، رغم وجود بيانات فعلية في الموسوعة تتحدث عن
     * الموضوع نفسه بكلمات أخرى (إنقاص الوزن، حرق الدهون، الرجيم، السمنة...).
     * السبب: لا [ArabicLexicon] (تجذير لغوي بحت، لا علاقة صرفية بين
     * "تنحيف" و"دهون" مثلاً) ولا [DictionaryLexicon] العام (قاموس لغوي
     * عام، لا يربط بالضرورة بين كل المرادفات العامية/الصحية الشائعة لموضوع
     * بعينه) يغطّيان هذا النوع من "الترادف الموضوعي" (كلمات مختلفة الجذر
     * تماماً لكنها تدل على نفس الموضوع الصحي عملياً).
     *
     * هذا الحل مخصَّص: مجموعات كلمات يدوية لكل موضوع، أي كلمة في المجموعة
     * تُوسَّع لتشمل *كل* كلمات مجموعتها عند التحليل — تماماً كمرادفات
     * [AiConfig.synonyms] لكن مبنية داخل الكود كتغطية أساسية جاهزة فور
     * التثبيت، بلا حاجة لأي إعداد يدوي من المطوّر. يمكن إضافة مجموعات
     * جديدة لأي موضوع آخر يتكرر فشل مطابقته لاحقاً بنفس الطريقة.
     *
     * مشكلة ثانية أُبلغ عنها لاحقاً وأصلحتها هذه النسخة: سؤال عن عشبة
     * "لتخفيف الألم" كان يعيد نتائج عن *إنقاص الوزن* بدل الألم! السبب:
     * كلمة "تخفيف" وحدها كانت ضمن مجموعة التنحيف أعلاه (بقصد تغطية "تخفيف
     * الوزن")، لكنها كلمة عامة جداً تُستخدم في عشرات السياقات الأخرى
     * (تخفيف الألم، تخفيف التوتر...)؛ فبمجرد ورودها في أي سؤال — أياً كان
     * موضوعه — كانت تُوسَّع تلقائياً لتشمل كل كلمات مجموعة الوزن (دهون،
     * سمنة، رجيم...) فتُغرق نتيجة البحث الحقيقية بنتائج تنحيف لا علاقة لها
     * بالسؤال. نفس الخطر كان قائماً مع "حرق" (يتقاطع مع "حرقة" المعدة بعد
     * التجذير). الحل: إبقاء كلمات كل مجموعة مقصورة على ألفاظ خاصة فعلاً
     * بموضوعها (لا أفعال/كلمات عامة تتقاطع مع مواضيع أخرى)، مع إضافة
     * مجموعة مخصّصة لموضوع "الألم" نفسه بدل الاعتماد على كلمة عامة ناقصة.
     *
     * إلى جانب الوزن والألم، تغطّي المجموعات أدناه الآن أكثر المواضيع
     * الصحية شيوعاً في أسئلة اقتراح عشبة (نوم، هضم، مناعة/تنفس، توتر، ضغط/
     * قلب/سكري، كبد/كلى، بشرة/شعر، دورة شهرية) بنفس المبدأ بالضبط: ألفاظ
     * محدَّدة غير عامة لكل موضوع، لا أفعال/كلمات فضفاضة قد تتقاطع مع مواضيع
     * أخرى. هذا لا يضمن تغطية كل سؤال ممكن (يعتمد فعلياً على ما تحويه نصوص
     * الموسوعة نفسها)، لكنه يوسّع فعلياً نطاق "الفهم الموضوعي" الذي كان
     * مقتصراً على التنحيف فقط.
     */
    private object HealthTopicSynonyms {
        // كل مجموعة الآن معنونة (label ← كلماتها) بدل مجموعة كلمات مجهولة —
        // العنوان يُستخدم لاحقاً في [buildSuggestionAnswer] ليصرّح سيمو
        // صراحةً بالموضوع الذي "فهمه" من السؤال (مثل "إنقاص الوزن") بدل
        // الاكتفاء بعرض نتائج مطابقة بلا أي إشارة لفهم الهدف الفعلي.
        private val rawClusters: List<Pair<String, Set<String>>> = listOf(
            // التنحيف/إنقاص الوزن — المجموعة التي عالجت المشكلة المُبلَغ عنها
            // أولاً. "تخفيف" و"حرق" أُزيلتا عمداً (عامّتان جداً، انظر التوثيق
            // أعلاه)؛ "دهون/سمنة/الوزن..." كافية وحدها لتفعيل الموضوع.
            "إنقاص الوزن" to setOf(
                "تنحيف", "تخسيس", "رجيم", "دايت", "حمية", "انقاص", "إنقاص",
                "الوزن", "وزن", "دهون", "الدهون", "سمنة", "السمنة", "نحافة",
                "خسارة", "كرش", "الكرش"
            ),
            // الألم/الوجع — المجموعة التي عالجت مشكلة "تخفيف الألم" المُبلَغ
            // عنها (كانت تُخطف بمجموعة الوزن أعلاه بسبب كلمة "تخفيف" العامة).
            "تخفيف الألم" to setOf(
                "الم", "ألم", "الألم", "اوجاع", "أوجاع", "الأوجاع", "وجع", "الوجع",
                "وجعة", "مغص", "تشنج", "تشنجات", "مسكن", "مسكنات", "تسكين",
                "يسكن", "صداع", "الصداع", "المي", "وجعي", "اوجاعي"
            ),
            // النوم/الأرق.
            "تحسين النوم" to setOf(
                "نوم", "النوم", "أرق", "ارق", "الأرق", "سهر", "السهر", "مهدئ",
                "مهدئات", "منوم", "منومات"
            ),
            // الهضم والجهاز الهضمي.
            "الهضم" to setOf(
                "هضم", "الهضم", "غازات", "الغازات", "انتفاخ", "الانتفاخ",
                "قولون", "القولون", "امساك", "الامساك", "إمساك", "اسهال",
                "الاسهال", "إسهال", "غثيان", "الغثيان", "قيء", "القيء", "استفراغ"
            ),
            // المناعة ونزلات البرد والجهاز التنفسي.
            "تقوية المناعة" to setOf(
                "مناعة", "المناعة", "برد", "البرد", "زكام", "الزكام",
                "انفلونزا", "الانفلونزا", "رشح", "الرشح", "سعال", "كحة", "الكحة",
                "ربو", "الربو", "تنفس", "التنفس", "بلغم", "البلغم", "احتقان", "الاحتقان"
            ),
            // التوتر والقلق.
            "تهدئة التوتر والقلق" to setOf(
                "توتر", "التوتر", "قلق", "القلق", "عصبية", "العصبية",
                "اعصاب", "الأعصاب", "استرخاء"
            ),
            // ضغط الدم والقلب والسكري.
            "ضغط الدم والقلب والسكري" to setOf(
                "ضغط", "الضغط", "قلب", "القلب", "كولسترول", "الكولسترول",
                "سكري", "السكري", "سكر", "السكر", "شرايين", "الشرايين"
            ),
            // الكبد والكلى وتنقية الجسم.
            "الكبد والكلى" to setOf(
                "كبد", "الكبد", "كلى", "الكلى", "كلية", "الكلية", "سموم",
                "السموم", "تنقية", "تسمم"
            ),
            // البشرة والشعر.
            "البشرة والشعر" to setOf(
                "بشرة", "البشرة", "جلد", "الجلد", "حبوب", "الحبوب", "اكزيما",
                "الاكزيما", "حكة", "الحكة", "شعر", "الشعر", "تساقط",
                "قشرة", "القشرة"
            ),
            // الدورة الشهرية والصحة الإنجابية.
            "الدورة الشهرية" to setOf(
                "طمث", "الطمث", "حيض", "الحيض", "رحم", "الرحم",
                "خصوبة", "الخصوبة", "هرمونات", "الهرمونات"
            )
        )

        private val clusters: List<Pair<String, Set<String>>> by lazy {
            rawClusters.map { (label, words) -> label to words.map { normalize(it) }.toSet() }
        }

        private val lookup: Map<String, Set<String>> by lazy {
            val map = mutableMapOf<String, MutableSet<String>>()
            clusters.forEach { (_, cluster) -> cluster.forEach { word -> map.getOrPut(word) { mutableSetOf() }.addAll(cluster) } }
            map
        }

        /** كل كلمات كل المجموعات مسطّحة — تُستخدم لتفعيل نية سؤال محدَّد (مثل فروع فائدة/اقتراح). */
        val allWords: List<String> by lazy { clusters.flatMap { it.second } }

        /** يوسّع كلمات السؤال بمرادفات مجموعتها الموضوعية إن وُجدت (إضافة فهم، لا حذف). */
        fun expand(words: Set<String>): Set<String> =
            if (words.isEmpty()) words else words + words.flatMap { lookup[it].orEmpty() }

        /** يعيد مجموعة الموضوع كاملة إن ذكر نص السؤال (المُطبَّع) أي كلمة منها، وإلا null. */
        /**
         * إصلاح خلل حقيقي أُبلغ عنه ("سؤال فوائد عام بيرجع نص مقصوص"): كانت
         * المطابقة هنا بـ qNorm.contains(word) — أي تطابق جزء من كلمة، لا
         * كلمة كاملة مستقلة. كلمة "الم" (من "ألم" بعد تطبيع الهمزة) موجودة
         * حرفياً كجزء من عشرات الكلمات العربية الشائعة جداً التي تبدأ بـ"ال"
         * التعريف متبوعة بحرف ميم — مثل "الماء"، "المعتاد"، "الملعقة"،
         * "المفيد" — فكان أي ذكر عرضي لكلمة كهذه بالسؤال يُفعِّل موضوع
         * "تخفيف الألم" خطأً، فيُقصر رد الفوائد على النقاط "المرتبطة" بهذا
         * الموضوع الوهمي بدل عرض كل الفوائد المسجّلة. الإصلاح: تقسيم السؤال
         * إلى كلمات مستقلة أولاً (كما تفعل [wordsOf] وغيرها في الملف) ثم
         * مطابقة كل كلمة *كاملة* مع كلمات المجموعة، لا كسلسلة فرعية.
         */
        private fun wordTokensOf(qNorm: String): Set<String> {
            val toks = qNorm.split(SPACES).filter { it.isNotBlank() }
            val out = LinkedHashSet<String>(toks)
            // أشكال الكلمة بلا سابقة/لاحقة: يلتقط "للنوم"/"بالنوم"/"ألمي" كموضوع نوم/ألم
            // (كانت المطابقة حرفية تامة فقط فيفشل أي سؤال فيه سابقة مثل "عشبة للنوم").
            for (t in toks) out.addAll(ArabicLexicon.formsOf(t))
            return out
        }

        fun clusterMentionedIn(qNorm: String): Set<String>? {
            val qWords = wordTokensOf(qNorm)
            if (qWords.isEmpty()) return null
            return clusters.firstOrNull { (_, cluster) -> cluster.any { it in qWords } }?.second
        }

        /**
         * عنوان الموضوع الصحي (بالعربية الفصحى، جاهز للعرض مباشرة في رد
         * سيمو) إن ذكر السؤال أي كلمة من مجموعته، وإلا null. يُستخدم في
         * [buildSuggestionAnswer] ليصرّح سيمو بالهدف الذي فهمه من مرادفات
         * السؤال (مثل "تنحيف" أو "دايت" أو "كرش" ← "إنقاص الوزن") بدل رد
         * عام لا يوضّح أنه فهم المرادف فعلاً.
         */
        fun labelMentionedIn(qNorm: String): String? {
            val qWords = wordTokensOf(qNorm)
            if (qWords.isEmpty()) return null
            return clusters.firstOrNull { (_, cluster) -> cluster.any { it in qWords } }?.first
        }
    }

    /** اسم فئة العشبة (إن وُجدت) عبر [HerbCategoryLookup] — نص فارغ إن لم تُحمَّل الفئات بعد أو لم يكن للعشبة معرّف فئة. */
    private fun categoryOf(herb: Herb): String = herb.categoryId?.let { HerbCategoryLookup.categoryNames[it] } ?: ""

    /**
     * ذاكرة تخزين مؤقت لآخر فهرسين مبنيَّين (بدل فهرس واحد فقط سابقاً): سيمو
     * يُستدعى فعلياً بنمطين مختلفين من نفس قائمة الأعشاب الكاملة ضمن نفس
     * جلسة الدردشة — أحياناً بلا خلطات ([buildSuggestionAnswer]) وأحياناً
     * معها ([buildGeneralSearchAnswer]) — وبخانة واحدة فقط كان كل تبديل بين
     * النمطين عبر رسالتين متتاليتين يُلغي الفهرس المبني للتو ويعيد بناءه من
     * الصفر (كل نصوص كل الأعشاب) حتى لو لم تتغيّر بيانات الموسوعة إطلاقاً —
     * إهدار حقيقي للمعالجة والذاكرة المؤقتة (Garbage) على كل رسالة. خانتان
     * (LRU بسيط) تكفيان لتغطية النمطين معاً فيبقى كلاهما جاهزاً دون إعادة
     * بناء متكررة، بذاكرة إضافية محدودة جداً (فهرس واحد إضافي فقط، لا أكثر).
     */
    private const val corpusIndexCacheCapacity = 2
    private val corpusIndexCache = object : LinkedHashMap<Pair<List<Herb>, List<Blend>>, CorpusIndex>(
        corpusIndexCacheCapacity, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<Pair<List<Herb>, List<Blend>>, CorpusIndex>): Boolean =
            size > corpusIndexCacheCapacity
    }

    /**
     * [blends] اختيارية (افتراضياً فارغة) حتى تبقى بقية الاستخدامات الحالية
     * (المقارنة بين أعشاب محدَّدة، اقتراح عشبة) كما هي بلا أي تغيير — فهرس
     * مبني من الأعشاب فقط، وهو السياق الصحيح لها. يُمرَّر [blends] فقط من
     * البحث الحر العام ([buildGeneralSearchAnswer]) حيث "كل الموسوعة" تشمل
     * الخلطات أيضاً. المطابقة بمرجع القائمة (لا محتواها) مقصودة: شاشات
     * التطبيق تُنشئ قائمة جديدة فقط عند تحديث فعلي للبيانات، فمرجع واحد
     * لنفس القائمة يعني بيانات لم تتغيّر إطلاقاً — نفس منطق الكاش السابق
     * قبل هذا التعديل، لكن الآن بخانتين بدل خانة واحدة.
     */
    private fun corpusIndexFor(herbs: List<Herb>, blends: List<Blend> = emptyList()): CorpusIndex {
        val key = herbs to blends
        corpusIndexCache.entries.forEach { (k, v) ->
            if (k.first === herbs && k.second === blends) return v
        }
        // فئة العشبة (اسمها عبر [categoryOf]) تُضاف الآن لنصوص الفهرسة، فيتوسّع
        // "إدراك" سيمو للموسوعة ليشمل تصنيفاتها لا الحقول الستة فقط — انظر
        // توثيق [HerbCategoryLookup].
        val herbTexts = herbs.flatMap { listOf(it.name, it.benefits, it.usage, it.warnings, it.harms, it.notes, categoryOf(it)) }
        val blendTexts = blends.flatMap { listOf(it.name, it.benefits, it.usage, it.warnings, it.notes) }
        val built = CorpusIndex(herbTexts + blendTexts)
        corpusIndexCache[key] = built
        return built
    }

    /** تشابه Jaccard موزون بأهمية الكلمات (IDF) بدل عدّها بالتساوي. */
    private fun weightedSimilarity(index: CorpusIndex, a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val union = a union b
        val unionWeight = union.sumOf { index.weightOf(it) }
        if (unionWeight == 0.0) return 0.0
        val interWeight = (a intersect b).sumOf { index.weightOf(it) }
        return interWeight / unionWeight
    }

    /**
     * يقارن سؤال المستخدم بكل "الحالات المدرَّبة" — اليدوية أولاً
     * ([AiConfig.trainedExamples]، أولوية مطلقة دوماً لأنها من مراجعة
     * المطوّر مباشرة)، ثم المتعلَّمة ذاتياً ([AiConfig.autoLearnedExamples])
     * بعتبة أعلى قليلاً تحوّطاً لأنها غير مراجَعة يدوياً. يعيد أقرب رد
     * مخصّص إن تجاوز التشابه العتبة المناسبة، وإلا يعيد null ليكمل سيمو
     * بمنطقه العام.
     *
     * المطابقة تستخدم الآن [richWordsOf] (تجذير + قاموس مرادفات) بدل كلمات
     * الجملة الحرفية فقط — سابقاً كانت صياغة مرادفة تماماً لسؤال مدرَّب
     * (مثل "شو ينفع الزنجبيل" بدل "ما فوائد الزنجبيل") قد لا تُطابق الحالة
     * المدرَّبة إطلاقاً رغم تطابق المعنى، لأن [jaccard] كان يقارن الكلمات
     * الحرفية فقط دون أي فهم للمرادفات — وهذا بالضبط ما يجعل "تعلّم سيمو"
     * الذاتي من التقييمات مفيداً فعلياً لصياغات لاحقة مشابهة معنوياً لا
     * حرفياً فقط.
     */
    private fun matchTrainedExample(question: String, allowAuto: Boolean = true): String? {
        val qWords = richWordsOf(question)
        if (qWords.isEmpty()) return null

        var bestManual: String? = null
        var bestManualScore = 0.0
        exampleWords(AiConfig.trainedExamples).forEach { (example, words) ->
            val score = jaccard(qWords, words)
            if (score > bestManualScore) { bestManualScore = score; bestManual = example.response }
        }
        if (bestManualScore >= AiConfig.trainedMatchThreshold) return bestManual

        if (!allowAuto || !AiConfig.autoLearnEnabled || AiConfig.autoLearnedExamples.isEmpty()) return null
        var bestAuto: String? = null
        var bestAutoScore = 0.0
        exampleWords(AiConfig.autoLearnedExamples).forEach { (example, words) ->
            val score = jaccard(qWords, words)
            if (score > bestAutoScore) { bestAutoScore = score; bestAuto = example.response }
        }
        val autoThreshold = (AiConfig.trainedMatchThreshold + 0.15).coerceAtMost(0.95)
        return if (bestAutoScore >= autoThreshold) bestAuto else null
    }

    // ذاكرتا تخزين مؤقت لكلمات أنماط الحالات المدرَّبة (يدوياً/ذاتياً) بعد
    // توسيعها بـ[richWordsOf] — إعادة استعلام قاموس المرادفات (قاعدة
    // SQLite) لكل الأنماط في كل رسالة دردشة مكلفة بلا داعٍ ما دامت قائمة
    // الأنماط نفسها لم تتغيّر؛ بنفس أسلوب [corpusIndexFor] (تحقّق من مرجع
    // القائمة نفسها، لا محتواها، لأن كل شاشة تُنشئ قائمة جديدة عند أي تعديل
    // فعلي من أدوات المطور أو التعلّم الذاتي).
    private var cachedTrainedRef: List<TrainedExample>? = null
    private var cachedTrainedWords: List<Pair<TrainedExample, Set<String>>> = emptyList()
    private var cachedAutoRef: List<TrainedExample>? = null
    private var cachedAutoWords: List<Pair<TrainedExample, Set<String>>> = emptyList()

    private fun exampleWords(examples: List<TrainedExample>): List<Pair<TrainedExample, Set<String>>> {
        val isTrained = examples === AiConfig.trainedExamples
        if (isTrained) {
            if (cachedTrainedRef === examples) return cachedTrainedWords
            val built = examples.map { it to richWordsOf(it.pattern) }
            cachedTrainedRef = examples
            cachedTrainedWords = built
            return built
        }
        if (cachedAutoRef === examples) return cachedAutoWords
        val built = examples.map { it to richWordsOf(it.pattern) }
        cachedAutoRef = examples
        cachedAutoWords = built
        return built
    }

    /** أقصى عدد حالات يحتفظ بها التعلّم الذاتي؛ الأقدم يُستبعد أولاً عند التجاوز. */
    private const val MAX_AUTO_LEARNED_EXAMPLES = 200

    /**
     * يسجّل تقييم المستخدم (👍/👎) على إجابة "قابلة للتعلّم" (انظر
     * [AssistantReply.learnable]) في شاشة الدردشة:
     * - عند الإعجاب: تُحفظ نقطة السؤال↔الرد كحالة يتعلّمها سيمو تلقائياً،
     *   بشرط ألا تكون قريبة بما يكفي من حالة متعلَّمة سابقاً (تفادي التكرار)،
     *   مع سقف أقصى لعدد الحالات ([MAX_AUTO_LEARNED_EXAMPLES]) يُستبعد عنده
     *   الأقدم أولاً.
     * - عند عدم الإعجاب: يُزال أي مثال متعلَّم ذاتياً يطابق هذا السؤال بدرجة
     *   كافية — أي أن سيمو "يتراجع" عن خطأ تعلّمه بنفسه — دون أي تأثير على
     *   حالات تدريب المطوّر اليدوية، المحمية دوماً من هذا المسار.
     * فحص "التكرار" هنا يستخدم [richWordsOf] أيضاً (بدل الكلمات الحرفية)
     * حتى يتّسق مع [matchTrainedExample] تماماً: لا يُحفَظ سؤال كحالة جديدة
     * إن كانت هناك حالة مرادفة معنوياً محفوظة أصلاً (لا حرفياً مطابقة فقط)،
     * فلا تتكدّس نسخ شبه مكرَّرة من نفس المعنى بصياغات مختلفة.
     * يعيد القائمة المحدَّثة مباشرة ليحفظها المستدعي (PreferencesRepository).
     */
    fun recordFeedback(question: String, reply: String, helpful: Boolean): List<TrainedExample> {
        val qWords = richWordsOf(question)
        if (qWords.isEmpty()) return AiConfig.autoLearnedExamples

        if (!helpful) {
            val remaining = AiConfig.autoLearnedExamples.filterNot {
                jaccard(qWords, richWordsOf(it.pattern)) >= AiConfig.trainedMatchThreshold
            }
            AiConfig.autoLearnedExamples = remaining
            return remaining
        }

        return AiConfig.autoLearnedExamples
    }

    // ── مزامنة التعلّم الذاتي بين الأجهزة (انظر SemoLearningRepository) ──
    // كل ما سبق ([recordFeedback] وقائمة [AiConfig.autoLearnedExamples])
    // كان يبقى محصوراً محلياً على DataStore الخاص بكل جهاز (انظر
    // PreferencesRepository) — إن تعلّم سيمو شيئاً من مستخدم على جهاز، لا
    // يستفيد منه مستخدم آخر على جهاز مختلف إطلاقاً، ويُعاد "تعليمه" نفس
    // الشيء من الصفر لو صادف نفس السؤال لاحقاً. الدالتان أدناه هما نقطة
    // الوصل بين هذا التعلّم المحلي وطبقة المزامنة الشبكية (Firestore):
    // [learningKey] يبني معرّفاً مستقراً يُستخدم مُعرِّف مستند في Firestore،
    // و[mergeLearnedExamples] يدمج ما وصل من الأجهزة الأخرى ضمن القائمة
    // المحلية بأمان (بلا تكرار دلالي).

    /**
     * معرّف ثابت (بصمة SHA-256) لسؤال معيّن، يُستخدم مُعرِّف مستند التعلّم
     * المشترك في Firestore بدل مُعرِّف عشوائي، حتى تتّحد حالتان بنفس المعنى
     * الدلالي (لا نفس الحروف بالضبط) من جهازين مختلفين تحت نفس المستند —
     * فيُصوَّت للحالة القيّمة فعلاً بدل تكديس نسخ شبه مكرَّرة منها بصياغات
     * مختلفة. يعتمد على نفس التوسيع الدلالي المستخدم في فحص التكرار بـ
     * [recordFeedback] ([richWordsOf])، مع ترتيب الكلمات أبجدياً كي لا
     * يغيّر ترتيب كتابتها في السؤال الأصلي المعرّف الناتج.
     */
    fun learningKey(question: String): String {
        val words = richWordsOf(question).sorted().joinToString("|")
        val source = words.ifEmpty { normalize(question) }
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * يدمج حالات تعلّم واردة من مصدر آخر (النسخة المشتركة من أجهزة أخرى عبر
     * Firestore) مع القائمة المحلية الحالية لهذا الجهاز، بنفس فحص التكرار
     * الدلالي المستخدم في [recordFeedback] (لا تكرار حرفي فقط) — فسؤال ورد
     * تعلّمه جهاز آخر بصياغة مختلفة عن حالة موجودة أصلاً محلياً لا يُضاف من
     * جديد. هذا فعلياً ما يجعل جهازاً ثانياً "يعرف" ما تعلّمه جهاز أول من
     * تقييمات مستخدميه دون أي حاجة لتكرار نفس التعلّم فيه من الصفر. يُبقي
     * على نفس سقف [MAX_AUTO_LEARNED_EXAMPLES]، مستبعِداً الأقدم أولاً عند
     * تجاوزه بعد الدمج.
     */
    fun mergeLearnedExamples(local: List<TrainedExample>, incoming: List<TrainedExample>): List<TrainedExample> {
        if (incoming.isEmpty()) return local
        val verified = ArrayList<TrainedExample>()
        val verifiedWords = ArrayList<Set<String>>()
        for (candidate in incoming) {
            val cWords = richWordsOf(candidate.pattern)
            if (cWords.isEmpty()) continue
            val duplicate = verifiedWords.any { jaccard(it, cWords) >= AiConfig.trainedMatchThreshold }
            if (!duplicate) {
                verified += candidate
                verifiedWords += cWords
            }
        }
        val capped = verified.takeLast(MAX_AUTO_LEARNED_EXAMPLES)
        val same = capped.size == local.size &&
            capped.indices.all { capped[it].pattern == local[it].pattern && capped[it].response == local[it].response }
        return if (same) local else capped
    }

    // ── المقارنة المنظّمة (يُستخدم في بطاقات المقارنة بالشاشة) ─────────

    /** نقطة مقارنة واحدة (فائدة، تحذير...) مع قائمة الأعشاب التي وردت فيها. */
    data class ComparisonPoint(val text: String, val herbIds: List<String>)

    /**
     * يبني مقارنة نقطة-بنقطة لحقل معيّن (مثل الفوائد) عبر الأعشاب المختارة:
     * يجمع النقاط المتشابهة معنوياً من أعشاب مختلفة في نقطة واحدة مشتركة
     * (بتشابه موزون بأهمية الكلمات عبر [CorpusIndex] بدل عدّ بسيط)، ويترك
     * النقاط المنفردة كما هي، بحيث تُعرض النتيجة منظّمة وواضحة.
     */
    fun compareField(herbs: List<Herb>, field: (Herb) -> String): List<ComparisonPoint> {
        val index = corpusIndexFor(herbs)
        val perHerbPoints = herbs.map { it.id to splitPoints(field(it)) }
        val usedFlags = perHerbPoints.map { (_, pts) -> BooleanArray(pts.size) }
        val results = mutableListOf<ComparisonPoint>()
        val threshold = AiConfig.similarityThreshold

        for (hi in herbs.indices) {
            val (herbId, points) = perHerbPoints[hi]
            for (pi in points.indices) {
                if (usedFlags[hi][pi]) continue
                usedFlags[hi][pi] = true
                val point = points[pi]
                val matched = mutableListOf(herbId)
                val pw = pointInfo(point).words
                for (hj in herbs.indices) {
                    if (hj == hi) continue
                    val (otherId, otherPoints) = perHerbPoints[hj]
                    // كانت هذه الحلقة تضمّ *كل* نقطة من عشبة hj تتجاوز العتبة،
                    // لا أفضلها فقط — فتُستهلك (تُعلَّم "مستخدمة") نقاط عدة من
                    // نفس العشبة لنفس نقطة المرساة رغم أن واحدة فقط تظهر فعلياً
                    // في matched، فتُفقد النقاط الأخرى من نتيجة المقارنة كلياً
                    // (لا تظهر لا هنا ولا كمجموعة منفصلة لاحقاً) رغم أنها قد
                    // كانت الأنسب فعلاً لمرساة أخرى ستُفحص بعدها. نختار الآن
                    // فقط أفضل نقطة غير مستخدمة (الأعلى تشابهاً) من كل عشبة
                    // أخرى، فلا تُفقد نقاط بلا داعٍ وتكون كل مجموعة مقارنة
                    // مبنية على أدقّ تطابق متاح فعلاً.
                    var bestPj = -1
                    var bestSim = 0.0
                    for (pj in otherPoints.indices) {
                        if (usedFlags[hj][pj]) continue
                        val sim = weightedSimilarity(index, pw, pointInfo(otherPoints[pj]).words)
                        if (sim >= threshold && sim > bestSim) {
                            bestSim = sim
                            bestPj = pj
                        }
                    }
                    if (bestPj >= 0) {
                        usedFlags[hj][bestPj] = true
                        if (otherId !in matched) matched += otherId
                    }
                }
                results += ComparisonPoint(point, matched)
            }
        }
        return results
    }

    /**
     * مقارنة شاملة حقيقية بين الأعشاب المختارة — لا الفوائد فقط كما كانت
     * سابقاً. مشكلة حقيقية أُبلغ عنها: "لما تسأله عن مقارنة لازم يبني مقارنة
     * شاملة"، أي يفهم كل ما هو مسجَّل عن العشبتين (فوائد، استخدام، تحذيرات/
     * أضرار) ويؤلّف منها إجابة واحدة مترابطة، لا يقارن حقلاً واحداً وحده. كل
     * قسم أدناه يُبنى بنفس منطق [compareField] (تجميع النقاط المتشابهة
     * معنوياً عبر عشبات مختلفة في نقطة مشتركة)، ثم تُختَم المقارنة بجملة
     * تركيبية صريحة تفصل بين الجانب الآمن والجانب الأقل أماناً — وهذا هو
     * الفارق بين "عرض نقاط منسوخة" و"مقارنة مفهومة فعلياً".
     */
    fun buildOverview(herbs: List<Herb>): String {
        if (herbs.size < 2) return ""

        fun cautionsOf(herb: Herb): String =
            listOf(herb.warnings, herb.harms).filter { it.isNotBlank() && it.trim() != "—" }.joinToString("، ")

        val benefitPoints = compareField(herbs) { it.benefits }
        val cautionPoints = compareField(herbs, ::cautionsOf)
        val sharedBenefits = benefitPoints.filter { it.herbIds.size == herbs.size }
        val sharedCautions = cautionPoints.filter { it.herbIds.size == herbs.size }

        return buildString {
            append("قارنتُ بين ${herbNames(herbs)} بالاعتماد على كل ما هو مسجَّل عنها في الموسوعة (الفوائد، الاستخدام، والتحذيرات):\n\n")

            append("🌿 الفوائد:\n")
            if (sharedBenefits.isNotEmpty()) {
                append("• مشترك بينها: ${sharedBenefits.take(3).joinToString("، ") { it.text }}\n")
            }
            herbs.forEach { herb ->
                val unique = benefitPoints.filter { it.herbIds.size == 1 && it.herbIds.first() == herb.id }
                if (unique.isNotEmpty()) append("• تنفرد ${herb.name} بـ: ${unique.take(2).joinToString("، ") { it.text }}\n")
            }
            if (sharedBenefits.isEmpty() && herbs.all { herb -> benefitPoints.none { herb.id in it.herbIds } }) {
                append("• لا تتوفر بيانات فوائد كافية بعد للمقارنة بينها.\n")
            }

            append("\n💊 طريقة الاستخدام:\n")
            herbs.forEach { herb -> append("• ${herb.name}: ${herb.usage.trim().ifBlank { "لا توجد طريقة استخدام مسجّلة." }}\n") }

            append("\n⚠️ التحذيرات والأضرار:\n")
            if (sharedCautions.isNotEmpty()) {
                append("• مشترك بينها: ${sharedCautions.take(2).joinToString("، ") { it.text }}\n")
            }
            herbs.forEach { herb ->
                val unique = cautionPoints.filter { it.herbIds.size == 1 && it.herbIds.first() == herb.id }
                if (unique.isNotEmpty()) append("• تنفرد ${herb.name} بـ: ${unique.take(2).joinToString("، ") { it.text }}\n")
            }
            val counts = herbs.associateWith { splitPoints(it.warnings).size + splitPoints(it.harms).size }
            val minCount = counts.values.minOrNull() ?: 0
            val safest = counts.entries.firstOrNull { it.value == minCount }?.key
            if (safest != null && counts.values.distinct().size > 1) {
                append("• من ناحية عدد التحذيرات المسجّلة فقط، تبدو ${safest.name} الأقل تحذيرات — لكن هذا لا يعني أنها الأنسب لحالتك؛ استشر مختصاً دوماً.\n")
            } else if (sharedCautions.isEmpty() && herbs.all { herb -> cautionPoints.none { herb.id in it.herbIds } }) {
                append("• لا توجد تحذيرات أو أضرار مسجّلة لهذه الأعشاب في الموسوعة.\n")
            }

            append("\n📌 باختصار: ")
            if (sharedBenefits.isNotEmpty()) {
                append("${herbNames(herbs)} متقاربتان في فوائدهما الأساسية، ")
            } else {
                append("${herbNames(herbs)} تختلفان أكثر مما تتشابهان من ناحية الفوائد المسجَّلة، ")
            }
            if (safest != null && counts.values.distinct().size > 1) {
                append("وتبدو ${safest.name} الخيار الأخف من ناحية التحذيرات.")
            } else {
                append("وتحذيراتهما متقاربة، فالاختيار بينهما يعتمد أكثر على الفائدة المحدَّدة التي تبحث عنها.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // التعرّف على الأعشاب المذكورة في السؤال (أسماء بديلة + أخطاء إملائية)
    // ═══════════════════════════════════════════════════════════════════

    /** الأعشاب المقصودة + تصحيحات إملائية أُجريت على أسمائها (للإعلان عنها للمستخدم). */
    class HerbResolution(val herbs: List<Herb>, val corrections: List<Pair<String, String>>)

    /**
     * أسماء بديلة/عامية/أجنبية شائعة لنفس العشبة (كل مجموعة = عشبة واحدة). يفيد حين
     * يكتب المستخدم اسماً مختلفاً عن المسجَّل بالموسوعة ("الشونيز" بدل "حبة
     * البركة"، "ينسون" بدل "يانسون"): تُطابَق العشبة إن ورد في السؤال أي اسم من
     * مجموعتها وكان أي اسم آخر من نفس المجموعة ضمن اسم العشبة بالموسوعة.
     */
    /** هل تظهر كلمات [seq] متتابعة داخل كلمات السؤال (كل كلمة بأشكالها [qSets])؟ */
    private fun containsSequence(seq: List<String>, qSets: List<Set<String>>): Boolean {
        if (seq.isEmpty() || seq.size > qSets.size) return false
        for (start in 0..(qSets.size - seq.size)) {
            var ok = true
            for (j in seq.indices) {
                if (seq[j] !in qSets[start + j]) { ok = false; break }
            }
            if (ok) return true
        }
        return false
    }

    private val NAME_ALT_SPLIT = Regex("\\s*[/،,|]\\s*|\\s+(?:أو|او)\\s+")

    /**
     * أسماء العشبة البديلة كما كُتبت بالموسوعة ("الكزبرة / الكسبرة" = اسمان)،
     * كل اسم كقائمة كلمات عربية مُطبَّعة بلا "ال" وبلا الاسم العلمي بين القوسين.
     */
    private fun nameAlternatives(herb: Herb): List<List<String>> {
        val parts = herb.name.split(NAME_ALT_SPLIT)
        val out = ArrayList<List<String>>(parts.size)
        for (part in parts) {
            val toks = tokensOf(normalize(ArabicLexicon.arabicNameCore(part)))
                .map { ArabicLexicon.stripAl(it) }
                .filter { it.length >= 2 }
            if (toks.isNotEmpty()) out.add(toks)
        }
        return out
    }

    private fun memberTokens(member: String): List<String> =
        tokensOf(normalize(member)).map { ArabicLexicon.stripAl(it) }.filter { it.isNotBlank() }

    private fun aliasMemberInQuestion(member: String, qSets: List<Set<String>>, qFlat: Set<String>): Boolean {
        val toks = memberTokens(member)
        if (toks.isEmpty()) return false
        return if (toks.size == 1) toks[0] in qFlat else containsSequence(toks, qSets)
    }

    private fun herbNameHasAlias(herb: Herb, group: List<String>): Boolean {
        val nameLoose = tokensOf(normalize(herb.name)).joinToString(" ") { ArabicLexicon.stripAl(it) }
        if (nameLoose.isBlank()) return false
        val nameTokens = tokensOf(nameLoose).toSet()
        val padded = " $nameLoose "
        return group.any { member ->
            val m = memberTokens(member).joinToString(" ")
            when {
                m.isBlank() -> false
                m.contains(' ') -> padded.contains(" $m ")
                else -> m in nameTokens
            }
        }
    }

    /** كلمات أدوات/نيّات/مواضيع شائعة — لا تُعامَل أبداً كاسم عشبة مكتوب بخطأ إملائي. */
    private val queryFillerWords: Set<String> by lazy {
        normSet(
            "ممكن", "بدي", "عندي", "اريد", "ابغى", "ابي", "اعطني", "اعطيني", "قولي", "خبرني", "ساعدني",
            "سمحت", "رجاء", "فضلك", "الله", "يعطيك", "العافية", "هاي", "هذه", "هذا", "هيك", "اللي", "الي",
            "انا", "انت", "لو", "يعني", "طيب", "بس", "كمان", "شوي", "كتير", "ضروري", "اعرف", "معلومات"
        )
    }

    private val nonHerbWords: Set<String> by lazy {
        val all = ArrayList<String>()
        all.addAll(combineIntentWords); all.addAll(planIntentWords); all.addAll(safetyIntentWords)
        all.addAll(usageIntentWords); all.addAll(compareIntentWords); all.addAll(benefitsIntentWords)
        all.addAll(HealthTopicSynonyms.allWords)
        all.addAll(suggestionFillerWords)
        val s = HashSet<String>()
        for (w in all) {
            for (p in tokensOf(normalize(w))) {
                s.add(p)
                s.add(ArabicLexicon.stripAl(p))
            }
        }
        s.addAll(baseStopWordsNorm)
        s.addAll(queryFillerWords)
        s
    }

    private fun fuzzyTokenMatch(qt: String, nt: String): Boolean {
        if (nt.length < 4 || qt.length < 4 || qt[0] != nt[0]) return false
        val limit = if (maxOf(qt.length, nt.length) >= 7) 2 else 1
        return ArabicLexicon.editDistanceAtMost(qt, nt, limit) <= limit
    }

    /** كل "أشكال" كلمة سؤال واحدة (هي نفسها + بلا "ال" + أشكالها الصرفية بلا سوابق/
     * لواحق شائعة، مع/بلا "ال" لكل شكل) — أساس مطابقة [containsSequence] كلمة
     * بكلمة داخل [resolveHerbs] بدل تطابق حرفي تام لا يلتقط "للزنجبيل"/"بالبابونج". */
    private fun tokenVariants(t: String): Set<String> {
        val s = LinkedHashSet<String>()
        s.add(t)
        s.add(ArabicLexicon.stripAl(t))
        for (f in ArabicLexicon.formsOf(t)) {
            s.add(f)
            s.add(ArabicLexicon.stripAl(f))
        }
        return s
    }

    /**
     * يبحث عن الأعشاب المذكورة في نص السؤال على أربعة مستويات (الأدق أولاً)،
     * بدل تطابق حرفي تام لاسم العشبة كاملاً بقوسه الإنجليزي كما كان سابقاً:
     * 1) الاسم العربي مذكور صراحةً ككلمات متتابعة (بأشكال الكلمة: "للزنجبيل"،
     *    "بالبابونج"، جمع/مفرد، "ال" التعريف...).
     * 2) اسم بديل/عامي/أجنبي معروف ([ArabicLexicon.herbAliasGroups]) — مثال: "الشونيز" ↔
     *    "حبة البركة".
     * 3) كل كلمات الاسم مذكورة بأي ترتيب (لأسماء من كلمتين فأكثر).
     * 4) خطأ إملائي بسيط في الاسم (حرف زائد/ناقص/مبدَّل)، مع إعادة الكلمة
     *    المصحَّحة كي يُعلَن عنها للمستخدم ("هل تقصد؟").
     * الأسماء العلمية/الإنجليزية بين الأقواس تُتجاهَل عند المطابقة (راجع
     * [ArabicLexicon.arabicNameCore]) — يؤثر هذا على *كل* عشبة بالموسوعة
     * مكتوب اسمها مع اسم علمي بين قوسين، وهو نمط شائع جداً بهذه الموسوعة.
     */
    fun resolveHerbs(question: String, allHerbs: List<Herb>): HerbResolution {
        val empty = HerbResolution(emptyList(), emptyList())
        val qNorm = normalize(question)
        if (qNorm.isBlank() || allHerbs.isEmpty()) return empty
        val qTokens = tokensOf(qNorm)
        if (qTokens.isEmpty()) return empty
        val qSets = qTokens.map { tokenVariants(it) }
        val qFlat = HashSet<String>()
        for (s in qSets) qFlat.addAll(s)

        val alts = allHerbs.map { it to nameAlternatives(it) }
        val found = LinkedHashSet<Herb>()

        for ((herb, altList) in alts) {
            if (altList.any { containsSequence(it, qSets) }) found.add(herb)
        }
        val aliasGroups = ArabicLexicon.herbAliasGroups.filter { g -> g.any { m -> aliasMemberInQuestion(m, qSets, qFlat) } }
        if (aliasGroups.isNotEmpty()) {
            for (herb in allHerbs) {
                if (aliasGroups.any { g -> herbNameHasAlias(herb, g) }) found.add(herb)
            }
        }
        if (found.isNotEmpty()) return HerbResolution(found.toList(), emptyList())

        for ((herb, altList) in alts) {
            if (altList.any { alt -> alt.size >= 2 && alt.all { it in qFlat } }) found.add(herb)
        }
        if (found.isNotEmpty()) return HerbResolution(found.toList(), emptyList())

        val candidates = qTokens.map { ArabicLexicon.stripAl(it) }.filter { it.length >= 4 && it !in nonHerbWords }
        if (candidates.isEmpty()) return empty
        val corrections = ArrayList<Pair<String, String>>(2)
        for ((herb, altList) in alts) {
            for (alt in altList) {
                var allMatched = true
                var used: Pair<String, String>? = null
                for (nt in alt) {
                    if (nt in qFlat) continue
                    val hit = candidates.firstOrNull { qt -> fuzzyTokenMatch(qt, nt) }
                    if (hit == null) { allMatched = false; break }
                    used = hit to nt
                }
                if (allMatched && used != null) {
                    found.add(herb)
                    corrections.add(used.first to ArabicLexicon.arabicNameCore(herb.name))
                    break
                }
            }
        }
        return HerbResolution(found.toList(), corrections.distinct())
    }

    /** توافقاً مع الاستدعاءات القديمة: الأعشاب فقط بلا تفاصيل تصحيح الإملاء. */
    fun relevantHerbs(question: String, allHerbs: List<Herb>): List<Herb> =
        resolveHerbs(question, allHerbs).herbs

    // ═══════════════════════════════════════════════════════════════════
    // فهرسة نقاط النص مرة واحدة (أداء) — أساس [CorpusIndex] و[matchScore]
    // ═══════════════════════════════════════════════════════════════════

    /** نقطة نص مُحلَّلة مرة واحدة: نصها المُطبَّع، كلماتها، وكلماتها مع أشكالها الصرفية. */
    private class PointInfo(val norm: String, val words: Set<String>, val expanded: Set<String>)

    private val pointCache = HashMap<String, PointInfo>()
    private var pointCacheSyn: Map<String, String>? = null
    private var pointCacheStop: Set<String>? = null

    /**
     * تحليل النقطة النصية مخزَّن حسب نصها نفسه: كان كل استعلام يعيد تطبيع وتقطيع
     * كل نقاط كل الأعشاب من الصفر (آلاف مرات لكل سؤال، ومرات عدة عبر المحاولات
     * المتدرجة في [buildGeneralSearchAnswer]) — أكبر سبب لبطء "التفكير". يُفرَّغ
     * الكاش عند تغيّر مرادفات/كلمات إيقاف المطوّر لأنها تغيّر ناتج [wordsOf].
     */
    @Synchronized
    private fun pointInfo(point: String): PointInfo {
        if (pointCacheSyn !== AiConfig.synonyms || pointCacheStop !== AiConfig.extraStopWords) {
            pointCache.clear()
            pointCacheSyn = AiConfig.synonyms
            pointCacheStop = AiConfig.extraStopWords
        }
        pointCache[point]?.let { return it }
        if (pointCache.size > 30_000) pointCache.clear()
        val words = wordsOf(point)
        val info = PointInfo(normalize(point), words, words + ArabicLexicon.expandForms(words))
        pointCache[point] = info
        return info
    }

    /**
     * إصلاح خلل حقيقي أُبلغ عنه ("سألت عن فوائد الزنجبيل للنوم فما لقى شي رغم
     * وجود نقطة نوم بالموسوعة"): العربية تلصق حروف الجر/العطف مباشرة بالكلمة
     * ("للنوم"، "بالنوم"، "ونوم") بلا مسافة، فتبقى كلمة السؤال ("للنوم") مختلفة
     * حرفياً عن كلمة الموسوعة ("النوم") رغم أنهما نفس المعنى تماماً — لا تطابق
     * حرفي ولا حتى Jaccard يلتقط هذا. الإصلاح: كلمات السؤال تُوسَّع بأشكالها
     * الصرفية ([ArabicLexicon.formsOf]) قبل المقارنة، وتُقارَن بأشكال كلمات
     * النقطة أيضاً ([PointInfo.expanded]، محسوبة مسبقاً هناك) — فتلتقي "نوم"
     * القادمة من الطرفين بصرف النظر عن أي سابقة/لاحقة كانت ملتصقة بأيّهما.
     */
    private fun queryCoverage(index: CorpusIndex, queryWords: Set<String>, pointWords: Set<String>): Double {
        if (queryWords.isEmpty() || pointWords.isEmpty()) return 0.0
        val queryWeight = queryWords.sumOf { index.weightOf(it) }
        if (queryWeight == 0.0) return 0.0
        val matchedWeight = (queryWords intersect pointWords).sumOf { index.weightOf(it) }
        return matchedWeight / queryWeight
    }

    /** أي نسبة من كلمات السؤال الخام موجودة حرفياً كسلسلة فرعية داخل نص النقطة — شبكة أمان أخيرة. */
    private fun rawContainmentRatio(queryWords: Set<String>, pointNormalized: String): Double {
        if (queryWords.isEmpty()) return 0.0
        val matched = queryWords.count { it.length > 1 && pointNormalized.contains(it) }
        return matched.toDouble() / queryWords.size
    }

    /**
     * الدرجة النهائية لمطابقة سؤال المستخدم بنقطة نص واحدة، مستخدمة في البحث
     * الحر والاقتراح: أعلى قيمة بين ثلاثة مقاييس (تشابه موزون، تغطية، احتواء
     * خام)، بعد توسيع كلمات السؤال بأشكالها الصرفية (راجع توثيق [queryCoverage]
     * أعلاه) ومقارنتها بأشكال كلمات النقطة المخزَّنة مسبقاً عبر [pointInfo].
     */
    private fun matchScore(index: CorpusIndex, queryWords: Set<String>, point: String): Double {
        if (queryWords.isEmpty()) return 0.0
        val info = pointInfo(point)
        val expandedQuery = HashSet<String>(queryWords.size * 3)
        expandedQuery.addAll(queryWords)
        for (w in queryWords) expandedQuery.addAll(ArabicLexicon.formsOf(w))
        val knownQuery = expandedQuery.filter { index.isKnown(it) }.toSet().ifEmpty { expandedQuery }
        return maxOf(
            weightedSimilarity(index, knownQuery, info.expanded),
            queryCoverage(index, knownQuery, info.expanded),
            rawContainmentRatio(queryWords, info.norm)
        )
    }

    /** يُبقي فقط نقاطاً مختلفة فعلياً عن بعضها (Jaccard كلماتها < 0.8) — يمنع تكرار نفس الفكرة بصياغتين قريبتين ضمن نفس الرد. */
    private fun <T> dedupeByText(items: List<T>, textOf: (T) -> String): List<T> {
        val seen = ArrayList<Set<String>>()
        val out = ArrayList<T>()
        for (item in items) {
            val w = pointInfo(textOf(item)).words
            if (w.isNotEmpty() && seen.any { jaccard(it, w) >= 0.8 }) continue
            seen.add(w)
            out.add(item)
        }
        return out
    }


    /**
     * أسئلة سريعة مقترحة تُعرض كأزرار فوق مربع الدردشة.
     *
     * كانت هذه الدالة تُرجع دائماً نفس السؤالين مبنيين على أول عشبتين في
     * القائمة (`take(2)`) — أي نفس الاقتراحين بالضبط في كل مرة يُفتح فيها
     * سيمو، بغضّ النظر عن حجم الموسوعة الفعلي، ونفس السؤالين العامّين
     * الثابتين دوماً. الآن تختار عشبتين عشوائيتين من الموسوعة (فتتنوّع
     * الاقتراحات بين مرة وأخرى وتُظهر تنوّع الموسوعة الفعلي بدل نفس
     * العشبتين الأوليين أبداً)، وتختار سؤالين عامّين عشوائياً من مجموعة
     * أوسع بدل سؤالين ثابتين لا يتغيّران.
     */
    private val generalSuggestionPool = listOf(
        "ما هي الأعشاب الآمنة أثناء الحمل؟",
        "اقترح عشبة لتحسين النوم",
        "ما الأعشاب المفيدة لتقوية المناعة؟",
        "ما الأعشاب التي تساعد على الهضم؟",
        "أعطني عشبة تخفّف التوتر والقلق",
        "ما الأعشاب المفيدة لآلام المفاصل؟"
    )

    fun quickSuggestions(selectedHerbs: List<Herb>, allHerbs: List<Herb> = emptyList()): List<String> = when {
        selectedHerbs.size >= 2 -> listOf(
            "ما أبرز الفروقات بينها؟",
            "أيهما أكثر أماناً؟",
            "هل يمكن الجمع بينهما؟",
            "ما طريقة استخدام كل منها؟"
        )
        selectedHerbs.size == 1 -> listOf(
            "ما فوائدها؟",
            "ما طريقة استخدامها؟",
            "هل لها تحذيرات؟"
        )
        else -> buildList {
            val sample = allHerbs.filter { it.name.isNotBlank() }.shuffled().take(2)
            if (sample.isNotEmpty()) add("ما فوائد ${sample[0].name}؟")
            if (sample.size >= 2) add("قارن بين ${sample[0].name} و ${sample[1].name}")
            addAll(generalSuggestionPool.shuffled().take(2))
        }
    }

    // ── الدردشة الذكية (إجابة حرة على أسئلة المستخدم) ──────────────────

    /**
     * رد سيمو مع بيان ما إذا كان "قابلاً للتعلّم الذاتي" منه: فقط إجابات
     * البحث الحر الفعلية (وليست الترحيب/الشكر/إجابة حالة مدرَّبة مسبقاً، إذ
     * لا معنى لإعادة تعلّم شيء متعلَّم أو ثابت أصلاً) تُعرض معها أزرار تقييم
     * في شاشة الدردشة، ليقرر المستخدم إن كانت مفيدة فتُحفظ عبر [recordFeedback].
     */
    data class AssistantReply(val text: String, val learnable: Boolean)

    /**
     * هل يحتاج سيمو فعلاً لـ"تفكير وبحث" ملحوظ قبل الإجابة على هذا السؤال؟
     * تُستخدم في واجهة الدردشة (SemoAssistantScreen) لعرض فقاعة "حسناً، دعني
     * أفكر وأبحث في الموسوعة 🌿" *قبل* استدعاء [answerDetailed] الثقيل،
     * فيشعر المستخدم أن سيمو يفكّر فعلاً بدل أن تظهر الإجابة فجأة بلا أي
     * سياق — تماماً كما يوضّح مساعد جيد خطته قبل تنفيذها.
     *
     * بنفس شروط `if` الأولى في [answerDetailed] بالضبط (لا تكرار منطق
     * مختلف قد ينحرف عنها الاحقاً): سؤال فارغ، أو مطابق لحالة مدرَّبة
     * (يدوياً أو ذاتياً)، أو تحية/شكر أو أي عبارة محادثة مبدئية أخرى
     * ([ConversationalSeed]) — كل هذه إجابات فورية جاهزة لا تحتاج أي بحث
     * فعلي في بيانات الموسوعة، فلا داعي لإظهار "أفكر وأبحث" قبلها. أي
     * سؤال آخر (مقارنة، أمان، استخدام، فوائد، اقتراح، أو بحث حر) يستدعي
     * فعلاً مسح بيانات الموسوعة، فيُعرض التفكير قبله.
     */
    fun needsThinking(question: String): Boolean {
        val qNorm = normalize(question)
        if (qNorm.isBlank()) return false
        // ═══ إصلاح خلل منطقي حقيقي أُبلغ عنه ═══
        // كان هذا الشرط يتحقق فقط من الحالات المدرَّبة، فيُعامِل أي سؤال آخر
        // — بما فيه التحية والشكر والوداع وأسئلة الحال/الهوية/القدرات
        // ("مرحبا"، "شكراً"، "كيفك"...) — على أنه "يحتاج بحثاً فعلياً"،
        // فتُرسَل هذه الردود الفورية البسيطة كلها إلى Gemini عبر الإنترنت
        // بمجرد توفر الوضع الذكي، رغم أنها لا علاقة لها إطلاقاً ببيانات
        // الموسوعة ولا تحتاج أي بحث فيها فعلياً — وهذا بالضبط ما يجعل *كل*
        // إجابة، حتى أبسطها، تعتمد على الإنترنت متى كان متصلاً، بينما تعمل
        // محلياً وبشكل صحيح فقط عند انعدام الاتصال. هذا يخالف تماماً ما
        // توثّقه [AiConfig.onlineEnabled] وSemoAssistantScreen صراحة: "الردود
        // الفورية (ترحيب/شكر/حالة مدرَّبة) تبقى محلية دوماً بلا أي استثناء
        // لأنها أسرع وأدق من أي استدعاء شبكة لها أصلاً" — وكذلك يخالف توثيق
        // هذه الدالة نفسها أعلاه.
        //
        // الإصلاح: مطابقة نفس شروط الرد الفوري الأولى في [answerDetailed]
        // بالضبط (حالة مدرَّبة ← [ConversationalSeed] ← تحية صافية ← صيغة
        // شكر قصيرة) — فهذه وحدها تبقى محلية دوماً بصرف النظر عن الاتصال.
        // فقط ما تبقّى بعدها (مقارنة/أمان/استخدام/فوائد/اقتراح/بحث حر) يُعتبر
        // بحاجة فعلية للتفكير والبحث، سواء جرى محلياً أم عبر الإنترنت.
        if (matchTrainedExample(question) != null) return false
        if (ConversationalSeed.match(question) != null) return false
        if (isPureGreeting(qNorm)) return false
        if (isShortExactPhraseMatch(qNorm, listOf("شكرا", "شكراً", "تسلم", "يعطيك العافية", "مشكور"), maxWords = 6)) return false
        return true
    }

    /**
     * "ذاكرة المحادثة" الخفيفة: هل يبدو هذا السؤال استكمالاً لعشبة/أعشاب
     * كانت محور الحديث في رسالة سابقة، رغم أن المستخدم لم يذكر اسمها هذه
     * المرة؟ مثال واقعي: يسأل المستخدم "ما فوائد الزنجبيل؟"، سيمو يجيب،
     * ثم يسأل المستخدم "طيب شو اضراره؟" دون تكرار اسم الزنجبيل — سؤال
     * طبيعي جداً من إنسان حقيقي، لكن [relevantHerbs] وحدها (تبحث عن اسم
     * عشبة حرفياً في نص السؤال) لا تجد شيئاً هنا فتُسقط السؤال لوضع البحث
     * الحر في كامل الموسوعة، فيضيع "التركيز" على الزنجبيل تحديداً رغم أن
     * السؤال واضح المعنى لأي قارئ.
     *
     * هذه الدالة لا تبني رداً بنفسها ولا تحدّد العشبة المقصودة (تلك مسؤولية
     * الشاشة التي تحتفظ فعلياً بآخر أعشبة كانت "محور تركيز" الحديث) — فقط
     * تقرر: هل يستحق هذا السؤال إعادة استخدام ذلك التركيز السابق بدل
     * اعتباره سؤالاً عاماً جديداً؟ الشرط: وجود كلمة نية واضحة (نفس قوائم
     * [rankedIntents]: أمان/استخدام/فائدة/مقارنة/دمج/خطة) *وليس* سؤال
     * اقتراح عام (ذاك يبحث عمداً في كامل الموسوعة لا في عشبة محدَّدة سلفاً،
     * فلا معنى لتضييقه على التركيز السابق)، مع بقاء السؤال قصيراً نسبياً
     * (١٢ كلمة كحد أقصى) تحوّطاً من اعتبار سؤال طويل جديد كلياً امتداداً
     * لحديث سابق لمجرد احتوائه كلمة نية عابرة ضمن موضوع مختلف تماماً.
     *
     * قدرة جديدة — تكملة موضوعية بلا كلمة نية صريحة: مشكلة حقيقية كانت
     * تفوت هنا: سؤال متابعة قصير مثل "طيب وشو عن الحامل؟" أو "بس كم المدة؟"
     * قد لا يحوي أي كلمة من قوائم النيّات حرفياً، فيُعامَل كسؤال عام جديد
     * يفقد التركيز السابق رغم وضوح أنه استكمال لنفس الحديث لأي قارئ. الآن،
     * إن فشل شرط كلمة النية، يُفحَص شرط أخف: سؤال قصير جداً (٨ كلمات كحد
     * أقصى) يبدأ بأداة "استكمال حديث" شائعة (طيب/وشو/وهل/بس/كمان...) — هذه
     * الأدوات نادراً ما تبدأ سؤالاً مستقلاً كلياً بلا سياق سابق، فوجودها في
     * بداية سؤال قصير مؤشر قوي على أنه تكملة لا بداية جديدة.
     */
    private val followUpCues = listOf(
        "طيب", "وشو", "وهل", "بس", "كمان", "وماذا", "وايش", "ومتى", "وكم", "ليش", "وين"
    )

    fun isFollowUpQuestion(question: String): Boolean {
        val qNorm = normalize(question)
        if (qNorm.isBlank()) return false
        if (isSuggestionIntent(qNorm)) return false
        val wordCount = qNorm.split(Regex("\\s+")).count { it.isNotBlank() }
        if (wordCount > 12) return false
        if (rankedIntents(qNorm).isNotEmpty()) return true
        return wordCount <= 8 && followUpCues.any { qNorm.startsWith(normalize(it)) }
    }

    /** توافقاً مع الاستدعاءات القديمة (مثل اختبار أدوات المطور) التي تحتاج النص فقط. */
    fun answer(question: String, herbs: List<Herb>, allowCompare: Boolean = true, blends: List<Blend> = emptyList()): String =
        answerDetailed(question, herbs, allowCompare, blends).text

    /**
     * يجيب على سؤال حر بالاعتماد على بيانات عشبة واحدة أو أكثر. لا يوجد هنا
     * أي حجب أو تقييد صناعي على المحتوى — المساعد محلي بالكامل ويستخدم فقط
     * نصوص الموسوعة التي أدخلها المطوّر (يدوياً أو عبر التعلّم الذاتي)، فيجيب
     * دوماً بأفضل ما يتوفر لديه من معلومات، ويوضّح بصراحة عندما لا تتوفر
     * بيانات كافية بدل رفض الإجابة.
     *
     * [allowCompare] يحدد ما إذا كان مسموحاً تفعيل منطق "المقارنة/الدمج"
     * المنظّم (يتطلب عشبتين محددتين بوضوح عبر اختيار المستخدم أو ذكرهما
     * بالاسم في السؤال). عند `false` (مثلاً حين تُمرَّر كل الموسوعة كسياق
     * افتراضي لعدم وجود أعشاب محددة) يظل سيمو يجيب بحرية، لكن دون أن "يقارن"
     * تلقائياً بين عشرات الأعشاب التي لم يطلبها أحد — تماماً كما لا يقارن
     * إلا إذا طُلب منه ذلك صراحة.
     */
    /**
     * ═══ ميزة جديدة: أولوية مطلقة لحالات الطوارئ/الأزمات ═══
     * قبل أي منطق آخر (حتى الحالات المدرَّبة): سؤال يحمل إشارة واضحة لإيذاء
     * النفس يُقابَل برد داعم يوجّه لطلب مساعدة حقيقية بدل أي محاولة إجابة عن
     * الأعشاب ([crisisReply])؛ وسؤال يحمل أعراضاً قد تكون طارئة طبياً يُسبَق
     * الجواب العادي بتنبيه صريح ([redFlagNotice]) بدل تجاهلها والإجابة كالمعتاد.
     * التفويض الفعلي هنا لـ[answerDetailedCore] (المنطق الأصلي كاملاً بلا تغيير).
     */
    fun answerDetailed(question: String, herbs: List<Herb>, allowCompare: Boolean = true, blends: List<Blend> = emptyList()): AssistantReply {
        val qNormSafety = normalize(question)
        crisisReply(qNormSafety)?.let { return AssistantReply(it, false) }
        val reply = answerDetailedCore(question, herbs, allowCompare, blends)
        val notice = redFlagNotice(qNormSafety)
        return if (notice != null) AssistantReply("$notice\n\n${reply.text}", false) else reply
    }

    private fun answerDetailedCore(question: String, herbs: List<Herb>, allowCompare: Boolean = true, blends: List<Blend> = emptyList()): AssistantReply {
        val qNorm = normalize(question)
        if (qNorm.isBlank()) {
            return AssistantReply("تفضّل، اسأل سيمو عن أي عشبة: فوائدها، طريقة استخدامها، أو تحذيراتها.", false)
        }

        // أولوية مطلقة للحالات المدرَّبة (يدوياً من المطوّر، أو ذاتياً من
        // تقييمات المستخدمين السابقة) — إن وُجدت مطابقة كافية، يستخدم سيمو
        // ردّها مباشرة قبل أي منطق عام آخر. هذا التحقق (وتحقق ConversationalSeed
        // بعده) يسبقان عمداً فحص "هل تتوفر بيانات أعشاب؟" أدناه: الترحيب
        // والشكر والسؤال عن الحال وهوية سيمو لا تحتاج أي بيانات أعشاب إطلاقاً،
        // فكانت تُخطَف سابقاً برسالة "لم أجد معلومات" فقط لأن قائمة الأعشاب
        // لم تكن قد حُمِّلت بعد من Firestore (أو كانت فارغة لأي سبب مؤقت) —
        // خلل معماري يجعل سؤالاً بسيطاً مثل "كيفك" يعتمد خطأً على بيانات لا
        // علاقة لها به إطلاقاً.
        // إصلاح دقة: الحالات "المتعلَّمة ذاتياً" (من تقييمات المستخدمين) كانت تتقدّم على
        // بيانات الموسوعة نفسها، فجواب خاطئ واحد مُخزَّن لسؤال مشابه كان يطغى على
        // معلومات العشبة الحقيقية. الآن لا تُستخدم إلا في الأسئلة العامة التي لا تسمّي
        // عشبة موجودة في الموسوعة؛ أما التدريب اليدوي للمطوّر فيبقى له الأولوية دوماً.
        val autoAllowed = herbs.size > 3 && resolveHerbs(question, herbs).herbs.isEmpty()
        matchTrainedExample(question, autoAllowed)?.let { return AssistantReply(it, false) }

        // تدريب سيمو الافتتاحي (أكثر من 500 صياغة محادثة، انظر [ConversationalSeed])
        // يأتي بعد تدريب المطوّر مباشرة وقبل أي منطق آخر: يغطي التحية والشكر
        // والوداع وأسئلة الهوية والقدرات، فلا تحتاج هذه لأي بحث في الموسوعة.
        ConversationalSeed.match(question)?.let { return AssistantReply(it, false) }

        // من هنا فقط (بعد استبعاد كل ردود المحادثة العامة التي لا تحتاج بيانات)
        // يصبح فحص توفّر بيانات الموسوعة منطقياً: أي سؤال متبقٍ يحتاج فعلاً
        // للبحث ضمن نصوص الأعشاب، فإن لم تتوفر بعد نعتذر بوضوح بدل الانهيار.
        if (herbs.isEmpty() && blends.isEmpty()) {
            return AssistantReply("لم أجد في الموسوعة معلومات كافية للإجابة على هذا السؤال بعد 🌿", false)
        }

        // "محدَّد" = عدد قليل من الأعشاب المستهدفة فعلياً (باختيار المستخدم أو
        // ذكرها بالاسم) — عندها فقط تُبنى إجابات مفصّلة لكل عشبة على حدة.
        // إن كان السياق هو كامل الموسوعة (لم يُطلب/يُحدَّد شيء)، يُستخدم
        // البحث الحر بدل تكرار كل عشبة، تفادياً لإغراق الدردشة بإجابة ضخمة
        // لم يطلبها أحد — نفس مبدأ "لا مقارنة أو استعراض إلا عند الطلب".
        val specific = herbs.size <= 3

        // ═══ إصلاح خلل حقيقي أُبلغ عنه: أحياناً يظهر ردّ الترحيب بدل تنفيذ
        // طلب مقارنة فعلي بين عشبتين ═══
        // السبب الجذري: [containsAny] فحص "احتواء نص فرعي" حرفي بلا حدود
        // كلمة، والقائمة تضم "hi" — وأي طلب مقارنة يذكر اسماً علمياً لاتينياً
        // لعشبة (شائع جداً بهذه الموسوعة تحديداً، انظر لقطة الشاشة المرفقة
        // بالبلاغ: "Matricaria"، "Origanum majorana") قد يحوي حرفياً "hi"
        // ضمن كلمة أخرى كلياً (Thymus، chamomilla...) فيُخطَف السؤال كاملاً
        // كـ"تحية" رغم عدم وجود أي نية ترحيب فعلية فيه — وهذا يفسّر بالضبط
        // لماذا يظهر الخلل مع أزواج أعشاب معيّنة (البابونج/الزعتر تحديداً
        // شائعان جداً وأسماؤهما العلمية تحوي "hi") لا مع كل الأزواج، أي
        // "أحياناً" حرفياً كما وُصف بالبلاغ. الإصلاح: التحية تُعتبر تحية
        // فقط إذا (أ) كانت كلمة التحية موجودة ككلمة مستقلة كاملة (لا كجزء من
        // كلمة أطول)، و(ب) كان السؤال كله قصيراً (٥ كلمات كحد أقصى) — سؤال
        // مقارنة طويل يذكر عشبتين لن يُصنَّف كتحية أبداً حتى لو تضمّن اسماً
        // علمياً بالمصادفة.
        if (isPureGreeting(qNorm)) {
            return AssistantReply(
                "أهلاً 👋 أنا سيمو، مساعدك الذكي في عالم الأعشاب. اسألني عن أي عشبة تريدها: فوائدها، طريقة استخدامها، تحذيراتها، أو اطلب مني مقارنة بين أكثر من عشبة، وسأجيبك فوراً من بيانات الموسوعة.",
                false
            )
        }
        if (isShortExactPhraseMatch(qNorm, listOf("شكرا", "شكراً", "تسلم", "يعطيك العافية", "مشكور"), maxWords = 6)) {
            return AssistantReply("عفواً 🌿 أنا سيمو، دائماً هنا لأي سؤال آخر عن الأعشاب.", false)
        }

        // ── تصنيف النية "بالنقاط" بدل أول شرط يتحقق بترتيب ثابت ──────────
        // مشكلة حقيقية أُبلغ عنها ("سؤال تحديد فوائد بيرجع تحذيرات أولاً بلا
        // داعٍ" — مثال حقيقي: "ما فوائد القطونة او لسان الحمل؟"): بعض أسماء
        // الأعشاب تحوي بذاتها كلمة من كلمات نية "الأمان" ضمن اسمها العادي
        // تماماً — "لسان الحمل" (اسم عشبة Plantain، لا علاقة له بالحمل أو
        // الرضاعة إطلاقاً) يحوي حرفياً كلمة "حمل" ضمن اسمه، وهي نفسها إحدى
        // كلمات [safetyIntentWords] الخاصة بتحذيرات الحمل/الرضاعة. فسؤال
        // بسيط مثل هذا كان يُسجَّل له نية "أمان" بمجرد ذكر اسم العشبة نفسه،
        // بنفس درجة نية "فوائد" (كل منهما كلمة واحدة مطابقة) — وعند التعادل
        // تسبق "أمان" "فوائد" دوماً بحكم ترتيب [rankedIntents]، فيظهر رد
        // التحذيرات أولاً رغم أن السؤال لم يذكر الحمل/الرضاعة إطلاقاً، بل
        // فقط اسم العشبة المطلوبة بالضبط. الإصلاح: تُزال أسماء الأعشاب
        // المحدَّدة فعلياً من نص السؤال قبل احتساب درجات النيّات — فكلمة
        // نية تقع ضمن اسم العشبة بذاته لا تُحتسب، بينما تبقى محتسبة بشكل
        // طبيعي لو ذكرها المستخدم صراحة خارج اسم العشبة (مثال: "ما أضرار
        // لسان الحمل على الحامل؟" تبقى تُصنَّف أماناً بجدارة، لأن "الحامل"
        // كلمة إضافية خارج اسم العشبة المُزال).
        val qNormForIntent = qNormWithoutHerbNames(qNorm, herbs)

        // مشكلة حقيقية أُبلغ عنها ("سؤال يحمل كلمات تخصّ أكثر من نية معاً
        // مثل "ما أضرار وطريقة استخدام الزنجبيل؟" — تحذير + استخدام معاً، أو
        // "ايهما أفضل لعلاج الأرق؟" — مقارنة + فائدة معاً) كان يُصنَّف دوماً
        // حسب أي فرع مكتوب أولاً في الكود، بغضّ النظر عن أن كلمات نية أخرى
        // قد تكون أوضح فعلياً في نص السؤال. الآن تُحسب درجة كل نية (عدد
        // كلماتها المفتاحية المطابقة فعلياً)، وتُرتَّب النيّات تنازلياً حسب
        // هذه الدرجة عبر [rankedIntents] فتُجرَّب الأقوى فعلياً أولاً؛ فإن
        // فشل "شرطها" الخاص (مثلاً "مقارنة" لكن لا توجد عشبتان فعلياً)،
        // يُجرَّب ما يليها في الترتيب بدل السقوط مباشرة للبحث الحر العام.
        // عند تعادل الدرجات يُحافَظ على ترتيب الأولوية الطبي الآمن نفسه
        // (دمج > أمان > استخدام > مقارنة > فوائد) لأن التحذيرات يجب أن
        // تسبق الفوائد دوماً — لكن هذا التعادل نفسه صار أدق الآن بعد
        // استبعاد اسم العشبة من الحساب أعلاه.
        val ranked = rankedIntents(qNormForIntent)

        // "دمج" و"خطة" حصريتان دوماً كما كانتا: إن تحققت شروطهما تُعطيان
        // الأولوية المطلقة وتُرجعان فوراً بلا أي دمج مع نيات أخرى، لأن كلتيهما
        // أصلاً رد مركَّب من عدة حقول معاً (لا معنى لدمجه بحقل منفصل آخر).
        for (intent in ranked) {
            when (intent) {
                "combine" -> if (allowCompare && specific && herbs.size >= 2) {
                    return AssistantReply(withContextHint(buildCombineAnswer(herbs), question, herbs), false)
                }
                "plan" -> if (specific) {
                    return AssistantReply(withContextHint(buildPlanAnswer(herbs), question, herbs), false)
                }
            }
        }

        // قدرة جديدة: أسئلة "مركّبة" تحمل أكثر من نية قابلة للدمج معاً (أمان/
        // استخدام/مقارنة/فائدة) بنفس الجملة — بدل الاكتفاء بأقوى نية وتجاهل
        // البقية كما كان سابقاً، تُجمَع كل النيات المطابقة فعلياً (بحد أقصى
        // ٣ لتبقى الإجابة مقروءة) بجواب واحد بعناوين واضحة لكل قسم. إن
        // طابقت نية واحدة فقط، يبقى السلوك مطابقاً تماماً لما كان سابقاً
        // (نفس دالة البناء ونفس الصياغة) بلا أي تغيير ملحوظ.
        val combinable = ranked.filter { it == "safety" || it == "usage" || it == "compare" || it == "benefits" }
            .filter { intent ->
                when (intent) {
                    "compare" -> allowCompare && specific && herbs.size >= 2
                    else -> specific
                }
            }
        if (combinable.size >= 2) {
            return AssistantReply(withContextHint(buildCombinedAnswer(combinable.take(3), herbs, qNorm), question, herbs), false)
        }
        if (combinable.size == 1) {
            val text = when (combinable.first()) {
                "safety" -> buildSafetyAnswer(herbs, qNorm)
                "usage" -> buildUsageAnswer(herbs)
                "compare" -> buildOverview(herbs)
                else -> buildBenefitsAnswer(herbs, qNorm)
            }
            return AssistantReply(withContextHint(text, question, herbs), false)
        }

        // "اقترح/رشّح/انصحني بعشبة": فقط عندما لا توجد عشبة محدَّدة سلفاً
        // (لا إرفاق ولا ذكر اسم صريح) — عندها "الاقتراح" له معنى فعلياً،
        // وهو اختيار الأنسب من كامل الموسوعة بدل عشبة واحدة معروفة أصلاً.
        // إن كانت هناك عشبة محدَّدة، تُعامَل الكلمة كجزء عادي من سؤال عادي
        // (فتُغطّى أصلاً عبر فروع الفائدة/الاستخدام أعلاه) بدل خطفها هنا.
        if (!specific && isSuggestionIntent(qNorm)) {
            val (text, learnable) = buildSuggestionAnswer(question, herbs)
            return AssistantReply(withContextHint(text, question, herbs), learnable)
        }

        val (text, learnable) = buildGeneralSearchAnswer(question, herbs, blends)
        return AssistantReply(withContextHint(text, question, herbs), learnable)
    }

    /**
     * قدرة جديدة: دمج أكثر من نية "قابلة للدمج" (أمان/استخدام/مقارنة/فائدة)
     * في جواب واحد بعناوين واضحة لكل قسم، بدل إجبار المستخدم على سؤال كل
     * نية على حدة عندما يسألها فعلياً بنفس الجملة (مثال حقيقي: "ما أضرار
     * وطريقة استخدام الزنجبيل؟" — تحذير + استخدام معاً بنفس السؤال).
     */
    private fun buildCombinedAnswer(intents: List<String>, herbs: List<Herb>, qNorm: String): String {
        val sectionTitles = mapOf(
            "safety" to "⚠️ التحذيرات والأضرار",
            "usage" to "💊 طريقة الاستخدام",
            "compare" to "🔍 المقارنة",
            "benefits" to "🌿 الفوائد"
        )
        return intents.joinToString("\n\n") { intent ->
            val title = sectionTitles[intent] ?: intent
            val body = when (intent) {
                "safety" -> buildSafetyAnswer(herbs, qNorm)
                "usage" -> buildUsageAnswer(herbs)
                "compare" -> buildOverview(herbs)
                else -> buildBenefitsAnswer(herbs, qNorm)
            }
            "$title:\n${body.trim()}"
        }
    }

    private val combineIntentWords = listOf("جمع", "دمج", "معا", "معاً", "سوية", "سويا", "نفس الوقت", "تفاعل", "خلط")
    private val safetyIntentWords = listOf("خطر", "اضرار", "أضرار", "تحذير", "حامل", "حمل", "رضاعة", "رضاعه", "طفل", "اطفال", "أطفال", "امان", "أمان", "اثار جانبية", "آثار جانبية")
    private val usageIntentWords = listOf("استخدام", "استعمال", "طريقة", "طريقه", "كيف استخدم", "جرعة", "جرعه", "مقدار")
    private val compareIntentWords = listOf("فرق", "يختلف", "اختلاف", "افضل", "أفضل", "احسن", "أحسن", "ايهما", "أيهما", "قارن", "مقارنة")
    // توسيع فهم "نية الفائدة": أُضيفت صيغ فصحى وعامية إضافية (خواص/منافع/
    // يشفي/شافي...) بجانب الكلمات الأصلية، فتُحتسَب أسئلة مثل "ما خواص
    // الزنجبيل؟" أو "ايش بيشفي فيه البابونج؟" كنية فائدة أيضاً، لا فقط
    // صياغة "ما فوائد X؟" الحرفية. راجع أيضاً [DialectNormalizer.rawMap]
    // حيث تُطبَّع هذه الصيغ العامية إلى مرادفها الفصيح أولاً.
    private val benefitsIntentWords = listOf(
        "فائدة", "فائده", "فوائد", "يفيد", "علاج", "يعالج", "مفيد",
        "خواص", "خاصية", "منافع", "منفعة", "يشفي", "شافي", "شافية", "علاجي", "علاجية"
    )
    private val planIntentWords = listOf(
        "خطة", "خطه", "روتين", "برنامج", "جدول", "كيف ابدا", "كيف أبدأ",
        "خطة استخدام", "برنامج استخدام", "طريقة يومية", "طريقه يوميه"
    )

    /**
     * يرتّب أسماء النيّات المحتملة (combine/safety/usage/compare/benefits)
     * تنازلياً حسب عدد كلماتها المفتاحية المطابقة فعلياً في [qNorm]
     * (المُطبَّع مسبقاً)، مع استبعاد أي نية لم تُطابَق إطلاقاً (درجتها صفر).
     * الترتيب الابتدائي في الخريطة (قبل الفرز) هو ترتيب الأولوية الطبي
     * الآمن نفسه المستخدم سابقاً، و`sortedByDescending` مستقرّ (Stable Sort)
     * في Kotlin فيحافظ على هذا الترتيب تلقائياً عند تعادل الدرجات — تماماً
     * كما كان السلوك السابق عند تطابق نية واحدة فقط.
     */
    /**
     * تُزيل من نص السؤال المُطبَّع (qNorm) أي جزء يطابق اسم عشبة من [herbs]
     * المحدَّدة فعلياً لهذا السؤال — تُستخدَم فقط قبل احتساب درجات النيّات
     * في [rankedIntents]، كي لا تُحتسَب كلمة نية (مثل "حمل" ضمن اسم عشبة
     * "لسان الحمل") لمجرد وقوعها داخل اسم العشبة نفسه بدل أن تكون طلباً
     * فعلياً من المستخدم. راجع التعليق أعلى استدعائها في [answerDetailed].
     */
    private fun qNormWithoutHerbNames(qNorm: String, herbs: List<Herb>): String {
        var result = qNorm
        herbs.forEach { herb ->
            val name = normalize(herb.name)
            if (name.isNotBlank()) result = result.replace(name, " ")
        }
        return result
    }

    private fun rankedIntents(qNorm: String): List<String> {
        val priorityOrder = listOf("combine", "plan", "safety", "usage", "compare", "benefits")
        val scores = mapOf(
            "combine" to combineIntentWords.count { qNorm.contains(normalize(it)) },
            "plan" to planIntentWords.count { qNorm.contains(normalize(it)) },
            "safety" to safetyIntentWords.count { qNorm.contains(normalize(it)) },
            "usage" to usageIntentWords.count { qNorm.contains(normalize(it)) },
            "compare" to compareIntentWords.count { qNorm.contains(normalize(it)) },
            "benefits" to (benefitsIntentWords + HealthTopicSynonyms.allWords).count { qNorm.contains(normalize(it)) }
        )
        return priorityOrder.filter { (scores[it] ?: 0) > 0 }.sortedByDescending { scores[it] ?: 0 }
    }

    private fun buildCombineAnswer(herbs: List<Herb>): String = buildString {
        append("لا تحتوي الموسوعة على قاعدة بيانات مخصّصة لتفاعلات الأعشاب مع بعضها، لذا لا يمكنني الجزم بأمان الجمع بين ${herbNames(herbs)}.\n\n")
        append("أبرز التحذيرات المسجّلة لكل عشبة على حدة:\n")
        herbs.forEach { herb ->
            val cautions = (splitPoints(herb.warnings) + splitPoints(herb.harms)).take(2)
            append("🔸 ${herb.name}: ")
            append(if (cautions.isNotEmpty()) cautions.joinToString("، ") else "لا توجد تحذيرات مسجّلة")
            append("\n")
        }
        append("\nالأفضل استشارة طبيب أو صيدلاني قبل الجمع بينهما، خصوصاً مع وجود أدوية أو حالة صحية.")
    }

    private fun buildSafetyAnswer(herbs: List<Herb>, qNorm: String): String {
        val pregnancyAsked = containsAny(qNorm, listOf("حامل", "حمل", "رضاعة", "رضاعه", "رضع"))
        return buildString {
            herbs.forEach { herb ->
                val points = splitPoints(herb.warnings) + splitPoints(herb.harms)
                append("🔸 ${herb.name}:\n")
                if (points.isEmpty()) {
                    append("لا توجد تحذيرات أو أضرار مسجّلة في الموسوعة لهذه العشبة.\n")
                } else {
                    val relevant = if (pregnancyAsked)
                        points.filter { containsAny(normalize(it), listOf("حامل", "حمل", "رضاعة", "رضاعه", "رضع")) }
                    else points
                    val toShow = relevant.ifEmpty { points }.take(3)
                    toShow.forEach { append("• $it\n") }
                    if (pregnancyAsked && relevant.isEmpty()) {
                        append("(لم يُذكر صراحةً الحمل أو الرضاعة، يُستحسن استشارة الطبيب للتأكد)\n")
                    }
                }
            }
            append("\nهذه المعلومات للاطلاع فقط ولا تُغني عن استشارة مختص.")
        }
    }

    private fun buildUsageAnswer(herbs: List<Herb>): String = buildString {
        herbs.forEach { herb ->
            append("🔸 ${herb.name}: ")
            append(herb.usage.ifBlank { "لا توجد طريقة استخدام مسجّلة." })
            append("\n")
        }
    }

    /**
     * قدرة جديدة: "خطة استخدام" مركَّبة — بدل عرض حقل الاستخدام وحده (كما
     * في [buildUsageAnswer])، تُركِّب هذه من عدة حقول معاً (استخدام + أبرز
     * فائدة مستهدَفة + أهم تحذير) رداً واحداً عملياً على هيئة خطوات، يشبه
     * ما يطلبه مستخدم فعلياً حين يسأل "كيف أبدأ" أو "أعطني روتيناً" بدل
     * سؤال جزئي عن حقل واحد فقط. يُفعَّل عبر [planIntentWords] ضمن
     * [rankedIntents]، بنفس مبدأ بقية النيّات (يتطلب عشبة/أعشاب محدَّدة).
     */
    private fun buildPlanAnswer(herbs: List<Herb>): String = buildString {
        append("خطة استرشادية للاستخدام (ليست وصفة طبية):\n\n")
        herbs.forEach { herb ->
            append("🔸 ${herb.name}:\n")
            append("• طريقة الاستخدام: ${herb.usage.ifBlank { "غير مسجّلة في الموسوعة" }}\n")
            splitPoints(herb.benefits).firstOrNull()?.let { append("• الهدف الأساسي: $it\n") }
            (splitPoints(herb.warnings) + splitPoints(herb.harms)).firstOrNull()?.let { append("• انتبه: $it\n") }
            append("• نصيحة عامة: ابدأ بكمية أقل من المعتاد أول مرة وراقب استجابة جسمك قبل الاعتماد عليها ضمن روتين يومي ثابت.\n\n")
        }
        append("هذه خطة عامة مبنية على بيانات الموسوعة فقط، ولا تغني عن استشارة طبيب أو أخصائي أعشاب قبل الالتزام بها.")
    }.trimEnd()

    /**
     * [qNorm] (افتراضياً فارغ للتوافق مع الاستدعاءات القديمة) يتيح تمييز
     * النقاط المرتبطة بموضوع محدَّد ذكره السؤال (مثل "تنحيف"/"وزن"، انظر
     * [HealthTopicSynonyms]) من بين كل فوائد العشبة، بدل عرض النص كاملاً
     * بلا تمييز حين يسأل المستخدم فعلياً عن غرض بعينه لا عن الفوائد عموماً
     * — نفس مبدأ فلترة سؤال الحمل/الرضاعة في [buildSafetyAnswer] تماماً.
     */
    private fun buildBenefitsAnswer(herbs: List<Herb>, qNorm: String = ""): String {
        if (herbs.size < 2) {
            val herb = herbs.first()
            val points = splitPoints(herb.benefits)
            if (points.isEmpty()) {
                return "🔸 ${herb.name}: لا توجد فوائد مسجّلة لهذه العشبة في الموسوعة بعد."
            }
            val topic = HealthTopicSynonyms.clusterMentionedIn(qNorm)
            val relevant = if (topic != null) points.filter { containsAny(normalize(it), topic.toList()) } else emptyList()
            val topicLabel = if (relevant.isNotEmpty()) HealthTopicSynonyms.labelMentionedIn(qNorm) else null
            return if (relevant.isNotEmpty()) {
                val prefix = if (topicLabel != null) "🔸 ${herb.name} (بخصوص $topicLabel):\n" else "🔸 ${herb.name}:\n"
                prefix + relevant.joinToString("\n") { "• $it" }
            } else {
                "🔸 ${herb.name}: ${herb.benefits}"
            }
        }
        val points = compareField(herbs) { it.benefits }
        val shared = points.filter { it.herbIds.size == herbs.size }
        return buildString {
            if (shared.isNotEmpty()) {
                append("مشترك بين ${herbNames(herbs)}: ${shared.joinToString("، ") { it.text }}\n\n")
            }
            herbs.forEach { herb ->
                val mine = points.filter { it.herbIds.size < herbs.size && herb.id in it.herbIds }
                if (mine.isNotEmpty()) {
                    append("🔸 ${herb.name} تنفرد بـ: ${mine.joinToString("، ") { it.text }}\n")
                }
            }
            if (shared.isEmpty() && herbs.all { herb -> points.none { herb.id in it.herbIds } }) {
                append("لا توجد فوائد مسجّلة لهذه الأعشاب في الموسوعة بعد.")
            }
        }
    }

    // ملاحظة: منطق "أقل الأعشاب تحذيراتٍ" (سابقاً في buildSafetyGlance
    // منفصلة) صار جزءاً من [buildOverview] نفسها ضمن قسم "التحذيرات
    // والأضرار" الموحَّد، بدل دالة منفصلة تُستدعى بعدها بلا سياق.

    // ── اقتراح عشبة مناسبة لهدف/عرض معيّن ("اقترح عشبة لتحسين النوم") ───

    /**
     * كلمات/عبارات "أمر الاقتراح" نفسها — لا تحمل أي دلالة عن الهدف الفعلي
     * (النوم، الهضم...) وتُستبعد من كلمات المطابقة في [buildSuggestionAnswer]
     * حتى لا "تُغرق" حساب التشابه بكلمات لا علاقة لها بنص الموسوعة.
     */
    private val suggestionFillerWords: Set<String> by lazy {
        listOf(
            "اقترح", "اقتراح", "اقترحلي", "رشح", "رشحلي", "رشحي", "انصح", "انصحني",
            "انصحيني", "أنصحني", "توصية", "اوصي", "أوصي", "توصي", "عشبة", "عشب",
            "اعشاب", "أعشاب", "نبات", "نبتة", "نباتات"
        ).map { normalize(it) }.toSet()
    }

    /**
     * "عشبة لـ..." (مثل "عشبة لتخفيف الألم") نمط شائع جداً لطلب اقتراح، لكن
     * مطابقتها كسلسلة نصية بسيطة ("عشبة ل") كانت تُخطئ أيضاً في عبارات لا
     * علاقة لها بطلب اقتراح إطلاقاً — مثل "هذه عشبة لها فوائد كثيرة" أو
     * "عشبة له استخدامات قديمة" — لأن "لها"/"له"/"لهم"... كلها تبدأ بـ"ل"
     * فتُطابق السلسلة الفرعية "عشبة ل" رغم أن "ل" هنا حرف جر لضمير متصل لا
     * بداية فعل/كلمة تصف الغرض من الاقتراح. هذا التعبير النمطي (Regex) يلتقط
     * فقط "عشبة ل" (أو "عشب ل") حين لا تكون الكلمة التالية أحد هذه الضمائر
     * المتصلة الشائعة، فيبقى يلتقط "عشبة لتخفيف الألم"/"عشبة لعلاج السكري"
     * دون أن يُخطئ في جمل عادية تصف عشبة بدل طلب اقتراح واحدة.
     */
    private val suggestionForPattern = Regex("عشبه?\\s+ل(?!ها\\b|له\\b|لهم\\b|لهن\\b|لك\\b|لي\\b|لنا\\b)")

    /** هل يطلب المستخدم اقتراحاً/توصية بعشبة (بدل سؤال مباشر عن عشبة معروفة)؟ */
    private fun isSuggestionIntent(qNorm: String): Boolean =
        suggestionForPattern.containsMatchIn(qNorm) || containsAny(
            qNorm,
            listOf(
                "اقترح", "اقتراح", "رشح", "رشحلي", "رشحي", "انصح", "انصحني", "انصحيني",
                "أنصحني", "توصية", "اوصي", "أوصي", "توصي", "افضل عشبة", "أفضل عشبة",
                "احسن عشبة", "أحسن عشبة", "عشبة تساعد", "عشبة تفيد",
                "علاج طبيعي", "حل طبيعي", "دواء طبيعي", "وش تنصح", "وش ينفع", "شو ينفع لل"
            )
        )

    /** أفضل نقطة مطابِقة لعشبة واحدة مع درجة تشابهها ومصدر الحقل. */
    private data class HerbMatch(val text: String, val field: String, val score: Double)

    /**
     * يبني اقتراحاً مرتَّباً لأفضل الأعشاب المتاحة لهدف/عرض ورد في السؤال،
     * عبر نفس خط أنابيب "تحليل → بحث → تحليل بيانات → تنظيم" المستخدم في
     * البحث الحر ([buildGeneralSearchAnswer])، لكن بتجميع على مستوى العشبة
     * (أفضل نقطة لكل عشبة) بدل عرض نقاط مبعثرة، وبترجيح حقل الفوائد أعلى من
     * غيره (لأن "الاقتراح" يعني عملياً: أي عشبة تفيد في هذا الغرض تحديداً)،
     * فتظهر أفضل ٣ أعشاب مرشَّحة مع سبب الترشيح من نص الموسوعة نفسه.
     */
    private fun buildSuggestionAnswer(question: String, herbs: List<Herb>): Pair<String, Boolean> {
        val index = corpusIndexFor(herbs)
        val qNorm = normalize(question)
        val qWords = analyzeQuestion(question, index) - suggestionFillerWords
        if (qWords.isEmpty()) {
            return "خبرني أكثر عن الهدف أو العرض اللي حابب عشبة تساعدك فيه (مثل: النوم، الهضم، المناعة، التوتر...) وسأبحث لك ضمن الموسوعة 🌿" to false
        }

        // الهدف/الموضوع الذي "فهمه" سيمو من مرادفات السؤال (إن وُجد)، مثل
        // "دايت" أو "كرش" ← "إنقاص الوزن" — يُذكر صراحةً في الرد أدناه، حتى
        // يتأكد المستخدم أن سيمو فهم مقصده الفعلي من المرادف ولم يكتفِ
        // بمطابقة كلمات حرفية عمياء لا يعرف معناها.
        val topicLabel = HealthTopicSynonyms.labelMentionedIn(qNorm)

        val fieldWeight = mapOf(
            "الفوائد" to 1.15, "الاستخدام" to 0.9, "ملاحظات" to 0.85,
            "التحذيرات" to 0.4, "الأضرار" to 0.4,
            // وزن منخفض عمداً: هدف "الاقتراح" هو الغرض/العرض (نوم، هضم...)
            // لا اسم العشبة نفسه، فيبقى حقل الاسم مساعداً فقط (مثلاً عندما
            // يذكر المستخدم اسماً علمياً ضمن سؤاله) دون أن يطغى على تطابق
            // الفوائد الفعلي.
            "الاسم" to 0.5
        )

        // قدرة جديدة — فهم النفي: "اقترح عشبة للنوم بس مو البابونج" يجب ألا
        // يرشّح البابونج رغم مطابقته الموضوع، لأن المستخدم استبعده صراحة.
        // تُستبعد أي عشبة ذُكر اسمها بصيغة منفية قبل بناء المرشَّحين أصلاً،
        // لا بعد الترشيح، حتى لا تُزاحم عشبة مستبعدة عشبة أخرى فعلاً مناسبة
        // من حصص "أفضل ٣" أدناه.
        val excludedByNegation = herbs.filter { isNegatedMention(qNorm, it.name) }.toSet()
        val candidateHerbs = if (excludedByNegation.isEmpty()) herbs else herbs.filterNot { it in excludedByNegation }

        val bestPerHerb = mutableMapOf<Herb, HerbMatch>()
        candidateHerbs.forEach { herb ->
            searchableFields.forEach { (label, getter) ->
                val weight = fieldWeight[label] ?: 1.0
                splitPoints(getter(herb)).forEach { point ->
                    val sim = matchScore(index, qWords, point) * weight
                    val current = bestPerHerb[herb]
                    if (current == null || sim > current.score) {
                        bestPerHerb[herb] = HerbMatch(point, label, sim)
                    }
                }
            }
        }

        var ranked = bestPerHerb.entries
            .filter { it.value.score > AiConfig.searchThreshold }
            .sortedByDescending { it.value.score }
            .take(3)

        // نفس مبدأ "يحاول قبل أن يستسلم" المستخدم في [buildGeneralSearchAnswer]:
        // لا نتيجة بالعتبة المعتادة؟ نحاول مرة ثانية بعتبة أخفّ قبل الإقرار
        // الصريح بعدم وجود اقتراح مناسب.
        var triedHarder = false
        if (ranked.isEmpty()) {
            val relaxedThreshold = (AiConfig.searchThreshold * 0.4).coerceAtLeast(0.02)
            ranked = bestPerHerb.entries
                .filter { it.value.score > relaxedThreshold }
                .sortedByDescending { it.value.score }
                .take(3)
            triedHarder = true
        }

        if (ranked.isEmpty()) {
            return "لم أجد في بيانات الموسوعة عشبة ترتبط مباشرة بما طلبته، رغم أنني وسّعت البحث أكثر من مرة. جرّب صياغة الهدف بكلمة مختلفة، أو اذكر عرضاً أو فائدة أكثر تحديداً." to false
        }

        // قدرة جديدة — مؤشر ثقة: تطابق ضعيف (أعلى بقليل فقط من العتبة) لا
        // يستحق نفس ثقة تطابق قوي واضح. بدل عرض الجواب بنفس لهجة الحسم
        // دوماً، يُقاس أقوى تطابق فعلياً مقابل عتبة أعلى بكثير من عتبة القبول
        // الأدنى؛ إن لم يبلغها، تُستخدم صياغة افتتاحية أكثر تحفّظاً تنبّه
        // المستخدم أن الترشيح تقريبي لا شبه مؤكَّد.
        val lowConfidence = ranked.first().value.score < AiConfig.searchThreshold * 2.2

        // ── جملة تركيبية صريحة (لا مجرد نسخ/لصق نقاط متفرّقة) ────────────
        // مشكلة حقيقية أُبلغ عنها: كان الرد يعرض النقاط المطابقة فقط دون أي
        // جملة واضحة تجمعها، فيبدو وكأن سيمو "لصق" مقتطفات بلا فهم حقيقي.
        // الآن يُصرَّح أولاً بالهدف الذي فهمه (إن أمكن تسميته)، ثم تُختَم
        // القائمة بجملة تركيبية صريحة تسمّي كل الأعشاب المرشَّحة معاً وتؤكد
        // أنها كلها تخدم نفس الهدف — هذا هو الفارق بين "بحث ولصق" و"فهم ورد".
        val suggestionSeed = ranked.joinToString("|") { it.key.id }.ifBlank { "seed" }
        val text = buildString {
            if (topicLabel != null) {
                append("فهمت من سؤالك أنك تبحث عن عشبة تساعد في \"$topicLabel\". ")
            }
            when {
                triedHarder && lowConfidence ->
                    append(pick(listOf(
                        "وسّعت البحث أكثر من مرة، ومش متأكد تماماً من دقة الترشيح، بس هاي أقرب الأعشاب المتوفرة لهدفك:\n\n",
                        "لم ألقَ تطابقاً قوياً من أول مرة، فوسّعت البحث، وهذه أقرب الأعشاب الممكنة:\n\n"
                    ), suggestionSeed))
                triedHarder ->
                    append(pick(listOf(
                        "وسّعت البحث أكثر من مرة في بيانات الموسوعة، وهذه أقرب الأعشاب المتوفرة لهدفك:\n\n",
                        "بعد بحث أوسع في الموسوعة، هذه الأعشاب الأقرب لما تحتاجه:\n\n"
                    ), suggestionSeed))
                lowConfidence ->
                    append(pick(listOf(
                        "مش متأكد تماماً من دقة الترشيح، بس هاي أقرب الأعشاب المتوفرة لهدفك بحسب بيانات الموسوعة:\n\n",
                        "الترابط هون تقريبي، بس هاي أقرب اقتراح ممكن من الموسوعة:\n\n"
                    ), suggestionSeed))
                else ->
                    append(pick(listOf(
                        "بحثت وحلّلت بيانات الموسوعة، وهذه أنسب الأعشاب المتوفرة لهدفك:\n\n",
                        "بحسب بيانات الموسوعة، هذه أفضل الأعشاب المتاحة لهدفك:\n\n"
                    ), suggestionSeed))
            }
            ranked.forEachIndexed { i, entry ->
                val herb = entry.key
                val match = entry.value
                append("${i + 1}. 🔸 ${herb.name}\n")
                append("   • ${formatPoint(match.field, match.text)}\n")
            }
            append("\n📌 باختصار: ")
            append(
                if (ranked.size == 1) "عشبة ${ranked.first().key.name} هي الأنسب من بيانات الموسوعة"
                else "الأعشاب ${ranked.joinToString("، ") { it.key.name }} كلها تساعد بحسب بيانات الموسوعة"
            )
            append(if (topicLabel != null) " في $topicLabel.\n\n" else " لهدفك.\n\n")
            if (excludedByNegation.isNotEmpty()) {
                append("(استبعدت ${herbNames(excludedByNegation)} بناءً على طلبك.)\n\n")
            }
            append("هذه النتائج مبنية فقط على نصوص الموسوعة، وليست بديلاً عن استشارة طبيب أو صيدلاني، خصوصاً مع وجود حمل أو أدوية أو حالة صحية مزمنة.")
        }
        return text to true
    }

    private data class SearchHit(val herb: Herb, val field: String, val text: String, val score: Double)

    // اسم العشبة (يحوي غالباً الاسم العلمي بالإنجليزية إلى جانب الاسم الشائع
    // بالعربية معاً في نفس الحقل، كما هو مخزَّن فعلياً في الموسوعة) لم يكن
    // يدخل البحث الحر أو الاقتراح إطلاقاً — كان يُفحص فقط عبر [relevantHerbs]
    // (احتواء حرفي تام). فسؤال حرّ يذكر الاسم العلمي أو جزءاً منه فقط (مثل
    // "Cucurbita" أو "لب القرع" ضمن سؤال أطول) دون التطابق الحرفي الكامل
    // الذي يشترطه [relevantHerbs] كان لا يجد العشبة إطلاقاً رغم أن اسمها
    // يحمل الإجابة مباشرة. إضافته كحقل بحث عادي (بنفس خط أنابيب التحليل/
    // المطابقة الموزون) توسّع دقة "البحث والمطابقة" دون أي تغيير في المنطق.
    private val searchableFields = listOf<Pair<String, (Herb) -> String>>(
        "الاسم" to { it.name },
        "الفوائد" to { it.benefits },
        "الاستخدام" to { it.usage },
        "التحذيرات" to { it.warnings },
        "الأضرار" to { it.harms },
        "ملاحظات" to { it.notes },
        // إدراك الفئة: انظر توثيق [HerbCategoryLookup] — يسمح لسيمو الآن بأن
        // يعرض/يطابق اسم فئة العشبة كنقطة بحث فعلية، لا فقط حقولها الستة.
        "الفئة" to { categoryOf(it) }
    )

    // ── دعم الخلطات ("الخلطات" — Blend) في البحث الحر العام ─────────────
    //
    // سيمو كان يقرأ فقط مجموعة "herbs" ولا يعرف بوجود مجموعة "blends"
    // إطلاقاً، رغم أنها جزء أصيل من الموسوعة (تُعرض له شاشتها الخاصة في
    // التطبيق تماماً كالأعشاب). سؤال حر عن خلطة بالاسم أو عن أي محتوى من
    // نصوصها كان يحصل دوماً على "لم أجد" رغم توفّر الإجابة فعلاً في قاعدة
    // البيانات. هذا القسم يوسّع البحث الحر (لا المقارنة المنظّمة بين
    // عشبتين تحديداً، التي تبقى للأعشاب فقط) ليشمل الخلطات أيضاً، بنفس خط
    // أنابيب التحليل/المطابقة الموزون المستخدم للأعشاب تماماً.

    private data class BlendHit(val blend: Blend, val field: String, val text: String, val score: Double)

    private val blendSearchableFields = listOf<Pair<String, (Blend) -> String>>(
        "الاسم" to { it.name },
        "الفوائد" to { it.benefits },
        "الاستخدام" to { it.usage },
        "التحذيرات" to { it.warnings },
        "ملاحظات" to { it.notes }
    )

    private fun gatherBlendCandidates(
        qWords: Set<String>,
        blends: List<Blend>,
        index: CorpusIndex,
        threshold: Double = AiConfig.searchThreshold
    ): List<BlendHit> {
        val hits = mutableListOf<BlendHit>()
        blends.forEach { blend ->
            blendSearchableFields.forEach { (label, getter) ->
                splitPoints(getter(blend)).forEach { point ->
                    val sim = matchScore(index, qWords, point)
                    if (sim > threshold) hits += BlendHit(blend, label, point, sim)
                }
            }
        }
        return hits
    }

    /**
     * إصلاح تكرار حقيقي: نقطتان مختلفتان نصياً لكن متقاربتان جداً بالمعنى (مثال:
     * نفس الفائدة مذكورة بصياغتين قريبتين ضمن حقلين مختلفين) كانتا تظهران معاً
     * كأنهما معلومتان منفصلتان. تُستبعَد النقاط شديدة التشابه ([dedupeByText])
     * *قبل* أخذ أفضل ٣، فتُستغَل الحصة الكاملة بمعلومات فعلاً متنوعة.
     */
    private fun organizeBlendHits(hits: List<BlendHit>): Map<Blend, List<BlendHit>> =
        hits.groupBy { it.blend }
            .entries
            .sortedByDescending { (_, blendHits) -> blendHits.maxOf { it.score } }
            .take(2)
            .associate { (blend, blendHits) ->
                blend to dedupeByText(blendHits.sortedByDescending { it.score }) { it.text }.take(3)
            }

    /**
     * يختار أهمّ [take] كلمات من كلمات السؤال حسب وزنها الفعلي في الموسوعة
     * (IDF عبر [CorpusIndex.weightOf]) — تُستخدم في المحاولة الأخيرة من
     * [buildGeneralSearchAnswer] لتبسيط سؤال طويل إلى "جوهره" فقط، فقد تكون
     * كلمة ثانوية ضمن السؤال (لا صلة فعلية لها بالموسوعة) هي ما خفّض
     * التشابه الإجمالي دون داعٍ في المحاولتين الأوليين.
     */
    private fun coreWordsOf(qWords: Set<String>, index: CorpusIndex, take: Int): Set<String> =
        qWords.sortedByDescending { index.weightOf(it) }.take(take).toSet()

    /**
     * البحث الحر الكامل في كل نصوص الموسوعة، على مراحل واضحة ومنفصلة —
     * بالضبط تسلسل "سؤال → تحليل → تفكير → تنظيم → تجميع النتيجة وإرسالها":
     * 1) [analyzeQuestion]  — يحلّل سؤال المستخدم إلى كلمات مفتاحية،
     *    ويوسّعها بعلاقات [CorpusIndex] الضمنية + جذور [ArabicLexicon]
     *    التقريبية، بحيث يفهم صيغاً لم تُذكر حرفياً في نص الموسوعة.
     * 2) [gatherCandidates] — "يفكّر" بالإجابة عبر مسح كل حقول كل الأعشاب
     *    المتاحة ومقارنة كل نقطة فيها بكلمات السؤال (تشابه موزون بالأهمية
     *    IDF بدل عدّ الكلمات بالتساوي)، ويحتفظ فقط بما يتجاوز عتبة القبول.
     * 3) [organizeHits]     — "ينسّق الأفكار": يرتّب النتائج حسب الصلة، ثم
     *    يجمّعها تحت كل عشبة معاً بدل تشتيتها.
     * 4) [composeAnswer]    — يجمع كل هذا في رد واحد مقروء ويُعيده جاهزاً
     *    للعرض في الدردشة.
     * يعيد النص + بياناً هل عُثر فعلاً على نتائج ذات صلة (`true`) أم أن الرد
     * كان رسالة تعذّر عامة (`false`) — يُستخدم هذا لتحديد أهلية الرد للتعلّم
     * الذاتي (انظر [AssistantReply.learnable]).
     *
     * "يحاول قبل أن يعطي الإجابة": سيمو لا يستسلم عند أول محاولة فارغة، بل
     * يمرّ بثلاث محاولات متدرّجة الصرامة (بالضبط بشروط `if` صريحة، لا خوارزمية
     * ضمنية) قبل أن يقرّ فعلاً بعدم وجود إجابة — يحاكي بذلك التفكير خطوة
     * بخطوة قبل إعطاء إجابة نهائية:
     * - المحاولة ١: العتبة المعتادة [AiConfig.searchThreshold] — الأدق.
     * - المحاولة ٢ (فقط إن فشلت ١): عتبة مخفَّضة، بحثاً بمرونة أكبر بنفس
     *   كلمات السؤال الموسَّعة، قبل التسليم بعدم وجود شيء.
     * - المحاولة ٣ (فقط إن فشلت ٢ أيضاً): تبسيط السؤال إلى أهمّ كلماته فقط
     *   ([coreWordsOf]) بعتبة شبه منعدمة — آخر محاولة ممكنة قبل الاعتراف
     *   الصريح بعدم توفّر إجابة (عبر [fallbackHelp]).
     */
    // ═══ جسور لـ[SemanticIndex] ═══
    /** كل نقاط عشبة (حقل → نص) كما يفهرسها البحث الحر. */
    fun searchPoints(herb: Herb): List<Pair<String, String>> =
        searchableFields.flatMap { (label, getter) -> splitPoints(getter(herb)).map { label to it } }

    /** كلمات النص المُطبَّعة بلا "ال" (نفس تحليل الموسوعة). */
    fun searchWords(text: String): List<String> =
        tokensOf(normalize(text)).map { ArabicLexicon.stripAl(it) }.filter { it.length >= 2 }

    /** كلمات النص بعد توسيعها بالجذور والمرادفات (للمطابقة المعنوية). */
    fun expandedWords(text: String): Set<String> = richWordsOf(text)

    fun dialectNormalize(text: String): String =
        tokensOf(normalize(text)).joinToString(" ") { DialectNormalizer.apply(it) }

    fun intentFields(question: String): Set<String> {
        val intents = rankedIntents(normalize(question))
        val out = LinkedHashSet<String>()
        for (intent in intents) {
            when (intent) {
                "safety" -> { out += "التحذيرات"; out += "الأضرار" }
                "usage" -> out += "الاستخدام"
                "benefits" -> out += "الفوائد"
            }
        }
        return out
    }

    private val chronicWordSet: Set<String> by lazy {
        normSet("سكر", "سكري", "السكري", "ضغط", "قلب", "كلى", "كلية", "كبد", "مزمن", "مزمنة", "صرع", "ربو", "غدة", "سرطان", "جلطة", "كوليسترول", "فشل")
    }

    fun requiresDoctorNotice(question: String): Boolean {
        val qNorm = normalize(question)
        if (qNorm.isBlank()) return false
        val forms = formsSetOf(qNorm)
        return pregnancyWordSet.any { it in forms } ||
            childWordSet.any { it in forms } ||
            medicationWordSet.any { it in forms } ||
            chronicWordSet.any { it in forms } ||
            surgeryWordSet.any { it in forms } ||
            ageNumberRe.containsMatchIn(qNorm)
    }

    private const val SEMANTIC_MIN = 0.30
    private const val SEMANTIC_SCALE = 0.8

    // ═══ تدقيق الرد قبل عرضه ═══
    private val safetyContextWords = listOf(
        "حامل", "حمل", "رضاعة", "مرضع", "طفل", "اطفال", "رضيع", "ضغط", "سكر", "سكري",
        "قلب", "كلى", "كلية", "كبد", "دواء", "ادوية", "مميع", "جراحة", "عملية", "حساسية", "صرع"
    )
    private val disclaimerMarkers = listOf("استشر", "الطبيب", "طبيب", "مختص", "صيدلي")

    /**
     * طبقة تدقيق أخيرة على أي رد قبل عرضه: إن كان السؤال يمسّ فئة حساسة طبياً (حمل، أطفال،
     * أمراض مزمنة، أدوية...) ولا يتضمن الرد أي تنبيه لاستشارة مختص، يُلحَق تنبيه ثابت.
     * لا تمسّ الردود الفورية (تحية/حالة مدرَّبة/أزمة) ولا ردود "لم أجد" والتوضيح.
     */
    fun verifyReply(question: String, reply: AssistantReply): AssistantReply {
        if (isSafetyCritical(question)) return reply
        if (!requiresDoctorNotice(question)) return reply
        val text = reply.text
        if (text.contains(SemoReplyPolicy.DOCTOR_NOTICE)) return reply
        return AssistantReply(SemoReplyPolicy.appendNotice(text), reply.learnable)
    }

    private fun clarifyAnswer(candidates: List<Herb>): String {
        val names = candidates.take(3).joinToString(" أو ") { it.name }
        return "لست متأكداً أن ما وجدته يجيب سؤالك بدقة" +
            (if (names.isNotBlank()) "، وأقرب ما وجدته: $names. هل تقصد إحداها؟" else ".") +
            " جرّب ذكر اسم العشبة، أو اسأل بصياغة أوضح (فوائد/استخدام/تحذيرات)."
    }

    // ═══ أدوات البحث الذكي المحلي-أولاً (تستعملها SemoAssistantScreen) ═══

    /** هل يطابق السؤال حالة مدرَّبة (يدوية أو ذاتية)؟ يستعمله المسار الأونلاين لتفادي الاتصال بلا داعٍ. */
    fun hasTrainedMatch(question: String): Boolean = matchTrainedExample(question) != null

    /** هل السؤال حساس (أزمة نفسية/خطر)؟ هذه تبقى محلية دوماً ولا تُرسَل لأي بحث إنترنت. */
    fun isSafetyCritical(question: String): Boolean = crisisReply(normalize(question)) != null

    /**
     * أقوى درجة تطابق (0..1) يجدها البحث المحلي لسؤال حرّ في أعشاب/خلطات الموسوعة.
     * 0.0 = لا شيء. تُقارَن بـ[isLocalAnswerStrong] لتقرير هل نكتفي بالموسوعة أم نبحث في الإنترنت.
     */
    fun localSearchStrength(question: String, herbs: List<Herb>, blends: List<Blend> = emptyList()): Double {
        if (herbs.isEmpty() && blends.isEmpty()) return 0.0
        val index = corpusIndexFor(herbs, blends)
        val qWords = analyzeQuestion(question, index)
        if (qWords.isEmpty()) return 0.0
        val hits = gatherCandidates(qWords, herbs, index, AiConfig.searchThreshold)
        val blendHits = gatherBlendCandidates(qWords, blends, index, AiConfig.searchThreshold)
        val lexical = maxOf(hits.maxOfOrNull { it.score } ?: 0.0, blendHits.maxOfOrNull { it.score } ?: 0.0)
        val semantic = if (herbs.size > 3)
            (SemanticIndex.rank(question, herbs, limit = 1).firstOrNull()?.score ?: 0.0).let { if (it >= SEMANTIC_MIN) it * SEMANTIC_SCALE else 0.0 }
        else 0.0
        return maxOf(lexical, semantic)
    }

    fun isLocalAnswerStrong(strength: Double): Boolean = strength >= AiConfig.searchThreshold * 2.2

    /**
     * هل يبدو السؤال استعلاماً عن عشبة محدّدة (فائدة/استخدام/أمان/مقارنة...) وليس
     * طلب اقتراح عام؟ إن كان كذلك ولم تُحَلّ أي عشبة محلياً، فالأرجح أن العشبة غير مسجّلة
     * في الموسوعة — فيستحق السؤال بحثاً في الإنترنت حتى لو وجد البحث المحلي نقاطاً ضعيفة عامة.
     */
    fun looksLikeHerbLookup(question: String): Boolean {
        val qNorm = normalize(question)
        if (qNorm.isBlank() || isSuggestionIntent(qNorm)) return false
        return rankedIntents(qNorm).isNotEmpty()
    }

    /**
     * أكثر [limit] أعشاب صلة بالسؤال (لا "أول N عشبة" عشوائياً كما كان يُرسَل لـGemini سابقاً،
     * فكانت أي عشبة بعد الـ150 الأولى تضيع كلياً من سياق الإجابة).
     */
    fun topRelevantHerbs(question: String, herbs: List<Herb>, blends: List<Blend> = emptyList(), limit: Int = 12): List<Herb> {
        if (herbs.size <= limit) return herbs
        val index = corpusIndexFor(herbs, blends)
        val qWords = analyzeQuestion(question, index)
        if (qWords.isEmpty()) return herbs.take(limit)
        val hits = gatherCandidates(qWords, herbs, index, 0.02)
        return hits.groupBy { it.herb }.entries
            .sortedByDescending { e -> e.value.maxOf { it.score } }
            .take(limit)
            .map { it.key }
    }

    private fun buildGeneralSearchAnswer(question: String, herbs: List<Herb>, blends: List<Blend> = emptyList()): Pair<String, Boolean> {
        val index = corpusIndexFor(herbs, blends)
        val qWords = analyzeQuestion(question, index)
        if (qWords.isEmpty()) return fallbackHelp(herbs, question) to false

        // المحاولة ١ — العتبة المعتادة.
        var hits = gatherCandidates(qWords, herbs, index, AiConfig.searchThreshold)
        var blendHits = gatherBlendCandidates(qWords, blends, index, AiConfig.searchThreshold)
        var triedHarder = false

        // المحاولة ٢ — لم يُعثر على شيء بعد؟ نخفّض عتبة القبول قبل الاستسلام،
        // تماماً كما يعيد إنسان البحث بكلمات أوسع لو لم يجد بالبحث الدقيق أول مرة.
        if (hits.isEmpty() && blendHits.isEmpty()) {
            val relaxedThreshold = (AiConfig.searchThreshold * 0.4).coerceAtLeast(0.02)
            hits = gatherCandidates(qWords, herbs, index, relaxedThreshold)
            blendHits = gatherBlendCandidates(qWords, blends, index, relaxedThreshold)
            triedHarder = true
        }

        // المحاولة ٣ — لا تزال فارغة؟ نُبسّط السؤال إلى أهمّ كلمتين فقط
        // (الأعلى وزناً في الموسوعة)، فقد تكون كلمة ثانوية في السؤال هي ما
        // أفسد المطابقة الإجمالية في المحاولتين السابقتين.
        if (hits.isEmpty() && blendHits.isEmpty()) {
            val coreWords = coreWordsOf(qWords, index, take = 2)
            if (coreWords.isNotEmpty() && coreWords != qWords) {
                hits = gatherCandidates(coreWords, herbs, index, threshold = 0.02)
                blendHits = gatherBlendCandidates(coreWords, blends, index, threshold = 0.02)
            }
            triedHarder = true
        }

        // المحاولة ٤ — تشابه المعنى التقريبي ([SemanticIndex]): يلتقط الأخطاء الإملائية واللهجات
        // والمرادفات التي فاتت المطابقة الحرفية، ويُدمَج مع نتائجها دون أن يطغى عليها.
        if (herbs.size > 3) {
            val hadLexical = hits.isNotEmpty()
            val extra = SemanticIndex.rank(question, herbs, limit = 12)
                .filter { it.score >= SEMANTIC_MIN }
                .map { SearchHit(it.herb, it.field, it.text, it.score * SEMANTIC_SCALE) }
            if (extra.isNotEmpty()) {
                val seen = hits.map { it.herb.id to it.text }.toHashSet()
                hits = hits + extra.filter { (it.herb.id to it.text) !in seen }
                if (!hadLexical) triedHarder = true
            }
        }

        // بعد كل هذه المحاولات: لا شيء فعلاً — الآن فقط يُقرّ سيمو بذلك.
        if (hits.isEmpty() && blendHits.isEmpty()) return fallbackHelp(herbs, question) to false

        val organized = organizeHits(hits)
        val organizedBlends = organizeBlendHits(blendHits)

        // قدرة جديدة — مؤشر ثقة: أقوى نتيجة فعلية (بغضّ النظر عن كونها من
        // أعشاب أو خلطات) تُقاس مقابل عتبة أعلى بكثير من عتبة القبول الأدنى؛
        // إن لم تبلغها، تُستخدم صياغة افتتاحية أكثر تحفّظاً في [composeAnswer]
        // بدل الإيحاء بنفس درجة الثقة لكل نتيجة بغضّ النظر عن قوة تطابقها.
        val topScore = maxOf(
            hits.maxOfOrNull { it.score } ?: 0.0,
            blendHits.maxOfOrNull { it.score } ?: 0.0
        )
        val lowConfidence = topScore < AiConfig.searchThreshold * 2.2

        // تدقيق قبل الرد: ثقة منخفضة ولا كلمة جوهرية من السؤال تظهر في أي نص سيُعرض ⇒ الأرجح
        // أنها مطابقة عرضية. نسأل المستخدم "هل تقصد؟" بدل أن نعرض نقاطاً قد تضلّله.
        if (lowConfidence && herbs.size > 3 && organizedBlends.isEmpty()) {
            val core = coreWordsOf(qWords, index, take = 3)
            if (core.isNotEmpty()) {
                val shown = organized.values.flatten().joinToString(" ") { it.text } + " " +
                    organized.keys.joinToString(" ") { it.name }
                val shownWords = richWordsOf(shown)
                if (core.none { it in shownWords }) return clarifyAnswer(organized.keys.toList()) to false
            }
        }

        return composeAnswer(organized, organizedBlends, triedHarder, lowConfidence) to true
    }

    /**
     * المرحلة ١ — تحليل السؤال: كلمات مفتاحية، مُوسَّعة على ثلاث مراحل
     * متتالية (كل مرحلة تضيف احتمالات مطابقة أكثر، بلا حذف لما قبلها):
     * 1) علاقات الموسوعة الضمنية ([CorpusIndex.expand]).
     * 2) جذور اللغة التقريبية ([ArabicLexicon.expand]) — تُطبَّق *قبل*
     *    القاموس عمداً (كانت بعده سابقاً): [DictionaryLexicon.synonymsOf]
     *    يبحث عن الكلمة بصيغتها المُطبَّعة تماماً كمفتاح أساسي في قاعدة
     *    البيانات، فكلمة سؤال بصيغة مختلفة عن الصيغة المخزَّنة (جمع، لاحقة
     *    ضمير، أداة تعريف...) كانت تفوّت أي مرادف موجود فعلياً لجذرها لمجرد
     *    اختلاف الصيغة — لا لعدم وجود المرادف. تجذير الكلمة أولاً ثم البحث
     *    عن مرادفات كل من الصيغة الأصلية *و* جذرها يرفع فعلياً عدد المرادفات
     *    التي يستخدمها سيمو من نفس القاموس المرفق دون أي بيانات إضافية.
     * 3) مرادفات القاموس الخارجي المرفق محلياً ([DictionaryLexicon.expand]
     *    — Rabih Dictionary + Arabic WordNet، بلا إنترنت ولا تكلفة)، وهذا
     *    ما يمكّن سيمو من فهم اسم بديل لعشبة أو مرادف عام لكلمة في السؤال
     *    (مثل "دواء" بدل "علاج") لم تُذكر حرفياً في نص الموسوعة.
     */
    private fun analyzeQuestion(question: String, index: CorpusIndex): Set<String> {
        val base = wordsOf(question)
        if (base.isEmpty()) return base
        val expandedByCorpus = index.expand(base)
        val expandedByStems = ArabicLexicon.expand(expandedByCorpus)
        val expandedByDictionary = DictionaryLexicon.expand(expandedByStems)
        return HealthTopicSynonyms.expand(expandedByDictionary)
    }

    /**
     * توسيع "خفيف" مماثل لِـ[analyzeQuestion] (تجذير + قاموس المرادفات)
     * لكن بلا حاجة لفهرس موسوعة ([CorpusIndex]) — يُستخدم لمطابقة نصوص
     * قصيرة مستقلة عن نصوص عشبة معيّنة (حالات التدريب اليدوي/الذاتي)، حتى
     * تفهم مطابقة الحالات المدرَّبة صياغات مرادفة لا الصياغة الحرفية فقط
     * — وهذا هو أثر "تعلّم سيمو من المرادفات" فعلياً على الحالات التي
     * يحفظها من تقييمات المستخدمين.
     */
    private fun richWordsOf(text: String): Set<String> {
        val base = wordsOf(text)
        if (base.isEmpty()) return base
        return HealthTopicSynonyms.expand(DictionaryLexicon.expand(ArabicLexicon.expand(base)))
    }

    /** المرحلة ٢ — "التفكير بالإجابة": مسح كل نقاط كل حقل، وترجيح كل نقطة حسب مدى صلتها الفعلية بالسؤال. */
    private fun gatherCandidates(
        qWords: Set<String>,
        herbs: List<Herb>,
        index: CorpusIndex,
        threshold: Double = AiConfig.searchThreshold
    ): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        herbs.forEach { herb ->
            searchableFields.forEach { (label, getter) ->
                splitPoints(getter(herb)).forEach { point ->
                    val sim = matchScore(index, qWords, point)
                    if (sim > threshold) hits += SearchHit(herb, label, point, sim)
                }
            }
        }
        return hits
    }

    /**
     * المرحلة ٣ — تنسيق الأفكار: تُجمَّع كل النقاط حسب العشبة أولاً، ثم
     * تُرتَّب *الأعشاب نفسها* تنازلياً حسب أقوى نقطة لديها (لا الاكتفاء
     * بترتيب النقاط المبعثرة عالمياً كما كان سابقاً)، فتظهر العشبة الأكثر
     * صلة بالسؤال أولاً دوماً، مع أفضل ٣ نقاط من نصوصها فقط — هذا هو
     * "التحليل" الفعلي لبيانات الموسوعة بدل عرض أول ٤ نقاط بغضّ النظر عن
     * مصدرها.
     */
    /** راجع توثيق [organizeBlendHits] أعلاه — نفس إصلاح تكرار النقاط المتقاربة، للأعشاب هنا. */
    private fun organizeHits(hits: List<SearchHit>): Map<Herb, List<SearchHit>> =
        hits.groupBy { it.herb }
            .entries
            .sortedByDescending { (_, herbHits) -> herbHits.maxOf { it.score } }
            .take(3)
            .associate { (herb, herbHits) ->
                herb to dedupeByText(herbHits.sortedByDescending { it.score }) { it.text }.take(3)
            }

    /**
     * إصلاح عرض حقيقي أُبلغ عنه: كانت كل نقطة تُعرض بصيغة "[التصنيف] النص"
     * (تسمية الحقل بين قوسين *قبل* النص). القوسان "[" "]" حرفان "محايدان"
     * في خوارزمية Bidi (تخضعان لـ"خوارزمية الأقواس المزدوجة" الخاصة بها)،
     * وعند غياب فرض اتجاه RTL صريح على مستوى التطبيق (انظر الإصلاح في
     * MainActivity) كانا يُعاد ترتيبهما بصرياً في نهاية السطر بدل بدايته —
     * تحديداً المشكلة الظاهرة في الصورة المُبلَّغ عنها (تسمية الحقل تظهر بعد
     * النص لا قبله). الصيغة الجديدة "التصنيف: النص" (نقطتان بدل قوسين) هي
     * الأسلوب العربي القياسي في عرض تسمية حقل، ولا تخضع لخوارزمية الأقواس
     * المزدوجة، فتبقى ثابتة الترتيب حتى في أسوأ ظروف Bidi — طبقة حماية
     * إضافية فوق إصلاح الاتجاه نفسه لا بديلاً عنه.
     */
    private fun formatPoint(field: String, text: String): String = "$field: $text"

    /**
     * المرحلة ٤ — تجميع النتيجة النهائية وإرسالها كرد واحد مقروء. تُعرض
     * نتائج الأعشاب أولاً ثم الخلطات (إن وُجدت) في قسم منفصل بعلامة مميّزة
     * (🧪) حتى يُدرك المستخدم أن الرد قد يخلط بين نوعين مختلفين من عناصر
     * الموسوعة. [triedHarder] = هل احتاج سيمو لمحاولة ثانية/ثالثة أوسع
     * (انظر [buildGeneralSearchAnswer]) قبل الوصول لهذه النتائج؟ إن كان
     * كذلك، تُستخدم صياغة افتتاحية مختلفة تعكس أن البحث لم يكن مباشراً.
     */
    /**
     * قدرة جديدة — تنويع الصياغة الافتتاحية: كانت كل حالة من الحالات
     * الأربع (عادية/بحث موسَّع/ثقة منخفضة/كلاهما) تستخدم جملة افتتاحية
     * *واحدة ثابتة* في كل مرة، فيبدو ردّ سيمو مكرَّراً وآلياً على أسئلة
     * متتالية رغم اختلاف مضمونها فعلياً — هذا بالضبط ما يجعل الحوار "أسلس"
     * حين يتنوّع بدل التكرار الحرفي. كل حالة الآن لديها عدة صياغات متكافئة
     * المعنى، ويُختار بينها بثبات حسب محتوى النتيجة نفسها (بصمة أسماء
     * الأعشاب/الخلطات الظاهرة) — لا عشوائياً بالكامل، حتى يبقى نفس السؤال
     * بنفس النتائج يُعيد نفس الصياغة دوماً (يحافظ على سلوك [recordFeedback]
     * القابل للتنبؤ)، بينما تتنوّع الصياغة فعلياً بين أسئلة مختلفة.
     */
    private val normalOpenings = listOf(
        "بحثت وحلّلت بيانات الموسوعة، وهذه أقرب النتائج لسؤالك:\n\n",
        "هاي أهم ما لقيته في الموسوعة يخص سؤالك:\n\n",
        "بحسب بيانات الموسوعة، هذه أقرب إجابة لسؤالك:\n\n"
    )
    private val triedHarderOpenings = listOf(
        "بحثت في كل زوايا الموسوعة ووسّعت البحث أكثر من مرة قبل أن أصل لهذا:\n\n",
        "ما لقيت جواباً مباشراً من أول محاولة، فوسّعت البحث أكثر، ووصلت لهذا:\n\n"
    )
    private val lowConfidenceOpenings = listOf(
        "مش متأكد تماماً من دقة هذا الجواب، بس هاي أقرب نتيجة لقيتها ضمن بيانات الموسوعة:\n\n",
        "الترابط هون مش قوي جداً، بس هاي أقرب ما وجدته في الموسوعة لسؤالك:\n\n"
    )
    private val triedHarderLowConfidenceOpenings = listOf(
        "بحثت في كل زوايا الموسوعة ووسّعت البحث أكثر من مرة، ومش متأكد تماماً من دقة هذا الجواب، بس هاي أقرب النتائج لسؤالك:\n\n",
        "وسّعت البحث كثيراً ولم أجد تطابقاً واضحاً، لكن هذه أقرب نتيجة ممكنة من الموسوعة:\n\n"
    )
    private val closingInvitations = listOf(
        "🌱 إذا حاب تفاصيل أكثر عن أي نقطة فيها، اسألني مباشرة.",
        "🌿 قل لي إذا تحتاج توضيحاً أكثر عن أي جزء من هذا."
    )

    /** يختار عنصراً ثابتاً من [pool] حسب بصمة [seed] — نفس المدخل يعطي دوماً نفس الاختيار. */
    private fun <T> pick(pool: List<T>, seed: String): T {
        val idx = seed.hashCode().let { if (it < 0) -it else it } % pool.size
        return pool[idx]
    }

    private fun composeAnswer(
        organized: Map<Herb, List<SearchHit>>,
        organizedBlends: Map<Blend, List<BlendHit>> = emptyMap(),
        triedHarder: Boolean = false,
        lowConfidence: Boolean = false
    ): String = buildString {
        val seed = (organized.keys.joinToString("|") { it.id } + "#" + organizedBlends.keys.joinToString("|") { it.id })
            .ifBlank { "seed" }
        when {
            triedHarder && lowConfidence -> append(pick(triedHarderLowConfidenceOpenings, seed))
            triedHarder -> append(pick(triedHarderOpenings, seed))
            lowConfidence -> append(pick(lowConfidenceOpenings, seed))
            else -> append(pick(normalOpenings, seed))
        }
        organized.forEach { (herb, herbHits) ->
            append("🔸 ${herb.name}:\n")
            herbHits.forEach { append("• ${formatPoint(it.field, it.text)}\n") }
            append("\n")
        }
        organizedBlends.forEach { (blend, blendHits) ->
            append("🧪 خلطة ${blend.name}:\n")
            blendHits.forEach { append("• ${formatPoint(it.field, it.text)}\n") }
            append("\n")
        }
        append(pick(closingInvitations, seed))
    }.trimEnd()

    /** ثلاثيات حروف متتالية لكلمة مُطبَّعة — أساس مقياس تشابه يتحمّل الأخطاء الإملائية الطفيفة أدناه. */
    private fun trigramsOf(word: String): Set<String> {
        if (word.length < 3) return setOf(word)
        return (0..word.length - 3).map { word.substring(it, it + 3) }.toSet()
    }

    /**
     * قدرة جديدة: "هل تقصد؟" — عندما يفشل البحث الحر تماماً في إيجاد أي
     * نتيجة (لا كلمة من السؤال طابقت شيئاً في الموسوعة)، سبب شائع فعلياً هو
     * خطأ إملائي بسيط في اسم العشبة نفسها (حرف ناقص/زائد/مبدَّل) لا غياب
     * حقيقي للمعلومة. بدل الاكتفاء برسالة "لم أجد" عامة، تُقارَن كلمات
     * السؤال بأسماء كل أعشاب الموسوعة عبر تشابه الثلاثيات الحرفية
     * (Trigram Jaccard) — مقياس رخيص لا يحتاج أي قاموس، ويتحمّل فروقاً
     * إملائية بسيطة بعكس المطابقة الحرفية التامة. يُقترَح اسم فقط إن تجاوز
     * التشابه عتبة معقولة (0.4) تكفي لالتقاط خطأ حرف أو اثنين في كلمة
     * متوسطة الطول دون اقتراح أسماء لا علاقة لها بالسؤال أصلاً.
     */
    private fun suggestSimilarHerbNames(question: String, herbs: List<Herb>): List<String> {
        val qWords = normalize(question).split(Regex("\\s+")).filter { it.length > 2 }
        if (qWords.isEmpty()) return emptyList()
        val qTrigrams = qWords.map { trigramsOf(it) }
        val scored = herbs.mapNotNull { herb ->
            val nameWords = normalize(herb.name).split(Regex("[\\s,()]+")).filter { it.length > 2 }
            if (nameWords.isEmpty()) return@mapNotNull null
            val best = nameWords.maxOf { nw ->
                val nwTrigrams = trigramsOf(nw)
                qTrigrams.maxOf { qt -> jaccard(qt, nwTrigrams) }
            }
            if (best >= 0.4) herb.name to best else null
        }
        return scored.sortedByDescending { it.second }.take(2).map { it.first }
    }

    private fun fallbackHelp(herbs: List<Herb>, question: String = ""): String {
        val suggestions = if (question.isNotBlank()) suggestSimilarHerbNames(question, herbs) else emptyList()
        val suggestionLine = if (suggestions.isNotEmpty())
            "\n\nهل تقصد ${suggestions.joinToString(" أو ")}؟ جرّب السؤال باسمها هكذا."
        else ""
        return if (herbs.size <= 3)
            "لم أجد إجابة مباشرة لسؤالك ضمن بيانات ${herbNames(herbs)}. جرّب أن تسأل عن: الفوائد، الاستخدام، التحذيرات، أو الفرق بينها إن ذكرت أكثر من عشبة." + suggestionLine
        else
            "لم أجد إجابة مباشرة لسؤالك في الموسوعة. جرّب ذكر اسم عشبة معيّنة، أو اسأل عن أعراض/فائدة محددة تبحث عن عشبة لها." + suggestionLine
    }
}
