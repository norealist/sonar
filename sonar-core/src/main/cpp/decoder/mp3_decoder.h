#pragma once

#include "i_decoder.h"

#include <memory>

namespace sonar::core {

class Mp3Decoder final : public IDecoder {
public:
    Mp3Decoder();
    ~Mp3Decoder() override;

    ErrorCode open(const std::string& path) override;
    DecodeResult decodeNextFrame(float* interleavedOutput, std::size_t maxFrames) override;
    ErrorCode seek(std::int64_t positionMs) override;
    void close() noexcept override;
    const StreamInfo& getStreamInfo() const noexcept override { return info_; }
    std::int64_t positionMs() const noexcept override;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    StreamInfo info_{};
    std::uint64_t positionSamples_ = 0;
};

} // namespace sonar::core
