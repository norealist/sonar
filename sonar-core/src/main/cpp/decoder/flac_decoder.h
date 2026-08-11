#pragma once

#include "i_decoder.h"

#include <memory>

namespace sonar::core {

class FlacDecoder final : public IDecoder {
public:
    struct Impl;

    FlacDecoder();
    ~FlacDecoder() override;

    ErrorCode open(const std::string& path) override;
    DecodeResult decodeNextFrame(float* interleavedOutput, std::size_t maxFrames) override;
    ErrorCode seek(std::int64_t positionMs) override;
    void close() noexcept override;
    const StreamInfo& getStreamInfo() const noexcept override { return info_; }
    std::int64_t positionMs() const noexcept override;

private:
    std::unique_ptr<Impl> impl_;
    StreamInfo info_{};
    std::int64_t positionFrames_ = 0;
};

} // namespace sonar::core
