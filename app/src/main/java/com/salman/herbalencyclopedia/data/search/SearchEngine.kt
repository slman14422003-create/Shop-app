package com.salman.herbalencyclopedia.data.search

import com.salman.herbalencyclopedia.data.ai.ArabicLexicon
import com.salman.herbalencyclopedia.data.ai.DictionaryLexicon
import com.salman.herbalencyclopedia.data.ai.HerbCategoryLookup
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

enum class MatchTier { MATCH, SUGGESTION }

enum class MatchField { BENEFITS, USAGE, WARNINGS, HARMS, NOTES, CATEGORY, INGREDIENTS }

data class SearchHit<T>(
    val item: T,
    val tier: MatchTier,
    val score: Int,
    val field: MatchField? = null,
    val snippet: String? = null
)

data class SearchResults(
    val herbs: List<SearchHit<Herb>> = emptyList(),
    val blends: List<SearchHit<Blend>> = emptyList(),
    val categories: List<SearchHit<Category>> = emptyList()
) {
    val isEmpty: Boolean get() = herbs.isEmpty() && blends.isEmpty() && categories.isEmpty()

    companion object {
        val Empty = SearchResults()
    }
}

object SearchEngine {

    private const val MAX_SUGGESTIONS = 30
    private const val SNIPPET_MAX_CHARS = 90
    private const val SNIPPET_WORDS_BEFORE = 3
    private const val SNIPPET_WORDS_AFTER = 12
    private const val CANCEL_CHECK_MASK = 63
    private const val ELLIPSIS = "\u2026"

    private val SPACES = Regex("\\s+")

    private val STOP_WORDS: Set<String> = setOf(
        "من", "في", "على", "عن", "الى", "مع", "هل", "ما", "ماذا", "كيف", "هذا", "هذه", "ذلك",
        "التي", "الذي", "هو", "هي", "لا", "لم", "لن", "ان", "او", "ثم", "قد", "كل", "بعض",
        "فوائد", "فايده", "فائده", "استخدام", "طريقه", "ابي", "اريد", "ابغى", "عندي", "اعطني"
    )

    fun normalize(text: String): String = ArabicLexicon.normalizeText(text)

    private fun core(text: String): String {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return normalized
        return normalized.split(SPACES)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { ArabicLexicon.stripAl(it) }
    }

    private fun tokensOf(core: String): List<String> =
        if (core.isEmpty()) emptyList() else core.split(SPACES).filter { it.length > 1 }

    private fun stemsOf(token: String): Set<String> = ArabicLexicon.formsOf(token)

