Ты — autonomous coding agent. Твоя задача — реализовать `sonar-core` по уже готовому `ARCHITECTURE.md`.

---

## Главный результат

* Рабочее Android NDK аудиоядро (library module `:sonar-core`).
* Kotlin/JNI bridge только там, где это нужно для core.
* Минимальный временный Android test harness (app module `:test-harness`) для проверки ядра.
* Никаких production UI, библиотек, Deezer, артистов, плиток, тем, анимаций и прочего app-level слоя.

---

## Project Bootstrap

Проект **не создан** — в репозитории только `ARCHITECTURE.md`, `CORE_PROMPT.md` и `test_audio_files/`.

Создай Android-проект с нуля по этим параметрам:

| Параметр | Значение |
|----------|----------|
| Структура | Два Gradle-модуля: `:sonar-core` (Android Library) и `:test-harness` (Android Application) |
| Package name (core) | `com.sonar.core` |
| Package name (harness) | `com.sonar.testharness` |
| `minSdk` | **24** (Android 7.0) |
| `compileSdk` / `targetSdk` | Последний стабильный (35+) |
| Kotlin | Последняя стабильная версия |
| Gradle / AGP | Последние стабильные версии |
| NDK | Последняя стабильная LTS-версия |
| Build system для C++ | **CMake** (через `externalNativeBuild` в Gradle) |
| `:test-harness` зависит от `:sonar-core` | `implementation(project(":sonar-core"))` |

---

## Third-party библиотеки (C++)

| Библиотека | Способ подключения |
|---|---|
| `minimp3` | Header-only. Скопировать `minimp3.h` + `minimp3_ex.h` в `cpp/third_party/minimp3/`. |
| `libopus`, `libogg`, `libopusfile` | Git submodules в `cpp/third_party/`. Подключить через CMake `add_subdirectory()`. |
| `libFLAC` | Git submodule в `cpp/third_party/`. Подключить через CMake `add_subdirectory()`. Собирать только decoder (без encoder). |

Все submodules инициализируй и пропиши в `.gitmodules`.

---

## Жёсткие границы

* Делай только `sonar-core` + `test-harness`.
* Финальное приложение Sonar не реализуй.
* Production UI не трогай.
* AAC / M4A в v1 не реализуй.
* Multichannel > 2ch в v1 отклоняй как unsupported (`ERR_UNSUPPORTED_FORMAT`).
* Gapless playback, crossfade, ReplayGain, cloud sync, lyrics, recommendations, MediaSession, notification controls, audio focus orchestration — не в scope ядра.
* Не делай USB direct / bit-perfect mode.
* Не делай DSP-эффекты внутри ядра.
* Не делай software volume в C++ — используй `AudioTrack.setVolume()` на Kotlin-стороне.
* Не делай полноценный media catalog / playlist manager.
* Не делай dithering в v1 (truncation достаточно).
* Не делай ресемплинг в engine — делегируй системному mixer.

---

## Контекст ядра

* Host: Kotlin. Engine: C++. Bridge: JNI.
* Внутренний поток: `File → Decoder → PCM ring buffer → FormatConverter → AudioTrack → System mixer → external EQ / DVC`.
* Поддерживаемые форматы v1: **MP3, OGG Opus, FLAC, WAV**.
* MP3 decoder: `minimp3` (header-only).
* Opus decoder: `libopusfile` + `libopus` + `libogg`.
* FLAC decoder: `libFLAC`.
* WAV: кастомный парсер без внешней библиотеки.
* Внутренний PCM формат: **float32**, interleaved, native sample rate источника, без ресемплинга.
* Вывод: обычный `AudioTrack` в `MODE_STREAM`, чтобы системный mixer path и внешний EQ могли перехватывать поток.
* `audioSessionId` — один на жизненный цикл `SonarPlayer`, экспортируется наружу.
* Position reporting — polling из Kotlin через `nativeGetPosition()` (возвращает ms).
* Audio focus — на стороне приложения/harness, не ядра.
* Process death recovery — на стороне приложения/harness, не ядра.
* Re-probe при смене audio output для v1 не обязателен; probe при каждом `open()` достаточно.

---

## Output format negotiation и fallback

Следуй Appendix B в `ARCHITECTURE.md` для полной fallback chain.

Краткая логика:

**Настройка "Выводить аудио до 32 бит" включена**, source ≥ 24-bit:

* API ≥ 31: `ENCODING_PCM_32BIT` → `ENCODING_PCM_24BIT_PACKED` → `ENCODING_PCM_FLOAT` → `ENCODING_PCM_16BIT`
* API 24–30: `ENCODING_PCM_FLOAT` (≈24-bit precision) → `ENCODING_PCM_16BIT`

**Настройка включена**, source = 16-bit (или MP3/Opus):

* `ENCODING_PCM_16BIT` — hi-res вывод не даёт преимущества для lossy/16-bit.

**Настройка выключена**:

