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
 * Fully local, on-device backup. No server involved, no notification ever
 * posted for it, no storage permission needed — the snapshot lives in the
 * app's own private storage (`filesDir/backups`), the same place Android
 * already isolates for this app alone, so it's invisible in any file
 * manager and is wiped automatically only if the app itself is
 * uninstalled (same lifetime guarantee as everything else the app owns).
 *
 * Only ONE snapshot is ever kept on disk. Every [performBackup] call
 * writes the new snapshot to its own new file first, then immediately
 * deletes every other file in the backups directory (see
 * [pruneOldBackups]). An older snapshot is never useful once a newer one
 * exists — restoring always means "put back the most recent state this
 * device saw" — so keeping a history around is just wasted device
 * storage, and it's exactly what used to read as confusing ("why are
 * there five of these") in Settings.
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

        pruneOldBackups(context, keep = file)
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
     * Deletes every backup file except [keep] (the one just written),
     * regardless of kind (daily/instant/legacy) — only the newest
     * snapshot is ever worth having on disk, see the class doc above.
     */
    private fun pruneOldBackups(context: Context, keep: File) {
        backupDir(context)
            .listFiles { f -> f.name.startsWith(LEGACY_PREFIX) && f.name.endsWith(SUFFIX) }
            ?.forEach { f -> if (f != keep) f.delete() }
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
