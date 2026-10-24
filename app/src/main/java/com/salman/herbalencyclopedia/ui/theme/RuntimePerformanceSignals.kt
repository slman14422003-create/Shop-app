package com.salman.herbalencyclopedia.ui.theme

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * إشارات أداء حقيقية من النظام لم يكن التطبيق يستخدمها إطلاقاً من قبل:
 * "توفير الطاقة" (Battery Saver) والحالة الحرارية للجهاز (Thermal Status).
 *
 * قبل هذا الملف، وضع الأداء كان قراراً ثابتاً بالكامل: إما ما اختاره
 * المستخدم يدوياً من الإعدادات، أو التوصية الأولى المبنية على رام الجهاز
 * فقط (DeviceCapability.kt) — ثم يبقى كما هو بلا أي تكيّف حتى لو تغيّرت
 * حالة الجهاز الفعلية أثناء الاستخدام: جهاز قوي اختار "أداء عالٍ" ثم
 * فعّل المستخدم توفير الطاقة يدوياً (أو دخل الجهاز تحكّماً حرارياً بعد
 * استخدام طويل تحت الشمس مثلاً) كان يستمر بتشغيل التمويه الحقيقي والتوهّج
 * المتحرك بكامل تكلفتهما على معالج رسومي مقيَّد فعلياً من النظام نفسه —
 * وهذا يزيد التقطيع والاستهلاك بدل تخفيفه بالضبط في اللحظة التي يحتاج
 * فيها الجهاز أقل حِمل ممكن.
 *
 * [rememberEffectivePerformanceMode] تحل هذا: تُبقي اختيار المستخدم كما
 * هو (لا تُغيّر تفضيله المحفوظ في الإعدادات إطلاقاً)، لكنها تُرجع نسخة
 * "فعلية" مؤقتة تتراجع تلقائياً لوضع [PerformanceMode.ECO] كلما كان
 * توفير الطاقة مفعّلاً أو كانت حالة الجهاز الحرارية معتدلة فأعلى — وتعود
 * تلقائياً لاختيار المستخدم الأصلي فور زوال السبب. هذه القيمة الفعلية هي
 * ما يجب تزويده لـ[LocalPerformanceMode] (راجع MainActivity)، بينما
 * DataStore يستمر بحفظ تفضيل المستخدم الخام بلا أي تدخل.
 */

/** هل "توفير الطاقة" مفعّل حالياً على الجهاز؟ آمنة الاستدعاء دائماً. */
fun isBatterySaverOn(context: Context): Boolean {
    val powerManager = context.applicationContext
        .getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return try {
        powerManager.isPowerSaveMode
    } catch (e: Exception) {
        false
    }
}

/** هل الجهاز في حالة تحكّم حراري (Thermal Throttling) معتدلة فأعلى الآن؟
 *  متاحة فقط من أندرويد 10 (API 29)؛ ترجع false دوماً قبل ذلك بأمان. */
fun isThermalThrottling(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val powerManager = context.applicationContext
        .getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return try {
        powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
    } catch (e: Exception) {
        false
    }
}

/**
 * النسخة "الفعلية" من وضع الأداء المختار: تتراجع تلقائياً إلى [PerformanceMode.ECO]
 * حين يفعّل النظام توفير الطاقة أو يدخل الجهاز حالة حرارية معتدلة فأعلى،
 * وتعود فوراً لاختيار المستخدم [selected] الأصلي بمجرد زوال السبب. لا تُغيّر
 * أي شيء محفوظ بالإعدادات — قيمة عرض/تشغيل لحظية فقط لكل مكوّنات الزجاج
 * والحركات التي تقرأ [LocalPerformanceMode].
 */
@Composable
fun rememberEffectivePerformanceMode(selected: PerformanceMode): PerformanceMode {
    val context = LocalContext.current
    var batterySaverOn by remember { mutableStateOf(isBatterySaverOn(context)) }
    var thermalThrottling by remember { mutableStateOf(isThermalThrottling(context)) }

    DisposableEffect(context) {
        val powerManager = context.applicationContext
            .getSystemService(Context.POWER_SERVICE) as? PowerManager

        val batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                batterySaverOn = isBatterySaverOn(context)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        val thermalListener: PowerManager.OnThermalStatusChangedListener? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PowerManager.OnThermalStatusChangedListener { status ->
                    thermalThrottling = status >= PowerManager.THERMAL_STATUS_MODERATE
                }
            } else null
        if (thermalListener != null && powerManager != null) {
            runCatching { powerManager.addThermalStatusListener(thermalListener) }
        }

        onDispose {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            if (thermalListener != null && powerManager != null) {
                runCatching { powerManager.removeThermalStatusListener(thermalListener) }
            }
        }
    }

    return if (selected.isHighQuality && (batterySaverOn || thermalThrottling)) {
        PerformanceMode.ECO
    } else {
        selected
    }
}
