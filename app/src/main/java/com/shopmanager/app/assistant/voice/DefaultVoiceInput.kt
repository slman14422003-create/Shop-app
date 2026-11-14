package com.shopmanager.app.assistant.voice

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class DefaultVoiceInput private constructor(private val app: Application) : VoiceInput {
    private val prefs = app.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
    private val manager = ModelManager(app)
    private val gate = NetworkGate(app)
    private val downloader = ModelDownloader(gate)
    private val tts = TtsSpeaker(app)
    private val control = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "voice-control").apply { isDaemon = true }
    }
    private val downloadExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "voice-download").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    override val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
    private val _results = MutableSharedFlow<SpeechResult>(extraBufferCapacity = 8)
    override val results: SharedFlow<SpeechResult> = _results
    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()
    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    override val download: StateFlow<DownloadState> = _download.asStateFlow()
    private val _active = MutableStateFlow<VoiceEngineKind?>(null)
    override val activeEngine: StateFlow<VoiceEngineKind?> = _active.asStateFlow()

    private val ramMb: Long by lazy {
        try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.totalMem / (1024L * 1024L)
        } catch (t: Throwable) {
            2048L
        }
    }

    @Volatile private var onDeviceAvailable: Boolean? = null
    @Volatile private var localeOk: Boolean? = null

    private fun profile(): DeviceProfile {
        val avail = onDeviceAvailable ?: SystemSpeechEngine.onDeviceAvailable(app).also { onDeviceAvailable = it }
        return DeviceProfile(
            ramMb = ramMb,
            cores = Runtime.getRuntime().availableProcessors(),
            sdk = Build.VERSION.SDK_INT,
            is64bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            onDeviceRecognizerAvailable = avail,
            localeSupported = if (avail) (localeOk ?: true) else false
        )
    }

    private fun deviceClass(): DeviceClass = EngineSelector.deviceClass(profile())

    private val engines = HashMap<VoiceEngineKind, VoiceEngine>()

    private fun engine(kind: VoiceEngineKind): VoiceEngine = synchronized(engines) {
        engines.getOrPut(kind) {
            when (kind) {
                VoiceEngineKind.SYSTEM -> SystemSpeechEngine(app)
                VoiceEngineKind.VOSK -> VoskEngine(manager)
                else -> WhisperEngine(manager) { deviceClass() }
            }
        }
    }

    private val session = AtomicInteger(0)
    @Volatile private var currentEngine: VoiceEngine? = null
    private var idleRelease: ScheduledFuture<*>? = null
    private var lastVocabulary = VocabularyList(emptyList())
    private val downloadCancel = AtomicBoolean(false)
    @Volatile private var pendingVocabulary: VocabularyList? = null
    @Volatile private var downloading = false

    init {
        control.execute { refreshModels() }
    }

    private fun loadSettings(): VoiceSettings {
        val pref = try {
            VoiceEngineKind.valueOf(prefs.getString("preferred", VoiceEngineKind.AUTO.name) ?: VoiceEngineKind.AUTO.name)
        } catch (t: Throwable) {
            VoiceEngineKind.AUTO
        }
        return VoiceSettings(
            preferred = pref,
            allowMobileData = prefs.getBoolean("allowMobileData", false),
            speakReplies = prefs.getBoolean("speakReplies", true),
            autoStopMs = prefs.getLong("autoStopMs", 1500L).coerceIn(600L, 5000L),
            minLevel = prefs.getFloat("minLevel", 0.015f).coerceIn(0.004f, 0.1f),
            autoSend = prefs.getBoolean("autoSend", true)
        )
    }

    override fun updateSettings(s: VoiceSettings) {
        _settings.value = s
        prefs.edit()
            .putString("preferred", s.preferred.name)
            .putBoolean("allowMobileData", s.allowMobileData)
            .putBoolean("speakReplies", s.speakReplies)
            .putLong("autoStopMs", s.autoStopMs)
            .putFloat("minLevel", s.minLevel)
            .putBoolean("autoSend", s.autoSend)
            .apply()
    }

    private fun refreshModels() {
        try {
            _models.value = manager.list()
        } catch (t: Throwable) {
        }
    }

    private fun isReady(kind: VoiceEngineKind): Boolean = when (kind) {
        VoiceEngineKind.SYSTEM -> true
        VoiceEngineKind.VOSK -> manager.installedVoskDir() != null
        VoiceEngineKind.WHISPER -> manager.whisperInstalled() && WhisperBridge.available()
        VoiceEngineKind.AUTO -> false
    }

    private fun readyChain(): List<VoiceEngineKind> {
        val s = _settings.value
        val order = EngineSelector.order(profile(), s.preferred, voskUsable = true, whisperUsable = true)
        return order.filter { isReady(it) }
    }

    private fun scheduleIdleRelease() {
        idleRelease?.cancel(false)
        val delay = if (deviceClass() == DeviceClass.LOW) 20L else 120L
        idleRelease = control.schedule({ releaseEngines() }, delay, TimeUnit.SECONDS)
    }

    private fun cancelIdleRelease() {
        idleRelease?.cancel(false)
        idleRelease = null
    }

    private fun releaseEngines() {
        if (_uiState.value is VoiceUiState.Listening || _uiState.value is VoiceUiState.Processing || _uiState.value is VoiceUiState.Preparing) return
        val list = synchronized(engines) { engines.values.toList().also { engines.clear() } }
        for (e in list) {
            try {
                e.release()
            } catch (t: Throwable) {
            }
        }
        currentEngine = null
        _active.value = null
    }

    override fun start(vocabulary: VocabularyList) {
        val id = session.incrementAndGet()
        control.execute {
            if (id != session.get()) return@execute
            lastVocabulary = vocabulary
            cancelIdleRelease()
            val prior = currentEngine
            if (prior != null) {
                try {
                    prior.cancel()
                } catch (t: Throwable) {
                }
            }
            tts.stop()
            val chain = readyChain()
            if (chain.isEmpty()) {
                needModel(id, vocabulary)
                return@execute
            }
            _uiState.value = VoiceUiState.Preparing
            runChain(id, chain, 0, vocabulary)
        }
    }

    private fun needModel(id: Int, vocabulary: VocabularyList) {
        pendingVocabulary = vocabulary
        val s = _settings.value
        var targetId = WHISPER_MODEL_ID
        var title = if (manager.whisperVariant == "small") "Whisper Small مضغوط (دقة أعلى)" else "Whisper Base مضغوط"
        var size = manager.whisperHeadSize()
        val voskAllowed = !manager.voskRejected() && EngineSelector.order(profile(), s.preferred, true, true).contains(VoiceEngineKind.VOSK)
        val preferVosk = voskAllowed && s.preferred == VoiceEngineKind.VOSK
        if (preferVosk) {
            var entry = manager.voskEntry()
            if (entry == null && gate.isAllowed(s.allowMobileData)) {
                entry = try {
                    manager.resolveVosk(ramMb)
                } catch (t: Throwable) {
                    null
                }
            }
            if (entry != null) {
                targetId = VOSK_MODEL_ID
                title = "Vosk عربي صغير (${entry.name})"
                size = entry.size
            }
        }
        if (targetId == WHISPER_MODEL_ID && size <= 0L && gate.isAllowed(s.allowMobileData)) {
            try {
                val r = HttpSupport.resolve(manager.whisperUrl)
                if (r.totalSize > 0L) {
                    size = r.totalSize
                    manager.saveWhisperHead(size, DownloadMath.sha256FromEtag(r.linkedEtag))
                }
            } catch (t: Throwable) {
            }
        }
        if (id != session.get()) return
        refreshModels()
        _uiState.value = VoiceUiState.NeedsModel(targetId, title, size, gate.isOnWifi())
    }

    private fun runChain(id: Int, chain: List<VoiceEngineKind>, index: Int, vocabulary: VocabularyList) {
        if (id != session.get()) return
        if (index >= chain.size) {
            _active.value = null
            if (chain.none { it != VoiceEngineKind.SYSTEM } && !manager.whisperInstalled() && manager.installedVoskDir() == null) {
                needModel(id, vocabulary)
            } else {
                _uiState.value = VoiceUiState.Unavailable("الصوت غير متوفر حالياً، اكتب الأمر")
            }
            scheduleIdleRelease()
            return
        }
        val kind = chain[index]
        val e = engine(kind)
        val err = try {
            e.prepare(vocabulary)
        } catch (t: Throwable) {
            "تعذر التحضير"
        }
        if (id != session.get()) return
        if (err != null) {
            runChain(id, chain, index + 1, vocabulary)
            return
        }
        currentEngine = e
        _active.value = kind
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
                    scheduleIdleRelease()
                }
            }

            override fun onError(message: String, retryNext: Boolean) {
                control.execute {
                    if (id != session.get()) return@execute
                    if (retryNext) {
                        try {
                            e.cancel()
                        } catch (t: Throwable) {
                        }
                        runChain(id, chain, index + 1, vocabulary)
                    } else {
                        _active.value = null
                        _uiState.value = VoiceUiState.Unavailable(message)
                        scheduleIdleRelease()
                    }
                }
            }
        }
        try {
            e.start(sink, _settings.value, vocabulary)
        } catch (t: Throwable) {
            runChain(id, chain, index + 1, vocabulary)
        }
    }

    override fun stop() {
        control.execute {
            try {
                currentEngine?.stop()
            } catch (t: Throwable) {
            }
        }
    }

    override fun cancel() {
        session.incrementAndGet()
        control.execute {
            try {
                currentEngine?.cancel()
            } catch (t: Throwable) {
            }
            if (_uiState.value !is VoiceUiState.Downloading && _uiState.value !is VoiceUiState.NeedsModel) _uiState.value = VoiceUiState.Idle
            scheduleIdleRelease()
        }
    }

    override fun confirmDownload(allowMobileData: Boolean) {
        val st = _uiState.value as? VoiceUiState.NeedsModel ?: return
        if (allowMobileData && !_settings.value.allowMobileData) updateSettings(_settings.value.copy(allowMobileData = true))
        startDownload(st.modelId, allowMobileData || _settings.value.allowMobileData, true)
    }

    override fun dismissDownload() {
        if (_uiState.value is VoiceUiState.NeedsModel) _uiState.value = VoiceUiState.Idle
        pendingVocabulary = null
    }

    override fun downloadModel(modelId: String) {
        startDownload(modelId, _settings.value.allowMobileData, false)
    }

    override fun cancelDownload() {
        downloadCancel.set(true)
    }

    override fun deleteModel(modelId: String) {
        control.execute {
            try {
                if (modelId == VOSK_MODEL_ID) synchronized(engines) { engines.remove(VoiceEngineKind.VOSK) }?.release()
                if (modelId == WHISPER_MODEL_ID) synchronized(engines) { engines.remove(VoiceEngineKind.WHISPER) }?.release()
                manager.delete(modelId)
            } catch (t: Throwable) {
            }
            refreshModels()
        }
    }

    private fun startDownload(modelId: String, allowMobile: Boolean, resumeVoiceAfter: Boolean) {
        if (downloading) return
        downloading = true
        downloadCancel.set(false)
        downloadExecutor.execute {
            try {
                runDownload(modelId, allowMobile, resumeVoiceAfter)
            } catch (t: Throwable) {
                _download.value = DownloadState.Failed(modelId, "تعذر التحميل")
                if (resumeVoiceAfter) _uiState.value = VoiceUiState.Unavailable("تعذر تنزيل النموذج، اكتب الأمر")
            } finally {
                downloading = false
                refreshModels()
            }
        }
    }

    private fun runDownload(modelId: String, allowMobile: Boolean, resumeVoiceAfter: Boolean) {
        var id = modelId
        var request: DownloadRequest
        var voskEntry: VoskCatalogEntry? = null
        if (id == VOSK_MODEL_ID) {
            voskEntry = manager.voskEntry() ?: try {
                manager.resolveVosk(ramMb)
            } catch (t: Throwable) {
                null
            }
            if (voskEntry == null) {
                id = WHISPER_MODEL_ID
            }
        }
        if (id == VOSK_MODEL_ID && voskEntry != null) {
            val dir = manager.downloadDirFor(VOSK_MODEL_ID)
            request = DownloadRequest(
                VOSK_MODEL_ID, voskEntry.url, java.io.File(dir, "model.zip"),
                null, voskEntry.md5.ifBlank { null }, voskEntry.size, false
            )
        } else {
            var size = manager.whisperHeadSize()
            var sha = manager.whisperHeadSha()
            if (size <= 0L) {
                try {
                    if (!gate.isAllowed(allowMobile)) {
                        _download.value = DownloadState.Waiting(WHISPER_MODEL_ID, "wifi")
                        if (!gate.awaitAllowed(allowMobile, downloadCancel)) {
                            _download.value = DownloadState.Idle
                            if (resumeVoiceAfter) _uiState.value = VoiceUiState.Idle
                            return
                        }
                    }
                    val r = HttpSupport.resolve(manager.whisperUrl)
                    size = r.totalSize
                    sha = DownloadMath.sha256FromEtag(r.linkedEtag)
                    if (size > 0L) manager.saveWhisperHead(size, sha)
                } catch (t: Throwable) {
                }
            }
            request = DownloadRequest(WHISPER_MODEL_ID, manager.whisperUrl, manager.whisperFile, sha, null, size, true)
        }
        val finalId = request.modelId
        val outcome = downloader.download(request, allowMobile, downloadCancel) { st ->
            _download.value = st
            when (st) {
                is DownloadState.Running -> if (resumeVoiceAfter) _uiState.value = VoiceUiState.Downloading(st.modelId, st.doneBytes, st.totalBytes)
                is DownloadState.Waiting -> if (resumeVoiceAfter) _uiState.value = VoiceUiState.Downloading(st.modelId, 0L, 0L)
                else -> {}
            }
        }
        when (outcome) {
            is DownloadOutcome.Success -> {
                var ok = true
                if (finalId == VOSK_MODEL_ID && voskEntry != null) {
                    _download.value = DownloadState.Verifying(finalId)
                    ok = manager.installVoskZip(request.dest, voskEntry) { downloadCancel.get() }
                    try {
                        request.dest.delete()
                        java.io.File(request.dest.parentFile, request.dest.name + ".meta").delete()
                    } catch (t: Throwable) {
                    }
                }
                if (ok) {
                    _download.value = DownloadState.Done(finalId)
                    refreshModels()
                    val vocab = pendingVocabulary
                    pendingVocabulary = null
                    if (resumeVoiceAfter) {
                        _uiState.value = VoiceUiState.Idle
                        if (vocab != null) start(vocab)
                    }
                } else {
                    _download.value = DownloadState.Failed(finalId, "فشل تثبيت النموذج")
                    if (finalId == VOSK_MODEL_ID) manager.markVoskRejected()
                    if (resumeVoiceAfter) _uiState.value = VoiceUiState.Unavailable("تعذر تثبيت النموذج، اكتب الأمر")
                }
            }
            is DownloadOutcome.Cancelled -> {
                _download.value = DownloadState.Idle
                if (resumeVoiceAfter) _uiState.value = VoiceUiState.Idle
            }
            is DownloadOutcome.Failed -> {
                _download.value = DownloadState.Failed(finalId, outcome.message)
                if (resumeVoiceAfter) _uiState.value = VoiceUiState.Unavailable("تعذر تنزيل النموذج، اكتب الأمر")
            }
        }
    }

    override fun preload(vocabulary: VocabularyList) {
        control.execute {
            lastVocabulary = vocabulary
            val chain = readyChain().filter { it != VoiceEngineKind.SYSTEM }
            val first = chain.firstOrNull() ?: return@execute
            try {
                engine(first).prepare(vocabulary)
            } catch (t: Throwable) {
            }
            scheduleIdleRelease()
        }
    }

    override fun release() {
        session.incrementAndGet()
        control.execute {
            try {
                currentEngine?.cancel()
            } catch (t: Throwable) {
            }
            _uiState.value = VoiceUiState.Idle
            releaseEngines()
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
