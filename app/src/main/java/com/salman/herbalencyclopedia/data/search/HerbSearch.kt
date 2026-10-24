package com.salman.herbalencyclopedia.data.search

import com.salman.herbalencyclopedia.data.ai.ArabicLexicon
import com.salman.herbalencyclopedia.data.ai.DictionaryLexicon
import com.salman.herbalencyclopedia.data.ai.HerbCategoryLookup
import com.salman.herbalencyclopedia.data.model.Herb

/**
 * محرك البحث المباشر عن الأعشاب (شاشة "كل الأعشاب" وشاشة "بحث" — مختلف عن
 * البحث الحر لسيمو في [com.salman.herbalencyclopedia.data.ai.HerbAssistant]
 * الذي يجيب بجمل كاملة). كان البحث سابقاً `it.name.contains(query)` حرفياً:
 * لا يطابق "الزعتر" مع "زعتر" (أداة التعريف)، ولا "زنجبيل" مع "الزّنجبيل"
 * (تشكيل)، ولا يفهم اسماً بديلاً أو خطأ إملائياً بسيطاً — وهذا بالضبط سبب
 * عجز البحث عن إيجاد أعشاب موجودة فعلاً في الموسوعة.
 *
 * هذا المحرك محلي بالكامل (بلا إنترنت، بلا مفتاح API، بلا تكلفة، يعمل دوماً
 * حتى بلا اتصال — "مضمون ومجاني" تماماً كباقي التطبيق) ويحسّن المطابقة عبر
 * عدة مراحل فوق المطابقة النصية المباشرة:
 * 1) **تطبيع عربي موحَّد** — يستخدم الآن [ArabicLexicon.normalizeText] نفسه
 *    المستخدم في سيمو (بدل نسخة محلية أضعف هنا كانت لا تُزيل التطويل ولا
 *    توحّد الياء/الكاف الفارسيتين)، فتُطابق "الزعتر" و"زعتر" و"الزّعتر"
 *    و"الــزعتر" ككلمة واحدة، وتبقى النتيجة متطابقة تماماً مع ما يفهمه سيمو.
 * 2) **أشكال الكلمة الصرفية** ([ArabicLexicon.formsOf]): يزيل سوابق ولواحق
 *    شائعة (لل/بال/ها/ين...) بدل جذع تقريبي واحد فقط، فتُطابق "للنوم" مع
 *    "النوم" مثلاً — بدل الاكتفاء بجذع واحد قد لا يطابق شيئاً.
 * 3) **أسماء بديلة معروفة** ([ArabicLexicon.herbAliasGroups]، نفس القائمة
 *    التي يعتمدها سيمو): "الشونيز" يجد "حبة البركة"، "ينسون" يجد "يانسون"…
 * 4) **توسيع بمرادفات القاموس** ([DictionaryLexicon]، من Rabih Dictionary
 *    وArabic WordNet): تبحث عن اسم بديل أو مرادف شائع لما كتبه المستخدم
 *    ضمن اسم العشبة أو نصوصها، لا حرفياً فقط.
 * 5) **تصنيف العشبة جزء من نصها القابل للبحث** ([HerbCategoryLookup]): سؤال
 *    عن اسم فئة كاملة ("أعشاب الجهاز الهضمي") يظهر أعشاب تلك الفئة حتى لو لم
 *    يحوِ نصها الفعلي هذه الكلمات بالذات.
 *
 * الترتيب: مطابقة الاسم أولاً (الأهم للمستخدم)، ثم مطابقة داخل نصوص العشبة
 * (الفوائد/الاستخدام/الملاحظات/التصنيف)، بحيث تظهر أدق النتائج أولاً بدل
 * ترتيب عشوائي.
 */
object HerbSearch {

    private val SPACES = Regex("\\s+")

