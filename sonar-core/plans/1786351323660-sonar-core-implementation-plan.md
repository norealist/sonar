# Sonar Core Implementation Plan

## Decisions

- Build only `:sonar-core` and `:test-harness`; exclude production UI, catalog/playlist, DSP, focus, MediaSession, notifications, AAC/M4A, gapless, crossfade, ReplayGain, cloud, lyrics, Vorbis, multichannel audio, and resampling.
- Pin AGP 9.3.0, Kotlin 2.3.21, Gradle 9.5, compile/target SDK 37, min SDK 24, JDK 17, NDK r27d LTS (`27.3.13750724`), and compatible SDK CMake 3.31.x. Local SDK has API 37/Build Tools 36.0.0 but no NDK/CMake, so bootstrap installs or reports them.
- Preserve JNI names but treat `outputBitDepth`/`bitDepth` as `AudioFormat.ENCODING_PCM_*` constants, allowing `PCM_FLOAT`. Create native engine provisionally at 48 kHz/stereo/PCM 16-bit; replace rate/channels after open, negotiate Kotlin output, then set native output format.
- `test_2_44.1khz.ogg` is Vorbis, not Opus. Do not add Vorbis. Generate a deterministic valid OGG Opus fixture, use it for Opus tests, and assert the supplied file is unsupported.

## Implementation Order

1. Bootstrap Gradle wrapper, root/module Kotlin DSL, settings/properties, library/application modules, namespaces, min SDK 24, compile/target 37, C++17, CMake externalNativeBuild, and `implementation(project(":sonar-core"))`.
2. Initialize Git only for submodules. Add `.gitmodules` for Ogg v1.3.6, Opus v1.6.1, Opusfile v0.12, and FLAC 1.5.0 under `src/main/cpp/third_party`; copy pinned minimp3 headers. Disable upstream tests/programs/examples/docs/shared libraries and Opus URL support.
3. Build a static decoder-only FLAC target from upstream sources, excluding `stream_encoder*` and encoder framing; link Ogg, Opus, Opusfile, and this target into `sonar_core`.
4. Add `engine_types.h`/`engine_config.h`: seven states, nine negative errors plus OK, output encoding constants, `StreamInfo`, decoder results, 200 ms ring, 50% prebuffer, and 50 consecutive-error limit.
5. Implement preallocated lock-free SPSC float-frame `RingBuffer` with acquire/release atomic indices, reset/wrap/full/empty handling, and no hot-path allocation. Implement `FormatConverter` for float, signed 16, packed signed 24 LE, and signed 32 with clamp/truncation.
6. Implement `IDecoder` and `WavDecoder`: checked RIFF/WAVE chunks in arbitrary order, PCM 8/16/24/32 and IEEE float 32, mono/stereo, duration/sample seek; reject RF64, malformed/truncated/unsupported data and channels above two.
7. Implement `PlaybackEngine` with state mutex, atomic polling, condition-variable decode thread, 50% prebuffer, underrun buffering, pause/resume, seek reset, completion after buffer drain, safe stop/reopen, fast error-code controls, and >50 consecutive decode errors to ERROR.
8. Implement MP3 via `minimp3_ex` with float conversion, lossy/16-bit metadata, frame metadata and seek. Implement OGG Opus via `libopusfile` (`op_read_float`, `op_pcm_seek`, total PCM), requiring OpusHead and rejecting Vorbis. Implement FLAC via `FLAC__StreamDecoder` callbacks, STREAMINFO, integer-to-float conversion, absolute seek, and channel validation.
9. Implement `DecoderFactory` with bounded magic detection for RIFF/WAVE, fLaC, OggS+OpusHead, and ID3/MPEG sync; actual content wins over extension and unsupported input returns `ERR_UNSUPPORTED_FORMAT`.
10. Implement JNI with checked handles, direct-buffer validation, UTF-8 paths, cached StreamInfo constructor, and no propagated exceptions. `nativeReadPcm` converts directly into ByteBuffer and returns frames/zero/negative error.
11. Add Kotlin `StreamInfo`, aligned `PlayerState`, `SonarError`, `PlayerConfig`, `NativeBridge`, and `SessionManager` with one session ID per `SonarPlayer` lifecycle.
12. Implement `OutputConfigNegotiator`: probe/cache per player and re-probe each open. Setting on and source >=24: API 31+ `PCM_32BIT -> PCM_24BIT_PACKED -> PCM_FLOAT -> PCM_16BIT`; API 24-30 `PCM_FLOAT -> PCM_16BIT`; 16-bit/lossy or off always PCM 16-bit. Display float separately from integer 32-bit.
13. Implement `AudioTrackWrapper` with media/music attributes, MODE_STREAM, source format, mono/stereo mask, negotiated encoding, minimum/40 ms buffer, stable session, one direct block buffer, dedicated blocking write thread, zero-frame sleep, completion/error handling, safe release, and `AudioTrack.setVolume()` only.
14. Implement `SonarPlayer`: open -> native info -> negotiation -> native format -> AudioTrack setup; all controls/position/state/error/session/output properties, volume, serialized error-code API, and switching as stop/open/play without playlist state.
15. Add Kotlin unit tests for state/error mappings and every negotiation branch with injectable probe seams.
16. Add one plain harness Activity with ACTION_OPEN_DOCUMENT, local-path handling, controls, SeekBar, bit-depth toggle, metadata/state/session/error fields, and 100-250 ms state/position polling.
17. Add standalone pinned GoogleTest CMake tests for ring concurrency/wrap/full/empty, conversion edge/clamp/24-bit, WAV variants/chunks/seeks, all decoders, factory mismatches, and engine transitions. Generate corrupt/empty/mono/32-bit/truncated/unsupported fixtures deterministically.
18. Add Android instrumented tests for WAV end-to-end, all formats, seeks, 100-operation stress, track/rate/encoding switches, negotiation, missing/empty/corrupt files, unsupported channels, and JNI errors. Assert no crash/deadlock and stable state/error values.

## Validation

- Install API 37, Build Tools 36.0.0, pinned NDK/CMake; verify Gradle/JDK.
- After each native milestone run CMake and Gradle native debug builds.
- Run `:sonar-core:testDebugUnitTest`, native GoogleTest, both assembleDebug tasks, and connected instrumented tests when a device exists.
- Probe corrected Opus and assert original Vorbis rejection.
- Run ASan/LSan where supported and TSan separately; measure threads, native heap, CPU, and latency.

## Risks and TODOs

- AudioTrack probing is device-specific; PCM 16-bit failure maps to `ERR_OUTPUT_FORMAT`.
- Physical latency/RAM/CPU targets require measurement; MP3 VBR seek depends on minimp3 scanning; Opus remains native 48 kHz and mixer resampling is external.
- Process death, focus, external EQ/DVC, and output-route invalidation remain app responsibilities.
- AAC/M4A, Vorbis, multichannel, gapless, crossfade, ReplayGain, and dithering remain intentionally unsupported.
- Preserve source-level decoder-only FLAC if upstream CMake changes.
