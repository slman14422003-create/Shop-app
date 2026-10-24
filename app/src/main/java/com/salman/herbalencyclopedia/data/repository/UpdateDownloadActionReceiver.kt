package com.salman.herbalencyclopedia.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.salman.herbalencyclopedia.data.repository.UpdateRepository

/**
 * قدرة جديدة: يسمح بزر "تحميل" داخل إشعار "تحديث متوفر" نفسه (راجع
 * [UpdateNotifier]) ببدء تحميل التحديث فوراً بالخلفية — بلا الحاجة لفتح
 * التطبيق أولاً والتنقّل يدوياً لشاشة الإعدادات ثم ضغط زر التحميل هناك، وهو
 * ما كان يحدث سابقاً (زر التحميل الحقيقي الوحيد كان داخل شاشة الإعدادات).
 *
 * لماذا BroadcastReceiver لا Activity: هذا إجراء خلفي بحت (بدء خدمة تحميل)
 * لا يحتاج فتح أي واجهة مستخدم إطلاقاً؛ استقبال الحدث هنا ثم تمريره فوراً
 * إلى [UpdateDownloadService.start] (التي تتولى بنفسها استدعاء
 * startForegroundService بالشكل الصحيح) أخفّ وأسرع من فتح MainActivity
 * بأكملها فقط لتنفيذ سطر واحد.
 */
class UpdateDownloadActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.salman.herbalencyclopedia.action.START_UPDATE_DOWNLOAD"
        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_USE_PROXY_FALLBACK = "use_proxy_fallback"
        const val EXTRA_CUSTOM_PROXY_BASE_URL = "custom_proxy_base_url"
        /** رقم إشعار "تحديث متوفر" نفسه — يُزال فور بدء التحميل فعلياً، فيحل محله إشعار تقدّم التحميل من UpdateDownloadService بدل بقاء الاثنين معاً. */
        const val EXTRA_DISMISS_NOTIFICATION_ID = "dismiss_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_DOWNLOAD) return
        val apkUrl = intent.getStringExtra(EXTRA_APK_URL) ?: return
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: return
        val useProxyFallback = intent.getBooleanExtra(EXTRA_USE_PROXY_FALLBACK, true)
        val customProxyBaseUrl = intent.getStringExtra(EXTRA_CUSTOM_PROXY_BASE_URL)

        // دالة خالصة لا تحتاج اتصالاً فعلياً بـ Firestore (راجع UpdateRepository.downloadCandidates)،
        // فإنشاء نسخة جديدة هنا آمن تماماً رغم استقبال هذا الـ Receiver في عملية منفصلة عن AppContainer المعتاد.
        val candidates = UpdateRepository().downloadCandidates(apkUrl, useProxyFallback, customProxyBaseUrl)
        UpdateDownloadService.start(context, candidates, versionName)

        val dismissId = intent.getIntExtra(EXTRA_DISMISS_NOTIFICATION_ID, -1)
        if (dismissId != -1) {
            NotificationManagerCompat.from(context).cancel(dismissId)
        }
    }
}
