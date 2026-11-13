package com.shopmanager.app.assistant.voice

import java.io.InputStream
import java.security.MessageDigest

object DownloadMath {
    const val MAX_ATTEMPTS = 5

    fun rangeHeader(offset: Long): String = "bytes=$offset-"

    fun resumeOffset(partSize: Long, totalSize: Long, savedEtag: String?, currentEtag: String?, acceptRanges: Boolean): Long {
        if (partSize <= 0L) return 0L
        if (!acceptRanges) return 0L
        if (savedEtag != null && currentEtag != null && savedEtag != currentEtag) return 0L
        if (totalSize > 0L && partSize > totalSize) return 0L
        return partSize
    }

    fun backoffMs(attempt: Int): Long {
        val a = attempt.coerceIn(0, 10)
        return minOf(30_000L, 1000L shl a)
    }

    fun totalFromContentRange(header: String?): Long {
        if (header == null) return -1L
        val slash = header.lastIndexOf('/')
        if (slash < 0) return -1L
        return header.substring(slash + 1).trim().toLongOrNull() ?: -1L
    }

    fun percent(done: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((done * 100L) / total).toInt().coerceIn(0, 100)
    }

    fun cleanEtag(raw: String?): String? {
        if (raw == null) return null
        var s = raw.trim()
        if (s.startsWith("W/")) s = s.substring(2)
        s = s.trim('"')
        return s.ifEmpty { null }
    }

    fun sha256FromEtag(raw: String?): String? {
        val s = cleanEtag(raw)?.lowercase() ?: return null
        if (s.length != 64) return null
        return if (s.all { it in '0'..'9' || it in 'a'..'f' }) s else null
    }

    fun checksumMatches(expected: String?, actual: String): Boolean {
        if (expected == null) return false
        return expected.trim().lowercase() == actual.trim().lowercase()
    }

    fun isGgmlMagic(head: ByteArray): Boolean {
        if (head.size < 4) return false
        val le = (head[0].toInt() and 0xff) or ((head[1].toInt() and 0xff) shl 8) or
            ((head[2].toInt() and 0xff) shl 16) or ((head[3].toInt() and 0xff) shl 24)
        val be = ((head[0].toInt() and 0xff) shl 24) or ((head[1].toInt() and 0xff) shl 16) or
            ((head[2].toInt() and 0xff) shl 8) or (head[3].toInt() and 0xff)
        return le == 0x67676d6c || be == 0x67676d6c
    }

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append("0123456789abcdef"[v shr 4]).append("0123456789abcdef"[v and 15])
        }
        return sb.toString()
    }

    fun digest(algorithm: String, input: InputStream, isCancelled: () -> Boolean = { false }): String? {
        val md = MessageDigest.getInstance(algorithm)
        val buf = ByteArray(64 * 1024)
        while (true) {
            if (isCancelled()) return null
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return hex(md.digest())
    }

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun md5(bytes: ByteArray): String = hex(MessageDigest.getInstance("MD5").digest(bytes))
}
