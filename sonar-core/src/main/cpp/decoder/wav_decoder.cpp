#include "wav_decoder.h"

#include <algorithm>
#include <array>
#include <cerrno>
#include <cmath>
#include <cstring>
#include <limits>

#if defined(_WIN32)
#include <io.h>
inline int sonar_dup(int fd) { return _dup(fd); }
inline int sonar_close_fd(int fd) { return _close(fd); }
#else
#include <unistd.h>
inline int sonar_dup(int fd) { return ::dup(fd); }
inline int sonar_close_fd(int fd) { return ::close(fd); }
#endif

namespace sonar::core {
namespace {

std::uint16_t readLe16(const std::uint8_t* p) noexcept {
    return static_cast<std::uint16_t>(p[0] | (static_cast<std::uint16_t>(p[1]) << 8));
}

std::uint32_t readLe32(const std::uint8_t* p) noexcept {
    return static_cast<std::uint32_t>(p[0]) |
           (static_cast<std::uint32_t>(p[1]) << 8) |
           (static_cast<std::uint32_t>(p[2]) << 16) |
           (static_cast<std::uint32_t>(p[3]) << 24);
}

std::int32_t signExtend24(const std::uint8_t* p) noexcept {
    std::int32_t value = static_cast<std::int32_t>(p[0]) |
                         (static_cast<std::int32_t>(p[1]) << 8) |
                         (static_cast<std::int32_t>(p[2]) << 16);
    if ((value & 0x00800000) != 0) value |= static_cast<std::int32_t>(0xff000000);
    return value;
}

bool fourcc(const std::uint8_t* p, const char* value) noexcept {
    return p[0] == static_cast<std::uint8_t>(value[0]) &&
           p[1] == static_cast<std::uint8_t>(value[1]) &&
           p[2] == static_cast<std::uint8_t>(value[2]) &&
           p[3] == static_cast<std::uint8_t>(value[3]);
}

} // namespace

ErrorCode WavDecoder::open(const std::string& path) {
    close();
    FILE* file = std::fopen(path.c_str(), "rb");
    if (file == nullptr) return ErrorCode::ERR_FILE_NOT_FOUND;
    return openFile(file);
}

ErrorCode WavDecoder::openFd(int fd) {
    close();
    if (fd < 0) return ErrorCode::ERR_FILE_NOT_FOUND;
    int dupFd = sonar_dup(fd);
    if (dupFd < 0) return ErrorCode::ERR_FILE_NOT_FOUND;
    FILE* file = fdopen(dupFd, "rb");
    if (file == nullptr) {
        sonar_close_fd(dupFd);
        return ErrorCode::ERR_FILE_READ;
    }
    return openFile(file);
}

ErrorCode WavDecoder::openFile(FILE* file) {
    file_ = file;
    if (std::fseek(file_, 0, SEEK_END) != 0) {
        close();
        return ErrorCode::ERR_FILE_READ;
    }
    const long end = std::ftell(file_);
    if (end < 12 || std::fseek(file_, 0, SEEK_SET) != 0) {
        close();
        return ErrorCode::ERR_FILE_READ;
    }
    const std::uint64_t fileSize = static_cast<std::uint64_t>(end);
    std::array<std::uint8_t, 12> header{};
    if (std::fread(header.data(), 1, header.size(), file_) != header.size()) {
        close();
        return ErrorCode::ERR_FILE_READ;
    }
    if (!fourcc(header.data(), "RIFF") || !fourcc(header.data() + 8, "WAVE")) {
        close();
        return ErrorCode::ERR_UNSUPPORTED_FORMAT;
    }
    const std::uint64_t riffEnd = 8ULL + readLe32(header.data() + 4);
    if (riffEnd > fileSize || riffEnd < 12) {
        close();
        return ErrorCode::ERR_FILE_READ;
    }

    bool haveFmt = false;
    bool haveData = false;
    std::uint32_t sampleRate = 0;
    std::uint16_t channels = 0;
    std::uint16_t formatTag = 0;
    std::uint64_t cursor = 12;
    while (cursor + 8 <= riffEnd) {
        if (std::fseek(file_, static_cast<long>(cursor), SEEK_SET) != 0) {
            close();
            return ErrorCode::ERR_FILE_READ;
        }
        std::array<std::uint8_t, 8> chunkHeader{};
        if (std::fread(chunkHeader.data(), 1, chunkHeader.size(), file_) != chunkHeader.size()) {
            close();
            return ErrorCode::ERR_FILE_READ;
        }
        const std::uint32_t chunkSize = readLe32(chunkHeader.data() + 4);
        const std::uint64_t chunkData = cursor + 8;
        const std::uint64_t next = chunkData + chunkSize + (chunkSize & 1U);
        if (next > riffEnd || next < chunkData) {
            close();
            return ErrorCode::ERR_FILE_READ;
        }
        if (fourcc(chunkHeader.data(), "fmt ")) {
            if (chunkSize < 16 || std::fseek(file_, static_cast<long>(chunkData), SEEK_SET) != 0) {
                close();
                return ErrorCode::ERR_FILE_READ;
            }
            std::array<std::uint8_t, 16> fmt{};
            if (std::fread(fmt.data(), 1, fmt.size(), file_) != fmt.size()) {
                close();
                return ErrorCode::ERR_FILE_READ;
            }
            formatTag = readLe16(fmt.data());
            channels = readLe16(fmt.data() + 2);
            sampleRate = readLe32(fmt.data() + 4);
            const std::uint32_t byteRate = readLe32(fmt.data() + 8);
            blockAlign_ = readLe16(fmt.data() + 12);
            bitsPerSample_ = readLe16(fmt.data() + 14);
            if ((formatTag != 1 && formatTag != 3) || channels == 0 || channels > 2 ||
                sampleRate == 0 || blockAlign_ == 0 ||
                (formatTag == 3 && bitsPerSample_ != 32) ||
                (formatTag == 1 && bitsPerSample_ != 8 && bitsPerSample_ != 16 &&
                 bitsPerSample_ != 24 && bitsPerSample_ != 32)) {
                close();
                return ErrorCode::ERR_UNSUPPORTED_FORMAT;
            }
            const std::uint32_t expectedAlign =
                static_cast<std::uint32_t>(channels) * ((bitsPerSample_ + 7U) / 8U);
            if (blockAlign_ != expectedAlign) {
                // A number of real-world WAV writers leave blockAlign at the
                // mono value while writing correct stereo data. Recover only
                // when byteRate independently confirms the format; truncated
                // or structurally inconsistent files remain rejected below.
                const std::uint64_t expectedByteRate =
                    static_cast<std::uint64_t>(sampleRate) * expectedAlign;
                if (byteRate != expectedByteRate) {
                    close();
                    return ErrorCode::ERR_FILE_READ;
                }
                blockAlign_ = static_cast<std::uint16_t>(expectedAlign);
            }
            sampleType_ = formatTag == 3 ? SampleType::IeeeFloat : SampleType::PcmInteger;
            haveFmt = true;
        } else if (fourcc(chunkHeader.data(), "data")) {
            if (!haveData) {
                dataOffset_ = chunkData;
                dataBytes_ = chunkSize;
                haveData = true;
            }
        }
        cursor = next;
    }
    if (cursor != riffEnd || !haveFmt || !haveData || dataBytes_ < blockAlign_ ||
        dataBytes_ % blockAlign_ != 0) {
        close();
        return ErrorCode::ERR_FILE_READ;
    }
    info_.sampleRate = static_cast<std::int32_t>(sampleRate);
    info_.channels = static_cast<std::int32_t>(channels);
    info_.sourceBitDepth = bitsPerSample_;
    info_.durationMs = static_cast<std::int64_t>((dataBytes_ / blockAlign_) * 1000ULL / sampleRate);
    info_.codec = "wav";
    framePosition_ = 0;
    opened_ = true;
    return ErrorCode::OK;
}

DecodeResult WavDecoder::decodeNextFrame(float* output, std::size_t maxFrames) {
    if (!opened_ || output == nullptr || maxFrames == 0) {
        return {opened_ ? ErrorCode::ERR_INTERNAL : ErrorCode::ERR_INVALID_STATE, 0, false};
    }
    const std::uint64_t totalFrames = dataBytes_ / blockAlign_;
    if (framePosition_ >= totalFrames) return {ErrorCode::OK, 0, true};
    const std::size_t frames = static_cast<std::size_t>(std::min<std::uint64_t>(
        maxFrames, totalFrames - framePosition_));
    const std::size_t bytes = frames * blockAlign_;
    std::array<std::uint8_t, 4 * 2 * 1024> local{};
    std::size_t done = 0;
    while (done < bytes) {
        std::size_t part = std::min(local.size(), bytes - done);
        part -= part % blockAlign_;
        if (part == 0) return {ErrorCode::ERR_INTERNAL, 0, false};
        if (std::fseek(file_, static_cast<long>(dataOffset_ + framePosition_ * blockAlign_ + done), SEEK_SET) != 0 ||
            std::fread(local.data(), 1, part, file_) != part) {
            return {ErrorCode::ERR_FILE_READ, 0, false};
        }
        for (std::size_t offset = 0; offset < part; offset += blockAlign_) {
            const std::size_t frame = (done + offset) / blockAlign_;
            for (std::size_t channel = 0; channel < static_cast<std::size_t>(info_.channels); ++channel) {
                const auto* sample = local.data() + offset + channel * ((bitsPerSample_ + 7U) / 8U);
                float value = 0.0f;
                if (sampleType_ == SampleType::IeeeFloat) {
                    std::memcpy(&value, sample, sizeof(value));
                } else if (bitsPerSample_ == 8) {
                    value = (static_cast<int>(*sample) - 128) / 128.0f;
                } else if (bitsPerSample_ == 16) {
                    value = static_cast<float>(static_cast<std::int16_t>(readLe16(sample))) / 32768.0f;
                } else if (bitsPerSample_ == 24) {
                    value = static_cast<float>(signExtend24(sample)) / 8388608.0f;
                } else {
                    value = static_cast<float>(static_cast<std::int32_t>(readLe32(sample))) / 2147483648.0f;
                }
                output[frame * info_.channels + channel] = value;
            }
        }
        done += part;
    }
    framePosition_ += frames;
    return {ErrorCode::OK, frames, framePosition_ >= totalFrames};
}

ErrorCode WavDecoder::seek(std::int64_t positionMs) {
    if (!opened_ || positionMs < 0) return ErrorCode::ERR_SEEK_FAILED;
    const auto totalFrames = dataBytes_ / blockAlign_;
    const std::uint64_t requested = static_cast<std::uint64_t>(positionMs) *
                                    static_cast<std::uint64_t>(info_.sampleRate) / 1000U;
    framePosition_ = std::min<std::uint64_t>(totalFrames, requested);
    return ErrorCode::OK;
}

void WavDecoder::close() noexcept {
    if (file_ != nullptr) std::fclose(file_);
    file_ = nullptr;
    opened_ = false;
    framePosition_ = 0;
    dataOffset_ = 0;
    dataBytes_ = 0;
    info_ = {};
}

std::int64_t WavDecoder::positionMs() const noexcept {
    return info_.sampleRate > 0
               ? static_cast<std::int64_t>(framePosition_ * 1000ULL / info_.sampleRate)
               : 0;
}

} // namespace sonar::core
