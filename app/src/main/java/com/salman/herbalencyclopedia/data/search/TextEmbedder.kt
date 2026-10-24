package com.salman.herbalencyclopedia.data.search

import kotlin.math.sqrt

interface TextEmbedder {
    val id: String
    val dimensions: Int
    val isReady: Boolean
    fun embed(texts: List<String>, isQuery: Boolean): List<FloatArray>?
}

object TextEmbedders {
    @Volatile
    var active: TextEmbedder? = null

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na <= 0.0 || nb <= 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}
