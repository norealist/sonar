#include "buffer/ring_buffer.h"
#include "decoder/decoder_factory.h"
#include "decoder/wav_decoder.h"
#include "engine/engine_types.h"
#include "engine/playback_engine.h"
#include "format/format_converter.h"

#include <cassert>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <filesystem>
#include <thread>
#include <atomic>
#include <string>
#include <vector>

#ifdef SONAR_HAS_GTEST
#include <gtest/gtest.h>
#define CHECK(condition) ASSERT_TRUE(condition)
#define CHECK_EQ(left, right) ASSERT_EQ(left, right)
#else
#define CHECK(condition) assert(condition)
#define CHECK_EQ(left, right) assert((left) == (right))
#endif

namespace {

std::string writeFixture(const std::string& path = "sonar_native_test.wav") {
    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    const std::uint32_t dataSize = 8;
    const std::uint32_t riffSize = 4 + (8 + 2) + (8 + 16) + (8 + dataSize);
    auto w16 = [&file](std::uint16_t value) {
        file.put(static_cast<char>(value)); file.put(static_cast<char>(value >> 8));
    };
    auto w32 = [&file](std::uint32_t value) {
        for (int i = 0; i < 4; ++i) file.put(static_cast<char>(value >> (i * 8)));
    };
    file.write("RIFF", 4); w32(riffSize); file.write("WAVE", 4);
    file.write("JUNK", 4); w32(2); file.put(0); file.put(0);
    file.write("fmt ", 4); w32(16); w16(1); w16(1); w32(8000); w32(16000); w16(2); w16(16);
    file.write("data", 4); w32(dataSize); w16(0); w16(16384); w16(32767);
    w16(static_cast<std::uint16_t>(-16384));
    file.close();
    return path;
}

std::string writeWavVariant(const std::string& path, std::uint16_t formatTag,
                            std::uint16_t channels, std::uint16_t bitsPerSample) {
    const std::uint32_t bytesPerSample = (bitsPerSample + 7U) / 8U;
    const std::uint16_t blockAlign = static_cast<std::uint16_t>(channels * bytesPerSample);
    const std::uint32_t sampleRate = 8000;
    const std::uint32_t dataSize = blockAlign * 2U;
    const std::uint32_t riffSize = 4U + 8U + 16U + 8U + dataSize;
    std::ofstream file(path, std::ios::binary | std::ios::trunc);
    auto w16 = [&file](std::uint16_t value) {
        file.put(static_cast<char>(value));
        file.put(static_cast<char>(value >> 8));
    };
    auto w32 = [&file](std::uint32_t value) {
        for (int i = 0; i < 4; ++i) file.put(static_cast<char>(value >> (i * 8)));
    };
    file.write("RIFF", 4); w32(riffSize); file.write("WAVE", 4);
    file.write("fmt ", 4); w32(16); w16(formatTag); w16(channels); w32(sampleRate);
    w32(sampleRate * blockAlign); w16(blockAlign); w16(bitsPerSample);
    file.write("data", 4); w32(dataSize);
    if (formatTag == 3) {
        const float samples[] = {0.5f, -0.5f, 0.25f, -0.25f};
        for (float sample : samples) file.write(reinterpret_cast<const char*>(&sample), sizeof(sample));
    } else {
        for (std::uint32_t i = 0; i < dataSize; ++i) file.put(static_cast<char>(i * 17U));
    }
    return path;
}

void ringTest() {
    sonar::core::RingBuffer ring(2, 2);
    const float input[] = {1, 2, 3, 4, 5, 6};
    float output[6]{};
    CHECK_EQ(ring.write(input, 3), 2U);
    CHECK(ring.full());
    CHECK_EQ(ring.read(output, 1), 1U);
    CHECK_EQ(ring.write(input + 4, 1), 1U);
    CHECK_EQ(ring.read(output + 2, 2), 2U);
    CHECK_EQ(output[2], 3.0f);
    CHECK_EQ(output[3], 4.0f);
}

void ringConcurrentTest() {
    sonar::core::RingBuffer ring(256, 1);
    constexpr std::size_t total = 100000;
    std::atomic<bool> producerDone{false};
    std::atomic<bool> failed{false};
    std::thread producer([&] {
        std::size_t next = 0;
        while (next < total) {
            float chunk[32]{};
            const std::size_t count = std::min<std::size_t>(32, total - next);
            for (std::size_t i = 0; i < count; ++i) chunk[i] = static_cast<float>(next + i);
            next += ring.write(chunk, count);
        }
        producerDone.store(true, std::memory_order_release);
    });
    std::size_t consumed = 0;
    while (!producerDone.load(std::memory_order_acquire) || !ring.empty()) {
        float chunk[31]{};
        const std::size_t count = ring.read(chunk, 31);
        for (std::size_t i = 0; i < count; ++i) {
            if (chunk[i] != static_cast<float>(consumed++)) failed.store(true, std::memory_order_relaxed);
        }
        if (count == 0) std::this_thread::yield();
    }
    producer.join();
    CHECK(!failed.load(std::memory_order_relaxed));
    CHECK_EQ(consumed, total);
}

void converterTest() {
    const float input[] = {-1.2f, -1.0f, 0.0f, 1.0f, 1.2f};
    std::uint8_t output[15]{};
    CHECK_EQ(sonar::core::FormatConverter::convert(input, 5, 1,
                                                    sonar::core::ENCODING_PCM_24BIT_PACKED,
                                                    output, sizeof(output)), 5U);
    CHECK_EQ(output[0], 1U);
    CHECK_EQ(output[2], 0x80U);
    CHECK_EQ(output[12], 0xffU);

    const float floatInput[] = {-1.5f, 0.25f};
    float floatOutput[2]{};
    CHECK_EQ(sonar::core::FormatConverter::convert(floatInput, 2, 1,
                                                    sonar::core::ENCODING_PCM_FLOAT,
                                                    floatOutput, sizeof(floatOutput)), 2U);
    CHECK_EQ(floatOutput[0], -1.5f);
    CHECK_EQ(floatOutput[1], 0.25f);
}

void wavAndFactoryTest() {
    const std::string path = writeFixture();
    CHECK_EQ(sonar::core::DecoderFactory::detect(path), sonar::core::DecoderKind::Wav);
    sonar::core::ErrorCode error = sonar::core::ErrorCode::ERR_INTERNAL;
    auto decoder = sonar::core::DecoderFactory::create(path, &error);
    CHECK(decoder != nullptr);
    CHECK_EQ(error, sonar::core::ErrorCode::OK);
    CHECK_EQ(decoder->getStreamInfo().sampleRate, 8000);
    float pcm[8]{};
    const auto result = decoder->decodeNextFrame(pcm, 4);
    CHECK_EQ(result.frames, 4U);
    CHECK(std::fabs(pcm[1] - 0.5f) < 0.001f);
    std::remove(path.c_str());
}

void engineStateTest() {
    sonar::core::PlaybackEngine engine(48000, sonar::core::ENCODING_PCM_16BIT, 2);
    CHECK_EQ(engine.play(), sonar::core::ErrorCode::ERR_INVALID_STATE);
    CHECK_EQ(engine.state(), sonar::core::PlayerState::IDLE);
    CHECK_EQ(engine.setOutputFormat(sonar::core::ENCODING_PCM_16BIT), sonar::core::ErrorCode::OK);
}

void enginePlaybackTest() {
    const std::string path = writeFixture("sonar_native_engine.wav");
    sonar::core::PlaybackEngine engine(8000, sonar::core::ENCODING_PCM_16BIT, 1);
    CHECK_EQ(engine.open(path), sonar::core::ErrorCode::OK);
    CHECK_EQ(engine.state(), sonar::core::PlayerState::OPENED);
    CHECK_EQ(engine.play(), sonar::core::ErrorCode::OK);
    std::uint8_t output[16]{};
    std::size_t frames = 0;
    for (int attempt = 0; attempt < 100 && frames == 0; ++attempt) {
        frames = engine.readPcm(output, sizeof(output), 4);
        if (frames == 0) std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    CHECK_EQ(frames, 4U);
    CHECK_EQ(engine.state(), sonar::core::PlayerState::COMPLETED);
    std::remove(path.c_str());
}

void engineSeekWhilePausedTest() {
    const std::string path = writeFixture("sonar_native_seek_paused.wav");
    sonar::core::PlaybackEngine engine(8000, sonar::core::ENCODING_PCM_16BIT, 1);
    CHECK_EQ(engine.open(path), sonar::core::ErrorCode::OK);
    CHECK_EQ(engine.play(), sonar::core::ErrorCode::OK);
    CHECK_EQ(engine.pause(), sonar::core::ErrorCode::OK);
    CHECK_EQ(engine.seek(0), sonar::core::ErrorCode::OK);
    CHECK_EQ(engine.resume(), sonar::core::ErrorCode::OK);

    std::uint8_t output[16]{};
    std::size_t frames = 0;
    for (int attempt = 0; attempt < 100 && frames == 0; ++attempt) {
        frames = engine.readPcm(output, sizeof(output), 4);
        if (frames == 0) std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    CHECK_EQ(frames, 4U);
    std::remove(path.c_str());
}

void referenceDecoderTest() {
    const std::string root = [] {
        for (const std::string candidate : {
                 std::string("test_audio_files/"),
                 std::string("../test_audio_files/"),
                 std::string("../../test_audio_files/")}) {
            if (std::filesystem::exists(candidate + "test_48khz_16bit.wav")) return candidate;
        }
        return std::string("test_audio_files/");
    }();
    const struct Fixture {
        const char* name;
        const char* codec;
        int sampleRate;
    } fixtures[] = {
        {"test_44.1khz.mp3", "mp3", 44100},
        {"test_opus_48khz.ogg", "opus", 48000},
        {"test_2_44.1khz.ogg", "vorbis", 44100},
        {"test_16bit_44.1khz.flac", "flac", 44100},
        {"test_48khz_16bit.wav", "wav", 48000},
    };
    for (const auto& fixture : fixtures) {
        sonar::core::ErrorCode error = sonar::core::ErrorCode::ERR_INTERNAL;
        auto decoder = sonar::core::DecoderFactory::create(root + fixture.name, &error);
        CHECK(decoder != nullptr);
        CHECK_EQ(error, sonar::core::ErrorCode::OK);
        CHECK_EQ(decoder->getStreamInfo().codec, std::string(fixture.codec));
        CHECK_EQ(decoder->getStreamInfo().sampleRate, fixture.sampleRate);
        std::vector<float> pcm(4096 * static_cast<std::size_t>(decoder->getStreamInfo().channels));
        const auto result = decoder->decodeNextFrame(pcm.data(), 4096);
        CHECK(result.error == sonar::core::ErrorCode::OK);
        CHECK(result.frames > 0);
        CHECK_EQ(decoder->seek(0), sonar::core::ErrorCode::OK);
    }

}

void wavValidationTest() {
    const std::string valid24 = writeWavVariant("sonar_native_24bit.wav", 1, 1, 24);
    sonar::core::ErrorCode error = sonar::core::ErrorCode::ERR_INTERNAL;
    auto decoder = sonar::core::DecoderFactory::create(valid24, &error);
    CHECK(decoder != nullptr);
    CHECK_EQ(decoder->getStreamInfo().sourceBitDepth, 24);
    std::vector<float> samples(2);
    CHECK_EQ(decoder->decodeNextFrame(samples.data(), 2).frames, 2U);
    std::remove(valid24.c_str());

    const std::string validFloat = writeWavVariant("sonar_native_float.wav", 3, 2, 32);
    error = sonar::core::ErrorCode::ERR_INTERNAL;
    decoder = sonar::core::DecoderFactory::create(validFloat, &error);
    CHECK(decoder != nullptr);
    CHECK_EQ(decoder->getStreamInfo().channels, 2);
    std::remove(validFloat.c_str());

    const std::string multichannel = writeWavVariant("sonar_native_3ch.wav", 1, 3, 16);
    error = sonar::core::ErrorCode::OK;
    decoder = sonar::core::DecoderFactory::create(multichannel, &error);
    CHECK(decoder == nullptr);
    CHECK_EQ(error, sonar::core::ErrorCode::ERR_UNSUPPORTED_FORMAT);
    std::remove(multichannel.c_str());

    const std::string mismatch = writeFixture("sonar_native_content_wins.mp3");
    error = sonar::core::ErrorCode::ERR_INTERNAL;
    decoder = sonar::core::DecoderFactory::create(mismatch, &error);
    CHECK(decoder != nullptr);
    CHECK_EQ(decoder->getStreamInfo().codec, std::string("wav"));
    std::remove(mismatch.c_str());

    const std::string empty = "sonar_native_empty.wav";
    std::ofstream(empty, std::ios::binary | std::ios::trunc).close();
    error = sonar::core::ErrorCode::OK;
    decoder = sonar::core::DecoderFactory::create(empty, &error);
    CHECK(decoder == nullptr);
    CHECK_EQ(error, sonar::core::ErrorCode::ERR_FILE_READ);
    std::remove(empty.c_str());
}

} // namespace

#ifdef SONAR_HAS_GTEST
TEST(RingBuffer, BasicSpscOperations) { ringTest(); }
TEST(RingBuffer, ConcurrentProducerConsumer) { ringConcurrentTest(); }
TEST(FormatConverter, Packed24AndClamp) { converterTest(); }
TEST(WavAndFactory, ChunkOrderAndDecode) { wavAndFactoryTest(); }
TEST(PlaybackEngine, InvalidTransition) { engineStateTest(); }
TEST(PlaybackEngine, DecodeAndComplete) { enginePlaybackTest(); }
TEST(PlaybackEngine, SeekWhilePausedThenResume) { engineSeekWhilePausedTest(); }
TEST(Decoders, ReferenceFilesIncludingVorbis) { referenceDecoderTest(); }
TEST(WavDecoder, VariantsAndValidation) { wavValidationTest(); }
#else
int main() {
    ringTest();
    ringConcurrentTest();
    converterTest();
    wavAndFactoryTest();
    engineStateTest();
    enginePlaybackTest();
    engineSeekWhilePausedTest();
    referenceDecoderTest();
    wavValidationTest();
    return 0;
}
#endif
