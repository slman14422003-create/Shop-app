package com.shopmanager.app.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.materials.MaterialsRepository
import java.util.concurrent.TimeUnit

/**
 * Runs once a day in the background and writes one local JSON snapshot of
 * everything (customers, debts, materials, prices, catalog) to the app's
 * own private storage — see [BackupManager]. Deliberately silent end to
 * end: this worker never posts a notification, on success or failure. If
 * it fails (no network yet, nothing synced at all so far, etc.) it just
 * retries on WorkManager's own backoff and tries again on the next
 * scheduled run; nothing is ever shown to the person either way.
 *
 * Scheduled with KEEP (not UPDATE, unlike BackgroundSyncWorker): a daily
 * backup's exact anchor time doesn't matter, so re-launching the app
 * should never reset an already-running schedule — only the very first
 * launch after install actually enqueues it.
 */
class DailyBackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Same defensive re-init as BackgroundSyncWorker: this can run in a
        // fresh process where MainActivity never got the chance to call
        // FirebaseModule.init() first.
        FirebaseModule.init(applicationContext)

        return try {
            BackupManager.performBackup(applicationContext, DebtsRepository(), MaterialsRepository())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "shop_manager_daily_backup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
