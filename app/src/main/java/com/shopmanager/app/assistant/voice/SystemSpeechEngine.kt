package com.shopmanager.app.assistant.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.shopmanager.app.assistant.engine.SpeechAlt
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList

internal class SystemSpeechEngine(context: Context) : VoiceEngine {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var recognizer: SpeechRecognizer? = null
    private var sink: EngineSink? = null
    private var localeIndex = 0
    private var settings = VoiceSettings()
    @Volatile private var bias: ArrayList<String> = ArrayList()
    @Volatile private var googleService: ComponentName? = null
    @Volatile private var googleResolved = false
    @Volatile private var active = false
    private var watchdog: Runnable? = null
    private var retried = false
    private var heardSpeech = false
    private var lastVoiceAt = 0L
    private var stopRequested = false
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false

    override fun prepare(vocabulary: VocabularyList): String? {
        val ok = try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (t: Throwable) {
            Log.w(TAG, "isRecognitionAvailable failed", t)
            false
        }
        if (!ok) return "خدمة التعرف على الصوت غير متوفرة، ثبّت تطبيق Google"
        buildBias(vocabulary)
        resolveGoogleService()
        return null
    }

    override fun preload(vocabulary: VocabularyList) {
        buildBias(vocabulary)
        resolveGoogleService()
        main.post {
            if (active || recognizer != null) return@post
            try {
                recognizer = createRecognizer()
            } catch (t: Throwable) {
                Log.w(TAG, "preload failed", t)
            }
        }
    }

    private fun buildBias(vocabulary: VocabularyList) {
        val out = LinkedHashSet<String>()
        for (p in vocabulary.phrases) {
            val v = p.trim()
            if (v.length in 2..40) out.add(v)
        }
        for (w in vocabulary.words) {
            val v = w.trim()
            if (v.length in 2..24) out.add(v)
        }
        bias = ArrayList(out.take(MAX_BIAS))
    }

    private fun resolveGoogleService() {
        if (googleResolved) return
        try {
            val list = appContext.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            val g = list.firstOrNull { it.serviceInfo?.packageName == GOOGLE_PACKAGE }?.serviceInfo
            googleService = if (g != null) ComponentName(g.packageName, g.name) else null
        } catch (t: Throwable) {
            Log.w(TAG, "queryIntentServices failed", t)
            googleService = null
        }
        googleResolved = true
    }

