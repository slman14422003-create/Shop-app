package com.salman.herbalencyclopedia.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * وضعا الأداء اللذان يختار المستخدم بينهما من الإعدادات:
 *
 * - [HIGH_QUALITY]: الزجاج السائل الكامل (تمويه حقيقي RenderEffect + توهّج
 *   متحرك + ظلال) وكل الحركات — مناسب للأجهزة الحديثة القوية.
 * - [ECO]: يوقف التمويه والظلال والتوهّج المتحرك (Infinite Transitions)
 *   ويقصّر مدد الحركات، مع الإبقاء على الانتقالات الأساسية فقط — مخصّص
 *   للأجهزة الاقتصادية/الضعيفة لضمان سلاسة كاملة بلا تقطيع.
 *
 * أي مكوّن بالتطبيق يقرأ [LocalPerformanceMode] ليقرر شكله وسلوكه، فمصدر
 * الحقيقة الوحيد لهذا الخيار هو DataStore عبر PreferencesRepository.performanceMode.
 */
enum class PerformanceMode(val label: String, val description: String) {
    HIGH_QUALITY(
        label = "أداء عالٍ",
        description = "زجاج سائل بتمويه حقيقي وتوهّج وحركات كاملة"
    ),
    ECO(
        label = "اقتصادي",
        description = "بلا تمويه أو ظلال ثقيلة — لأقصى سلاسة على الأجهزة الضعيفة"
    );

    val isHighQuality: Boolean get() = this == HIGH_QUALITY

    companion object {
        fun fromId(id: String?, fallback: PerformanceMode = HIGH_QUALITY): PerformanceMode =
            entries.find { it.name == id } ?: fallback
    }
}

val LocalPerformanceMode = compositionLocalOf { PerformanceMode.HIGH_QUALITY }
