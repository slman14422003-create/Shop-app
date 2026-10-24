package com.salman.herbalencyclopedia.data.model


/**
 * Admin-editable update settings, stored in Firestore at "app_config/update"
 * (same project as the app's other data, editable from the app's admin panel).
 *
 * The app checks the GitHub release metadata for version information. The
 * app is distributed as a direct APK download (not published on Google
 * Play), so an update hands the user straight to the .apk asset's download
 * link on GitHub, opened in the browser.
 */
data class AppUpdateConfig(
    val enabled: Boolean = true,
    val githubRepo: String = "slman14422003-create/Harbs",
    val overrideVersionName: String? = null,
    val releaseNotesOverride: String? = null,
    val minVersionCode: Int = 0,
    /**
     * When GitHub itself is blocked in the user's country, a direct request to
     * api.github.com / github.com never completes (with or without a VPN, from
     * the app's perspective it just times out). If true, the update check and
     * the resulting download link automatically fall back to a public GitHub
     * mirror/proxy so the feature keeps working without asking the user to
     * turn on a VPN. Direct GitHub is always tried first; the proxy is only
     * used when direct access fails.
     */
    val useProxyFallback: Boolean = true,
    /**
     * Optional custom proxy base URL (e.g. a self-hosted Cloudflare Worker),
     * tried before the built-in public mirrors. Expected to work as a prefix:
     * "<base><original GitHub URL>", e.g. "https://my-proxy.example.com/".
     */
    val customProxyBaseUrl: String? = null,
    /**
     * Optional: base URL of a self-hosted "app-download-proxy" Cloudflare
     * Worker (the same worker used for the public download page). When set,
     * this becomes the ONLY source used for update checks — GitHub is not
     * contacted at all. The app calls "<url>/api/latest" for version info
     * and points the download at "<url>/download", so the check always goes
     * through the worker's own cache instead of hitting api.github.com
     * directly. Expected format: "https://my-worker.example.workers.dev"
     * (a trailing slash is stripped automatically).
     */
    val updateSourceUrl: String? = null
)

/** Result of a successful update check: there IS a newer version available. */
data class AppUpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    /** Direct (unproxied) download link to the .apk asset on the GitHub release, if one was attached. */
    val apkUrl: String? = null,
    val mandatory: Boolean,
    /**
     * Proxy settings captured at check time, carried along so the actual .apk
     * download can build its own direct-then-proxy-mirrors fallback chain later.
     * This matters because the release metadata (api.github.com) and the release
     * asset itself (github.com/releases/download/... which redirects to
     * objects.githubusercontent.com) are separate GitHub domains that can be
     * blocked independently of one another — the metadata check succeeding
     * unproxied is no guarantee the asset download will also succeed unproxied,
     * and vice versa.
     */
    val useProxyFallback: Boolean = true,
    val customProxyBaseUrl: String? = null
)
