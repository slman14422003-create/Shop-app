package com.shopmanager.app.assistant.voice

import android.content.Context

internal class VoiceSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shop_voice_settings", Context.MODE_PRIVATE)

    fun load(): VoiceSettings {
        val kind = runCatching { VoiceEngineKind.valueOf(prefs.getString("preferred", "AUTO") ?: "AUTO") }.getOrDefault(VoiceEngineKind.AUTO)
        return VoiceSettings(
            preferred = kind,
            allowMobileData = prefs.getBoolean("mobile", false),
            speakReplies = prefs.getBoolean("speak", true),
            autoStopMs = prefs.getLong("autostop", 1500L).coerceIn(600L, 5000L),
            minLevel = prefs.getFloat("minlevel", 0.015f).coerceIn(0.002f, 0.2f),
            autoSend = prefs.getBoolean("autosend", true)
        )
    }

    fun save(s: VoiceSettings) {
        prefs.edit()
            .putString("preferred", s.preferred.name)
            .putBoolean("mobile", s.allowMobileData)
            .putBoolean("speak", s.speakReplies)
            .putLong("autostop", s.autoStopMs)
            .putFloat("minlevel", s.minLevel)
            .putBoolean("autosend", s.autoSend)
            .apply()
    }
}
