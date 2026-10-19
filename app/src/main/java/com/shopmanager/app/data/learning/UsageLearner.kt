package com.shopmanager.app.data.learning

import android.content.Context
import com.shopmanager.app.data.materials.MaterialCatalogItem
import com.shopmanager.app.data.materials.MissingFinderStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class UsageArea(val key: String) {
    APP_OPEN("o"),
    MATERIALS("m"),
    NOTES("n"),
    MISSING_FINDER("f"),
    PRICES("p")
}

enum class SuggestionKind(val key: String) {
    DUE_MATERIALS("d"),
    USUAL_CHECK("c"),
    PRICES_REVIEW("r")
}

data class HomeSuggestion(
    val kind: SuggestionKind,
    val names: List<String> = emptyList()
)

/**
 * All access happens on [LearningHub.io] (single thread), so no locking is needed.
 * [UsageArea] intentionally has no debts entry: nothing about the debts section is
 * ever observed, stored or suggested.
 */
class UsageLearner(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("shop_usage_learner", Context.MODE_PRIVATE)

    private val counts = HashMap<UsageArea, FloatArray>()
    private val accepted = IntArray(SuggestionKind.values().size)
    private val dismissed = IntArray(SuggestionKind.values().size)
    private var lastKind: Int = -1
    private var loaded = false

    private val writer = DebouncedTask(900L) { persist() }

    fun preload() {
        if (loaded) return
        val raw = prefs.getString(KEY, null)
        if (raw != null) runCatching {
            val o = JSONObject(raw)
            val c = o.optJSONObject("c")
            UsageArea.values().forEach { a ->
                val arr = c?.optJSONArray(a.key) ?: return@forEach
                val f = FloatArray(SLOTS)
                for (i in 0 until minOf(SLOTS, arr.length())) f[i] = arr.optDouble(i, 0.0).toFloat()
                counts[a] = f
            }
            o.optJSONArray("a")?.let { for (i in accepted.indices) accepted[i] = it.optInt(i) }
            o.optJSONArray("x")?.let { for (i in dismissed.indices) dismissed[i] = it.optInt(i) }
            lastKind = o.optInt("l", -1)
        }
        loaded = true
    }

    fun record(area: UsageArea) {
        preload()
        val arr = counts.getOrPut(area) { FloatArray(SLOTS) }
        for (i in arr.indices) arr[i] *= DECAY
        arr[slotNow()] += 1f
        writer.trigger()
    }

    fun recordFeedback(kind: SuggestionKind, accept: Boolean) {
        preload()
        if (accept) accepted[kind.ordinal]++ else dismissed[kind.ordinal]++
        writer.trigger()
    }

    fun pickSuggestion(
        store: MissingFinderStore,
        catalog: List<MaterialCatalogItem>,
        currentShortages: Collection<String>
    ): HomeSuggestion? {
        preload()
        val slot = slotNow()
        data class Cand(val s: HomeSuggestion, val p: Double)
        val cands = ArrayList<Cand>(3)

        val due = store.dueNames(currentShortages, catalog, 3)
        if (due.isNotEmpty()) {
            cands += Cand(HomeSuggestion(SuggestionKind.DUE_MATERIALS, due), 3.0 + due.size * 0.3)
        }
        habit(UsageArea.MISSING_FINDER, slot)?.let {
            cands += Cand(HomeSuggestion(SuggestionKind.USUAL_CHECK), 2.0 + it)
        }
        habit(UsageArea.PRICES, slot)?.let {
            cands += Cand(HomeSuggestion(SuggestionKind.PRICES_REVIEW), 1.6 + it)
        }

        val best = cands
            .map { c ->
                val k = c.s.kind.ordinal
                var p = c.p * feedbackFactor(k)
                if (k == lastKind) p *= 0.6
                Cand(c.s, p)
            }
            .filter { it.p >= 0.8 }
            .maxByOrNull { it.p }
            ?.s

        if (best != null) {
            lastKind = best.kind.ordinal
            writer.trigger()
        }
        return best
    }

    fun flushNow() = writer.flushNow()

    private fun feedbackFactor(k: Int): Double {
        val a = accepted[k].toDouble()
        val d = dismissed[k].toDouble()
        return 2.0 * (a + 1.0) / (a + d + 2.0)
    }

    private fun habit(area: UsageArea, slot: Int): Double? {
        val arr = counts[area] ?: return null
        val total = arr.sum()
        if (total < 3f) return null
        val day = slot / PARTS
        val part = slot % PARTS
        var here = arr[slot]
        here += 0.5f * arr[((day + 6) % 7) * PARTS + part]
        here += 0.5f * arr[((day + 1) % 7) * PARTS + part]
        var peak = 0f
        for (i in arr.indices) if (arr[i] > peak) peak = arr[i]
        if (peak <= 0f) return null
        val strength = (here / peak).toDouble().coerceAtMost(1.0)
        if (strength < 0.5) return null
        val confidence = total / (total + 5.0)
        return strength * confidence
    }

    private fun slotNow(): Int {
        val c = Calendar.getInstance()
        val day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val h = c.get(Calendar.HOUR_OF_DAY)
        val part = when {
            h in 5..11 -> 0
            h in 12..16 -> 1
            h in 17..21 -> 2
            else -> 3
        }
        return day * PARTS + part
    }

    private fun persist() {
        val c = JSONObject()
        counts.forEach { (a, arr) ->
            val ja = JSONArray()
            arr.forEach { ja.put((Math.round(it * 100f) / 100f).toDouble()) }
            c.put(a.key, ja)
        }
        val o = JSONObject()
            .put("c", c)
            .put("a", JSONArray(accepted.toList()))
            .put("x", JSONArray(dismissed.toList()))
            .put("l", lastKind)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val KEY = "usage_json"
        const val PARTS = 4
        const val SLOTS = 7 * PARTS
        const val DECAY = 0.985f
    }
}
