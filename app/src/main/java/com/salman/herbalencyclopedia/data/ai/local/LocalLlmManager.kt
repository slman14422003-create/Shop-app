package com.salman.herbalencyclopedia.data.ai.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.salman.herbalencyclopedia.data.ai.hasActiveInternetConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object LocalLlmManager {

    enum class Phase { NOT_INSTALLED, DOWNLOADING, INSTALLED }

    data class Status(
        val phase: Phase = Phase.NOT_INSTALLED,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val errorCode: String? = null,
        val enabled: Boolean = false,
        val modelId: String = LocalLlmModels.default.id,
        val installedBytes: Long = 0L,
        val hasToken: Boolean = false
    )

    const val MIN_TOTAL_RAM_BYTES = 3_500L * 1024 * 1024
    private const val MIN_AVAILABLE_RAM_BYTES = 900L * 1024 * 1024
    private const val MIN_VALID_FILE_BYTES = 20L * 1024 * 1024

    const val ERR_OFFLINE = "offline"
    const val ERR_AUTH = "auth"
    const val ERR_SPACE = "space"
    const val ERR_NETWORK = "network"
    const val ERR_SIZE = "size"
    const val ERR_OTHER = "other"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()
    private val downloader = LocalLlmDownloader()
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var store: LocalLlmStore? = null
    @Volatile private var engine: LocalLlmEngine? = null
    private var downloadJob: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        store = LocalLlmStore(context.applicationContext)
        refresh()
    }

    private fun ctx(): Context = appContext ?: throw IllegalStateException("LocalLlmManager not initialised")

    fun selectedSpec(): LocalModelSpec = LocalLlmModels.byId(store?.modelId)

    fun modelFile(spec: LocalModelSpec): File = File(File(ctx().filesDir, "local_llm"), spec.fileName)

    fun partFile(spec: LocalModelSpec): File = File(File(ctx().filesDir, "local_llm"), spec.fileName + ".part")

    fun isInstalled(spec: LocalModelSpec): Boolean {
        val f = modelFile(spec)
        return f.exists() && f.length() >= MIN_VALID_FILE_BYTES
    }

    fun installedBytes(spec: LocalModelSpec): Long = if (isInstalled(spec)) modelFile(spec).length() else 0L

    fun supportReason(context: Context, ecoMode: Boolean): String? {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) return "الجهاز غير مدعوم: يتطلب معالج ARM بـ64 بت"
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            if (am.isLowRamDevice) return "الجهاز ضعيف الذاكرة"
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            if (info.totalMem < MIN_TOTAL_RAM_BYTES) return "الذاكرة أقل من 4 غيغابايت"
        }
        if (ecoMode) return "غير متاح في وضع الأداء الاقتصادي"
        return null
    }

    fun refresh() {
        val st = store ?: return
        val spec = LocalLlmModels.byId(st.modelId)
        val current = _status.value
        val downloading = current.phase == Phase.DOWNLOADING
        _status.value = current.copy(
            phase = if (downloading) Phase.DOWNLOADING else if (isInstalled(spec)) Phase.INSTALLED else Phase.NOT_INSTALLED,
            enabled = st.enabled,
            modelId = spec.id,
            installedBytes = installedBytes(spec),
            hasToken = st.hfToken.isNotBlank()
        )
    }

    fun setEnabled(value: Boolean) {
        store?.enabled = value
        if (!value) release()
        refresh()
    }

    fun selectModel(id: String) {
        val st = store ?: return
        if (st.modelId == id) return
        cancelDownload()
        release()
        st.modelId = id
        _status.value = _status.value.copy(errorCode = null, downloadedBytes = 0L, totalBytes = 0L)
        refresh()
    }

    fun setToken(token: String) {
        store?.hfToken = token
        refresh()
    }

    fun token(): String = store?.hfToken ?: ""

    fun startDownload() {
        val st = store ?: return
        if (_status.value.phase == Phase.DOWNLOADING) return
        val spec = LocalLlmModels.byId(st.modelId)
        if (!hasActiveInternetConnection(ctx())) {
            _status.value = _status.value.copy(errorCode = ERR_OFFLINE)
            return
        }
        _status.value = _status.value.copy(
            phase = Phase.DOWNLOADING,
            errorCode = null,
            downloadedBytes = if (partFile(spec).exists()) partFile(spec).length() else 0L,
            totalBytes = spec.approxSizeMb * 1024L * 1024L
        )
        downloadJob = scope.launch {
            try {
                downloader.download(spec, st.hfToken, modelFile(spec)) { done, total ->
                    _status.value = _status.value.copy(downloadedBytes = done, totalBytes = total)
                }
                _status.value = _status.value.copy(phase = Phase.NOT_INSTALLED, errorCode = null)
                st.enabled = true
                refresh()
            } catch (e: CancellationException) {
                _status.value = _status.value.copy(phase = Phase.NOT_INSTALLED)
                refresh()
                throw e
            } catch (e: LocalLlmDownloader.DownloadException) {
                val code = when (e.kind) {
                    LocalLlmDownloader.DownloadException.Kind.AUTH -> ERR_AUTH
                    LocalLlmDownloader.DownloadException.Kind.NO_SPACE -> ERR_SPACE
                    LocalLlmDownloader.DownloadException.Kind.NETWORK -> ERR_NETWORK
                    LocalLlmDownloader.DownloadException.Kind.SIZE_MISMATCH -> ERR_SIZE
                    LocalLlmDownloader.DownloadException.Kind.HTTP -> ERR_NETWORK
                }
                _status.value = _status.value.copy(phase = Phase.NOT_INSTALLED, errorCode = code)
                refresh()
            } catch (e: Throwable) {
                _status.value = _status.value.copy(phase = Phase.NOT_INSTALLED, errorCode = ERR_OTHER)
                refresh()
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun deleteModel() {
        val st = store ?: return
        val spec = LocalLlmModels.byId(st.modelId)
        cancelDownload()
        release()
        scope.launch {
            runCatching { modelFile(spec).delete() }
            runCatching { partFile(spec).delete() }
            st.enabled = false
            _status.value = _status.value.copy(
                phase = Phase.NOT_INSTALLED, errorCode = null, downloadedBytes = 0L, totalBytes = 0L
            )
            refresh()
        }
    }

    fun isUsable(context: Context, ecoMode: Boolean): Boolean {
        val st = store ?: return false
        if (!st.enabled) return false
        if (supportReason(context, ecoMode) != null) return false
        return isInstalled(LocalLlmModels.byId(st.modelId))
    }

    private fun hasMemoryHeadroom(): Boolean {
        val am = ctx().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem >= MIN_AVAILABLE_RAM_BYTES
    }

    suspend fun loadIfNeeded(): LocalLlmEngine? = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            val current = engine
            if (current != null && current.isLoaded) return@withLock current
            val spec = selectedSpec()
            if (!isInstalled(spec)) return@withLock null
            if (!hasMemoryHeadroom()) return@withLock null
            try {
                val created = current ?: LiteRtLmEngine(ctx())
                created.load(modelFile(spec).absolutePath)
                engine = created
                created
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                engine?.release()
                engine = null
                null
            }
        }
    }

    fun release() {
        val current = engine
        engine = null
        if (current != null) runCatching { current.release() }
    }
}
