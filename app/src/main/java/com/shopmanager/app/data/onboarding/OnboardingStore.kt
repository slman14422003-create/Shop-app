package com.shopmanager.app.data.onboarding

import android.content.Context
import com.shopmanager.app.data.updates.AppVersionInfo

/** ما الذي يجب عرضه عند فتح التطبيق الآن. */
sealed class IntroAction {
    /** أول تثبيت: شرح التطبيق. */
    data object Welcome : IntroAction()

    /** تحديث: ميزات جديدة فقط. */
    data class WhatsNew(val releases: List<ReleaseNotes>) : IntroAction()
}

/**
 * يخزّن حالة العرض محلياً:
 *  - مثبّت جديد (لا يوجد سجل سابق ولا تحديث للحزمة): يرى الشرح مرة واحدة ثم لا يتكرر.
 *  - مستخدم قديم يحدّث لأول مرة على هذا النظام: لا يرى الشرح أبداً، يرى فقط آخر ملاحظات الإصدار.
 *  - بعد ذلك: كل إصدار جديد في [IntroContent.releases] يظهر مرة واحدة فقط.
 */
object OnboardingStore {
    private const val PREFS = "shop_manager_onboarding"
    private const val KEY_WELCOME_DONE = "welcome_done"
    private const val KEY_LAST_RELEASE = "last_seen_release"

    fun pending(context: Context): IntroAction? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val latest = IntroContent.latestReleaseId

        if (!prefs.contains(KEY_WELCOME_DONE)) {
            return if (isExistingUser(context)) {
                // مستخدم قديم: يتخطى الشرح، ويرى آخر ميزات فقط.
                prefs.edit().putBoolean(KEY_WELCOME_DONE, true).apply()
                val newest = IntroContent.releases.maxByOrNull { it.id }
                if (newest != null && isBuildEligible(context, newest)) IntroAction.WhatsNew(listOf(newest))
                else {
                    prefs.edit().putInt(KEY_LAST_RELEASE, latest).apply()
                    null
                }
            } else {
                IntroAction.Welcome
            }
        }

        val lastSeen = prefs.getInt(KEY_LAST_RELEASE, 0)
        val unseen = IntroContent.releases
            .filter { it.id > lastSeen && isBuildEligible(context, it) }
            .sortedBy { it.id }
        return if (unseen.isEmpty()) null else IntroAction.WhatsNew(unseen)
    }

    fun markDone(context: Context, action: IntroAction) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_WELCOME_DONE, true)
            .putInt(KEY_LAST_RELEASE, IntroContent.latestReleaseId)
            .apply()
    }

    private fun isBuildEligible(context: Context, notes: ReleaseNotes): Boolean =
        notes.minVersionCode <= AppVersionInfo.current(context).code

    /** مستخدم قديم = الحزمة حُدِّثت بعد تثبيتها (التثبيت فوق نسخة سابقة). التثبيت من الصفر لا يُعدّ قديماً. */
    private fun isExistingUser(context: Context): Boolean {
        val updated = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.lastUpdateTime - info.firstInstallTime > 60_000L
        }.getOrDefault(false)
        return updated
    }
}
