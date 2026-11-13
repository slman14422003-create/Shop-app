package com.shopmanager.app.assistant.voice

data class DeviceProfile(
    val ramMb: Long,
    val cores: Int,
    val sdk: Int,
    val is64bit: Boolean,
    val onDeviceRecognizerAvailable: Boolean,
    val localeSupported: Boolean
)

enum class DeviceClass { LOW, MID, HIGH }

object EngineSelector {
    val SUPPORTED_LOCALES = listOf("ar-SY", "ar-LB", "ar-JO")

    fun deviceClass(p: DeviceProfile): DeviceClass = when {
        p.ramMb < 3000 || p.cores <= 4 || !p.is64bit -> DeviceClass.LOW
        p.ramMb >= 6000 && p.cores >= 8 -> DeviceClass.HIGH
        else -> DeviceClass.MID
    }

    fun systemEligible(p: DeviceProfile): Boolean =
        p.sdk >= 31 && p.onDeviceRecognizerAvailable && p.localeSupported

    fun order(
        profile: DeviceProfile,
        preferred: VoiceEngineKind,
        voskUsable: Boolean,
        whisperUsable: Boolean
    ): List<VoiceEngineKind> {
        val sys = systemEligible(profile)
        fun usable(k: VoiceEngineKind) = when (k) {
            VoiceEngineKind.SYSTEM -> sys
            VoiceEngineKind.VOSK -> voskUsable
            VoiceEngineKind.WHISPER -> whisperUsable
            VoiceEngineKind.AUTO -> false
        }
        val defaultOrder = listOf(VoiceEngineKind.SYSTEM, VoiceEngineKind.VOSK, VoiceEngineKind.WHISPER)
        val result = ArrayList<VoiceEngineKind>()
        if (preferred != VoiceEngineKind.AUTO) {
            if (usable(preferred)) result.add(preferred)
            for (k in defaultOrder) if (k != preferred && usable(k)) result.add(k)
            return result
        }
        if (sys) result.add(VoiceEngineKind.SYSTEM)
        val rest = if (deviceClass(profile) == DeviceClass.LOW) {
            listOf(VoiceEngineKind.VOSK, VoiceEngineKind.WHISPER)
        } else {
            listOf(VoiceEngineKind.WHISPER, VoiceEngineKind.VOSK)
        }
        for (k in rest) if (usable(k)) result.add(k)
        return result
    }
}
