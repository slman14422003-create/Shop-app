package com.shopmanager.app.assistant.voice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

internal const val WHISPER_MODEL_ID = "whisper"
internal const val VOSK_MODEL_ID = "vosk"
internal const val VOSK_CATALOG_URL = "https://alphacephei.com/vosk/models/model-list.json"
internal const val WHISPER_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
internal const val WHISPER_FILE_NAME = "ggml-tiny-q5_1.bin"

internal class ModelManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voice_models_meta", Context.MODE_PRIVATE)
    val root = File(context.applicationContext.filesDir, "voice_models")
    private val whisperDir get() = File(root, "whisper")
    private val voskRoot get() = File(root, "vosk")

    val whisperFile: File get() = File(whisperDir, WHISPER_FILE_NAME)

    fun whisperInstalled(): Boolean = whisperFile.isFile && whisperFile.length() > 0L

    fun whisperHeadSize(): Long = prefs.getLong("whisper_size", 0L)

    fun whisperHeadSha(): String? = prefs.getString("whisper_sha", null)

    fun saveWhisperHead(size: Long, sha: String?) {
        prefs.edit().putLong("whisper_size", size).putString("whisper_sha", sha).apply()
    }

    fun voskEntry(): VoskCatalogEntry? {
        val raw = prefs.getString("vosk_entry", null) ?: return null
        return try {
            val o = JSONObject(raw)
            VoskCatalogEntry(
                o.getString("name"), o.getString("lang"), o.getString("type"), o.getLong("size"),
                o.optString("md5"), o.getString("url"), false
            )
        } catch (t: Throwable) {
            null
        }
    }

    fun voskRejected(): Boolean = prefs.getBoolean("vosk_rejected", false)

    fun markVoskRejected() {
        prefs.edit().putBoolean("vosk_rejected", true).remove("vosk_entry").apply()
    }

    private fun saveVoskEntry(e: VoskCatalogEntry) {
        val o = JSONObject().put("name", e.name).put("lang", e.lang).put("type", e.type)
            .put("size", e.size).put("md5", e.md5).put("url", e.url)
        prefs.edit().putString("vosk_entry", o.toString()).putBoolean("vosk_rejected", false).apply()
    }

    fun installedVoskDir(): File? {
        val dirs = voskRoot.listFiles() ?: return null
        return dirs.firstOrNull { it.isDirectory && File(it, ".complete").isFile }
    }

    fun voskUsable(): Boolean = installedVoskDir() != null || (voskEntry() != null && !voskRejected())

    fun resolveVosk(ramMb: Long): VoskCatalogEntry? {
        val text = HttpSupport.getText(VOSK_CATALOG_URL)
        val arr = JSONArray(text)
        val list = ArrayList<VoskCatalogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                VoskCatalogEntry(
                    o.optString("name"), o.optString("lang"), o.optString("type"), o.optLong("size", 0L),
                    o.optString("md5"), o.optString("url"), o.optString("obsolete", "false").equals("true", ignoreCase = true)
                )
            )
        }
        val chosen = VoskCatalogLogic.chooseVoskModel(list, ramMb)
        if (chosen == null) markVoskRejected() else saveVoskEntry(chosen)
        return chosen
    }

    fun list(): List<ModelInfo> {
        val out = ArrayList<ModelInfo>()
        val vd = installedVoskDir()
        val entry = voskEntry()
        if (vd != null || (!voskRejected())) {
            val size = entry?.size ?: 0L
            out.add(
                ModelInfo(
                    VOSK_MODEL_ID, VoiceEngineKind.VOSK,
                    "Vosk عربي صغير" + (entry?.let { " (${it.name})" } ?: ""),
                    size, vd != null, if (vd != null) dirSize(vd) else 0L
                )
            )
        }
        out.add(
            ModelInfo(
                WHISPER_MODEL_ID, VoiceEngineKind.WHISPER, "Whisper Tiny مضغوط",
                whisperHeadSize(), whisperInstalled(), if (whisperInstalled()) whisperFile.length() else 0L
            )
        )
        return out
    }

    fun delete(id: String) {
        when (id) {
            WHISPER_MODEL_ID -> {
                whisperDir.deleteRecursively()
            }
            VOSK_MODEL_ID -> {
                voskRoot.deleteRecursively()
            }
        }
    }

    fun dirSize(dir: File): Long {
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isDirectory) f.listFiles()?.forEach { stack.add(it) } else total += f.length()
        }
        return total
    }

    fun installVoskZip(zip: File, entry: VoskCatalogEntry, isCancelled: () -> Boolean): Boolean {
        val target = File(voskRoot, entry.name)
        val staging = File(voskRoot, entry.name + ".staging")
        staging.deleteRecursively()
        staging.mkdirs()
        val stagingCanonical = staging.canonicalPath
        try {
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                var prefix: String? = null
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (isCancelled()) {
                        staging.deleteRecursively()
                        return false
                    }
                    val ze = zin.nextEntry ?: break
                    var name = ze.name.replace('\\', '/')
                    if (prefix == null) {
                        val slash = name.indexOf('/')
                        prefix = if (slash > 0) name.substring(0, slash + 1) else ""
                    }
                    if (prefix.isNotEmpty() && name.startsWith(prefix)) name = name.substring(prefix.length)
                    if (name.isEmpty()) continue
                    val out = File(staging, name)
                    if (!out.canonicalPath.startsWith(stagingCanonical)) throw IOException("bad entry")
                    if (ze.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            while (true) {
                                val n = zin.read(buf)
                                if (n < 0) break
                                fos.write(buf, 0, n)
                            }
                        }
                    }
                }
            }
            if (!File(staging, "am").exists() && !File(staging, "conf").exists()) {
                staging.deleteRecursively()
                return false
            }
            File(staging, ".complete").writeText("ok")
            voskRoot.listFiles()?.forEach { if (it != staging) it.deleteRecursively() }
            if (!staging.renameTo(target)) {
                staging.deleteRecursively()
                return false
            }
            return true
        } catch (t: Throwable) {
            staging.deleteRecursively()
            return false
        } finally {
            zip.delete()
        }
    }

    fun downloadDirFor(id: String): File {
        val d = if (id == WHISPER_MODEL_ID) whisperDir else voskRoot
        d.mkdirs()
        return d
    }
}
