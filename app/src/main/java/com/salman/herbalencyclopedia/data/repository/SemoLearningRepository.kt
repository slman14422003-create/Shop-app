package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.ai.TrainedExample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * يزامن "تعلّم سيمو الذاتي" (انظر [HerbAssistant.recordFeedback]) بين كل
 * الأجهزة عبر مجموعة "semo_learned" في Firestore، بدل بقائه محصوراً محلياً
 * على DataStore لكل جهاز على حدة كما كان الحال سابقاً (انظر
 * [PreferencesRepository]): كل حالة (سؤال↔رد) يتعلّمها سيمو من تقييم 👍 على
 * جهاز ما تُرفع هنا، وأي جهاز آخر يستمع لهذه المجموعة حياً فيدمجها فوراً
 * ضمن قائمته المحلية (انظر AppViewModel + [HerbAssistant.mergeLearnedExamples])
 * — أي أن التعلّم يحدث فعلياً "مرة واحدة" على مستوى كل مستخدمي التطبيق، لا
 * مرة مستقلة لكل جهاز من الصفر.
 *
 * معرّف كل مستند هو [HerbAssistant.learningKey] لنص السؤال (لا مُعرِّف
 * عشوائي)، فتتّحد حالتان بنفس المعنى الدلالي من جهازين مختلفين تحت نفس
 * المستند بدل تكراره، وترتفع بذلك "أصوات" (upvotes) الحالات المفيدة فعلاً
 * بدل تكديس نسخ شبه متطابقة منها.
 *
 * التصويت السلبي (👎) يزيد "downvotes" بدل حذف المستند مباشرة (الحذف محمي
 * للأدمن فقط في firestore.rules) — تفادياً لتمكين أي مستخدم مجهول الهوية
 * من محو معرفة مشتركة يعتمد عليها آخرون بضغطة واحدة. بدلاً من ذلك،
 * [observeSharedLearnedExamples] يستبعد أي حالة صارت أصواتها السلبية أكبر
 * من أو تساوي الإيجابية، فتخرج تلقائياً من التداول العام بعد تراكم تقييمات
 * سلبية كافية من مستخدمين حقيقيين، لا من تقييم سلبي واحد فقط.
 *
 * لا يوجد تسجيل دخول مطلوب هنا عمداً (نفس نمط مجموعة "feedback" الحالية):
 * هذه معرفة عامة عن أعشاب/خلطات الموسوعة نفسها (لا بيانات شخصية لأي
 * مستخدم)، وقواعد Firestore تتحقق من شكل كل مستند وتمنع تعديل نصّه بعد
 * إنشائه (انظر firestore.rules)، فأقصى ما يمكن أن يفعله مستخدم مسيء هو
 * التصويت لا التلاعب بالمحتوى.
 */
class SemoLearningRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection get() = db.collection("semo_learned")

    /** أقصى عدد حالات مشتركة تُسحب من الشبكة دفعة واحدة، بنفس فلسفة سقف [HerbAssistant]. */
    private companion object {
        const val REMOTE_LIMIT = 300L
        const val MIN_DISTINCT_VOTERS = 3
    }

    /**
     * بث حيّ لكل الحالات المشتركة "المقبولة" حالياً (باستبعاد ما تجاوزت
     * أصواته السلبية إيجابيّه)، مرتّبة تنازلياً حسب الأكثر فائدة (upvotes).
     * فشل الاتصال بالشبكة هنا لا يُغلق التدفّق ولا يرمي خطأ — سيمو يجب أن
     * يستمر بالعمل محلياً بكامل طاقته حتى بلا إنترنت، والمزامنة مجرد إضافة
     * انتهازية فوقه، لا شرط لعمله.
     */
    fun observeSharedLearnedExamples(): Flow<List<TrainedExample>> = callbackFlow {
        val registration = collection
            .orderBy("upvotes", Query.Direction.DESCENDING)
            .limit(REMOTE_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // نتجاهل الخطأ عمداً بدل إغلاق التدفّق: يبقى سيمو يعمل
                    // بآخر بيانات محلية معروفة له إلى أن تعود الشبكة.
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val examples = snapshot.documents.mapNotNull { doc ->
                        val pattern = doc.getString("pattern") ?: return@mapNotNull null
                        val response = doc.getString("response") ?: return@mapNotNull null
                        val upvotes = doc.getLong("upvotes") ?: 0L
                        val downvotes = doc.getLong("downvotes") ?: 0L
                        val voters = (doc.get("voters") as? List<*>)?.filterIsInstance<String>()?.distinct()?.size ?: 0
                        if (voters < MIN_DISTINCT_VOTERS || downvotes >= upvotes) null else TrainedExample(pattern, response)
                    }
                    trySend(examples)
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * يرفع حالة (سؤال↔رد) أُعجب بها المستخدم إلى المجموعة المشتركة: إن لم
     * توجد حالة بنفس المفتاح الدلالي بعد، تُنشأ بصوت إيجابي واحد؛ إن وُجدت
     * فعلاً (سؤال مشابه تعلّمه جهاز آخر من قبل) يُضاف صوت إيجابي إضافي بدل
     * تكرار المستند. يُستدعى بعد 👍 مباشرة من AppViewModel بلا انتظار
     * (fire-and-forget) فلا يُعطَّل أي تفاعل محلي بانتظار الشبكة، وأي فشل
     * شبكي هنا صامت عمداً (`runCatching`) لأن التعلّم المحلي (DataStore)
     * تم حفظه أصلاً بغضّ النظر عن نجاح هذه المزامنة الإضافية.
     */
    suspend fun contribute(question: String, response: String) {
        val trimmedQuestion = question.trim()
        val trimmedResponse = response.trim()
        if (trimmedQuestion.isEmpty() || trimmedResponse.isEmpty()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val doc = collection.document(HerbAssistant.learningKey(trimmedQuestion))
        runCatching {
            db.runTransaction { tx ->
                val snapshot = tx.get(doc)
                if (!snapshot.exists()) {
                    tx.set(
                        doc,
                        hashMapOf(
                            "pattern" to trimmedQuestion,
                            "response" to trimmedResponse,
                            "upvotes" to 1L,
                            "downvotes" to 0L,
                            "voters" to listOf(uid),
                            "created_at" to FieldValue.serverTimestamp(),
                            "updated_at" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val voters = (snapshot.get("voters") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (uid !in voters) {
                        tx.update(
                            doc,
                            mapOf(
                                "upvotes" to FieldValue.increment(1),
                                "voters" to FieldValue.arrayUnion(uid),
                                "updated_at" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                null
            }.await()
        }
    }

    /**
     * يسجّل تقييماً سلبياً (👎) على حالة مشتركة موجودة فعلاً بنفس المفتاح
     * الدلالي؛ لا شيء يحدث إن لم تكن هذه الحالة قد شُوركت شبكياً أصلاً (لا
     * تُنشئ مستنداً جديداً بصوت سلبي وحيد بلا أي محتوى مفيد سبق أن أثبت
     * نفسه). فشل الشبكة هنا صامت لنفس سبب [contribute].
     */
    suspend fun demote(question: String) {
        val trimmedQuestion = question.trim()
        if (trimmedQuestion.isEmpty()) return
        val doc = collection.document(HerbAssistant.learningKey(trimmedQuestion))
        runCatching {
            db.runTransaction { tx ->
                val snapshot = tx.get(doc)
                if (snapshot.exists()) {
                    tx.update(
                        doc,
                        mapOf(
                            "downvotes" to FieldValue.increment(1),
                            "updated_at" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        }
    }
}
