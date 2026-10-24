package com.salman.herbalencyclopedia.data.search

object SearchTuning {
    const val DEFAULT_NAME_WEIGHT = 1.0
    const val DEFAULT_BENEFITS_WEIGHT = 0.8
    const val DEFAULT_USAGE_WEIGHT = 0.6
    const val DEFAULT_WARNINGS_WEIGHT = 0.6
    const val DEFAULT_HARMS_WEIGHT = 0.55
    const val DEFAULT_NOTES_WEIGHT = 0.4
    const val DEFAULT_CATEGORY_WEIGHT = 0.3
    const val DEFAULT_ENGINE_MIX = 0.5
    const val DEFAULT_SEMANTIC_MIX = 0.6
    const val DEFAULT_EMBEDDING_MIX = 0.35
    const val DEFAULT_INTENT_BOOST = 1.6
    const val DEFAULT_OFF_INTENT_FACTOR = 0.55
    const val DEFAULT_MIN_POINT_SCORE = 0.12
    const val DEFAULT_ONLINE_EXPAND_THRESHOLD = 0.20
    const val DEFAULT_MAX_POINTS = 5
    const val DEFAULT_MIN_POINTS = 3
    const val DEFAULT_CACHE_SIZE = 50

    @Volatile var nameWeight = DEFAULT_NAME_WEIGHT
    @Volatile var benefitsWeight = DEFAULT_BENEFITS_WEIGHT
    @Volatile var usageWeight = DEFAULT_USAGE_WEIGHT
    @Volatile var warningsWeight = DEFAULT_WARNINGS_WEIGHT
    @Volatile var harmsWeight = DEFAULT_HARMS_WEIGHT
    @Volatile var notesWeight = DEFAULT_NOTES_WEIGHT
    @Volatile var categoryWeight = DEFAULT_CATEGORY_WEIGHT
    @Volatile var engineMix = DEFAULT_ENGINE_MIX
    @Volatile var semanticMix = DEFAULT_SEMANTIC_MIX
    @Volatile var embeddingMix = DEFAULT_EMBEDDING_MIX
    @Volatile var intentBoost = DEFAULT_INTENT_BOOST
    @Volatile var offIntentFactor = DEFAULT_OFF_INTENT_FACTOR
    @Volatile var minPointScore = DEFAULT_MIN_POINT_SCORE
    @Volatile var onlineExpandThreshold = DEFAULT_ONLINE_EXPAND_THRESHOLD
    @Volatile var maxPoints = DEFAULT_MAX_POINTS
    @Volatile var cacheSize = DEFAULT_CACHE_SIZE

    fun weightForField(label: String): Double = when (label) {
        "الاسم" -> nameWeight
        "الفوائد" -> benefitsWeight
        "الاستخدام" -> usageWeight
        "التحذيرات" -> warningsWeight
        "الأضرار" -> harmsWeight
        "ملاحظات" -> notesWeight
        else -> categoryWeight
    }

    fun weightForKind(kind: MatchField?): Double = when (kind) {
        null -> nameWeight
        MatchField.BENEFITS -> benefitsWeight
        MatchField.USAGE -> usageWeight
        MatchField.WARNINGS -> warningsWeight
        MatchField.HARMS -> harmsWeight
        MatchField.NOTES -> notesWeight
        MatchField.CATEGORY, MatchField.INGREDIENTS -> categoryWeight
    }

    fun reset() {
        nameWeight = DEFAULT_NAME_WEIGHT
        benefitsWeight = DEFAULT_BENEFITS_WEIGHT
        usageWeight = DEFAULT_USAGE_WEIGHT
        warningsWeight = DEFAULT_WARNINGS_WEIGHT
        harmsWeight = DEFAULT_HARMS_WEIGHT
        notesWeight = DEFAULT_NOTES_WEIGHT
        categoryWeight = DEFAULT_CATEGORY_WEIGHT
        engineMix = DEFAULT_ENGINE_MIX
        semanticMix = DEFAULT_SEMANTIC_MIX
        embeddingMix = DEFAULT_EMBEDDING_MIX
        intentBoost = DEFAULT_INTENT_BOOST
        offIntentFactor = DEFAULT_OFF_INTENT_FACTOR
        minPointScore = DEFAULT_MIN_POINT_SCORE
        onlineExpandThreshold = DEFAULT_ONLINE_EXPAND_THRESHOLD
        maxPoints = DEFAULT_MAX_POINTS
        cacheSize = DEFAULT_CACHE_SIZE
    }
}
