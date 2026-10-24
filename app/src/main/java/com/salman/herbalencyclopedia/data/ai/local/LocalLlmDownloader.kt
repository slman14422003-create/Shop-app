package com.salman.herbalencyclopedia.data.ai.local

import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class LocalLlmDownloader {

    class DownloadException(val kind: Kind, message: String) : IOException(message) {
        enum class Kind { AUTH, NO_SPACE, NETWORK, SIZE_MISMATCH, HTTP }
    }

    private class Remote(val connection: HttpURLConnection, val finalUrl: String)

    private fun openFollowing(startUrl: String, token: String, rangeStart: Long): Remote {
        var current = startUrl
        var hops = 0
        while (true) {
            val url = URL(current)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "HerbalEncyclopedia/LocalAI")
            if (token.isNotBlank() && url.host.endsWith("huggingface.co")) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            if (rangeStart > 0) conn.setRequestProperty("Range", "bytes=$rangeStart-")
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank() || hops >= MAX_REDIRECTS) {
                    throw DownloadException(DownloadException.Kind.HTTP, "redirect")
                }
                current = URL(url, location).toString()
                hops++
                continue
            }
            return Remote(conn, current)
        }
    }

    fun remoteSize(url: String, token: String): Long {
        return try {
            val remote = openFollowing(url, token, 0)
            try {
                val code = remote.connection.responseCode
                if (code == 401 || code == 403) throw DownloadException(DownloadException.Kind.AUTH, "auth")
                remote.connection.contentLengthLong
            } finally {
                remote.connection.disconnect()
            }
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            throw DownloadException(DownloadException.Kind.NETWORK, e.message ?: "network")
        }
    }

    fun freeBytes(dir: File): Long {
        dir.mkdirs()
        return try {
            val stat = StatFs(dir.path)
            stat.availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun download(
        spec: LocalModelSpec,
        token: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val dir = target.parentFile ?: throw DownloadException(DownloadException.Kind.NO_SPACE, "dir")
        dir.mkdirs()
        val part = File(dir, target.name + ".part")
        val expected = remoteSize(spec.url, token)
        val needed = if (expected > 0) expected else spec.approxSizeMb * 1024L * 1024L
        val alreadyHave = if (part.exists()) part.length() else 0L
        val required = ((needed - alreadyHave) * 1.1).toLong() + SAFETY_MARGIN_BYTES
        if (freeBytes(dir) < required) {
            throw DownloadException(DownloadException.Kind.NO_SPACE, "space")
        }
        var offset = alreadyHave
        if (expected > 0 && offset > expected) {
            part.delete()
            offset = 0
        }
        val remote = try {
            openFollowing(spec.url, token, offset)
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            throw DownloadException(DownloadException.Kind.NETWORK, e.message ?: "network")
        }
        val conn = remote.connection
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) throw DownloadException(DownloadException.Kind.AUTH, "auth")
            if (code == 416) {
                part.delete()
                throw DownloadException(DownloadException.Kind.HTTP, "range")
            }
            if (code !in 200..299) throw DownloadException(DownloadException.Kind.HTTP, "http $code")
            val resumed = code == 206
            if (!resumed && offset > 0) {
                part.delete()
                offset = 0
            }
            val total = if (expected > 0) expected else offset + conn.contentLengthLong
            var downloaded = offset
            val ctx = currentCoroutineContext()
            FileOutputStream(part, resumed).use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastReport = 0L
                    while (true) {
                        ctx.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            onProgress(downloaded, total)
                        }
                    }
                    out.flush()
                }
            }
            onProgress(downloaded, total)
            if (total > 0 && downloaded != total) {
                throw DownloadException(DownloadException.Kind.SIZE_MISMATCH, "size")
            }
            if (downloaded < MIN_VALID_BYTES) {
                part.delete()
                throw DownloadException(DownloadException.Kind.SIZE_MISMATCH, "tiny")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                throw DownloadException(DownloadException.Kind.NO_SPACE, "rename")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: IOException) {
            throw DownloadException(DownloadException.Kind.NETWORK, e.message ?: "network")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 300L
        const val MAX_REDIRECTS = 6
        const val SAFETY_MARGIN_BYTES = 150L * 1024 * 1024
        const val MIN_VALID_BYTES = 20L * 1024 * 1024
    }
}
