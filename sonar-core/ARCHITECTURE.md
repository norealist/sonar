# Sonar Audio Core — Architecture Specification

> **Version:** 0.2-draft  
> **Date:** 2026-08-10  
> **Status:** Design — awaiting review  
> **Audience:** Android/C++ developers, code reviewers, QA  

---

## Summary

Sonar Audio Core — нативный модуль воспроизведения локальных аудиофайлов для Android.  
Архитектура разделена на три слоя:

| Слой | Язык | Ответственность |
|------|------|-----------------|
| **Host** | Kotlin | Lifecycle, настройки, AudioTrack, audio session, JNI-мост |
| **Engine** | C++ | Декодирование, буферизация, формат-конверсия, state-machine |
| **Bridge** | JNI (C) | Тонкий маршалинг между Host и Engine |

Данные движутся в одну сторону:

```
File → Decoder → PCM ring buffer → Format negotiator → AudioTrack → System mixer → (external EQ / DVC)
```

Ядро **не** содержит UI, playlist-логику, media catalog, DSP-эффекты или сетевой код.

---

## 1. Общая архитектура

### 1.1 Принцип

Ядро реализует **pull-модель**: AudioTrack callback запрашивает порцию PCM из Engine.  
Engine декодирует данные заранее в фоновом потоке (pre-buffer), а callback читает готовые семплы из ring buffer.

### 1.2 Диаграмма

```
┌──────────────────────────────────────────────────────────┐
│  Kotlin (Host Layer)                                     │
│                                                          │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐   │
│  │ SonarPlayer  │  │ OutputConfig  │  │ AudioTrack   │   │
│  │  (public API) │  │ Negotiator   │  │  Wrapper     │   │
│  └──────┬───────┘  └───────┬───────┘  └──────┬───────┘   │
│         │                  │                 │           │
│─────────┼──────────────────┼─────────────────┼───────────│
│         │        JNI Bridge (C)              │           │
│─────────┼──────────────────┼─────────────────┼───────────│
│         ▼                  ▼                 ▼           │
│  C++ (Engine Layer)                                      │
│                                                          │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐   │
│  │ PlaybackEngine│ │ DecoderFactory│  │ RingBuffer   │   │
│  │ (state machine)│ │  + Decoders  │  │              │   │
│  └──────────────┘  └───────────────┘  └──────────────┘   │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Слои и их ответственность

### 2.1 Host Layer (Kotlin)

| Компонент | Ответственность |
|-----------|-----------------|
| `SonarPlayer` | Публичный API для вызывающего кода. Методы: `play(uri)`, `pause()`, `resume()`, `stop()`, `seekTo(ms)`, `next(uri)`, `previous(uri)`. Передаёт команды в Engine через JNI. |
| `OutputConfigNegotiator` | Определяет поддерживаемый формат вывода (bit depth, sample rate) через `AudioTrack.isDirectPlaybackSupported()` / probe-based fallback. Хранит текущую пользовательскую настройку "Выводить аудио до 32 бит". |
| `AudioTrackWrapper` | Создаёт и управляет `AudioTrack` в `MODE_STREAM`. Отвечает за `audioSessionId`, write loop, error handling. |
| `SessionManager` | Получает/хранит `audioSessionId` для привязки внешних эффектов. Передаёт id наружу (для UI / системных эффектов). |

> **Принцип**: Host Layer не знает ничего о кодеках и PCM-форматах кроме итогового `AudioFormat`, необходимого для `AudioTrack`.

### 2.2 Bridge Layer (JNI / C)

Минимальный слой:

- Экспортирует функции `Java_com_sonar_core_NativeBridge_*`.
- Маршалит примитивы (`int`, `long`, `float`) и byte-массивы.
- Не содержит бизнес-логику.
- Все JNI-функции потоко-безопасны через внутреннюю синхронизацию Engine.

Передаваемые данные:

| Направление | Данные |
|-------------|--------|
| Kotlin → C++ | команда (enum int), file descriptor / path, seek position, output format params |
| C++ → Kotlin | состояние (enum int), ошибка (int + string), decoded PCM (ByteBuffer), stream info (sample rate, channels, duration, bit depth) |

### 2.3 Engine Layer (C++)

| Компонент | Ответственность |
|-----------|-----------------|
| `PlaybackEngine` | State machine, координация decoder ↔ buffer ↔ output. |
| `DecoderFactory` | Создаёт конкретный decoder по расширению/MIME/header magic. |
| `IDecoder` | Интерфейс декодера. Методы: `open()`, `decodeNextFrame()`, `seek()`, `close()`, `getStreamInfo()`. |
| `Mp3Decoder` | Декодирование MP3 через `minimp3` (header-only). |
| `OpusDecoder` | Декодирование OGG Opus через `libopusfile` (`libopus` + `libogg`). |
| `FlacDecoder` | Декодирование FLAC через `libFLAC`. |
| `WavDecoder` | Парсинг WAV header + прямое чтение PCM (без внешней библиотеки). |
| `RingBuffer` | Lock-free single-producer single-consumer ring buffer для PCM-данных. |
| `FormatConverter` | Конвертация PCM: int↔float, 32→24→16 bit, sample rate не конвертируется (см. §4). |

---

## 3. Граница Kotlin ↔ C++

### 3.1 JNI API (контракт)

```
// Lifecycle
nativeCreateEngine(outputSampleRate: Int, outputBitDepth: Int, outputChannels: Int): Long  // → engine handle
nativeDestroyEngine(handle: Long)

