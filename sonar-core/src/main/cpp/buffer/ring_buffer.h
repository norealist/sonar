#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>

namespace sonar::core {

// A frame-oriented, lock-free SPSC queue. The storage is allocated only by
// the constructor; read/write never allocate or take a lock.
class RingBuffer final {
public:
    RingBuffer(std::size_t capacityFrames, std::size_t channels);
    ~RingBuffer();

    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;

    std::size_t capacityFrames() const noexcept { return capacityFrames_; }
    std::size_t channels() const noexcept { return channels_; }
    std::size_t availableFrames() const noexcept;
    std::size_t freeFrames() const noexcept;
    bool empty() const noexcept { return availableFrames() == 0; }
    bool full() const noexcept { return freeFrames() == 0; }

    std::size_t write(const float* interleavedFrames, std::size_t frames) noexcept;
    std::size_t read(float* interleavedFrames, std::size_t frames) noexcept;
    void reset() noexcept;

private:
    const std::size_t capacityFrames_;
    const std::size_t channels_;
    std::unique_ptr<float[]> storage_;
    std::atomic<std::uint64_t> readIndex_{0};
    std::atomic<std::uint64_t> writeIndex_{0};
};

} // namespace sonar::core
