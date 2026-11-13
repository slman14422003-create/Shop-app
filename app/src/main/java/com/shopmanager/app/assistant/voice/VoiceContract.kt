package com.shopmanager.app.assistant.voice

import android.app.Application
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class VoiceEngineKind { AUTO, SYSTEM, VOSK, WHISPER }

data class VoiceSettings(
    val preferred: VoiceEngineKind = VoiceEngineKind.AUTO,
    val allowMobileData: Boolean = false,
    val speakReplies: Boolean = true,
    val autoStopMs: Long = 1500L,
    val minLevel: Float = 0.015f,
    val autoSend: Boolean = true
)

data class ModelInfo(
    val id: String,
    val engine: VoiceEngineKind,
    val title: String,
    val sizeBytes: Long,
    val installed: Boolean,
    val installedBytes: Long
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Waiting(val modelId: String, val reason: String) : DownloadState
    data class Running(val modelId: String, val doneBytes: Long, val totalBytes: Long, val resumed: Boolean) : DownloadState
    data class Verifying(val modelId: String) : DownloadState
    data class Failed(val modelId: String, val message: String) : DownloadState
    data class Done(val modelId: String) : DownloadState
}

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data class NeedsModel(val modelId: String, val title: String, val sizeBytes: Long, val onWifi: Boolean) : VoiceUiState
    data class Downloading(val modelId: String, val doneBytes: Long, val totalBytes: Long) : VoiceUiState
    data object Preparing : VoiceUiState
    data class Listening(val partial: String, val level: Float) : VoiceUiState
    data object Processing : VoiceUiState
    data class Unavailable(val message: String) : VoiceUiState
}

interface VoiceInput {
    val uiState: StateFlow<VoiceUiState>
    val results: SharedFlow<SpeechResult>
    val settings: StateFlow<VoiceSettings>
    val models: StateFlow<List<ModelInfo>>
    val download: StateFlow<DownloadState>
    val activeEngine: StateFlow<VoiceEngineKind?>

    fun updateSettings(s: VoiceSettings)
    fun start(vocabulary: VocabularyList)
    fun stop()
    fun cancel()
    fun confirmDownload(allowMobileData: Boolean)
    fun dismissDownload()
    fun downloadModel(modelId: String)
    fun cancelDownload()
    fun deleteModel(modelId: String)
    fun preload(vocabulary: VocabularyList)
    fun release()
    fun speak(text: String)
    fun stopSpeaking()

    companion object {
        fun get(app: Application): VoiceInput = DefaultVoiceInput.get(app)
    }
}
