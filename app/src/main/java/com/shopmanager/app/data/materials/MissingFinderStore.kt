package com.shopmanager.app.data.materials

import android.content.Context
import com.shopmanager.app.data.learning.DebouncedTask
import com.shopmanager.app.data.learning.LearningHub
import com.shopmanager.app.ui.common.ArabicText
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class PendingMaterial(
    val name: String,
    val quantity: Double,
    val unit: String,
    val notes: String = "",
    val important: Boolean = false
)

/**
 * Local learning model for missing materials.
 *
 * Threading: every mutation is posted to the single learning thread and persisted with a
 * debounce; queries are in-memory reads that never touch disk and return empty results
 * until [preload] has finished.
 */
class MissingFinderStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("shop_missing_finder", Context.MODE_PRIVATE)

    data class Stat(
        val added: Int = 0,
        val important: Int = 0,
        val exists: Int = 0,
        val skipped: Int = 0,
        val lastAdded: Long = 0L,
        val avgGapDays: Float = 0f,
        val gapDev: Float = 0f,
        val qty: Int = 1,
        val unit: String = MaterialUnit.KG.label,
        val name: String = "",
        val weekdays: IntArray = IntArray(7)
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

    private val statsMap = HashMap<String, Stat>()
    private val pairsMap = HashMap<String, MutableMap<String, Int>>()
    private val keyCache = HashMap<String, String>()
    @Volatile private var loaded = false

    private val writer = DebouncedTask(700L) { writeToPrefs() }

    private fun key(name: String): String {
        if (keyCache.size > 3000) keyCache.clear()
        return keyCache.getOrPut(name) { ArabicText.normalize(name) }
    }

    fun preload() {
        synchronized(this) { ensureLoaded() }
    }

    private fun ensureLoaded() {
        if (loaded) return
        prefs.getString(KEY_STATS, null)?.let { raw ->
            runCatching {
                val obj = JSONObject(raw)
                obj.keys().forEach { k ->
                    val o = obj.getJSONObject(k)
                    val wd = IntArray(7)
                    o.optJSONArray("w")?.let { a -> for (i in 0 until min(7, a.length())) wd[i] = a.optInt(i) }
                    statsMap[k] = Stat(
                        added = o.optInt("a"), important = o.optInt("i"), exists = o.optInt("e"),
                        skipped = o.optInt("s"), lastAdded = o.optLong("t"),
                        avgGapDays = o.optDouble("g", 0.0).toFloat(),
                        gapDev = o.optDouble("d", 0.0).toFloat(),
                        qty = o.optInt("q", 1).coerceAtLeast(1),
                        unit = o.optString("u", MaterialUnit.KG.label),
                        name = o.optString("n", ""),
                        weekdays = wd
                    )
                }
            }
        }
        prefs.getString(KEY_PAIRS, null)?.let { raw ->
            runCatching {
                val obj = JSONObject(raw)
                obj.keys().forEach { k ->
                    val o = obj.getJSONObject(k)
                    val m = HashMap<String, Int>()
                    o.keys().forEach { k2 -> m[k2] = o.optInt(k2) }
                    pairsMap[k] = m
                }
            }
        }
        loaded = true
    }

    private fun writeToPrefs() {
        val statsCopy: Map<String, Stat>
        val pairsCopy: Map<String, Map<String, Int>>
        synchronized(this) {
            if (!loaded) return
            statsCopy = HashMap(statsMap)
            pairsCopy = pairsMap.mapValues { HashMap(it.value) }
        }
        val so = JSONObject()
        statsCopy.forEach { (k, s) ->
            so.put(
                k,
                JSONObject().put("a", s.added).put("i", s.important).put("e", s.exists)
                    .put("s", s.skipped).put("t", s.lastAdded).put("g", s.avgGapDays.toDouble())
                    .put("d", s.gapDev.toDouble()).put("q", s.qty).put("u", s.unit).put("n", s.name)
                    .put("w", JSONArray(s.weekdays.toList()))
            )
        }
        val po = JSONObject()
        pairsCopy.forEach { (k, m) ->
            val o = JSONObject()
            m.forEach { (k2, c) -> o.put(k2, c) }
            po.put(k, o)
        }
        prefs.edit().putString(KEY_STATS, so.toString()).putString(KEY_PAIRS, po.toString()).apply()
    }

    fun flushNow() = writer.flushNow()

    private fun post(onDone: () -> Unit = {}, block: () -> Unit) {
        LearningHub.scope.launch {
            synchronized(this@MissingFinderStore) {
                ensureLoaded()
                block()
                if (statsMap.size > MAX_ITEMS) prune()
            }
            writer.trigger()
            onDone()
        }
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        val drop = statsMap.entries
            .sortedBy { score(it.value, now) }
            .take(statsMap.size - MAX_ITEMS + 50)
            .map { it.key }
        drop.forEach { statsMap.remove(it); pairsMap.remove(it) }
        pairsMap.values.forEach { m -> drop.forEach { m.remove(it) } }
    }

    fun recordAdded(name: String, quantity: Double, unit: String, important: Boolean, onDone: () -> Unit = {}) {
        post(onDone) {
            val k = key(name)
            val old = statsMap[k] ?: Stat()
            val now = System.currentTimeMillis()
            val gap = if (old.lastAdded > 0L) (now - old.lastAdded).toFloat() / DAY_MS else 0f
            var avg = old.avgGapDays
            var dev = old.gapDev
            if (gap >= 0.5f) {
                if (avg <= 0f) {
                    avg = gap
                } else {
                    dev = dev * 0.6f + kotlin.math.abs(gap - avg) * 0.4f
                    avg = avg * 0.6f + gap * 0.4f
                }
            }
            val wd = old.weekdays.copyOf()
            wd[weekdayIndex()]++
            statsMap[k] = old.copy(
                added = old.added + 1,
                important = old.important + if (important) 1 else 0,
                lastAdded = now,
                avgGapDays = avg,
                gapDev = dev,
                qty = quantity.toInt().coerceAtLeast(1),
                unit = unit,
                name = name,
                weekdays = wd
            )
        }
    }

    fun recordImportant(name: String, onDone: () -> Unit = {}) {
        post(onDone) {
            val k = key(name)
            val old = statsMap[k] ?: Stat(name = name)
            statsMap[k] = old.copy(important = old.important + 1)
        }
    }

    fun recordExists(names: Collection<String>, onDone: () -> Unit = {}) {
        if (names.isEmpty()) return
        val copy = names.toList()
        post(onDone) {
            copy.forEach { n ->
                val k = key(n)
                val old = statsMap[k] ?: Stat(name = n)
                statsMap[k] = old.copy(exists = old.exists + 1)
            }
        }
    }

    fun recordSkipped(names: Collection<String>, onDone: () -> Unit = {}) {
        if (names.isEmpty()) return
        val copy = names.toList()
        post(onDone) {
            copy.forEach { n ->
                val k = key(n)
                val old = statsMap[k] ?: Stat(name = n)
                statsMap[k] = old.copy(skipped = old.skipped + 1)
            }
        }
    }

    /** Learns which materials go missing together in one session. */
    fun recordBatch(names: Collection<String>, onDone: () -> Unit = {}) {
        val copy = names.toList()
        post(onDone) {
            val keys = copy.map { key(it) }.distinct().take(MAX_BATCH)
            if (keys.size < 2) return@post
            for (a in keys) {
                val m = pairsMap.getOrPut(a) { HashMap() }
                for (b in keys) if (a != b) m[b] = (m[b] ?: 0) + 1
                if (m.size > MAX_PARTNERS) {
                    val keep = m.entries.sortedByDescending { it.value }.take(MAX_PARTNERS).associate { it.key to it.value }
                    m.clear(); m.putAll(keep)
                }
            }
        }
    }

    fun rename(oldName: String, newName: String, onDone: () -> Unit = {}) {
        post(onDone) {
            val from = key(oldName)
            val to = key(newName)
            if (from == to) return@post
            val moved = statsMap.remove(from) ?: return@post
            val existing = statsMap[to]
            statsMap[to] = if (existing == null) moved.copy(name = newName) else existing.copy(
                added = existing.added + moved.added,
                important = existing.important + moved.important,
                exists = existing.exists + moved.exists,
                skipped = existing.skipped + moved.skipped,
                lastAdded = max(existing.lastAdded, moved.lastAdded),
                name = newName,
                weekdays = IntArray(7) { existing.weekdays[it] + moved.weekdays[it] }
            )
            pairsMap.remove(from)?.let { pairsMap.getOrPut(to) { HashMap() }.putAll(it) }
            pairsMap.values.forEach { m -> m.remove(from)?.let { c -> m[to] = (m[to] ?: 0) + c } }
        }
    }

    @Synchronized
    fun partnerOf(name: String, pendingNames: Collection<String>): String? {
        if (!loaded || pendingNames.isEmpty()) return null
        val m = pairsMap[key(name)] ?: return null
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
        if (days > 120f && days > s.avgGapDays * 6f) return false
        val threshold = max(s.avgGapDays - 0.5f * s.gapDev, s.avgGapDays * 0.6f)
        return days >= threshold
    }

    private fun score(s: Stat?, now: Long): Double {
        if (s == null || (s.added == 0 && s.important == 0)) return 0.0
        val days = if (s.lastAdded > 0L) (now - s.lastAdded).toDouble() / DAY_MS else 9999.0
        val recency = if (s.lastAdded == 0L) 0.0 else 3.0 * exp(-days / 14.0)
        var due = 0.0
        if (isDue(s, now)) {
            val ratio = (days / s.avgGapDays).coerceAtMost(2.0)
            due = 1.5 + (ratio - 0.6).coerceAtLeast(0.0)
        }
        val total = s.weekdays.sum()
        var weekday = 0.0
        if (total >= 3) {
            val share = s.weekdays[weekdayIndex()].toDouble() / total
            if (share >= 0.4) weekday = 1.5 * share
        }
        val conf = s.added / (s.added + 2.0)
        val base = ln(1.0 + s.added) * 3.0 + s.important * 2.5 + recency + due + weekday
        val penalty = s.exists.toDouble() / (s.added + s.exists + 1.0) * 3.0 + min(s.skipped, 5) * 0.15
        return (base * (0.6 + 0.4 * conf) - penalty).coerceAtLeast(0.1)
    }

    @Synchronized
    fun ranked(items: List<MaterialCatalogItem>): List<MaterialCatalogItem> {
        if (!loaded) return items.sortedBy { it.name }
        val now = System.currentTimeMillis()
        val scored = items.map { it to score(statsMap[key(it.name)], now) }
        return scored.sortedWith(compareByDescending<Pair<MaterialCatalogItem, Double>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    @Synchronized
    fun suggested(items: List<MaterialCatalogItem>, limit: Int = 12): List<MaterialCatalogItem> {
        if (!loaded) return emptyList()
        val now = System.currentTimeMillis()
        return items.map { it to score(statsMap[key(it.name)], now) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Display names of materials that are statistically due, excluding those already in [exclude]. */
    @Synchronized
    fun dueNames(exclude: Collection<String>, catalog: List<MaterialCatalogItem>, limit: Int = 3): List<String> {
        if (!loaded) return emptyList()
        val now = System.currentTimeMillis()
        val skip = exclude.map { key(it) }.toHashSet()
        val catalogNames = HashMap<String, String>(catalog.size)
        catalog.forEach { catalogNames[key(it.name)] = it.name }
        return statsMap.entries
            .filter { it.key !in skip && isDue(it.value, now) && it.value.exists <= it.value.added }
            .map { (catalogNames[it.key] ?: it.value.name) to score(it.value, now) }
            .filter { it.first.isNotBlank() }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    @Synchronized
    fun frequency(name: String): Int = if (loaded) statsMap[key(name)]?.added ?: 0 else 0

    @Synchronized
    fun suggestion(name: String): Pair<Int, MaterialUnit>? {
        if (!loaded) return null
        val s = statsMap[key(name)] ?: return null
        if (s.added == 0) return null
        return s.qty to MaterialUnit.fromLabel(s.unit)
    }

    @Synchronized
    fun insight(name: String): Insight? {
        if (!loaded) return null
        val s = statsMap[key(name)] ?: return null
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

    private fun weekdayIndex(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

    private companion object {
        const val KEY_STATS = "stats_json"
        const val KEY_PAIRS = "pairs_json"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_PARTNERS = 8
        const val MAX_BATCH = 30
        const val MAX_ITEMS = 800
    }
}
