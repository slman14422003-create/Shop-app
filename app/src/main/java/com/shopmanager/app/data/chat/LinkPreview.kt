package com.shopmanager.app.data.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

data class LinkMeta(val url: String, val host: String, val title: String, val imageUrl: String?)

object LinkExtractor {

    private val URL_REGEX = Regex("(?i)\\bhttps?://[^\\s<>\"']+")
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '،', '؛', '؟', '»')

    fun firstUrl(text: String): String? {
        val m = URL_REGEX.find(text) ?: return null
        val cleaned = m.value.trimEnd(*TRAILING)
        return cleaned.takeIf { it.length > 10 }
    }

    fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}

object HtmlMeta {

    private val META_TAG = Regex("(?is)<meta\\s+[^>]*>")
    private val ATTR = Regex("([a-zA-Z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val TITLE_TAG = Regex("(?is)<title[^>]*>(.*?)</title>")

    fun decodeEntities(s: String): String {
        var out = s
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        out = Regex("&#(\\d{1,6});").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrNull() } ?: m.value
        }
        out = Regex("&#[xX]([0-9a-fA-F]{1,6});").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrNull() } ?: m.value
        }
        return out
    }

    fun parse(html: String, baseUrl: String): LinkMeta? {
        val props = HashMap<String, String>()
        for (tag in META_TAG.findAll(html)) {
            var key: String? = null
            var content: String? = null
            for (a in ATTR.findAll(tag.value)) {
                val name = a.groupValues[1].lowercase()
                val value = a.groupValues[2].ifEmpty { a.groupValues[3] }
                if (name == "property" || name == "name") key = value.lowercase()
                if (name == "content") content = value
            }
            if (key != null && content != null && !props.containsKey(key)) props[key] = content
        }
        val rawTitle = props["og:title"]
            ?: props["twitter:title"]
            ?: TITLE_TAG.find(html)?.groupValues?.get(1)
        val title = rawTitle?.let { decodeEntities(it).replace(Regex("\\s+"), " ").trim() }.orEmpty().take(120)
        if (title.isEmpty()) return null
        val rawImage = props["og:image"] ?: props["twitter:image"]
        val image = rawImage?.let { decodeEntities(it).trim() }?.takeIf { it.isNotEmpty() }?.let { resolve(baseUrl, it) }
        return LinkMeta(baseUrl, LinkExtractor.hostOf(baseUrl), title, image)
    }

    fun resolve(base: String, ref: String): String? =
        runCatching { URI(base).resolve(ref).toString() }.getOrNull()
}

class LinkPreviewResult(val meta: LinkMeta?, val thumb: Bitmap?)

object LinkPreviewRepository {

    private const val MAX_HTML_BYTES = 96 * 1024
    private const val MAX_IMAGE_BYTES = 160 * 1024
    private const val THUMB_DIM = 160
    private const val TIMEOUT_MS = 4000

    private val cache = LruCache<String, LinkPreviewResult>(40)
    private val gate = Semaphore(2)

    fun cached(url: String): LinkPreviewResult? = cache.get(url)

    suspend fun load(url: String): LinkPreviewResult {
        cache.get(url)?.let { return it }
        return gate.withPermit {
            cache.get(url)?.let { return@withPermit it }
            val result = withContext(Dispatchers.IO) { fetch(url) }
            cache.put(url, result)
            result
        }
    }

    private fun isPublicHttps(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = runCatching { URI(url).host }.getOrNull() ?: return false
        return runCatching {
            val a = InetAddress.getByName(host)
            !(a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress)
        }.getOrDefault(false)
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        c.setRequestProperty("Accept-Language", "ar,en;q=0.8")
        return c
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun fetch(url: String): LinkPreviewResult {
        if (!isPublicHttps(url)) return LinkPreviewResult(null, null)
        var conn: HttpURLConnection? = null
        try {
            conn = open(url)
            if (conn.responseCode !in 200..299) return LinkPreviewResult(null, null)
            val type = conn.contentType.orEmpty().lowercase()
            if (type.isNotEmpty() && !type.contains("html")) return LinkPreviewResult(null, null)
            val csName = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)
            val bytes = conn.inputStream.use { readLimited(it, MAX_HTML_BYTES) }
            val html = runCatching { String(bytes, java.nio.charset.Charset.forName(csName ?: "UTF-8")) }.getOrElse { String(bytes, Charsets.UTF_8) }
            val meta = HtmlMeta.parse(html, conn.url.toString()) ?: return LinkPreviewResult(null, null)
            val thumb = meta.imageUrl?.let { fetchThumb(it) }
            return LinkPreviewResult(meta, thumb)
        } catch (_: Exception) {
            return LinkPreviewResult(null, null)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun fetchThumb(url: String): Bitmap? {
        if (!isPublicHttps(url)) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url)
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { readLimited(it, MAX_IMAGE_BYTES) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, THUMB_DIM)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