// Playback control
nativeOpen(handle: Long, filePath: String): Int         // → 0 = OK, <0 = error code
nativePlay(handle: Long): Int
nativePause(handle: Long): Int
nativeResume(handle: Long): Int
nativeStop(handle: Long): Int
nativeSeek(handle: Long, positionMs: Long): Int

// Data pull
nativeReadPcm(handle: Long, buffer: ByteBuffer, maxFrames: Int): Int  // → frames written

// Info
nativeGetStreamInfo(handle: Long): StreamInfo           // → sample rate, channels, duration, source bit depth
nativeGetState(handle: Long): Int                       // → state enum
nativeGetPosition(handle: Long): Long                   // → текущая позиция воспроизведения (ms)
nativeGetError(handle: Long): String?                   // → last error message
nativeSetOutputFormat(handle: Long, bitDepth: Int): Int // → динамическая смена bit depth
```

> **Примечание**: `nativeReadPcm` используется из write-потока AudioTrack на Kotlin-стороне. Альтернативный вариант — push из C++ через JNI callback — рассмотрен и отвергнут ради простоты и предсказуемости (см. §12).

### 3.2 StreamInfo (структура, передаётся через JNI)

| Поле | Тип | Описание |
|------|-----|----------|
| `sampleRate` | `int` | Частота дискретизации источника (Hz) |
| `channels` | `int` | Количество каналов (1 или 2) |
| `durationMs` | `long` | Длительность трека (ms), -1 если неизвестна |
| `sourceBitDepth` | `int` | Bit depth источника (16, 24, 32) |
| `codec` | `String` | Название кодека ("mp3", "opus", "flac", "wav") |

### 3.3 Правила передачи данных

1. **PCM buffer**: используется `DirectByteBuffer`, аллоцированный на Kotlin-стороне, передаётся в C++ по указателю. Это zero-copy для JNI.
2. **File path**: передаётся как UTF-8 строка. Engine открывает файл самостоятельно через `fopen` / POSIX `open`.
3. **Ошибки**: возвращаются как `int` error code. Текстовое описание доступно через `nativeGetError()`.

---

## 4. Формат внутреннего PCM-потока

### 4.1 Канонический внутренний формат

Engine внутри работает с PCM в формате:

| Параметр | Значение |
|----------|----------|
| Encoding | **32-bit float** (IEEE 754) |
| Byte order | Native (little-endian на ARM/x86) |
| Channel layout | Interleaved |
| Sample rate | **Native sample rate источника** (без ресемплинга) |

> **Обоснование**: float32 — единый внутренний формат, который покрывает все входные bit depth без потерь точности для ≤24-bit источников, и естественен для декодеров (libopus, minimp3 выдают float).

### 4.2 Конвертация на выходе

Перед записью в `AudioTrack` буфер `FormatConverter` преобразует float32 → целевой integer формат:

| Выходной формат | AudioFormat encoding | Конвертация |
|-----------------|---------------------|-------------|
| 32-bit int | `ENCODING_PCM_32BIT` | `float × 2147483647 → int32`, clamp |
| 24-bit packed | `ENCODING_PCM_24BIT_PACKED` | `float × 8388607 → int24`, pack 3 bytes LE |
| 16-bit int | `ENCODING_PCM_16BIT` | `float × 32767 → int16`, clamp |
| float | `ENCODING_PCM_FLOAT` | pass-through (если AudioTrack поддерживает) |

> **Примечание**: `ENCODING_PCM_24BIT_PACKED` доступен с API 31+. На более ранних API 24-bit **недоступен** через `AudioTrack`; в этом случае fallback на 32-bit float или 16-bit int.

### 4.3 Sample Rate

Engine **не выполняет ресемплинг**. `AudioTrack` создаётся с sample rate, совпадающим с источником. Если AudioTrack не поддерживает данный sample rate, Android system mixer выполнит ресемплинг автоматически. Это допустимо, поскольку ядро работает через стандартный mixer path.

При смене трека с другим sample rate `AudioTrack` **пересоздаётся**.

---

## 5. Стратегия декодирования

### 5.1 Общая схема

```
File → IDecoder::decodeNextFrame() → float32 PCM → RingBuffer
```

Каждый декодер реализует `IDecoder` и преобразует сжатые данные в canonical float32 PCM.

### 5.2 По форматам

| Формат | Библиотека | Входные данные | Выход декодера | Примечания |
|--------|-----------|----------------|----------------|------------|
| **MP3** | `minimp3` (header-only) | MPEG frames | float32 | Компактный, zero-dependency, достаточная совместимость. При обнаружении edge-case проблем — миграция на `libmpg123` как fallback dependency. |
| **OGG Opus** | `libopusfile` (libopus + libogg) | OGG container | float32 | opusfile выдаёт float напрямую. Всегда 48 kHz (спецификация Opus). |
| **FLAC** | `libFLAC` | FLAC native container | intN → float32 | libFLAC выдаёт int samples (16/24/32). Конвертация в float32 с сохранением precision. `sourceBitDepth` берётся из stream info. |
| **WAV** | Кастомный код (без библиотеки) | RIFF/WAVE | intN → float32 | Парсинг заголовка: fmt chunk → sample rate, channels, bits per sample, data chunk offset. Поддержка: PCM 8/16/24/32 int, 32-bit float IEEE. |

> **AAC / M4A**: не поддерживается в v1. Может быть добавлено в будущих версиях.

### 5.3 Gapless playback

На текущем этапе gapless playback **не реализуется** (см. Non-goals). Между треками допустим короткий silence при пересоздании `AudioTrack`.

---

## 6. Буферизация и управление воспроизведением

### 6.1 Ring Buffer

- **Тип**: Single-producer (decode thread) / single-consumer (AudioTrack write thread).
- **Размер**: Конфигурируемый, default = **200 мс** PCM при текущем sample rate и channel count.
- **Реализация**: Lock-free на атомарных read/write указателях.
- **Аллокация**: Один раз при `nativeCreateEngine()`, переаллокация при смене sample rate / channels.

### 6.2 Pre-buffer

При `nativeOpen()` + `nativePlay()`:

1. Engine переходит в состояние `BUFFERING`.
2. Decode thread заполняет ring buffer до **50% ёмкости** (configurable threshold).
3. Engine переходит в `PLAYING`, AudioTrack write loop начинает pull.
4. Decode thread продолжает декодирование ahead.

При underrun (ring buffer пуст, decoder не успевает):

1. Engine переходит в `BUFFERING`.
2. AudioTrack ставится на pause.
3. После re-fill до threshold — resume.

> Для локальных файлов underrun крайне маловероятен; механизм нужен для robustness.

### 6.3 State Machine

```
         open()          play()
