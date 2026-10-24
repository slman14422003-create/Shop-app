package com.salman.herbalencyclopedia.data.ai.local

data class LocalModelSpec(
    val id: String,
    val label: String,
    val fileName: String,
    val url: String,
    val requiresToken: Boolean,
    val approxSizeMb: Int,
    val stripThinking: Boolean
)

object LocalLlmModels {

    val GEMMA3_1B = LocalModelSpec(
        id = "gemma3_1b",
        label = "Gemma 3 1B",
        fileName = "gemma3-1b-it-int4.litertlm",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
        requiresToken = true,
        approxSizeMb = 584,
        stripThinking = false
    )

    val QWEN3_06B = LocalModelSpec(
        id = "qwen3_06b",
        label = "Qwen3 0.6B",
        fileName = "qwen3_0_6b_mixed_int4.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
        requiresToken = false,
        approxSizeMb = 400,
        stripThinking = true
    )

    val all: List<LocalModelSpec> = listOf(GEMMA3_1B, QWEN3_06B)

    val default: LocalModelSpec = GEMMA3_1B

    fun byId(id: String?): LocalModelSpec = all.firstOrNull { it.id == id } ?: default
}
