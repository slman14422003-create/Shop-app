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
        val skipped: Int = 0,
        val lastAdded: Long = 0L,
        val avgGapDays: Float = 0f,
        val qty: Int = 1,
        val unit: String = MaterialUnit.KG.label
    )

    data class Insight(
        val added: Int,
        val exists: Int,
        val daysSinceLast: Int?,
        val qty: Int,
        val unit: MaterialUnit,
        val avgGapDays: Int?,
        val due: Boolean
    )

    @Volatile private var statsCache: MutableMap<String, Stat>? = null
    @Volatile private var pairsCache: MutableMap<String, MutableMap<String, Int>>? = null
    private val keyCache = HashMap<String, String>()

    @Synchronized
    private fun key(name: String): String = keyCache.getOrPut(name) { ArabicText.normalize(name) }

    @Synchronized
    private fun stats(): MutableMap<String, Stat> {
        statsCache?.let { return it }
        val raw = prefs.getString(KEY_STATS, null)
        val out = mutableMapOf<String, Stat>()
        if (raw != null) runCatching {
            val obj = JSONObject(raw)
            obj.keys().forEach { k ->
                val o = obj.getJSONObject(k)
                out[k] = Stat(
                    added = o.optInt("a"), important = o.optInt("i"), exists = o.optInt("e"),
                    skipped = o.optInt("s"), lastAdded = o.optLong("t"),
                    avgGapDays = o.optDouble("g", 0.0).toFloat(),
                    qty = o.optInt("q", 1).coerceAtLeast(1),
                    unit = o.optString("u", MaterialUnit.KG.label)
                )
            }
        }
        statsCache = out
        return out
    }

    @Synchronized
    private fun pairs(): MutableMap<String, MutableMap<String, Int>> {
        pairsCache?.let { return it }
        val raw = prefs.getString(KEY_PAIRS, null)
        val out = mutableMapOf<String, MutableMap<String, Int>>()
        if (raw != null) runCatching {
            val obj = JSONObject(raw)
            obj.keys().forEach { k ->
                val o = obj.getJSONObject(k)
                val m = mutableMapOf<String, Int>()
                o.keys().forEach { k2 -> m[k2] = o.optInt(k2) }
                out[k] = m
            }
        }
        pairsCache = out
        return out
    }

    private fun persistStats() {
        val map = stats()
        val obj = JSONObject()
        map.forEach { (k, s) ->
            obj.put(k, JSONObject().put("a", s.added).put("i", s.important).put("e", s.exists)
                .put("s", s.skipped).put("t", s.lastAdded).put("g", s.avgGapDays.toDouble())
                .put("q", s.qty).put("u", s.unit))
        }
        prefs.edit().putString(KEY_STATS, obj.toString()).apply()
    }

    private fun persistPairs() {
        val obj = JSONObject()
        pairs().forEach { (k, m) ->
            val o = JSONObject()
            m.forEach { (k2, c) -> o.put(k2, c) }
            obj.put(k, o)
        }
        prefs.edit().putString(KEY_PAIRS, obj.toString()).apply()
    }

    @Synchronized
    fun recordAdded(name: String, quantity: Double, unit: String, important: Boolean) {
        val stats = stats()
        val k = key(name)
        val old = stats[k] ?: Stat()
        val now = System.currentTimeMillis()
        val gap = if (old.lastAdded > 0L) (now - old.lastAdded).toFloat() / DAY_MS else 0f
        val avg = when {
            gap < 0.5f -> old.avgGapDays
            old.avgGapDays <= 0f -> gap
            else -> old.avgGapDays * 0.6f + gap * 0.4f
        }
        stats[k] = old.copy(
            added = old.added + 1,
            important = old.important + if (important) 1 else 0,
            lastAdded = now,
            avgGapDays = avg,
            qty = quantity.toInt().coerceAtLeast(1),
            unit = unit
        )
        persistStats()
    }

    @Synchronized
    fun recordImportant(name: String) {
        val stats = stats()
        val k = key(name)
        val old = stats[k] ?: Stat()
        stats[k] = old.copy(important = old.important + 1)
        persistStats()
    }

    @Synchronized
    fun recordExists(names: Collection<String>) {
        if (names.isEmpty()) return
        val stats = stats()
        names.forEach { n ->
            val k = key(n)
            val old = stats[k] ?: Stat()
            stats[k] = old.copy(exists = old.exists + 1)
        }
        persistStats()
    }

    @Synchronized
    fun recordSkipped(names: Collection<String>) {
        if (names.isEmpty()) return
        val stats = stats()
        names.forEach { n ->
            val k = key(n)
            val old = stats[k] ?: Stat()
            stats[k] = old.copy(skipped = old.skipped + 1)
        }
        persistStats()
    }

    /** Learns which materials go missing together in one session. */
    @Synchronized
    fun recordBatch(names: Collection<String>) {
        val keys = names.map { key(it) }.distinct().take(MAX_BATCH)
        if (keys.size < 2) return
        val pairs = pairs()
        for (a in keys) {
            val m = pairs.getOrPut(a) { mutableMapOf() }
            for (b in keys) if (a != b) m[b] = (m[b] ?: 0) + 1
            if (m.size > MAX_PARTNERS) {
                val keep = m.entries.sortedByDescending { it.value }.take(MAX_PARTNERS).associate { it.key to it.value }
                m.clear(); m.putAll(keep)
            }
        }
        persistPairs()
    }

    /** Name of a material from [pendingNames] that is usually missing together with [name], if any. */
    @Synchronized
    fun partnerOf(name: String, pendingNames: Collection<String>): String? {
        if (pendingNames.isEmpty()) return null
        val m = pairs()[key(name)] ?: return null
        var best: String? = null
        var bestCount = 1
        for (p in pendingNames) {
            val c = m[key(p)] ?: 0
            if (c > bestCount) { bestCount = c; best = p }
        }
        return best
    }

    private fun isDue(s: Stat, now: Long): Boolean {
        if (s.added < 2 || s.avgGapDays < 1f || s.lastAdded == 0L) return false
        val days = (now - s.lastAdded).toFloat() / DAY_MS
        return days >= s.avgGapDays * 0.8f
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
        val due = if (isDue(s, now)) 2.5 else 0.0
        val base = s.added * 2.0 + s.important * 3.0 + recency + due
        val penalty = minOf(s.exists, s.added) * 0.4 + minOf(s.skipped, 5) * 0.15
        return (base - penalty).coerceAtLeast(0.1)
    }

    /** Catalog ordered by learned relevance (most likely to be missing first), ties alphabetical. */
    @Synchronized
    fun ranked(items: List<MaterialCatalogItem>): List<MaterialCatalogItem> {
        val stats = stats()
        val now = System.currentTimeMillis()
        val scored = items.map { it to score(stats[key(it.name)], now) }
        return scored.sortedWith(compareByDescending<Pair<MaterialCatalogItem, Double>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    /** Only items with a learned score, best first, capped. */
    @Synchronized
    fun suggested(items: List<MaterialCatalogItem>, limit: Int = 12): List<MaterialCatalogItem> {
        val stats = stats()
        val now = System.currentTimeMillis()
        return items.map { it to score(stats[key(it.name)], now) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    @Synchronized
    fun frequency(name: String): Int = stats()[key(name)]?.added ?: 0

    @Synchronized
    fun suggestion(name: String): Pair<Int, MaterialUnit>? {
        val s = stats()[key(name)] ?: return null
        if (s.added == 0) return null
        return s.qty to MaterialUnit.fromLabel(s.unit)
    }

    @Synchronized
    fun insight(name: String): Insight? {
        val s = stats()[key(name)] ?: return null
        if (s.added == 0 && s.exists == 0) return null
        val now = System.currentTimeMillis()
        val days = if (s.lastAdded > 0L) ((now - s.lastAdded) / DAY_MS).toInt() else null
        return Insight(
            added = s.added,
            exists = s.exists,
            daysSinceLast = days,
            qty = s.qty,
            unit = MaterialUnit.fromLabel(s.unit),
            avgGapDays = if (s.avgGapDays >= 1f) s.avgGapDays.toInt() else null,
            due = isDue(s, now)
        )
    }

    @Synchronized
    fun rename(oldName: String, newName: String) {
        val from = key(oldName)
        val to = key(newName)
        if (from == to) return
        val stats = stats()
        val moved = stats.remove(from) ?: return
        val existing = stats[to]
        stats[to] = if (existing == null) moved else existing.copy(
            added = existing.added + moved.added,
            important = existing.important + moved.important,
            exists = existing.exists + moved.exists,
            skipped = existing.skipped + moved.skipped,
            lastAdded = maxOf(existing.lastAdded, moved.lastAdded)
        )
        persistStats()
        val pairs = pairs()
        pairs.remove(from)?.let { pairs.getOrPut(to) { mutableMapOf() }.putAll(it) }
        pairs.values.forEach { m -> m.remove(from)?.let { c -> m[to] = (m[to] ?: 0) + c } }
        persistPairs()
    }

    private companion object {
        const val KEY_STATS = "stats_json"
        const val KEY_PAIRS = "pairs_json"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_PARTNERS = 8
        const val MAX_BATCH = 30
    }
}
