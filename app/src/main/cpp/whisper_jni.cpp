#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include "whisper.h"

static std::atomic<float> g_last_conf(0.0f);

static std::string sanitize_utf8(const std::string &in) {
    std::string out;
    out.reserve(in.size());
    size_t i = 0;
    const size_t n = in.size();
    while (i < n) {
        unsigned char c = static_cast<unsigned char>(in[i]);
        size_t len = 0;
        if (c < 0x80) len = 1;
        else if ((c & 0xE0) == 0xC0) len = 2;
        else if ((c & 0xF0) == 0xE0) len = 3;
        else if ((c & 0xF8) == 0xF0) len = 4;
        if (len == 0 || i + len > n) {
            i++;
            continue;
        }
        bool ok = true;
        for (size_t k = 1; k < len; k++) {
            if ((static_cast<unsigned char>(in[i + k]) & 0xC0) != 0x80) {
                ok = false;
                break;
            }
        }
        if (ok) out.append(in, i, len);
        i += ok ? len : 1;
    }
    return out;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_shopmanager_app_assistant_voice_WhisperBridge_nativeInit(JNIEnv *env, jclass, jstring path) {
    if (path == nullptr) return 0;
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (p == nullptr) return 0;
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cparams);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jbyteArray JNICALL
Java_com_shopmanager_app_assistant_voice_WhisperBridge_nativeTranscribe(
        JNIEnv *env, jclass, jlong handle, jfloatArray pcm, jstring lang, jstring prompt, jint threads) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr || pcm == nullptr) return nullptr;
    jsize n = env->GetArrayLength(pcm);
    if (n <= 0) return nullptr;
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    if (samples == nullptr) return nullptr;

    const char *l = lang != nullptr ? env->GetStringUTFChars(lang, nullptr) : nullptr;
    const char *pr = prompt != nullptr ? env->GetStringUTFChars(prompt, nullptr) : nullptr;
    std::string language = l != nullptr ? l : "ar";
    std::string initial = pr != nullptr ? pr : "";
    if (l != nullptr) env->ReleaseStringUTFChars(lang, l);
    if (pr != nullptr) env->ReleaseStringUTFChars(prompt, pr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_timestamps = true;
    params.no_context = true;
    params.single_segment = false;
    params.language = language.c_str();
    params.detect_language = false;
    params.n_threads = threads > 0 ? threads : 2;
    params.temperature = 0.0f;
    params.greedy.best_of = 1;
    if (!initial.empty()) params.initial_prompt = initial.c_str();

    int rc = whisper_full(ctx, params, samples, static_cast<int>(n));
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (rc != 0) return nullptr;

    std::string text;
    double sum = 0.0;
    int count = 0;
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        const char *t = whisper_full_get_segment_text(ctx, i);
        if (t != nullptr) text += t;
        int tokens = whisper_full_n_tokens(ctx, i);
        for (int k = 0; k < tokens; k++) {
            whisper_token_data d = whisper_full_get_token_data(ctx, i, k);
            if (d.id < whisper_token_eot(ctx)) {
                sum += d.p;
                count++;
            }
        }
    }
    g_last_conf.store(count > 0 ? static_cast<float>(sum / count) : 0.0f);

    std::string clean = sanitize_utf8(text);
    jbyteArray out = env->NewByteArray(static_cast<jsize>(clean.size()));
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(clean.size()), reinterpret_cast<const jbyte *>(clean.data()));
    return out;
}

JNIEXPORT jfloat JNICALL
Java_com_shopmanager_app_assistant_voice_WhisperBridge_nativeLastConfidence(JNIEnv *, jclass) {
    return g_last_conf.load();
}

JNIEXPORT void JNICALL
Java_com_shopmanager_app_assistant_voice_WhisperBridge_nativeFree(JNIEnv *, jclass, jlong handle) {
    whisper_context *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

}
