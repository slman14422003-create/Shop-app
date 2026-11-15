package com.shopmanager.app.data.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VoicePlayState(
    val id: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val speed: Float = 1f
)

/**
 * مشغّل واحد مشترك: رسالة صوتية واحدة تعمل في كل مرة (تشغيل رسالة جديدة يوقف السابقة).
 * الملف يُفك من Base64 إلى مجلد الكاش المؤقت ثم يُشغَّل بـ MediaPlayer.
 */
object VoicePlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var tick: Job? = null
    private var loadingId: String? = null
    private var speed = 1f

    private val _state = MutableStateFlow(VoicePlayState())
    val state: StateFlow<VoicePlayState> = _state

    fun toggle(context: Context, id: String, audioB64: String) {
        val cur = _state.value
        val p = player
        if (cur.id == id && p != null) {
            if (cur.playing) {
                runCatching { p.pause() }
                _state.value = cur.copy(playing = false)
            } else {
                runCatching { p.start() }
                applySpeed(p)
                _state.value = cur.copy(playing = true)
                startTick()
            }
            return
        }
        if (loadingId == id) return
        stop()
        loadingId = id
        val app = context.applicationContext
        scope.launch {
            val file = withContext(Dispatchers.IO) { writeTemp(app, id, audioB64) }
            if (loadingId != id) return@launch // المستخدم بدّل لرسالة أخرى أثناء التحميل
            loadingId = null
            if (file == null) return@launch
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { stop() }
                mp.setOnErrorListener { _, _, _ -> stop(); true }
                mp.prepare()
                mp.start()
                applySpeed(mp)
            } catch (_: Exception) {
                runCatching { mp.release() }
                return@launch
            }
            player = mp
            _state.value = VoicePlayState(id, true, 0, mp.duration, speed)
            startTick()
        }
    }

    fun cycleSpeed() {
        speed = PlaybackSpeeds.next(speed)
        val p = player
        if (p != null && _state.value.playing) applySpeed(p)
        _state.value = _state.value.copy(speed = speed)
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    fun stop() {
        tick?.cancel()
        tick = null
        loadingId = null
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        _state.value = VoicePlayState(speed = speed)
    }

    private fun startTick() {
        tick?.cancel()
        tick = scope.launch {
            while (isActive) {
                val p = player ?: break
                val pos = runCatching { p.currentPosition }.getOrDefault(0)
                _state.value = _state.value.copy(positionMs = pos)
                delay(80)
            }
        }
    }

    private fun writeTemp(context: Context, id: String, b64: String): File? = try {
        val dir = File(context.cacheDir, "chat_voice").apply { mkdirs() }
        // تنظيف الملفات الأقدم من يوم كي لا تتراكم.
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24L * 3600 * 1000) it.delete() }
        val f = File(dir, id.filter { it.isLetterOrDigit() } + ".m4a")
        if (!f.exists()) f.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
        f
    } catch (_: Exception) {
        null
    }
}
