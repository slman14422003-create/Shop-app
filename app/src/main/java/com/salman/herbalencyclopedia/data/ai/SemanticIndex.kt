package com.salman.herbalencyclopedia.data.ai

import com.salman.herbalencyclopedia.data.model.Herb
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * فهرس "تشابه المعنى التقريبي" محلي بالكامل (بلا نموذج ولا تنزيل ولا إنترنت):
 * كل نقطة في الموسوعة (اسم/فائدة/استخدام/تحذير/ضرر/ملاحظة) تُحوَّل إلى متجه TF-IDF من
 * مقاطع حرفية (ثلاثية) وكلمات كاملة، والسؤال بنفس الطريقة بعد توسيعه بالمرادفات والجذور
 * الموجودة أصلاً في سيمو. التشابه الجيبي (cosine) بين السؤال والنقطة يلتقط ما تفوّته
 * المطابقة الحرفية: الأخطاء الإملائية، اختلاف اللهجة، صيغ الجمع/الضمائر، والمرادفات.
 *
 * هذا لا يغني عن نموذج تضمين حقيقي مستقبلاً، لكنه يعمل الآن على أي جهاز بلا حجم إضافي.
 * يُبنى مرة واحدة لكل نسخة من قائمة الأعشاب ويُخزَّن مؤقتاً (البناء أول مرة فقط).
 */
object SemanticIndex {

    class Hit(val herb: Herb, val field: String, val text: String, val score: Double)

    /** تخطيط CSR مضغوط (مصفوفات أولية) لتوفير الذاكرة على الأجهزة الضعيفة. */
    private class Index(
        val key: Long,
        val herbs: List<Herb>,
        val pointHerb: IntArray,
        val pointField: Array<String>,
        val pointText: Array<String>,
        val dense: HashMap<Int, Int>,
        val idf: FloatArray,
        val offsets: IntArray,
        val postPoint: IntArray,
        val postWeight: FloatArray
    )

    @Volatile private var cached: Index? = null

    private const val MIN_POINT_CHARS = 4
    private const val WORD_BOOST = 1.5f
    private const val EXPANSION_WEIGHT = 0.5f

    private fun contentKey(herbs: List<Herb>): Long {
        var k = herbs.size.toLong()
        for (h in herbs) k = k * 31 + h.id.hashCode() + h.name.hashCode() + h.benefits.length + h.warnings.length + h.usage.length
        return k
    }

    private fun addFeatures(text: String, weight: Float, out: HashMap<Int, Float>) {
        for (w in HerbAssistant.searchWords(text)) {
            val wk = ("w:$w").hashCode()
            out[wk] = (out[wk] ?: 0f) + weight * WORD_BOOST
            val padded = "#$w#"
            for (i in 0..padded.length - 3) {
                val tk = ("t:" + padded.substring(i, i + 3)).hashCode()
                out[tk] = (out[tk] ?: 0f) + weight
            }
        }
    }

