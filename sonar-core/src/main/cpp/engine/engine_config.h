#pragma once

#include <cstddef>

namespace sonar::core {

struct EngineConfig {
    std::size_t ringDurationMs = 200;
    std::size_t prebufferPercent = 50;
    std::size_t maxConsecutiveDecodeErrors = 50;
    std::size_t decodeChunkFrames = 1024;
};

constexpr std::size_t kDefaultRingDurationMs = 200;
constexpr std::size_t kDefaultPrebufferPercent = 50;
constexpr std::size_t kDefaultMaxConsecutiveDecodeErrors = 50;

} // namespace sonar::core
