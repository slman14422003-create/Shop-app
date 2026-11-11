package com.shopmanager.app.data.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File
import kotlin.math.ln
import kotlin.math.roundToInt

const val MAX_VOICE_MS = 120_000L
const val MIN_VOICE_MS = 700L
private const val WAVE_BARS = 40

// حد أمان لحجم النص المشفّر داخل مستند Firestore (الحد الأقصى للمستند 1MiB).
private const val MAX_B64_CHARS = 900_000

class RecordedVoice(val audioB64: String, val durationMs: Long, val wave: List<Int>)

/**
 * تسجيل صوت مضغوط للكلام: AAC أحادي 16kHz بمعدل 24kbps (≈3KB/ثانية، أي دقيقتان ≈ 360KB قبل
 * الترميز Base64). المستوى المرئي (الموجة) يُلتقط أثناء التسجيل ويُخزَّن مع الرسالة فلا نحتاج
 * لفك ترميز الصوت لرسمه.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val samples = ArrayList<Int>()

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return false
        val out = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(24_000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
        } catch (_: Exception) {
            runCatching { r.release() }
            out.delete()
            return false
        }
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
        samples.clear()
        return true
    }

    fun elapsedMs(): Long = if (recorder == null) 0L else System.currentTimeMillis() - startedAt

    /** مستوى الصوت الحالي 0..100 (مقياس لوغاريتمي)، ويُسجَّل لرسم الموجة. */
    fun sampleLevel(): Int {
        val r = recorder ?: return 0
        val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
        val level = (ln(1.0 + amp) / ln(1.0 + 32767.0) * 100).roundToInt().coerceIn(0, 100)
        samples.add(level)
        return level
    }

    /** يوقف التسجيل ويعيد النتيجة، أو null إن كان قصيراً جداً/فشل/كبيراً جداً. */
    fun stop(): RecordedVoice? {
        val r = recorder ?: return null
        val f = file
        val duration = elapsedMs()
        recorder = null
        file = null
        val ok = try {
            r.stop(); true
        } catch (_: RuntimeException) {
            false // يحدث عند تسجيل قصير جداً
        } finally {
            runCatching { r.release() }
        }
        try {
            if (!ok || f == null || duration < MIN_VOICE_MS) return null
            val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            if (b64.length > MAX_B64_CHARS) return null
            return RecordedVoice(b64, duration.coerceAtMost(MAX_VOICE_MS), downsample(samples, WAVE_BARS))
        } finally {
            f?.delete()
        }
    }

    fun cancel() {
        val r = recorder ?: return
        val f = file
        recorder = null
        file = null
        runCatching { r.stop() }
        runCatching { r.release() }
        f?.delete()
    }

    private fun downsample(src: List<Int>, bars: Int): List<Int> {
        if (src.isEmpty()) return List(bars) { 8 }
        return List(bars) { i ->
            val from = (i * src.size / bars.toFloat()).toInt()
            val to = (((i + 1) * src.size / bars.toFloat()).toInt()).coerceAtLeast(from + 1).coerceAtMost(src.size)
            var m = 0
            for (k in from until to) if (src[k] > m) m = src[k]
            m.coerceIn(6, 100)
        }
    }
}
