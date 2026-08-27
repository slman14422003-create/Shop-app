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
object BackupManager {

    private const val DIR_NAME = "backups"
    private const val PREFIX = "backup_"
    private const val SUFFIX = ".json"

    /** Keep roughly a month of snapshots; older ones are pruned
     * automatically after every successful backup. Each edit/add/delete
     * writes its own new timestamped file (see [performBackup]) — nothing
     * is ever overwritten in place, only the oldest file past this count
     * is removed to keep local storage bounded. */
    private const val MAX_BACKUPS_KEPT = 30

    private fun stampFormat() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

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
        materialsRepo: MaterialsRepository
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

        val file = File(backupDir(context), "$PREFIX${stampFormat().format(System.currentTimeMillis())}$SUFFIX")
        file.writeText(root.toString())

        pruneOldBackups(context)
    }

    fun listBackups(context: Context): List<BackupInfo> =
        backupDir(context)
            .listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
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

    private fun pruneOldBackups(context: Context) {
        val files = backupDir(context)
            .listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_BACKUPS_KEPT).forEach { it.delete() }
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
