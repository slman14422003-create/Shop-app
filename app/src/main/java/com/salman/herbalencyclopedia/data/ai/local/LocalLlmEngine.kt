package com.salman.herbalencyclopedia.data.ai.local

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface LocalLlmEngine {
    val isLoaded: Boolean
    fun load(modelPath: String)
    fun generate(systemPrompt: String, userPrompt: String): Flow<String>
    fun release()
}

class LiteRtLmEngine(context: Context) : LocalLlmEngine {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var engine: Engine? = null

    override val isLoaded: Boolean
        get() = synchronized(lock) { engine != null }

    override fun load(modelPath: String) {
        synchronized(lock) {
            if (engine != null) return
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.path
            )
            val created = Engine(config)
            try {
                created.initialize()
            } catch (e: Throwable) {
                runCatching { created.close() }
                throw e
            }
            engine = created
        }
    }

    override fun generate(systemPrompt: String, userPrompt: String): Flow<String> = flow {
        val current = synchronized(lock) { engine } ?: throw IllegalStateException("model not loaded")
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.2)
        )
        current.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(userPrompt).collect { message ->
                emit(message.toString())
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            val current = engine
            engine = null
            if (current != null) runCatching { current.close() }
        }
    }
}
