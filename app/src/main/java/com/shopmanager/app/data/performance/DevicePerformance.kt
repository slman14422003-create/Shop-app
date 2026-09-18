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
 * - FEATURE ADDED ("اصلاحات للاجهزة اللي فيها معالج رسوميات ضعيف"): the
 *   three signals above are all about RAM/CPU — none of them actually
 *   look at the GPU, so a phone with decent RAM and core count but a
 *   genuinely weak/old GPU (common on budget MediaTek Helio-series chips,
 *   which often pair 4GB+ RAM with a low-end Mali GPU) still landed on
 *   STANDARD and got the full gradients/glow/blur-adjacent effects it
 *   can't actually push at 60fps. ActivityManager's own
 *   `getDeviceConfigurationInfo().reqGlEsVersion` — the OpenGL ES version
 *   the device's GPU driver reports supporting — is a standard, free
 *   (no GL context needed) proxy for GPU generation: anything below ES
 *   3.0 is old/entry-level hardware by now. Reported the same way as the
 *   others below (as `glEsVersion`, and folded into `tier` alongside RAM/
 *   cores), so a weak GPU alone is now enough to land a device on LOW even
 *   when its RAM and core count look fine.
 *
 * Any one of these tripping is enough to land a device on LOW — false
 * positives (an OK phone getting the lighter UI) just mean slightly fewer
 * animations, which is a much cheaper mistake than false negatives (a weak
 * phone getting the full-effects UI and lagging).
 */
enum class PerformanceTier { LOW, STANDARD }

/**
 * "تفضيل الأداء" (Settings → الأداء): lets the person override the
 * automatic device detection above instead of being stuck with whatever
 * [DevicePerformance.detectTier] guessed.
 *
 * - AUTO (default): use the detected [PerformanceTier] as before — no
 *   behavior change for anyone who never opens this setting.
 * - HIGH: always run the full-effects UI (gradients, longer transitions,
 *   the counting-up animation), even on a device that auto-detected as
 *   LOW. For someone whose "weak" phone actually handles it fine.
 * - LOW: always run the reduced-motion/no-gradient UI, even on a device
 *   that auto-detected as STANDARD. For anyone who simply prefers a
 *   snappier, more static feel or wants to save battery.
 */
enum class PerformanceMode { AUTO, HIGH, LOW }

/** Combines the auto-detected tier with the user's manual preference —
 * the single place both are resolved into the [PerformanceTier] actually
 * handed to the UI via [LocalPerformanceTier]. */
fun resolvePerformanceTier(detected: PerformanceTier, mode: PerformanceMode): PerformanceTier =
    when (mode) {
        PerformanceMode.AUTO -> detected
        PerformanceMode.HIGH -> PerformanceTier.STANDARD
        PerformanceMode.LOW -> PerformanceTier.LOW
    }

object DevicePerformance {
    private const val PREFS = "shop_manager_device"
    private const val KEY_TIER = "performance_tier"

    private const val LOW_RAM_THRESHOLD_MB = 3072L
    private const val LOW_CORE_THRESHOLD = 4
    // OpenGL ES 3.0 = 0x30000 (major version in the upper 16 bits). Any
    // device reporting less than that is old/entry-level GPU hardware —
    // ES 3.0 shipped in 2012/Android 4.3, so by now a sub-3.0 report means
    // a genuinely weak GPU, not just an unusual one.
    private const val MIN_GL_ES_VERSION = 0x30000

    /** FEATURE ADDED (Settings → الأداء diagnostics): the raw signals
     * [detectTier] measures, exposed on their own so the person can see
     * *why* their device landed on a given tier instead of it being an
     * opaque decision — useful context right next to the new "إعادة فحص
     * أداء الجهاز" button. Always measures fresh (never reads the cached
     * tier), so this reflects the device's current state even if the
     * cached classification is stale. */
    data class DeviceInfo(
        val totalRamMb: Long,
        val cores: Int,
        val osFlaggedLowRam: Boolean,
        val glEsVersion: Int,
        val weakGpu: Boolean,
        val tier: PerformanceTier
    )

    fun currentDeviceInfo(context: Context): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val osFlaggedLowRam = activityManager?.isLowRamDevice == true
        // reqGlEsVersion is free to read (no GL context/surface needed) —
        // it's just the driver-reported capability from the package
        // manager's configuration info, same source `<uses-feature
        // android:glEsVersion>` checks against at install time.
        val glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: MIN_GL_ES_VERSION
        val weakGpu = glEsVersion in 1 until MIN_GL_ES_VERSION
        val lowRam = totalRamMb in 1..LOW_RAM_THRESHOLD_MB
        val lowCores = cores in 1..LOW_CORE_THRESHOLD
        val tier = if (osFlaggedLowRam || weakGpu || (lowRam && lowCores)) PerformanceTier.LOW else PerformanceTier.STANDARD
        return DeviceInfo(totalRamMb, cores, osFlaggedLowRam, glEsVersion, weakGpu, tier)
    }

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

        // Reuses the exact same signals/thresholds as [currentDeviceInfo]
        // (see the BUG FIXED note that used to live here: LOW only when
        // isLowRamDevice OR both RAM *and* core count point that way, never
        // from a single weak signal alone — except a weak GPU, which is
        // its own independent trigger since a phone can easily have fine
        // RAM/cores paired with an old/entry-level GPU) — one shared
        // implementation, so the diagnostics shown in Settings can never
        // silently drift from what actually decided the cached tier.
        val tier = currentDeviceInfo(context).tier
        prefs.edit().putString(KEY_TIER, tier.name).apply()
        return tier
    }

    /** Clears the cached classification so the next detectTier() call
     * re-measures from scratch. Not wired to any screen yet — available
     * if a "recheck device" option in Settings is ever added, so a
     * misclassification doesn't require a reinstall to fix. */
    fun resetCachedTier(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TIER).apply()
    }
}

/** Provided once near the root of the tree (see MainActivity); defaults to
 * STANDARD so Compose previews and anything outside the provider still
 * render the full-effects UI. */
val LocalPerformanceTier = staticCompositionLocalOf { PerformanceTier.STANDARD }
