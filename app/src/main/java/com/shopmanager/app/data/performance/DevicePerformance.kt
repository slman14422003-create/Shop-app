package com.shopmanager.app.data.performance

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * "وضع لكل هاتف": instead of one fixed UI for every device, the app decides
 * once — the very first time it runs on a phone — whether that phone is
 * entry-level or not, and remembers the answer locally (no server call, no
 * network, no per-model whitelist to maintain).
 *
 * There is no reliable public API for "which phone model is this" mapped to
 * "how fast is it", and hardcoding a list of model names (redmi a10,
 * sm-a165 for the Samsung A16, ...) would break the moment a new budget
 * phone ships. So instead of guessing from the model string, this reads the
 * two signals Android actually exposes that correlate with real-world
 * jank on low-end hardware:
 *
 * - total RAM (via ActivityManager.MemoryInfo) — a Redmi A10 ships with
 *   2–3GB, a Samsung A16 with 4–6GB.
 * - CPU core count (Runtime.availableProcessors()) — budget SoCs are
 *   commonly quad-core, mid-range and up are usually 8-core.
 * - ActivityManager.isLowRamDevice() — Android's own "go edition / low
 *   RAM" flag, set by the OEM/OS itself for exactly this purpose.
 *
 * Any one of these tripping is enough to land a device on LOW — false
 * positives (an OK phone getting the lighter UI) just mean slightly fewer
 * animations, which is a much cheaper mistake than false negatives (a weak
 * phone getting the full-effects UI and lagging).
 */
enum class PerformanceTier { LOW, STANDARD }

object DevicePerformance {
    private const val PREFS = "shop_manager_device"
    private const val KEY_TIER = "performance_tier"

    private const val LOW_RAM_THRESHOLD_MB = 3072L
    private const val LOW_CORE_THRESHOLD = 4

    /**
     * Reads the cached tier if this device has been classified before
     * (every launch after the first), otherwise measures it once and
     * persists the result — so this never re-runs the ActivityManager
     * query on every cold start.
     */
    fun detectTier(context: Context): PerformanceTier {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_TIER, null)?.let { cached ->
            return runCatching { PerformanceTier.valueOf(cached) }.getOrDefault(PerformanceTier.STANDARD)
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val osFlaggedLowRam = activityManager?.isLowRamDevice == true

        val tier = if (osFlaggedLowRam ||
            (totalRamMb in 1..LOW_RAM_THRESHOLD_MB) ||
            cores in 1..LOW_CORE_THRESHOLD
        ) {
            PerformanceTier.LOW
        } else {
            PerformanceTier.STANDARD
        }

        prefs.edit().putString(KEY_TIER, tier.name).apply()
        return tier
    }
}

/** Provided once near the root of the tree (see MainActivity); defaults to
 * STANDARD so Compose previews and anything outside the provider still
 * render the full-effects UI. */
val LocalPerformanceTier = staticCompositionLocalOf { PerformanceTier.STANDARD }