IDLE ──────────► OPENED ─────────► BUFFERING ──► PLAYING
                   ▲                                │
                   │          pause()                │
                   │       ◄────────── PAUSED ◄─────┘
                   │                    │  ▲
                   │          resume()  │  │
                   │                    ▼  │
                   │               PLAYING─┘
                   │
                   │  stop()  (from any active state)
                   ◄──────────────────────────────────
                   │
              IDLE (resources released)
                   │
                   │  error (from any state)
                   ▼
                 ERROR
```

Состояния (enum):

| Состояние | Описание |
|-----------|----------|
| `IDLE` | Engine создан, файл не открыт. |
| `OPENED` | Файл открыт, stream info доступно, decode не начат. |
| `BUFFERING` | Decode thread работает, pre-buffer не завершён. |
| `PLAYING` | AudioTrack пишет данные, звук воспроизводится. |
| `PAUSED` | AudioTrack на паузе, decode thread приостановлен. |
| `COMPLETED` | Файл декодирован полностью, AudioTrack доигрывает оставшийся буфер. |
| `ERROR` | Произошла ошибка. Детали через `nativeGetError()`. |

### 6.4 Seek

1. Вызов `nativeSeek(positionMs)`.
2. Engine ставит decode thread на паузу.
3. Ring buffer сбрасывается (reset read/write pointers).
4. `IDecoder::seek(positionMs)` вызывается на текущем декодере.
5. Decode thread возобновляется с новой позиции.
6. Если Engine был в `PLAYING` — переходит в `BUFFERING` → `PLAYING`.
7. Если был в `PAUSED` — остаётся в `PAUSED` с обновлённой позицией.

> **Точность seek**: зависит от кодека. MP3 — к ближайшему frame (minimp3 не имеет встроенного seek index; для VBR может потребоваться scan); Opus — к granule position; FLAC/WAV — sample-accurate. Возвращённая позиция после seek может отличаться от запрошенной.

### 6.5 Pause / Resume

- **Pause**: `AudioTrack.pause()`, decode thread приостанавливается (conditional variable wait).
- **Resume**: decode thread пробуждается, `AudioTrack.play()`.

### 6.6 Stop

- `AudioTrack.stop()` + `AudioTrack.release()`.
- Decoder закрывается (`IDecoder::close()`).
- Ring buffer сбрасывается.
- Engine → `IDLE`.

### 6.7 Next / Previous

На уровне ядра `next(uri)` и `previous(uri)` — это:

1. `stop()` текущего трека.
2. `open(newUri)` + `play()`.

Ядро не знает о playlist; URI нового трека передаётся вызывающим кодом.

> **Оптимизация (будущее)**: pre-decode следующего трека для gapless. На текущем этапе — not in scope.

---

## 7. Negotiation формата вывода

### 7.1 Алгоритм

Negotiation выполняется на **Kotlin-стороне** (`OutputConfigNegotiator`), поскольку требует вызовов Android API.

```
Вход:
  - userSetting: Boolean  ("Выводить аудио до 32 бит")
  - sourceBitDepth: Int    (из StreamInfo)
  - deviceCapabilities     (probed)

Выход:
  - outputEncoding: AudioFormat.ENCODING_PCM_*
  - outputBitDepth: Int    (16, 24, 32)
