#include "opus_decoder.h"

#include <algorithm>
#include <memory>

#if defined(SONAR_HAS_OPUSFILE)
#include <opusfile.h>
#endif

namespace sonar::core {

struct OpusDecoder::Impl {
#if defined(SONAR_HAS_OPUSFILE)
    OggOpusFile* file = nullptr;
#endif
};

OpusDecoder::OpusDecoder() : impl_(std::make_unique<Impl>()) {}
OpusDecoder::~OpusDecoder() { close(); }

ErrorCode OpusDecoder::open(const std::string& path) {
    close();
#if defined(SONAR_HAS_OPUSFILE)
    int error = 0;
    impl_->file = op_open_file(path.c_str(), &error);
    if (impl_->file == nullptr) return ErrorCode::ERR_DECODER_INIT;
    const OpusHead* head = op_head(impl_->file, -1);
    const int channels = op_channel_count(impl_->file, -1);
    if (head == nullptr || channels < 1 || channels > 2) {
        close();
        return head == nullptr ? ErrorCode::ERR_UNSUPPORTED_FORMAT
                               : ErrorCode::ERR_UNSUPPORTED_FORMAT;
    }
    const ogg_int64_t total = op_pcm_total(impl_->file, -1);
    info_.sampleRate = 48000;
    info_.channels = channels;
    info_.sourceBitDepth = 16;
    info_.durationMs = total >= 0 ? static_cast<std::int64_t>(total * 1000 / 48000) : -1;
    info_.codec = "opus";
    positionFrames_ = 0;
    return ErrorCode::OK;
#else
    (void)path;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

DecodeResult OpusDecoder::decodeNextFrame(float* output, std::size_t maxFrames) {
#if defined(SONAR_HAS_OPUSFILE)
    if (impl_->file == nullptr || output == nullptr || maxFrames == 0) {
        return {impl_->file != nullptr ? ErrorCode::ERR_INTERNAL : ErrorCode::ERR_INVALID_STATE, 0, false};
    }
    int link = 0;
    const int frames = op_read_float(impl_->file, output,
                                     static_cast<int>(std::min<std::size_t>(maxFrames, 4096)), &link);
    if (frames == 0) return {ErrorCode::OK, 0, true};
    if (frames < 0) return {ErrorCode::ERR_DECODER_DECODE, 0, false};
    positionFrames_ += frames;
    return {ErrorCode::OK, static_cast<std::size_t>(frames), false};
#else
    (void)output;
    (void)maxFrames;
    return {ErrorCode::ERR_UNSUPPORTED_FORMAT, 0, false};
#endif
}

ErrorCode OpusDecoder::seek(std::int64_t positionMs) {
#if defined(SONAR_HAS_OPUSFILE)
    if (impl_->file == nullptr || positionMs < 0) return ErrorCode::ERR_SEEK_FAILED;
    const auto frame = static_cast<ogg_int64_t>(positionMs) * 48000 / 1000;
    if (op_pcm_seek(impl_->file, frame) != 0) return ErrorCode::ERR_SEEK_FAILED;
    positionFrames_ = frame;
    return ErrorCode::OK;
#else
    (void)positionMs;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

void OpusDecoder::close() noexcept {
#if defined(SONAR_HAS_OPUSFILE)
    if (impl_ && impl_->file != nullptr) op_free(impl_->file);
    if (impl_) impl_->file = nullptr;
#endif
    info_ = {};
    positionFrames_ = 0;
}

std::int64_t OpusDecoder::positionMs() const noexcept {
    return static_cast<std::int64_t>(positionFrames_ * 1000 / 48000);
}

} // namespace sonar::core
