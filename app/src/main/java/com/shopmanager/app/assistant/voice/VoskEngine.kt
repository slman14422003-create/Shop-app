package com.shopmanager.app.assistant.voice

import com.shopmanager.app.assistant.engine.SpeechAlt
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.SpeechWord
import com.shopmanager.app.assistant.engine.VocabularyList
import com.shopmanager.app.assistant.engine.toGrammarJson
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.atomic.AtomicBoolean

internal class VoskEngine(private val models: ModelManager) : VoiceEngine {
    override val kind = VoiceEngineKind.VOSK
    private var model: Model? = null
    private var loadedPath: String? = null
    private val stopFlag = AtomicBoolean(false)
    private val cancelFlag = AtomicBoolean(false)
    @Volatile private var thread: Thread? = null

    @Synchronized
    override fun prepare(vocabulary: VocabularyList): String? {
        val dir = models.installedVoskDir() ?: return "نموذج فوسك غير منزّل"
        return try {
            if (model == null || loadedPath != dir.absolutePath) {
                model?.close()
                model = Model(dir.absolutePath)
                loadedPath = dir.absolutePath
            }
            null
        } catch (t: Throwable) {
            model = null
            loadedPath = null
            "تعذر تحميل نموذج فوسك"
        }
    }

    override fun start(sink: EngineSink, settings: VoiceSettings, vocabulary: VocabularyList) {
        stopFlag.set(false)
        cancelFlag.set(false)
        val t = Thread({ run(sink, settings, vocabulary) }, "voice-vosk")
        t.priority = Thread.NORM_PRIORITY
        thread = t
        t.start()
    }

    private fun createRecognizer(m: Model, vocabulary: VocabularyList): Recognizer {
        val r = try {
            Recognizer(m, AudioCapture.SAMPLE_RATE.toFloat(), vocabulary.toGrammarJson())
        } catch (t: Throwable) {
            Recognizer(m, AudioCapture.SAMPLE_RATE.toFloat())
        }
        r.setMaxAlternatives(5)
        r.setWords(true)
        return r
    }

    private fun run(sink: EngineSink, settings: VoiceSettings, vocabulary: VocabularyList) {
        val m = synchronized(this) { model }
        if (m == null) {
            sink.onError("نموذج فوسك غير جاهز", true)
            return
        }
        val capture = AudioCapture()
        var recognizer: Recognizer? = null
        try {
            recognizer = createRecognizer(m, vocabulary)
            if (!capture.open()) {
                sink.onError("تعذر فتح المايك", false)
                return
            }
            val vad = Vad(AudioCapture.SAMPLE_RATE, settings.autoStopMs, 160L, 30000L, 8000L, settings.minLevel)
            val buf = ShortArray(AudioCapture.CHUNK)
            val segments = ArrayList<SegmentJoiner.Segment>()
            val allAlts = ArrayList<List<SpeechAlt>>()
            val allWords = ArrayList<SpeechWord>()
            var lastEndMs = 0L
            var elapsedMs = 0L
            var lastPartial = ""
            var noSpeech = false
            while (!stopFlag.get() && !cancelFlag.get()) {
                val n = capture.read(buf, buf.size)
                if (n < 0) break
                if (n == 0) continue
                val ms = n * 1000L / AudioCapture.SAMPLE_RATE
                elapsedMs += ms
                val ev = vad.feed(buf, n)
                sink.onLevel(Vad.normalizedLevel(vad.lastLevel))
                if (recognizer.acceptWaveForm(buf, n)) {
                    val seg = parseFinal(recognizer.result)
                    if (seg != null) {
                        val gap = (elapsedMs - lastEndMs).coerceAtLeast(0L)
                        segments.add(SegmentJoiner.Segment(seg.first.firstOrNull()?.text.orEmpty(), if (segments.isEmpty()) 0L else gap))
                        allAlts.add(seg.first)
                        allWords.addAll(seg.second)
                    }
                    lastEndMs = elapsedMs
                    lastPartial = ""
                } else {
                    val p = parsePartial(recognizer.partialResult)
                    if (p.isNotBlank() && p != lastPartial) {
                        lastPartial = p
                        val so = SegmentJoiner.join(segments, 300L)
                        sink.onPartial(if (so.isEmpty()) p else "$so $p")
                    }
                }
                if (ev == VadEvent.ENDED) break
                if (ev == VadEvent.NO_SPEECH) {
                    noSpeech = true
                    break
                }
            }
            if (cancelFlag.get()) return
            sink.onProcessing()
            capture.close()
            val tail = parseFinal(recognizer.finalResult)
            if (tail != null) {
                val gap = (elapsedMs - lastEndMs).coerceAtLeast(0L)
                segments.add(SegmentJoiner.Segment(tail.first.firstOrNull()?.text.orEmpty(), if (segments.isEmpty()) 0L else gap))
                allAlts.add(tail.first)
                allWords.addAll(tail.second)
            }
            if (cancelFlag.get()) return
            val text = SegmentJoiner.join(segments, 300L)
            if (noSpeech && text.isBlank()) {
                sink.onResult(SpeechResult("", emptyList(), emptyList(), "vosk"))
                return
            }
            sink.onResult(SpeechResult(text, mergeAlternatives(text, allAlts), allWords, "vosk"))
        } catch (t: Throwable) {
            if (!cancelFlag.get()) sink.onError("تعذر التعرف عبر فوسك", true)
        } finally {
            capture.close()
            try {
                recognizer?.close()
            } catch (t: Throwable) {
            }
        }
    }

