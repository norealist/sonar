#include <jni.h>

#include "../engine/engine_types.h"
#include "../engine/playback_engine.h"
#include "../format/format_converter.h"

#include <cstdint>
#include <limits>
#include <mutex>
#include <string>
#include <unordered_set>

namespace {

using sonar::core::ErrorCode;
using sonar::core::PlaybackEngine;

struct NativeHandle {
    static constexpr std::uint64_t kMagic = 0x534f4e4152434f52ULL;
    std::uint64_t magic = kMagic;
    PlaybackEngine engine;

    NativeHandle(jint sampleRate, jint encoding, jint channels)
        : engine(sampleRate, encoding, channels) {}
};

std::mutex gHandlesMutex;
std::unordered_set<NativeHandle*> gHandles;
std::mutex gStreamInfoMutex;
jclass gStreamInfoClass = nullptr;
jmethodID gStreamInfoConstructor = nullptr;

void clearException(JNIEnv* env) noexcept {
    if (env != nullptr && env->ExceptionCheck()) env->ExceptionClear();
}

bool appendUtf8(std::string& result, std::uint32_t codePoint) {
    if (codePoint <= 0x7f) {
        result.push_back(static_cast<char>(codePoint));
    } else if (codePoint <= 0x7ff) {
        result.push_back(static_cast<char>(0xc0 | (codePoint >> 6)));
        result.push_back(static_cast<char>(0x80 | (codePoint & 0x3f)));
    } else if (codePoint <= 0xffff) {
        result.push_back(static_cast<char>(0xe0 | (codePoint >> 12)));
        result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3f)));
        result.push_back(static_cast<char>(0x80 | (codePoint & 0x3f)));
    } else if (codePoint <= 0x10ffff) {
        result.push_back(static_cast<char>(0xf0 | (codePoint >> 18)));
        result.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3f)));
        result.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3f)));
        result.push_back(static_cast<char>(0x80 | (codePoint & 0x3f)));
    } else {
        return false;
    }
    return true;
}

bool toUtf8(JNIEnv* env, jstring value, std::string& result) {
    result.clear();
    if (value == nullptr) return false;
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr || env->ExceptionCheck()) {
        clearException(env);
        return false;
    }
    result.reserve(static_cast<std::size_t>(length));
    bool valid = true;
    for (jsize i = 0; i < length && valid; ++i) {
        std::uint32_t codePoint = chars[i];
        if (codePoint >= 0xd800 && codePoint <= 0xdbff) {
            if (i + 1 >= length || chars[i + 1] < 0xdc00 || chars[i + 1] > 0xdfff) {
                valid = false;
                break;
            }
            codePoint = 0x10000 + ((codePoint - 0xd800) << 10) + (chars[++i] - 0xdc00);
        } else if (codePoint >= 0xdc00 && codePoint <= 0xdfff) {
            valid = false;
            break;
        }
        valid = appendUtf8(result, codePoint);
    }
    env->ReleaseStringChars(value, chars);
    return valid;
}

jobject makeStreamInfo(JNIEnv* env, const sonar::core::StreamInfo& info) noexcept {
    std::lock_guard<std::mutex> lock(gStreamInfoMutex);
    if (gStreamInfoClass == nullptr) {
        jclass local = env->FindClass("com/sonar/core/StreamInfo");
        if (local == nullptr || env->ExceptionCheck()) {
            clearException(env);
            return nullptr;
        }
        gStreamInfoClass = static_cast<jclass>(env->NewGlobalRef(local));
        env->DeleteLocalRef(local);
        if (gStreamInfoClass == nullptr || env->ExceptionCheck()) {
            clearException(env);
            return nullptr;
        }
        gStreamInfoConstructor = env->GetMethodID(gStreamInfoClass, "<init>", "(IIJILjava/lang/String;)V");
        if (gStreamInfoConstructor == nullptr || env->ExceptionCheck()) {
            clearException(env);
            return nullptr;
        }
    }
    jstring codec = env->NewStringUTF(info.codec.c_str());
    if (codec == nullptr || env->ExceptionCheck()) {
        clearException(env);
        return nullptr;
    }
    jobject object = env->NewObject(gStreamInfoClass, gStreamInfoConstructor,
                                    info.sampleRate, info.channels,
                                    static_cast<jlong>(info.durationMs), info.sourceBitDepth, codec);
    env->DeleteLocalRef(codec);
    clearException(env);
    return object;
}

