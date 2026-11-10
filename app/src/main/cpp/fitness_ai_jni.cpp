// The link between Kotlin (io.github.randomtools3377.fitness.ai.NativeAi) and fitness_ai.cpp.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "fitness_ai.h"

static volatile bool g_stop = false;

static std::string str(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

// Java's UTF-8 functions need valid text; a model can stop mid-letter, so cut any broken end.
static jstring jstr(JNIEnv * env, const std::string & s) {
    size_t end = s.size();
    size_t i = 0;
    while (i < s.size()) {
        unsigned char c = s[i];
        size_t n = c < 0x80 ? 1 : (c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : (c >> 3) == 30 ? 4 : 0;
        if (n == 0 || i + n > s.size()) { end = i; break; }
        i += n;
    }
    std::vector<jchar> utf16;
    i = 0;
    while (i < end) {
        unsigned char c = s[i];
        uint32_t cp;
        size_t n;
        if (c < 0x80) { cp = c; n = 1; }
        else if ((c >> 5) == 6) { cp = c & 0x1F; n = 2; }
        else if ((c >> 4) == 14) { cp = c & 0x0F; n = 3; }
        else { cp = c & 0x07; n = 4; }
        for (size_t k = 1; k < n; k++) cp = (cp << 6) | (s[i + k] & 0x3F);
        i += n;
        if (cp >= 0x10000) {
            cp -= 0x10000;
            utf16.push_back((jchar) (0xD800 + (cp >> 10)));
            utf16.push_back((jchar) (0xDC00 + (cp & 0x3FF)));
        } else {
            utf16.push_back((jchar) cp);
        }
    }
    return env->NewString(utf16.data(), (jsize) utf16.size());
}

extern "C" {

JNIEXPORT void JNICALL Java_io_github_randomtools3377_fitness_ai_NativeAi_init(JNIEnv * env, jobject, jstring lib_dir) {
    fitai::init(str(env, lib_dir).c_str());
}

JNIEXPORT jlong JNICALL Java_io_github_randomtools3377_fitness_ai_NativeAi_load(JNIEnv * env, jobject, jstring model, jstring mmproj, jint n_ctx, jint n_threads) {
    std::string mm = str(env, mmproj);
    return (jlong) fitai::load(str(env, model).c_str(), mm.empty() ? nullptr : mm.c_str(), n_ctx, n_threads);
}

JNIEXPORT void JNICALL Java_io_github_randomtools3377_fitness_ai_NativeAi_unload(JNIEnv *, jobject, jlong m) {
    fitai::unload((fitai::Model *) m);
}

JNIEXPORT jstring JNICALL Java_io_github_randomtools3377_fitness_ai_NativeAi_format(JNIEnv * env, jobject, jlong m, jstring system, jstring user, jstring examples) {
    std::string u = str(env, user);
    size_t at = u.find("{media}");
    if (at != std::string::npos) u.replace(at, 7, fitai::media_marker());
    return jstr(env, fitai::format((fitai::Model *) m, str(env, system), u, str(env, examples)));
}

JNIEXPORT jstring JNICALL Java_io_github_randomtools3377_fitness_ai_NativeAi_answer(JNIEnv * env, jobject, jlong m, jstring prompt, jstring grammar, jint max_tokens, jbyteArray media) {
    g_stop = false;
    std::vector<unsigned char> bytes;
    if (media) {
        jsize n = env->GetArrayLength(media);
        bytes.resize(n);
        env->GetByteArrayRegion(media, 0, n, (jbyte *) bytes.data());
    }
    std::string error;
    std::string out = fitai::answer((fitai::Model *) m, str(env, prompt), str(env, grammar), max_tokens,
                                    bytes.empty() ? nullptr : bytes.data(), bytes.size(), &g_stop, error);
    if (!error.empty()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.c_str());
        return nullptr;
    }
    return jstr(env, out);
}

JNIEXPORT void JNICALL Java_io_github_randomtools3377_fitness_ai_NativeAi_stop(JNIEnv *, jobject) {
    g_stop = true;
}

}
