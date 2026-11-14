package com.shopmanager.app.data.chat

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.coroutines.resume

sealed interface TranscribeResult {
    data class Text(val text: String) : TranscribeResult
    data class Error(val message: String) : TranscribeResult
}

object VoiceTranscriber {

    private const val MIN_SDK = 33
    private const val TIMEOUT_MS = 90_000L
    private val TAGS = listOf("ar-SY", "ar-LB", "ar-JO", "ar-SA")

    private class Pcm(val file: File, val sampleRate: Int, val channels: Int)

    private sealed interface Attempt {
        class Done(val text: String) : Attempt
        class Failed(val code: Int) : Attempt
        data object Crashed : Attempt
    }

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= MIN_SDK && runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    suspend fun transcribe(context: Context, audioB64: String): TranscribeResult {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            return TranscribeResult.Error("النسخ النصي للرسائل الصوتية يحتاج أندرويد 13 أو أحدث")
        }
        if (!isSupported(app)) {
            return TranscribeResult.Error("خدمة التعرف على الصوت غير متوفرة على هذا الجهاز")
        }
        val pcm = withContext(Dispatchers.IO) { runCatching { decodeToPcm(app, audioB64) }.getOrNull() }
            ?: return TranscribeResult.Error("تعذر قراءة الرسالة الصوتية")
        try {
            var lastCode = SpeechRecognizer.ERROR_CLIENT
            for (tag in TAGS) {
                val attempt = withTimeoutOrNull(TIMEOUT_MS) { recognizeOnce(app, pcm, tag) }
                    ?: return TranscribeResult.Error("انتهت مهلة النسخ النصي")
                when (attempt) {
                    is Attempt.Done -> {
                        val t = attempt.text.trim()
                        return if (t.isEmpty()) TranscribeResult.Error("ما قدرت أفهم الكلام بالتسجيل") else TranscribeResult.Text(t)
                    }
                    is Attempt.Failed -> {
                        lastCode = attempt.code
                        val languageIssue = attempt.code == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                            attempt.code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        if (!languageIssue) break
                    }
                    Attempt.Crashed -> break
                }
            }
            return TranscribeResult.Error(errorText(lastCode))
        } finally {
            pcm.file.delete()
        }
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "ما في إنترنت للنسخ النصي"
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ما قدرت أفهم الكلام بالتسجيل"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اسمح بصلاحية المايك لاستخدام النسخ النصي"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "الخدمة مشغولة، جرّب بعد قليل"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "اللغة العربية غير متوفرة في خدمة الصوت"
        else -> "تعذر النسخ النصي على هذا الجهاز"
    }

    private suspend fun recognizeOnce(context: Context, pcm: Pcm, tag: String): Attempt =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Attempt> { cont ->
                var recognizer: SpeechRecognizer? = null
                var readEnd: ParcelFileDescriptor? = null
                var finished = false
                fun finish(a: Attempt) {
                    if (finished) return
                    finished = true
                    runCatching { recognizer?.destroy() }
                    runCatching { readEnd?.close() }
                    if (cont.isActive) cont.resume(a)
                }
                try {
                    val pipe = ParcelFileDescriptor.createPipe()
                    readEnd = pipe[0]
                    val writeEnd = pipe[1]
                    val r = SpeechRecognizer.createSpeechRecognizer(context)
                    recognizer = r
                    r.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                        override fun onError(error: Int) = finish(Attempt.Failed(error))
                        override fun onResults(results: Bundle?) {
                            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                            finish(Attempt.Done(t))
                        }
                    })
                    thread(name = "voice-transcribe-feed", isDaemon = true) {
                        runCatching {
                            ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
                                FileInputStream(pcm.file).use { input ->
                                    val buf = ByteArray(8192)
                                    while (true) {
                                        val n = input.read(buf)
                                        if (n < 0) break
                                        out.write(buf, 0, n)
                                    }
                                }
                            }
                        }
                    }
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                        if (Build.VERSION.SDK_INT >= MIN_SDK) {
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, pcm.channels)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate)
                        }
                    }
                    r.startListening(intent)
                } catch (_: Throwable) {
                    finish(Attempt.Crashed)
                }
                cont.invokeOnCancellation {
                    runCatching { recognizer?.cancel() }
                    runCatching { recognizer?.destroy() }
                    runCatching { readEnd?.close() }
                }
            }
        }

    private fun decodeToPcm(context: Context, audioB64: String): Pcm? {
        val dir = File(context.cacheDir, "chat_transcribe").apply { mkdirs() }
        val stamp = System.nanoTime()
        val src = File(dir, "src_$stamp.m4a")
        val out = File(dir, "pcm_$stamp.raw")
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            src.writeBytes(Base64.decode(audioB64, Base64.NO_WRAP))
            val ex = MediaExtractor().apply { setDataSource(src.absolutePath) }
            extractor = ex
            var track = -1
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) { track = i; break }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 16_000
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idle = 0
            FileOutputStream(out).use { fos ->
                while (!outputDone && idle < 400) {
                    if (!inputDone) {
                        val ii = c.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val buf = c.getInputBuffer(ii)
                            val size = if (buf != null) ex.readSampleData(buf, 0) else -1
                            if (size < 0) {
                                c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                c.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val oi = c.dequeueOutputBuffer(info, 10_000)
                    when {
                        oi >= 0 -> {
                            idle = 0
                            val ob = c.getOutputBuffer(oi)
                            if (ob != null && info.size > 0) {
                                ob.position(info.offset)
                                ob.limit(info.offset + info.size)
                                val arr = ByteArray(info.size)
                                ob.get(arr)
                                fos.write(arr)
                            }
                            c.releaseOutputBuffer(oi, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val nf = c.outputFormat
                            if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        else -> idle++
                    }
                }
            }
            if (out.length() < 3200L) {
                out.delete()
                return null
            }
            return Pcm(out, sampleRate, channels)
        } catch (_: Throwable) {
            out.delete()
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
            src.delete()
        }
    }
}