```

### 7.2 Матрица решений

#### Режим включён (`userSetting = true`)

| Источник | Попытка 1 | Попытка 2 | Попытка 3 (fallback) |
|----------|-----------|-----------|----------------------|
| 32-bit FLAC/WAV | `ENCODING_PCM_32BIT` | `ENCODING_PCM_24BIT_PACKED` | `ENCODING_PCM_16BIT` |
| 24-bit FLAC/WAV | `ENCODING_PCM_24BIT_PACKED` | `ENCODING_PCM_32BIT` | `ENCODING_PCM_16BIT` |
| 16-bit (любой) | `ENCODING_PCM_16BIT` | — | — |
| MP3, Opus | `ENCODING_PCM_16BIT` | — | — |

> **Примечание**: MP3 и Opus декодируются в float32 внутри, но их source precision ≤ 16-bit (MP3: ~15.5 bit, Opus: ~16 bit). Вывод в 24/32 bit для этих форматов **не даёт качественного преимущества** и поэтому не выполняется. При необходимости это поведение может быть пересмотрено.

#### Режим выключен (`userSetting = false`)

| Источник | Выход |
|----------|-------|
| Любой | `ENCODING_PCM_16BIT` |

### 7.3 Capability Detection

Проверка поддержки bit depth выполняется через пробное создание `AudioTrack`:

```kotlin
// Псевдокод — только для иллюстрации контракта
fun probeEncoding(encoding: Int, sampleRate: Int, channels: Int): Boolean {
    return try {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        minBuf > 0 && minBuf != AudioTrack.ERROR_BAD_VALUE
    } catch (_: Exception) {
        false
    }
}
```

**Кэширование**: результат probe кэшируется на время жизни `SonarPlayer`. Инвалидация — при смене audio output (headphones ↔ speaker); для v1 допустимо не инвалидировать (re-probe при каждом `open()`).

### 7.4 Предупреждение о системной конвертации

> [!WARNING]  
> Даже если `AudioTrack` принимает 24/32-bit PCM, системный mixer (AudioFlinger) **может** выполнять внутреннее преобразование в формат HAL-устройства. На многих устройствах внутренний pipeline — float32 или int16. Это означает, что 24/32-bit вывод **не гарантирует** bit-perfect доставку до ЦАП. Пользовательская настройка должна содержать соответствующий disclaimer (реализация disclaimer — на стороне UI, не ядра).

---

## 8. Логика Fallback

### 8.1 Bit Depth Fallback

Реализуется в `OutputConfigNegotiator` как chain:

```
requested → probe → если fail → следующий в chain → ... → ENCODING_PCM_16BIT (always supported)
```

`ENCODING_PCM_16BIT` считается **всегда поддерживаемым**. Если даже он недоступен — это fatal error устройства.

### 8.2 Decoder Fallback

Если декодер не может открыть файл:

1. `IDecoder::open()` возвращает error code.
2. Engine переходит в `ERROR`.
3. Error code и сообщение доступны через JNI.
4. Ядро **не пытается** использовать альтернативный декодер для того же файла.

### 8.3 Sample Rate

Fallback не нужен: `AudioTrack` принимает произвольный sample rate, ресемплинг выполняет система.

### 8.4 Mono / Stereo

Engine передаёт количество каналов из `StreamInfo`. `AudioTrack` создаётся с соответствующим `channelMask`. Если источник mono — `CHANNEL_OUT_MONO`; stereo — `CHANNEL_OUT_STEREO`. Multichannel (>2) на текущем этапе **не поддерживается**; при обнаружении — `ERROR`.

---

## 9. AudioTrack и Audio Session ID

### 9.1 Создание AudioTrack

```
AudioTrack.Builder()
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(USAGE_MEDIA)
        .setContentType(CONTENT_TYPE_MUSIC)
        .build())
    .setAudioFormat(AudioFormat.Builder()
        .setEncoding(negotiatedEncoding)
        .setSampleRate(streamInfo.sampleRate)
        .setChannelMask(channelMask)
        .build())
    .setBufferSizeInBytes(bufferSize)
    .setTransferMode(MODE_STREAM)
    .setSessionId(audioSessionId)
    .build()