    private fun createRecognizer(): SpeechRecognizer {
        val cn = googleService
        if (cn != null) {
            try {
                return SpeechRecognizer.createSpeechRecognizer(appContext, cn)
            } catch (t: Throwable) {
                Log.w(TAG, "createSpeechRecognizer(Google) failed", t)
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(appContext)
    }

    override fun start(sink: EngineSink, settings: VoiceSettings, vocabulary: VocabularyList) {
        this.sink = sink
        this.settings = settings
        localeIndex = 0
        retried = false
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
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
                if (bias.isNotEmpty()) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias))
            }
        }

    private fun begin() {
        if (!active) return
        val s = sink ?: return
        try {
            if (localeIndex >= LOCALES.size) {
                active = false
                destroyRecognizer()
                abandonFocus()
                s.onError("اللغة العربية غير متوفرة في خدمة الصوت", false)
                return
            }
            heardSpeech = false
            lastVoiceAt = 0L
            stopRequested = false
            requestFocus()
            val r = recognizer ?: createRecognizer().also { recognizer = it }
            r.setRecognitionListener(listener)
            r.startListening(intentFor(LOCALES[localeIndex]))
            armWatchdog()
        } catch (t: Throwable) {
            Log.w(TAG, "begin failed", t)
            active = false
            destroyRecognizer()
            abandonFocus()
            s.onError("تعذر تشغيل التعرف على الصوت", false)
        }
    }

    private fun requestFocus() {
        val am = audioManager ?: return
        if (focusHeld) return
        try {
            val granted = if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            focusHeld = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (t: Throwable) {
            Log.w(TAG, "requestAudioFocus failed", t)
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        if (!focusHeld) return
        focusHeld = false
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "abandonAudioFocus failed", t)
        }
    }

    private fun armWatchdog() {
        disarmWatchdog()
        val w = Runnable {
            if (active) {
                try {
                    recognizer?.stopListening()
                } catch (t: Throwable) {
                    Log.w(TAG, "watchdog stopListening failed", t)
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
        override fun onReadyForSpeech(params: Bundle?) {
            if (active) sink?.onReady()
        }

        override fun onBeginningOfSpeech() {
            heardSpeech = true
            lastVoiceAt = SystemClock.elapsedRealtime()
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            sink?.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            if (!active || stopRequested) return
            val now = SystemClock.elapsedRealtime()
            if (rmsdB >= SPEECH_RMS_DB) {
                heardSpeech = true
                lastVoiceAt = now
            } else if (heardSpeech && lastVoiceAt > 0L && now - lastVoiceAt >= settings.autoStopMs) {
                stopRequested = true
                try {
                    recognizer?.stopListening()
                } catch (t: Throwable) {
                    Log.w(TAG, "silence stopListening failed", t)
                }
            }
        }

        override fun onEndOfSpeech() {
            sink?.onProcessing()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) {
                heardSpeech = true
                lastVoiceAt = SystemClock.elapsedRealtime()
                sink?.onPartial(t)
            }
        }

        override fun onResults(results: Bundle?) {
            disarmWatchdog()
            abandonFocus()
            active = false
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val alts = texts.mapIndexed { i, t ->
                SpeechAlt(t, scores?.getOrNull(i)?.takeIf { it >= 0f } ?: if (i == 0) 0.8f else 0.4f)
            }
            val best = texts.firstOrNull().orEmpty()
            sink?.onResult(SpeechResult(best, if (best.isBlank()) emptyList() else alts.take(5), emptyList(), "system"))
        }

        override fun onError(error: Int) {
            disarmWatchdog()
            if (!active) return
            when (error) {
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    localeIndex++
                    destroyRecognizer()
                    main.post { begin() }
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    destroyRecognizer()
                    if (!retried) {
                        retried = true
                        main.post { begin() }
                    } else {
                        active = false
                        abandonFocus()
                        sink?.onResult(SpeechResult("", emptyList(), emptyList(), "system"))
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    active = false
                    destroyRecognizer()
                    abandonFocus()
                    sink?.onError("ما في صلاحية للمايك", false)
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    active = false
                    destroyRecognizer()
                    abandonFocus()
                    sink?.onError("ما في إنترنت، اكتب الأمر", false)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    active = false
                    destroyRecognizer()
                    abandonFocus()
                    sink?.onError("الخدمة مشغولة، جرّب مرة ثانية", false)
                }
                else -> {
                    active = false
                    destroyRecognizer()
                    abandonFocus()
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
                Log.w(TAG, "recognizer cancel failed", t)
            }
            try {
                r.destroy()
            } catch (t: Throwable) {
                Log.w(TAG, "recognizer destroy failed", t)
            }
        }
    }

    override fun stop() {
        main.post {
            try {
                recognizer?.stopListening()
            } catch (t: Throwable) {
                Log.w(TAG, "stopListening failed", t)
            }
        }
    }

    override fun cancel() {
        active = false
        main.post {
            disarmWatchdog()
            abandonFocus()
            try {
                recognizer?.cancel()
            } catch (t: Throwable) {
                Log.w(TAG, "cancel failed", t)
                destroyRecognizer()
            }
        }
    }

    override fun release() {
        active = false
        main.post {
            disarmWatchdog()
            abandonFocus()
            destroyRecognizer()
        }
    }

    companion object {
        private const val TAG = "SystemSpeechEngine"
        private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val MAX_BIAS = 100
        private const val SPEECH_RMS_DB = 4f
        private val LOCALES = listOf("ar-SY", "ar-LB", "ar-JO", "ar-SA")
    }
}
