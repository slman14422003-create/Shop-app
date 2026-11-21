package com.shopmanager.app.data.materials

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

@Immutable
data class PriceChange(val at: Long, val oldPrice: Double?, val newPrice: Double) {
    val delta: Double? get() = oldPrice?.let { newPrice - it }
}

object PriceHistoryStore {

    const val MAX_PER_MATERIAL = 10
    const val RECENT_WINDOW_MS = 7L * 86_400_000L

    private const val PREFS = "shop_price_history"
    private const val KEY = "history_v1"
    private const val NO_OLD = -1.0

    private val migrating = AtomicBoolean(false)

    fun isRecent(change: PriceChange?, now: Long): Boolean =
        change != null && change.oldPrice != null && change.oldPrice != change.newPrice &&
            now - change.at in 0..RECENT_WINDOW_MS

    /** Returns the new newest-first list, or null when nothing should be recorded. */
    fun next(existing: List<PriceChange>, oldPrice: Double?, newPrice: Double, at: Long): List<PriceChange>? {
        if (oldPrice == newPrice) return null
        if (existing.firstOrNull()?.newPrice == newPrice) return null
        return (listOf(PriceChange(at, oldPrice, newPrice)) + existing).take(MAX_PER_MATERIAL)
    }

    fun merge(a: List<PriceChange>, b: List<PriceChange>): List<PriceChange> =
        (a + b).distinct().sortedByDescending { it.at }.take(MAX_PER_MATERIAL)

    private fun readLegacy(context: Context): Map<String, List<PriceChange>>? {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val parsed = HashMap<String, List<PriceChange>>()
        val root = JSONObject(raw)
        val names = root.keys()
        while (names.hasNext()) {
            val name = names.next()
            val arr = root.optJSONArray(name) ?: continue
            val list = (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONArray(i) ?: return@mapNotNull null
                val old = e.optDouble(1, NO_OLD)
                PriceChange(e.optLong(0), if (old == NO_OLD) null else old, e.optDouble(2))
            }
            val key = name.trim()
            if (list.isNotEmpty() && key.isNotEmpty()) parsed[key] = merge(parsed[key].orEmpty(), list)
        }
        return parsed
    }

    /** One-time push of the old SharedPreferences history to Firestore; keeps the local copy on failure. */
    suspend fun migrateLegacy(context: Context, repo: MaterialsRepository) {
        if (!migrating.compareAndSet(false, true)) return
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY)) return
            val legacy = readLegacy(context)
            if (legacy == null) {
                prefs.edit().remove(KEY).apply()
                return
            }
            if (legacy.isNotEmpty()) repo.mergeLegacyHistory(legacy)
            prefs.edit().remove(KEY).apply()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("PriceHistoryStore", "legacy history migration failed, will retry", e)
        } finally {
            migrating.set(false)
        }
    }
}