    /**
     * تطبيع موحَّد مع سيمو: مفوَّض بالكامل الآن لـ[ArabicLexicon.normalizeText]
     * بدل نسخة محلية مكرَّرة كانت أضعف (لا تُزيل التطويل "ـ"، ولا تحوّل الأرقام
     * العربية-الهندية، ولا توحّد "ی"/"ک" الفارسيتين مع "ي"/"ك" العربيتين).
     * إبقاء الاسم `normalize` واستمرار كونها `fun` عامة يحافظ على توافق أي كود
     * خارجي يستدعيها مباشرة (مثال: [findMatchSnippet] وشاشات العرض).
     */
    fun normalize(text: String): String = ArabicLexicon.normalizeText(text)

    private fun tokens(normalizedText: String): List<String> =
        normalizedText.split(SPACES).filter { it.length > 1 }

    /** هل يُعتبر [a] و[b] "نفس الكلمة تقريباً"؟ تطابق تام دوماً، أو احتواء فرعي
     * فقط إن كان طرفا الاحتواء ≥ 3 أحرف — يمنع كلمة قصيرة (حرفين) من "اختطاف"
     * أي كلمة أطول تحويها بالصدفة (كان هذا يُنتج نتائج بحث غير مرتبطة إطلاقاً). */
    private fun looseWordMatch(a: String, b: String): Boolean =
        a == b || (a.length >= 3 && b.length >= 3 && (a.contains(b) || b.contains(a)))

    private data class Scored(
        val herb: Herb,
        val score: Int,
        val matchedByName: Boolean,
        val matchLabel: String? = null,
        val matchSnippet: String? = null
    )

    private fun categoryOf(herb: Herb): String =
        herb.categoryId?.let { HerbCategoryLookup.categoryNames[it] } ?: ""

    /** حقول العشبة النصية (غير الاسم)، بترتيب الأولوية عند شرح "أين وُجدت المطابقة" —
     * الفوائد أولاً لأنها الأكثر أهمية للمستخدم، ثم الاستخدام، فالتحذيرات/الأضرار،
     * فالملاحظات، وأخيراً التصنيف (قدرة جديدة: راجع توثيق [HerbCategoryLookup]
     * أعلى الملف). الاسم العربي في كل زوج هو ما يُعرض فعلياً في واجهة نتائج البحث. */
    private val LABELED_FIELDS: List<Pair<String, (Herb) -> String>> = listOf(
        "الفوائد" to Herb::benefits,
        "طريقة الاستخدام" to Herb::usage,
        "التحذيرات" to Herb::warnings,
        "الأضرار" to Herb::harms,
        "ملاحظات" to Herb::notes,
        "التصنيف" to ::categoryOf
    )

    private const val SNIPPET_MAX_LEN = 90

    /**
     * عندما تُطابق عشبة بحثاً ما دون أن يحتوي اسمها على أي جزء من الاستعلام (أي أن
     * المطابقة جاءت من الفوائد/الاستخدام/التحذيرات/التصنيف إلخ)، يبحث هذا عن أول
     * حقل يحتوي فعلياً كلمة من الاستعلام (أو مرادفها) ويقتطع منه مقطعاً قصيراً —
     * كي تشرح واجهة البحث للمستخدم *لماذا* ظهرت هذه العشبة، بدل عرضها بلا تفسير.
     */
    private fun findMatchSnippet(herb: Herb, qNorm: String, expandedTokens: Set<String>): Pair<String, String>? {
        for ((label, getter) in LABELED_FIELDS) {
            val raw = getter(herb)
            if (raw.isBlank()) continue
            val norm = normalize(raw)
            val matches = norm.contains(qNorm) || tokens(norm).any { ft ->
                expandedTokens.any { et -> looseWordMatch(et, ft) }
            }
            if (matches) {
                val snippet = if (raw.length > SNIPPET_MAX_LEN) raw.take(SNIPPET_MAX_LEN).trimEnd() + "…" else raw
                return label to snippet
            }
        }
        return null
    }

