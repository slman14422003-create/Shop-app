package com.shopmanager.app.data.updates

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Progress of an in-app APK download — "من التطبيق نفسه بدون متصفح
 * خارجي" (like Telegram's in-chat file download): a plain 0..100 percent
 * the caller renders as a LinearProgressIndicator, no external app ever
 * opens for the download step. */
sealed class DownloadState {
    data class InProgress(val percent: Int) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object ApkDownloader {

    /** Streams the APK at [apkUrl] into this app's own cache dir (exposed
     * to the system installer only through FileProvider — see
     * file_paths.xml — never through a world-readable path), reporting
     * percent complete via [onProgress]. Overwrites any previous download
     * so a retry after a failed/partial attempt doesn't append onto a
     * corrupt file. */
    suspend fun download(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): DownloadState = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(updatesDir, "update.apk")
            if (outFile.exists()) outFile.delete()

            connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
            }
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext DownloadState.Error("تعذر تحميل الملف — رمز الحالة ${connection.responseCode}")
            }

            val totalBytes = connection.contentLength
            var readBytes = 0L
            var lastReportedPercent = -1

            connection.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readBytes += read
                        if (totalBytes > 0) {
                            val percent = ((readBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            // Unknown content-length (some servers omit it): we still
            // downloaded the whole stream above, just couldn't show
            // incremental percent along the way — report 100 now that
            // it's actually finished.
            if (totalBytes <= 0) onProgress(100)

            DownloadState.Done(outFile)
        } catch (e: Exception) {
            DownloadState.Error(e.message ?: "فشل التحميل")
        } finally {
            connection?.disconnect()
        }
    }

    /** True once Android will actually let this app trigger a package
     * install — API 26+ requires the person to have flipped the "install
     * unknown apps" toggle for this app specifically first. Below API 26
     * the classic install-time permission covers this and no runtime
     * check is needed. */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Intent to send the person to the "السماح من هذا المصدر" system
     * settings screen for this app, when [canInstallPackages] is false. */
    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    /** Fires the system package installer for a previously-downloaded APK.
     * Uses the app's existing FileProvider authority (already declared in
     * the manifest for the materials-report share flow) so the installer
     * gets a content:// URI instead of a raw file:// path, which Android
     * 7+ blocks between apps (StrictMode FileUriExposedException). */
    fun install(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
