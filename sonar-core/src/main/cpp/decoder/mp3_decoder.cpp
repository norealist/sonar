#include "mp3_decoder.h"

#include <cstdio>
#include <memory>

#if defined(_WIN32)
#include <io.h>
inline int sonar_dup(int fd) { return _dup(fd); }
inline int sonar_close_fd(int fd) { return _close(fd); }
#else
#include <unistd.h>
inline int sonar_dup(int fd) { return ::dup(fd); }
inline int sonar_close_fd(int fd) { return ::close(fd); }
#endif

#if defined(SONAR_HAS_MINIMP3)
#define MINIMP3_FLOAT_OUTPUT
#define MINIMP3_IMPLEMENTATION
#include "minimp3_ex.h"

namespace {
size_t mp3ReadCb(void* buf, size_t size, void* user_data) {
    return std::fread(buf, 1, size, static_cast<FILE*>(user_data));
}
int mp3SeekCb(uint64_t position, void* user_data) {
    return std::fseek(static_cast<FILE*>(user_data), static_cast<long>(position), SEEK_SET);
}
} // namespace
#endif

namespace sonar::core {

struct Mp3Decoder::Impl {
#if defined(SONAR_HAS_MINIMP3)
    mp3dec_ex_t decoder{};
    mp3dec_io_t io{};
    FILE* file = nullptr;
    bool open = false;
#endif
};

Mp3Decoder::Mp3Decoder() : impl_(std::make_unique<Impl>()) {}
Mp3Decoder::~Mp3Decoder() { close(); }

ErrorCode Mp3Decoder::open(const std::string& path) {
    close();
#if defined(SONAR_HAS_MINIMP3)
    FILE* file = std::fopen(path.c_str(), "rb");
    if (file == nullptr) return ErrorCode::ERR_FILE_NOT_FOUND;
    return openFile(file);
#else
    (void)path;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

ErrorCode Mp3Decoder::openFd(int fd) {
    close();
#if defined(SONAR_HAS_MINIMP3)
    if (fd < 0) return ErrorCode::ERR_FILE_NOT_FOUND;
    int dupFd = sonar_dup(fd);
    if (dupFd < 0) return ErrorCode::ERR_FILE_NOT_FOUND;
    FILE* file = fdopen(dupFd, "rb");
    if (file == nullptr) {
        sonar_close_fd(dupFd);
        return ErrorCode::ERR_FILE_READ;
    }
    return openFile(file);
#else
    (void)fd;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

ErrorCode Mp3Decoder::openFile(FILE* file) {
#if defined(SONAR_HAS_MINIMP3)
    impl_->file = file;
    impl_->io.read = mp3ReadCb;
    impl_->io.read_data = file;
    impl_->io.seek = mp3SeekCb;
    impl_->io.seek_data = file;
    if (mp3dec_ex_open_cb(&impl_->decoder, &impl_->io, MP3D_SEEK_TO_SAMPLE) != 0) {
        close();
        return ErrorCode::ERR_DECODER_INIT;
    }
    impl_->open = true;
    if (impl_->decoder.info.hz <= 0 || impl_->decoder.info.channels < 1 ||
        impl_->decoder.info.channels > 2) {
        close();
        return ErrorCode::ERR_UNSUPPORTED_FORMAT;
    }
    info_.sampleRate = impl_->decoder.info.hz;
    info_.channels = impl_->decoder.info.channels;
    info_.sourceBitDepth = 16;
    info_.durationMs = impl_->decoder.samples > 0
                           ? static_cast<std::int64_t>(impl_->decoder.samples * 1000LL /
                                                        info_.sampleRate / info_.channels)
                           : -1;
    info_.codec = "mp3";
    positionSamples_ = 0;
    return ErrorCode::OK;
#else
    if (file != nullptr) std::fclose(file);
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

DecodeResult Mp3Decoder::decodeNextFrame(float* output, std::size_t maxFrames) {
#if defined(SONAR_HAS_MINIMP3)
    if (!impl_->open || output == nullptr || maxFrames == 0) {
        return {impl_->open ? ErrorCode::ERR_INTERNAL : ErrorCode::ERR_INVALID_STATE, 0, false};
    }
    const std::size_t samples = mp3dec_ex_read(&impl_->decoder, output,
                                               maxFrames * static_cast<std::size_t>(info_.channels));
    if (samples == 0) return {ErrorCode::OK, 0, true};
    const std::size_t frames = samples / static_cast<std::size_t>(info_.channels);
    positionSamples_ += samples;
    return {ErrorCode::OK, frames, false};
#else
    (void)output;
    (void)maxFrames;
    return {ErrorCode::ERR_UNSUPPORTED_FORMAT, 0, false};
#endif
}

ErrorCode Mp3Decoder::seek(std::int64_t positionMs) {
#if defined(SONAR_HAS_MINIMP3)
    if (!impl_->open || positionMs < 0) return ErrorCode::ERR_SEEK_FAILED;
    const auto sample = static_cast<std::uint64_t>(positionMs) * info_.sampleRate / 1000ULL *
                        static_cast<std::uint64_t>(info_.channels);
    if (mp3dec_ex_seek(&impl_->decoder, sample) != 0) return ErrorCode::ERR_SEEK_FAILED;
    positionSamples_ = sample;
    return ErrorCode::OK;
#else
    (void)positionMs;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

void Mp3Decoder::close() noexcept {
#if defined(SONAR_HAS_MINIMP3)
    if (impl_ && impl_->open) {
        mp3dec_ex_close(&impl_->decoder);
        impl_->open = false;
    }
    if (impl_ && impl_->file) {
        std::fclose(impl_->file);
        impl_->file = nullptr;
    }
#endif
    info_ = {};
    positionSamples_ = 0;
}

std::int64_t Mp3Decoder::positionMs() const noexcept {
    return info_.sampleRate > 0
               ? static_cast<std::int64_t>((positionSamples_ / static_cast<std::uint64_t>(info_.channels)) *
                                            1000ULL / static_cast<std::uint64_t>(info_.sampleRate))
               : 0;
}

} // namespace sonar::core
