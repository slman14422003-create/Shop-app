package com.salman.herbalencyclopedia.data.image

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * يرفع صورة مضغوطة (WebP، من [ImageCompressor.compressToWebpBytes]) إلى
 * مستودع GitHub عام كملف حقيقي، عبر Cloudflare Worker وسيط (راجع
 * cloudflare-worker/index.js بجذر المشروع)، بدل تضمينها base64 داخل مستند
 * Firestore. هذا يحرّر مساحة Firestore المجانية (١ GiB) بالكامل تقريباً
 * لأن الصور تخرج من قاعدة البيانات كلياً (يبقى فيها فقط رابط نصي قصير)،
 * ويستفيد من مساحة GitHub + شبكة توصيل jsDelivr المجانيتين بلا أي حساب
 * فوترة إطلاقاً.
 *
 * أمان مهم: هذا الصف لا يحمل مفتاح GitHub إطلاقاً ولا يمكن أن يحمله - أي
 * قيمة مضمَّنة هنا ستكون قابلة للاستخراج من ملف الـ APK نفسه بفك الحزمة.
 * بدلاً من ذلك، يُرسِل فقط بايتات الصورة + توكن هوية Firebase للأدمن
 * الحالي المسجّل دخوله، والـ Worker (لا هذا التطبيق) هو من يتحقق من صحة
 * التوكن ومطابقته لمعرّف الأدمن الثابت قبل أي كتابة فعلية على GitHub، ثم
 * يستخدم مفتاحه الخاص (مخزَّن كسرّ على Cloudflare فقط) لإتمام الرفع.
 */
object GithubImageUploader {

    // رابط الـ Worker بعد نشره فعلياً (خطوة "wrangler deploy" في دليل
    // cloudflare-worker/README.md). لازم تعدّل هذا السطر بالرابط الحقيقي
    // الذي يطبعه wrangler عند النشر، وإلا سيفشل الرفع بصمت وتعمل الصور
    // بالطريقة القديمة (تضمين محلي) عبر التراجع التلقائي بشاشات الإدارة.
    private const val WORKER_URL = "https://soft-block-b285.slman14422003.workers.dev/upload"

    /**
     * يرفع [imageBytes] عبر الـ Worker باستخدام [idToken] (توكن هوية Firebase
     * الحالي للأدمن، وليس مفتاح GitHub). يرجع رابط jsDelivr الجاهز للعرض
     * المباشر عند النجاح، أو null عند أي فشل (بلا إنترنت، الـ Worker متوقف،
     * رفض التحقق من الهوية، ...) - الفشل هنا صامت عمداً لأن شاشات الإدارة
     * (AdminEditHerbScreen/AdminEditBlendScreen) تتعامل معه بالتراجع للتخزين
     * المحلي القديم بدل تعطيل الحفظ بالكامل.
     */
    suspend fun upload(imageBytes: ByteArray, idToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject()
                .put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                .toString()

            val connection = (URL(WORKER_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
            }

            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                return@runCatching null
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            JSONObject(responseText).optString("url").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