    /**
     * تطبيع/تقطيع كل نصوص عشبة واحدة، محسوبة مرة واحدة فقط لكل عشبة (انظر
     * [indexFor] أدناه) بدل إعادة حسابها من الصفر مع كل ضغطة حرف في مربع
     * البحث. مشكلة أداء حقيقية كانت هنا: [search] يُستدعى من واجهة البحث
     * الفوري (مربع بحث يُعيد الفلترة مع كل حرف يكتبه المستخدم)، وكانت
     * `normalize()` — التي تُجري عدة عمليات Regex.replace على نص — تُطبَّق
     * من جديد على *كل حقول كل عشبة مجتمعة* (الفوائد + الاستخدام + التحذيرات
     * + الأضرار + الملاحظات) عند كل ضغطة حرف لكل عشبة لم يُطابق اسمها
     * الاستعلام مباشرة — وهذا يشمل غالبية الأعشاب في أي بحث نموذجي (يبحث
     * الناس عادة بعرَض/فائدة لا باسم العشبة). النتيجة: كلفة تتضاعف مع طول
     * الموسوعة، مكرَّرة بلا داعٍ لأن نصوص العشبة نفسها لا تتغيّر بين ضغطتي
     * حرف متتاليتين إطلاقاً. تصنيف العشبة (اسم الفئة) صار الآن جزءاً من
     * `fieldsNorm`/`fieldTokens` أيضاً — قدرة جديدة، راجع توثيق الملف أعلاه.
     */
    private data class HerbIndex(
        val nameNorm: String,
        val nameTokens: List<String>,
        val fieldsNorm: String,
        val fieldTokens: Set<String>
    )

    // ذاكرة تخزين مؤقت بنفس أسلوب [HerbAssistant] (فحص مرجع القائمة، لا
    // محتواها — تُنشئ شاشات التطبيق قائمة جديدة فقط عند تغيّر فعلي للبيانات
    // من Firestore، فمقارنة المرجع (`===`) كافية ورخيصة لاكتشاف "لا تغيير").
    // يُفرَّغ الفهرس أيضاً عند تغيّر خريطة أسماء التصنيفات (قد تصل من Firestore
    // بعد الأعشاب نفسها) كي لا يبقى فهرس مبني قبل معرفة اسم أي تصنيف.
    private var cachedHerbsRef: List<Herb>? = null
    private var cachedCategoriesRef: Map<String, String>? = null
    private var cachedIndexById: Map<String, HerbIndex> = emptyMap()

    private fun indexFor(herbs: List<Herb>): Map<String, HerbIndex> {
        val categories = HerbCategoryLookup.categoryNames
        if (cachedHerbsRef === herbs && cachedCategoriesRef === categories) return cachedIndexById
        val built = herbs.associate { herb ->
            val nameNorm = normalize(herb.name)
            val fieldsNorm = normalize(
                herb.benefits + " " + herb.usage + " " + herb.warnings + " " +
                    herb.harms + " " + herb.notes + " " + categoryOf(herb)
            )
            herb.id to HerbIndex(
                nameNorm = nameNorm,
                nameTokens = tokens(nameNorm),
                fieldsNorm = fieldsNorm,
                fieldTokens = tokens(fieldsNorm).toSet()
            )
        }
        cachedHerbsRef = herbs
        cachedCategoriesRef = categories
        cachedIndexById = built
        return built
    }

    /**
     * أسماء بديلة/عامية معروفة لنفس العشبة ([ArabicLexicon.herbAliasGroups]،
     * نفس القائمة التي يعتمدها سيمو). عند ذكر أي اسم من مجموعة، تُضاف كلمات
     * كل الأسماء الأخرى بنفس المجموعة إلى كلمات البحث الموسَّعة — فيُطابق
     * "الشونيز" اسمَ "حبة البركة" رغم اختلافهما تماماً حرفياً.
     */
    private val normalizedAliasGroups: List<List<String>> by lazy {
        ArabicLexicon.herbAliasGroups.map { group -> group.map { normalize(it) }.filter { it.isNotBlank() } }
    }

    private fun expandWithAliases(base: Set<String>): Set<String> {
        if (base.isEmpty()) return base
        val out = LinkedHashSet<String>(base)
        for (group in normalizedAliasGroups) {
            val mentioned = group.any { member ->
                val memberTokens = tokens(member)
                if (memberTokens.size <= 1) member in base else base.containsAll(memberTokens)
            }
            if (mentioned) for (member in group) out.addAll(tokens(member))
        }
        return out
    }