* `ENCODING_PCM_16BIT` всегда.

> `ENCODING_PCM_FLOAT` — важный промежуточный fallback на API 24–30, не пропускай его.

---

## Что нужно реализовать

### Kotlin (`:sonar-core`)

1. `SonarPlayer.kt` — публичный API.
2. `NativeBridge.kt` — JNI external declarations.
3. `OutputConfigNegotiator.kt` — probe bit depth через `AudioTrack`, fallback chain.
4. `AudioTrackWrapper.kt` — AudioTrack lifecycle, write loop в dedicated thread.
5. `SessionManager.kt` — `audioSessionId` через `AudioManager.generateAudioSessionId()`.
6. `StreamInfo.kt` — data class (sampleRate, channels, durationMs, sourceBitDepth, codec).
7. `PlayerState.kt` — enum: `IDLE`, `OPENED`, `BUFFERING`, `PLAYING`, `PAUSED`, `COMPLETED`, `ERROR`.
8. `SonarError.kt` — error code constants и mapping (9 кодов — см. §11 ARCHITECTURE.md).
9. `PlayerConfig.kt` — конфигурация: buffer sizes, pre-buffer threshold, max consecutive errors.

### C++ (`:sonar-core`, `src/main/cpp/`)

10. `PlaybackEngine` — state machine (7 состояний, см. §6.3), координация decoder ↔ buffer ↔ output.
11. `IDecoder` — abstract interface: `open()`, `decodeNextFrame()`, `seek()`, `close()`, `getStreamInfo()`.
12. `DecoderFactory` — выбор декодера по extension + header magic bytes.
13. `Mp3Decoder` — через minimp3.
14. `OpusDecoder` — через libopusfile.
15. `FlacDecoder` — через libFLAC.
16. `WavDecoder` — кастомный парсер RIFF/WAVE.
17. `RingBuffer` — lock-free SPSC, pre-allocated, ~200 мс PCM.
18. `FormatConverter` — float32 → int16/int24/int32 (truncation, без dithering).
19. `native_bridge.cpp` — JNI function implementations.
20. `engine_types.h` — enums, structs (State, ErrorCode, StreamInfo).
21. `engine_config.h` — compile-time и runtime config constants.

### JNI API (полный контракт — §3.1 ARCHITECTURE.md)

```
// Lifecycle
nativeCreateEngine(outputSampleRate: Int, outputBitDepth: Int, outputChannels: Int): Long
nativeDestroyEngine(handle: Long)

// Playback control
nativeOpen(handle: Long, filePath: String): Int
nativePlay(handle: Long): Int
nativePause(handle: Long): Int
nativeResume(handle: Long): Int
nativeStop(handle: Long): Int
nativeSeek(handle: Long, positionMs: Long): Int

// Data pull
nativeReadPcm(handle: Long, buffer: ByteBuffer, maxFrames: Int): Int

// Info
nativeGetStreamInfo(handle: Long): StreamInfo
nativeGetState(handle: Long): Int
nativeGetPosition(handle: Long): Long    // текущая позиция (ms)
nativeGetError(handle: Long): String?
nativeSetOutputFormat(handle: Long, bitDepth: Int): Int
```

### Тесты

22. C++ unit tests (Google Test): RingBuffer, FormatConverter, каждый decoder, PlaybackEngine state machine, DecoderFactory.
23. Android instrumented tests: полный цикл воспроизведения, seek, rapid operations, error paths.

### Test Harness

24. Минимальный временный test harness (`:test-harness` module) — см. секцию ниже.

---

## Архитектурные решения, которые нужно соблюдать

* `PlaybackEngine` — state machine с **7 состояниями** (IDLE, OPENED, BUFFERING, PLAYING, PAUSED, COMPLETED, ERROR). Переходы — см. §6.3 ARCHITECTURE.md.
* Pre-buffer: decode thread заполняет ring buffer до **50%** перед переходом BUFFERING → PLAYING.
* `RingBuffer` — SPSC, lock-free на атомарных read/write указателях.
* `FormatConverter` — float32 → int16/int24/int32, truncation (без dithering).
* `AudioTrack` пересоздаётся при смене sample rate, channel count или negotiated encoding.
* `audioSessionId` сохраняется на весь жизненный цикл `SonarPlayer` (не меняется между треками).
* External EQ работает через стандартный Android audio session path.
* `nativeReadPcm()` вызывается **только** из write thread на Kotlin-стороне.
* Все JNI-вызовы управления — быстрые и не блокируют UI thread (< 5 мс).
* Ошибки декодирования отдельных frames: пропускать, инкрементировать счётчик. > 50 подряд → ERROR.

---

## Требования к качеству

* Следуй `ARCHITECTURE.md` буквально.
* Если что-то неясно, не додумывай молча — фиксируй как TODO/open question.
* Предпочитай минимальные безопасные решения для v1.
* Избегай монолитных файлов.
* Используй маленькие интерфейсы и чёткие границы.
* Если придётся упростить, упрощай без ломки архитектуры.
* Код должен быть готов к последующему добавлению AAC/M4A как отдельной итерации.

