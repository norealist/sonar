# Sonar Audio Core: Code Architecture

## 1. Scope and Runtime Model

Sonar Audio Core is a local-file Android playback library. It uses a pull-oriented output path:

```text
URI/path
  -> Kotlin URI materialization
  -> NativeBridge.open()
  -> DecoderFactory
  -> IDecoder
  -> float32 interleaved PCM
  -> lock-free SPSC RingBuffer
  -> NativeBridge.readPcm()
  -> FormatConverter
  -> Kotlin AudioTrackWrapper
  -> Android AudioFlinger / device route / external effects
```

The core does not own a playlist, media database, UI navigation, audio focus policy, DSP, or network transport. The test harness supplies only a small verification UI around the library.

## 2. Repository and Module Boundaries

```text
root
├── build.gradle.kts                         Plugin versions
├── settings.gradle.kts                      :sonar-core and :test-harness
├── gradle/wrapper/                          Gradle 9.5.0 wrapper
├── sonar-core/                              Android library module
│   ├── src/main/kotlin/com/sonar/core/      Host API
│   ├── src/main/cpp/CMakeLists.txt          Forwarding CMake entry point
│   ├── src/test/kotlin/                     JVM unit tests
│   └── src/androidTest/kotlin/              Device contract tests
├── test-harness/                            Android verification application
├── src/main/cpp/                            Authoritative native source tree
├── cpp_test/                                Standalone host-native tests
└── test_audio_files/                        Native reference audio fixtures
```

The Android module has the conventional `sonar-core/src/main/cpp` entry point, but the actual C++ implementation remains in the root `src/main/cpp` tree. The forwarding file uses `add_subdirectory()` so Gradle and standalone CMake compile the same targets and sources.

## 3. Host Layer: Kotlin

### `SonarPlayer`

`SonarPlayer` is the public lifecycle and playback facade. It owns:

- The native engine handle.
- The stable `SessionManager` audio session ID.
- The current `StreamInfo` and negotiated `OutputConfig`.
- The active `AudioTrackWrapper`.
- High-resolution output preference and last error state.

Its lifecycle is:

```text
create
  -> open(path or Uri)
  -> native stream info
  -> negotiate output
  -> create AudioTrack
  -> play / pause / resume / seek
  -> stop or open another file
  -> release
```

When opening a `content://` URI, the host copies the content to the application cache and passes a local filesystem path to native code. This keeps the native decoder interface local-file-only and avoids passing Android file descriptors through the decoder layer.

### `NativeBridge`

`NativeBridge` contains only external declarations and loads `libsonar_core`. It exposes lifecycle, control, PCM pull, stream-info, state, position, error, and output-format functions. The JNI implementation validates handles and direct buffers, catches native exceptions, and returns error codes instead of allowing exceptions to cross the JNI boundary.

### `OutputConfigNegotiator`

Negotiation is performed on the Kotlin side because it must call Android `AudioTrack` APIs. The probe result is cached for the current open and cleared by `beginOpen()`.

For a lossless source with at least 24-bit source depth and high-resolution enabled:

```text
API 31+:
    PCM_32BIT
    -> PCM_24BIT_PACKED
    -> PCM_FLOAT
    -> PCM_16BIT

API 24-30:
    PCM_FLOAT
    -> PCM_16BIT
```

High-resolution is never requested for 16-bit or lossy sources (`mp3`, `opus`). The preferred encoding is selected by probing, but the actual `AudioTrackWrapper` creation repeats the fallback chain. This is required because a device, external EQ, DVC, or current output route may accept a probe but reject the real track configuration.

### `AudioTrackWrapper`

`AudioTrackWrapper` owns one `AudioTrack` and one dedicated blocking write thread. It:

- Allocates one direct PCM buffer for the write loop.
- Pulls frames through `nativeReadPcm()`.
- Writes using `AudioTrack.WRITE_BLOCKING`.
- Handles `PLAYING`, buffering, completion, decoder errors, and output errors.
- Stops the track before joining the writer during release.
- Suppresses callbacks from a writer that was already deactivated during track replacement.

This last rule prevents a released track from reporting a stale `ERR_OUTPUT_FORMAT` while a new fallback track is being created.

### `SessionManager`

The session ID is generated once per `SonarPlayer` lifecycle and passed into every `AudioTrack`. Recreating a track for a new sample rate or output encoding therefore preserves the session binding used by external effects.

### Models

