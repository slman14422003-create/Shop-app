package com.shopmanager.app.assistant.voice

class WhisperBridge private constructor() {
    companion object {
        @Volatile private var state = 0

        @Synchronized
        fun available(): Boolean {
            if (state == 0) {
                state = try {
                    System.loadLibrary("shopwhisper")
                    1
                } catch (t: Throwable) {
                    -1
                }
            }
            return state == 1
        }

        @JvmStatic external fun nativeInit(path: String): Long
        @JvmStatic external fun nativeTranscribe(ctx: Long, pcm: FloatArray, lang: String, prompt: String, threads: Int): ByteArray?
        @JvmStatic external fun nativeLastConfidence(): Float
        @JvmStatic external fun nativeFree(ctx: Long)
    }
}
