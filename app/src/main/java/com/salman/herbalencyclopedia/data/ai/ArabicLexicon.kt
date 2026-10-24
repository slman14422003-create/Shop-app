package com.salman.herbalencyclopedia.data.ai

/**
 * "قاموس" سيمو للغة العربية — بديل محلي وخفيف عن ملف قاموس ضخم يحوي كل
 * الكلمات: بدل حفظ مئات آلاف الكلمات (وهو ما يحتاج تحميل ملف كبير من
 * الإنترنت لا يمكن تضمينه هنا فعلياً)، هذا الصف يطبّق "تجذيراً خفيفاً"
 * (light stemming) يزيل أشهر السوابق واللواحق العربية عن أي كلمة عربية —
 * فيتعرّف عملياً على *كل* الكلمات العربية بصيغها المختلفة (جمع، تأنيث،
 * أدوات تعريف، ضمائر متصلة...) دون الحاجة لسردها واحدة واحدة، وهذا مجاني
 * بالكامل ويعمل بلا إنترنت.
 *
 * يُستخدم هذا فقط لتوسيع كلمات سؤال المستخدم في البحث الحر (إضافة احتمالات
 * أكثر للمطابقة)، ولا يستبدل [normalize]/wordsOf الأساسية في باقي التطبيق
 * (المقارنة المنظمة مثلاً) تفادياً لأي أثر جانبي غير مقصود على ميزات تعمل
 * أصلاً بشكل جيد.
 *
 * ملاحظة ترخيص مهمة: طُلب فحص مشاريع تجذيع عربية جاهزة (Tashaphyne،
 * Arabic-Stemmers/ARLSTem) للاستفادة منها هنا. كلا المشروعين مرخَّصان
 * بـ GPLv2/GPLv3 — نسخ أكوادهما أو قوائم زوائدهما حرفياً داخل هذا التطبيق
 * (غير المرخَّص GPL) يُلزم قانونياً بفتح مصدر الجزء المتأثر (وربما التطبيق
 * كاملاً) بنفس الترخيص، وهو التزام لا رجعة فيه لا ينبغي فرضه دون قرار
 * صريح منك. لذلك القوائم أدناه *لم تُنسخ* من أي منهما؛ هي إعادة كتابة
 * كاملة مبنية على قواعد الصرف العربي العامة المعروفة أكاديمياً (بادئات
 * حروف الجر/العطف + أداة التعريف، ولواحق الضمائر المتصلة وعلامات الجمع/
 * التثنية) — نفس الفكرة العلمية العامة (غير قابلة للحماية بحقوق نشر) دون
 * أي التزام ترخيصي جديد على المشروع.
 *
 * التحسين الفعلي المستفاد من مراجعة تلك المشاريع: تطبيق التجذيع على
 * مرحلتين متتاليتين (سابقة ثم لاحقة) بدل جهة واحدة فقط، مع اختيار *أطول*
 * سابقة/لاحقة مطابقة فعلياً (لا أول مطابقة حسب ترتيب القائمة) — فكلمة مثل
 * "وبالأعشاب" (و + بال + أعشاب) أو "لأدويتهم" (ل + أدوية + هم) تحتاج قص
 * الجهتين معاً، وبأطول زائدة مركّبة ممكنة، لتصل لجذر مفيد فعلاً للمطابقة.
 */
internal object ArabicLexicon {

    private val prefixes = listOf(
        // زوائد مركّبة (حرف عطف/جر + أداة تعريف) يجب اختبارها كوحدة واحدة
        // حتى لا يُقصّ جزء منها فقط ويبقى الباقي ملتصقاً بالكلمة خطأً.
        "فبال", "وبال", "كبال", "وكال", "فكال", "وسال", "فسال",
        "بال", "كال", "فال", "وال", "سال", "لل",
        "ال", "بـ", "كـ", "فـ", "لـ", "وـ",
        "و", "ف", "ب", "ك", "ل"
    )

