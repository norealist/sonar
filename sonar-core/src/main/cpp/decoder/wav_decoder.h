#pragma once

#include "i_decoder.h"

#include <cstdio>
#include <cstdint>
#include <string>

namespace sonar::core {

class WavDecoder final : public IDecoder {
public:
    WavDecoder() = default;
    ~WavDecoder() override { close(); }

    ErrorCode open(const std::string& path) override;
    ErrorCode openFd(int fd) override;
    ErrorCode openFile(FILE* file);
    DecodeResult decodeNextFrame(float* interleavedOutput, std::size_t maxFrames) override;
    ErrorCode seek(std::int64_t positionMs) override;
    void close() noexcept override;
    const StreamInfo& getStreamInfo() const noexcept override { return info_; }
    std::int64_t positionMs() const noexcept override;

private:
    enum class SampleType { PcmInteger, IeeeFloat };

    FILE* file_ = nullptr;
    StreamInfo info_{};
    SampleType sampleType_ = SampleType::PcmInteger;
    std::uint16_t bitsPerSample_ = 0;
    std::uint16_t blockAlign_ = 0;
    std::uint64_t dataOffset_ = 0;
    std::uint64_t dataBytes_ = 0;
    std::uint64_t framePosition_ = 0;
    bool opened_ = false;
};

} // namespace sonar::core
