package com.shopmanager.app.assistant.voice

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class Resolved(
    val finalUrl: String,
    val linkedEtag: String?,
    val linkedSize: Long,
    val contentLength: Long,
    val etag: String?,
    val acceptRanges: Boolean
) {
    val totalSize: Long get() = if (contentLength > 0L) contentLength else linkedSize
}

internal object HttpSupport {
    private const val UA = "ShopManager-Assistant"

    fun open(url: String, method: String, timeoutMs: Int, follow: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.instanceFollowRedirects = follow
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Encoding", "identity")
        return c
    }

    fun resolve(url: String, timeoutMs: Int = 15000): Resolved {
        var current = url
        var linkedEtag: String? = null
        var linkedSize = -1L
        var hops = 0
        while (hops < 6) {
            hops++
            val c = open(current, "HEAD", timeoutMs, false)
            try {
                val code = c.responseCode
                if (linkedEtag == null) linkedEtag = c.getHeaderField("X-Linked-Etag")
                if (linkedSize < 0L) linkedSize = c.getHeaderField("X-Linked-Size")?.toLongOrNull() ?: -1L
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IOException("redirect")
                    current = URL(URL(current), loc).toString()
                    continue
                }
                if (code == 405 || code == 501) {
                    return resolveByRange(current, linkedEtag, linkedSize, timeoutMs)
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val len = c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                val ranges = c.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) ?: true
                return Resolved(current, linkedEtag, linkedSize, len, DownloadMath.cleanEtag(c.getHeaderField("ETag")), ranges)
            } finally {
                c.disconnect()
            }
        }
        throw IOException("too many redirects")
    }

    private fun resolveByRange(url: String, linkedEtag: String?, linkedSize: Long, timeoutMs: Int): Resolved {
        val c = open(url, "GET", timeoutMs, true)
        c.setRequestProperty("Range", "bytes=0-0")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = if (code == 206) DownloadMath.totalFromContentRange(c.getHeaderField("Content-Range"))
            else c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            return Resolved(c.url.toString(), linkedEtag, linkedSize, total, DownloadMath.cleanEtag(c.getHeaderField("ETag")), code == 206)
        } finally {
            c.disconnect()
        }
    }

    fun getText(url: String, timeoutMs: Int = 15000, maxBytes: Int = 4 * 1024 * 1024): String {
        val c = open(url, "GET", timeoutMs, true)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException("too large")
                }
                return out.toString("UTF-8")
            }
        } finally {
            c.disconnect()
        }
    }
}
