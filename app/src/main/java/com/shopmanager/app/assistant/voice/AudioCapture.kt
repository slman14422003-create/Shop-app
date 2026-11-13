package com.shopmanager.app.assistant.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

internal class AudioCapture {
    private var record: AudioRecord? = null
    private var suppressor: NoiseSuppressor? = null
    private var gain: AutomaticGainControl? = null
    private var echo: AcousticEchoCanceler? = null

    fun open(): Boolean {
        try {
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return false
            val size = maxOf(min, SAMPLE_RATE)
            val r = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size
            )
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                return false
            }
            val sid = r.audioSessionId
            try {
                if (NoiseSuppressor.isAvailable()) suppressor = NoiseSuppressor.create(sid)?.also { it.enabled = true }
            } catch (t: Throwable) {
                suppressor = null
            }
            try {
                if (AutomaticGainControl.isAvailable()) gain = AutomaticGainControl.create(sid)?.also { it.enabled = true }
            } catch (t: Throwable) {
                gain = null
            }
            try {
                if (AcousticEchoCanceler.isAvailable()) echo = AcousticEchoCanceler.create(sid)?.also { it.enabled = true }
            } catch (t: Throwable) {
                echo = null
            }
            r.startRecording()
            if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                r.release()
                releaseEffects()
                return false
            }
            record = r
            return true
        } catch (t: Throwable) {
            close()
            return false
        }
    }

    fun read(buf: ShortArray, len: Int): Int {
        val r = record ?: return -1
        return try {
            r.read(buf, 0, len)
        } catch (t: Throwable) {
            -1
        }
    }

    private fun releaseEffects() {
        try {
            suppressor?.release()
        } catch (t: Throwable) {
        }
        try {
            gain?.release()
        } catch (t: Throwable) {
        }
        try {
            echo?.release()
        } catch (t: Throwable) {
        }
        suppressor = null
        gain = null
        echo = null
    }

    fun close() {
        val r = record
        record = null
        if (r != null) {
            try {
                r.stop()
            } catch (t: Throwable) {
            }
            try {
                r.release()
            } catch (t: Throwable) {
            }
        }
        releaseEffects()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK = 1600
    }
}
