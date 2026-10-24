package com.salman.herbalencyclopedia.data.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * عميل بسيط لواجهة ترجمة جوجل "المجانية" غير الرسمية (نفس الواجهة التي
 * يستخدمها امتداد المتصفح ومكتبات الترجمة المجانية المعروفة) — طلب GET
 * عادي بلا مفتاح API وبلا أي اعتماد إضافي (Gson/Retrofit) على المشروع:
 * فقط HttpURLConnection وorg.json المتوفرين أصلاً ضمن Android SDK.
 *
 * ملاحظات مهمة:
 *  - هذه واجهة غير رسمية وغير موثَّقة من جوجل، وقد تتوقف أو يتغيّر شكلها
 *    دون إشعار. عند فشلها [translate] تُعيد null بهدوء، فيستمر التطبيق
 *    بعرض النص الأصلي (عربي) بدل تعطّل الشاشة أو ظهور خطأ للمستخدم.
 *  - لا حاجة لمفتاح حساب أو فوترة، لكنها تخضع لتقييد غير موثَّق لعدد
 *    الطلبات من نفس عنوان IP؛ التخزين المؤقت في [TranslationRepository]
 *    يقلّل الاعتماد عليها لنفس النص لاحقاً.
 */
object GoogleTranslateService {

    suspend fun translate(text: String, targetLang: String, sourceLang: String = "ar"): String? =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext text
            runCatching {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    // مهم جداً: بعض الطلبات لهذه الواجهة غير الرسمية تُرفض أو
                    // تُحجب (رد فارغ/403) إن لم تحمل ترويسة User-Agent شبيهة
                    // بمتصفح حقيقي، لأن الطلبات بلا أي User-Agent (وهذا ما
                    // يرسله HttpURLConnection افتراضياً على أندرويد) تُميَّز
                    // بسهولة كطلبات آلية. هذا على الأرجح هو السبب الرئيسي وراء
                    // عدم ترجمة أي بيانات فعلياً رغم أن الكود يعمل بلا أخطاء.
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                }
                val body = try {
                    if (connection.responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        null
                    }
                } finally {
                    connection.disconnect()
                }
                body?.let(::parseTranslatedText)
            }.getOrNull()
        }

    /**
     * شكل الاستجابة: مصفوفة متداخلة مثل
     * [[["Translated part","Original part",null,null,...], ...], null, "ar"]
     * — النص الكامل قد يصل مقسّماً لعدة جمل/أجزاء، فنجمعها كلها بالترتيب.
     */
    private fun parseTranslatedText(json: String): String {
        val root = JSONArray(json)
        val sentences = root.getJSONArray(0)
        val builder = StringBuilder()
        for (i in 0 until sentences.length()) {
            val part = sentences.optJSONArray(i) ?: continue
            builder.append(part.optString(0, ""))
        }
        return builder.toString()
    }
}
