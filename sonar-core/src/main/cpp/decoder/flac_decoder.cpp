#include "flac_decoder.h"

#include <algorithm>
#include <cstdio>
#include <memory>
#include <vector>

#if defined(SONAR_HAS_FLAC)
#include <FLAC/stream_decoder.h>
#endif

namespace sonar::core {

struct FlacDecoder::Impl {
#if defined(SONAR_HAS_FLAC)
    FLAC__StreamDecoder* decoder = nullptr;
    FILE* file = nullptr;
    float* output = nullptr;
    std::size_t outputFrames = 0;
    std::size_t writtenFrames = 0;
    std::vector<float> pending;
    std::size_t pendingFrames = 0;
    std::size_t pendingOffset = 0;
    StreamInfo* info = nullptr;
    bool metadataReady = false;
    bool decodeError = false;
#endif
};

#if defined(SONAR_HAS_FLAC)
namespace {
FLAC__StreamDecoderReadStatus readCallback(const FLAC__StreamDecoder*, FLAC__byte* buffer,
                                            size_t* bytes, void* client) {
    auto* impl = static_cast<FlacDecoder::Impl*>(client);
    const std::size_t n = std::fread(buffer, 1, *bytes, impl->file);
    *bytes = n;
    if (n == 0) return std::feof(impl->file) ? FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM
                                             : FLAC__STREAM_DECODER_READ_STATUS_ABORT;
    return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
}
FLAC__StreamDecoderSeekStatus seekCallback(const FLAC__StreamDecoder*, FLAC__uint64 offset, void* client) {
    auto* impl = static_cast<FlacDecoder::Impl*>(client);
    return std::fseek(impl->file, static_cast<long>(offset), SEEK_SET) == 0
               ? FLAC__STREAM_DECODER_SEEK_STATUS_OK : FLAC__STREAM_DECODER_SEEK_STATUS_ERROR;
}
FLAC__StreamDecoderTellStatus tellCallback(const FLAC__StreamDecoder*, FLAC__uint64* offset, void* client) {
    auto* impl = static_cast<FlacDecoder::Impl*>(client);
    const long value = std::ftell(impl->file);
    if (value < 0) return FLAC__STREAM_DECODER_TELL_STATUS_ERROR;
    *offset = static_cast<FLAC__uint64>(value);
    return FLAC__STREAM_DECODER_TELL_STATUS_OK;
}
FLAC__StreamDecoderLengthStatus lengthCallback(const FLAC__StreamDecoder*, FLAC__uint64* length, void* client) {
    auto* impl = static_cast<FlacDecoder::Impl*>(client);
    const long current = std::ftell(impl->file);
    if (current < 0 || std::fseek(impl->file, 0, SEEK_END) != 0) return FLAC__STREAM_DECODER_LENGTH_STATUS_ERROR;
    const long end = std::ftell(impl->file);
    std::fseek(impl->file, current, SEEK_SET);
    if (end < 0) return FLAC__STREAM_DECODER_LENGTH_STATUS_ERROR;
    *length = static_cast<FLAC__uint64>(end);
    return FLAC__STREAM_DECODER_LENGTH_STATUS_OK;
}
FLAC__bool eofCallback(const FLAC__StreamDecoder*, void* client) {
    return std::feof(static_cast<FlacDecoder::Impl*>(client)->file) ? true : false;
}
FLAC__StreamDecoderWriteStatus writeCallback(const FLAC__StreamDecoder*, const FLAC__Frame* frame,
                                              const FLAC__int32* const buffer[], void* client) {
    auto* impl = static_cast<FlacDecoder::Impl*>(client);
    const std::size_t frames = frame->header.blocksize;
    if (impl->info == nullptr || frame->header.channels < 1 || frame->header.channels > 2 ||
        frames > impl->pending.size() / static_cast<std::size_t>(frame->header.channels)) {
        impl->decodeError = true;
        return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    }
    const int channels = frame->header.channels;
    const int bits = frame->header.bits_per_sample;
    if (bits < 1 || bits > 32) {
        impl->decodeError = true;
        return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    }
    const float scale = bits == 32 ? 2147483648.0f : static_cast<float>(std::uint64_t{1} << (bits - 1));
    for (std::size_t frameIndex = 0; frameIndex < frames; ++frameIndex) {
        for (int channel = 0; channel < channels; ++channel) {
            impl->pending[frameIndex * channels + channel] =
                static_cast<float>(buffer[channel][frameIndex]) / scale;
        }
    }
    impl->pendingFrames = frames;
    impl->pendingOffset = 0;
    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}
void metadataCallback(const FLAC__StreamDecoder*, const FLAC__StreamMetadata* metadata, void* client) {
    auto* impl = static_cast<FlacDecoder::Impl*>(client);
    if (metadata->type == FLAC__METADATA_TYPE_STREAMINFO) {
        impl->metadataReady = true;
        impl->info->sampleRate = static_cast<std::int32_t>(metadata->data.stream_info.sample_rate);
        impl->info->channels = static_cast<std::int32_t>(metadata->data.stream_info.channels);
        impl->info->sourceBitDepth = static_cast<std::int32_t>(metadata->data.stream_info.bits_per_sample);
        impl->info->durationMs = metadata->data.stream_info.sample_rate != 0
                                     ? static_cast<std::int64_t>(metadata->data.stream_info.total_samples * 1000 /
                                                                 metadata->data.stream_info.sample_rate)
                                     : -1;
        impl->info->codec = "flac";
        const std::size_t block = std::max<std::size_t>(metadata->data.stream_info.max_blocksize, 1);
        impl->pending.resize(block * static_cast<std::size_t>(std::max(impl->info->channels, 1)));
    }
}
void errorCallback(const FLAC__StreamDecoder*, FLAC__StreamDecoderErrorStatus, void* client) {
    static_cast<FlacDecoder::Impl*>(client)->decodeError = true;
}
} // namespace
#endif

FlacDecoder::FlacDecoder() : impl_(std::make_unique<Impl>()) {}
FlacDecoder::~FlacDecoder() { close(); }

ErrorCode FlacDecoder::open(const std::string& path) {
    close();
#if defined(SONAR_HAS_FLAC)
    impl_->file = std::fopen(path.c_str(), "rb");
    if (impl_->file == nullptr) return ErrorCode::ERR_FILE_NOT_FOUND;
    impl_->decoder = FLAC__stream_decoder_new();
    if (impl_->decoder == nullptr) {
        close();
        return ErrorCode::ERR_DECODER_INIT;
    }
    impl_->info = &info_;
    if (FLAC__stream_decoder_init_stream(impl_->decoder, readCallback, seekCallback, tellCallback,
                                         lengthCallback, eofCallback, writeCallback, metadataCallback,
                                         errorCallback, impl_.get()) != FLAC__STREAM_DECODER_INIT_STATUS_OK ||
        !FLAC__stream_decoder_process_until_end_of_metadata(impl_->decoder) || !impl_->metadataReady) {
        close();
        return ErrorCode::ERR_DECODER_INIT;
    }
    if (info_.sampleRate <= 0 || info_.channels < 1 || info_.channels > 2 ||
        info_.sourceBitDepth < 4 || info_.sourceBitDepth > 32) {
        close();
        return ErrorCode::ERR_UNSUPPORTED_FORMAT;
    }
    positionFrames_ = 0;
    return ErrorCode::OK;
#else
    (void)path;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

DecodeResult FlacDecoder::decodeNextFrame(float* output, std::size_t maxFrames) {
#if defined(SONAR_HAS_FLAC)
    if (impl_->decoder == nullptr || output == nullptr || maxFrames == 0) {
        return {impl_->decoder != nullptr ? ErrorCode::ERR_INTERNAL : ErrorCode::ERR_INVALID_STATE, 0, false};
    }
    impl_->decodeError = false;
    const std::size_t channels = static_cast<std::size_t>(info_.channels);
    std::size_t copied = 0;
    if (impl_->pendingFrames > impl_->pendingOffset) {
        const std::size_t available = impl_->pendingFrames - impl_->pendingOffset;
        const std::size_t count = std::min(maxFrames, available);
        std::copy_n(impl_->pending.data() + impl_->pendingOffset * channels,
                    count * channels, output);
        impl_->pendingOffset += count;
        copied = count;
        if (impl_->pendingOffset == impl_->pendingFrames) {
            impl_->pendingFrames = 0;
            impl_->pendingOffset = 0;
        }
    }
    if (copied == maxFrames) {
        positionFrames_ += static_cast<std::int64_t>(copied);
        return {ErrorCode::OK, copied, false};
    }
    if (!FLAC__stream_decoder_process_single(impl_->decoder)) {
        if (FLAC__stream_decoder_get_state(impl_->decoder) == FLAC__STREAM_DECODER_END_OF_STREAM) {
            return {ErrorCode::OK, 0, true};
        }
        return {ErrorCode::ERR_DECODER_DECODE, 0, false};
    }
    if (impl_->decodeError) return {ErrorCode::ERR_DECODER_DECODE, 0, false};
    const std::size_t available = impl_->pendingFrames - impl_->pendingOffset;
    const std::size_t count = std::min(maxFrames - copied, available);
    std::copy_n(impl_->pending.data() + impl_->pendingOffset * channels,
                count * channels, output + copied * channels);
    impl_->pendingOffset += count;
    if (impl_->pendingOffset == impl_->pendingFrames) {
        impl_->pendingFrames = 0;
        impl_->pendingOffset = 0;
    }
    const std::size_t total = copied + count;
    if (total == 0) return {ErrorCode::OK, 0, true};
    positionFrames_ += static_cast<std::int64_t>(total);
    return {ErrorCode::OK, total, false};
#else
    (void)output;
    (void)maxFrames;
    return {ErrorCode::ERR_UNSUPPORTED_FORMAT, 0, false};
#endif
}

ErrorCode FlacDecoder::seek(std::int64_t positionMs) {
#if defined(SONAR_HAS_FLAC)
    if (impl_->decoder == nullptr || positionMs < 0) return ErrorCode::ERR_SEEK_FAILED;
    const auto frame = static_cast<FLAC__uint64>(positionMs) * info_.sampleRate / 1000ULL;
    if (!FLAC__stream_decoder_seek_absolute(impl_->decoder, frame)) return ErrorCode::ERR_SEEK_FAILED;
    impl_->pendingFrames = 0;
    impl_->pendingOffset = 0;
    positionFrames_ = static_cast<std::int64_t>(frame);
    return ErrorCode::OK;
#else
    (void)positionMs;
    return ErrorCode::ERR_UNSUPPORTED_FORMAT;
#endif
}

void FlacDecoder::close() noexcept {
#if defined(SONAR_HAS_FLAC)
    if (impl_ && impl_->decoder != nullptr) {
        FLAC__stream_decoder_finish(impl_->decoder);
        FLAC__stream_decoder_delete(impl_->decoder);
        impl_->decoder = nullptr;
    }
    if (impl_ && impl_->file != nullptr) std::fclose(impl_->file);
    if (impl_) impl_->file = nullptr;
    if (impl_) {
        impl_->info = nullptr;
        impl_->pending.clear();
        impl_->pendingFrames = 0;
        impl_->pendingOffset = 0;
    }
#endif
    info_ = {};
    positionFrames_ = 0;
}

std::int64_t FlacDecoder::positionMs() const noexcept {
    return info_.sampleRate > 0 ? positionFrames_ * 1000 / info_.sampleRate : 0;
}

} // namespace sonar::core
