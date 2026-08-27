package com.shopmanager.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shopmanager.app.data.performance.PerformanceMode
import com.shopmanager.app.ui.theme.AppColorPalette
import com.shopmanager.app.ui.theme.AppThemeMode
import java.security.MessageDigest

/**
 * Replaces the old debt-app `auth.js` / `security.js` - those files existed
 * in the repo but were never loaded by index.html (dead code, no real
 * protection existed). This is a small but genuinely working local PIN lock,
 * useful for a shared shop device: nothing fancy, no Firebase account needed,
 * just a 4-6 digit PIN hashed and stored locally.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shop_manager_settings", Context.MODE_PRIVATE)

    var themeMode: AppThemeMode
        get() = AppThemeMode.valueOf(prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name)!!)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** "لوحة الألوان" — the accent color pair used for the header gradient,
     * status bar, and Material primary/secondary colors. Defaults to the
     * original Indigo/Violet look so nobody's app changes color unless they
     * open Settings and pick something else. */
    var colorPalette: AppColorPalette
        get() = runCatching {
            AppColorPalette.valueOf(prefs.getString(KEY_COLOR_PALETTE, AppColorPalette.INDIGO.name)!!)
        }.getOrDefault(AppColorPalette.INDIGO)
        set(value) = prefs.edit().putString(KEY_COLOR_PALETTE, value.name).apply()

    /** Currency label shown across the app (money amounts, share text, notifications). */
    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY, "ل.س") ?: "ل.س"
        set(value) = prefs.edit().putString(KEY_CURRENCY, value.ifBlank { "ل.س" }).apply()

    /** Master switch for the shortage-list / new-debt local notifications. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    /** "تفضيل الأداء" — manual override of the auto-detected device tier
     * (see [PerformanceMode]). Defaults to AUTO so nobody's experience
     * changes unless they open Settings and pick something else. */
    var performanceMode: PerformanceMode
        get() = runCatching {
            PerformanceMode.valueOf(prefs.getString(KEY_PERFORMANCE_MODE, PerformanceMode.AUTO.name)!!)
        }.getOrDefault(PerformanceMode.AUTO)
        set(value) = prefs.edit().putString(KEY_PERFORMANCE_MODE, value.name).apply()

    /** "رابط فحص التحديثات" — a JSON manifest URL (versionCode/versionName/
     * apkUrl/notes) the normal-user "تحقق من التحديثات" button in Settings
     * reads from. Set only from the hidden developer panel (لوحة المسؤول
     * السرية) — a regular user never sees or edits this, they just tap
     * "تحقق من التحديثات" and this URL is what gets checked. Empty by
     * default so a fresh install with no manifest configured yet fails
     * quietly/gracefully instead of hitting a placeholder URL. */
    /** "رابط فحص التحديثات" — the JSON/GitHub-release URL the normal-user
     * "تحقق من التحديثات" button in Settings reads from. Defaults to this
     * repo's own GitHub Releases API URL (see
     * UpdateChecker.defaultManifestUrl — built automatically from
     * BuildConfig.GITHUB_REPO at CI build time, no link ever needs typing
     * in by hand). A value explicitly saved from the developer panel
     * (لوحة المسؤول السرية) still overrides that default, for anyone who
     * wants to point updates somewhere else. */
    var updateManifestUrl: String
        get() = prefs.getString(KEY_UPDATE_MANIFEST_URL, null)
            ?: com.shopmanager.app.data.updates.UpdateChecker.defaultManifestUrl()
        set(value) = prefs.edit().putString(KEY_UPDATE_MANIFEST_URL, value.trim()).apply()

    /** Clears any manually-saved override so [updateManifestUrl] goes back
     * to auto-resolving this repo's GitHub Releases URL. */
    fun resetUpdateManifestUrlToDefault() {
        prefs.edit().remove(KEY_UPDATE_MANIFEST_URL).apply()
    }

    /** Timestamp (epoch millis) of the last time anyone — dev or user —
     * successfully reached the update manifest, shown in the admin panel
     * as a quick "is the update server even reachable" signal. */
    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hash(pin)
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_COLOR_PALETTE = "color_palette"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_UPDATE_MANIFEST_URL = "update_manifest_url"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check_at"
    }
}
