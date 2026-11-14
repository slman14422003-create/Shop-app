package com.shopmanager.app.data.chat

object ChatSearch {

    class Folded(val text: String, val map: IntArray)

    fun fold(src: String): Folded {
        val sb = StringBuilder(src.length)
        val map = IntArray(src.length)
        var n = 0
        for (i in src.indices) {
            val ch = src[i]
            if (ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' || ch == 'ء') continue
            val c: Char = when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى', 'ئ', 'ی' -> 'ي'
                'ة' -> 'ه'
                'ؤ' -> 'و'
                'ک' -> 'ك'
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> ch.lowercaseChar()
            }
            sb.append(c)
            map[n++] = i
        }
        return Folded(sb.toString(), map.copyOf(n))
    }

    private fun foldQuery(q: String): String =
        fold(q.trim()).text.replace(Regex("\\s+"), " ")

    fun ranges(text: String, query: String): List<IntRange> {
        val q = foldQuery(query)
        if (q.isEmpty() || text.isEmpty()) return emptyList()
        val f = fold(text)
        if (f.text.length < q.length) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (from <= f.text.length - q.length) {
            val at = f.text.indexOf(q, from)
            if (at < 0) break
            val start = f.map[at]
            val end = f.map[at + q.length - 1]
            out.add(start..end)
            from = at + q.length
        }
        return out
    }

    fun searchableText(m: ChatMessage): String = when (m.type) {
        MessageType.TEXT, MessageType.IMAGE -> m.text
        MessageType.VOICE -> ""
    }

    fun matchIds(messages: List<ChatMessage>, query: String): List<String> {
        if (foldQuery(query).isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (m in messages) {
            val t = searchableText(m)
            if (t.isNotEmpty() && ranges(t, query).isNotEmpty()) out.add(m.id)
        }
        return out
    }

    fun step(current: Int, total: Int, older: Boolean): Int {
        if (total <= 0) return -1
        if (current < 0 || current >= total) return total - 1
        return if (older) (current - 1 + total) % total else (current + 1) % total
    }
}
