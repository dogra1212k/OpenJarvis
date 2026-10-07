#include "engine.h"
#include <jni.h>
#include <memory>
#include <mutex>

static std::mutex engineMutex;
static std::unique_ptr<OfflineEngine> engine;

static std::string bytes(JNIEnv *env, jbyteArray value) {
    const auto size = env->GetArrayLength(value);
    std::string result(size, '\0');
    env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte *>(result.data()));
    return result;
}
static jbyteArray array(JNIEnv *env, const std::string &text) {
    auto result = env->NewByteArray(static_cast<jsize>(text.size()));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dogra_hindijarvis_OfflineAi_generate(JNIEnv *env, jobject, jbyteArray path, jbyteArray prompt, jobject callback) {
    std::lock_guard<std::mutex> lock(engineMutex);
    try {
        if (!engine) engine = std::make_unique<OfflineEngine>(bytes(env, path));
        jclass cls = env->GetObjectClass(callback);
        jmethodID method = env->GetMethodID(cls, "onText", "([B)V");
        if (!method) return nullptr;
        auto result = engine->generate(bytes(env, prompt), [&](const std::string &partial) {
            auto data = array(env, partial);
            env->CallVoidMethod(callback, method, data);
            env->DeleteLocalRef(data);
        });
        env->DeleteLocalRef(cls);
        return array(env, result);
    } catch (const std::exception &e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
        return nullptr;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_dogra_hindijarvis_OfflineAi_setCancelled(JNIEnv *, jobject, jboolean value) { cancelled = value; }
extern "C" JNIEXPORT void JNICALL
Java_com_dogra_hindijarvis_OfflineAi_close(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engineMutex);
    engine.reset();
}
