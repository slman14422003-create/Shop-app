package com.shopmanager.app.assistant.engine

/**
 * نماذج محرك الفهم المحلي (المساعد المحلي). الحزمة كلها Kotlin نقي بلا أي اعتماد على
 * أندرويد حتى تُختبر بوحدات اختبار JUnit عادية.
 */
enum class Intent {
    ADD_DEBT, PAY_DEBT, REDUCE_DEBT, DELETE_DEBT,
    QUERY_BALANCE, QUERY_TOTAL_DEBTS, QUERY_TOP_DEBTORS, QUERY_STALE_DEBTORS,
    ADD_SHORTAGE, REMOVE_SHORTAGE, LIST_SHORTAGES,
    QUERY_PRICE, SET_PRICE, ADD_MATERIAL, SEARCH_MATERIAL, RUN_MISSING_FINDER,
    SUMMARY, OPEN, ADD_NOTE, REMINDER, CALCULATE,
    UNDO, DELETE_LAST, REPEAT, HELP, GREETING, THANKS, CONFIRM, CANCEL, UNKNOWN,
    ADD_PERSON, RENAME_PERSON, DELETE_PERSON, SET_PHONE, DELETE_MATERIAL, RENAME_MATERIAL
}

enum class Slot { PERSON, AMOUNT, MATERIAL, TEXT, DATE }

enum class Screen { HOME, DEBTS, MATERIALS, NOTES, SETTINGS, ACTIVITY, MISSING_FINDER, CATALOG, ASSISTANT }

enum class Period { TODAY, WEEK, MONTH }

/** مستوى الثقة بمطابقة كيان مع قاعدة بيانات التطبيق. */
enum class MatchLevel { EXACT, HIGH, MEDIUM, NEW, NONE }

enum class AmbiguityKind { PERSON, MATERIAL, INTENT, FUZZY_PERSON, FUZZY_MATERIAL }

/** عميل معروف في قاعدة البيانات (لقطة تُبنى من الـ ViewModel، بلا Firestore داخل المحرك). */
data class KnownPerson(
    val id: String,
    val name: String,
    val balance: Double,
    /** أقدم دين لم يُسدَّد بعد (0 = لا شيء). */
    val oldestDebtAt: Long = 0L,
    val lastDebtAt: Long = 0L,
    /** متوسط الدين المعتاد لهذا الشخص (0 = لا تاريخ كافٍ). */
    val typicalAmount: Double = 0.0
)

data class KnownMaterial(
    val name: String,
    val price: Double? = null,
    val inShortage: Boolean = false
)

data class EngineSnapshot(
    val persons: List<KnownPerson> = emptyList(),
    val materials: List<KnownMaterial> = emptyList(),
    /** اسم بديل مُطبَّع -> الاسم الأصلي (يتعلمه المساعد من تصحيحات المستخدم). */
    val aliases: Map<String, String> = emptyMap(),
    val currency: String = "ل.س",
    val nowMillis: Long = System.currentTimeMillis(),
    val timeZoneId: String = "Asia/Damascus"
)

data class Candidate(val name: String, val id: String?, val score: Double)

data class PersonRef(
    /** النص كما كتبه المستخدم. */
    val rawSpan: String,
    val id: String?,
    val name: String,
    val score: Double,
    val level: MatchLevel,
    val candidates: List<Candidate> = emptyList(),
    val viaAlias: Boolean = false
)

data class MaterialRef(
    val rawSpan: String,
    val name: String,
    val score: Double,
    val level: MatchLevel,
    val quantity: Double? = null,
    /** إحدى وحدات التطبيق (كيلو، نص كيلو، ربع كيلو، لوقية...) أو null. */
    val unit: String? = null,
    /** كلمة تعبئة (كيس، علبة...) تُحفظ ضمن الملاحظة لأن وحدات التطبيق ثابتة. */
    val pack: String? = null,
    val candidates: List<Candidate> = emptyList(),
    val viaAlias: Boolean = false
)

data class DateRef(
    val startMillis: Long,
    val endMillis: Long? = null,
    val label: String,
    /** وقت محدد داخل اليوم (للتذكير) أو null. */
    val timeMillis: Long? = null
)

data class AmbiguityOption(val label: String, val value: String)

data class Ambiguity(
    val kind: AmbiguityKind,
    val question: String,
    val options: List<AmbiguityOption>
)

/** تعلّم اسم بديل بعد أن يؤكد المستخدم مطابقة تقريبية. */
data class AliasLearn(val alias: String, val canonical: String)

data class Command(
    val intent: Intent,
    val score: Double = 0.0,
    val rawText: String = "",
    val person: PersonRef? = null,
    val materials: List<MaterialRef> = emptyList(),
    val amount: Double? = null,
    val date: DateRef? = null,
    val text: String? = null,
    val screen: Screen? = null,
    val period: Period? = null,
    val limit: Int? = null,
    val expression: String? = null,
    val result: Double? = null,
    /** تسديد كامل. */
    val full: Boolean = false,
    val missing: List<Slot> = emptyList(),
    val confirmReasons: List<String> = emptyList(),
    /** حذف أو تعديل سعر أو مبلغ غير معتاد: تأكيد دائماً. */
    val risky: Boolean = false,
    val understood: String = "",
    val prompt: String? = null,
    val ambiguity: Ambiguity? = null,
    val aliasToLearn: AliasLearn? = null,
    val important: Boolean = false,
    /** الاسم الجديد عند تغيير اسم عميل أو مادة. */
    val newName: String? = null,
    val phone: String? = null
) {
    /** مكتمل ولا غموض فيه، جاهز لبطاقة التأكيد/التنفيذ. */
    val isReady: Boolean get() = missing.isEmpty() && ambiguity == null && intent != Intent.UNKNOWN

    /** أوامر القراءة فقط لا تغيّر أي بيانات. */
    val isReadOnly: Boolean
        get() = when (intent) {
            Intent.QUERY_BALANCE, Intent.QUERY_TOTAL_DEBTS, Intent.QUERY_TOP_DEBTORS,
            Intent.QUERY_STALE_DEBTORS, Intent.LIST_SHORTAGES, Intent.QUERY_PRICE,
            Intent.SEARCH_MATERIAL, Intent.SUMMARY, Intent.OPEN, Intent.CALCULATE,
            Intent.HELP, Intent.GREETING, Intent.THANKS, Intent.RUN_MISSING_FINDER,
            Intent.CONFIRM, Intent.CANCEL, Intent.UNKNOWN -> true
            else -> false
        }
}

data class ConversationContext(
    val lastPerson: PersonRef? = null,
    val lastMaterial: String? = null,
    val lastAmount: Double? = null,
    val lastCommand: Command? = null,
    /** أمر ناقص ينتظر جواب المستخدم على سؤال المتابعة. */
    val pending: Command? = null
)

data class EngineOutput(
    val commands: List<Command>,
    val context: ConversationContext,
    /** سطر "فهمت: ..." الجاهز للعرض. */
    val understood: String
)

/** نقطة توسعة لطبقة نموذج لغوي صغير اختيارية لاحقاً دون تغيير باقي الكود. */
fun interface NluFallback {
    fun interpret(text: String, snapshot: EngineSnapshot, context: ConversationContext): EngineOutput?
}
