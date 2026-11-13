package com.shopmanager.app.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.shopmanager.app.assistant.engine.SpeechAlt
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

internal class SystemSpeechEngine(context: Context) : VoiceEngine {
    override val kind = VoiceEngineKind.SYSTEM
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var sink: EngineSink? = null
    private var localeIndex = 0
    private var settings = VoiceSettings()
    @Volatile private var active = false
    private var watchdog: Runnable? = null

    override fun prepare(vocabulary: VocabularyList): String? {
        if (Build.VERSION.SDK_INT < 31) return "المحرك المدمج يحتاج أندرويد 12 أو أحدث"
        return try {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) null else "التعرف المدمج غير متوفر"
        } catch (t: Throwable) {
            "التعرف المدمج غير متوفر"
        }
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
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, settings.autoStopMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, settings.autoStopMs)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }

    private fun begin() {
        if (!active) return
        val s = sink ?: return
        try {
            destroyRecognizer()
            if (Build.VERSION.SDK_INT < 31) {
                s.onError("المحرك المدمج يحتاج أندرويد 12 أو أحدث", true)
                return
            }
            if (localeIndex >= EngineSelector.SUPPORTED_LOCALES.size) {
                s.onError("اللغة العربية غير مثبتة للتعرف المدمج", true)
                return
            }
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            recognizer = r
            r.setRecognitionListener(listener)
            r.startListening(intentFor(EngineSelector.SUPPORTED_LOCALES[localeIndex]))
            armWatchdog()
        } catch (t: Throwable) {
            s.onError("تعذر تشغيل التعرف المدمج", true)
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
        main.postDelayed(w, 20000L)
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
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val t = list?.firstOrNull()
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
                else -> {
                    active = false
                    destroyRecognizer()
                    sink?.onError("تعذر التعرف المدمج", true)
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
        fun onDeviceAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 31) return false
            return try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context.applicationContext)
            } catch (t: Throwable) {
                false
            }
        }

        fun probeLocaleSupport(context: Context, timeoutMs: Long): Boolean {
            if (Build.VERSION.SDK_INT < 33) return true
            val appContext = context.applicationContext
            val latch = CountDownLatch(1)
            var result = true
            val main = Handler(Looper.getMainLooper())
            main.post {
                try {
                    val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    }
                    val exec = Executor { cmd -> main.post(cmd) }
                    r.checkRecognitionSupport(intent, exec, object : RecognitionSupportCallback {
                        override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                            val all = recognitionSupport.supportedOnDeviceLanguages + recognitionSupport.installedOnDeviceLanguages
                            val norm = all.map { it.replace('_', '-').lowercase() }
                            result = EngineSelector.SUPPORTED_LOCALES.any { it.lowercase() in norm }
                            try {
                                r.destroy()
                            } catch (t: Throwable) {
                            }
                            latch.countDown()
                        }

                        override fun onError(error: Int) {
                            result = true
                            try {
                                r.destroy()
                            } catch (t: Throwable) {
                            }
                            latch.countDown()
                        }
                    })
                } catch (t: Throwable) {
                    result = true
                    latch.countDown()
                }
            }
            return try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                result
            } catch (t: Throwable) {
                true
            }
        }
    }
}
