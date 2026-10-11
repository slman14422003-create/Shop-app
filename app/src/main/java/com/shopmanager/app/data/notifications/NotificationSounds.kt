package com.shopmanager.app.data.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.shopmanager.app.R
import com.shopmanager.app.data.settings.SettingsRepository

/** أنواع الإشعارات التي يمكن اختيار صوت مستقل لكل منها من الإعدادات. */
enum class NotifKind(
    val idPrefix: String,
    val prefKey: String,
    val label: String,
    val defaultSound: NotifSound
) {
    SHOPPING("low_stock_channel_s_", "shopping", "قائمة النواقص والمشتريات", NotifSound.DROP_SOFT),
    DEBTS("debts_channel_s_", "debts", "تنبيهات الديون", NotifSound.DROP_DOUBLE),
    NOTES("notes_channel_s_", "notes", "تذكيرات الملاحظات الهامة", NotifSound.DROP_CRYSTAL)
}

/** الأصوات المتاحة: ثلاث "قطرات ماء" خاصة بالتطبيق + صوت النظام + صامت. */
enum class NotifSound(val key: String, val label: String, val hint: String, val rawRes: Int?) {
    DROP_SOFT("drop_soft", "قطرة ناعمة", "قطرة واحدة هادئة مع تموّج خفيف", R.raw.water_drop_soft),
    DROP_DOUBLE("drop_double", "قطرتان", "«بلِب–بلوب» سريعة التمييز", R.raw.water_drop_double),
    DROP_CRYSTAL("drop_crystal", "قطرات كريستال", "ثلاث قطرات لامعة تنتهي بقطرة أعمق", R.raw.water_drop_crystal),
    SYSTEM("system", "صوت النظام الافتراضي", "نغمة الإشعارات الافتراضية للجهاز", null),
    SILENT("silent", "بدون صوت", "يظهر الإشعار بصمت (مع الاهتزاز إن وُجد)", null);

    companion object {
        fun fromKey(key: String?): NotifSound? = values().firstOrNull { it.key == key }
    }
}

object NotificationSounds {

    val AUDIO_ATTRS: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * صوت قناة الإشعار لا يتغيّر بعد إنشائها (قيد من أندرويد)، لذلك يُضمَّن مفتاح الصوت
     * في معرّف القناة نفسه: تغيير الصوت من الإعدادات = قناة جديدة بالصوت الجديد.
     */
    fun channelId(context: Context, kind: NotifKind): String =
        kind.idPrefix + SettingsRepository(context.applicationContext).soundFor(kind).key

    fun soundUri(context: Context, sound: NotifSound): Uri? = when {
        sound.rawRes != null -> Uri.parse("android.resource://${context.packageName}/${sound.rawRes}")
        sound == NotifSound.SYSTEM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> null
    }

    private var player: MediaPlayer? = null

    /** تشغيل معاينة الصوت داخل الإعدادات. */
    fun preview(context: Context, sound: NotifSound) {
        stopPreview()
        try {
            when {
                sound.rawRes != null -> {
                    player = MediaPlayer().apply {
                        setAudioAttributes(AUDIO_ATTRS)
                        context.resources.openRawResourceFd(sound.rawRes).use {
                            setDataSource(it.fileDescriptor, it.startOffset, it.length)
                        }
                        setOnCompletionListener { stopPreview() }
                        prepare()
                        start()
                    }
                }
                sound == NotifSound.SYSTEM -> {
                    val r = RingtoneManager.getRingtone(
                        context.applicationContext,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    )
                    r?.setAudioAttributes(AUDIO_ATTRS)
                    r?.play()
                }
                else -> Unit
            }
        } catch (_: Exception) {
            stopPreview()
        }
    }

    fun stopPreview() {
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
