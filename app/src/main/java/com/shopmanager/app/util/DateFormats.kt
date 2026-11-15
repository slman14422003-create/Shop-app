package com.shopmanager.app.util

import java.text.SimpleDateFormat
import java.util.HashMap
import java.util.Locale
import java.util.TimeZone

object DateFormats {
    private val cache = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
        override fun initialValue(): HashMap<String, SimpleDateFormat> = HashMap()
    }

    fun get(pattern: String, locale: Locale, lenient: Boolean = true): SimpleDateFormat {
        val map = cache.get()
        val key = pattern + "|" + locale.toLanguageTag() + "|" + lenient
        val f = map.getOrPut(key) {
            SimpleDateFormat(pattern, locale).apply { isLenient = lenient }
        }
        f.timeZone = TimeZone.getDefault()
        return f
    }
}
