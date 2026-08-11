#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

namespace sonar::core {

enum class PlayerState : std::int32_t {
    IDLE = 0,
    OPENED = 1,
    BUFFERING = 2,
    PLAYING = 3,
    PAUSED = 4,
    COMPLETED = 5,
    ERROR = 6,
};

enum class ErrorCode : std::int32_t {
    OK = 0,
    ERR_FILE_NOT_FOUND = -1,
    ERR_FILE_READ = -2,
    ERR_UNSUPPORTED_FORMAT = -3,
    ERR_DECODER_INIT = -4,
    ERR_DECODER_DECODE = -5,
    ERR_INVALID_STATE = -6,
    ERR_SEEK_FAILED = -7,
    ERR_OUTPUT_FORMAT = -8,
    ERR_INTERNAL = -9,
};

// Android AudioFormat encoding values are kept here so the JNI contract does
// not reinterpret an encoding constant as a bit count.
constexpr std::int32_t ENCODING_PCM_16BIT = 2;
constexpr std::int32_t ENCODING_PCM_8BIT = 3;
constexpr std::int32_t ENCODING_PCM_FLOAT = 4;
constexpr std::int32_t ENCODING_PCM_32BIT = 0x40000000;
constexpr std::int32_t ENCODING_PCM_24BIT_PACKED = static_cast<std::int32_t>(0x80000000u);

struct StreamInfo {
    std::int32_t sampleRate = 0;
    std::int32_t channels = 0;
    std::int64_t durationMs = -1;
    std::int32_t sourceBitDepth = 0;
    std::string codec;
};

struct DecodeResult {
    ErrorCode error = ErrorCode::OK;
    std::size_t frames = 0;
    bool endOfStream = false;
};

inline constexpr int errorValue(ErrorCode code) noexcept {
    return static_cast<int>(code);
}

inline constexpr int stateValue(PlayerState state) noexcept {
    return static_cast<int>(state);
}

} // namespace sonar::core
