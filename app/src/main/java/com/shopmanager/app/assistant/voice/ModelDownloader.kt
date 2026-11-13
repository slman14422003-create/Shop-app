package com.shopmanager.app.assistant.voice

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface DownloadOutcome {
    data object Success : DownloadOutcome
    data object Cancelled : DownloadOutcome
    data class Failed(val message: String) : DownloadOutcome
}

internal class DownloadRequest(
    val modelId: String,
    val url: String,
    val dest: File,
    val expectedSha256: String?,
    val expectedMd5: String?,
    val expectedSize: Long,
    val checkGgmlMagic: Boolean
)

internal class ModelDownloader(private val gate: NetworkGate) {

    fun download(
        req: DownloadRequest,
        allowMobile: Boolean,
        cancel: AtomicBoolean,
        onState: (DownloadState) -> Unit
    ): DownloadOutcome {
        req.dest.parentFile?.mkdirs()
        val part = File(req.dest.parentFile, req.dest.name + ".part")
        val meta = File(req.dest.parentFile, req.dest.name + ".meta")
        var attempt = 0
        var lastError = "تعذر التحميل"
        var resumedOnce = false
        while (attempt <= DownloadMath.MAX_ATTEMPTS) {
            if (cancel.get()) return DownloadOutcome.Cancelled
            if (!gate.isAllowed(allowMobile)) {
                onState(DownloadState.Waiting(req.modelId, "wifi"))
                if (!gate.awaitAllowed(allowMobile, cancel)) return DownloadOutcome.Cancelled
            }
            try {
                val resolved = HttpSupport.resolve(req.url)
                val total = if (resolved.totalSize > 0L) resolved.totalSize else req.expectedSize
                val etag = resolved.etag ?: DownloadMath.cleanEtag(resolved.linkedEtag)
                val savedEtag = readMetaEtag(meta)
                var offset = DownloadMath.resumeOffset(part.length(), total, savedEtag, etag, resolved.acceptRanges)
                if (offset == 0L && part.exists()) part.delete()
                if (etag != null) meta.writeText(etag)
                if (offset > 0L && total > 0L && offset == total) {
                    return finish(req, part, meta, total)
                }
                val conn = HttpSupport.open(resolved.finalUrl, "GET", 20000, true)
                try {
                    if (offset > 0L) {
                        conn.setRequestProperty("Range", DownloadMath.rangeHeader(offset))
                        if (etag != null) conn.setRequestProperty("If-Range", "\"$etag\"")
                        resumedOnce = true
                    }
                    val code = conn.responseCode
                    val append: Boolean
                    when (code) {
                        206 -> append = true
                        200 -> {
                            append = false
                            offset = 0L
                        }
                        416 -> {
                            part.delete()
                            throw IOException("range")
                        }
                        else -> throw IOException("HTTP $code")
                    }
                    var done = offset
                    val len = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                    val grandTotal = if (total > 0L) total else if (len > 0L) len + offset else 0L
                    var lastEmit = 0L
                    conn.inputStream.use { input ->
                        FileOutputStream(part, append).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                if (cancel.get()) return DownloadOutcome.Cancelled
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val now = System.currentTimeMillis()
                                if (now - lastEmit > 250L) {
                                    lastEmit = now
                                    onState(DownloadState.Running(req.modelId, done, grandTotal, resumedOnce))
                                }
                            }
                        }
                    }
                    onState(DownloadState.Running(req.modelId, done, grandTotal, resumedOnce))
                    if (grandTotal > 0L && part.length() != grandTotal) throw IOException("incomplete")
                    return finish(req, part, meta, grandTotal)
                } finally {
                    conn.disconnect()
                }
            } catch (t: IOException) {
                lastError = "انقطع التحميل"
                attempt++
                if (attempt > DownloadMath.MAX_ATTEMPTS) break
                val wait = DownloadMath.backoffMs(attempt - 1)
                var waited = 0L
                while (waited < wait) {
                    if (cancel.get()) return DownloadOutcome.Cancelled
                    Thread.sleep(250L)
                    waited += 250L
                }
            } catch (t: Throwable) {
                return DownloadOutcome.Failed("تعذر التحميل")
            }
        }
        return DownloadOutcome.Failed(lastError)
    }

    private fun readMetaEtag(meta: File): String? = try {
        if (meta.isFile) meta.readText().trim().ifEmpty { null } else null
    } catch (t: Throwable) {
        null
    }

    private fun finish(req: DownloadRequest, part: File, meta: File, total: Long): DownloadOutcome {
        val ok = verify(req, part, total)
        if (!ok) {
            part.delete()
            meta.delete()
            return DownloadOutcome.Failed("الملف ما اكتمل بشكل سليم، جرّب مرة ثانية")
        }
        meta.delete()
        if (req.dest.exists()) req.dest.delete()
        if (!part.renameTo(req.dest)) {
            return DownloadOutcome.Failed("تعذر حفظ الملف")
        }
        return DownloadOutcome.Success
    }

    private fun verify(req: DownloadRequest, file: File, total: Long): Boolean {
        if (total > 0L && file.length() != total) return false
        if (req.expectedSha256 != null) {
            val actual = FileInputStream(file).use { DownloadMath.digest("SHA-256", it) } ?: return false
            return DownloadMath.checksumMatches(req.expectedSha256, actual)
        }
        if (!req.expectedMd5.isNullOrBlank()) {
            val actual = FileInputStream(file).use { DownloadMath.digest("MD5", it) } ?: return false
            return DownloadMath.checksumMatches(req.expectedMd5, actual)
        }
        if (req.checkGgmlMagic) {
            val head = ByteArray(4)
            val n = FileInputStream(file).use { it.read(head) }
            return n == 4 && DownloadMath.isGgmlMagic(head)
        }
        return file.length() > 0L
    }
}
