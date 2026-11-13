package com.shopmanager.app.assistant.voice

data class VoskCatalogEntry(
    val name: String,
    val lang: String,
    val type: String,
    val size: Long,
    val md5: String,
    val url: String,
    val obsolete: Boolean
)

object VoskCatalogLogic {
    fun budgetBytes(ramMb: Long): Long {
        val b = ramMb * 1024L * 1024L / 10L
        return b.coerceIn(40L * 1024 * 1024, 600L * 1024 * 1024)
    }

    fun chooseVoskModel(entries: List<VoskCatalogEntry>, ramMb: Long): VoskCatalogEntry? {
        val smallest = entries
            .filter { it.lang.equals("ar", ignoreCase = true) && it.type.equals("small", ignoreCase = true) && !it.obsolete && it.size > 0L && it.url.isNotBlank() }
            .minByOrNull { it.size } ?: return null
        return if (smallest.size <= budgetBytes(ramMb)) smallest else null
    }
}

object SegmentJoiner {
    data class Segment(val text: String, val gapBeforeMs: Long)

    fun join(segments: List<Segment>, commaGapMs: Long = 900L): String {
        val sb = StringBuilder()
        for (s in segments) {
            val t = s.text.trim()
            if (t.isEmpty()) continue
            if (sb.isNotEmpty()) {
                if (s.gapBeforeMs >= commaGapMs) sb.append(" ، ") else sb.append(' ')
            }
            sb.append(t)
        }
        return sb.toString()
    }
}

object WhisperPostFilter {
    private val HALLUCINATIONS = listOf(
        "اشتركوا في القناة", "اشترك في القناة", "شكرا للمشاهدة", "شكرا لمشاهدتكم", "ترجمة نانسي قنقر",
        "ترجمة", "subtitles by", "thanks for watching", "amara.org"
    )

    fun clean(raw: String): String {
        var t = raw.replace(Regex("\\[[^\\]]*\\]"), " ").replace(Regex("\\([^)]*\\)"), " ")
        t = t.replace("♪", " ").replace("‏", "").replace("‎", "")
        t = t.replace(Regex("\\s+"), " ").trim()
        val low = t.lowercase()
        for (h in HALLUCINATIONS) {
            if (low == h || low.trim('.', ' ', '!') == h) return ""
        }
        return t
    }
}