- `StreamInfo`: sample rate, channel count, duration, source depth, and codec.
- `PlayerState`: `IDLE`, `OPENED`, `BUFFERING`, `PLAYING`, `PAUSED`, `COMPLETED`, `ERROR`.
- `SonarError`: stable integer error codes and messages.
- `PlayerConfig`: latency, write chunk, ring duration, prebuffer percentage, and decoder error threshold.
- `OutputConfig`: Android output encoding plus human-readable display information.

## 4. JNI Boundary

The Kotlin declarations map to `Java_com_sonar_core_NativeBridge_*` functions in `src/main/cpp/jni/native_bridge.cpp`.

| Direction | Data |
|---|---|
| Kotlin -> C++ | Engine handle, path, control commands, seek position, encoding |
| C++ -> Kotlin | Integer result, state, position, error text, `StreamInfo`, PCM frames |
| PCM path | Kotlin-owned direct `ByteBuffer`, filled in native code without an intermediate JNI byte array |

Native handles are stored in a synchronized registry. A handle must be present in the registry and have the expected magic value before it is dereferenced. Direct buffers are checked for non-null address and sufficient capacity before conversion.

## 5. Native Engine

### `PlaybackEngine`

`PlaybackEngine` coordinates the decoder, ring buffer, conversion, and state transitions. It owns the C++ decode thread and uses a mutex for lifecycle mutations plus a condition variable for pause/resume/stop wakeups.

Important operations:

- `open()`: stop old resources, create a decoder, allocate stream-sized buffers, and expose stream info.
- `play()`: transition `OPENED` or `PAUSED` to `BUFFERING` and start/notify decoding.
- `pause()`: transition active playback to `PAUSED`.
- `resume()`: transition `PAUSED` to `PLAYING`.
- `stop()`: stop/join decoding, close the decoder, reset buffers, and return to `IDLE`.
- `seek()`: stop decoding, seek the decoder, reset the ring, restore the appropriate state, and restart when needed.
- `readPcm()`: consume float frames from the ring and convert them into the requested output encoding.
- `setOutputFormat()`: atomically change the conversion encoding after validating it.

### State Flow

```text
IDLE --open--> OPENED --play--> BUFFERING --prebuffer--> PLAYING
                                  ^                         |
                                  |                         v
                               PAUSED <--pause---------- PLAYING
                                  |
                               resume
                                  v
                               PLAYING

Any active state --stop--> IDLE
Decoder or lifecycle failure --> ERROR
End of decoder input --> COMPLETED
```

The Kotlin write loop observes `COMPLETED` and drains the remaining PCM before ending its writer thread.

### `RingBuffer`

`RingBuffer` is a preallocated single-producer/single-consumer queue of interleaved float frames. The decode thread is the only producer; the Kotlin AudioTrack write thread is the only consumer. Atomic read/write counters avoid a mutex in the PCM hot path.

### `FormatConverter`

The canonical engine format is interleaved float32. The converter supports:

| Output | Bytes/sample | Behavior |
|---|---:|---|
| `PCM_16BIT` | 2 | Clamp and scale to signed 16-bit |
| `PCM_24BIT_PACKED` | 3 | Clamp, scale, and pack little-endian 24-bit samples |
| `PCM_32BIT` | 4 | Clamp and scale to signed 32-bit |
| `PCM_FLOAT` | 4 | Float pass-through |

The engine does not resample. `AudioTrack` is created at the source sample rate and Android's mixer may resample if the route requires it.

## 6. Decoder Layer

All decoders implement `IDecoder`:

```text
open(path)
decodeNextFrame(float* output, maxFrames)
seek(positionMs)
close()
getStreamInfo()
positionMs()
```

`DecoderFactory` uses content magic first and file extension only as a fallback hint. This prevents a file renamed with the wrong extension from selecting the wrong decoder.

| Decoder | Container/codec | Implementation |
|---|---|---|
| `Mp3Decoder` | MPEG MP3 | `minimp3` / `minimp3_ex` |
| `FlacDecoder` | Native FLAC | Decoder-only `libFLAC` |
| `WavDecoder` | RIFF/WAVE | Custom parser and PCM reader |
| `VorbisDecoder` | OGG/Vorbis | `libvorbisfile` + `libvorbis` + Ogg |
| `OpusDecoder` | OGG/Opus | `libopusfile` + Opus + Ogg |

`WavDecoder` accepts PCM integer 8/16/24/32-bit and IEEE float32, mono/stereo, arbitrary RIFF chunk order, and recoverable real-world headers where the byte rate confirms the intended block alignment. RF64, WAVE extensible formats, unsupported channel counts, and structurally inconsistent/truncated files are rejected.

