package com.salman.herbalencyclopedia.data.ai.local

import android.content.Context
import android.content.SharedPreferences

class LocalLlmStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("local_llm_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    var modelId: String
        get() = prefs.getString(KEY_MODEL, LocalLlmModels.default.id) ?: LocalLlmModels.default.id
        set(value) { prefs.edit().putString(KEY_MODEL, value).apply() }

    var hfToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_TOKEN, value.trim()).apply() }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_MODEL = "model_id"
        const val KEY_TOKEN = "hf_token"
    }
}
