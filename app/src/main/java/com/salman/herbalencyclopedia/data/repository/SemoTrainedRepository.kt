package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.salman.herbalencyclopedia.data.ai.TrainedExample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * يزامن "الحالات المدرَّبة يدوياً" ([com.salman.herbalencyclopedia.data.ai.AiConfig.trainedExamples]
 * — يضبطها المطوّر حصراً من أدوات المطور في نسخة full، انظر
 * HerbalNavGraph.kt) عبر مستند واحد "current" داخل مجموعة "semo_trained"
 * في Firestore، بدل بقائها محصورة محلياً على DataStore جهاز المطوّر وحده
 * كما كان الحال سابقاً — فما يدرّبه المطوّر على جهازه لم يكن يصل لأي
 * مستخدم آخر مثبِّت نسخة public إطلاقاً.
 *
 * القراءة ([observeSharedTrainedExamples]) مفتوحة لأي جهاز بما فيها نسخة
 * public (التي لا تملك أصلاً شاشة أدوات المطور، فلم يكن ممكناً أن تصلها
 * هذه الحالات من قبل بأي شكل). الكتابة ([publish]) محمية بالكامل بقواعد
 * Firestore (isAdmin() في firestore.rules — نفس حساب المطوّر الثابت
 * المستخدَم أصلاً لحماية تعديل الأعشاب/الخلطات)، فلا تصل من نسخة public
 * أو أي جهاز آخر غير جهاز المطوّر المسجَّل دخوله فعلياً (AuthRepository،
 * متاح بنسخة full فقط) حتى لو حاول عبثاً من كود مُعدَّل.
 *
 * فارق مهم عن [SemoLearningRepository] (تعلّم تراكمي إضافي من تقييمات
 * المستخدمين، "مرفوع" وليس "منسَّقاً"): هذه قائمة مُنسَّقة يدوياً بالكامل
 * من المطوّر، فكل [publish] يستبدل القائمة المشتركة بأكملها بدل الإضافة
 * عليها فقط — حذف أو تعديل مثال من أدوات المطور ينعكس فوراً على كل
 * الأجهزة الأخرى (بما فيها نسخة public) بدل أن يبقى معلَّقاً عندها للأبد.
 */
class SemoTrainedRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val doc get() = db.collection("semo_trained").document("current")

    /**
     * بث حيّ للقائمة المشتركة الحالية بأكملها (استبدال لا دمج، راجع
     * التوثيق أعلاه). يعيد `null` تحديداً حين لا يوجد أي نشر سابق إطلاقاً
     * (المستند غير موجود بعد) — فرق مهم عن قائمة فارغة فعلياً منشورة عمداً:
     * يمنع هذا أي جهاز (وبالأخص جهاز المطوّر نفسه عند أول تشغيل بعد هذا
     * التحديث، قبل أي [publish] فعلي) من مسح حالاته المحلية الموجودة مسبقاً
     * ظناً منه أن القائمة المشتركة "فارغة فعلاً". راجع AppViewModel.init
     * لكيفية استخدام هذا الفرق (نشر تلقائي لمرة واحدة كبذرة أولى من نسخة
     * full إن وُجدت حالات محلية سابقة ولا شيء منشور بعد). فشل الاتصال
     * بالشبكة هنا لا يُغلق التدفّق ولا يرمي خطأ — سيمو يستمر بالعمل محلياً
     * بآخر قائمة معروفة له إلى أن تعود الشبكة، تماماً كنمط
     * [SemoLearningRepository.observeSharedLearnedExamples].
     */
    fun observeSharedTrainedExamples(): Flow<List<TrainedExample>?> = callbackFlow {
        val registration = doc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // نتجاهل الخطأ عمداً بدل إغلاق التدفّق — نفس فلسفة
                // SemoLearningRepository: البقاء بآخر بيانات محلية معروفة.
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                val raw = snapshot.get("examples") as? List<Map<String, Any>> ?: emptyList()
                val examples = raw.mapNotNull { entry ->
                    val pattern = entry["pattern"] as? String ?: return@mapNotNull null
                    val response = entry["response"] as? String ?: return@mapNotNull null
                    if (pattern.isBlank() || response.isBlank()) null else TrainedExample(pattern, response)
                }
                trySend(examples)
            } else if (snapshot != null) {
                // المستند غير موجود بعد (أول تشغيل قبل أي نشر من المطوّر
                // إطلاقاً) — null صراحة، لا قائمة فارغة (راجع التوثيق أعلاه).
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * يستبدل القائمة المشتركة بأكملها بالقائمة الحالية عند المطوّر. تُستدعى
     * فعلياً فقط من مسار تعديل نسخة full (راجع HerbalNavGraph.kt —
     * onTrainedExamplesChange)؛ قواعد Firestore ترفض الكتابة من أي حساب
     * غير المطوّر المحدَّد دفاعاً إضافياً حتى لو استُدعيت من مكان آخر
     * بالخطأ. فشل الشبكة هنا صامت عمداً (runCatching): الحفظ المحلي
     * (DataStore) يتم أصلاً بغضّ النظر عن نجاح هذه المزامنة الإضافية.
     */
    suspend fun publish(examples: List<TrainedExample>) {
        runCatching {
            doc.set(
                mapOf(
                    "examples" to examples.map {
                        mapOf("pattern" to it.pattern, "response" to it.response)
                    },
                    "updated_at" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