    /** نتيجة بحث واحدة: العشبة نفسها، وهل طابقتها بالاسم مباشرة، وإن لم يكن كذلك —
     * أين وُجدت المطابقة فعلاً (أي حقل) ومقطع قصير منه، لعرضه في واجهة البحث بدل ترك
     * المستخدم بلا تفسير لسبب ظهور عشبة لا يحتوي اسمها على ما بحث عنه. */
    data class HerbSearchResult(
        val herb: Herb,
        val matchedByName: Boolean,
        val matchLabel: String? = null,
        val matchSnippet: String? = null
    )

    /**
     * يبحث عن كل الأعشاب المطابقة للاستعلام، مرتّبة تنازلياً حسب دقة
     * المطابقة. يعيد قائمة فارغة إن كان الاستعلام فارغاً (بدل كل الأعشاب)،
     * كي تستمر شاشات البحث بعرض "اكتب للبحث" كما كانت.
     *
     * محسَّن على عدة جبهات فوق النسخة السابقة (كانت تتطلب مطابقة الجملة
     * كاملة حرفياً ضمن نصوص العشبة، فتفشل مع أي سؤال طبيعي متعدد الكلمات):
     * 1) **توسيع الكلمات بأشكالها الصرفية** ([ArabicLexicon.formsOf] بدل جذع
     *    تقريبي واحد فقط) + **أسماء بديلة معروفة** ([expandWithAliases]) +
     *    مرادفات القاموس الخارجي، فتُطابق "للنوم" مع "النوم"، و"الشونيز" مع
     *    "حبة البركة"، و"بينفع" مع "نافع".
     * 2) **مطابقة على مستوى الكلمات لا الجملة كاملة**: يُحسب عدد كلمات
     *    الاستعلام (بعد التوسيع) الموجودة فعلياً في اسم العشبة أو نصوصها،
     *    وتُرجَّح النتيجة حسب *نسبة* الكلمات المطابقة، بدل اشتراط وجود
     *    الجملة بالضبط.
     * 3) **احتواء جزئي آمن**: أي مطابقة "تحتوي إحداها الأخرى" (لالتقاط جمع/
     *    تصغير بسيط) تشترط الآن ≥ 3 أحرف بالطرفين ([looseWordMatch]) — كلمة
     *    من حرفين كانت تُطابق أي كلمة أطول تحويها بالصدفة فتُظهر نتائج لا
     *    علاقة لها بالبحث إطلاقاً.
     * 4) **تسامح مع خطأ إملائي بسيط** في اسم العشبة، بسقف مسافة تحرير يتّسع
     *    قليلاً مع طول الكلمة (حرف واحد للكلمات القصيرة، حرفان للكلمات من ٧
     *    أحرف فأكثر) عبر [ArabicLexicon.editDistanceAtMost] المحدود مسبقاً —
     *    بدل حساب DP كامل دوماً بسقف واحد يساوي ١ مهما طال اسم العشبة.
     * 5) **فهرسة مُخزَّنة مؤقتاً لكل عشبة** ([indexFor]) بدل إعادة تطبيع كل
     *    نصوصها من الصفر مع كل استدعاء — أهم تحسين أداء هنا، انظر توثيق
     *    [HerbIndex].
     * 6) **تصنيف العشبة قابل للبحث** (راجع توثيق الملف أعلاه و[categoryOf]).
     * 7) **شرح المطابقة**: عندما تُطابق عشبة بحثاً عبر حقولها الأخرى لا
     *    اسمها، تحمل نتيجتها ([HerbSearchResult.matchLabel]/[matchSnippet])
     *    الحقل الفعلي الذي وُجدت فيه المطابقة، بدل تركها بلا تفسير في
     *    الواجهة. أعشاب الاسم المطابق تبقى دوماً أعلى الترتيب (نقاطها
     *    ≥30 مقابل ≤20 كحدّ أقصى لمطابقة الحقول وحدها) فتظهر أولاً كما هي.
     */
    fun search(query: String, herbs: List<Herb>): List<HerbSearchResult> {
        val qNorm = normalize(query)
        if (qNorm.isBlank()) return emptyList()

        val qTokens = tokens(qNorm)
        if (qTokens.isEmpty()) return emptyList()

        val withForms = qTokens.toSet() + qTokens.flatMap { ArabicLexicon.formsOf(it) }
        val withAliases = expandWithAliases(withForms)
        val dictSynonyms = withAliases.flatMap { DictionaryLexicon.synonymsOf(it) }.toSet()
        val expandedTokens = withAliases + dictSynonyms

        val index = indexFor(herbs)
        val scored = herbs.mapNotNull { herb ->
            val idx = index[herb.id] ?: return@mapNotNull null
            val nameNorm = idx.nameNorm
            val nameTokens = idx.nameTokens
            var score = 0

            when {
                nameNorm.isBlank() -> {}
                nameNorm == qNorm -> score = 100
                nameNorm.contains(qNorm) || qNorm.contains(nameNorm) -> score = 70
                expandedTokens.any { it.length >= 3 && nameNorm.contains(it) } -> score = 45
                else -> {
                    // مطابقة جزئية على مستوى الكلمات بين اسم العشبة والاستعلام
                    // الموسّع: كل كلمة مشتركة (أو تحتوي إحداهما الأخرى، لالتقاط
                    // جمع/تصغير بسيط، بشرط ≥ 3 أحرف — راجع [looseWordMatch])
                    // ترفع الدرجة تدريجياً بدل رفض النتيجة كلياً.
                    val overlap = nameTokens.count { nt -> expandedTokens.any { et -> looseWordMatch(et, nt) } }
                    if (overlap > 0) score = 30 + (overlap * 6).coerceAtMost(20)
                }
            }

            // تسامح مع خطأ إملائي بسيط في الاسم (حرف مبدَّل/زائد/ناقص) فقط إن
            // فشلت كل المطابقات النصية والمرادفات أعلاه. السقف يتّسع مع طول
            // الكلمة كي لا يبقى اسم عشبة طويل صعب التصحيح بسقف حرف واحد فقط.
            if (score == 0 && qTokens.size == 1 && qNorm.length >= 3) {
                val limit = if (qNorm.length >= 7) 2 else 1
                val hasCloseTypo = nameTokens.any { nt ->
                    nt.length >= 3 && kotlin.math.abs(nt.length - qNorm.length) <= limit &&
                        ArabicLexicon.editDistanceAtMost(nt, qNorm, limit) <= limit
                }
                if (hasCloseTypo) score = 55
            }

            val matchedByName = score > 0

            if (score == 0) {
                if (idx.fieldsNorm.contains(qNorm)) {
                    score = 20
                } else {
                    val matchedCount = expandedTokens.count { et ->
                        et.length >= 2 && idx.fieldTokens.any { looseWordMatch(et, it) }
                    }
                    if (matchedCount > 0) {
                        // كلما زادت نسبة كلمات الاستعلام المطابَقة فعلياً داخل
                        // نصوص العشبة، ارتفع ترتيبها — عدد الكلمات المطابقة
                        // وحده لا يكفي مؤشراً بدون أخذ طول السؤال بالحسبان.
                        val ratio = matchedCount.toDouble() / qTokens.size.coerceAtLeast(1)
                        score = (6 + ratio * 14).toInt().coerceAtLeast(6)
                    }
                }
            }

            if (score == 0) return@mapNotNull null

            val (matchLabel, matchSnippet) = if (!matchedByName) {
                findMatchSnippet(herb, qNorm, expandedTokens) ?: (null to null)
            } else null to null

            Scored(herb, score, matchedByName, matchLabel, matchSnippet)
        }

        return scored.sortedByDescending { it.score }.map {
            HerbSearchResult(it.herb, it.matchedByName, it.matchLabel, it.matchSnippet)
        }
    }
}
