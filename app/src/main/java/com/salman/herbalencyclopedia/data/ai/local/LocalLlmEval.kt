package com.salman.herbalencyclopedia.data.ai.local

import android.content.Context
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.data.search.HybridSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

object LocalLlmEvalSet {
    val questions: List<String> = listOf(
        "ما فوائد الزنجبيل؟",
        "كيف أستخدم النعناع؟",
        "هل البابونج آمن للحامل؟",
        "ما أضرار القرفة؟",
        "ما فوائد الينسون للمغص؟",
        "عشبة تساعد على النوم",
        "ما العشبة المناسبة للسعال؟",
        "هل الزعتر مناسب للأطفال؟",
        "ما أضرار الإفراط في الشاي الأخضر؟",
        "كيف أحضر مشروب الميرمية؟",
        "ما فوائد الكركم؟",
        "هل الكركم يتعارض مع الأدوية؟",
        "عشبة للهضم والانتفاخ",
        "ما فوائد الحلبة للمرضع؟",
        "ما تحذيرات استخدام القرنفل؟",
        "ما فوائد الخزامى؟",
        "عشبة لتهدئة الأعصاب",
        "هل النعناع يضر مرضى الحموضة؟",
        "كيف أستخدم الزنجبيل للغثيان؟",
        "ما فوائد اليانسون؟",
        "وما أضرارها؟",
        "هل يمكن استخدامها يومياً؟",
        "ما أفضل عشبة للمعدة؟",
        "هل القرفة تناسب مرضى السكر؟",
        "ما فوائد الميرمية للحلق؟",
        "عشبة لخفض الضغط",
        "ما أضرار الزعتر؟",
        "كيف أستخدم البابونج للأطفال؟",
        "ما فوائد الشاي الأخضر؟",
        "ما العشبة التي تساعد على الاسترخاء؟"
    )
}

object LocalLlmEvalRunner {

    private class Row(
        val question: String,
        val pointCount: Int,
        val accepted: Boolean,
        val millis: Long,
        val output: String
    )

    private const val PER_QUESTION_TIMEOUT_MS = 40_000L

    suspend fun run(context: Context, herbs: List<Herb>): String = withContext(Dispatchers.Default) {
        val report = StringBuilder()
        report.append("تقرير مقارنة النماذج المحلية\n")
        report.append("عدد الأسئلة: ").append(LocalLlmEvalSet.questions.size).append('\n')
        val installed = LocalLlmModels.all.filter { LocalLlmManager.isInstalled(it) }
        if (installed.isEmpty()) {
            report.append("لا يوجد أي نموذج محمّل للمقارنة.\n")
            return@withContext report.toString()
        }
        LocalLlmManager.release()
        val summaries = ArrayList<String>()
        for (spec in installed) {
            val engine = LiteRtLmEngine(context)
            val rows = ArrayList<Row>()
            try {
                withContext(Dispatchers.IO) { engine.load(LocalLlmManager.modelFile(spec).absolutePath) }
                for (q in LocalLlmEvalSet.questions) {
                    val grounding = HybridSearch.rank(q, herbs)
                    if (grounding.isEmpty) {
                        rows += Row(q, 0, false, 0L, "")
                        continue
                    }
                    val buffer = StringBuilder()
                    val start = System.currentTimeMillis()
                    val finished = try {
                        withTimeoutOrNull(PER_QUESTION_TIMEOUT_MS) {
                            engine.generate(
                                LocalRephraser.SYSTEM_PROMPT,
                                LocalRephraser.buildUserPrompt(q, grounding.points)
                            ).collect { buffer.append(it) }
                            true
                        }
                    } catch (e: Throwable) {
                        null
                    }
                    val elapsed = System.currentTimeMillis() - start
                    val text = RephraseValidator.stripThinking(buffer.toString())
                    val ok = finished == true && RephraseValidator.validate(text, grounding.points).ok
                    rows += Row(q, grounding.points.size, ok, elapsed, text)
                }
            } catch (e: Throwable) {
                report.append("تعذر تشغيل ").append(spec.label).append(": ").append(e.javaClass.simpleName).append('\n')
            } finally {
                runCatching { engine.release() }
            }
            val answered = rows.filter { it.pointCount > 0 }
            val accepted = answered.count { it.accepted }
            val avg = if (answered.isEmpty()) 0L else answered.sumOf { it.millis } / answered.size
            summaries += "${spec.label}: مقبول $accepted من ${answered.size} (بزمن متوسط $avg مللي ثانية)"
            report.append("\n=== ").append(spec.label).append(" ===\n")
            for (row in rows) {
                report.append("- ").append(row.question).append('\n')
                if (row.pointCount == 0) {
                    report.append("  لا توجد نقاط في الموسوعة لهذا السؤال\n")
                } else {
                    report.append("  ").append(if (row.accepted) "مقبول" else "مرفوض")
                        .append(" | ").append(row.millis).append(" مللي ثانية\n")
                    report.append("  ").append(row.output.replace('\n', ' ').take(300)).append('\n')
                }
            }
        }
        report.append("\n=== الملخص ===\n")
        summaries.forEach { report.append(it).append('\n') }
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        runCatching { File(dir, "semo_local_llm_eval.txt").writeText(report.toString()) }
        report.toString()
    }
}
