package com.salman.herbalencyclopedia

import com.salman.herbalencyclopedia.data.search.HybridSearch
import com.salman.herbalencyclopedia.data.search.NameSuggester
import com.salman.herbalencyclopedia.data.search.SearchTuning
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SemoSearchRegressionTest {

    private val minAccuracy = 0.70

    @Before
    fun setUp() {
        SearchTuning.reset()
        HybridSearch.clearCache()
    }

    @Test
    fun expectedHerbAppearsInTopPoints() = runBlocking {
        val failures = ArrayList<String>()
        for (case in SemoTestData.cases) {
            val grounding = HybridSearch.rank(case.question, SemoTestData.herbs)
            val names = grounding.points.map { it.herbName }.distinct().take(3)
            if (case.expectedHerb !in names) failures += "${case.question} -> expected ${case.expectedHerb}, got $names"
        }
        val accuracy = 1.0 - failures.size.toDouble() / SemoTestData.cases.size
        println("accuracy=$accuracy failures=${failures.size}")
        failures.forEach { println(it) }
        assertTrue("accuracy $accuracy below $minAccuracy:\n" + failures.joinToString("\n"), accuracy >= minAccuracy)
    }

    @Test
    fun cacheKeepsAtMostFiftyEntries() = runBlocking {
        for (i in 1..70) HybridSearch.rank("ما فوائد الزنجبيل رقم $i", SemoTestData.herbs)
        assertTrue(HybridSearch.cacheSizeNow() <= SearchTuning.DEFAULT_CACHE_SIZE)
    }

    @Test
    fun suggestsNearestNamesForTypo() {
        val suggestions = NameSuggester.suggest("فوائد الزنجبل", SemoTestData.herbs, 3)
        assertTrue(suggestions.isNotEmpty())
        assertEquals("الزنجبيل", suggestions.first())
    }
}
