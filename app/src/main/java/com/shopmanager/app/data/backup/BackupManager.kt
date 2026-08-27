package com.shopmanager.app.data.backup

import android.content.Context
import com.shopmanager.app.data.debts.Debt
import com.shopmanager.app.data.debts.DebtsRepository
import com.shopmanager.app.data.debts.Person
import com.shopmanager.app.data.materials.Material
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MaterialsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fully local, on-device daily backup. No server involved, no notification
 * ever posted for it, no storage permission needed — snapshots live in the
 * app's own private storage (`filesDir/backups`), the same place Android
 * already isolates for this app alone, so they're invisible in any file
 * manager and are wiped automatically only if the app itself is
 * uninstalled (same lifetime guarantee as everything else the app owns).
 *
 * Restoring is intentionally NEVER silent/automatic in the sense of
 * quietly overwriting Firestore on its own: it always requires either an
 * explicit tap in Settings, or a tap on the one-time prompt shown when the
 * live data fails to load from the server (see DebtsViewModel /
 * MaterialsViewModel `hasSyncError`). That keeps a real, temporary network
 * hiccup from ever being "fixed" by silently discarding whatever the
 * server actually has.
 */
/**
 * Which trigger produced a given local snapshot. Kept as a filename
 * prefix (not a JSON field) so pruning can tell the two apart with a
 * plain directory listing — no need to open/parse every file just to
 * decide what to delete.
 */
enum class BackupKind(val filePrefix: String) {
    /** Once a day, via [com.shopmanager.app.data.backup.DailyBackupWorker]. */
    DAILY("backup_daily_"),
    /** Right after an add/edit/delete, via [InstantBackupWorker]. */
    INSTANT("backup_instant_")
}

object BackupManager {

    private const val DIR_NAME = "backups"
    private const val LEGACY_PREFIX = "backup_"
    private const val SUFFIX = ".json"

    /**
     * BUG FIXED (silent backup deleting real history): before
     * [InstantBackupWorker] existed, every snapshot came from
     * [DailyBackupWorker] alone (~1/day), so a single "keep the newest 30
     * files" cap really did mean "roughly a month of history" like the
     * old comment here claimed. Once instant backups started firing after
     * *every* add/edit of a material, a debt, a price, or anything else,
     * a single busy day of normal shop use (dozens of edits) could by
     * itself produce more than 30 files — which silently pruned away
     * last week's/last month's daily snapshots to make room, even though
     * nothing about *those* was actually old or unwanted. That's exactly
     * what read as "saving a material for backup deletes the old backup
     * and replaces it with just the material (or debt, or whatever) I
     * just touched" — the frequent, low-value instant snapshots were
     * evicting the sparse, high-value daily ones.
     *
     * Fix: daily and instant snapshots are now pruned against two
     * completely independent caps (see [pruneOldBackups]), so no number
     * of same-day instant backups can ever touch the daily/legacy
     * history. Legacy pre-fix files (no kind prefix at all, from before
     * this change existed) are counted against the daily cap, since they
     * were all daily-worker output at the time.
     */
    private const val MAX_DAILY_KEPT = 30

    /** Instant snapshots are a short-lived "last few seconds of edits"
     * safety net, not a history — keeping a handful is enough to recover
     * from a crash/restore right after editing, without letting a busy
     * editing session balloon local storage or crowd out daily backups. */
    private const val MAX_INSTANT_KEPT = 8

