package com.shopmanager.app

import android.app.Application
import androidx.work.Configuration

/**
 * Paired with the `tools:node="remove"` on WorkManagerInitializer in
 * AndroidManifest.xml. Implementing Configuration.Provider is the
 * officially documented way to get WorkManager's "on-demand
 * initialization": instead of WorkManager building its Room database via
 * a ContentProvider that runs unconditionally before this class's
 * onCreate on every single cold start, it now only initializes itself the
 * first time something actually calls WorkManager.getInstance(...) — in
 * this app, that's BackgroundSyncWorker.schedule(), which MainActivity
 * calls from a background coroutine (see MainActivity.kt), not on the
 * main thread during startup.
 */
class ShopManagerApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