template <typename Function>
jint handleResult(JNIEnv* env, jlong raw, Function&& function) noexcept {
    try {
        std::lock_guard<std::mutex> lock(gHandlesMutex);
        auto* handle = reinterpret_cast<NativeHandle*>(static_cast<std::uintptr_t>(raw));
        if (raw == 0 || gHandles.find(handle) == gHandles.end() || handle->magic != NativeHandle::kMagic) {
            return static_cast<jint>(ErrorCode::ERR_INVALID_STATE);
        }
        return static_cast<jint>(function(handle->engine));
    } catch (...) {
        clearException(env);
        return static_cast<jint>(ErrorCode::ERR_INTERNAL);
    }
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_sonar_core_NativeBridge_nativeCreateEngine(JNIEnv* env, jclass, jint sampleRate,
                                                     jint outputEncoding, jint channels) noexcept {
    try {
        if (sampleRate <= 0 || channels < 1 || channels > 2 ||
            sonar::core::FormatConverter::bytesPerSample(outputEncoding) == 0) return 0;
        auto* handle = new NativeHandle(sampleRate, outputEncoding, channels);
        std::lock_guard<std::mutex> lock(gHandlesMutex);
        gHandles.insert(handle);
        return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(handle));
    } catch (...) {
        clearException(env);
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sonar_core_NativeBridge_nativeDestroyEngine(JNIEnv* env, jclass, jlong raw) noexcept {
    try {
        std::lock_guard<std::mutex> lock(gHandlesMutex);
        auto* handle = reinterpret_cast<NativeHandle*>(static_cast<std::uintptr_t>(raw));
        const auto found = gHandles.find(handle);
        if (raw == 0 || found == gHandles.end()) return;
        gHandles.erase(found);
        handle->magic = 0;
        delete handle;
    } catch (...) {
        clearException(env);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeOpen(JNIEnv* env, jclass, jlong handle, jstring path) noexcept {
    std::string utf8;
    if (!toUtf8(env, path, utf8)) return static_cast<jint>(ErrorCode::ERR_FILE_NOT_FOUND);
    return handleResult(env, handle, [&utf8](PlaybackEngine& engine) { return engine.open(utf8); });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeOpenFd(JNIEnv* env, jclass, jlong handle, jint fd) noexcept {
    if (fd < 0) return static_cast<jint>(ErrorCode::ERR_FILE_NOT_FOUND);
    return handleResult(env, handle, [fd](PlaybackEngine& engine) { return engine.openFd(fd); });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativePlay(JNIEnv* env, jclass, jlong handle) noexcept {
    return handleResult(env, handle, [](PlaybackEngine& engine) { return engine.play(); });
}
extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativePause(JNIEnv* env, jclass, jlong handle) noexcept {
    return handleResult(env, handle, [](PlaybackEngine& engine) { return engine.pause(); });
}
extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeResume(JNIEnv* env, jclass, jlong handle) noexcept {
    return handleResult(env, handle, [](PlaybackEngine& engine) { return engine.resume(); });
}
extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeStop(JNIEnv* env, jclass, jlong handle) noexcept {
    return handleResult(env, handle, [](PlaybackEngine& engine) { return engine.stop(); });
}
extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeSeek(JNIEnv* env, jclass, jlong handle, jlong positionMs) noexcept {
    return handleResult(env, handle, [positionMs](PlaybackEngine& engine) { return engine.seek(positionMs); });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeReadPcm(JNIEnv* env, jclass, jlong raw, jobject buffer,
                                                jint maxFrames) noexcept {
    try {
        if (maxFrames <= 0 || buffer == nullptr) return static_cast<jint>(ErrorCode::ERR_INVALID_STATE);
        std::lock_guard<std::mutex> lock(gHandlesMutex);
        auto* handle = reinterpret_cast<NativeHandle*>(static_cast<std::uintptr_t>(raw));
        if (raw == 0 || gHandles.find(handle) == gHandles.end() || handle->magic != NativeHandle::kMagic) {
            return static_cast<jint>(ErrorCode::ERR_INVALID_STATE);
        }
        void* address = env->GetDirectBufferAddress(buffer);
        const jlong capacity = env->GetDirectBufferCapacity(buffer);
        if (address == nullptr || capacity <= 0) return static_cast<jint>(ErrorCode::ERR_INVALID_STATE);
        const auto info = handle->engine.streamInfo();
        const auto bytes = sonar::core::FormatConverter::bytesPerSample(handle->engine.outputEncoding());
        if (info.channels <= 0 || bytes == 0 ||
            static_cast<std::uint64_t>(maxFrames) * static_cast<std::uint64_t>(info.channels) * bytes >
                static_cast<std::uint64_t>(capacity)) {
            return static_cast<jint>(ErrorCode::ERR_INVALID_STATE);
        }
        const auto frames = handle->engine.readPcm(address, static_cast<std::size_t>(capacity),
                                                   static_cast<std::size_t>(maxFrames));
        return frames > static_cast<std::size_t>(std::numeric_limits<jint>::max())
                   ? std::numeric_limits<jint>::max() : static_cast<jint>(frames);
    } catch (...) {
        clearException(env);
        return static_cast<jint>(ErrorCode::ERR_INTERNAL);
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_sonar_core_NativeBridge_nativeGetStreamInfo(JNIEnv* env, jclass, jlong raw) noexcept {
    try {
        std::lock_guard<std::mutex> lock(gHandlesMutex);
        auto* handle = reinterpret_cast<NativeHandle*>(static_cast<std::uintptr_t>(raw));
        if (raw == 0 || gHandles.find(handle) == gHandles.end() || handle->magic != NativeHandle::kMagic) return nullptr;
        return makeStreamInfo(env, handle->engine.streamInfo());
    } catch (...) {
        clearException(env);
        return nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeGetState(JNIEnv*, jclass, jlong raw) noexcept {
    std::lock_guard<std::mutex> lock(gHandlesMutex);
    auto* handle = reinterpret_cast<NativeHandle*>(static_cast<std::uintptr_t>(raw));
    if (raw == 0 || gHandles.find(handle) == gHandles.end() || handle->magic != NativeHandle::kMagic) {
        return sonar::core::stateValue(sonar::core::PlayerState::ERROR);
    }
    return sonar::core::stateValue(handle->engine.state());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_sonar_core_NativeBridge_nativeGetPosition(JNIEnv*, jclass, jlong raw) noexcept {
    std::lock_guard<std::mutex> lock(gHandlesMutex);
    auto* handle = reinterpret_cast<NativeHandle*>(static_cast<std::uintptr_t>(raw));
    if (raw == 0 || gHandles.find(handle) == gHandles.end() || handle->magic != NativeHandle::kMagic) return 0;
    return static_cast<jlong>(handle->engine.positionMs());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sonar_core_NativeBridge_nativeGetError(JNIEnv* env, jclass, jlong raw) noexcept {
    try {
        std::lock_guard<std::mutex> lock(gHandlesMutex);
        auto* handle = reinterpret_cast<NativeHandle*>(static_cast<std::uintptr_t>(raw));
        if (raw == 0 || gHandles.find(handle) == gHandles.end() || handle->magic != NativeHandle::kMagic) return nullptr;
        const std::string message = handle->engine.errorMessage();
        if (message.empty()) return nullptr;
        jstring result = env->NewStringUTF(message.c_str());
        clearException(env);
        return result;
    } catch (...) {
        clearException(env);
        return nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sonar_core_NativeBridge_nativeSetOutputFormat(JNIEnv* env, jclass, jlong handle,
                                                        jint encoding) noexcept {
    return handleResult(env, handle, [encoding](PlaybackEngine& engine) {
        return engine.setOutputFormat(encoding);
    });
}
