# Third-Party Notices

This file records the external source repositories used by Sonar Audio Core, the exact source revisions selected for the current build, their role in the project, and the local license files retained with the source.

The native sources are stored under `src/main/cpp/third_party`. Their internal Git metadata is not required for the build; provenance is recorded here and in `.gitmodules`.

## Native Runtime Dependencies

### libogg

- Source: <https://github.com/xiph/ogg>
- Tag: `v1.3.6`
- Resolved commit: `be05b13e98b048f0b5a0f5fa8ce514d56db5f822`
- Local source: `src/main/cpp/third_party/ogg`
- License: BSD-style license
- License file: `src/main/cpp/third_party/ogg/COPYING`
- Used for: OGG container parsing for Vorbis and Opus streams
- Build: static library; upstream tests, documentation, and install packaging are disabled

### libopus

- Source: <https://github.com/xiph/opus>
- Tag: `v1.6.1`
- Resolved commit: `22244de5a79bd1d6d623c32e72bf1954b56235be`
- Local source: `src/main/cpp/third_party/opus`
- License: BSD-style license
- License file: `src/main/cpp/third_party/opus/COPYING`
- Used for: Opus codec support through libopusfile
- Build: static library; programs, tests, and install packaging are disabled

### libopusfile

- Source: <https://github.com/xiph/opusfile>
- Tag: `v0.12`
- Resolved commit: `a55c164e9891a9326188b7d4d216ec9a88373739`
- Local source: `src/main/cpp/third_party/opusfile`
- License: BSD-style license
- License file: `src/main/cpp/third_party/opusfile/COPYING`
- Used for: local OGG/Opus file decoding
- Build: static decoder target assembled by the project CMake file
- Network behavior: HTTP and URL sources are intentionally omitted; the core is local-file-only

### libvorbis

- Source: <https://github.com/xiph/vorbis>
- Tag: `v1.3.7`
- Resolved commit: `0657aee69dec8508a0011f47f3b69d7538e9d262`
- Local source: `src/main/cpp/third_party/vorbis`
- License: BSD-style license
- License file: `src/main/cpp/third_party/vorbis/COPYING`
- Used for: OGG/Vorbis decoding through `libvorbisfile`
- Build: decoder and `vorbisfile` static targets only; encoder target is excluded

### libFLAC

- Source: <https://github.com/xiph/flac>
- Tag: `1.5.0`
- Resolved commit: `1507800de4b70e21be71f38caa0d9079d0bc6e45`
- Local source: `src/main/cpp/third_party/flac`
- License: Xiph license for the library sources
- License file: `src/main/cpp/third_party/flac/COPYING.Xiph`
- Additional upstream license files: `COPYING.GPL`, `COPYING.LGPL`, and `COPYING.FDL`
- Used for: FLAC decoding
- Build: static decoder-only target; encoder, C++ wrapper, programs, examples, tests, docs, and install targets are excluded

### minimp3

- Source: <https://github.com/lieff/minimp3>
- Resolved revision: `ea99364f61c14656440e8d77e9c233ccf3124633`
- Local source: `src/main/cpp/third_party/minimp3`
- Retained files: `minimp3.h`, `minimp3_ex.h`
- License: CC0 1.0 Universal
- Upstream license: <https://github.com/lieff/minimp3/blob/ea99364f61c14656440e8d77e9c233ccf3124633/LICENSE>
- Used for: header-only MP3 decoding
- Build: included directly in the native MP3 decoder target

## Android and Test Dependencies

These dependencies are build- or test-time dependencies, not additional native runtime codecs:

| Dependency | Version | Role | License/source |
|---|---|---|---|
| Android Gradle Plugin | `9.3.0` | Android build plugin | [Android build releases](https://developer.android.com/build/releases/gradle-plugin) |
| Kotlin | `2.3.21` | Kotlin compilation | [Kotlin](https://github.com/JetBrains/kotlin), Apache 2.0 |
| Gradle Wrapper | `9.5.0` | Build execution | [Gradle](https://gradle.org/releases/), Apache 2.0 |
| Android SDK Platform | `37` | Compile/target API | [Android SDK](https://developer.android.com/studio) |
| Android NDK | `27.3.13750724` | Native Android toolchain | [Android NDK](https://developer.android.com/ndk) |
| Android CMake | `3.31.0` | Native build system | [Android Studio tooling](https://developer.android.com/studio/projects/install-ndk) |
| JDK | `17` | Java/Kotlin toolchain | Local JDK installation; vendor-specific license |
| JUnit | `4.13.2` | JVM unit tests | [JUnit 4](https://github.com/junit-team/junit4), EPL 1.0 |
| AndroidX Test Runner | `1.6.2` | Instrumented tests | [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test), Apache 2.0 |
| AndroidX Test Rules | `1.6.1` | Instrumented tests | [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test), Apache 2.0 |
| AndroidX Test JUnit extension | `1.2.1` | Instrumented tests | [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test), Apache 2.0 |

The exact dependency declarations are in `build.gradle.kts` files. Android SDK and build-tool licenses are accepted through the local Android SDK installation and are not redistributed by this repository.

## Generated Test Fixture

`test_audio_files/test_opus_48khz.ogg` is a test-only OGG/Opus fixture generated from a deterministic 440 Hz sine source using FFmpeg's `libopus` encoder. It is not packaged into the production AAR or APK.

- Tool: FFmpeg `8.0` full build
- Source: <https://ffmpeg.org>
- Role: local test-fixture generation only
- Distribution: no FFmpeg binary or FFmpeg source is included in Sonar artifacts

The remaining files in `test_audio_files/` are reference inputs used by the native decoder tests. They are not production runtime dependencies.

## Build Policy

The Android native library is produced as `libsonar_core.so` and statically links the decoder libraries listed above. The project intentionally does not build or package:

- MP3/FLAC/Vorbis/Opus encoder targets.
- OGG/Vorbis or Opus command-line tools.
- Opusfile HTTP/URL support.
- Third-party examples, documentation generators, and test programs.
- Network streaming code.

Before redistributing a release artifact, retain the relevant notices required by each upstream license and review the current license files in the vendored source trees.
