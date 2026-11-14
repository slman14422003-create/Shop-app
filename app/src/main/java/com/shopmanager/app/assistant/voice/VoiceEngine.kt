package com.shopmanager.app.assistant.voice

import com.shopmanager.app.assistant.engine.SpeechResult
import com.shopmanager.app.assistant.engine.VocabularyList

internal interface EngineSink {
    fun onPartial(text: String)
    fun onLevel(level: Float)
    fun onProcessing()
    fun onResult(result: SpeechResult)
    fun onError(message: String, retryNext: Boolean)
}

internal interface VoiceEngine {
    fun prepare(vocabulary: VocabularyList): String?
    fun start(sink: EngineSink, settings: VoiceSettings, vocabulary: VocabularyList)
    fun stop()
    fun cancel()
    fun release()
}
