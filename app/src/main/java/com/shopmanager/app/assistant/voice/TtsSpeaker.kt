package com.shopmanager.app.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class TtsSpeaker(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "voice-tts").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var failed = false

    fun speak(text: String) {
        if (text.isBlank() || failed) return
        executor.execute {
            try {
                ensure()
                val engine = tts
                if (ready && engine != null) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shop-tts")
            } catch (t: Throwable) {
                failed = true
            }
        }
    }

    fun stop() {
        executor.execute {
            try {
                tts?.stop()
            } catch (t: Throwable) {
            }
        }
    }

    fun release() {
        executor.execute {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (t: Throwable) {
            }
            tts = null
            ready = false
            failed = false
        }
    }

    private fun ensure() {
        if (tts != null) return
        val latch = CountDownLatch(1)
        var ok = false
        val engine = TextToSpeech(appContext) { status ->
            ok = status == TextToSpeech.SUCCESS
            latch.countDown()
        }
        latch.await(6, TimeUnit.SECONDS)
        if (!ok) {
            try {
                engine.shutdown()
            } catch (t: Throwable) {
            }
            failed = true
            return
        }
        val candidates = listOf(Locale("ar", "SY"), Locale("ar", "LB"), Locale("ar"))
        var chosen = false
        for (l in candidates) {
            val r = engine.isLanguageAvailable(l)
            if (r >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = l
                chosen = true
                break
            }
        }
        if (!chosen) {
            try {
                engine.shutdown()
            } catch (t: Throwable) {
            }
            failed = true
            return
        }
        tts = engine
        ready = true
    }
}
