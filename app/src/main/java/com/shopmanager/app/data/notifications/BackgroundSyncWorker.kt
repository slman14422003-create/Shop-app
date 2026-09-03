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
import com.shopmanager.app.data.performance.DevicePerformance
import com.shopmanager.app.data.performance.PerformanceTier
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

    /**
     * BUG FIXED (missing background notification): this used to diff the
     * "persons" collection, so it only ever caught a brand-new customer —
     * same gap as the old in-app DebtsViewModel logic (see its comment).
     * A new debt added to an *existing* customer while the app is fully
     * closed never surfaced here either. Diffing the "debts" collection
     * itself instead catches both cases the same way the in-app check now
     * does, so a phone that's been closed for a while and gets woken up by
     * this worker reports the same things the live in-app listener would
     * have.
     */
    private suspend fun checkNewDebts(settings: SettingsRepository) {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val db = FirebaseModule.debtsDb
        val personsSnapshot = db.collection("persons").get(Source.SERVER).await()
        val debtsSnapshot = db.collection("debts").get(Source.SERVER).await()
        val personNamesById = personsSnapshot.documents.associate { it.id to (it.getString("name") ?: "عميل") }

        val knownIds = prefs.getStringSet(KEY_KNOWN_DEBTS, null)
        // First run ever: just seed the known set silently, nothing to
        // compare against yet (otherwise every existing debt would fire a
        // "new debt" notification the first time this runs).
        if (knownIds != null) {
            for (doc in debtsSnapshot.documents) {
                if (doc.id !in knownIds) {
                    val personId = doc.getString("personId") ?: continue
                    val amount = doc.getDouble("amount") ?: 0.0
                    if (amount > 0) {
                        val name = personNamesById[personId] ?: "عميل"
                        // BUG FIXED: pass doc.id (the debt id) so several new
                        // debts caught in the same background sync cycle
                        // (e.g. after being offline a while) each get their
                        // own notification instead of overwriting one
                        // another - see NotificationHelper.showNewDebtNotification.
                        NotificationHelper.showNewDebtNotification(
                            applicationContext, name, formatAmount(amount), settings.currencySymbol, doc.id
                        )
                    }
                }
            }
        }
        prefs.edit()
            .putStringSet(KEY_KNOWN_DEBTS, debtsSnapshot.documents.map { it.id }.toSet())
            .remove(KEY_KNOWN_PERSONS) // no longer used — replaced by KEY_KNOWN_DEBTS above
            .apply()
    }

    /**
     * BUG FIXED (edit/delete notifications unreliable): this used to diff
     * only the *set of names* on the shortage list, the same gap as the old
     * in-app MaterialsViewModel logic (see its comment) - editing an
     * existing item's quantity/unit while its name stayed the same never
     * looked like a change here either, so a phone woken up by this worker
     * could report nothing even though the list had genuinely changed.
     * Diffing each item's full signature (id + name + quantity + unit)
     * instead catches edits the same way the in-app check now does.
     */
    private suspend fun checkShortageList() {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val db = FirebaseModule.materialsDb
        val snapshot = db.collection("spices_final_v12").get(Source.SERVER).await()
        val signature = snapshot.documents.associate { doc ->
            doc.id to "${doc.getString("name") ?: ""}|${doc.getDouble("quantity") ?: 0.0}|${doc.getString("unit") ?: ""}"
        }
        val names = snapshot.documents.mapNotNull { it.getString("name") }.distinct()

        val lastSignature = prefs.getString(KEY_KNOWN_MATERIALS, null)
        val encoded = signature.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
        if (lastSignature != null && encoded != lastSignature && names.isNotEmpty()) {
            NotificationHelper.showShoppingListNotification(applicationContext, names)
        }
        prefs.edit().putString(KEY_KNOWN_MATERIALS, encoded).apply()
    }

    private fun formatAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

    companion object {
        private const val PREFS = "shop_manager_sync"
        private const val KEY_KNOWN_PERSONS = "known_person_ids"
        private const val KEY_KNOWN_DEBTS = "known_debt_ids"
        // Renamed from "known_material_names": that old key held a
        // StringSet (just names). Reusing it here with getString() would
        // throw ClassCastException on any device upgrading from the old
        // version with a value already stored under it - a new key name
        // sidesteps that entirely (worst case: one silent reseed on the
        // first run after updating, same as a fresh install).
        private const val KEY_KNOWN_MATERIALS = "known_material_signature"
        private const val UNIQUE_WORK_NAME = "shop_manager_background_sync"

        /**
         * Every 15 minutes — WorkManager's absolute minimum periodic
         * interval; anything shorter than this is silently clamped up to
         * it by the OS, so 15 is the fastest this check can ever actually
         * run. Each run is still just the one cheap server read per
         * collection described above (no live connection kept open
         * between runs), and Android may still delay/batch the exact
         * moment for battery reasons regardless of what's requested here.
         * Call once (MainActivity.onCreate) — KEEP means re-launching the
         * app never creates duplicate workers, and switching an existing
         * install from the old 6-hour schedule to this one happens the
         * next time the worker is (re)scheduled without any extra code,
         * since KEEP only skips scheduling when a worker under this name
         * already exists — it doesn't need to match the old interval.
         *
         * "أداء الأجهزة الاقتصادية": on a device that auto-detected (or was
         * manually set) as [PerformanceTier.LOW], this silent background
         * check is also the kind of thing that quietly drains a weak
         * phone's battery/data over a day without the person ever seeing
         * it running — there's no UI to blame. So on LOW tier the interval
         * is stretched to 30 minutes (still just an occasional cheap read,
         * simply less often) and an extra `setRequiresBatteryNotLow(true)`
         * constraint is added, so the OS skips a cycle entirely on a phone
         * that's already low on battery instead of waking radios/CPU for a
         * network read at exactly the worst moment. STANDARD/HIGH devices
         * keep the original 15-minute, battery-unconstrained schedule
         * unchanged.
         */
        fun schedule(context: Context, tier: PerformanceTier = DevicePerformance.detectTier(context)) {
            val isLowTier = tier == PerformanceTier.LOW
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .apply { if (isLowTier) setRequiresBatteryNotLow(true) }
                .build()

            val intervalMinutes = if (isLowTier) 30L else 15L
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
