package com.salman.herbalencyclopedia.data.translate

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val MAX_CONCURRENT_REQUESTS = 6

private val networkSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

class TranslationRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val cache: SharedPreferences by lazy {
        appContext.getSharedPreferences("translation_cache", Context.MODE_PRIVATE)
    }

    suspend fun translate(text: String, targetLang: String, sourceLang: String = "ar"): String {
        if (text.isBlank() || targetLang == sourceLang) return text

        val (key, cached) = withContext(Dispatchers.IO) {
            val k = cacheKey(text, sourceLang, targetLang)
            k to cache.getString(k, null)
        }
        if (cached != null) return cached

        val translated = networkSemaphore.withPermit {
            GoogleTranslateService.translate(text, targetLang, sourceLang)
        } ?: return text

        withContext(Dispatchers.IO) {
            cache.edit().putString(key, translated).apply()
        }
        return translated
    }

    private fun cacheKey(text: String, sourceLang: String, targetLang: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "$sourceLang-$targetLang:$hash"
    }

    companion object {
        @Volatile
        private var instance: TranslationRepository? = null

        fun shared(context: Context): TranslationRepository =
            instance ?: synchronized(this) {
                instance ?: TranslationRepository(context).also { instance = it }
            }
    }
}
