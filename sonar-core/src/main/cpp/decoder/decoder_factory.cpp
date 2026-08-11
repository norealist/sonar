#include "decoder_factory.h"

#include "flac_decoder.h"
#include "mp3_decoder.h"
#include "opus_decoder.h"
#include "vorbis_decoder.h"
#include "wav_decoder.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <string_view>

namespace sonar::core {
namespace {

std::string lowerExtension(const std::string& path) {
    const auto slash = path.find_last_of("/\\");
    const auto dot = path.find_last_of('.');
    if (dot == std::string::npos || (slash != std::string::npos && dot < slash)) return {};
    std::string result = path.substr(dot + 1);
    std::transform(result.begin(), result.end(), result.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return result;
}

bool hasBytes(const std::uint8_t* bytes, std::size_t length, std::string_view value,
              std::size_t offset) noexcept {
    return offset + value.size() <= length &&
           std::memcmp(bytes + offset, value.data(), value.size()) == 0;
}

} // namespace

DecoderKind DecoderFactory::detect(const std::string& path) noexcept {
    std::array<std::uint8_t, 65536> buffer{};
    std::size_t length = 0;
    if (FILE* file = std::fopen(path.c_str(), "rb")) {
        length = std::fread(buffer.data(), 1, buffer.size(), file);
        std::fclose(file);
    } else {
        return DecoderKind::Unsupported;
    }
    if (length >= 12 && hasBytes(buffer.data(), length, "RIFF", 0) &&
        hasBytes(buffer.data(), length, "WAVE", 8)) return DecoderKind::Wav;
    if (length >= 4 && hasBytes(buffer.data(), length, "fLaC", 0)) return DecoderKind::Flac;
    if (length >= 4 && hasBytes(buffer.data(), length, "OggS", 0)) {
        for (std::size_t i = 0; i + 8 <= length; ++i) {
            if (hasBytes(buffer.data(), length, "OpusHead", i)) return DecoderKind::Opus;
        }
        for (std::size_t i = 0; i + 6 <= length; ++i) {
            if (hasBytes(buffer.data(), length, "vorbis", i)) return DecoderKind::Vorbis;
        }
        return DecoderKind::Unsupported;
    }
    const bool id3 = length >= 3 && hasBytes(buffer.data(), length, "ID3", 0);
    bool mpegSync = false;
    for (std::size_t i = 0; i + 1 < length; ++i) {
        if (buffer[i] == 0xff && (buffer[i + 1] & 0xe0U) == 0xe0U) {
            mpegSync = true;
            break;
        }
    }
    if (id3 || mpegSync) return DecoderKind::Mp3;

    // Extension is only a fallback hint when the content is not identifiable.
    const std::string extension = lowerExtension(path);
    if (extension == "wav" || extension == "wave") return DecoderKind::Wav;
    if (extension == "mp3") return DecoderKind::Mp3;
    if (extension == "opus") return DecoderKind::Opus;
    if (extension == "ogg") return DecoderKind::Vorbis;
    if (extension == "flac") return DecoderKind::Flac;
    return DecoderKind::Unsupported;
}

std::unique_ptr<IDecoder> DecoderFactory::create(const std::string& path,
                                                 ErrorCode* error) noexcept {
    try {
        if (error != nullptr) *error = ErrorCode::OK;
        if (FILE* file = std::fopen(path.c_str(), "rb")) {
            std::fclose(file);
        } else {
            if (error != nullptr) *error = ErrorCode::ERR_FILE_NOT_FOUND;
            return nullptr;
        }
        std::unique_ptr<IDecoder> decoder;
        switch (detect(path)) {
            case DecoderKind::Wav: decoder = std::make_unique<WavDecoder>(); break;
            case DecoderKind::Mp3: decoder = std::make_unique<Mp3Decoder>(); break;
            case DecoderKind::Opus: decoder = std::make_unique<OpusDecoder>(); break;
            case DecoderKind::Vorbis: decoder = std::make_unique<VorbisDecoder>(); break;
            case DecoderKind::Flac: decoder = std::make_unique<FlacDecoder>(); break;
            case DecoderKind::Unsupported:
                if (error != nullptr) *error = ErrorCode::ERR_UNSUPPORTED_FORMAT;
                return nullptr;
        }
        const ErrorCode openError = decoder->open(path);
        if (openError != ErrorCode::OK) {
            if (error != nullptr) *error = openError;
            return nullptr;
        }
        return decoder;
    } catch (...) {
        if (error != nullptr) *error = ErrorCode::ERR_INTERNAL;
        return nullptr;
    }
}

} // namespace sonar::core
