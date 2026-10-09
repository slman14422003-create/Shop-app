package com.shopmanager.app.data.performance

import android.os.Build
import android.view.Display
import android.view.Window
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs

/** معدل تحديث الشاشة الحالي (Hz) كما يراه التطبيق؛ تقرؤه حركات التطبيق لتضبط سرعتها. */
val LocalRefreshRateHz = staticCompositionLocalOf { 60f }

/**
 * سياسة معدل التحديث حسب وضع الأداء:
 * - الأداء القوي (STANDARD): أقرب وضع إلى 90Hz (ثابت وسلس وأوفر من 120Hz). إن لم يدعم
 *   الجهاز 90 يُختار أقرب وضع أعلى منه، وإن كانت الشاشة 60Hz فقط تبقى 60 وتتولّى
 *   [com.shopmanager.app.ui.common.MotionSpecs] جعل الحركة أسرع استجابة.
 * - المتوازن (BALANCED) والاقتصادي (LOW): أقرب وضع إلى 60Hz لتوفير الطاقة وثبات توقيت
 *   الإطارات (إطارات أقل = حمل أقل على المعالج الرسومي، والحركة تتكيّف عبر MotionSpecs).
 * لا يتغير الـ resolution أبداً (نختار فقط من أوضاع بنفس الدقة) لتفادي وميض تبديل الوضع.
 */
object RefreshRatePolicy {
    private const val HIGH_TARGET_HZ = 90f
    private const val LOW_TARGET_HZ = 60f
    private const val HIGH_MIN_HZ = 85f

    fun apply(window: Window, display: Display?, tier: PerformanceTier) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || display == null) return
        val current = display.mode ?: return
        val modes = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val target = choose(modes, tier) ?: return
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId == target.modeId && attrs.preferredRefreshRate == target.refreshRate) return
        window.attributes = attrs.apply {
            preferredDisplayModeId = target.modeId
            preferredRefreshRate = target.refreshRate
        }
    }

    private fun choose(modes: List<Display.Mode>, tier: PerformanceTier): Display.Mode? {
        if (modes.isEmpty()) return null
        return if (tier == PerformanceTier.STANDARD) {
            val fast = modes.filter { it.refreshRate >= HIGH_MIN_HZ }
            if (fast.isEmpty()) {
                modes.maxByOrNull { it.refreshRate }
            } else {
                fast.minWithOrNull(
                    compareBy<Display.Mode>({ abs(it.refreshRate - HIGH_TARGET_HZ) }, { -it.refreshRate })
                )
            }
        } else {
            modes.minByOrNull { abs(it.refreshRate - LOW_TARGET_HZ) }
        }
    }
}
