package com.shopmanager.app.data.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

/** Result of a single "تحقق من التحديثات" tap. Modeled as a sealed class
 * (rather than a plain nullable) so the admin panel can show *why* a check
 * failed — a wrong URL, a server error, a malformed JSON body, or plain
 * network unreachability — instead of just "didn't work", which is the
 * whole point of having a developer panel for chasing server problems. */
sealed class UpdateCheckResult {
    data class UpToDate(val current: AppVersion) : UpdateCheckResult()
    data class UpdateAvailable(val manifest: UpdateManifest, val current: AppVersion) : UpdateCheckResult()
    data class Failed(val reason: String) : UpdateCheckResult()
}

object UpdateChecker {

    private const val TIMEOUT_MS = 12_000

    // MANUAL BUILDS: hardcoded to the dedicated updates repo, since you're
    // now building the APK by hand (not through GitHub Actions) — there's
    // no GITHUB_RUN_NUMBER/GITHUB_REPOSITORY env var at build time to fill
    // BuildConfig.GITHUB_REPO automatically, so it would've stayed blank
    // forever and this whole check would've always failed with "لم يتم
    // إعداد رابط التحديثات بعد". This repo is SEPARATE from wherever your
    // actual app source lives — it only ever needs to hold Releases (tag +
    // an .apk asset), nothing else, and it must stay Public (see the 404
    // explanation from earlier — a private repo 404s here no matter what).
    private const val UPDATES_REPO = "slman14422003-create/Shop-app-updates"

    /** The GitHub Releases API URL for the dedicated updates repo (see
     * [UPDATES_REPO] above). Every time you publish a new Release there
     * (tag ending in a number + an .apk asset — any name works, see
     * [parseGitHubRelease]), this is what the app reads to notice it and
     * offer the download — no manifest URL ever needs typing into the
     * admin panel by hand. */
    fun defaultManifestUrl(): String =
        "https://api.github.com/repos/$UPDATES_REPO/releases/latest"

