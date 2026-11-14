package com.shopmanager.app.data.chat

enum class ConvertKind { SHORTAGE, NOTE, DEBT }

data class ConvertOption(val label: String, val text: String)

sealed interface ConversionPlan {
    data class Ready(val kind: ConvertKind, val summary: String, val handle: Any) : ConversionPlan
    data class Suggest(val kind: ConvertKind, val message: String, val options: List<ConvertOption>) : ConversionPlan
    data class Missing(val kind: ConvertKind, val message: String) : ConversionPlan
}

class ConversionResult(
    val title: String,
    val details: String,
    val success: Boolean,
    val undo: (suspend () -> Unit)? = null
)

interface MessageConverter {
    suspend fun plan(kind: ConvertKind, text: String): ConversionPlan
    suspend fun apply(plan: ConversionPlan.Ready): ConversionResult
}
