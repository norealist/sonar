# Sonar Audio Core

Sonar Audio Core is a local-file audio playback core for Android. It combines a Kotlin host layer, a small JNI bridge, and a C++ decoding/playback engine.

The project is intended to be embedded as the `:sonar-core` Android library. The `:test-harness` module is a minimal application used to verify playback, metadata, output-format negotiation, and player state transitions on a real device.

## Features

- MP3 decoding through header-only `minimp3`.
- FLAC decoding through decoder-only `libFLAC`.
- WAV decoding without an external library.
- OGG/Vorbis decoding through `libvorbisfile`.
- OGG/Opus decoding through `libopusfile`.
- 8/16/24/32-bit PCM WAV input and 32-bit IEEE-float WAV input.
- Mono and stereo playback.
- Float32 interleaved PCM as the internal engine format.
- Runtime output fallback through `PCM_32BIT`, packed `PCM_24BIT`, `PCM_FLOAT`, and `PCM_16BIT`.
- Stable Android audio session ID for external audio effects.
- Lock-free native SPSC ring buffer between decoder and output reader.
- Kotlin `AudioTrack` writer with safe release and stale-error suppression.
- JNI error codes instead of propagated native exceptions.

The core handles local files only. AAC/M4A, DRM, network streams, multichannel output, resampling, gapless playback, playlists, and DSP effects are outside the current scope.

## Project Layout

```text
sonar-core/
├── sonar-core/                 Android library module
│   ├── src/main/kotlin/         Kotlin host API and AudioTrack layer
│   ├── src/main/cpp/            Gradle CMake entry point
│   └── src/test/                Kotlin unit tests
├── test-harness/                Minimal Android verification app
├── src/main/cpp/                Authoritative native C++ source tree
│   ├── buffer/                  Lock-free PCM ring buffer
│   ├── decoder/                 Decoder interface, factory, and codecs
│   ├── engine/                  Playback engine and state machine
│   ├── format/                  Float-to-output PCM conversion
│   ├── jni/                     NativeBridge implementation
│   └── third_party/             Pinned decoder dependencies
├── cpp_test/                    Standalone native smoke/reference tests
├── test_audio_files/            Reference files used by native tests
├── CODE_ARCHITECTURE.md         Current implementation architecture
└── ARCHITECTURE.md              Original detailed design specification
```

The module-local CMake file at `sonar-core/src/main/cpp/CMakeLists.txt` forwards to the authoritative root native tree. This keeps the normal Android Gradle layout while allowing the standalone `cpp_test` target to build the same native sources.

## Requirements

The project is pinned to the following toolchain:

- Android SDK Platform 37.
- Android Build Tools 36.0.0 or a compatible installed 36.x version.
- Android NDK `27.3.13750724`.
- Android CMake `3.31.0`.
- JDK 17.
- Gradle Wrapper 9.5.0.
- Android Gradle Plugin 9.3.0.
- Kotlin 2.3.21 through the approved root plugin configuration.

On Windows, the SDK is normally installed at:

```text
C:\Users\<user>\AppData\Local\Android\Sdk
```

## Build

Set the SDK and JDK paths if they are not already visible to Gradle:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "C:\Users\<user>\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

Build the library and test harness:

```powershell
.\gradlew.bat :sonar-core:assembleDebug :test-harness:assembleDebug
```

The outputs are:

```text
sonar-core/build/outputs/aar/sonar-core-debug.aar
test-harness/build/outputs/apk/debug/test-harness-debug.apk
```

## Tests

Run Kotlin unit tests:

```powershell
.\gradlew.bat :sonar-core:testDebugUnitTest
```

Run the native reference suite with the Visual Studio CMake executable or another CMake 3.22+ installation:

```powershell
cmake -S cpp_test -B cpp_test/build -DCMAKE_BUILD_TYPE=Debug
cmake --build cpp_test/build --config Debug
ctest --test-dir cpp_test/build -C Debug --output-on-failure
```

The native suite covers ring-buffer operations and concurrency, PCM conversion, WAV variants, decoder factory behavior, MP3, FLAC, OGG/Vorbis, OGG/Opus, malformed inputs, and playback-engine transitions.

