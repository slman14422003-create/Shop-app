package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.salman.herbalencyclopedia.BuildConfig
import com.salman.herbalencyclopedia.data.model.AppUpdateConfig
import com.salman.herbalencyclopedia.data.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the admin update metadata and checks the configured GitHub Release.
 * The app is distributed as a direct APK download rather than through Google
 * Play, so an available update points straight at the .apk asset's download
 * URL (falling back to the release page if no .apk asset is attached).
 */
class UpdateRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    /**
     * "full" (نسخة المطوّر/dev harb، بلوحة تحكم إدارية) أو "public" (نسخة
     * المستخدم العادي) — راجع flavorDimensions في app/build.gradle.kts.
     * كل نكهة تُصدَر بووركفلو منفصل تماماً وعلى تاجات ريليس منفصلة على نفس
     * المستودع: release.yml ينشر تحت تاجات "v<اسم>-<كود>" لنسخة public،
     * وfull.yml ينشر تحت تاجات "full-v<اسم>" لنسخة full — بلا أي تداخل
     * بينهما. لذلك لازم معرفة النكهة الحالية هنا لتصفية الريليسات على
     * تاجها الصحيح (راجع [tagMatchesFlavor]) بدل الاعتماد على "أحدث ريليس
     * بتاريخ النشر" الذي قد يرجع تاج النكهة الأخرى بالغلط.
     */
    private val flavor: String = BuildConfig.FLAVOR
) {
    companion object {
        private const val DEFAULT_REPO = "slman14422003-create/Harbs"

        /**
         * مستودع عام فيه ريليسات نسخة public فقط (APK). يُنشَر إليه تلقائياً من
         * release.yml، فيمكن قراءته بلا أي توكن بينما المستودع الأصلي يبقى خاصاً.
         */
        private const val PUBLIC_RELEASES_REPO = "slman14422003-create/Harbs-releases"
        private const val CONFIG_COLLECTION = "app_config"
        private const val CONFIG_DOC = "update"

        /**
         * Public GitHub mirrors, tried in order, only when direct access to
         * GitHub fails outright (the usual symptom when GitHub is blocked at
         * the country level). Each one works as a plain URL prefix: you paste
         * the *original* github.com / api.github.com / githubusercontent.com
         * URL right after the base and it proxies the request through.
         *
         * These are community-run and can go up/down independently of this
         * app, which is exactly why several are tried in sequence instead of
         * hardcoding just one. An admin can also set a private proxy
         * (customProxyBaseUrl in Firestore) that is tried before this list.
         */
        private val PUBLIC_PROXY_MIRRORS = listOf(
            "https://ghfast.top/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://mirror.ghproxy.com/",
            "https://ghproxy.homeboyc.cn/",
            "https://github.akams.cn/"
        )
    }

    // ------------------------------------------------------------------
    // Admin-editable configuration
    // ------------------------------------------------------------------

    suspend fun fetchConfig(): AppUpdateConfig = withContext(Dispatchers.IO) {
        try {
            val doc = db.collection(CONFIG_COLLECTION).document(CONFIG_DOC).get(Source.DEFAULT).await()
            if (doc.exists()) {
                AppUpdateConfig(
                    enabled = doc.getBoolean("enabled") ?: true,
                    githubRepo = doc.getString("github_repo")?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_REPO,
                    overrideVersionName = doc.getString("override_version_name")?.trim()?.takeIf { it.isNotBlank() },
                    releaseNotesOverride = doc.getString("release_notes_override")?.trim()?.takeIf { it.isNotBlank() },
                    minVersionCode = (doc.getLong("min_version_code") ?: 0L).toInt(),
                    useProxyFallback = doc.getBoolean("use_proxy_fallback") ?: true,
                    customProxyBaseUrl = doc.getString("custom_proxy_base_url")?.trim()?.takeIf { it.isNotBlank() },
                    updateSourceUrl = doc.getString("update_source_url")?.trim()?.takeIf { it.isNotBlank() },
                )
            } else {
                AppUpdateConfig(githubRepo = DEFAULT_REPO)
            }
        } catch (e: Exception) {
            // Offline / no config yet: fall back to defaults instead of failing loudly.
            AppUpdateConfig(githubRepo = DEFAULT_REPO)
        }
    }

    suspend fun saveConfig(config: AppUpdateConfig) {
        db.collection(CONFIG_COLLECTION).document(CONFIG_DOC).set(
            hashMapOf(
                "enabled" to config.enabled,
                "github_repo" to config.githubRepo.trim(),
                "override_version_name" to (config.overrideVersionName?.trim() ?: ""),
                "release_notes_override" to (config.releaseNotesOverride?.trim() ?: ""),
                "min_version_code" to config.minVersionCode,
                "use_proxy_fallback" to config.useProxyFallback,
                "custom_proxy_base_url" to (config.customProxyBaseUrl?.trim() ?: ""),
                "update_source_url" to (config.updateSourceUrl?.trim() ?: ""),
                "updated_at" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // ------------------------------------------------------------------
    // Checking GitHub
    // ------------------------------------------------------------------

    /** Returns update info if a newer (or admin-forced) version is available, else null. */
    suspend fun checkForUpdate(currentVersionCode: Int, currentVersionName: String): AppUpdateInfo? =
        checkForUpdate(fetchConfig(), currentVersionCode, currentVersionName)

    /**
     * نفس منطق [checkForUpdate] أعلاه، لكن يأخذ الإعدادات كمعامل مباشر بدل
     * قراءتها من Firestore — يسمح للوحة الإدارة باختبار إعدادات لم تُحفَظ
     * بعد (القيم في الحقول حالياً) فوراً، دون الحاجة لحفظها أولاً ثم الخروج
     * لشاشة المستخدم العادية للتأكد من أنها تعمل كما يُتوقَّع.
     */
    suspend fun checkForUpdate(
        config: AppUpdateConfig,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
            if (!config.enabled) return@withContext null

            // نسخة "full" (نسخة المطوّر، تُعرَف باسم dev harb) مخصّصة للمطوّر
            // نفسه فقط — تحديثاتها تُقرأ حصراً من الريليس الرسمي على GitHub
            // مباشرة، بلا أي مرور عبر بروكسي عام أو Worker مخصّص (تلك الآلية
            // مبنية أصلاً لخدمة المستخدم العادي في الدول التي تحجب GitHub،
            // وليست جزءاً من مسار نسخة المطوّر). لذلك تُلغى هذه الإعدادات هنا
            // حتى لو كانت مفعّلة في Firestore للنسخة العامة.
            val effectiveConfig = if (flavor == "full") {
                config.copy(useProxyFallback = false, customProxyBaseUrl = null, updateSourceUrl = null)
            } else {
                // نسخة public: المصدر الوحيد هو مستودع الريليسات العام، بغض النظر عن
                // أي قيمة قديمة محفوظة في Firestore (مستودع خاص أو Worker محذوف).
                config.copy(githubRepo = PUBLIC_RELEASES_REPO, updateSourceUrl = null)
            }

            // إن كان هناك رابط Worker مخصّص (نسخة "public" فقط، بعد الإلغاء
            // أعلاه لنسخة "full")، هو المصدر الوحيد للتحقق من التحديث — لا
            // يُتصل بـ GitHub إطلاقاً في هذه الحالة، لأن الـ Worker نفسه هو
            // من يقرأ من GitHub ويخزّن النتيجة مؤقتاً (Cache)، فالاتصال به
            // مباشرة أسرع وأكثر ثباتاً من تكرار نفس المنطق على الجهاز.
            if (!effectiveConfig.updateSourceUrl.isNullOrBlank()) {
                return@withContext checkCustomSourceUpdate(effectiveConfig, currentVersionCode, currentVersionName)
            }

            val repo = effectiveConfig.githubRepo.trim().trim('/')
            if (repo.isBlank()) return@withContext null

            val release = fetchLatestReleaseWithFallback(repo, effectiveConfig)
            if (release?.apkUrl == null) return@withContext null
            // This project's release workflow tags releases as "v<versionName>-<versionCode>"
            // (see .github/workflows/android-release.yml), e.g. "v2.0.0-42". Split those apart
            // so "42" is compared as the actual build number, not as a 4th version segment —
            // treating it as part of the version text made the app think an update was always
            // available, even right after installing that exact release.
            val parsedVersionName: String?
            val parsedVersionCode: Int?
            if (release != null) {
                val parsed = parseTag(release.tagName)
                parsedVersionName = parsed.first
                parsedVersionCode = parsed.second
            } else {
                parsedVersionName = null
                parsedVersionCode = null
            }

            val remoteVersionName = effectiveConfig.overrideVersionName ?: parsedVersionName
            if (remoteVersionName.isNullOrBlank()) return@withContext null

            val mandatory = effectiveConfig.minVersionCode > 0 && currentVersionCode < effectiveConfig.minVersionCode

            val newer = when {
                // Admin forced a specific version label: nothing numeric to trust, compare as text.
                effectiveConfig.overrideVersionName != null -> isVersionNewer(remoteVersionName, currentVersionName)
                // Normal case: the tag carries a real build number, so compare it directly against
                // the installed versionCode. This is exact — no guessing from a version string.
                parsedVersionCode != null -> currentVersionCode < parsedVersionCode
                // Fallback for a differently-named tag with no numeric build suffix.
                else -> isVersionNewer(remoteVersionName, currentVersionName)
            }
            if (!newer && !mandatory) return@withContext null

            // Direct-download distribution: point the user at the .apk asset itself
            // (falling back to the release page if this release has no .apk attached).
            val releasePageUrl = release?.htmlUrl ?: "https://github.com/$repo/releases"

            AppUpdateInfo(
                versionName = remoteVersionName,
                // Don't show the raw GitHub release body (CI-generated / commit-log style
                // text) to end users — always show a fixed, friendly Arabic message unless
                // the admin explicitly typed a custom one in the admin panel.
                releaseNotes = effectiveConfig.releaseNotesOverride ?: "تم تحديث الأخطاء وإدخال تحسينات جديدة.",
                releasePageUrl = releasePageUrl,
                apkUrl = release?.apkUrl,
                mandatory = mandatory,
                useProxyFallback = effectiveConfig.useProxyFallback,
                customProxyBaseUrl = effectiveConfig.customProxyBaseUrl
            )
        }

    /**
     * Ordered list of URLs to try when actually downloading [originalUrl] (a plain,
     * unproxied github.com / objects.githubusercontent.com link): the direct link
     * first, then each configured proxy mirror in turn — the exact same fallback
     * chain [fetchLatestReleaseWithFallback] uses for the metadata check, but kept
     * independent because the asset CDN can be reachable/blocked differently than
     * the metadata API. Called from [com.salman.herbalencyclopedia.ui.AppViewModel]
     * so the in-app .apk download gets its own retry chain instead of only reusing
     * whichever path the earlier metadata check happened to succeed through.
     */
    fun downloadCandidates(originalUrl: String, useProxyFallback: Boolean, customProxyBaseUrl: String?): List<String> {
        if (!useProxyFallback) return listOf(originalUrl)
        val proxies = buildList {
            customProxyBaseUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(PUBLIC_PROXY_MIRRORS)
        }
        return listOf(originalUrl) + proxies.map { viaProxy(it, originalUrl) }
    }

    /**
     * Tries GitHub directly AND every proxy mirror at the same time, and takes
     * whichever one answers first — instead of the old direct → mirror 1 →
     * mirror 2 → ... chain tried one at a time. That sequential approach was
     * the actual cause of the update check "not working" on networks that
     * block GitHub outright: each attempt only fails after its own multi-
     * second timeout, so a fully-blocked connection had to sit through
     * direct + every mirror's timeout back to back (up to a minute or more)
     * before ever reaching the one that would have worked. Racing them means
     * the check finishes as soon as the *fastest* reachable path answers,
     * whether that's straight to GitHub, or one specific mirror the local
     * network doesn't block — no VPN required either way.
     *
     * The public mirrors in [PUBLIC_PROXY_MIRRORS] are built and mainly used
     * for proxying plain github.com / objects.githubusercontent.com *file*
     * requests (releases, raw files) — not necessarily the api.github.com
     * REST API this method calls for metadata. So this races the JSON API
     * first (direct + every mirror); only if every single one of those comes
     * back empty does it race a second time against plain github.com release
     * pages instead, which is what these mirrors are actually built for.
     */
    private suspend fun fetchLatestReleaseWithFallback(repo: String, config: AppUpdateConfig): ReleaseData? {
        val bases: List<String?> = listOf(null) + if (config.useProxyFallback) {
            buildList {
                config.customProxyBaseUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                addAll(PUBLIC_PROXY_MIRRORS)
            }
        } else emptyList()

        val viaJson = raceFirstSuccess(bases.map { proxyBase -> suspend { fetchLatestRelease(repo, proxyBase) } })
        if (viaJson != null) return viaJson

        return raceFirstSuccess(bases.map { proxyBase -> suspend { fetchLatestReleaseViaHtml(repo, proxyBase) } })
    }

    /**
     * Runs every attempt in [candidates] at once and returns whichever result
     * comes back non-null first, cancelling the rest immediately. Used to turn
     * the old "try each URL one at a time until one works" chains into a race,
     * which is what actually fixes slow/blocked-network update checks — see
     * [fetchLatestReleaseWithFallback].
     */
    private suspend fun <T> raceFirstSuccess(candidates: List<suspend () -> T?>): T? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null
        val results = Channel<T?>(candidates.size)
        val jobs = candidates.map { attempt ->
            launch(Dispatchers.IO) {
                val value = runCatching { attempt() }.getOrNull()
                results.trySend(value)
            }
        }
        var winner: T? = null
        repeat(candidates.size) {
            if (winner == null) {
                val value = results.receive()
                if (value != null) winner = value
            }
        }
        jobs.forEach { it.cancel() }
        winner
    }

    /** Rewrites a github.com/objects.githubusercontent.com URL to go through a proxy prefix. */
    private fun viaProxy(proxyBase: String, url: String): String =
        proxyBase.trimEnd('/') + "/" + url

    private fun fetchLatestReleaseViaHtml(repo: String, proxyBase: String?): ReleaseData? {
        // نفس سبب التعديل بـ [fetchLatestRelease]: رابط "releases/latest"
        // (والتحويل الذي يصدره) يرجع الأحدث زمنياً بغض النظر عن بادئة
        // التاج، وهذا خاطئ هنا لوجود تاجَي إصدار منفصلين (v* و full-v*)
        // على نفس المستودع. بدلها نقرأ صفحة فهرس الريليسات (مرتّبة، الأحدث
        // أولاً) ونلقط أول تاج يطابق النكهة الحالية عبر [tagMatchesFlavor].
        val listPageUrl = "https://github.com/$repo/releases"
        val requestListUrl = if (proxyBase != null) viaProxy(proxyBase, listPageUrl) else listPageUrl
        val listHtml = fetchText(requestListUrl) ?: return null

        val tag = Regex("""releases/tag/([^"/?]+)""")
            .findAll(listHtml)
            .map { it.groupValues[1] }
            .firstOrNull { tagMatchesFlavor(it) }
            ?: return null

        val tagPageUrl = "https://github.com/$repo/releases/tag/$tag"
        val requestTagUrl = if (proxyBase != null) viaProxy(proxyBase, tagPageUrl) else tagPageUrl
        val html = fetchText(requestTagUrl) ?: return null

        // قد تحتوي صفحة الإصدار على رابطَي .apk (full وpublic معاً)، لذا نجمع
        // كل الروابط الموجودة ثم نمرّرها لنفس منطق [pickApkAsset] المستخدم مع
        // استجابة الـ API، بدل أخذ أول رابط .apk يظهر في الصفحة كما كان سابقاً.
        val apkAssets = Regex("""href="(/${Regex.escape(repo)}/releases/download/[^"]+?\.apk)${'"'}""")
            .findAll(html)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .map { path -> path.substringAfterLast('/') to "https://github.com$path" }
            .toList()
        val apkUrl = pickApkAsset(apkAssets)

        return ReleaseData(tagName = tag, body = "", htmlUrl = tagPageUrl, apkUrl = apkUrl)
    }

    /** يجلب نص صفحة عادية (مع اتّباع أي تحويلات) — يُستخدم لقراءة HTML صفحة الإصدار فقط. */
    private fun fetchText(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Harbs-App-Update-Checker")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Splits a release tag into (versionName, versionCode).
     * "v2.0.0-42" -> ("2.0.0", 42). "v2.0.0" (no numeric build suffix) -> ("2.0.0", null).
     */
    private fun parseTag(tag: String): Pair<String, Int?> {
        val withoutV = tag.trim().removePrefix("v").removePrefix("V")
        val lastDash = withoutV.lastIndexOf('-')
        if (lastDash != -1) {
            val code = withoutV.substring(lastDash + 1).toIntOrNull()
            if (code != null) return withoutV.substring(0, lastDash) to code
        }
        return withoutV to null
    }

    private data class ReleaseData(
        val tagName: String,
        val body: String,
        /** Always the plain, unproxied github.com URL — never rewritten, even when this
         *  JSON itself was fetched through a proxy mirror. See [downloadCandidates]. */
        val htmlUrl: String,
        /** Always the plain, unproxied asset URL, for the same reason. */
        val apkUrl: String?
    )

    /**
     * Fetches the latest release JSON from GitHub's API. When [proxyBase] is
     * given, the api.github.com request itself is routed through the proxy
     * (some mirrors only proxy github.com/objects.githubusercontent.com, so a
     * connection failure here is treated the same as any other failure — the
     * caller just moves on to the next mirror). The asset/release-page links
     * inside the JSON body are themselves already plain github.com URLs
     * regardless of how we fetched the JSON, so they're returned as-is —
     * proxying them (if needed at all) happens uniformly later via
     * [downloadCandidates], instead of guessing here whether the same proxy
     * that worked for the metadata API will also work for the asset CDN.
     */
    private fun fetchLatestRelease(repo: String, proxyBase: String? = null): ReleaseData? = try {
        // لا نستخدم "/releases/latest": هذا المسار يرجع أحدث ريليس بتاريخ
        // النشر بغض النظر عن بادئة التاج، بينما full.yml و release.yml
        // يصدران على تاجين منفصلين تماماً على نفس المستودع (full-v* مقابل
        // v*). لو صدر full بعد آخر إصدار public، "/releases/latest" كان
        // يرجع ريليس full لنسخة public (والعكس صحيح) — لهيك نجلب قائمة
        // الريليسات (الأحدث أولاً) ونختار أول واحد تاجه يطابق النكهة
        // الحالية عبر [tagMatchesFlavor].
        val listUrl = "https://api.github.com/repos/$repo/releases?per_page=100"
        val requestUrl = if (proxyBase != null) viaProxy(proxyBase, listUrl) else listUrl
        val conn = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Harbs-App-Update-Checker")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            null
        } else {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val list = org.json.JSONArray(text)
            var result: ReleaseData? = null
            for (i in 0 until list.length()) {
                val json = list.getJSONObject(i)
                if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) continue
                val tag = json.optString("tag_name")
                if (tag.isBlank() || !tagMatchesFlavor(tag)) continue

                val body = json.optString("body")
                val htmlUrl = json.optString("html_url")
                val assets = json.optJSONArray("assets")
                val assetList = if (assets != null) {
                    (0 until assets.length()).map { j ->
                        val asset = assets.getJSONObject(j)
                        asset.optString("name") to asset.optString("browser_download_url")
                    }
                } else emptyList()
                val apkUrl = pickApkAsset(assetList)
                // apkUrl/htmlUrl are left as plain github.com URLs here regardless of
                // [proxyBase] — see the ReleaseData/downloadCandidates doc comments.
                result = ReleaseData(tag, body, htmlUrl, apkUrl)
                break
            }
            result
        }
    } catch (e: Exception) {
        null
    }

    /**
     * يتحقق أن تاج الريليس يخص النكهة الحالية، بدل الاعتماد على "أحدث ريليس
     * بتاريخ النشر" الذي قد يرجع تاج النكهة الأخرى (راجع تعليق [fetchLatestRelease]).
     * public: تاجات "v<رقم>..." (مثال v2.0.0-42) من release.yml.
     * full: تاجات "full-v<رقم>..." (مثال full-v1.0) من full.yml.
     */
    private fun tagMatchesFlavor(tag: String): Boolean {
        val t = tag.trim()
        return if (flavor == "full") {
            Regex("^full-v\\d", RegexOption.IGNORE_CASE).containsMatchIn(t)
        } else {
            Regex("^v\\d", RegexOption.IGNORE_CASE).containsMatchIn(t)
        }
    }

    /**
     * Picks the .apk asset that matches the running edition ([flavor]) out of a
     * release's assets ("name" to "download URL" pairs).
     *
     * The public GitHub release for this project always carries BOTH editions'
     * .apk files at once — "full" (dev harb) and "public" — because one CI run
     * builds and uploads both flavors to the same release (see
     * android-release.yml). Grabbing "whichever .apk comes first" (the old
     * behavior) meant either edition could randomly end up being offered the
     * other edition's file. AGP's default output filename here is
     * "app-<flavor>-release.apk", so assets are matched on that "-<flavor>-"
     * segment. If nothing matches (e.g. an older release built before this
     * fix, with a single un-suffixed .apk attached) and there's exactly one
     * .apk on the release, that one is used as a compatibility fallback —
     * otherwise, no match is treated as no update available, rather than
     * risking handing out the wrong edition's file.
     */
    private fun pickApkAsset(assets: List<Pair<String, String>>): String? {
        val apks = assets.filter { (name, _) -> name.endsWith(".apk", ignoreCase = true) }
        apks.firstOrNull { (name, _) -> name.contains("-$flavor-", ignoreCase = true) }
            ?.let { return it.second }
        return apks.singleOrNull()?.second
    }

    // ------------------------------------------------------------------
    // Custom update source (self-hosted "app-download-proxy" Worker)
    // ------------------------------------------------------------------

    /**
     * Checks for an update using [AppUpdateConfig.updateSourceUrl] instead of
     * GitHub. Calls "<url>/api/latest" for `{ version, size, published_at }`
     * (the same JSON shape the worker's download page itself consumes) and
     * points the resulting download link at "<url>/download".
     */
    private fun checkCustomSourceUpdate(
        config: AppUpdateConfig,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateInfo? {
        val bases = config.updateSourceUrl!!.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotBlank() }
        val data = bases.firstNotNullOfOrNull { fetchCustomSourceRelease(it) } ?: return null

        val remoteVersionName = config.overrideVersionName ?: data.version
        if (remoteVersionName.isBlank()) return null

        val mandatory = config.minVersionCode > 0 && currentVersionCode < config.minVersionCode
        val newer = isVersionNewer(remoteVersionName, currentVersionName)
        if (!newer && !mandatory) return null

        return AppUpdateInfo(
            versionName = remoteVersionName,
            releaseNotes = config.releaseNotesOverride ?: "تم تحديث الأخطاء وإدخال تحسينات جديدة.",
            releasePageUrl = data.apkUrl,
            apkUrl = data.apkUrl,
            mandatory = mandatory,
            // لا داعي لسلسلة البروكسي هنا: الرابط أصلاً يشير إلى الـ Worker
            // الخاص بالمستخدم، وهو نفسه المسؤول عن أي وصول بديل لـ GitHub.
            useProxyFallback = false,
            customProxyBaseUrl = null
        )
    }

    private data class CustomSourceData(val version: String, val apkUrl: String)

    private fun fetchCustomSourceRelease(baseUrl: String): CustomSourceData? = try {
        val base = baseUrl.trim().trimEnd('/')
        val apiUrl = "$base/api/latest"
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Harbs-App-Update-Checker")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            null
        } else {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(text)
            if (json.optBoolean("error", false)) return null
            val version = json.optString("version").trim()
            if (version.isBlank()) null else CustomSourceData(version = version, apkUrl = "$base/download")
        }
    } catch (e: Exception) {
        null
    }

    /** Simple numeric version comparison: 2.10.1 > 2.9.9, 2.1 > 2.0.9, etc. */
    private fun isVersionNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trim().trimStart('v', 'V').split('.', '-')
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }


}
