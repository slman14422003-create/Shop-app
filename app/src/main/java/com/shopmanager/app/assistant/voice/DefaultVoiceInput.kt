package com.shopmanager.app.assistant.voice

import android.app.Application
import android.content.Context
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class DefaultVoiceInput private constructor(private val app: Application) : VoiceInput {
    private val prefs = app.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
    private val tts = TtsSpeaker(app)
    private val control = Executors.newSingleThreadExecutor { r ->
        Thread(r, "voice-control").apply { isDaemon = true }
    }

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    override val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
    private val _results = MutableSharedFlow<SpeechResult>(extraBufferCapacity = 8)
    override val results: SharedFlow<SpeechResult> = _results
    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    private val engine: VoiceEngine by lazy { SystemSpeechEngine(app) }
    private val session = AtomicInteger(0)

    private fun loadSettings(): VoiceSettings = VoiceSettings(
        speakReplies = prefs.getBoolean("speakReplies", true),
        autoStopMs = prefs.getLong("autoStopMs", 1500L).coerceIn(600L, 5000L),
        autoSend = prefs.getBoolean("autoSend", true)
    )

    override fun updateSettings(s: VoiceSettings) {
        _settings.value = s
        prefs.edit()
            .putBoolean("speakReplies", s.speakReplies)
            .putLong("autoStopMs", s.autoStopMs)
            .putBoolean("autoSend", s.autoSend)
            .apply()
    }

    override fun start(vocabulary: VocabularyList) {
        val id = session.incrementAndGet()
        control.execute {
            if (id != session.get()) return@execute
            try {
                engine.cancel()
            } catch (t: Throwable) {
            }
            tts.stop()
            _uiState.value = VoiceUiState.Preparing
            val err = try {
                engine.prepare(vocabulary)
            } catch (t: Throwable) {
                "تعذر التحضير"
            }
            if (id != session.get()) return@execute
            if (err != null) {
                _uiState.value = VoiceUiState.Unavailable(err)
                return@execute
            }
            _uiState.value = VoiceUiState.Listening("", 0f)
            val sink = object : EngineSink {
                override fun onPartial(text: String) {
                    if (id != session.get()) return
                    val lv = (_uiState.value as? VoiceUiState.Listening)?.level ?: 0f
                    _uiState.value = VoiceUiState.Listening(text, lv)
                }

                override fun onLevel(level: Float) {
                    if (id != session.get()) return
                    val p = (_uiState.value as? VoiceUiState.Listening)?.partial ?: return
                    _uiState.value = VoiceUiState.Listening(p, level)
                }

                override fun onProcessing() {
                    if (id != session.get()) return
                    _uiState.value = VoiceUiState.Processing
                }

                override fun onResult(result: SpeechResult) {
                    control.execute {
                        if (id != session.get()) return@execute
                        if (result.text.isBlank()) {
                            _uiState.value = VoiceUiState.Unavailable("ما سمعت شي، جرّب مرة ثانية")
                        } else {
                            _uiState.value = VoiceUiState.Idle
                            _results.tryEmit(result)
                        }
                    }
                }

                override fun onError(message: String, retryNext: Boolean) {
                    control.execute {
                        if (id != session.get()) return@execute
                        _uiState.value = VoiceUiState.Unavailable(message)
                    }
                }
            }
            try {
                engine.start(sink, _settings.value, vocabulary)
            } catch (t: Throwable) {
                _uiState.value = VoiceUiState.Unavailable("تعذر تشغيل الصوت، اكتب الأمر")
            }
        }
    }

    override fun stop() {
        control.execute {
            try {
                engine.stop()
            } catch (t: Throwable) {
            }
        }
    }

    override fun cancel() {
        session.incrementAndGet()
        control.execute {
            try {
                engine.cancel()
            } catch (t: Throwable) {
            }
            _uiState.value = VoiceUiState.Idle
        }
    }

    override fun preload(vocabulary: VocabularyList) {}

    override fun release() {
        session.incrementAndGet()
        control.execute {
            try {
                engine.release()
            } catch (t: Throwable) {
            }
            _uiState.value = VoiceUiState.Idle
            tts.release()
        }
    }

    override fun speak(text: String) {
        if (_settings.value.speakReplies) tts.speak(text)
    }

    override fun stopSpeaking() {
        tts.stop()
    }

    companion object {
        @Volatile private var instance: DefaultVoiceInput? = null

        fun get(app: Application): DefaultVoiceInput =
            instance ?: synchronized(this) { instance ?: DefaultVoiceInput(app).also { instance = it } }
    }
}
