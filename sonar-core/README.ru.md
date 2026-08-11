# Sonar Audio Core

Sonar Audio Core — локальное аудио-ядро для Android. Проект объединяет Kotlin host layer, тонкий JNI-мост и C++ engine для декодирования, буферизации и воспроизведения аудио.

Основной артефакт проекта — Android-библиотека `:sonar-core`. Модуль `:test-harness` — минимальное приложение для проверки воспроизведения, metadata, output negotiation и переходов состояний на реальном устройстве.

## Возможности

- MP3 через header-only `minimp3`.
- FLAC через decoder-only `libFLAC`.
- WAV без внешней библиотеки.
- OGG/Vorbis через `libvorbisfile`.
- OGG/Opus через `libopusfile`.
- PCM WAV 8/16/24/32-bit и 32-bit IEEE float WAV.
- Mono и stereo.
- Канонический внутренний формат: interleaved PCM float32.
- Runtime fallback вывода: `PCM_32BIT` → packed `PCM_24BIT` → `PCM_FLOAT` → `PCM_16BIT`.
- Стабильный Android audio session ID для внешних аудиоэффектов.
- Lock-free SPSC ring buffer между decoder thread и write thread.
- Kotlin `AudioTrack` writer с безопасным release и подавлением устаревших ошибок.
- JNI error codes вместо native exceptions.

Ядро работает только с локальными файлами. AAC/M4A, DRM, network streams, multichannel output, resampling, gapless playback, playlist и DSP-эффекты находятся вне текущего scope.

## Структура проекта

```text
sonar-core/
├── sonar-core/                 Android library module
│   ├── src/main/kotlin/         Kotlin host API и AudioTrack layer
│   ├── src/main/cpp/            Gradle CMake entry point
│   └── src/test/                Kotlin unit tests
├── test-harness/                Минимальное Android verification app
├── src/main/cpp/                Основное дерево native C++ исходников
│   ├── buffer/                  Lock-free PCM ring buffer
│   ├── decoder/                 Decoder interface, factory и codecs
│   ├── engine/                  Playback engine и state machine
│   ├── format/                  Float-to-output PCM conversion
│   ├── jni/                     NativeBridge implementation
│   └── third_party/             Pinned decoder dependencies
├── cpp_test/                    Standalone native tests
├── test_audio_files/            Reference audio files
├── CODE_ARCHITECTURE.md         Архитектура текущей реализации
└── ARCHITECTURE.md              Исходная подробная design specification
```

Файл `sonar-core/src/main/cpp/CMakeLists.txt` является module-local entry point для Android Gradle Plugin и перенаправляет сборку в корневое authoritative native tree. Благодаря этому Android и standalone `cpp_test` используют один и тот же C++ код.

## Требования

Проект зафиксирован на следующем toolchain:

- Android SDK Platform 37.
- Android Build Tools 36.0.0 или совместимый 36.x.
- Android NDK `27.3.13750724`.
- Android CMake `3.31.0`.
- JDK 17.
- Gradle Wrapper 9.5.0.
- Android Gradle Plugin 9.3.0.
- Kotlin 2.3.21 в approved root configuration.

Для Windows SDK обычно находится здесь:

```text
C:\Users\<user>\AppData\Local\Android\Sdk
```

## Сборка

Если Gradle не видит JDK и Android SDK, задайте переменные окружения:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "C:\Users\<user>\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

Сборка библиотеки и test harness:

```powershell
.\gradlew.bat :sonar-core:assembleDebug :test-harness:assembleDebug
```

Артефакты:

```text
sonar-core/build/outputs/aar/sonar-core-debug.aar
test-harness/build/outputs/apk/debug/test-harness-debug.apk
```

## Тесты

Kotlin unit tests:

```powershell
.\gradlew.bat :sonar-core:testDebugUnitTest
```

Standalone native tests:

```powershell
cmake -S cpp_test -B cpp_test/build -DCMAKE_BUILD_TYPE=Debug
cmake --build cpp_test/build --config Debug
ctest --test-dir cpp_test/build -C Debug --output-on-failure
```

Native suite проверяет ring buffer и concurrent producer/consumer, PCM conversion, WAV variants, decoder factory, MP3, FLAC, OGG/Vorbis, OGG/Opus, malformed inputs и playback-engine transitions.

