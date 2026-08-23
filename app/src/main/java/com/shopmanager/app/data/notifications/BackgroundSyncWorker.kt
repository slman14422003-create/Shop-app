package com.shopmanager.app.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.Source
import com.shopmanager.app.data.FirebaseModule
import com.shopmanager.app.data.settings.SettingsRepository
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Free background notifications for new debts / shortage-list changes.
 *
 * A "real" push (a notification arriving instantly the moment another
 * device writes to Firestore, app fully closed) needs either Firebase
 * Cloud Messaging triggered server-side, or a Cloud Function watching the
 * collection — both require the Firebase project to be on the Blaze
 * (pay-as-you-go) plan, even if actual usage stays inside the free quota.
 * That's a real account/billing change, not just app code, so it isn't
 * what's wired up here.
 *
 * What WorkManager gives us for free: the OS itself wakes the app
 * periodically (Android decides the exact moment, batched with other apps
 * for battery reasons — this is not instant, typically within a window of
 * the requested interval), this worker does ONE cheap server read per
 * collection (not a live listener — nothing stays connected or drains
 * battery between runs), diffs it against what was seen last time, and
 * notifies only about what's actually new. No server component, no
 * billing change, works fully offline-tolerant (skips silently on
 * failure and retries next cycle).
 */
class BackgroundSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The worker can run in a fresh process (app fully killed since the
        // last launch), where MainActivity never ran and Firebase was never
        // initialized — so it's initialized defensively here too.
        FirebaseModule.init(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)

        val settings = SettingsRepository(applicationContext)
        if (!settings.notificationsEnabled) return Result.success()

        return try {
            checkNewDebts(settings)
            checkShortageList()
            Result.success()
        } catch (e: Exception) {
            // Network blip / offline — try again on the next scheduled run
            // rather than spamming retries.
            Result.retry()
        }
    }

    private suspend fun checkNewDebts(settings: SettingsRepository) {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val db = FirebaseModule.debtsDb
        val snapshot = db.collection("persons").get(Source.SERVER).await()

        val knownIds = prefs.getStringSet(KEY_KNOWN_PERSONS, null)
        // First run ever: just seed the known set silently, nothing to
        // compare against yet (otherwise every existing customer would
        // fire a "new debt" notification the first time this runs).
        if (knownIds != null) {
            for (doc in snapshot.documents) {
                if (doc.id !in knownIds) {
                    val name = doc.getString("name") ?: continue
                    val amount = doc.getDouble("amount") ?: 0.0
                    if (amount > 0) {
                        NotificationHelper.showNewDebtNotification(
                            applicationContext, name, formatAmount(amount), settings.currencySymbol
                        )
                    }
                }
            }
        }
        prefs.edit()
            .putStringSet(KEY_KNOWN_PERSONS, snapshot.documents.map { it.id }.toSet())
            .apply()
    }

    private suspend fun checkShortageList() {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val db = FirebaseModule.materialsDb
        val snapshot = db.collection("spices_final_v12").get(Source.SERVER).await()
        val names = snapshot.documents.mapNotNull { it.getString("name") }

        val lastNames = prefs.getStringSet(KEY_KNOWN_MATERIALS, null)
        if (lastNames != null && names.toSet() != lastNames && names.isNotEmpty()) {
            NotificationHelper.showShoppingListNotification(applicationContext, names)
        }
        prefs.edit().putStringSet(KEY_KNOWN_MATERIALS, names.toSet()).apply()
    }

    private fun formatAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

    companion object {
        private const val PREFS = "shop_manager_sync"
        private const val KEY_KNOWN_PERSONS = "known_person_ids"
        private const val KEY_KNOWN_MATERIALS = "known_material_names"
        private const val UNIQUE_WORK_NAME = "shop_manager_background_sync"

        /**
         * Every 6 hours is a deliberate choice, not the WorkManager minimum
         * (15 minutes): a shorter interval would check more often but costs
         * more battery for a shop-inventory app where "a few hours old" is
         * perfectly fine, and Android may still delay/batch it regardless
         * of what's requested here. Call once (MainActivity.onCreate) —
         * KEEP means re-launching the app never creates duplicate workers.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(6, TimeUnit.HOURS)
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
