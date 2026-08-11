#pragma once

#include "i_decoder.h"

#include <memory>
#include <string>

namespace sonar::core {

enum class DecoderKind { Wav, Mp3, Opus, Vorbis, Flac, Unsupported };

class DecoderFactory final {
public:
    static DecoderKind detect(const std::string& path) noexcept;
    static std::unique_ptr<IDecoder> create(const std::string& path,
                                            ErrorCode* error = nullptr) noexcept;
};

} // namespace sonar::core
