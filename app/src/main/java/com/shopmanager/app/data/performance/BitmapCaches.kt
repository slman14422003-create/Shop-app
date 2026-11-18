package com.shopmanager.app.data.performance

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * سجل واحد لكاشات الصور (Bitmap). كل كاش يسجّل دالة إخلاء هنا، فيستدعيها MainActivity عند
 * onTrimMemory، وتُحجَّم الكاشات من [DevicePerformance.memoryClassMb] بدل رقم ثابت.
 */
object BitmapCaches {
    private val evictors = CopyOnWriteArrayList<() -> Unit>()

    fun register(evict: () -> Unit) {
        evictors.add(evict)
    }

    fun evictAll() {
        for (e in evictors) runCatching { e() }
    }

    /** حجم كاش بالبايت = [fraction] من سقف heap التطبيق (memoryClass)، بحد أدنى 2MB. */
    fun budgetBytes(context: Context, fraction: Float): Int {
        val bytes = DevicePerformance.memoryClassMb(context).toLong() * 1024L * 1024L
        return (bytes * fraction).toLong().coerceIn(2L * 1024 * 1024, 64L * 1024 * 1024).toInt()
    }
}