    private val suffixes = listOf(
        // ضمائر متصلة مركّبة مع جمع مؤنث سالم أولاً (أطول فأطول)، ثم
        // لواحق مفردة، حتى يختار stripSuffix أطول تطابق فعلي دوماً.
        "اتهما", "اتكما",
        "اتها", "اتهم", "اتهن", "اتكم", "اتكن", "اتنا", "اتك", "اتي",
        "ياتي", "كما", "هما", "تين", "ات", "ون", "ين", "ان",
        "ية", "تي", "كم", "كن", "هم", "هن", "ها", "نا", "ني", "ه", "ي", "ة"
    )

    /** أقل طول للكلمة بعد التجذير كي لا تختفي كلمات قصيرة أصلاً (تفادي الإفراط في القص). */
    private const val MIN_STEM_LENGTH = 2

    /** يزيل أطول سابقة معروفة تُطابق فعلياً بداية الكلمة (لا أول مطابقة حسب ترتيب القائمة). */
    private fun stripPrefix(word: String): String {
        val prefix = prefixes
            .filter { word.startsWith(it) && word.length - it.length >= MIN_STEM_LENGTH }
            .maxByOrNull { it.length }
        return if (prefix != null) word.removePrefix(prefix) else word
    }

    /** نظير [stripPrefix] للاحقة: أطول لاحقة معروفة تُطابق فعلياً نهاية الكلمة. */
    private fun stripSuffix(word: String): String {
        val suffix = suffixes
            .filter { word.endsWith(it) && word.length - it.length >= MIN_STEM_LENGTH }
            .maxByOrNull { it.length }
        return if (suffix != null) word.removeSuffix(suffix) else word
    }

    /**
     * يعيد جذراً تقريبياً واحداً للكلمة، بعد إزالة أطول سابقة مطابقة ثم
     * أطول لاحقة مطابقة على التوالي (مرحلتان، لا مرحلة واحدة فقط كما كان
     * سابقاً) — يغطي كلمات ملتصقة بزائدتين معاً من الجهتين. يعيد الكلمة
     * كما هي إذا كانت قصيرة أصلاً أو لم يوجد ما يُزال.
     */
    fun lightStem(word: String): String {
        if (word.length <= MIN_STEM_LENGTH + 1) return word
        val w = stripSuffix(stripPrefix(word))
        return w.ifBlank { word }
    }

    /** يوسّع مجموعة كلمات (إضافة الجذور التقريبية لها) دون حذف الكلمات الأصلية. */
    fun expand(words: Set<String>): Set<String> {
        if (words.isEmpty()) return words
        val stems = words.map { lightStem(it) }.filter { it.length >= MIN_STEM_LENGTH }
        return words + stems
    }

    // ═══════════════════════════════════════════════════════════════════
    // إضافات (تحسين البحث والفهم) — لا تُغيّر سلوك [lightStem]/[expand]
    // القديمين عمداً: [HerbAssistant.learningKey] (معرّف مستندات التعلّم
    // المشترك في Firestore) يعتمد عليهما، وتغييرهما كان سيُغيّر المعرّفات
    // ويفصل أصوات الحالات المتعلَّمة سابقاً عن الجديدة ويكسر التصويت السلبي.
    // ═══════════════════════════════════════════════════════════════════

    private val DIACRITICS_RE = Regex("[\\u064B-\\u0652\\u0640]")
    private val NON_WORD_RE = Regex("[^\\p{L}\\p{N}\\s]")
    private val PAREN_RE = Regex("[\\(\\[][^)\\]]*[\\)\\]]")
    private val SPACES_RE = Regex("\\s+")

    /**
     * تطبيع عربي موحَّد (يستخدمه سيمو والبحث المباشر معاً كي يتطابقا دوماً):
     * إزالة التشكيل والتطويل، توحيد الألف/الياء/التاء المربوطة/الكاف الفارسية،
     * تحويل الأرقام العربية-الهندية إلى لاتينية (كي يلتقط \d أعمار مثل "٣ سنوات")،
     * إزالة الرموز، ثم تصغير الحروف اللاتينية. Regex مُصرَّح عنها مرة واحدة هنا
     * بدل إنشائها من جديد في كل استدعاء (كانت تُنشأ عشرات آلاف المرات في البحث).
     */
    fun normalizeText(text: String): String {
        if (text.isEmpty()) return text
        val noMarks = DIACRITICS_RE.replace(text, "")
        val sb = StringBuilder(noMarks.length)
        for (ch in noMarks) {
            sb.append(
                when (ch) {
                    'أ', 'إ', 'آ' -> 'ا'
                    'ى', 'ی' -> 'ي'
                    'ة' -> 'ه'
                    'ک' -> 'ك'
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    else -> ch
                }
            )
        }
        return NON_WORD_RE.replace(sb.toString(), " ").trim().lowercase()
    }