## 7. Third-Party Build

Native dependencies are placed under `src/main/cpp/third_party`:

- Ogg `v1.3.6`.
- Opus `v1.6.1`.
- Opusfile `v0.12`, built from local-file decoder sources only; HTTP sources are omitted.
- Vorbis `v1.3.7`, built with decoder and `vorbisfile` sources; encoder target is omitted.
- FLAC `1.5.0`, with encoder sources removed from the target after upstream configuration.
- minimp3 headers at the revision documented in `src/main/cpp/CMakeLists.txt`.

All third-party libraries are static and compiled into the Android shared library `libsonar_core.so`. CMake disables upstream tests, examples, programs, docs, shared-library builds, and unnecessary install packaging where supported.

## 8. Threading and Ownership

| Thread | Owner | Responsibility |
|---|---|---|
| Main/UI | Android application | User commands and UI polling |
| Decode | `PlaybackEngine` | File I/O, decoding, ring-buffer production |
| Write | `AudioTrackWrapper` | JNI PCM pull and blocking AudioTrack writes |

Ownership rules:

- `PlaybackEngine` owns the decoder, ring, decode buffers, and decode thread.
- `SonarPlayer` owns the native handle, wrapper, session ID, and output preference.
- `AudioTrackWrapper.release()` deactivates the writer before releasing the Android track.
- `SonarPlayer.release()` releases the wrapper before destroying the native engine.
- Native lifecycle commands are serialized by engine/host locks; PCM consumption remains SPSC.

## 9. Error Handling

| Code | Constant | Meaning |
|---:|---|---|
| `0` | `OK` | Success |
| `-1` | `ERR_FILE_NOT_FOUND` | Missing/inaccessible file |
| `-2` | `ERR_FILE_READ` | Read/truncation/structural file error |
| `-3` | `ERR_UNSUPPORTED_FORMAT` | Unsupported container, codec, or channel count |
| `-4` | `ERR_DECODER_INIT` | Decoder initialization failure |
| `-5` | `ERR_DECODER_DECODE` | Frame decode failure |
| `-6` | `ERR_INVALID_STATE` | Invalid lifecycle operation |
| `-7` | `ERR_SEEK_FAILED` | Seek failure |
| `-8` | `ERR_OUTPUT_FORMAT` | No usable AudioTrack encoding could be created |
| `-9` | `ERR_INTERNAL` | Unexpected internal failure |

Native errors are converted into integer codes and optional text. Kotlin output fallback treats an individual high-resolution track creation failure as a capability result and continues toward PCM16. An error is reported only when the complete fallback chain fails.

## 10. Tests and Validation

### Kotlin

- `OutputConfigNegotiatorTest`: source/codec gating, API-level fallback, probe caching, format mapping, and runtime candidate chain.
- `PlayerMappingsTest`: native integer mappings for states and errors.
- `NativeContractInstrumentedTest`: invalid handle behavior, missing file behavior, and audio-session lifecycle.

### Native

`cpp_test/native_smoke_tests.cpp` covers:

- Ring buffer basic operations and concurrent producer/consumer behavior.
- Float-to-PCM conversion and clipping/pass-through behavior.
- WAV parser chunk ordering, integer/float variants, recoverable block alignment, unsupported channels, empty files, and extension/content mismatches.
- MP3, FLAC, OGG/Vorbis, OGG/Opus, and WAV reference files.
- Decoder seek and first-frame decode.
- Playback engine invalid transitions, decode completion, and output format changes.

## 11. External Audio Effects

The core does not implement EQ, DVC, bass boost, reverb, or other DSP. It creates a standard `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` `AudioTrack` and exposes the stable session ID. External effects may alter the actual output encoding or sample path; this is expected Android behavior and is reported by the current output description rather than hidden.

## 12. Known Limitations

- AAC/M4A is not implemented.
- Only mono and stereo are supported.
- No resampling is performed by the core.
- No playlist manager or media catalog is included.
- `next(uri)` and `previous(uri)` reopen the URI supplied by the caller; playlist ordering belongs to the application layer.
- No gapless playback or crossfade.
- No Android `MediaSession`, notification controls, audio-focus policy, or process-death recovery.
- Bit depth accepted by `AudioTrack` does not guarantee bit-perfect delivery through AudioFlinger, EQ/DVC, or the hardware route.
