package com.shopmanager.app.data.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Parsed contents of the update-manifest JSON that lives at whatever URL
 * the hidden developer panel (لوحة المسؤول) configures. Expected shape:
 *
 * ```json
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1.0",
 *   "apkUrl": "https://example.com/shop-manager-1.1.0.apk",
 *   "notes": "إصلاحات وتحسينات"
 * }
 * ```
 *
 * `notes` is optional; everything else is required for the manifest to be
 * considered valid (see [UpdateChecker]).
 */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

/** The current app's own version, read from PackageManager rather than
 * BuildConfig — this project doesn't have android.buildFeatures.buildConfig
 * turned on, and reading it from the installed package info works exactly
 * the same and needs no build-file change. */
data class AppVersion(val code: Long, val name: String)

object AppVersionInfo {
    fun current(context: Context): AppVersion = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        AppVersion(code = code, name = info.versionName ?: "?")
    } catch (e: PackageManager.NameNotFoundException) {
        AppVersion(code = 0L, name = "?")
    }
}
