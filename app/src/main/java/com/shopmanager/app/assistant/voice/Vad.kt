package com.shopmanager.app.assistant.voice

import kotlin.math.max
import kotlin.math.sqrt

enum class VadEvent { SILENCE, SPEECH, ENDED, NO_SPEECH }

class Vad(
    private val sampleRate: Int = 16000,
    private val hangoverMs: Long = 1500L,
    private val minSpeechMs: Long = 160L,
    private val maxUtteranceMs: Long = 15000L,
    private val noSpeechTimeoutMs: Long = 8000L,
    private val minLevel: Float = 0.015f
) {
    var noiseFloor: Float = minLevel * 0.5f
        private set
    var lastLevel: Float = 0f
        private set
    var started: Boolean = false
        private set
    var speechMs: Long = 0L
        private set
    var silenceMs: Long = 0L
        private set
    var totalMs: Long = 0L
        private set

    private var voicedRun = 0L

    fun threshold(): Float = max(minLevel, noiseFloor * 3f)

    fun feed(frame: ShortArray, len: Int): VadEvent {
        if (len <= 0) return VadEvent.SILENCE
        val ms = len * 1000L / sampleRate
        val level = rms(frame, len)
        lastLevel = level
        totalMs += ms
        val speech = level >= threshold()
        if (speech) {
            voicedRun += ms
            silenceMs = 0L
            speechMs += ms
            if (!started && voicedRun >= minSpeechMs) started = true
        } else {
            voicedRun = 0L
            if (started) silenceMs += ms
            noiseFloor = noiseFloor * 0.9f + level * 0.1f
            if (noiseFloor > 0.08f) noiseFloor = 0.08f
        }
        if (started && silenceMs >= hangoverMs) return VadEvent.ENDED
        if (totalMs >= maxUtteranceMs) return if (started) VadEvent.ENDED else VadEvent.NO_SPEECH
        if (!started && totalMs >= noSpeechTimeoutMs) return VadEvent.NO_SPEECH
        return if (speech) VadEvent.SPEECH else VadEvent.SILENCE
    }

    fun reset() {
        noiseFloor = minLevel * 0.5f
        lastLevel = 0f
        started = false
        speechMs = 0L
        silenceMs = 0L
        totalMs = 0L
        voicedRun = 0L
    }

    companion object {
        fun rms(frame: ShortArray, len: Int): Float {
            if (len <= 0) return 0f
            var sum = 0.0
            for (i in 0 until len) {
                val v = frame[i].toDouble()
                sum += v * v
            }
            return (sqrt(sum / len) / 32768.0).toFloat().coerceIn(0f, 1f)
        }

        fun normalizedLevel(rms: Float): Float = (rms * 8f).coerceIn(0f, 1f)
    }
}

class UtteranceBuffer(private val maxSamples: Int) {
    private var data = ShortArray(16000)
    var size: Int = 0
        private set

    fun append(src: ShortArray, len: Int) {
        if (len <= 0) return
        val room = maxSamples - size
        val n = if (len > room) room else len
        if (n <= 0) return
        if (size + n > data.size) {
            var cap = data.size
            while (cap < size + n) cap *= 2
            if (cap > maxSamples) cap = maxSamples
            data = data.copyOf(cap)
        }
        System.arraycopy(src, 0, data, size, n)
        size += n
    }

    fun trimTail(samples: Int) {
        size = max(0, size - samples)
    }

    fun toFloats(): FloatArray {
        val out = FloatArray(size)
        for (i in 0 until size) out[i] = data[i] / 32768f
        return out
    }

    fun clear() {
        java.util.Arrays.fill(data, 0.toShort())
        size = 0
    }
}

class VadSegmenter(
    private val sampleRate: Int = 16000,
    hangoverMs: Long = 1500L,
    minLevel: Float = 0.015f,
    maxUtteranceMs: Long = 15000L,
    private val preRollMs: Long = 300L,
    private val tailKeepMs: Long = 300L
) {
    private val vad = Vad(sampleRate, hangoverMs, 160L, maxUtteranceMs, 8000L, minLevel)
    private val buffer = UtteranceBuffer((sampleRate * (maxUtteranceMs / 1000L + 2)).toInt())
    private val preRollSamples = (sampleRate * preRollMs / 1000L).toInt()
    private var pre = ShortArray(preRollSamples)
    private var preSize = 0
    private var flushedPre = false

    val level: Float get() = vad.lastLevel
    val started: Boolean get() = vad.started
    val speechMs: Long get() = vad.speechMs
    val bufferedSamples: Int get() = buffer.size

    fun push(frame: ShortArray, len: Int): VadEvent {
        val ev = vad.feed(frame, len)
        if (vad.started) {
            if (!flushedPre) {
                buffer.append(pre, preSize)
                flushedPre = true
            }
            buffer.append(frame, len)
        } else {
            if (len >= preRollSamples) {
                System.arraycopy(frame, len - preRollSamples, pre, 0, preRollSamples)
                preSize = preRollSamples
            } else {
                val keep = minOf(preRollSamples - len, preSize)
                System.arraycopy(pre, preSize - keep, pre, 0, keep)
                System.arraycopy(frame, 0, pre, keep, len)
                preSize = keep + len
            }
        }
        return ev
    }

    fun speechFloats(): FloatArray {
        val extraSilence = (vad.silenceMs - tailKeepMs).coerceAtLeast(0L)
        val drop = (extraSilence * sampleRate / 1000L).toInt()
        if (drop > 0) buffer.trimTail(drop)
        return buffer.toFloats()
    }

    fun snapshotFloats(): FloatArray = buffer.toFloats()

    fun clear() {
        buffer.clear()
        java.util.Arrays.fill(pre, 0.toShort())
        preSize = 0
        flushedPre = false
        vad.reset()
    }
}
