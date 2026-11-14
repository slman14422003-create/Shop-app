package com.shopmanager.app.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.shopmanager.app.assistant.engine.SpeechAlt
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList

internal class SystemSpeechEngine(context: Context) : VoiceEngine {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var sink: EngineSink? = null
    private var localeIndex = 0
    private var settings = VoiceSettings()
    private var bias: ArrayList<String> = ArrayList()
    @Volatile private var active = false
    private var watchdog: Runnable? = null

    override fun prepare(vocabulary: VocabularyList): String? {
        val ok = try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (t: Throwable) {
            false
        }
        if (!ok) return "خدمة التعرف على الصوت غير متوفرة، ثبّت تطبيق Google"
        bias = ArrayList(vocabulary.words.asSequence().filter { it.length in 2..24 }.distinct().take(100).toList())
        return null
    }

    override fun start(sink: EngineSink, settings: VoiceSettings, vocabulary: VocabularyList) {
        this.sink = sink
        this.settings = settings
        localeIndex = 0
        active = true
        main.post { begin() }
    }

    private fun intentFor(tag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, settings.autoStopMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, settings.autoStopMs)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            if (Build.VERSION.SDK_INT >= 33 && bias.isNotEmpty()) putStringArrayListExtra(RecognizerIntent.EXTRA_BIAS_STRINGS, bias)
        }

    private fun begin() {
        if (!active) return
        val s = sink ?: return
        try {
            destroyRecognizer()
            if (localeIndex >= LOCALES.size) {
                active = false
                s.onError("اللغة العربية غير متوفرة في خدمة الصوت", false)
                return
            }
            val r = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = r
            r.setRecognitionListener(listener)
            r.startListening(intentFor(LOCALES[localeIndex]))
            armWatchdog()
        } catch (t: Throwable) {
            active = false
            s.onError("تعذر تشغيل التعرف على الصوت", false)
        }
    }

    private fun armWatchdog() {
        disarmWatchdog()
        val w = Runnable {
            if (active) {
                try {
                    recognizer?.stopListening()
                } catch (t: Throwable) {
                }
            }
        }
        watchdog = w
        main.postDelayed(w, 25000L)
    }

    private fun disarmWatchdog() {
        watchdog?.let { main.removeCallbacks(it) }
        watchdog = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            sink?.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onEndOfSpeech() {
            sink?.onProcessing()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) sink?.onPartial(t)
        }

        override fun onResults(results: Bundle?) {
            disarmWatchdog()
            active = false
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val alts = texts.mapIndexed { i, t ->
                SpeechAlt(t, scores?.getOrNull(i)?.takeIf { it >= 0f } ?: if (i == 0) 0.8f else 0.4f)
            }
            val best = texts.firstOrNull().orEmpty()
            destroyRecognizer()
            sink?.onResult(SpeechResult(best, if (best.isBlank()) emptyList() else alts.take(5), emptyList(), "system"))
        }

        override fun onError(error: Int) {
            disarmWatchdog()
            if (!active) return
            when (error) {
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    localeIndex++
                    main.post { begin() }
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    active = false
                    destroyRecognizer()
                    sink?.onResult(SpeechResult("", emptyList(), emptyList(), "system"))
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    active = false
                    destroyRecognizer()
                    sink?.onError("ما في صلاحية للمايك", false)
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    active = false
                    destroyRecognizer()
                    sink?.onError("ما في إنترنت، اكتب الأمر", false)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    active = false
                    destroyRecognizer()
                    sink?.onError("الخدمة مشغولة، جرّب مرة ثانية", false)
                }
                else -> {
                    active = false
                    destroyRecognizer()
                    sink?.onError("تعذر التعرف على الصوت", false)
                }
            }
        }
    }

    private fun destroyRecognizer() {
        val r = recognizer
        recognizer = null
        if (r != null) {
            try {
                r.cancel()
            } catch (t: Throwable) {
            }
            try {
                r.destroy()
            } catch (t: Throwable) {
            }
        }
    }

    override fun stop() {
        main.post {
            try {
                recognizer?.stopListening()
            } catch (t: Throwable) {
            }
        }
    }

    override fun cancel() {
        active = false
        main.post {
            disarmWatchdog()
            destroyRecognizer()
        }
    }

    override fun release() {
        cancel()
    }

    companion object {
        private val LOCALES = listOf("ar-SY", "ar-LB", "ar-JO", "ar-SA")
    }
}
