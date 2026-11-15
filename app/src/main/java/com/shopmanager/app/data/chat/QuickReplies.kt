package com.shopmanager.app.data.chat

object QuickReplies {

    val DEFAULTS = listOf("تمام", "أرسل لك السعر", "وصلت الطلبية")
    const val MAX_COUNT = 12
    const val MAX_LEN = 60

    fun clean(list: List<String>): List<String> =
        list.map { it.replace(Regex("\\s+"), " ").trim().take(MAX_LEN) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_COUNT)

    fun encode(list: List<String>): String = clean(list).joinToString("\n")

    fun decode(raw: String?): List<String> =
        if (raw == null) DEFAULTS else clean(raw.split('\n'))

    fun add(list: List<String>, text: String): List<String> = clean(list + text)

    fun update(list: List<String>, index: Int, text: String): List<String> {
        if (index !in list.indices) return list
        val next = list.toMutableList()
        next[index] = text
        return clean(next)
    }

    fun remove(list: List<String>, index: Int): List<String> {
        if (index !in list.indices) return list
        return list.filterIndexed { i, _ -> i != index }
    }
}