## Test Harness

Install the debug harness on a connected device:

```powershell
adb install -r test-harness/build/outputs/apk/debug/test-harness-debug.apk
```

The harness provides:

- File selection through `ACTION_OPEN_DOCUMENT`.
- Artist, title, album, filename, codec, sample rate, channel, and bit-depth display.
- Standard `Prev / Play / Next` controls.
- `Debug player` mode with separate `Play / Pause / Resume / Stop` controls.
- Seek position and duration.
- High-resolution output preference with the actual selected output encoding displayed beside it.
- Player state, stable audio session ID, and error display.

The output preference is best-effort. Android AudioFlinger, the device HAL, USB/Bluetooth routes, or external processing such as an EQ/DVC layer may force the final device path to another format. The UI therefore reports the format accepted by the current `AudioTrack`, not a guarantee of bit-perfect DAC delivery.

## Public Kotlin API

The primary entry point is `com.sonar.core.SonarPlayer`:

```kotlin
val player = SonarPlayer(context)
val result = player.open(uri)
if (result.isOk) {
    player.play()
}

player.pause()
player.resume()
player.seekTo(30_000)
player.setVolume(0.8f)
player.stop()
player.release()
```

`SonarPlayer` exposes `state`, `streamInfo`, `positionMs`, `audioSessionId`, `outputEncoding`, `outputBitDepth`, `outputDescription`, and `error`.

## Error Codes

| Code | Name | Meaning |
|---:|---|---|
| `0` | `OK` | Operation completed successfully. |
| `-1` | `ERR_FILE_NOT_FOUND` | File is missing or inaccessible. |
| `-2` | `ERR_FILE_READ` | File is truncated or cannot be read. |
| `-3` | `ERR_UNSUPPORTED_FORMAT` | Container or codec is not supported. |
| `-4` | `ERR_DECODER_INIT` | Decoder initialization failed. |
| `-5` | `ERR_DECODER_DECODE` | Decoder failed while reading a frame. |
| `-6` | `ERR_INVALID_STATE` | Operation is invalid for the current player state. |
| `-7` | `ERR_SEEK_FAILED` | Seek operation failed. |
| `-8` | `ERR_OUTPUT_FORMAT` | No usable `AudioTrack` output format was available. |
| `-9` | `ERR_INTERNAL` | Unexpected internal failure. |

## Dependencies

Native dependencies are pinned in `.gitmodules` and configured as static decoder dependencies:

| Dependency | Version | License | Purpose |
|---|---|---|---|
| Ogg | `v1.3.6` | BSD-style, see upstream `COPYING` | OGG container support |
| Opus | `v1.6.1` | BSD-style, see upstream `COPYING` | Opus codec |
| Opusfile | `v0.12` | BSD-style, see upstream `COPYING` | Local OGG/Opus decoding |
| Vorbis | `v1.3.7` | BSD-style, see upstream `COPYING` | OGG/Vorbis decoding |
| FLAC | `1.5.0` | Xiph license, see upstream `COPYING.Xiph` | FLAC decoding |
| minimp3 | pinned revision | CC0 1.0, see upstream `LICENSE` | Header-only MP3 decoding |

Source repositories:

- Ogg: <https://github.com/xiph/ogg>
- Opus: <https://github.com/xiph/opus>
- Opusfile: <https://github.com/xiph/opusfile>
- Vorbis: <https://github.com/xiph/vorbis>
- FLAC: <https://github.com/xiph/flac>
- minimp3: <https://github.com/lieff/minimp3>

The exact source commits, local license paths, build policy, AndroidX test dependencies, and generated fixture provenance are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

All native dependencies are statically linked into `libsonar_core.so`. Encoder targets, tools, examples, tests, HTTP support, and documentation targets are excluded from the Android runtime build where applicable.

## Further Documentation

- [Current code architecture](CODE_ARCHITECTURE.md)
- [Original architecture specification](ARCHITECTURE.md)
- [Implementation plan](plans/1786351323660-sonar-core-implementation-plan.md)
- [Third-party notices and provenance](THIRD_PARTY_NOTICES.md)