```

### 9.2 Audio Session ID

- Генерируется через `AudioManager.generateAudioSessionId()` при инициализации `SonarPlayer`.
- Один `sessionId` на весь жизненный цикл плеера (не меняется между треками).
- `AudioTrack` всегда создаётся с этим `sessionId`.
- `sessionId` экспортируется наружу через публичный API `SonarPlayer.audioSessionId` для привязки UI-эффектов.

### 9.3 Buffer Size

`bufferSize` для `AudioTrack`:

```
bufferSize = max(
    AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding),
    desiredLatencyFrames × frameSize
)
```

`desiredLatencyFrames` — конфигурируемое значение, default = **40 мс** в frames.

### 9.4 Write Loop

На Kotlin-стороне dedicated thread выполняет:

```
while (isActive) {
    val frames = nativeReadPcm(engineHandle, directBuffer, maxFrames)
    if (frames > 0) {
        audioTrack.write(directBuffer, frames * frameSize, WRITE_BLOCKING)
    } else if (frames == 0) {
        // underrun or buffering — short sleep
    } else {
        // end of stream or error
    }
}
```

### 9.5 Пересоздание AudioTrack

AudioTrack пересоздаётся при:

1. Смене трека с другим sample rate, channel count, или negotiated encoding.
2. Явном изменении пользовательской настройки bit depth (runtime).

При пересоздании:

1. `audioTrack.stop()` → `audioTrack.release()`.
2. Новый `AudioTrack` с тем же `sessionId`.
3. Write loop продолжает работу.

---

## 10. Работа со сторонними эффектами / EQ

### 10.1 Принцип

Ядро **не применяет** никаких аудиоэффектов. Вся обработка (EQ, DVC, bass boost и т.д.) выполняется **внешними** модулями, подключенными к `audioSessionId` через стандартный Android audio effects framework.

### 10.2 Требования к ядру

1. Использовать `AudioAttributes` с `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` — это позволяет системным эффектам корректно определять тип потока.
2. Использовать `MODE_STREAM` — обязательно для работы системного mixer path.
3. Не использовать `FLAG_LOW_LATENCY` или `PERFORMANCE_MODE_LOW_LATENCY` — они могут bypass эффекты.
4. Экспортировать `audioSessionId` наружу.
5. Не пересоздавать `audioSessionId` без необходимости — иначе внешние эффекты потеряют привязку.

### 10.3 DVC (Dynamic Volume Control)

DVC работает на уровне AudioFlinger и привязывается к session. Ядру не нужно делать ничего специального — достаточно использовать стандартный `AudioTrack` path.

---

## 11. Обработка ошибок

### 11.1 Error Codes

| Код | Константа | Описание |
|-----|-----------|----------|
| 0 | `OK` | Успех |
| -1 | `ERR_FILE_NOT_FOUND` | Файл не найден или недоступен |
| -2 | `ERR_FILE_READ` | Ошибка чтения файла |
| -3 | `ERR_UNSUPPORTED_FORMAT` | Формат не поддерживается |
| -4 | `ERR_DECODER_INIT` | Декодер не смог инициализироваться |
| -5 | `ERR_DECODER_DECODE` | Ошибка при декодировании frame |
| -6 | `ERR_INVALID_STATE` | Операция невозможна в текущем состоянии |
| -7 | `ERR_SEEK_FAILED` | Seek не удался |
| -8 | `ERR_OUTPUT_FORMAT` | Не удалось создать AudioTrack с запрошенным форматом |
| -9 | `ERR_INTERNAL` | Внутренняя ошибка (bug) |

### 11.2 Стратегия

1. **Ошибки декодирования отдельных frames**: декодер пропускает повреждённый frame и продолжает. Счётчик ошибок инкрементируется. Если подряд > N (default=50) ошибок — `ERR_DECODER_DECODE`, переход в `ERROR`.
2. **Ошибки открытия файла**: немедленный `ERROR`.
3. **Ошибки AudioTrack**: Kotlin-сторона ловит exception, пытается пересоздать AudioTrack с fallback encoding. Если все encoding fail — `ERR_OUTPUT_FORMAT`.
4. **JNI exceptions**: не бросаются. Все ошибки возвращаются как error codes.

### 11.3 Unsupported Formats

- Engine определяет формат по file extension и header magic bytes.
- Если формат не поддерживается — `ERR_UNSUPPORTED_FORMAT`.
- Если extension не совпадает с реальным содержимым — поведение определяется по header magic (extension как fallback hint).

---

## 12. Потоковая модель и многопоточность

### 12.1 Потоки

| Поток | Создаётся | Ответственность |
|-------|-----------|-----------------|
| **UI thread** (Kotlin) | Android | Вызовы `SonarPlayer` API. Все JNI-вызовы неблокирующие (<1 мс). |
| **Decode thread** (C++) | Engine | Чтение файла, декодирование, запись в ring buffer. |
| **Write thread** (Kotlin) | AudioTrackWrapper | Чтение из ring buffer через JNI `nativeReadPcm()`, запись в AudioTrack. |

### 12.2 Синхронизация

```
                  Decode Thread              Write Thread
                       │                         │
                       ▼                         ▼
                  ┌─────────┐              ┌─────────┐
                  │ produce │───ring buf──►│ consume │
                  └─────────┘   (lock-free) └─────────┘
                       │                         │
                  mutex for                 reads state
                  state changes             atomically