## Test Harness

Установка debug APK на подключенное устройство:

```powershell
adb install -r test-harness/build/outputs/apk/debug/test-harness-debug.apk
```

В приложении доступны:

- Выбор файла через `ACTION_OPEN_DOCUMENT`.
- Artist, title, album, filename, codec, sample rate, channels и source bit depth.
- Обычные кнопки `Prev / Play / Next`.
- Режим `Debug player` с отдельными `Play / Pause / Resume / Stop`.
- Seek position и duration.
- Переключатель high-resolution output и фактический output encoding рядом с ним.
- Player state, стабильный audio session ID и error display.

Настройка high-resolution output является best-effort. Android AudioFlinger, device HAL, USB/Bluetooth route или внешняя обработка вроде EQ/DVC могут выбрать другой фактический формат. Поэтому UI показывает формат, принятый текущим `AudioTrack`, а не гарантирует bit-perfect доставку до ЦАП.

## Публичный Kotlin API

Главный entry point — `com.sonar.core.SonarPlayer`:

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

`SonarPlayer` предоставляет `state`, `streamInfo`, `positionMs`, `audioSessionId`, `outputEncoding`, `outputBitDepth`, `outputDescription` и `error`.

## Коды ошибок

| Код | Имя | Значение |
|---:|---|---|
| `0` | `OK` | Операция выполнена успешно. |
| `-1` | `ERR_FILE_NOT_FOUND` | Файл отсутствует или недоступен. |
| `-2` | `ERR_FILE_READ` | Файл усечен или не может быть прочитан. |
| `-3` | `ERR_UNSUPPORTED_FORMAT` | Container или codec не поддерживается. |
| `-4` | `ERR_DECODER_INIT` | Не удалось инициализировать decoder. |
| `-5` | `ERR_DECODER_DECODE` | Ошибка чтения decoder frame. |
| `-6` | `ERR_INVALID_STATE` | Операция невозможна в текущем состоянии. |
| `-7` | `ERR_SEEK_FAILED` | Seek завершился ошибкой. |
| `-8` | `ERR_OUTPUT_FORMAT` | Не удалось создать пригодный `AudioTrack`. |
| `-9` | `ERR_INTERNAL` | Непредвиденная внутренняя ошибка. |

## Зависимости

Native dependencies зафиксированы в `.gitmodules` и собираются как static decoder dependencies:

| Зависимость | Версия | Лицензия | Назначение |
|---|---|---|---|
| Ogg | `v1.3.6` | BSD-style, upstream `COPYING` | Поддержка OGG container |
| Opus | `v1.6.1` | BSD-style, upstream `COPYING` | Opus codec |
| Opusfile | `v0.12` | BSD-style, upstream `COPYING` | Декодирование local OGG/Opus |
| Vorbis | `v1.3.7` | BSD-style, upstream `COPYING` | Декодирование OGG/Vorbis |
| FLAC | `1.5.0` | Xiph license, upstream `COPYING.Xiph` | Декодирование FLAC |
| minimp3 | pinned revision | CC0 1.0, upstream `LICENSE` | Header-only MP3 decoder |

Исходные репозитории:

- Ogg: <https://github.com/xiph/ogg>
- Opus: <https://github.com/xiph/opus>
- Opusfile: <https://github.com/xiph/opusfile>
- Vorbis: <https://github.com/xiph/vorbis>
- FLAC: <https://github.com/xiph/flac>
- minimp3: <https://github.com/lieff/minimp3>

Точные source commits, локальные пути к license-файлам, build policy, AndroidX test dependencies и происхождение generated fixtures описаны в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Все native dependencies статически линкуются в `libsonar_core.so`. Encoder targets, tools, examples, tests, HTTP support и documentation targets исключены из Android runtime build там, где это применимо.

## Дополнительная документация

- [Архитектура текущего кода](CODE_ARCHITECTURE.md)
- [Исходная архитектурная спецификация](ARCHITECTURE.md)
- [Implementation plan](plans/1786351323660-sonar-core-implementation-plan.md)
- [Third-party notices и provenance](THIRD_PARTY_NOTICES.md)