    /** الاسم العربي الفعلي لعشبة: يحذف ما بين الأقواس والكلمات اللاتينية (الاسم العلمي/الإنجليزي). */
    fun arabicNameCore(name: String): String {
        val withoutParens = name.replace(PAREN_RE, " ")
        val arabicOnly = withoutParens.split(SPACES_RE)
            .filter { it.isNotBlank() }
            .filterNot { token -> token.none { ch -> ch in '\u0600'..'\u06FF' } }
        return arabicOnly.joinToString(" ").ifBlank { name }
    }

    /** يزيل "ال" التعريف من بداية كلمة (إن بقي بعدها حرفان على الأقل). */
    fun stripAl(word: String): String =
        if (word.startsWith("ال") && word.length - 2 >= 2) word.substring(2) else word

    // سوابق ولواحق عربية شائعة بعد التطبيع (ة→ه، ى→ي، أإآ→ا) — لا تُنسخ من أي مشروع
    // GPL (انظر ملاحظة الترخيص أعلى الملف)، بل قواعد صرفية عامة.
    /**
     * أسماء بديلة/عامية/أجنبية شائعة لنفس العشبة (كل مجموعة = عشبة واحدة)، مشتركة
     * الآن بين سيمو ([com.salman.herbalencyclopedia.data.ai.HerbAssistant]) ومحرك
     * البحث المباشر ([com.salman.herbalencyclopedia.data.search.HerbSearch]) —
     * قائمة واحدة بدل نسختين كانتا لتنحرفا عن بعضهما مع أي تحديث مستقبلي. القيم
     * هنا نصوص خام غير مُطبَّعة عمداً؛ كل مستخدم للقائمة يطبّعها بدالة normalize
     * الخاصة به (كلا الملفين يعتمدان فعلياً على [normalizeText] أعلاه).
     */
    val herbAliasGroups: List<List<String>> = listOf(
        listOf("حبة البركة", "الحبة السوداء", "حبة سوداء", "الشونيز", "nigella", "black seed"),
        listOf("يانسون", "ينسون", "أنيسون", "انيسون", "anise", "aniseed"),
        listOf("قرفة", "دارسين", "cinnamon"),
        listOf("كركديه", "كركدية", "ورد النيل", "hibiscus"),
        listOf("بابونج", "بابونك", "كاموميل", "chamomile", "camomile", "matricaria"),
        listOf("نعناع", "نعنع", "mint", "peppermint"),
        listOf("زنجبيل", "جنزبيل", "ginger", "zingiber"),
        listOf("كمون", "cumin"),
        listOf("كزبرة", "كسبرة", "coriander"),
        listOf("حلبة", "fenugreek"),
        listOf("شمر", "شومر", "fennel"),
        listOf("مريمية", "ميرمية", "مرمرية", "salvia", "sage"),
        listOf("زعتر", "صعتر", "thyme", "thymus"),
        listOf("إكليل الجبل", "اكليل الجبل", "روزماري", "rosemary"),
        listOf("خزامى", "لافندر", "lavender"),
        listOf("قرنفل", "clove"),
        listOf("هيل", "حبهان", "cardamom"),
        listOf("زعفران", "saffron"),
        listOf("كركم", "تورمريك", "turmeric", "curcuma"),
        listOf("ثوم", "garlic"),
        listOf("عرق سوس", "سوس", "licorice", "liquorice"),
        listOf("ريحان", "حبق", "basil"),
        listOf("كينا", "أوكالبتوس", "اوكاليبتوس", "eucalyptus"),
        listOf("جنسنغ", "جينسنغ", "ginseng"),
        listOf("كراوية", "كراويا", "caraway"),
        listOf("لسان الحمل", "قطونة", "بزر قطونة", "plantain", "psyllium"),
        listOf("بذور الكتان", "بزر الكتان", "كتان", "flaxseed", "flax"),
        listOf("شيح", "artemisia", "wormwood"),
        listOf("ورق الغار", "غار", "laurel", "bay leaf"),
        listOf("هندباء", "هندبا", "chicory", "dandelion"),
        listOf("حشيشة الليمون", "عشبة الليمون", "ليمون غراس", "lemongrass"),
        listOf("عناب", "jujube"),
        listOf("لبان", "لبان دكر", "frankincense"),
        listOf("سنا مكي", "سنامكي", "senna"),
        listOf("صبار", "ألوفيرا", "الوفيرا", "aloe vera", "aloe"),
        listOf("مورينجا", "مورنجا", "moringa"),
        listOf("بادرنجبويه", "ترنجان", "melissa", "lemon balm"),
        listOf("شبت", "dill"),
        listOf("عرعر", "juniper"),
        listOf("تمر هندي", "tamarind"),
        listOf("جنكة", "جنكو", "ginkgo")
    )