```

- **Ring buffer**: lock-free SPSC. Не требует mutex.
- **State machine**: защищён `std::mutex`. State transitions — под lock. State reads — atomic load (relaxed ordering допустим для polling).
- **Decode thread wakeup**: `std::condition_variable` для pause/resume/seek.
- **JNI вызовы управления** (play, pause, stop, seek): захватывают state mutex, изменяют state, сигнализируют condition variable. Гарантированно завершаются быстро.

### 12.3 Thread Safety контракт

- `nativeReadPcm()` вызывается **только** из write thread.
- `nativeOpen/Play/Pause/Resume/Stop/Seek` могут вызываться из **любого** потока, но не параллельно друг с другом (сериализация через mutex внутри Engine).
- `nativeGetState()` / `nativeGetStreamInfo()` — thread-safe, non-blocking.

---

## 13. Требования к стабильности, latency и resource usage

### 13.1 Стабильность

| Требование | Критерий |
|------------|----------|
| Crash-free | Ни одна ошибка не должна приводить к native crash. Все ошибки — через error codes. |
| Memory safety | Нет `new`/`malloc` в hot path (decode loop). Pre-allocated buffers. |
| Leak-free | `nativeDestroyEngine()` освобождает все ресурсы. Верифицируется через ASan/LSan. |
| ANR-free | Никакие JNI-вызовы не блокируют UI thread > 5 мс. |

### 13.2 Latency

| Метрика | Target |
|---------|--------|
| Open-to-sound | < 200 мс (для локальных файлов) |
| Seek-to-sound | < 150 мс |
| Pause → Resume | < 50 мс |
| State transition response | < 5 мс |

> Latency зависит от `AudioTrack` buffer size и системного mixer. Указанные значения — цели для оптимизации, не жёсткие SLA.

### 13.3 Resource Usage

| Ресурс | Ограничение |
|--------|-------------|
| RAM (native heap) | < 5 MB при воспроизведении (buffers + decoder state) |
| CPU | < 5% одного ядра при воспроизведении (без UI) |
| Threads | Не более 3 (decode, write, main JNI) |
| File descriptors | 1 (текущий файл) |
| Battery | Не выше, чем стандартный `MediaPlayer` для аналогичного файла |

---

## 14. Тестовая стратегия

### 14.1 Unit Tests (C++, Google Test)

| Компонент | Что тестируется |
|-----------|-----------------|
| `RingBuffer` | Корректность SPSC: write/read, wrap-around, full/empty, concurrent producer-consumer. |
| `FormatConverter` | Точность конвертации float32 → int16/24/32. Edge cases: 0.0, ±1.0, clipping. |
| `WavDecoder` | Парсинг WAV headers: PCM 16/24/32, float, mono/stereo, нестандартные chunk orders. |
| `IDecoder` (каждый) | Open/decode/seek/close для reference файлов каждого формата. Проверка sample count, duration. |
| `PlaybackEngine` | State machine transitions: все валидные и невалидные переходы. |
| `DecoderFactory` | Выбор декодера по extension и header magic. |

### 14.2 Integration Tests (Android Instrumented)

| Сценарий | Что проверяется |
|----------|-----------------|
| Play MP3 start to end | Полный цикл: open → play → completed. Нет crash, нет утечек. |
| Seek в каждом формате | Seek forward/backward, seek to 0, seek to end. |
| Rapid play/pause/seek | Stress test: 100 случайных операций за 10 сек. Нет deadlock, нет crash. |
| Format negotiation | Проверка что AudioTrack создаётся с корректным encoding при разных настройках. |
| Switch tracks | Быстрое переключение треков с разными sample rate / channels. |
| Error paths | Повреждённые файлы, несуществующие файлы, пустые файлы. |

### 14.3 Reference Audio Files

Набор тестовых файлов (не входят в production APK):

- `test_sine_440hz_16bit.wav` — sine wave, reference signal.
- `test_silence.flac` — тишина, 24-bit.
- `test_short.mp3` — 1 секунда, для seek tests.
- `test_long.ogg` — 5 минут, Opus, для stability.
- `test_corrupt.mp3` — повреждённый файл.
- `test_not_audio.wav` — WAV header, но данные — мусор.
- По одному файлу каждого формата с разными sample rates (44100, 48000, 96000) и bit depths (16, 24, 32 где применимо).

### 14.4 Sanitizers

- **AddressSanitizer (ASan)**: запускать на CI для каждого PR. Все native тесты — под ASan.
- **ThreadSanitizer (TSan)**: периодически для проверки race conditions.
- **LeakSanitizer (LSan)**: встроен в ASan, проверка утечек.

---

## 15. Что ядро НЕ должно делать (Non-goals)

| Non-goal | Обоснование |
|----------|-------------|
| UI / верстка | Отдельный проект. |
| Playlist management | Ядро воспроизводит один трек. Playlist — уровень выше. |
| Media catalog / library scan | Не ответственность ядра. |
| Network streaming | Только локальные файлы. |
| DRM / protected content | Не поддерживается. |
| DSP эффекты (EQ, reverb, etc.) | Внешние эффекты через audio session. |
| Визуализатор / FFT | Не в scope. |
| Bluetooth-специфичные оптимизации | Стандартный AudioTrack path. |
| USB direct / bit-perfect output | Явно исключено. |
| Lyrics | Не ответственность ядра. |
| Cloud sync | Не в scope. |
| Recommendations / playlist intelligence | Не в scope. |
| AAC / M4A декодирование | Отложено; может быть добавлено в будущих версиях. |
| Gapless playback | Может быть добавлено позже как extension. |
| Crossfade | Может быть добавлено позже. |
| ReplayGain | Может быть добавлено позже. |
| Android `MediaSession` integration | Ответственность Host-приложения, не ядра. |
| Notification controls | Ответственность Host-приложения. |
| Audio focus management | Ответственность Host-приложения. SonarPlayer предоставляет pause/resume, приложение решает когда их вызывать. |
| Ресемплинг | Делегирован системному mixer. |

---

## Таблица компонентов

| Компонент | Назначение | Реализация | Зависимости | Риски |
|-----------|-----------|------------|-------------|-------|
| `SonarPlayer` | Публичный Kotlin API ядра | Kotlin class, singleton-safe | `NativeBridge`, `OutputConfigNegotiator`, `AudioTrackWrapper` | API design — заморозка после v1 |
| `NativeBridge` | JNI-мост | Kotlin `external fun` + C JNI | Engine (C++) | JNI overhead на каждый `readPcm` call |
| `OutputConfigNegotiator` | Определение output format | Kotlin, probe через `AudioTrack` API | Android SDK | Device-specific поведение; probe может быть неточным |
| `AudioTrackWrapper` | Управление AudioTrack lifecycle | Kotlin | Android SDK | Пересоздание при смене формата; timing glitches |
| `SessionManager` | Audio session ID | Kotlin | `AudioManager` | Session ID persistence при process death |
| `PlaybackEngine` | State machine, координация | C++ | `IDecoder`, `RingBuffer`, `FormatConverter` | Deadlock при некорректной синхронизации |
| `DecoderFactory` | Создание декодера по формату | C++ | Все decoder-ы | Некорректное определение формата |
| `IDecoder` | Интерфейс декодера | C++ abstract class | — | — |
| `Mp3Decoder` | MP3 декодирование | C++ + minimp3 | minimp3 (header-only) | VBR seek accuracy; некоторые edge-case MP3. При проблемах — миграция на libmpg123. |
| `OpusDecoder` | OGG Opus декодирование | C++ + libopusfile | libopus, libogg, libopusfile | Всегда 48 kHz — несовпадение с другими tracks |
| `FlacDecoder` | FLAC декодирование | C++ + libFLAC | libFLAC | Hi-res файлы: большие буферы |
| `WavDecoder` | WAV декодирование | C++ (custom) | — | Нестандартные WAV (extensible format, RF64) |
| `RingBuffer` | PCM буфер | C++ lock-free SPSC | — | Correctness of lock-free impl |
| `FormatConverter` | float32 → intN конвертация | C++ | — | Precision, dithering (not implemented in v1) |

---

## Resolved Questions

Все архитектурные вопросы закрыты. Решения зафиксированы ниже.

| # | Вопрос | Решение |
|---|--------|--------|
| 1 | M4A/MP4 container support | Убрано из v1 вместе с AAC. |
| 2 | Лицензия FDK-AAC | Снято — AAC убран из v1. |
| 3 | minimp3 vs libmpg123 | **minimp3** для v1. mpg123 — fallback dependency только при реальных проблемах. |
| 4 | Dithering при конвертации 32→16 bit | **Truncation** для v1. Dithering (TPDF) — как enhancement в будущем. |
| 5 | Position reporting | **Polling** через `nativeGetPosition()`. UI вызывает по таймеру (100–250 мс). |
| 6 | Audio focus | Полностью на стороне приложения. Ядро экспортирует `pause()`/`resume()`/`setVolume()`. |
| 7 | Volume control | **`AudioTrack.setVolume()`** на Kotlin-стороне. Software volume в C++ не дублируется. |
| 8 | Multichannel WAV/FLAC (>2ch) | **Отклонять** с `ERR_UNSUPPORTED_FORMAT` в v1. |
| 9 | Re-probe при смене audio output | Не для v1. **Probe при каждом `open()`** достаточно. |
| 10 | Process death | Не ответственность ядра. Приложение сохраняет track + position, вызывает `open()` + `seekTo()`. |

---

## Suggested File Structure

```
sonar-core/
├── ARCHITECTURE.md                          ← этот документ
├── README.md
├── build.gradle.kts                         ← Android library module
├── settings.gradle.kts
│
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   │
│   │   ├── kotlin/com/sonar/core/
│   │   │   ├── SonarPlayer.kt              ← публичный API
│   │   │   ├── NativeBridge.kt             ← JNI external declarations
│   │   │   ├── OutputConfigNegotiator.kt   ← bit depth probing & negotiation
│   │   │   ├── AudioTrackWrapper.kt        ← AudioTrack lifecycle & write loop
│   │   │   ├── SessionManager.kt           ← audio session ID management
│   │   │   ├── StreamInfo.kt               ← data class for stream metadata
│   │   │   ├── PlayerState.kt              ← enum: IDLE, OPENED, BUFFERING, ...
│   │   │   ├── SonarError.kt               ← error code constants & mapping
│   │   │   └── PlayerConfig.kt             ← configuration (buffer sizes, thresholds)
│   │   │
│   │   └── cpp/
│   │       ├── CMakeLists.txt              ← CMake build для native code
│   │       │
│   │       ├── jni/
│   │       │   └── native_bridge.cpp       ← JNI function implementations
│   │       │
│   │       ├── engine/
│   │       │   ├── playback_engine.h
│   │       │   ├── playback_engine.cpp     ← state machine, coordination
│   │       │   ├── engine_types.h          ← enums, structs (State, ErrorCode, StreamInfo)
│   │       │   └── engine_config.h         ← compile-time & runtime config constants
│   │       │
│   │       ├── decoder/
│   │       │   ├── i_decoder.h             ← abstract decoder interface
│   │       │   ├── decoder_factory.h
│   │       │   ├── decoder_factory.cpp
│   │       │   ├── mp3_decoder.h
│   │       │   ├── mp3_decoder.cpp
│   │       │   ├── opus_decoder.h
│   │       │   ├── opus_decoder.cpp
│   │       │   ├── flac_decoder.h
│   │       │   ├── flac_decoder.cpp
│   │       │   ├── wav_decoder.h
│   │       │   └── wav_decoder.cpp
│   │       │
│   │       ├── buffer/
│   │       │   ├── ring_buffer.h
│   │       │   └── ring_buffer.cpp         ← lock-free SPSC ring buffer
│   │       │
│   │       ├── format/
│   │       │   ├── format_converter.h
│   │       │   └── format_converter.cpp    ← float32 → int16/24/32 conversion
│   │       │
│   │       └── third_party/
│   │           ├── minimp3/                ← header-only MP3 decoder
│   │           │   └── minimp3.h
│   │           ├── libopus/                ← built via CMake ExternalProject or submodule
│   │           ├── libogg/
│   │           ├── libopusfile/
│   │           └── libflac/
│   │
│   ├── test/                               ← Kotlin unit tests
│   │   └── kotlin/com/sonar/core/
│   │       ├── OutputConfigNegotiatorTest.kt
│   │       └── PlayerStateTest.kt
│   │
│   └── androidTest/                        ← Android instrumented tests
│       ├── kotlin/com/sonar/core/
│       │   ├── PlaybackIntegrationTest.kt
│       │   ├── SeekTest.kt
│       │   ├── FormatNegotiationTest.kt
│       │   └── ErrorHandlingTest.kt
│       └── assets/
│           └── test_audio/                 ← reference audio files
│               ├── sine_440hz_16bit.wav
│               ├── silence_24bit.flac
│               ├── short_1s.mp3
│               ├── long_5min.opus
│               ├── stereo_44100_24bit.flac
│               ├── stereo_48000_32bit.wav
│               ├── mono_44100_16bit.mp3
│               ├── corrupt.mp3
│               └── not_audio.wav
│
└── cpp_test/                               ← standalone C++ unit tests (Google Test)
    ├── CMakeLists.txt
    ├── ring_buffer_test.cpp
    ├── format_converter_test.cpp
    ├── wav_decoder_test.cpp
    ├── mp3_decoder_test.cpp
    ├── opus_decoder_test.cpp
    ├── flac_decoder_test.cpp
    ├── decoder_factory_test.cpp
    ├── playback_engine_test.cpp
    └── test_data/                          ← test audio files for C++ tests
        └── ...
