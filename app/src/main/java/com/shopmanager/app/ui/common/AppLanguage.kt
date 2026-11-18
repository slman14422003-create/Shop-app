package com.shopmanager.app.ui.common

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection

enum class Lang(val code: String, val nativeName: String) {
    AR("ar", "العربية"),
    EN("en", "English")
}

object AppLanguage {
    private const val PREFS = "app_language_prefs"
    private const val KEY = "lang"

    var current by mutableStateOf(Lang.AR)
        private set

    val direction: LayoutDirection
        get() = if (current == Lang.EN) LayoutDirection.Ltr else LayoutDirection.Rtl

    private val cache = LruCache<String, String>(3000)
    private val entries: List<Pair<String, String>> by lazy { EN_STRINGS }

    fun init(context: Context) {
        val code = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current = Lang.entries.firstOrNull { it.code == code } ?: Lang.AR
    }

    fun set(context: Context, lang: Lang) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang.code).apply()
        current = lang
    }

    fun translate(text: String): String {
        if (current != Lang.EN || text.isEmpty()) return text
        cache.get(text)?.let { return it }
        val out = convert(text)
        cache.put(text, out)
        return out
    }

    private fun isArabicLetter(c: Char) = c in 'ء'..'ي' || c in 'ٱ'..'ۓ'

    private fun hasArabic(text: String): Boolean {
        for (c in text) if (isArabicLetter(c)) return true
        return false
    }

    private fun convert(text: String): String {
        if (!hasArabic(text)) return text
        var s = text
        for ((ar, en) in entries) {
            var from = 0
            while (true) {
                val i = s.indexOf(ar, from)
                if (i < 0) break
                val end = i + ar.length
                val okBefore = i == 0 || !isArabicLetter(s[i - 1])
                val okAfter = end >= s.length || !isArabicLetter(s[end])
                if (okBefore && okAfter) {
                    s = s.substring(0, i) + en + s.substring(end)
                    from = i + en.length
                } else {
                    from = i + 1
                }
            }
            if (!hasArabic(s)) break
        }
        return s.replace("،", ",").replace("؟", "?").replace("؛", ";")
    }
}
