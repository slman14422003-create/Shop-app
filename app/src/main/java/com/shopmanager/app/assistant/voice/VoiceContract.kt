package com.shopmanager.app.assistant.voice

import android.app.Application
import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class VoiceSettings(
    val speakReplies: Boolean = true,
    val autoStopMs: Long = 1500L,
    val autoSend: Boolean = true
)

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data object Preparing : VoiceUiState
    data class Listening(val partial: String, val level: Float) : VoiceUiState
    data object Processing : VoiceUiState
    data class Unavailable(val message: String) : VoiceUiState
}

interface VoiceInput {
    val uiState: StateFlow<VoiceUiState>
    val results: SharedFlow<SpeechResult>
    val settings: StateFlow<VoiceSettings>

    fun updateSettings(s: VoiceSettings)
    fun start(vocabulary: VocabularyList)
    fun stop()
    fun cancel()
    fun preload(vocabulary: VocabularyList)
    fun release()
    fun speak(text: String)
    fun stopSpeaking()

    companion object {
        fun get(app: Application): VoiceInput = DefaultVoiceInput.get(app)
    }
}