```

---

## Appendix A: API Level Requirements

| Функция | Минимальный API | Примечание |
|---------|-----------------|------------|
| `AudioTrack` (MODE_STREAM) | 1 | Базовая функциональность |
| `ENCODING_PCM_FLOAT` | 21 | Для float output |
| `AudioTrack.Builder` | 23 | Рекомендуемый способ создания |
| `ENCODING_PCM_24BIT_PACKED` | 31 | 24-bit вывод; до API 31 — fallback |
| `ENCODING_PCM_32BIT` | 31 | 32-bit int вывод; до API 31 — fallback на float или 16-bit |
| `AudioManager.generateAudioSessionId()` | 21 | — |

> **Рекомендуемый minSdk**: **24** (Android 7.0). Покрытие ~99% устройств на 2026 год. `ENCODING_PCM_24BIT_PACKED` и `ENCODING_PCM_32BIT` доступны только на API 31+; на API 24-30 hi-res fallback: `ENCODING_PCM_FLOAT` (24-bit precision) → `ENCODING_PCM_16BIT`.

## Appendix B: Fallback Chain (Complete)

```
userSetting = ON, sourceBitDepth ≥ 24, API ≥ 31:
  try ENCODING_PCM_32BIT
  → try ENCODING_PCM_24BIT_PACKED
  → try ENCODING_PCM_FLOAT
  → ENCODING_PCM_16BIT

