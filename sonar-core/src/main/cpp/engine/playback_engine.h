#pragma once

#include "engine_config.h"
#include "engine_types.h"

#include "../buffer/ring_buffer.h"
#include "../decoder/i_decoder.h"

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace sonar::core {

class PlaybackEngine final {
public:
    PlaybackEngine(std::int32_t outputSampleRate, std::int32_t outputEncoding,
                   std::int32_t outputChannels, EngineConfig config = {});
    ~PlaybackEngine();

    PlaybackEngine(const PlaybackEngine&) = delete;
    PlaybackEngine& operator=(const PlaybackEngine&) = delete;

    ErrorCode open(const std::string& path);
    ErrorCode play();
    ErrorCode pause();
    ErrorCode resume();
    ErrorCode stop();
    ErrorCode seek(std::int64_t positionMs);
    std::size_t readPcm(void* output, std::size_t outputBytes, std::size_t maxFrames);
    ErrorCode setOutputFormat(std::int32_t encoding);

    PlayerState state() const noexcept { return state_.load(std::memory_order_relaxed); }
    std::int64_t positionMs() const noexcept;
    StreamInfo streamInfo() const;
    std::string errorMessage() const;
    std::int32_t outputEncoding() const noexcept { return outputEncoding_.load(std::memory_order_relaxed); }

private:
    void decodeLoop();
    void startDecodeThread();
    void requestDecodeStop();
    void setError(ErrorCode code, const char* message);
    bool canDecode() const noexcept;

    EngineConfig config_;
    std::int32_t requestedSampleRate_;
    std::int32_t requestedChannels_;
    std::atomic<std::int32_t> activeSampleRate_{0};
    std::atomic<std::int32_t> outputEncoding_;

    mutable std::mutex stateMutex_;
    std::condition_variable decodeCondition_;
    std::atomic<PlayerState> state_{PlayerState::IDLE};
    std::atomic<bool> stopDecode_{false};
    std::thread decodeThread_;

    std::unique_ptr<IDecoder> decoder_;
    std::shared_ptr<RingBuffer> ring_;
    StreamInfo info_{};
    std::vector<float> decodeBuffer_;
    std::vector<float> readBuffer_;
    std::atomic<std::uint64_t> playedFrames_{0};
    std::size_t prebufferFrames_ = 0;
    std::size_t consecutiveDecodeErrors_ = 0;

    mutable std::mutex errorMutex_;
    ErrorCode lastError_ = ErrorCode::OK;
    std::string errorMessage_;
};

} // namespace sonar::core