    private fun build(herbs: List<Herb>, key: Long): Index {
        val herbIdx = ArrayList<Int>()
        val fields = ArrayList<String>()
        val texts = ArrayList<String>()
        val dense = HashMap<Int, Int>()
        val dfList = ArrayList<Int>()

        // المرور ١: النقاط + عدّ تكرار كل ميزة عبر النقاط (df) بلا تخزين متجهات كاملة.
        herbs.forEachIndexed { hi, herb ->
            for ((field, point) in HerbAssistant.searchPoints(herb)) {
                if (point.length < MIN_POINT_CHARS) continue
                val tf = HashMap<Int, Float>()
                addFeatures(point, 1f, tf)
                if (tf.isEmpty()) continue
                herbIdx += hi; fields += field; texts += point
                for (f in tf.keys) {
                    val id = dense.getOrPut(f) { dfList.add(0); dfList.size - 1 }
                    dfList[id] = dfList[id] + 1
                }
            }
        }
        val n = texts.size.coerceAtLeast(1)
        val featureCount = dfList.size
        val idf = FloatArray(featureCount) { ln(1f + n.toFloat() / dfList[it].toFloat()) }
        val offsets = IntArray(featureCount + 1)
        for (i in 0 until featureCount) offsets[i + 1] = offsets[i] + dfList[i]
        val postPoint = IntArray(offsets[featureCount])
        val postWeight = FloatArray(offsets[featureCount])
        val cursor = offsets.copyOf(featureCount)

        // المرور ٢: أوزان TF-IDF مُطبَّعة لكل نقطة، تُوضَع مباشرة في القوائم المعكوسة.
        for (pi in texts.indices) {
            val tf = HashMap<Int, Float>()
            addFeatures(texts[pi], 1f, tf)
            var norm = 0f
            val ids = IntArray(tf.size)
            val ws = FloatArray(tf.size)
            var k = 0
            for ((f, c) in tf) {
                val id = dense.getValue(f)
                val w = (1f + ln(c)) * idf[id]
                ids[k] = id; ws[k] = w; norm += w * w; k++
            }
            val inv = if (norm > 0f) 1f / sqrt(norm) else 1f
            for (j in 0 until k) {
                val pos = cursor[ids[j]]++
                postPoint[pos] = pi
                postWeight[pos] = ws[j] * inv
            }
        }
        return Index(
            key, herbs, herbIdx.toIntArray(), fields.toTypedArray(), texts.toTypedArray(),
            dense, idf, offsets, postPoint, postWeight
        )
    }

    private fun indexFor(herbs: List<Herb>): Index {
        val key = contentKey(herbs)
        cached?.let { if (it.key == key && it.herbs.size == herbs.size) return it }
        synchronized(this) {
            cached?.let { if (it.key == key && it.herbs.size == herbs.size) return it }
            val built = build(herbs, key)
            cached = built
            return built
        }
    }

    /**
     * أقرب [limit] نقطة للسؤال (cosine ∈ 0..1). قائمة فارغة إن لم يوجد شيء يستحق.
     * آمنة للاستدعاء من أي خيط؛ أول استدعاء لقائمة جديدة يبني الفهرس (ثوانٍ قليلة كحد أقصى).
     */
    fun rank(question: String, herbs: List<Herb>, limit: Int = 12): List<Hit> {
        if (herbs.isEmpty() || question.isBlank()) return emptyList()
        val index = indexFor(herbs)
        if (index.pointText.isEmpty()) return emptyList()

        val q = HashMap<Int, Float>()
        addFeatures(question, 1f, q)
        val original = HerbAssistant.searchWords(question).toSet()
        val extra = HerbAssistant.expandedWords(question) - original
        if (extra.isNotEmpty()) addFeatures(extra.joinToString(" "), EXPANSION_WEIGHT, q)
        if (q.isEmpty()) return emptyList()

        val n = index.pointText.size.toFloat()
        var qNorm = 0f
        val qIds = ArrayList<Int>()
        val qWs = ArrayList<Float>()
        for ((f, c) in q) {
            val id = index.dense[f]
            val idf = if (id != null) index.idf[id] else ln(1f + n)
            val w = (1f + ln(c.coerceAtLeast(1f))) * idf
            qNorm += w * w
            if (id != null) { qIds += id; qWs += w }
        }
        if (qNorm <= 0f) return emptyList()
        val qInv = 1f / sqrt(qNorm)

        val scores = FloatArray(index.pointText.size)
        for (i in qIds.indices) {
            val id = qIds[i]
            val qv = qWs[i] * qInv
            for (j in index.offsets[id] until index.offsets[id + 1]) {
                scores[index.postPoint[j]] += qv * index.postWeight[j]
            }
        }

        val order = scores.indices.filter { scores[it] > 0.05f }.sortedByDescending { scores[it] }.take(limit)
        return order.map { pi ->
            Hit(index.herbs[index.pointHerb[pi]], index.pointField[pi], index.pointText[pi], scores[pi].toDouble())
        }
    }
}
