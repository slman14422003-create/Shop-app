package com.salman.herbalencyclopedia.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.salman.herbalencyclopedia.MainActivity
import com.salman.herbalencyclopedia.R
import com.salman.herbalencyclopedia.data.model.AppUpdateInfo

/**
 * قدرة جديدة: إشعار نظام تلقائي عند اكتشاف تحديث متوفر للتطبيق.
 *
 * حتى الآن كان التحقق من التحديث يحدث فقط عند ضغط المستخدم يدوياً على "تحقق
 * من التحديثات" بشاشة الإعدادات — لا يوجد أي تنبيه تلقائي إن لم يفتح
 * المستخدم تلك الشاشة بنفسه، حتى لو مرّت أشهر على وجود إصدار أحدث فعلياً.
 * [com.salman.herbalencyclopedia.ui.AppViewModel.checkForUpdateSilently] يستدعي
 * هذا الكائن تلقائياً في كل مرة يُفتح فيها التطبيق (init{} الخاص بـ
 * AppViewModel، الذي يُنشأ مرة واحدة فعلياً لكل فتحة للتطبيق)، فيصل التنبيه
 * للمستخدم بلا حاجة لأي إجراء منه.
 *
 * يستخدم عمداً *نفس* معرّف القناة (CHANNEL_ID) المستخدم أصلاً في
 * [UpdateDownloadService] لإشعارات تقدّم/جاهزية التحميل — كلاهما يخص نفس
 * الموضوع العملي من منظور المستخدم ("تحديثات التطبيق")، فتظهر كل إشعارات
 * التحديث تحت مفتاح تفعيل/تعطيل واحد في إعدادات النظام بدل قناتين منفصلتين
 * لغرض واحد. [ensureChannel] هنا مستقلة تماماً عن تلك الموجودة في الخدمة
 * (private هناك، ولا داعي لمشاركتها) لأن هذا الإشعار قد يظهر أولاً — قبل أن
 * يبدأ المستخدم أي تحميل فعلي يُنشئ القناة من جانب الخدمة.
 */
object UpdateNotifier {
    private const val CHANNEL_ID = "update_downloads"
    private const val NOTIFICATION_ID = 4103
    /** يقرأه MainActivity (راجع EXTRA_OPEN_SCREEN هناك) ليتنقّل تلقائياً لشاشة الإعدادات بمجرد أن يصبح التطبيق جاهزاً للتنقّل — إصلاح ثغرة "الضغط على الإشعار كان يفتح التطبيق فقط بلا أي انتقال فعلي لشاشة التحديث". */
    const val EXTRA_OPEN_SCREEN = "open_screen"
    const val OPEN_SCREEN_SETTINGS = "settings"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
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

    /** أندرويد 13+ يتطلّب إذناً صريحاً؛ إن لم يُمنح بعد، لا يُظهَر شيء (نفس منطق UpdateDownloadService — التحقق نفسه يستمر يعمل، فقط الإشعار المرئي لا يظهر). */
    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * يُظهر إشعاراً واحداً بتوفّر [info].
     *
     * *** إصلاح: الضغط على الإشعار كان يفتح الشاشة الرئيسية للتطبيق فقط
     * بلا أي انتقال فعلي لشاشة الإعدادات (حيث زر التحميل الحقيقي) — المستخدم
     * كان يضطر للتنقّل يدوياً بنفسه في كل مرة. ***
     * الآن: [EXTRA_OPEN_SCREEN] يُرفق مع نيّة الفتح، وMainActivity (راجع
     * companion object هناك) يقرأه ويوجّه NavGraph تلقائياً لشاشة الإعدادات
     * بمجرد أن يصبح التطبيق جاهزاً للتنقّل — سواء كان التطبيق مغلقاً تماماً
     * (بدء بارد) أو يعمل بالخلفية بالفعل (onNewIntent).
     *
     * *** إصلاح ثانٍ: زر "تحميل" حقيقي داخل الإشعار نفسه *** — قبله كان
     * الإشعار مجرد رابط لفتح التطبيق فقط، والتحميل الفعلي يتطلّب فتح شاشة
     * الإعدادات والضغط على زرها يدوياً حتى لو كان المستخدم يريد التحميل مباشرة.
     * إن توفّر رابط تنزيل مباشر ([AppUpdateInfo.apkUrl])، الزر يبدأ التحميل
     * بالخلفية فوراً (عبر [UpdateDownloadActionReceiver]) بضغطة واحدة من لوحة
     * الإشعارات بلا فتح أي واجهة؛ وإلا (لا يوجد ملف .apk مرفق، فقط صفحة
     * إصدار) يفتح رابط الصفحة في المتصفح مباشرة.
     */
    fun notifyUpdateAvailable(context: Context, info: AppUpdateInfo) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_SCREEN, OPEN_SCREEN_SETTINGS)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "النسخة ${info.versionName} من التطبيق متوفرة الآن — اضغط للتحميل من شاشة الإعدادات"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("تحديث جديد متوفر 🌿")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val apkUrl = info.apkUrl
        if (apkUrl != null) {
            val downloadIntent = Intent(context, UpdateDownloadActionReceiver::class.java).apply {
                action = UpdateDownloadActionReceiver.ACTION_START_DOWNLOAD
                putExtra(UpdateDownloadActionReceiver.EXTRA_APK_URL, apkUrl)
                putExtra(UpdateDownloadActionReceiver.EXTRA_VERSION_NAME, info.versionName)
                putExtra(UpdateDownloadActionReceiver.EXTRA_USE_PROXY_FALLBACK, info.useProxyFallback)
                putExtra(UpdateDownloadActionReceiver.EXTRA_CUSTOM_PROXY_BASE_URL, info.customProxyBaseUrl)
                putExtra(UpdateDownloadActionReceiver.EXTRA_DISMISS_NOTIFICATION_ID, NOTIFICATION_ID)
            }
            val downloadPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_launcher_monochrome, "تحميل", downloadPendingIntent)
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.releasePageUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val browserPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                browserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_launcher_monochrome, "تحميل", browserPendingIntent)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
