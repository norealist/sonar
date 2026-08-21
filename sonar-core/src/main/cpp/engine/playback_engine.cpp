#include "playback_engine.h"

#include "../decoder/decoder_factory.h"
#include "../format/format_converter.h"

#include <algorithm>
#include <chrono>
#include <cstring>

namespace sonar::core {
namespace {

const char* errorText(ErrorCode code) noexcept {
    switch (code) {
        case ErrorCode::OK: return "";
        case ErrorCode::ERR_FILE_NOT_FOUND: return "file not found";
        case ErrorCode::ERR_FILE_READ: return "file read error";
        case ErrorCode::ERR_UNSUPPORTED_FORMAT: return "unsupported audio format";
        case ErrorCode::ERR_DECODER_INIT: return "decoder initialization failed";
        case ErrorCode::ERR_DECODER_DECODE: return "decoder error";
        case ErrorCode::ERR_INVALID_STATE: return "invalid player state";
        case ErrorCode::ERR_SEEK_FAILED: return "seek failed";
        case ErrorCode::ERR_OUTPUT_FORMAT: return "unsupported output format";
        case ErrorCode::ERR_INTERNAL: return "internal error";
    }
    return "internal error";
}

} // namespace

PlaybackEngine::PlaybackEngine(std::int32_t outputSampleRate, std::int32_t outputEncoding,
                               std::int32_t outputChannels, EngineConfig config)
    : config_(config), requestedSampleRate_(outputSampleRate),
      requestedChannels_(outputChannels), outputEncoding_(outputEncoding) {
    if (config_.maxConsecutiveDecodeErrors == 0) {
        // Keep the documented default meaningful even for a zero-initialized config.
        config_.maxConsecutiveDecodeErrors = kDefaultMaxConsecutiveDecodeErrors;
    }
}

PlaybackEngine::~PlaybackEngine() {
    stop();
}

void PlaybackEngine::requestDecodeStop() {
    stopDecode_.store(true, std::memory_order_release);
    decodeCondition_.notify_all();
    if (decodeThread_.joinable()) decodeThread_.join();
}

void PlaybackEngine::startDecodeThread() {
    if (decodeThread_.joinable()) return;
    stopDecode_.store(false, std::memory_order_release);
    decodeThread_ = std::thread(&PlaybackEngine::decodeLoop, this);
}

ErrorCode PlaybackEngine::open(const std::string& path) {
    stop();
    ErrorCode factoryError = ErrorCode::OK;
    auto decoder = DecoderFactory::create(path, &factoryError);
    if (!decoder) {
        setError(factoryError == ErrorCode::OK ? ErrorCode::ERR_DECODER_INIT : factoryError,
                 errorText(factoryError));
        state_.store(PlayerState::ERROR, std::memory_order_release);
        return factoryError == ErrorCode::OK ? ErrorCode::ERR_DECODER_INIT : factoryError;
    }
    const StreamInfo stream = decoder->getStreamInfo();
    if (stream.sampleRate <= 0 || stream.channels < 1 || stream.channels > 2) {
        decoder->close();
        setError(ErrorCode::ERR_UNSUPPORTED_FORMAT, errorText(ErrorCode::ERR_UNSUPPORTED_FORMAT));
        state_.store(PlayerState::ERROR, std::memory_order_release);
        return ErrorCode::ERR_UNSUPPORTED_FORMAT;
    }
    const std::size_t ringFrames = std::max<std::size_t>(1,
        static_cast<std::size_t>((static_cast<std::uint64_t>(stream.sampleRate) * config_.ringDurationMs) / 1000));
    const std::size_t decodeFrames = std::max(config_.decodeChunkFrames, std::size_t{1});
    try {
        auto ring = std::make_shared<RingBuffer>(ringFrames, static_cast<std::size_t>(stream.channels));
        std::vector<float> decodeBuffer(decodeFrames * static_cast<std::size_t>(stream.channels));
        std::vector<float> readBuffer(decodeFrames * static_cast<std::size_t>(stream.channels));
        std::lock_guard<std::mutex> lock(stateMutex_);
        decoder_ = std::move(decoder);
        ring_ = std::move(ring);
        decodeBuffer_ = std::move(decodeBuffer);
        readBuffer_ = std::move(readBuffer);
        info_ = stream;
        requestedSampleRate_ = stream.sampleRate;
        requestedChannels_ = stream.channels;
        activeSampleRate_.store(stream.sampleRate, std::memory_order_release);
        prebufferFrames_ = std::min(ringFrames, std::max<std::size_t>(1,
            ringFrames * std::min<std::size_t>(config_.prebufferPercent, 100) / 100));
        playedFrames_.store(0, std::memory_order_relaxed);
        consecutiveDecodeErrors_ = 0;
        {
            std::lock_guard<std::mutex> errorLock(errorMutex_);
            lastError_ = ErrorCode::OK;
            errorMessage_.clear();
        }
        state_.store(PlayerState::OPENED, std::memory_order_release);
    } catch (...) {
        if (decoder) decoder->close();
        setError(ErrorCode::ERR_INTERNAL, errorText(ErrorCode::ERR_INTERNAL));
        state_.store(PlayerState::ERROR, std::memory_order_release);
        return ErrorCode::ERR_INTERNAL;
    }
    return ErrorCode::OK;
}

ErrorCode PlaybackEngine::openFd(int fd) {
    stop();
    ErrorCode factoryError = ErrorCode::OK;
    auto decoder = DecoderFactory::createFd(fd, &factoryError);
    if (!decoder) {
        setError(factoryError == ErrorCode::OK ? ErrorCode::ERR_DECODER_INIT : factoryError,
                 errorText(factoryError));
        state_.store(PlayerState::ERROR, std::memory_order_release);
        return factoryError == ErrorCode::OK ? ErrorCode::ERR_DECODER_INIT : factoryError;
    }
    const StreamInfo stream = decoder->getStreamInfo();
    if (stream.sampleRate <= 0 || stream.channels < 1 || stream.channels > 2) {
        decoder->close();
        setError(ErrorCode::ERR_UNSUPPORTED_FORMAT, errorText(ErrorCode::ERR_UNSUPPORTED_FORMAT));
        state_.store(PlayerState::ERROR, std::memory_order_release);
        return ErrorCode::ERR_UNSUPPORTED_FORMAT;
    }
    const std::size_t ringFrames = std::max<std::size_t>(1,
        static_cast<std::size_t>((static_cast<std::uint64_t>(stream.sampleRate) * config_.ringDurationMs) / 1000));
    const std::size_t decodeFrames = std::max(config_.decodeChunkFrames, std::size_t{1});
    try {
        auto ring = std::make_shared<RingBuffer>(ringFrames, static_cast<std::size_t>(stream.channels));
        std::vector<float> decodeBuffer(decodeFrames * static_cast<std::size_t>(stream.channels));
        std::vector<float> readBuffer(decodeFrames * static_cast<std::size_t>(stream.channels));
        std::lock_guard<std::mutex> lock(stateMutex_);
        decoder_ = std::move(decoder);
        ring_ = std::move(ring);
        decodeBuffer_ = std::move(decodeBuffer);
        readBuffer_ = std::move(readBuffer);
        info_ = stream;
        requestedSampleRate_ = stream.sampleRate;
        requestedChannels_ = stream.channels;
        activeSampleRate_.store(stream.sampleRate, std::memory_order_release);
        prebufferFrames_ = std::min(ringFrames, std::max<std::size_t>(1,
            ringFrames * std::min<std::size_t>(config_.prebufferPercent, 100) / 100));
        playedFrames_.store(0, std::memory_order_relaxed);
        consecutiveDecodeErrors_ = 0;
        {
            std::lock_guard<std::mutex> errorLock(errorMutex_);
            lastError_ = ErrorCode::OK;
            errorMessage_.clear();
        }
        state_.store(PlayerState::OPENED, std::memory_order_release);
    } catch (...) {
        if (decoder) decoder->close();
        setError(ErrorCode::ERR_INTERNAL, errorText(ErrorCode::ERR_INTERNAL));
        state_.store(PlayerState::ERROR, std::memory_order_release);
        return ErrorCode::ERR_INTERNAL;
    }
    return ErrorCode::OK;
}

ErrorCode PlaybackEngine::play() {
    std::lock_guard<std::mutex> lock(stateMutex_);
    const PlayerState current = state_.load(std::memory_order_relaxed);
    if (current != PlayerState::OPENED && current != PlayerState::PAUSED) {
        return ErrorCode::ERR_INVALID_STATE;
    }
    state_.store(PlayerState::BUFFERING, std::memory_order_release);
    startDecodeThread();
    decodeCondition_.notify_all();
    return ErrorCode::OK;
}

ErrorCode PlaybackEngine::pause() {
    std::lock_guard<std::mutex> lock(stateMutex_);
    const PlayerState current = state_.load(std::memory_order_relaxed);
    if (current != PlayerState::PLAYING && current != PlayerState::BUFFERING) {
        return ErrorCode::ERR_INVALID_STATE;
    }
    state_.store(PlayerState::PAUSED, std::memory_order_release);
    decodeCondition_.notify_all();
    return ErrorCode::OK;
}

ErrorCode PlaybackEngine::resume() {
    std::lock_guard<std::mutex> lock(stateMutex_);
    if (state_.load(std::memory_order_relaxed) != PlayerState::PAUSED) {
        return ErrorCode::ERR_INVALID_STATE;
    }
    state_.store(PlayerState::PLAYING, std::memory_order_release);
    // A seek while paused joins the old decode thread and intentionally does
    // not restart it. Resume must recreate that thread before waking it.
    startDecodeThread();
    decodeCondition_.notify_all();
    return ErrorCode::OK;
}

ErrorCode PlaybackEngine::stop() {
    requestDecodeStop();
    std::lock_guard<std::mutex> lock(stateMutex_);
    if (ring_) ring_->reset();
    if (decoder_) decoder_->close();
    decoder_.reset();
    ring_.reset();
    decodeBuffer_.clear();
    readBuffer_.clear();
    info_ = {};
    activeSampleRate_.store(0, std::memory_order_release);
    playedFrames_.store(0, std::memory_order_relaxed);
    state_.store(PlayerState::IDLE, std::memory_order_release);
    return ErrorCode::OK;
}

ErrorCode PlaybackEngine::seek(std::int64_t positionMs) {
    const PlayerState before = state_.load(std::memory_order_acquire);
    if (before != PlayerState::OPENED && before != PlayerState::PLAYING &&
        before != PlayerState::BUFFERING && before != PlayerState::PAUSED &&
        before != PlayerState::COMPLETED) return ErrorCode::ERR_INVALID_STATE;
    requestDecodeStop();
    ErrorCode result = ErrorCode::ERR_SEEK_FAILED;
    {
        std::lock_guard<std::mutex> lock(stateMutex_);
        if (decoder_ && ring_) {
            result = decoder_->seek(positionMs);
            if (result == ErrorCode::OK) {
                ring_->reset();
                playedFrames_.store(static_cast<std::uint64_t>(decoder_->positionMs()) *
                    static_cast<std::uint64_t>(info_.sampleRate) / 1000ULL,
                    std::memory_order_relaxed);
                consecutiveDecodeErrors_ = 0;
                state_.store(before == PlayerState::PAUSED ? PlayerState::PAUSED
                                                           : before == PlayerState::OPENED
                                                                 ? PlayerState::OPENED
                                                                 : PlayerState::BUFFERING,
                             std::memory_order_release);
            }
        }
    }
    if (result != ErrorCode::OK) {
        setError(result, errorText(result));
        return result;
    }
    if (before != PlayerState::PAUSED && before != PlayerState::OPENED) {
        startDecodeThread();
        decodeCondition_.notify_all();
    }
    return ErrorCode::OK;
}

std::size_t PlaybackEngine::readPcm(void* output, std::size_t outputBytes, std::size_t maxFrames) {
    if (output == nullptr || maxFrames == 0) return 0;
    std::lock_guard<std::mutex> lock(stateMutex_);
    const auto& ring = ring_;
    std::size_t channels = 0;
    channels = info_.channels > 0 ? static_cast<std::size_t>(info_.channels) : 0;
    if (!ring || channels == 0 || readBuffer_.size() < channels) return 0;
    const auto encoding = outputEncoding();
    const std::size_t sampleBytes = FormatConverter::bytesPerSample(encoding);
    if (sampleBytes == 0 || outputBytes < sampleBytes * channels) return 0;
    const std::size_t availableFrames = std::min(maxFrames, readBuffer_.size() / channels);
    const std::size_t outputFrames = std::min(availableFrames, outputBytes / (sampleBytes * channels));
    const std::size_t frames = ring->read(readBuffer_.data(), outputFrames);
    if (frames == 0) {
        PlayerState expected = PlayerState::PLAYING;
        state_.compare_exchange_strong(expected, PlayerState::BUFFERING,
                                       std::memory_order_acq_rel);
        decodeCondition_.notify_all();
        return 0;
    }
    const std::size_t converted = FormatConverter::convert(readBuffer_.data(), frames, channels,
                                                           encoding, output, outputBytes);
    playedFrames_.fetch_add(converted, std::memory_order_relaxed);
    decodeCondition_.notify_all();
    return converted;
}

ErrorCode PlaybackEngine::setOutputFormat(std::int32_t encoding) {
    if (FormatConverter::bytesPerSample(encoding) == 0) return ErrorCode::ERR_OUTPUT_FORMAT;
    outputEncoding_.store(encoding, std::memory_order_release);
    return ErrorCode::OK;
}

std::int64_t PlaybackEngine::positionMs() const noexcept {
    const auto activeRate = activeSampleRate_.load(std::memory_order_acquire);
    return activeRate > 0 ? static_cast<std::int64_t>(playedFrames_.load(std::memory_order_relaxed) *
                                                       1000ULL / static_cast<std::uint64_t>(activeRate)) : 0;
}

StreamInfo PlaybackEngine::streamInfo() const {
    std::lock_guard<std::mutex> lock(stateMutex_);
    return info_;
}

std::string PlaybackEngine::errorMessage() const {
    std::lock_guard<std::mutex> lock(errorMutex_);
    return errorMessage_;
}

void PlaybackEngine::setError(ErrorCode code, const char* message) {
    std::lock_guard<std::mutex> lock(errorMutex_);
    lastError_ = code;
    errorMessage_ = message == nullptr ? "" : message;
}

bool PlaybackEngine::canDecode() const noexcept {
    const auto current = state_.load(std::memory_order_acquire);
    return current == PlayerState::BUFFERING || current == PlayerState::PLAYING;
}

void PlaybackEngine::decodeLoop() {
    for (;;) {
        std::unique_lock<std::mutex> waitLock(stateMutex_);
        decodeCondition_.wait(waitLock, [this] {
            return stopDecode_.load(std::memory_order_acquire) || canDecode();
        });
        if (stopDecode_.load(std::memory_order_acquire)) return;
        auto decoder = decoder_.get();
        auto ring = ring_;
        waitLock.unlock();
        if (decoder == nullptr || !ring) return;
        if (ring->freeFrames() == 0) {
            std::unique_lock<std::mutex> lock(stateMutex_);
            decodeCondition_.wait_for(lock, std::chrono::milliseconds(2), [this, &ring] {
                return stopDecode_.load(std::memory_order_acquire) || !canDecode() || ring->freeFrames() != 0;
            });
            continue;
        }
        const std::size_t channels = static_cast<std::size_t>(info_.channels);
        const std::size_t requestFrames = std::min(ring->freeFrames(), decodeBuffer_.size() / channels);
        const DecodeResult result = decoder->decodeNextFrame(decodeBuffer_.data(), requestFrames);
        if (result.error != ErrorCode::OK) {
            if (++consecutiveDecodeErrors_ > config_.maxConsecutiveDecodeErrors) {
                setError(ErrorCode::ERR_DECODER_DECODE, errorText(ErrorCode::ERR_DECODER_DECODE));
                state_.store(PlayerState::ERROR, std::memory_order_release);
                return;
            }
            std::this_thread::yield();
            continue;
        }
        consecutiveDecodeErrors_ = 0;
        if (result.frames != 0) ring->write(decodeBuffer_.data(), result.frames);
        if (result.endOfStream) {
            state_.store(PlayerState::COMPLETED, std::memory_order_release);
            continue;
        }
        if (state_.load(std::memory_order_acquire) == PlayerState::BUFFERING &&
            ring->availableFrames() >= prebufferFrames_) {
            state_.store(PlayerState::PLAYING, std::memory_order_release);
        }
    }
}

} // namespace sonar::core
