#include "ring_buffer.h"

#include <algorithm>
#include <cstring>
#include <limits>

namespace sonar::core {

RingBuffer::RingBuffer(std::size_t capacityFrames, std::size_t channels)
    : capacityFrames_(capacityFrames), channels_(channels),
      storage_(capacityFrames != 0 && channels != 0
                   ? std::make_unique<float[]>(capacityFrames * channels)
                   : nullptr) {}

RingBuffer::~RingBuffer() = default;

std::size_t RingBuffer::availableFrames() const noexcept {
    const auto read = readIndex_.load(std::memory_order_acquire);
    const auto write = writeIndex_.load(std::memory_order_acquire);
    return static_cast<std::size_t>(write - read);
}

std::size_t RingBuffer::freeFrames() const noexcept {
    return capacityFrames_ - std::min(capacityFrames_, availableFrames());
}

std::size_t RingBuffer::write(const float* input, std::size_t frames) noexcept {
    if (input == nullptr || storage_ == nullptr || frames == 0) return 0;
    const auto read = readIndex_.load(std::memory_order_acquire);
    const auto write = writeIndex_.load(std::memory_order_relaxed);
    const std::size_t count = std::min(frames, capacityFrames_ -
                                                   static_cast<std::size_t>(write - read));
    for (std::size_t i = 0; i < count; ++i) {
        const std::size_t slot = static_cast<std::size_t>((write + i) % capacityFrames_);
        std::memcpy(storage_.get() + slot * channels_, input + i * channels_,
                    channels_ * sizeof(float));
    }
    writeIndex_.store(write + count, std::memory_order_release);
    return count;
}

std::size_t RingBuffer::read(float* output, std::size_t frames) noexcept {
    if (output == nullptr || storage_ == nullptr || frames == 0) return 0;
    const auto write = writeIndex_.load(std::memory_order_acquire);
    const auto read = readIndex_.load(std::memory_order_relaxed);
    const std::size_t count = std::min(frames, static_cast<std::size_t>(write - read));
    for (std::size_t i = 0; i < count; ++i) {
        const std::size_t slot = static_cast<std::size_t>((read + i) % capacityFrames_);
        std::memcpy(output + i * channels_, storage_.get() + slot * channels_,
                    channels_ * sizeof(float));
    }
    readIndex_.store(read + count, std::memory_order_release);
    return count;
}

void RingBuffer::reset() noexcept {
    const auto write = writeIndex_.load(std::memory_order_relaxed);
    readIndex_.store(write, std::memory_order_release);
}

} // namespace sonar::core
