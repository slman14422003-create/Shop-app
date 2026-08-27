package com.shopmanager.app.data.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

/** Result of a single "تحقق من التحديثات" tap. Modeled as a sealed class
 * (rather than a plain nullable) so the admin panel can show *why* a check
 * failed — a wrong URL, a server error, a malformed JSON body, or plain
 * network unreachability — instead of just "didn't work", which is the
 * whole point of having a developer panel for chasing server problems. */
sealed class UpdateCheckResult {
    data class UpToDate(val current: AppVersion) : UpdateCheckResult()
    data class UpdateAvailable(val manifest: UpdateManifest, val current: AppVersion) : UpdateCheckResult()
    data class Failed(val reason: String) : UpdateCheckResult()
}

object UpdateChecker {

    private const val TIMEOUT_MS = 12_000

    suspend fun check(context: Context, manifestUrl: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = AppVersionInfo.current(context)
        if (manifestUrl.isBlank()) {
            return@withContext UpdateCheckResult.Failed("لم يتم إعداد رابط التحديثات بعد")
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(manifestUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
            connection.connect()

            val status = connection.responseCode
            if (status !in 200..299) {
                return@withContext UpdateCheckResult.Failed("الخادم أعاد رمز الحالة $status")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val manifest = parseManifest(body)
                ?: return@withContext UpdateCheckResult.Failed("تنسيق ملف التحديثات غير صحيح")

            return@withContext if (manifest.versionCode > current.code) {
                UpdateCheckResult.UpdateAvailable(manifest, current)
            } else {
                UpdateCheckResult.UpToDate(current)
            }
        } catch (e: java.net.UnknownHostException) {
            return@withContext UpdateCheckResult.Failed("تعذر الوصول للخادم — تحقق من الاتصال بالإنترنت")
        } catch (e: java.net.SocketTimeoutException) {
            return@withContext UpdateCheckResult.Failed("انتهت مهلة الاتصال بالخادم")
        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Failed(e.message ?: "خطأ غير متوقع")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseManifest(raw: String): UpdateManifest? = try {
        val json = JSONObject(raw)
        val apkUrl = json.getString("apkUrl")
        val versionCode = json.getLong("versionCode")
        if (apkUrl.isBlank() || versionCode <= 0) null else UpdateManifest(
            versionCode = versionCode,
            versionName = json.optString("versionName", ""),
            apkUrl = apkUrl,
            notes = json.optString("notes", "")
        )
    } catch (e: Exception) {
        null
    }

    /** Cheap "is the update endpoint reachable at all" probe for the admin
     * panel — same request, but callers only care about the boolean. */
    suspend fun canReach(manifestUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (manifestUrl.isBlank()) return@withContext false
        var connection: URLConnection? = null
        try {
            connection = URL(manifestUrl).openConnection().apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            connection.connect()
            true
        } catch (e: Exception) {
            false
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }
}
