package com.shopmanager.app.assistant.engine

data class SpeechAlt(val text: String, val confidence: Float = 0f)

data class SpeechWord(val text: String, val confidence: Float = 1f)

data class SpeechResult(
    val text: String,
    val alternatives: List<SpeechAlt> = emptyList(),
    val words: List<SpeechWord> = emptyList(),
    val engine: String = ""
)

data class VocabularyList(val words: List<String>, val phrases: List<String> = emptyList())

fun VocabularyList.toGrammarJson(): String {
    val all = LinkedHashSet<String>()
    all.addAll(words)
    all.addAll(phrases)
    all.add("[unk]")
    val sb = StringBuilder("[")
    var first = true
    for (w in all) {
        if (!first) sb.append(',')
        first = false
        sb.append('"')
        for (ch in w) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n', '\r', '\t' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        sb.append('"')
    }
    sb.append(']')
    return sb.toString()
}

fun VocabularyList.toPrompt(maxChars: Int = 220): String {
    val sb = StringBuilder()
    for (w in words) {
        if (sb.length + w.length + 2 > maxChars) break
        if (sb.isNotEmpty()) sb.append("، ")
        sb.append(w)
    }
    return sb.toString()
}
