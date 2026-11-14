package com.shopmanager.app.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class VoicePureTest {

    private fun frame(amp: Int, n: Int = 1600): ShortArray = ShortArray(n) { if (it % 2 == 0) amp.toShort() else (-amp).toShort() }

    @Test fun vad_ends_after_hangover() {
        val vad = Vad(hangoverMs = 600L, minSpeechMs = 100L)
        repeat(3) { vad.feed(frame(50), 1600) }
        var ev = VadEvent.SILENCE
        repeat(5) { ev = vad.feed(frame(8000), 1600) }
        assertEquals(VadEvent.SPEECH, ev)
        assertTrue(vad.started)
        var ended = false
        repeat(10) { if (vad.feed(frame(50), 1600) == VadEvent.ENDED) ended = true }
        assertTrue(ended)
    }

    @Test fun vad_no_speech_timeout() {
        val vad = Vad(noSpeechTimeoutMs = 1000L)
        var ev = VadEvent.SILENCE
        repeat(20) { if (ev != VadEvent.NO_SPEECH) ev = vad.feed(frame(10), 1600) }
        assertEquals(VadEvent.NO_SPEECH, ev)
    }

    @Test fun vad_max_utterance() {
        val vad = Vad(maxUtteranceMs = 1000L, hangoverMs = 5000L)
        var ev = VadEvent.SILENCE
        repeat(20) { if (ev != VadEvent.ENDED) ev = vad.feed(frame(9000), 1600) }
        assertEquals(VadEvent.ENDED, ev)
    }

    @Test fun vad_adapts_noise_floor() {
        val vad = Vad(minLevel = 0.005f)
        repeat(30) { vad.feed(frame(700), 1600) }
        assertTrue(vad.threshold() > 0.005f)
    }

    @Test fun segmenter_collects_only_after_speech_start() {
        val seg = VadSegmenter(hangoverMs = 400L)
        repeat(5) { seg.push(frame(20), 1600) }
        assertEquals(0, seg.bufferedSamples)
        repeat(5) { seg.push(frame(9000), 1600) }
        assertTrue(seg.bufferedSamples >= 8000)
        var ended = false
        repeat(10) { if (seg.push(frame(20), 1600) == VadEvent.ENDED) ended = true }
        assertTrue(ended)
        val floats = seg.speechFloats()
        assertTrue(floats.isNotEmpty())
        seg.clear()
        assertEquals(0, seg.bufferedSamples)
    }

    @Test fun selector_system_first_when_eligible() {
        val p = DeviceProfile(4000, 8, 33, true, true, true)
        val o = EngineSelector.order(p, VoiceEngineKind.AUTO, voskUsable = true, whisperUsable = true)
        assertEquals(listOf(VoiceEngineKind.SYSTEM, VoiceEngineKind.WHISPER, VoiceEngineKind.VOSK), o)
    }

    @Test fun selector_low_prefers_whisper_then_vosk() {
        val p = DeviceProfile(2000, 4, 29, false, false, false)
        val o = EngineSelector.order(p, VoiceEngineKind.AUTO, voskUsable = true, whisperUsable = true)
        assertEquals(listOf(VoiceEngineKind.WHISPER, VoiceEngineKind.VOSK), o)
    }

    @Test fun selector_whisper_primary_when_vosk_unusable() {
        val p = DeviceProfile(2000, 4, 29, false, false, false)
        val o = EngineSelector.order(p, VoiceEngineKind.AUTO, voskUsable = false, whisperUsable = true)
        assertEquals(listOf(VoiceEngineKind.WHISPER), o)
    }

    @Test fun selector_system_needs_android12_and_locale() {
        val old = DeviceProfile(8000, 8, 30, true, true, true)
        assertFalse(EngineSelector.systemEligible(old))
        val noLocale = DeviceProfile(8000, 8, 34, true, true, false)
        assertFalse(EngineSelector.systemEligible(noLocale))
        assertTrue(EngineSelector.systemEligible(DeviceProfile(8000, 8, 34, true, true, true)))
    }

    @Test fun selector_manual_preference_first() {
        val p = DeviceProfile(8000, 8, 34, true, true, true)
        val o = EngineSelector.order(p, VoiceEngineKind.VOSK, true, true)
        assertEquals(VoiceEngineKind.VOSK, o.first())
        assertEquals(listOf(VoiceEngineKind.VOSK, VoiceEngineKind.SYSTEM, VoiceEngineKind.WHISPER), o)
        val skip = EngineSelector.order(DeviceProfile(8000, 8, 30, true, true, true), VoiceEngineKind.SYSTEM, true, true)
        assertEquals(listOf(VoiceEngineKind.VOSK, VoiceEngineKind.WHISPER), skip)
    }

    @Test fun device_classes() {
        assertEquals(DeviceClass.LOW, EngineSelector.deviceClass(DeviceProfile(2000, 8, 34, true, false, false)))
        assertEquals(DeviceClass.MID, EngineSelector.deviceClass(DeviceProfile(4000, 8, 34, true, false, false)))
        assertEquals(DeviceClass.HIGH, EngineSelector.deviceClass(DeviceProfile(8000, 8, 34, true, false, false)))
    }

    @Test fun download_math() {
        assertEquals("bytes=1024-", DownloadMath.rangeHeader(1024))
        assertEquals(0L, DownloadMath.resumeOffset(0, 100, null, null, true))
        assertEquals(50L, DownloadMath.resumeOffset(50, 100, "a", "a", true))
        assertEquals(0L, DownloadMath.resumeOffset(50, 100, "a", "b", true))
        assertEquals(0L, DownloadMath.resumeOffset(50, 100, "a", "a", false))
        assertEquals(0L, DownloadMath.resumeOffset(150, 100, null, null, true))
        assertEquals(1000L, DownloadMath.backoffMs(0))
        assertEquals(4000L, DownloadMath.backoffMs(2))
        assertEquals(30000L, DownloadMath.backoffMs(9))
        assertEquals(1000L, DownloadMath.totalFromContentRange("bytes 100-999/1000"))
        assertEquals(-1L, DownloadMath.totalFromContentRange(null))
        assertEquals(50, DownloadMath.percent(50, 100))
        assertEquals(0, DownloadMath.percent(5, 0))
    }

    @Test fun etag_and_checksum() {
        val sha = "a".repeat(64)
        assertEquals(sha, DownloadMath.sha256FromEtag("\"$sha\""))
        assertNull(DownloadMath.sha256FromEtag("\"abc123\""))
        assertNull(DownloadMath.sha256FromEtag(null))
        assertEquals("abc", DownloadMath.cleanEtag("W/\"abc\""))
        assertTrue(DownloadMath.checksumMatches(" ABC ", "abc"))
        assertFalse(DownloadMath.checksumMatches(null, "abc"))
    }

    @Test fun hashing() {
        val data = "hello".toByteArray()
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", DownloadMath.sha256(data))
        assertEquals("5d41402abc4b2a76b9719d911017c592", DownloadMath.md5(data))
        assertEquals(DownloadMath.sha256(data), DownloadMath.digest("SHA-256", ByteArrayInputStream(data)))
        assertNull(DownloadMath.digest("MD5", ByteArrayInputStream(data)) { true })
    }

    @Test fun ggml_magic() {
        assertTrue(DownloadMath.isGgmlMagic(byteArrayOf(0x6c, 0x6d, 0x67, 0x67)))
        assertFalse(DownloadMath.isGgmlMagic(byteArrayOf(1, 2, 3, 4)))
        assertFalse(DownloadMath.isGgmlMagic(byteArrayOf(1)))
    }

    private fun entry(name: String, lang: String, type: String, size: Long, obsolete: Boolean = false) =
        VoskCatalogEntry(name, lang, type, size, "md5", "https://x/$name.zip", obsolete)

    @Test fun vosk_choose_smallest_arabic_small() {
        val list = listOf(
            entry("big", "ar", "big", 300_000_000),
            entry("s2", "ar", "small", 80_000_000),
            entry("s1", "ar", "small", 50_000_000),
            entry("en", "en-us", "small", 40_000_000),
            entry("old", "ar", "small", 10_000_000, obsolete = true)
        )
        assertEquals("s1", VoskCatalogLogic.chooseVoskModel(list, 2000)?.name)
    }

    @Test fun vosk_none_when_no_arabic_small_or_over_budget() {
        assertNull(VoskCatalogLogic.chooseVoskModel(listOf(entry("big", "ar", "big", 300_000_000)), 4000))
        assertNull(VoskCatalogLogic.chooseVoskModel(listOf(entry("s", "ar", "small", 500_000_000)), 1000))
        assertNull(VoskCatalogLogic.chooseVoskModel(emptyList(), 4000))
        assertNotNull(VoskCatalogLogic.chooseVoskModel(listOf(entry("s", "ar", "small", 100_000_000)), 4000))
    }

    @Test fun segment_joiner_uses_comma_on_long_pause() {
        val s = listOf(
            SegmentJoiner.Segment("سكر", 0),
            SegmentJoiner.Segment("هيل", 1200),
            SegmentJoiner.Segment("ملح", 300)
        )
        assertEquals("سكر ، هيل ملح", SegmentJoiner.join(s))
        assertEquals("", SegmentJoiner.join(listOf(SegmentJoiner.Segment(" ", 0))))
    }

    @Test fun whisper_filter() {
        assertEquals("", WhisperPostFilter.clean("شكرا للمشاهدة."))
        assertEquals("سجل على سامر", WhisperPostFilter.clean("[موسيقى] سجل على سامر (ضحك)"))
        assertEquals("ضيف سكر", WhisperPostFilter.clean("  ضيف   سكر "))
    }
}
