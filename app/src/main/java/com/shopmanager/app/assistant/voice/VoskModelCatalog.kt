package com.shopmanager.app.assistant.voice

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object VoskModelCatalog {
    private const val LIST_URL = "https://alphacephei.com/vosk/models/model-list.json"
    private const val NEGATIVE_TTL_MS = 7L * 24 * 3600 * 1000

    fun cached(prefs: SharedPreferences): VoskEntry? {
        val raw = prefs.getString("vosk_entry", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            VoskEntry(
                o.getString("name"), o.getString("lang"), o.getString("type"), o.getLong("size"),
                o.optString("size_text"), o.optString("md5"), o.getString("url"), false
            )
        }.getOrNull()
    }

    fun negativeFresh(prefs: SharedPreferences, now: Long): Boolean {
        val at = prefs.getLong("vosk_none_at", 0L)
        return at > 0 && now - at < NEGATIVE_TTL_MS
    }

    fun resolve(prefs: SharedPreferences, ramMb: Long): VoskEntry? {
        val entries = fetch() ?: return null
        val chosen = DownloadMath.chooseVoskModel(entries, ramMb)
        val e = prefs.edit()
        if (chosen == null) {
            e.remove("vosk_entry").putLong("vosk_none_at", System.currentTimeMillis())
        } else {
            val o = JSONObject()
                .put("name", chosen.name).put("lang", chosen.lang).put("type", chosen.type)
                .put("size", chosen.size).put("size_text", chosen.sizeText).put("md5", chosen.md5).put("url", chosen.url)
            e.putString("vosk_entry", o.toString()).remove("vosk_none_at")
        }
        e.apply()
        return chosen
    }

    private fun fetch(): List<VoskEntry>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(LIST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONArray(text)
            val out = ArrayList<VoskEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    VoskEntry(
                        name = o.optString("name"),
                        lang = o.optString("lang"),
                        type = o.optString("type"),
                        size = o.optLong("size", 0L),
                        sizeText = o.optString("size_text"),
                        md5 = o.optString("md5"),
                        url = o.optString("url"),
                        obsolete = o.optString("obsolete", "false").equals("true", true)
                    )
                )
            }
            out
        } catch (_: Throwable) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }
}
