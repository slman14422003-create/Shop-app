package com.salman.herbalencyclopedia.ui.theme

import android.app.ActivityManager
import android.content.Context

/**
 * قبل هذا الملف كان أول تشغيل للتطبيق يعطي كل جهاز (ضعيفاً كان أو قوياً)
 * وضع "أداء عالٍ" افتراضياً (انظر PerformanceMode.fromId)، وينتظر أن يذهب
 * المستخدم بنفسه للإعدادات ويبدّله يدوياً لوضع "اقتصادي" لو جهازه ضعيف —
 * وهذا عملياً لا يحدث لمعظم الناس، فتبقى الأجهزة الضعيفة تعاني تقطيعاً
 * من أول لحظة تشغيل.
 *
 * هذه الدالة تفحص قدرة الجهاز فعلياً (نفس الإشارة الرسمية التي يستخدمها
 * أندرويد نفسه لتحديد الأجهزة "منخفضة الإمكانيات" + إجمالي الرام) وتختار
 * وضعاً افتراضياً مناسباً تلقائياً — بينما يبقى بإمكان المستخدم تجاوز هذا
 * الاختيار يدوياً من الإعدادات في أي وقت (تفضيله المحفوظ له الأولوية دائماً،
 * هذه الدالة تُستخدم فقط كقيمة افتراضية أول مرة).
 */
fun recommendedPerformanceMode(context: Context): PerformanceMode {
    val activityManager = context.applicationContext
        .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return PerformanceMode.HIGH_QUALITY

    // إشارة أندرويد الرسمية لتصنيف "جهاز منخفض الرام" — نفس ما تعتمده
    // أنظمة التشغيل والتطبيقات الكبيرة لتقرير إيقاف الرسوم الثقيلة.
    if (activityManager.isLowRamDevice) return PerformanceMode.ECO

    // لو كان المستخدم قد فعّل "توفير الطاقة" أصلاً وقت أول تشغيل للتطبيق،
    // فهذا اختيار واعٍ منه لتقليل استهلاك الجهاز عموماً — من غير المنطقي
    // أن يبدأ التطبيق افتراضياً بأثقل وضع رسوميات ممكن رغم ذلك. القيمة
    // المحفوظة تبقى قابلة للتغيير يدوياً من الإعدادات لاحقاً كأي توصية
    // أولى أخرى هنا؛ وبعد أول تشغيل يتولى rememberEffectivePerformanceMode
    // (راجع RuntimePerformanceSignals.kt) التكيّف اللحظي مع نفس الإشارة.
    if (isBatterySaverOn(context)) return PerformanceMode.ECO

    val memoryInfo = ActivityManager.MemoryInfo()
    return try {
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        // أقل من 3 جيجا رام: عملياً جهاز اقتصادي اليوم حتى لو لم يُصنَّف
        // isLowRamDevice رسمياً على بعض الإصدارات المخصّصة من الشركات المصنّعة.
        if (totalMemGb < 3.0) PerformanceMode.ECO else PerformanceMode.HIGH_QUALITY
    } catch (e: Exception) {
        PerformanceMode.HIGH_QUALITY
    }
}
