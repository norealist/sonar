#pragma once

#include <cstddef>

namespace sonar::core {

struct EngineConfig {
    std::size_t ringDurationMs = 1000;
    std::size_t prebufferPercent = 25;
    std::size_t maxConsecutiveDecodeErrors = 50;
    std::size_t decodeChunkFrames = 2048;
};

constexpr std::size_t kDefaultRingDurationMs = 1000;
constexpr std::size_t kDefaultPrebufferPercent = 25;
constexpr std::size_t kDefaultMaxConsecutiveDecodeErrors = 50;

} // namespace sonar::core
