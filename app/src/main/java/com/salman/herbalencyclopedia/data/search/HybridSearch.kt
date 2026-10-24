package com.salman.herbalencyclopedia.data.search

import com.salman.herbalencyclopedia.data.ai.ArabicLexicon
import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.ai.SemanticIndex
import com.salman.herbalencyclopedia.data.model.Herb

data class GroundedPoint(
    val herbId: String,
    val herbName: String,
    val field: String,
    val text: String,
    val score: Double
)

data class SourceRef(val herbId: String, val herbName: String, val field: String)

data class Grounding(val points: List<GroundedPoint>, val bestScore: Double) {
    val isEmpty: Boolean get() = points.isEmpty()

    fun sources(): List<SourceRef> =
        points.map { SourceRef(it.herbId, it.herbName, it.field) }.distinct()

    companion object {
        val EMPTY = Grounding(emptyList(), 0.0)
    }
}

object HybridSearch {

    private const val SEMANTIC_LIMIT = 40
    private const val NAME_STRONG = 0.70
    private const val POINTS_PER_NAMED_HERB = 4
    private const val EMBED_CANDIDATES = 12
    private const val SEMANTIC_FLOOR = 0.20

    private class EngineInfo(val norm: Double, val isName: Boolean, val kind: MatchField?)

    private class Candidate(
        val herb: Herb,
        val field: String,
        val text: String,
        var semantic: Double
    )

    private val cacheLock = Any()
    private var cacheDataKey: Long = Long.MIN_VALUE
    private val cache = object : LinkedHashMap<String, Grounding>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Grounding>?): Boolean =
            size > SearchTuning.cacheSize
    }

    fun clearCache() {
        synchronized(cacheLock) { cache.clear() }
    }

    fun cacheSizeNow(): Int = synchronized(cacheLock) { cache.size }

    private fun dataKey(herbs: List<Herb>): Long {
        var k = herbs.size.toLong()
        for (h in herbs) {
            k = k * 31 + h.id.hashCode() + h.name.hashCode() + h.benefits.length + h.warnings.length +
                h.usage.length + h.harms.length + h.notes.length
        }
        return k
    }

    fun cacheKeyFor(question: String): String =
        HerbAssistant.dialectNormalize(question).split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { ArabicLexicon.stripAl(it) }

    suspend fun rank(
        question: String,
        herbs: List<Herb>,
        restrictTo: List<Herb>? = null,
        limit: Int = SearchTuning.maxPoints
    ): Grounding {
        val pool = restrictTo?.takeIf { it.isNotEmpty() } ?: herbs
        if (pool.isEmpty() || question.isBlank()) return Grounding.EMPTY
        val normalized = cacheKeyFor(question)
        if (normalized.isBlank()) return Grounding.EMPTY
        val dk = dataKey(herbs)
        val key = normalized + "|" + pool.joinToString(",") { it.id } + "|" + limit
        synchronized(cacheLock) {
            if (cacheDataKey != dk) {
                cache.clear()
                cacheDataKey = dk
            }
            cache[key]?.let { return it }
        }
        val result = compute(question, pool, limit)
        synchronized(cacheLock) {
            if (cacheDataKey == dk) cache[key] = result
        }
        return result
    }

    private suspend fun compute(question: String, pool: List<Herb>, limit: Int): Grounding {
        var engineHits = SearchEngine.searchHerbs(question, pool)
        if (engineHits.isEmpty()) {
            val alt = HerbAssistant.dialectNormalize(question)
            if (alt.isNotBlank() && alt != question) engineHits = SearchEngine.searchHerbs(alt, pool)
        }
        val engineByHerb = HashMap<String, EngineInfo>()
        for (hit in engineHits.take(40)) {
            val id = hit.item.id
            val info = EngineInfo(hit.score / 100.0, hit.field == null, hit.field)
            val old = engineByHerb[id]
            if (old == null || engineScore(info) > engineScore(old)) engineByHerb[id] = info
        }

        val intentFields = HerbAssistant.intentFields(question)
        val candidates = LinkedHashMap<String, Candidate>()

        val semantic = SemanticIndex.rank(question, pool, SEMANTIC_LIMIT)
        for (hit in semantic) {
            if (hit.score < SEMANTIC_FLOOR) continue
            val k = hit.herb.id + "\u0001" + hit.field + "\u0001" + hit.text
            val existing = candidates[k]
            if (existing == null) candidates[k] = Candidate(hit.herb, hit.field, hit.text, hit.score)
            else if (hit.score > existing.semantic) existing.semantic = hit.score
        }

        val targetFields = if (intentFields.isNotEmpty()) intentFields else setOf("الفوائد")
        for (herb in pool) {
            val info = engineByHerb[herb.id] ?: continue
            if (!info.isName || info.norm < NAME_STRONG) continue
            var added = 0
            for ((field, text) in HerbAssistant.searchPoints(herb)) {
                if (field !in targetFields) continue
                if (added >= POINTS_PER_NAMED_HERB) break
                val k = herb.id + "\u0001" + field + "\u0001" + text
                if (!candidates.containsKey(k)) candidates[k] = Candidate(herb, field, text, 0.0)
                added++
            }
        }

        if (candidates.isEmpty()) return Grounding.EMPTY

        val scored = ArrayList<GroundedPoint>(candidates.size)
        for (c in candidates.values) {
            val eng = engineByHerb[c.herb.id]?.let { engineScore(it) } ?: 0.0
            val fieldW = SearchTuning.weightForField(c.field)
            val fieldFactor = 0.5 + 0.5 * fieldW
            val intentMult = when {
                intentFields.isEmpty() -> 1.0
                c.field in intentFields -> SearchTuning.intentBoost
                else -> SearchTuning.offIntentFactor
            }
            val base = SearchTuning.engineMix * eng + SearchTuning.semanticMix * c.semantic * fieldFactor
            scored += GroundedPoint(c.herb.id, c.herb.name, c.field, c.text, base * intentMult)
        }
        var ordered = scored.sortedByDescending { it.score }

        val embedder = TextEmbedders.active
        if (embedder != null && embedder.isReady) {
            ordered = rerankWithEmbedder(embedder, question, ordered)
        }

        val kept = ordered.filter { it.score >= SearchTuning.minPointScore }.take(limit)
        if (kept.isEmpty()) return Grounding(emptyList(), ordered.firstOrNull()?.score ?: 0.0)
        return Grounding(kept, kept.first().score)
    }

    private fun engineScore(info: EngineInfo): Double =
        SearchTuning.weightForKind(if (info.isName) null else info.kind) * info.norm

    private fun rerankWithEmbedder(
        embedder: TextEmbedder,
        question: String,
        ordered: List<GroundedPoint>
    ): List<GroundedPoint> {
        return try {
            val head = ordered.take(EMBED_CANDIDATES)
            val tail = ordered.drop(EMBED_CANDIDATES)
            val q = embedder.embed(listOf(question), true)?.firstOrNull() ?: return ordered
            val docs = embedder.embed(head.map { it.text }, false) ?: return ordered
            if (docs.size != head.size) return ordered
            val boosted = head.mapIndexed { i, p ->
                val cos = TextEmbedders.cosine(q, docs[i]).coerceAtLeast(0.0)
                p.copy(score = p.score + SearchTuning.embeddingMix * cos)
            }
            boosted.sortedByDescending { it.score } + tail
        } catch (e: Exception) {
            ordered
        }
    }
}
