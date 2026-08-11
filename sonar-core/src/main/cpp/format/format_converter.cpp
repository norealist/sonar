#include "format_converter.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace sonar::core {

std::size_t FormatConverter::bytesPerSample(std::int32_t encoding) noexcept {
    switch (encoding) {
        case ENCODING_PCM_FLOAT:
        case ENCODING_PCM_32BIT: return 4;
        case ENCODING_PCM_24BIT_PACKED: return 3;
        case ENCODING_PCM_16BIT: return 2;
        default: return 0;
    }
}

float FormatConverter::clamp(float value) noexcept {
    return std::max(-1.0f, std::min(1.0f, value));
}

std::size_t FormatConverter::convert(const float* input, std::size_t frames,
                                     std::size_t channels, std::int32_t encoding,
                                     void* output, std::size_t outputBytes) noexcept {
    const std::size_t sampleBytes = bytesPerSample(encoding);
    if (input == nullptr || output == nullptr || channels == 0 || sampleBytes == 0) return 0;
    const std::size_t frameBytes = channels * sampleBytes;
    const std::size_t count = std::min(frames, outputBytes / frameBytes);
    auto* bytes = static_cast<std::uint8_t*>(output);
    for (std::size_t i = 0; i < count * channels; ++i) {
        // Float output is the canonical representation and must remain a
        // pass-through. Integer output is the only path that clips samples.
        const float value = encoding == ENCODING_PCM_FLOAT ? input[i] : clamp(input[i]);
        switch (encoding) {
            case ENCODING_PCM_FLOAT:
                std::memcpy(bytes + i * 4, &value, sizeof(value));
                break;
            case ENCODING_PCM_16BIT: {
                const auto sample = static_cast<std::int16_t>(value * 32767.0f);
                std::memcpy(bytes + i * 2, &sample, sizeof(sample));
                break;
            }
            case ENCODING_PCM_24BIT_PACKED: {
                const auto sample = static_cast<std::int32_t>(value * 8388607.0f);
                bytes[i * 3] = static_cast<std::uint8_t>(sample & 0xff);
                bytes[i * 3 + 1] = static_cast<std::uint8_t>((sample >> 8) & 0xff);
                bytes[i * 3 + 2] = static_cast<std::uint8_t>((sample >> 16) & 0xff);
                break;
            }
            case ENCODING_PCM_32BIT: {
                const auto sample = static_cast<std::int32_t>(value * 2147483647.0f);
                std::memcpy(bytes + i * 4, &sample, sizeof(sample));
                break;
            }
            default: return 0;
        }
    }
    return count;
}

} // namespace sonar::core