userSetting = ON, sourceBitDepth ≥ 24, API 24-30:
  try ENCODING_PCM_FLOAT          (≈24-bit precision)
  → ENCODING_PCM_16BIT

userSetting = ON, sourceBitDepth = 16:
  ENCODING_PCM_16BIT              (no benefit from hi-res output)

userSetting = OFF:
  ENCODING_PCM_16BIT              (always)
```

## Appendix C: Glossary

| Термин | Определение |
|--------|-------------|
| **Engine** | C++ часть ядра (декодирование, буферизация, state machine) |
| **Host** | Kotlin часть ядра (AudioTrack, session, JNI bridge) |
| **SPSC** | Single-Producer Single-Consumer (тип lock-free очереди) |
| **PCM** | Pulse-Code Modulation — несжатое цифровое аудио |
| **DVC** | Dynamic Volume Control — системная функция Android |
| **HAL** | Hardware Abstraction Layer — интерфейс между AudioFlinger и аудио-драйвером |
| **AudioFlinger** | Системный audio mixer Android |
| **Probe** | Пробное создание AudioTrack для определения поддерживаемых форматов |
| **Frame** | Один семпл на все каналы (stereo frame = 2 samples) |
| **Ring buffer** | Циклический буфер с указателями чтения и записи |
| **Canonical format** | Единый внутренний формат PCM в Engine (float32, interleaved) |

---

*Конец документа.*
