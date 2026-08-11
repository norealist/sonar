#pragma once

#include "../engine/engine_types.h"

#include <cstddef>
#include <cstdint>
#include <string>

namespace sonar::core {

class IDecoder {
public:
    virtual ~IDecoder() = default;

    virtual ErrorCode open(const std::string& path) = 0;
    virtual DecodeResult decodeNextFrame(float* interleavedOutput,
                                         std::size_t maxFrames) = 0;
    virtual ErrorCode seek(std::int64_t positionMs) = 0;
    virtual void close() noexcept = 0;
    virtual const StreamInfo& getStreamInfo() const noexcept = 0;
    virtual std::int64_t positionMs() const noexcept = 0;
};

} // namespace sonar::core
