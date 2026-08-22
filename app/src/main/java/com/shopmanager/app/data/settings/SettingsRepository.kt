package com.shopmanager.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    var lowStockThreshold: Double
        get() = prefs.getFloat(KEY_LOW_STOCK, 5f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LOW_STOCK, value.toFloat()).apply()

    /** Currency label shown across the app (money amounts, share text, notifications). */
    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY, "ل.س") ?: "ل.س"
        set(value) = prefs.edit().putString(KEY_CURRENCY, value.ifBlank { "ل.س" }).apply()

    /** Master switch for the low-stock / new-debt local notifications. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

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
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_LOW_STOCK = "low_stock_threshold"
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
