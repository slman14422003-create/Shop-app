package com.shopmanager.app.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * تخزين المساعد المحلي (SharedPreferences + JSON، نفس أسلوب ChangeHistory): سجل المحادثة (آخر 200 رسالة)،
 * الأسماء البديلة المتعلَّمة، سجل ما نفّذه المساعد، والإعدادات. لا Firestore ولا إنترنت.
 */
class AssistantStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("shop_manager_assistant", Context.MODE_PRIVATE)

    // ---------------- إعدادات
    fun loadSettings() = AssistantSettings(
        proactiveSuggestions = prefs.getBoolean(K_PROACTIVE, true),
        alwaysConfirm = prefs.getBoolean(K_CONFIRM, true),
        robotAnimation = prefs.getBoolean(K_ANIM, true),
        newHome = prefs.getBoolean(K_HOME, true)
    )

    fun saveSettings(s: AssistantSettings) {
        prefs.edit()
            .putBoolean(K_PROACTIVE, s.proactiveSuggestions)
            .putBoolean(K_CONFIRM, s.alwaysConfirm)
            .putBoolean(K_ANIM, s.robotAnimation)
            .putBoolean(K_HOME, s.newHome)
            .apply()
    }

    // ---------------- سجل المحادثة
    @Synchronized
    fun loadHistory(): List<AssistantMessage> = runCatching {
        val arr = JSONArray(prefs.getString(K_HISTORY, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val card: AssistantCard? = when (o.optString("ct")) {
                "answer" -> AssistantCard.Answer(
                    o.optString("title"),
                    rowsOf(o.optJSONArray("rows")),
                    o.optString("note").ifBlank { null }
                )
                "result" -> AssistantCard.Result(o.optString("title"), o.optString("details"), null, o.optBoolean("ok", true))
                else -> null
            }
            AssistantMessage(o.getLong("id"), o.getBoolean("u"), o.optString("t"), o.optLong("time"), card)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun saveHistory(list: List<AssistantMessage>) {
        val arr = JSONArray()
        for (m in list.takeLast(MAX_HISTORY)) {
            val o = JSONObject().put("id", m.id).put("u", m.fromUser).put("t", m.text).put("time", m.time)
            when (val c = m.card) {
                is AssistantCard.Answer -> {
                    o.put("ct", "answer").put("title", c.title).put("note", c.note ?: "")
                    o.put("rows", rowsJson(c.rows))
                }
                is AssistantCard.Result -> o.put("ct", "result").put("title", c.title).put("details", c.details).put("ok", c.success)
                else -> {}
            }
            arr.put(o)
        }
        prefs.edit().putString(K_HISTORY, arr.toString()).apply()
    }

    fun clearHistory() { prefs.edit().remove(K_HISTORY).apply() }

    private fun rowsOf(a: JSONArray?): List<CardField> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { CardField(a.getJSONArray(it).getString(0), a.getJSONArray(it).getString(1)) }
    }

    private fun rowsJson(rows: List<CardField>): JSONArray {
        val a = JSONArray()
        rows.take(30).forEach { a.put(JSONArray().put(it.label).put(it.value)) }
        return a
    }

    // ---------------- الأسماء البديلة
    @Synchronized
    fun loadAliases(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(K_ALIASES, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun addAlias(alias: String, canonical: String) {
        val a = alias.trim()
        if (a.isEmpty() || canonical.isBlank()) return
        val m = loadAliases().toMutableMap()
        m[a] = canonical
        saveAliases(m)
    }

    @Synchronized
    fun removeAlias(alias: String) {
        val m = loadAliases().toMutableMap()
        m.remove(alias)
        saveAliases(m)
    }

    @Synchronized
    fun clearAliases() { prefs.edit().remove(K_ALIASES).apply() }

    private fun saveAliases(m: Map<String, String>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(K_ALIASES, o.toString()).apply()
    }

    // ---------------- سجل ما نفّذه المساعد
    @Synchronized
    fun loadActions(): List<ActionLogEntry> = runCatching {
        val arr = JSONArray(prefs.getString(K_ACTIONS, "[]") ?: "[]")
        (0 until arr.length()).map { val o = arr.getJSONObject(it); ActionLogEntry(o.getLong("id"), o.getString("t"), o.getLong("time")) }
    }.getOrDefault(emptyList())

    @Synchronized
    fun saveActions(list: List<ActionLogEntry>) {
        val arr = JSONArray()
        list.takeLast(MAX_ACTIONS).forEach { arr.put(JSONObject().put("id", it.id).put("t", it.text).put("time", it.time)) }
        prefs.edit().putString(K_ACTIONS, arr.toString()).apply()
    }

    companion object {
        const val MAX_HISTORY = 200
        const val MAX_ACTIONS = 30
        private const val K_PROACTIVE = "proactive"
        private const val K_CONFIRM = "always_confirm"
        private const val K_ANIM = "robot_anim"
        private const val K_HOME = "new_home"
        private const val K_HISTORY = "history"
        private const val K_ALIASES = "aliases"
        private const val K_ACTIONS = "actions"
    }
}
