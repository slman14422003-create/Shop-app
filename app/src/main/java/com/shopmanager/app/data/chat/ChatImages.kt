package com.shopmanager.app.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageSizing {

    const val MAX_DIM = 1024
    const val MAX_BYTES = 80_000
    const val MIN_QUALITY = 40
    const val START_QUALITY = 80

    fun scaledSize(w: Int, h: Int, maxDim: Int = MAX_DIM): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1 to 1
        val longest = max(w, h)
        if (longest <= maxDim) return w to h
        val f = maxDim.toFloat() / longest
        return max(1, (w * f).roundToInt()) to max(1, (h * f).roundToInt())
    }

    fun sampleSize(w: Int, h: Int, targetDim: Int): Int {
        if (w <= 0 || h <= 0 || targetDim <= 0) return 1
        var s = 1
        val longest = max(w, h)
        while (longest / (s * 2) >= targetDim) s *= 2
        return s
    }

    fun nextQuality(q: Int): Int = max(MIN_QUALITY, q - 10)

    fun displaySize(w: Int, h: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return maxW to min(maxW, maxH)
        val f = min(maxW.toFloat() / w, maxH.toFloat() / h)
        return max(1, (w * f).roundToInt()) to max(1, (h * f).roundToInt())
    }
}

class EncodedImage(val b64: String, val width: Int, val height: Int)

object ChatImageCodec {

    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    init {
        com.shopmanager.app.data.performance.BitmapCaches.register { cache.evictAll() }
    }

    /** حجم الكاش = ~8% من سقف heap التطبيق (memoryClass) بدل رقم ثابت. */
    fun configure(context: Context) {
        cache.resize(com.shopmanager.app.data.performance.BitmapCaches.budgetBytes(context, 0.08f))
    }

    fun cached(key: String): Bitmap? = cache.get(key)

    suspend fun encode(context: Context, uri: Uri): EncodedImage? = withContext(Dispatchers.IO) {
        try {
            encodeBlocking(context.applicationContext, uri)
        } catch (t: Throwable) {
            android.util.Log.e("ChatImageCodec", "encode failed", t)
            null
        }
    }

    /**
     * يقرأ الصورة مرة واحدة إلى الذاكرة (بعض مزودات المحتوى/منتقي الصور لا تتحمل فتح الـ stream
     * أكثر من مرة)، ثم يفك الترميز بـ BitmapFactory وإن فشل (HEIC/AVIF…) يجرّب ImageDecoder.
     * حلقة الضغط تقلّل الأبعاد فعلياً في كل دورة بعد الجودة الدنيا، فلا تعود null إلا لو تعذّر فك الصورة نفسها.
     */
    private fun encodeBlocking(context: Context, uri: Uri): EncodedImage? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty()) return null

        var bmp: Bitmap? = null
        var orientationHandled = false

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, ImageSizing.MAX_DIM)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
        }
        if (bmp == null && Build.VERSION.SDK_INT >= 28) {
            bmp = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val (tw, th) = ImageSizing.scaledSize(info.size.width, info.size.height)
                    decoder.setTargetSize(tw, th)
                }
            }.getOrNull()
            orientationHandled = bmp != null // ImageDecoder يطبّق اتجاه EXIF بنفسه
        }
        var current: Bitmap = bmp ?: return null

        if (!orientationHandled) {
            val degrees = runCatching {
                when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                val rotated = runCatching {
                    Bitmap.createBitmap(current, 0, 0, current.width, current.height, m, true)
                }.getOrNull()
                if (rotated != null && rotated !== current) {
                    current.recycle()
                    current = rotated
                }
            }
        }

        var quality = ImageSizing.START_QUALITY
        var dim = ImageSizing.MAX_DIM
        var last: EncodedImage? = null
        for (attempt in 0 until 16) {
            val (tw, th) = ImageSizing.scaledSize(current.width, current.height, dim)
            val flat = flatten(current, tw, th)
            val out = ByteArrayOutputStream()
            flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val jpeg = out.toByteArray()
            val w = flat.width
            val h = flat.height
            flat.recycle()
            last = EncodedImage(Base64.encodeToString(jpeg, Base64.NO_WRAP), w, h)
            if (jpeg.size <= ImageSizing.MAX_BYTES) break
            if (quality > ImageSizing.MIN_QUALITY) {
                quality = ImageSizing.nextQuality(quality)
            } else {
                dim = max(160, (dim * 0.8f).roundToInt())
            }
        }
        current.recycle()
        return last
    }

    private fun flatten(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val dst = android.graphics.Rect(0, 0, w, h)
        canvas.drawBitmap(src, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun decode(key: String, b64: String, targetDim: Int): Bitmap? {
        val config = com.shopmanager.app.data.performance.PerformanceRuntime.thumbnailConfig
        val cacheKey = "$key@$targetDim@${config.name}"
        cache.get(cacheKey)?.let { return it }
        if (b64.isEmpty()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, targetDim)
                inPreferredConfig = config
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (bmp != null) cache.put(cacheKey, bmp)
            bmp
        } catch (_: Throwable) {
            null
        }
    }
}