    private fun mergeAlternatives(best: String, perSegment: List<List<SpeechAlt>>): List<SpeechAlt> {
        if (perSegment.isEmpty()) return emptyList()
        if (perSegment.size == 1) return perSegment[0].drop(1)
        val out = ArrayList<SpeechAlt>()
        val maxN = perSegment.maxOf { it.size }
        for (i in 1 until maxN) {
            val parts = perSegment.map { it.getOrNull(i)?.text ?: it.firstOrNull()?.text.orEmpty() }
            val joined = parts.filter { it.isNotBlank() }.joinToString(" ")
            if (joined.isNotBlank() && joined != best) {
                val c = perSegment.map { it.getOrNull(i)?.confidence ?: it.firstOrNull()?.confidence ?: 0f }.average().toFloat()
                out.add(SpeechAlt(joined, c))
            }
        }
        return out
    }

    private fun clean(t: String): String =
        t.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()

    private fun parsePartial(json: String): String = try {
        clean(JSONObject(json).optString("partial", ""))
    } catch (t: Throwable) {
        ""
    }

    private fun parseFinal(json: String): Pair<List<SpeechAlt>, List<SpeechWord>>? {
        return try {
            val o = JSONObject(json)
            val alts = ArrayList<SpeechAlt>()
            val words = ArrayList<SpeechWord>()
            val arr = o.optJSONArray("alternatives")
            if (arr != null && arr.length() > 0) {
                var sum = 0.0
                for (i in 0 until arr.length()) sum += arr.getJSONObject(i).optDouble("confidence", 0.0).coerceAtLeast(0.0)
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    val text = clean(a.optString("text", ""))
                    if (text.isEmpty()) continue
                    val c = if (sum > 0.0) (a.optDouble("confidence", 0.0).coerceAtLeast(0.0) / sum).toFloat() else 0f
                    alts.add(SpeechAlt(text, c))
                    if (i == 0 || words.isEmpty()) {
                        val rs = a.optJSONArray("result")
                        if (rs != null) {
                            for (k in 0 until rs.length()) {
                                val w = rs.getJSONObject(k)
                                val wt = w.optString("word", "")
                                if (wt.isNotBlank() && wt != "[unk]") words.add(SpeechWord(wt, w.optDouble("conf", 1.0).toFloat()))
                            }
                        }
                    }
                }
            } else {
                val text = clean(o.optString("text", ""))
                if (text.isNotEmpty()) alts.add(SpeechAlt(text, 1f))
                val rs = o.optJSONArray("result")
                if (rs != null) {
                    for (k in 0 until rs.length()) {
                        val w = rs.getJSONObject(k)
                        val wt = w.optString("word", "")
                        if (wt.isNotBlank() && wt != "[unk]") words.add(SpeechWord(wt, w.optDouble("conf", 1.0).toFloat()))
                    }
                }
            }
            if (alts.isEmpty()) null else alts to words
        } catch (t: Throwable) {
            null
        }
    }

    override fun stop() {
        stopFlag.set(true)
    }

    override fun cancel() {
        cancelFlag.set(true)
        stopFlag.set(true)
    }

    @Synchronized
    override fun release() {
        cancel()
        val t = thread
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(1500)
            } catch (e: InterruptedException) {
            }
        }
        try {
            model?.close()
        } catch (t2: Throwable) {
        }
        model = null
        loadedPath = null
    }
}