    private val formPrefixes = listOf(
        "وبال", "فبال", "كبال", "وكال", "فكال", "وال", "فال", "بال", "كال", "لل", "ال",
        "و", "ف", "ب", "ك", "ل"
    )
    private val formSuffixes = listOf(
        "اتهما", "اتكما", "اتها", "اتهم", "اتهن", "اتكم", "اتكن", "اتنا", "اتك", "اتي",
        "هما", "كما", "تين", "ات", "ون", "ين", "ان", "يه", "ته", "تها", "تهم", "ها", "هم", "هن",
        "كم", "كن", "نا", "ني", "ه", "ي"
    )

    private val formsCache = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    /**
     * "أشكال" كلمة مُطبَّعة: الكلمة نفسها + كل صيغها بعد إزالة سابقة صالحة و/أو
     * لاحقة صالحة (بشرط ألا يقل الباقي عن ٣ أحرف). الفكرة: مطابقة **كلمة بكلمة**
     * عبر تقاطع مجموعتي أشكال (الزنجبيل ↔ زنجبيل، وبالأعشاب ↔ أعشاب، فوائدها ↔ فوائد)
     * بدل المطابقة بسلسلة فرعية حرفية التي كانت تُنتج نتائج غير مرتبطة (مثال:
     * الجذر "مون" المستخرج من "كمون" كان يُطابق "ليمون" فيظهر الليمون جواباً عن الكمون).
     * النتيجة مخزَّنة مؤقتاً لأن مفردات الموسوعة محدودة.
     */
    fun formsOf(token: String): Set<String> {
        if (token.length < 3) return setOf(token)
        formsCache[token]?.let { return it }
        val afterPrefix = LinkedHashSet<String>()
        afterPrefix += token
        for (p in formPrefixes) {
            if (token.startsWith(p) && token.length - p.length >= 3) afterPrefix += token.substring(p.length)
        }
        val out = LinkedHashSet<String>(afterPrefix)
        for (base in afterPrefix) {
            for (s in formSuffixes) {
                if (base.endsWith(s) && base.length - s.length >= 3) out += base.substring(0, base.length - s.length)
            }
        }
        val result: Set<String> = out
        if (formsCache.size > 60_000) formsCache.clear()
        formsCache[token] = result
        return result
    }

    /** يوسّع مجموعة كلمات بكل أشكالها ([formsOf]) دون حذف الأصل. */
    fun expandForms(words: Collection<String>): Set<String> {
        if (words.isEmpty()) return emptySet()
        val out = LinkedHashSet<String>()
        for (w in words) out += formsOf(w)
        return out
    }

    /**
     * مسافة تحرير (Levenshtein) بسقف: تعيد `max + 1` فور تجاوز السقف (توقف مبكر)،
     * فتبقى رخيصة حتى عند مسح آلاف كلمات المفردات لاقتراح تصحيح إملائي.
     */
    fun editDistanceAtMost(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
