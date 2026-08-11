#include "vorbis_decoder.h"

#include <algorithm>

#if defined(SONAR_HAS_VORBIS)
#include <vorbis/vorbisfile.h>
#endif

namespace sonar::core {

struct VorbisDecoder::Impl {
#if defined(SONAR_HAS_VORBIS)
    OggVorbis_File file{};
    bool opened = false;
#endif
};

VorbisDecoder::VorbisDecoder() : impl_(std::make_unique<Impl>()) {}
VorbisDecoder::~VorbisDecoder() { close(); }

ErrorCode VorbisDecoder::open(const std::string& path) {
    close();
#if defined(SONAR_HAS_VORBIS)
    if (ov_fopen(path.c_str(), &impl_->file) < 0) return ErrorCode::ERR_DECODER_INIT;
    impl_->opened = true;
    const vorbis_info* stream = ov_info(&impl_->file, -1);
    const ogg_int64_t total = ov_pcm_total(&impl_->file, -1);
    if (stream == nullptr || stream->rate <= 0 || stream->channels < 1 || stream->channels > 2) {
        close();
        return ErrorCode::ERR_UNSUPPORTED_FORMAT;
    }
    info_.sampleRate = stream->rate;
    info_.channels = stream->channels;
    info_.sourceBitDepth = 16;
    info_.durationMs = total >= 0
                           ? static_cast<std::int64_t>(total * 1000 / stream->rate)
                           : -1;
    info_.codec = "vorbis";
    positionFrames_ = 0;
    return ErrorCode::OK;
#else
    (void)path;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

DecodeResult VorbisDecoder::decodeNextFrame(float* output, std::size_t maxFrames) {
#if defined(SONAR_HAS_VORBIS)
    if (!impl_->opened || output == nullptr || maxFrames == 0) {
        return {impl_->opened ? ErrorCode::ERR_INTERNAL : ErrorCode::ERR_INVALID_STATE, 0, false};
    }
    float** channels = nullptr;
    int bitstream = 0;
    const long frames = ov_read_float(
        &impl_->file, &channels, static_cast<int>(std::min<std::size_t>(maxFrames, 4096)), &bitstream);
    if (frames == 0) return {ErrorCode::OK, 0, true};
    if (frames < 0 || channels == nullptr) return {ErrorCode::ERR_DECODER_DECODE, 0, false};
    for (long frame = 0; frame < frames; ++frame) {
        for (std::int32_t channel = 0; channel < info_.channels; ++channel) {
            output[frame * info_.channels + channel] = channels[channel][frame];
        }
    }
    positionFrames_ += frames;
    return {ErrorCode::OK, static_cast<std::size_t>(frames), false};
#else
    (void)output;
    (void)maxFrames;
    return {ErrorCode::ERR_UNSUPPORTED_FORMAT, 0, false};
#endif
}

ErrorCode VorbisDecoder::seek(std::int64_t positionMs) {
#if defined(SONAR_HAS_VORBIS)
    if (!impl_->opened || positionMs < 0) return ErrorCode::ERR_SEEK_FAILED;
    const auto frame = static_cast<ogg_int64_t>(positionMs) * info_.sampleRate / 1000;
    if (ov_pcm_seek(&impl_->file, frame) != 0) return ErrorCode::ERR_SEEK_FAILED;
    positionFrames_ = frame;
    return ErrorCode::OK;
#else
    (void)positionMs;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

void VorbisDecoder::close() noexcept {
#if defined(SONAR_HAS_VORBIS)
    if (impl_ && impl_->opened) ov_clear(&impl_->file);
    if (impl_) impl_->opened = false;
#endif
    info_ = {};
    positionFrames_ = 0;
}

std::int64_t VorbisDecoder::positionMs() const noexcept {
    return info_.sampleRate > 0
               ? static_cast<std::int64_t>(positionFrames_ * 1000 / info_.sampleRate)
               : 0;
}

} // namespace sonar::core
