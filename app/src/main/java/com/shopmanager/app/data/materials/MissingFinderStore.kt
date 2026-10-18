package com.shopmanager.app.data.materials

import android.content.Context
import com.shopmanager.app.ui.common.ArabicText
import org.json.JSONObject

data class PendingMaterial(
    val name: String,
    val quantity: Double,
    val unit: String,
    val notes: String = "",
    val important: Boolean = false
)

class MissingFinderStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("shop_missing_finder", Context.MODE_PRIVATE)

    data class Stat(
        val added: Int = 0,
        val important: Int = 0,
        val exists: Int = 0,
        val lastAdded: Long = 0L,
        val qty: Int = 1,
        val unit: String = MaterialUnit.KG.label
    )

    data class Insight(
        val added: Int,
        val exists: Int,
        val daysSinceLast: Int?,
        val qty: Int,
        val unit: MaterialUnit
    )

    private fun key(name: String) = ArabicText.normalize(name)

    private fun readStats(): MutableMap<String, Stat> {
        val raw = prefs.getString(KEY_STATS, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, Stat>()
            obj.keys().forEach { k ->
                val o = obj.getJSONObject(k)
                out[k] = Stat(
                    added = o.optInt("a"), important = o.optInt("i"), exists = o.optInt("e"),
                    lastAdded = o.optLong("t"), qty = o.optInt("q", 1).coerceAtLeast(1),
                    unit = o.optString("u", MaterialUnit.KG.label)
                )
            }
            out
        }.getOrDefault(mutableMapOf())
    }

    private fun writeStats(map: Map<String, Stat>) {
        val obj = JSONObject()
        map.forEach { (k, s) ->
            obj.put(k, JSONObject().put("a", s.added).put("i", s.important).put("e", s.exists)
                .put("t", s.lastAdded).put("q", s.qty).put("u", s.unit))
        }
        prefs.edit().putString(KEY_STATS, obj.toString()).apply()
    }

    @Synchronized
    fun recordAdded(name: String, quantity: Double, unit: String, important: Boolean) {
        val stats = readStats()
        val k = key(name)
        val old = stats[k] ?: Stat()
        stats[k] = old.copy(
            added = old.added + 1,
            important = old.important + if (important) 1 else 0,
            lastAdded = System.currentTimeMillis(),
            qty = quantity.toInt().coerceAtLeast(1),
            unit = unit
        )
        writeStats(stats)
    }

    @Synchronized
    fun recordImportant(name: String) {
        val stats = readStats()
        val k = key(name)
        val old = stats[k] ?: Stat()
        stats[k] = old.copy(important = old.important + 1)
        writeStats(stats)
    }

    @Synchronized
    fun recordExists(names: Collection<String>) {
        if (names.isEmpty()) return
        val stats = readStats()
        names.forEach { n ->
            val k = key(n)
            val old = stats[k] ?: Stat()
            stats[k] = old.copy(exists = old.exists + 1)
        }
        writeStats(stats)
    }

    private fun score(s: Stat?, now: Long): Double {
        if (s == null || (s.added == 0 && s.important == 0)) return 0.0
        val days = (now - s.lastAdded) / DAY_MS
        val recency = when {
            s.lastAdded == 0L -> 0.0
            days <= 3 -> 3.0
            days <= 7 -> 2.0
            days <= 30 -> 1.0
            else -> 0.0
        }
        val base = s.added * 2.0 + s.important * 3.0 + recency
        return (base - minOf(s.exists, s.added) * 0.4).coerceAtLeast(0.1)
    }

    /** Catalog ordered by learned relevance (most likely to be missing first), ties alphabetical. */
    fun ranked(items: List<MaterialCatalogItem>): List<MaterialCatalogItem> {
        val stats = readStats()
        val now = System.currentTimeMillis()
        return items.sortedWith(
            compareByDescending<MaterialCatalogItem> { score(stats[key(it.name)], now) }.thenBy { it.name }
        )
    }

    /** Only items with a learned score, best first, capped. */
    fun suggested(items: List<MaterialCatalogItem>, limit: Int = 12): List<MaterialCatalogItem> {
        val stats = readStats()
        val now = System.currentTimeMillis()
        return items.map { it to score(stats[key(it.name)], now) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun frequency(name: String): Int = readStats()[key(name)]?.added ?: 0

    fun suggestion(name: String): Pair<Int, MaterialUnit>? {
        val s = readStats()[key(name)] ?: return null
        if (s.added == 0) return null
        return s.qty to MaterialUnit.fromLabel(s.unit)
    }

    fun insight(name: String): Insight? {
        val s = readStats()[key(name)] ?: return null
        if (s.added == 0 && s.exists == 0) return null
        val days = if (s.lastAdded > 0L) ((System.currentTimeMillis() - s.lastAdded) / DAY_MS).toInt() else null
        return Insight(s.added, s.exists, days, s.qty, MaterialUnit.fromLabel(s.unit))
    }

    @Synchronized
    fun rename(oldName: String, newName: String) {
        val from = key(oldName)
        val to = key(newName)
        if (from == to) return
        val stats = readStats()
        val moved = stats.remove(from) ?: return
        val existing = stats[to]
        stats[to] = if (existing == null) moved else existing.copy(
            added = existing.added + moved.added,
            important = existing.important + moved.important,
            exists = existing.exists + moved.exists,
            lastAdded = maxOf(existing.lastAdded, moved.lastAdded)
        )
        writeStats(stats)
    }

    private companion object {
        const val KEY_STATS = "stats_json"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
