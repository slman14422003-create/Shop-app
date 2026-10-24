package com.salman.herbalencyclopedia.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * خدمة أمامية (Foreground Service) حقيقية تُنزّل ملف تحديث التطبيق
 * (.apk) بالخلفية — تستمر حتى لو أغلق المستخدم شاشة التطبيق أو خرج
 * منه تماماً، وتظهر إشعاراً دائماً بهوية التطبيق (أيقونته) يعرض نسبة
 * التقدّم لحظة بلحظة. عند اكتمال التحميل:
 *   - تحاول فتح مثبّت النظام تلقائياً فوراً (يعمل غالباً، لكن أندرويد
 *     قد يمنع فتح شاشة من الخلفية في بعض الحالات — قيد نظام التشغيل
 *     نفسه، وليس بالإمكان تجاوزه بالكامل من تطبيق عادي).
 *   - يبقى الإشعار قائماً بعنوان "اضغط للتثبيت" كطريق مضمون بديل يعمل
 *     دائماً بضغطة واحدة، بدل الحاجة للبحث عن الملف يدوياً بمدير الملفات
 *     أو تطبيق "مثبّت الحزم".
 *
 * الحالة نفسها (نسبة التقدّم، الجاهزية للتثبيت، الفشل) تُكتب في
 * [UpdateDownloadStatus] المشتركة، وشاشة الإعدادات (عبر AppViewModel)
 * تعرضها كما هي بلا أي تغيير يُذكر بمنطق الواجهة.
 */
class UpdateDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "update_downloads"
        private const val NOTIFICATION_ID = 4102

        private const val EXTRA_CANDIDATES = "candidates"
        private const val EXTRA_VERSION_NAME = "version_name"

        @Volatile private var activeConnection: HttpURLConnection? = null

        /** يبدأ التحميل بالخلفية. [candidates] هو الرابط المباشر + روابط الوسيط الاحتياطية بنفس الترتيب المُجهَّز مسبقاً في UpdateRepository. */
        fun start(context: Context, candidates: List<String>, versionName: String) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putStringArrayListExtra(EXTRA_CANDIDATES, ArrayList(candidates))
                putExtra(EXTRA_VERSION_NAME, versionName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** يوقف التحميل الجاري فوراً (زر "إلغاء" بشاشة الإعدادات). */
        fun cancel(context: Context) {
            activeConnection?.disconnect()
            runCatching { context.stopService(Intent(context, UpdateDownloadService::class.java)) }
            UpdateDownloadStatus.reset()
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val candidates = intent?.getStringArrayListExtra(EXTRA_CANDIDATES)
        val versionName = intent?.getStringExtra(EXTRA_VERSION_NAME)
        if (candidates.isNullOrEmpty() || versionName == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat(progressNotification(0))
        UpdateDownloadStatus.update(UpdateDownloadState.Downloading(0))

        serviceScope.launch {
            val result = runCatching { downloadApk(candidates, versionName) }
            val file = result.getOrNull()
            if (file != null) {
                UpdateDownloadStatus.downloadedApk = file
                UpdateDownloadStatus.update(UpdateDownloadState.ReadyToInstall)
                onDownloadReady(file)
            } else {
                UpdateDownloadStatus.update(
                    UpdateDownloadState.Failed(result.exceptionOrNull()?.localizedMessage ?: "فشل تحميل التحديث")
                )
                failureNotification()
            }
            ServiceCompat.stopForeground(this@UpdateDownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // التحميل الفعلي — نفس منطق AppViewModel السابق (تحقق أولي من الروابط
    // القابلة للوصول، ثم تنزيل فعلي من أول رابط يستجيب)، منقول هنا فقط.
    // ------------------------------------------------------------------

    private suspend fun downloadApk(candidates: List<String>, versionName: String): File {
        // تحقّق صريح على مستوى التطبيق (بمعزل عن NetworkSecurityConfig الذي
        // يمنع أصلاً أي اتصال cleartext على مستوى النظام): نرفض أي رابط
        // ليس https:// قبل محاولة أي اتصال به إطلاقاً، بدل الاعتماد فقط على
        // فشل الاتصال لاحقاً. هذا يمنع أيضاً أي محاولة تنزيل عبر أي مخطط
        // (scheme) غير متوقع (file://، content://...) قد يصل ضمن قائمة
        // الروابط الاحتياطية من مصدر بيانات بعيد (UpdateRepository) بأي خطأ
        // أو تلاعب لاحق في تلك القائمة.
        val secureCandidates = candidates.filter { it.startsWith("https://", ignoreCase = true) }
        if (secureCandidates.isEmpty()) {
            throw SecurityException("لا توجد روابط تحديث آمنة (HTTPS) صالحة")
        }

        val dir = File(cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val destFile = File(dir, "update-$versionName.apk")

        val reachable = raceFirstReachable(secureCandidates)
        val ordered = if (reachable != null) {
            listOf(reachable) + secureCandidates.filter { it != reachable }
        } else secureCandidates

        var lastError: Throwable? = null
        for (url in ordered) {
            val attempt = runCatching { downloadOneUrl(url, destFile) }
            if (attempt.isSuccess) return destFile
            destFile.delete()
            lastError = attempt.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("فشل تحميل التحديث")
    }

    private suspend fun raceFirstReachable(candidates: List<String>): String? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null
        val results = Channel<String?>(candidates.size)
        val jobs = candidates.map { url ->
            launch(Dispatchers.IO) {
                val ok = runCatching { probeReachable(url) }.getOrDefault(false)
                results.trySend(if (ok) url else null)
            }
        }
        var winner: String? = null
        repeat(candidates.size) {
            if (winner == null) {
                val value = results.receive()
                if (value != null) winner = value
            }
        }
        jobs.forEach { it.cancel() }
        winner
    }

    private fun probeReachable(url: String): Boolean {
        fun attempt(method: String): Boolean {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Harbs-App-Update-Downloader")
                if (method == "GET") setRequestProperty("Range", "bytes=0-0")
            }
            return try {
                val code = conn.responseCode
                code in 200..299 || code == 206
            } catch (e: Exception) {
                false
            } finally {
                conn.disconnect()
            }
        }
        return runCatching { attempt("HEAD") }.getOrDefault(false) || runCatching { attempt("GET") }.getOrDefault(false)
    }

    private fun downloadOneUrl(url: String, destFile: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Harbs-App-Update-Downloader")
        }
        activeConnection = conn
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("فشل الاتصال بالخادم (رمز $code)")

            val totalSize = conn.contentLength
            var lastReportedPercent = -1
            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var totalRead = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (totalSize > 0) {
                            val percent = ((totalRead * 100) / totalSize).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                UpdateDownloadStatus.update(UpdateDownloadState.Downloading(percent))
                                updateProgressNotification(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            activeConnection = null
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------
    // الإشعارات — بهوية التطبيق (أيقونة السيلويت أحادية اللون نفسها
    // المستخدمة كأيقونة التطبيق بأندرويد 13+).
    // ------------------------------------------------------------------

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تحديثات التطبيق",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات تحميل وتثبيت تحديثات التطبيق"
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun progressNotification(percent: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.salman.herbalencyclopedia.R.drawable.ic_launcher_monochrome)
            .setContentTitle("جارٍ تحميل تحديث التطبيق")
            .setContentText("$percent%")
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun startForegroundCompat(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    private fun updateProgressNotification(percent: Int) {
        if (!hasNotificationPermission()) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, progressNotification(percent))
    }

    private fun failureNotification() {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.salman.herbalencyclopedia.R.drawable.ic_launcher_monochrome)
            .setContentTitle("فشل تحميل التحديث")
            .setContentText("اضغط لإعادة المحاولة من إعدادات التطبيق")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    /**
     * يُستدعى فور اكتمال التحميل: يجهّز نية التثبيت، يحاول فتحها تلقائياً
     * فوراً (أفضل جهد)، ويحدّث الإشعار بحيث ضغطة واحدة عليه — في أي وقت
     * لاحق — تفتح نفس شاشة التثبيت، بدل ترك المستخدم يبحث عن الملف يدوياً.
     */
    private fun onDownloadReady(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            showReadyNotification(
                settingsIntent,
                title = "التحديث جاهز",
                text = "اضغط للسماح بتثبيت التطبيقات من هذا المصدر أولاً"
            )
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // محاولة تلقائية فورية — قد يمنعها النظام إن لم يكن التطبيق بالمقدّمة
        // مؤخراً، وحينها الإشعار أدناه هو الطريق المضمون البديل بضغطة واحدة.
        runCatching { startActivity(installIntent) }

        showReadyNotification(installIntent, title = "التحديث جاهز للتثبيت", text = "اضغط هنا لإتمام التثبيت")
    }

    private fun showReadyNotification(intent: Intent, title: String, text: String) {
        if (!hasNotificationPermission()) return
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.salman.herbalencyclopedia.R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }
}
