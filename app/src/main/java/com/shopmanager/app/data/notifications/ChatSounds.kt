package com.shopmanager.app.data.notifications

import android.content.Context
import android.media.MediaPlayer
import com.shopmanager.app.R
import com.shopmanager.app.data.settings.SettingsRepository

object ChatSounds {

    private const val SEND_VOLUME = 0.32f

    private var sendPlayer: MediaPlayer? = null

    fun playSend(context: Context) {
        val app = context.applicationContext
        val repo = SettingsRepository(app)
        if (!repo.chatSoundsEnabled || !repo.chatSendSoundEnabled) return
        releaseSend()
        try {
            val mp = MediaPlayer()
            sendPlayer = mp
            mp.setAudioAttributes(NotificationSounds.AUDIO_ATTRS)
            app.resources.openRawResourceFd(R.raw.water_drop_crystal).use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mp.setVolume(SEND_VOLUME, SEND_VOLUME)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { if (sendPlayer === it) releaseSend() else it.release() }
            mp.setOnErrorListener { player, _, _ ->
                if (sendPlayer === player) releaseSend() else player.release()
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            releaseSend()
        }
    }

    fun playIncoming(context: Context) {
        val app = context.applicationContext
        val repo = SettingsRepository(app)
        if (!repo.chatSoundsEnabled || !repo.chatIncomingSoundEnabled) return
        NotificationSounds.playIncoming(app, NotifKind.CHAT)
    }

    fun releaseSend() {
        try { sendPlayer?.release() } catch (_: Exception) {}
        sendPlayer = null
    }
}
