package com.shopmanager.app.assistant.voice

import com.shopmanager.app.assistant.engine.SpeechAlt
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList
import com.shopmanager.app.assistant.engine.toPrompt
import java.util.concurrent.atomic.AtomicBoolean

internal class WhisperEngine(
    private val models: ModelManager,
    private val deviceClass: () -> DeviceClass
) : VoiceEngine {
    override val kind = VoiceEngineKind.WHISPER
    private val lock = Any()
    private var ctx = 0L
    private val stopFlag = AtomicBoolean(false)
    private val cancelFlag = AtomicBoolean(false)
    @Volatile private var thread: Thread? = null

    override fun prepare(vocabulary: VocabularyList): String? {
        if (!WhisperBridge.available()) return "محرك ويسبر غير متوفر على هذا الجهاز"
        if (!models.whisperInstalled()) return "نموذج ويسبر غير منزّل"
        synchronized(lock) {
            if (ctx != 0L) return null
            return try {
                val c = WhisperBridge.nativeInit(models.whisperFile.absolutePath)
                if (c == 0L) "تعذر تحميل نموذج ويسبر" else {
                    ctx = c
                    null
                }
            } catch (t: Throwable) {
                "تعذر تحميل نموذج ويسبر"
            }
        }
    }

    private fun threads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when (deviceClass()) {
            DeviceClass.LOW -> minOf(2, cores).coerceAtLeast(1)
            DeviceClass.MID -> minOf(3, cores).coerceAtLeast(1)
            DeviceClass.HIGH -> minOf(4, cores).coerceAtLeast(1)
        }
    }

    private fun transcribe(pcm: FloatArray, prompt: String): Pair<String, Float>? {
        if (pcm.size < AudioCapture.SAMPLE_RATE / 4) return null
        synchronized(lock) {
            val c = ctx
            if (c == 0L) return null
            val bytes = WhisperBridge.nativeTranscribe(c, pcm, "ar", prompt, threads()) ?: return null
            val text = WhisperPostFilter.clean(String(bytes, Charsets.UTF_8))
            return text to WhisperBridge.nativeLastConfidence()
        }
    }

    override fun start(sink: EngineSink, settings: VoiceSettings, vocabulary: VocabularyList) {
        stopFlag.set(false)
        cancelFlag.set(false)
        val t = Thread({ run(sink, settings, vocabulary) }, "voice-whisper")
        t.priority = Thread.NORM_PRIORITY
        thread = t
        t.start()
    }

    private fun run(sink: EngineSink, settings: VoiceSettings, vocabulary: VocabularyList) {
        val capture = AudioCapture()
        try {
            if (!capture.open()) {
                sink.onError("تعذر فتح المايك", false)
                return
            }
            val prompt = vocabulary.toPrompt()
            val seg = VadSegmenter(AudioCapture.SAMPLE_RATE, settings.autoStopMs, settings.minLevel, 15000L)
            val buf = ShortArray(AudioCapture.CHUNK)
            val interim = deviceClass() != DeviceClass.LOW
            var lastInterimAt = 0L
            var noSpeech = false
            while (!stopFlag.get() && !cancelFlag.get()) {
                val n = capture.read(buf, buf.size)
                if (n < 0) break
                if (n == 0) continue
                val ev = seg.push(buf, n)
                sink.onLevel(Vad.normalizedLevel(seg.level))
                if (interim && seg.started) {
                    val now = System.currentTimeMillis()
                    if (now - lastInterimAt > 2500L && seg.speechMs > 1200L) {
                        lastInterimAt = now
                        val part = transcribe(seg.snapshotFloats(), prompt)
                        if (part != null && part.first.isNotBlank() && !stopFlag.get()) sink.onPartial(part.first)
                    }
                }
                if (ev == VadEvent.ENDED) break
                if (ev == VadEvent.NO_SPEECH) {
                    noSpeech = true
                    break
                }
            }
            if (cancelFlag.get()) return
            capture.close()
            if (noSpeech || !seg.started) {
                sink.onResult(SpeechResult("", emptyList(), emptyList(), "whisper"))
                return
            }
            sink.onProcessing()
            val pcm = seg.speechFloats()
            val r = transcribe(pcm, prompt)
            if (cancelFlag.get()) return
            if (r == null) {
                sink.onError("تعذر التعرف عبر ويسبر", true)
                return
            }
            val alts = if (r.first.isBlank()) emptyList() else listOf(SpeechAlt(r.first, r.second))
            sink.onResult(SpeechResult(r.first, alts, emptyList(), "whisper"))
        } catch (t: Throwable) {
            if (!cancelFlag.get()) sink.onError("تعذر التعرف عبر ويسبر", true)
        } finally {
            capture.close()
        }
    }

    override fun stop() {
        stopFlag.set(true)
    }

    override fun cancel() {
        cancelFlag.set(true)
        stopFlag.set(true)
    }

    override fun release() {
        cancel()
        val t = thread
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(3000)
            } catch (e: InterruptedException) {
            }
        }
        synchronized(lock) {
            if (ctx != 0L) {
                try {
                    WhisperBridge.nativeFree(ctx)
                } catch (e: Throwable) {
                }
                ctx = 0L
            }
        }
    }
}
