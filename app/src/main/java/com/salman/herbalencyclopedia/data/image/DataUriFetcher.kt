package com.salman.herbalencyclopedia.data.image

import android.content.Context
import android.net.Uri
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

/**
 * Coil 2.x (المستخدم بهذا المشروع) لا يدعم روابط "data:" (صور base64)
 * إطلاقاً — هذا الدعم أُضيف فقط لاحقاً بإصدار Coil 3.x. بما أن صور
 * الأعشاب تُخزَّن كـ data URL كاملة داخل مستند Firestore (بدل رفعها
 * لـ Firebase Storage)، بدون هذا الـ Fetcher فإن AsyncImage لا يعرف
 * كيف يقرأ هذا الرابط إطلاقاً ولا يظهر أي شيء.
 *
 * ملاحظة مهمة: Coil يحوّل أي String تلقائياً إلى Uri (عبر مُحوِّل
 * داخلي StringMapper) قبل أن تصل البيانات لأي Fetcher. لذلك يجب أن
 * يكون هذا الـ Factory من نوع Fetcher.Factory<Uri> وليس
 * Fetcher.Factory<String> — وإلا فلن يُستدعى إطلاقاً مهما كان صحيحاً.
 */
class DataUriFetcher(
    private val dataUri: String,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val commaIndex = dataUri.indexOf(',')
        val header = dataUri.substring(5, commaIndex) // بعد "data:" وقبل الفاصلة
        val base64Data = dataUri.substring(commaIndex + 1)
        val mimeType = header.substringBefore(';').ifBlank { "image/jpeg" }
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)

        return SourceResult(
            source = ImageSource(Buffer().write(bytes), context),
            mimeType = mimeType,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uriString = data.toString()
            if (data.scheme != "data" || !uriString.contains(";base64,")) return null
            return DataUriFetcher(uriString, options.context)
        }
    }
}