    suspend fun check(context: Context, manifestUrl: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = AppVersionInfo.current(context)
        if (manifestUrl.isBlank()) {
            return@withContext UpdateCheckResult.Failed("لم يتم إعداد رابط التحديثات بعد")
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(manifestUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            connection.connect()

            val status = connection.responseCode
            if (status !in 200..299) {
                return@withContext UpdateCheckResult.Failed(describeHttpFailure(status, manifestUrl, connection))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val manifest = parseManifest(body)
                ?: return@withContext UpdateCheckResult.Failed("تنسيق ملف التحديثات غير صحيح")

            return@withContext if (manifest.versionCode > current.code) {
                UpdateCheckResult.UpdateAvailable(manifest, current)
            } else {
                UpdateCheckResult.UpToDate(current)
            }
        } catch (e: java.net.UnknownHostException) {
            return@withContext UpdateCheckResult.Failed("تعذر الوصول للخادم — تحقق من الاتصال بالإنترنت")
        } catch (e: java.net.SocketTimeoutException) {
            return@withContext UpdateCheckResult.Failed("انتهت مهلة الاتصال بالخادم")
        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Failed(e.message ?: "خطأ غير متوقع")
        } finally {
            connection?.disconnect()
        }
    }

    // BUG FIXED (was: raw "الخادم أعاد رمز الحالة 404" with no way for
    // whoever's holding the phone to act on it). A 404 from the GitHub
    // Releases API specifically ("GET /repos/{owner}/{repo}/releases/latest")
    // is not a random server hiccup — GitHub returns exactly this status
    // for three, and only three, root causes, so we can name all three
    // instead of just the number:
    //   1. The repo has never published a GitHub Release yet (the
    //      release.yml workflow hasn't successfully run to completion).
    //   2. The owner/repo name hardcoded into the build (UPDATES_REPO
    //      above) is wrong, or the repo got renamed/deleted since — this
    //      no longer comes from CI, it's a plain constant in this file now
    //      (see [defaultManifestUrl]) so it only ever changes if edited
    //      here directly.
    //   3. The repo is PRIVATE. This is the one that looks identical to
    //      "no releases yet" from a 404 alone but has a different fix:
    //      GitHub's API returns 404 (never 403) for private repos to
    //      unauthenticated requests, specifically so the API doesn't leak
    //      whether a private repo exists. Since this check runs from the
    //      installed app with no token, a private repo will ALWAYS 404
    //      here no matter how many releases it has — making the repo
    //      public (or switching to a manifest hosted somewhere that
    //      doesn't require auth) is the only real fix for that case.
    // For a non-GitHub (custom JSON manifest) URL, 404 just means the
    // file isn't at that address — a plainer message covers that case.
    private fun describeHttpFailure(status: Int, requestUrl: String, connection: HttpURLConnection): String {
        val isGitHubReleasesApi = requestUrl.contains("api.github.com/repos/") && requestUrl.contains("/releases/")
        if (status == 404 && isGitHubReleasesApi) {
            val ghMessage = try {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?.let { JSONObject(it).optString("message", "") }
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
            val detail = ghMessage?.let { " ($it)" } ?: ""
            return "لم يتم العثور على أي إصدار (404)$detail — الأسباب المحتملة:\n" +
                "• لم يتم نشر أي Release على GitHub بعد (تأكد إن release.yml اشتغل ونجح)\n" +
                "• اسم المستودع (owner/repo) المبني بالتطبيق غلط أو المستودع انسمّى اسم تاني\n" +
                "• المستودع خاص (Private) — الـ API بيرجع 404 دايماً بهاي الحالة لأنه بدون توكن دخول، فلازم يصير المستودع عام (Public) أو يتخزن ملف التحديثات بمكان تاني ما بيحتاج تسجيل دخول"
        }
        return "الخادم أعاد رمز الحالة $status"
    }

    private fun parseManifest(raw: String): UpdateManifest? = try {
        val json = JSONObject(raw)
        if (json.has("tag_name")) {
            parseGitHubRelease(json)
        } else {
            parseCustomManifest(json)
        }
    } catch (e: Exception) {
        null
    }

    /** Custom simple manifest shape (still supported for anyone hosting
     * their own JSON somewhere other than GitHub — see the admin panel's
     * "رابط التحديثات" field, which can still be overridden manually):
     * ```json
     * { "versionCode": 2, "versionName": "1.1.0", "apkUrl": "...", "notes": "..." }
     * ```
     */
    private fun parseCustomManifest(json: JSONObject): UpdateManifest? {
        val apkUrl = json.getString("apkUrl")
        val versionCode = json.getLong("versionCode")
        return if (apkUrl.isBlank() || versionCode <= 0) null else UpdateManifest(
            versionCode = versionCode,
            versionName = json.optString("versionName", ""),
            apkUrl = apkUrl,
            notes = json.optString("notes", "")
        )
    }

    /** GitHub's own `GET /repos/{owner}/{repo}/releases/latest` response
     * shape — this is what [defaultManifestUrl]/[UPDATES_REPO] resolves
     * to. You publish a Release by hand on that repo (any tag, any .apk
     * asset name — see the loop below) and this reads it directly; no
     * manifest.json ever needs hosting anywhere.
     *
     * Version comparison: since releases are manual now, YOU pick the
     * tag (e.g. "v1.0.3") and YOU set MANUAL_VERSION_CODE in
     * app/build.gradle.kts (e.g. 3) — the trailing number after the last
     * "." in the tag must match that build's versionCode exactly, or the
     * app either won't notice the update or will nag about it forever
     * (see the long comment on MANUAL_VERSION_CODE in build.gradle.kts).
     */
    private fun parseGitHubRelease(json: JSONObject): UpdateManifest? {
        val tagName = json.optString("tag_name", "")
        val versionCode = tagName.substringAfterLast('.', "").toLongOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            // Prefer the release (smaller, optimized) build; fall back to
            // whatever .apk asset exists if the release one is missing.
            if (name == "shop-manager-release.apk") {
                apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                break
            }
            if (apkUrl == null && name.endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
            }
        }
        val finalApkUrl = apkUrl ?: return null
        return UpdateManifest(
            versionCode = versionCode,
            versionName = tagName,
            apkUrl = finalApkUrl,
            notes = json.optString("body", "")
        )
    }

    /** Cheap "is the update endpoint reachable at all" probe for the admin
     * panel — same request, but callers only care about the boolean. */
    suspend fun canReach(manifestUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (manifestUrl.isBlank()) return@withContext false
        var connection: URLConnection? = null
        try {
            connection = URL(manifestUrl).openConnection().apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            connection.connect()
            true
        } catch (e: Exception) {
            false
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }
}