    private fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 3 || b.length < 3) return false
        val stemsA = stemsOf(a)
        if (b in stemsA) return true
        val stemsB = stemsOf(b)
        if (a in stemsB) return true
        return stemsA.any { it in stemsB }
    }

    private class FieldIndex(
        val kind: MatchField,
        val raw: String,
        val core: String,
        val stems: Set<String>
    )

    private class DocIndex(
        val nameCore: String,
        val nameTokens: List<String>,
        val fields: List<FieldIndex>,
        val allStems: Set<String>
    )

    private class IndexCache(val source: Any, val extra: Any?, val docs: List<DocIndex>)

    @Volatile private var herbCache: IndexCache? = null
    @Volatile private var blendCache: IndexCache? = null
    @Volatile private var categoryCache: IndexCache? = null

    private fun fieldIndex(kind: MatchField, raw: String): FieldIndex? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val fieldCore = core(trimmed)
        if (fieldCore.isEmpty()) return null
        val stems = HashSet<String>()
        for (token in tokensOf(fieldCore)) stems.addAll(stemsOf(token))
        return FieldIndex(kind, trimmed, fieldCore, stems)
    }

    private fun docIndex(name: String, fields: List<FieldIndex>): DocIndex {
        val nameCore = core(name)
        val stems = HashSet<String>()
        for (field in fields) stems.addAll(field.stems)
        return DocIndex(nameCore, tokensOf(nameCore), fields, stems)
    }

    private suspend fun herbIndex(herbs: List<Herb>): List<DocIndex> {
        val categoryNames = HerbCategoryLookup.categoryNames
        herbCache?.let { if (it.source === herbs && it.extra === categoryNames) return it.docs }
        val docs = ArrayList<DocIndex>(herbs.size)
        for ((i, herb) in herbs.withIndex()) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val categoryName = herb.categoryId?.let { categoryNames[it] }.orEmpty()
            docs += docIndex(
                herb.name,
                listOfNotNull(
                    fieldIndex(MatchField.BENEFITS, herb.benefits),
                    fieldIndex(MatchField.USAGE, herb.usage),
                    fieldIndex(MatchField.WARNINGS, herb.warnings),
                    fieldIndex(MatchField.HARMS, herb.harms),
                    fieldIndex(MatchField.NOTES, herb.notes),
                    fieldIndex(MatchField.CATEGORY, categoryName)
                )
            )
        }
        herbCache = IndexCache(herbs, categoryNames, docs)
        return docs
    }

    private suspend fun blendIndex(blends: List<Blend>, herbs: List<Herb>): List<DocIndex> {
        blendCache?.let { if (it.source === blends && it.extra === herbs) return it.docs }
        val herbNames = HashMap<String, String>(herbs.size * 2)
        for (herb in herbs) herbNames[herb.id] = herb.name
        val docs = ArrayList<DocIndex>(blends.size)
        for ((i, blend) in blends.withIndex()) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val ingredients = blend.herbIds.mapNotNull { herbNames[it] }.joinToString(" ")
            docs += docIndex(
                blend.name,
                listOfNotNull(
                    fieldIndex(MatchField.INGREDIENTS, ingredients),
                    fieldIndex(MatchField.BENEFITS, blend.benefits),
                    fieldIndex(MatchField.USAGE, blend.usage),
                    fieldIndex(MatchField.WARNINGS, blend.warnings),
                    fieldIndex(MatchField.NOTES, blend.notes)
                )
            )
        }
        blendCache = IndexCache(blends, herbs, docs)
        return docs
    }

    private suspend fun categoryIndex(categories: List<Category>): List<DocIndex> {
        categoryCache?.let { if (it.source === categories) return it.docs }
        val docs = ArrayList<DocIndex>(categories.size)
        for ((i, category) in categories.withIndex()) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            docs += docIndex(category.name, emptyList())
        }
        categoryCache = IndexCache(categories, null, docs)
        return docs
    }

    private val aliasGroups: List<List<String>> by lazy {
        ArabicLexicon.herbAliasGroups.map { group -> group.map { core(it) }.filter { it.isNotBlank() } }
    }

    private class Query(
        val core: String,
        val tokens: List<String>,
        val contentTokens: List<String>,
        val contentStems: List<Set<String>>,
        val aliasCores: List<String>,
        val synonyms: List<String>
    )

    private fun aliasCoresFor(queryCore: String, queryTokens: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (group in aliasGroups) {
            val mentioned = group.any { member ->
                val memberTokens = tokensOf(member)
                when {
                    member == queryCore -> true
                    memberTokens.size == 1 -> queryTokens.any { sameWord(memberTokens[0], it) }
                    memberTokens.size > 1 -> memberTokens.all { m -> queryTokens.any { sameWord(m, it) } }
                    else -> false
                }
            }
            if (mentioned) for (member in group) if (member != queryCore) out.add(member)
        }
        return out.toList()
    }

    private fun synonymsFor(tokens: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (token in tokens) {
            if (token.length < 3) continue
            var found = DictionaryLexicon.synonymsOf(token)
            if (found.isEmpty()) {
                for (stem in stemsOf(token)) {
                    if (stem == token || stem.length < 3) continue
                    found = DictionaryLexicon.synonymsOf(stem)
                    if (found.isNotEmpty()) break
                }
            }
            for (synonym in found) {
                val synonymCore = core(synonym)
                if (synonymCore.length >= 3) out.add(synonymCore)
            }
        }
        return out.toList()
    }

    private fun prepare(raw: String): Query? {
        val queryCore = core(raw)
        if (queryCore.isBlank()) return null
        val tokens = tokensOf(queryCore)
        if (tokens.isEmpty()) return null
        val content = tokens.filter { it.length >= 3 && it !in STOP_WORDS }.ifEmpty { tokens }
        return Query(
            core = queryCore,
            tokens = tokens,
            contentTokens = content,
            contentStems = content.map { stemsOf(it) },
            aliasCores = aliasCoresFor(queryCore, tokens),
            synonyms = synonymsFor(content)
        )
    }

    private class NameScore(val score: Int, val tier: MatchTier)

    private fun scoreName(doc: DocIndex, q: Query): NameScore? {
        val nameCore = doc.nameCore
        if (nameCore.isEmpty()) return null
        val nameTokens = doc.nameTokens
        if (nameCore == q.core) return NameScore(100, MatchTier.MATCH)
        if (nameTokens.isNotEmpty()) {
            if (q.tokens.all { qt -> nameTokens.any { sameWord(qt, it) } }) return NameScore(92, MatchTier.MATCH)
            if (nameCore.length >= 3 && nameTokens.all { nt -> q.tokens.any { sameWord(nt, it) } }) {
                return NameScore(84, MatchTier.MATCH)
            }
        }
        for (alias in q.aliasCores) {
            if (nameCore == alias) return NameScore(80, MatchTier.MATCH)
            val aliasTokens = tokensOf(alias)
            if (aliasTokens.isNotEmpty() && nameTokens.isNotEmpty() &&
                aliasTokens.all { at -> nameTokens.any { sameWord(at, it) } }
            ) {
                return NameScore(78, MatchTier.MATCH)
            }
        }
        if (q.tokens.size == 1) {
            val qt = q.tokens[0]
            if (nameTokens.any { it.startsWith(qt) }) {
                return NameScore(70 + minOf(qt.length, 6), MatchTier.MATCH)
            }
        } else {
            val head = q.tokens.dropLast(1)
            val last = q.tokens.last()
            if (nameCore.startsWith(q.core) ||
                (head.all { h -> nameTokens.any { sameWord(h, it) } } && nameTokens.any { it.startsWith(last) })
            ) {
                return NameScore(72, MatchTier.MATCH)
            }
        }
        if (q.tokens.size == 1 && q.core.length >= 3) {
            val qt = q.tokens[0]
            val limit = if (qt.length >= 7) 2 else 1
            val typo = nameTokens.any { nt ->
                nt.length >= 3 && kotlin.math.abs(nt.length - qt.length) <= limit &&
                    ArabicLexicon.editDistanceAtMost(nt, qt, limit) <= limit
            }
            if (typo) return NameScore(55, MatchTier.SUGGESTION)
        }
        if (q.contentTokens.any { qt -> qt.length >= 3 && nameTokens.any { nt -> nt.length > qt.length && nt.contains(qt) } }) {
            return NameScore(52, MatchTier.SUGGESTION)
        }
        val overlap = q.contentTokens.count { qt -> nameTokens.any { sameWord(qt, it) } }
        if (overlap > 0) return NameScore(40 + minOf(overlap * 4, 12), MatchTier.SUGGESTION)
        if (q.synonyms.isNotEmpty() &&
            q.synonyms.any { s -> nameCore == s || nameTokens.any { nt -> sameWord(nt, s) } }
        ) {
            return NameScore(46, MatchTier.SUGGESTION)
        }
        return null
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        phrase.isNotEmpty() && " $text ".contains(" $phrase ")

    private fun fieldMatches(field: FieldIndex, q: Query): Boolean {
        if (q.core.length >= 3 && containsPhrase(field.core, q.core)) return true
        if (q.contentStems.any { stems -> stems.any { it in field.stems } }) return true
        return q.synonyms.any { s -> tokensOf(s).let { ts -> ts.isNotEmpty() && ts.all { t -> stemsOf(t).any { it in field.stems } } } }
    }

    private class FieldScore(val score: Int, val field: FieldIndex)

    private fun scoreFields(doc: DocIndex, q: Query): FieldScore? {
        if (doc.fields.isEmpty()) return null
        if (q.core.length >= 3) {
            val phraseField = doc.fields.firstOrNull { containsPhrase(it.core, q.core) }
            if (phraseField != null) return FieldScore(34, phraseField)
        }
        val matched = q.contentStems.count { stems -> stems.any { it in doc.allStems } }
        val ratio = matched.toDouble() / q.contentStems.size.coerceAtLeast(1)
        val enough = matched > 0 && (q.contentStems.size == 1 || ratio >= 0.5)
        if (enough) {
            val field = doc.fields.firstOrNull { f -> q.contentStems.any { stems -> stems.any { it in f.stems } } }
                ?: return null
            return FieldScore(12 + (ratio * 18).toInt(), field)
        }
        val synonymField = doc.fields.firstOrNull { fieldMatches(it, q) }
        return synonymField?.let { FieldScore(10, it) }
    }

    private fun makeSnippet(raw: String, q: Query): String {
        val words = raw.trim().split(SPACES).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        var index = -1
        for ((i, word) in words.withIndex()) {
            val wordCore = core(word)
            if (wordCore.isEmpty()) continue
            if (q.contentTokens.any { sameWord(it, wordCore) } || q.synonyms.any { sameWord(it, wordCore) }) {
                index = i
                break
            }
        }
        if (index < 0) index = 0
        val from = (index - SNIPPET_WORDS_BEFORE).coerceAtLeast(0)
        val to = (index + SNIPPET_WORDS_AFTER).coerceAtMost(words.size)
        var text = words.subList(from, to).joinToString(" ")
        if (text.length > SNIPPET_MAX_CHARS) text = text.take(SNIPPET_MAX_CHARS).trimEnd() + ELLIPSIS
        else if (to < words.size) text += ELLIPSIS
        if (from > 0) text = ELLIPSIS + text
        return text
    }

    private suspend fun <T> collect(items: List<T>, docs: List<DocIndex>, q: Query): List<SearchHit<T>> {
        val matches = ArrayList<SearchHit<T>>()
        val suggestions = ArrayList<SearchHit<T>>()
        val count = minOf(items.size, docs.size)
        for (i in 0 until count) {
            if ((i and CANCEL_CHECK_MASK) == 0) coroutineContext.ensureActive()
            val doc = docs[i]
            val nameScore = scoreName(doc, q)
            if (nameScore != null) {
                val hit = SearchHit(items[i], nameScore.tier, nameScore.score)
                if (nameScore.tier == MatchTier.MATCH) matches += hit else suggestions += hit
                continue
            }
            val fieldScore = scoreFields(doc, q) ?: continue
            suggestions += SearchHit(
                item = items[i],
                tier = MatchTier.SUGGESTION,
                score = fieldScore.score,
                field = fieldScore.field.kind,
                snippet = makeSnippet(fieldScore.field.raw, q)
            )
        }
        matches.sortByDescending { it.score }
        suggestions.sortByDescending { it.score }
        return matches + suggestions.take(MAX_SUGGESTIONS)
    }

    suspend fun searchAll(
        query: String,
        herbs: List<Herb>,
        blends: List<Blend>,
        categories: List<Category>
    ): SearchResults {
        val q = prepare(query) ?: return SearchResults.Empty
        val herbHits = collect(herbs, herbIndex(herbs), q)
        val blendHits = collect(blends, blendIndex(blends, herbs), q)
        val categoryHits = collect(categories, categoryIndex(categories), q)
        return SearchResults(herbHits, blendHits, categoryHits)
    }

    suspend fun searchHerbs(query: String, herbs: List<Herb>): List<SearchHit<Herb>> {
        val q = prepare(query) ?: return emptyList()
        return collect(herbs, herbIndex(herbs), q)
    }
}