### Целевые метрики (из §13 ARCHITECTURE.md)

| Метрика | Target |
|---------|--------|
| Open-to-sound | < 200 мс |
| Seek-to-sound | < 150 мс |
| Pause → Resume | < 50 мс |
| RAM (native heap) | < 5 MB |
| CPU | < 5% одного ядра |
| Threads | ≤ 3 (decode, write, JNI) |
| Crash/ANR | Ни одна ошибка не должна приводить к native crash. Все ошибки — через error codes. |

---

## Тестовые аудиофайлы

Директория `test_audio_files/` содержит **7 reference-файлов**:

| Файл | Формат | Sample Rate | Bit Depth | Назначение |
|------|--------|-------------|-----------|------------|
| `test_44.1khz.mp3` | MP3 | 44.1 kHz | — | MP3 decoder, базовый playback |
| `test_2_44.1khz.ogg` | OGG Opus | 44.1 kHz | — | Opus decoder |
| `test_48khz_16bit.wav` | WAV | 48 kHz | 16-bit | WAV decoder |
| `test_16bit_44.1khz.flac` | FLAC | 44.1 kHz | 16-bit | FLAC decoder, 16-bit |
| `test_2_44.1khz_24bit.flac` | FLAC | 44.1 kHz | 24-bit | FLAC hi-res |
| `test_48khz_24bit.flac` | FLAC | 48 kHz | 24-bit | FLAC hi-res, другой sample rate |
| `test_96khz_24bit.flac` | FLAC | 96 kHz | 24-bit | FLAC hi-res, 96 kHz |

Используй их для реального playback / integration тестирования.

Для edge cases, которых нет в `test_audio_files/` (corrupt файлы, пустые файлы, моно, 32-bit WAV и т.д.), **создавай маленькие детерминированные синтетические тестовые файлы** программно, а не скачивай внешние файлы.

---

## Минимальный временный test harness (`:test-harness`)

Одна Activity с минимальным UI:

* Кнопка выбора локального файла (через `Intent` / file picker).
* Кнопки: Play / Pause / Resume / Stop.
* SeekBar.
* Отображение:
  * track name (имя файла);
  * format (codec);
  * source sample rate;
  * source bit depth;
  * negotiated output bit depth;
  * current position / duration;
  * playback state;
  * audio session ID;
  * error state (если есть).
* Toggle: "Выводить аудио до 32 бит" (on/off) — передаётся в `OutputConfigNegotiator`.

Это **только стенд** для проверки ядра. Не делай красивый дизайн, не делай navigation, не делай landscape layout.

---

## План реализации

1. **Bootstrap**: создай Gradle-проект с двумя модулями, настрой NDK + CMake.
2. **Third-party**: подключи minimp3 (copy), libopus/libogg/libopusfile/libFLAC (git submodules), настрой CMakeLists.txt.
3. **Типы и контракт**: `engine_types.h`, `engine_config.h`, Kotlin data classes (`StreamInfo`, `PlayerState`, `SonarError`, `PlayerConfig`), `NativeBridge.kt`.
4. **RingBuffer**: реализуй и протестируй (C++ unit test).
5. **FormatConverter**: реализуй и протестируй.
6. **WavDecoder**: реализуй и протестируй (самый простой decoder для отладки pipeline).
7. **PlaybackEngine**: state machine + coordination с WavDecoder.
8. **JNI bridge** (`native_bridge.cpp`): свяжи Engine с Kotlin.
9. **AudioTrackWrapper + OutputConfigNegotiator + SessionManager**: Kotlin-сторона вывода.
10. **SonarPlayer**: свяжи всё вместе.
11. **Test harness**: минимальная Activity, проверь playback WAV end-to-end.
12. **Mp3Decoder**: реализуй, протестируй через harness.
13. **OpusDecoder**: реализуй, протестируй.
14. **FlacDecoder**: реализуй, протестируй (включая 24-bit hi-res negotiation).
15. **DecoderFactory**: автоматический выбор decoder по extension + magic bytes.
16. **Unit tests**: C++ (Google Test) + Kotlin.
17. **Instrumented tests**: полный цикл, seek, stress test, error paths.

После каждого крупного этапа проверяй сборку и тесты. Если что-то ломается — фиксируй и продолжай.

---

## Формат работы

1. Сначала кратко перечисли, как ты понял ограничения.
2. Затем реализуй по этапам.
3. После каждого крупного этапа проверяй сборку и тесты.
4. Если что-то ломается — фиксируй и продолжай.
5. В конце дай короткий отчёт:
   * что реализовано;
   * что ещё не сделано;
   * какие ограничения остались;
   * какие TODO нужно будет закрыть позже.

Если видишь, что какой-то кусок архитектуры требует уточнения, лучше пометь его как open question, чем расширять проект за рамки задачи.