    private fun stampFormat() = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)

    private fun backupDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    data class BackupInfo(
        val file: File,
        val createdAt: Long,
        val personsCount: Int,
        val materialsCount: Int
    )

    /**
     * Reads current data straight from Firestore — which itself already
     * falls back to its local offline cache automatically when there's no
     * network (see FirebaseModule's persistent cache setup) — and writes
     * one timestamped JSON snapshot, then prunes anything past
     * [MAX_BACKUPS_KEPT]. Throws only if there's truly nothing synced yet
     * (e.g. very first run, no network, empty cache); the daily worker
     * treats that as "try again next scheduled run", never as something
     * worth surfacing to the person.
     */
    suspend fun performBackup(
        context: Context,
        debtsRepo: DebtsRepository,
        materialsRepo: MaterialsRepository,
        kind: BackupKind = BackupKind.DAILY
    ) {
        val (persons, debts) = debtsRepo.fetchAllForBackup()
        val (materials, prices, catalog) = materialsRepo.fetchAllForBackup()

        val pricesJson = JSONObject()
        prices.forEach { (name, price) -> pricesJson.put(name, price) }

        val root = JSONObject().apply {
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("persons", JSONArray(persons.map { it.toJson() }))
            put("debts", JSONArray(debts.map { it.toJson() }))
            put("materials", JSONArray(materials.map { it.toJson() }))
            put("prices", pricesJson)
            put("catalog", JSONArray(catalog.map { it.toJson() }))
        }

        // Millisecond-resolution timestamp in the filename (not just
        // seconds) so two backups triggered a moment apart — e.g. an
        // instant backup plus a WorkManager retry — can never collide on
        // the exact same filename and silently overwrite one another.
        val file = File(backupDir(context), "${kind.filePrefix}${stampFormat().format(System.currentTimeMillis())}$SUFFIX")
        file.writeText(root.toString())

        pruneOldBackups(context)
    }

    fun listBackups(context: Context): List<BackupInfo> =
        backupDir(context)
            .listFiles { f -> f.name.startsWith(LEGACY_PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f ->
                runCatching {
                    val json = JSONObject(f.readText())
                    BackupInfo(
                        file = f,
                        createdAt = json.optLong("createdAt", f.lastModified()),
                        personsCount = json.optJSONArray("persons")?.length() ?: 0,
                        materialsCount = json.optJSONArray("materials")?.length() ?: 0
                    )
                }.getOrNull()
            } ?: emptyList()

    fun latestBackup(context: Context): BackupInfo? = listBackups(context).firstOrNull()

    fun hasAnyBackup(context: Context): Boolean = listBackups(context).isNotEmpty()

    /**
     * Prunes daily/legacy and instant snapshots against two independent
     * caps (see [MAX_DAILY_KEPT]/[MAX_INSTANT_KEPT] for why they must stay
     * separate). A file with no recognized kind prefix is treated as
     * legacy daily output, so upgrading the app never deletes backups a
     * person already had before this fix existed.
     */
    private fun pruneOldBackups(context: Context) {
        val all = backupDir(context)
            .listFiles { f -> f.name.startsWith(LEGACY_PREFIX) && f.name.endsWith(SUFFIX) }
            ?.toList() ?: return

        val (instant, dailyAndLegacy) = all.partition { it.name.startsWith(BackupKind.INSTANT.filePrefix) }

        instant.sortedByDescending { it.lastModified() }.drop(MAX_INSTANT_KEPT).forEach { it.delete() }
        dailyAndLegacy.sortedByDescending { it.lastModified() }.drop(MAX_DAILY_KEPT).forEach { it.delete() }
    }

    /** Writes a chosen local snapshot back into Firestore, replacing what's
     * there now. Only ever called after explicit user confirmation. */
    suspend fun restore(
        backup: BackupInfo,
        debtsRepo: DebtsRepository,
        materialsRepo: MaterialsRepository
    ) {
        val json = JSONObject(backup.file.readText())

        val persons = json.getJSONArray("persons").toObjectList { it.toPerson() }
        val debts = json.getJSONArray("debts").toObjectList { it.toDebt() }
        val materials = json.getJSONArray("materials").toObjectList { it.toMaterial() }
        val catalog = json.getJSONArray("catalog").toObjectList { it.toCatalogItem() }
        val pricesJson = json.getJSONObject("prices")
        val prices = pricesJson.keys().asSequence().associateWith { pricesJson.getDouble(it) }

        debtsRepo.restoreFromBackup(persons, debts)
        materialsRepo.restoreFromBackup(materials, prices, catalog)
    }

    // --- (de)serialization helpers -----------------------------------

    private inline fun <T> JSONArray.toObjectList(map: (JSONObject) -> T): List<T> =
        (0 until length()).map { map(getJSONObject(it)) }

    private fun Person.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("amount", amount); put("date", date); put("createdAt", createdAt)
    }

    private fun Debt.toJson() = JSONObject().apply {
        put("id", id); put("personId", personId); put("amount", amount)
        put("date", date); put("note", note); put("createdAt", createdAt)
    }

    private fun Material.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("quantity", quantity)
        put("unit", unit); put("section", section); put("updatedAt", updatedAt)
    }

    private fun MaterialCatalogItem.toJson() = JSONObject().apply { put("id", id); put("name", name) }

    private fun JSONObject.toPerson() = Person(
        id = optString("id"), name = optString("name"), amount = optDouble("amount", 0.0),
        date = optString("date"), createdAt = optLong("createdAt", 0L)
    )

    private fun JSONObject.toDebt() = Debt(
        id = optString("id"), personId = optString("personId"), amount = optDouble("amount", 0.0),
        date = optString("date"), note = optString("note"), createdAt = optLong("createdAt", 0L)
    )

    private fun JSONObject.toMaterial() = Material(
        id = optString("id"), name = optString("name"), quantity = optDouble("quantity", 0.0),
        unit = optString("unit"), section = optString("section", "main"), updatedAt = optLong("updatedAt", 0L)
    )

    private fun JSONObject.toCatalogItem() = MaterialCatalogItem(id = optString("id"), name = optString("name"))
}
