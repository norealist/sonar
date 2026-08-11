#pragma once

#include "../engine/engine_types.h"

#include <cstddef>
#include <cstdint>

namespace sonar::core {

class FormatConverter final {
public:
    static std::size_t bytesPerSample(std::int32_t encoding) noexcept;
    static std::size_t convert(const float* input, std::size_t frames,
                               std::size_t channels, std::int32_t encoding,
                               void* output, std::size_t outputBytes) noexcept;

private:
    static float clamp(float value) noexcept;
};

} // namespace sonar::core
