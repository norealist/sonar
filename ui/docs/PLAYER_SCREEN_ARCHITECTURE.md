# Архитектура и точный визуально-технический спецификатор Player Screen (`player-container`)

Документ содержит подробнейший разбор интерфейса экрана **Player Screen** (`index.html`, `styles.css`, `app.js`) внутри главного контейнера `.player-container`: типографики, алгоритма извлечения цвета из обложки трека и исчерпывающего описания блочной модели (Content, Padding, Border, Margin, Border-Radius, Shadows, Glows) для каждого элемента.

---

## 🔤 1. Точный разбор типографики и шрифтов

### ⚠️ Подтверждение спецификации шрифтов Display
В названии трека (`.track-title`) и имени артиста (`.artist-name`) используется шрифт **`Syne ExtraBold` (`font-weight: 800`)**, заданный через CSS-переменную `--font-display`:

```css
:root {
  --font-display: 'Syne', 'Plus Jakarta Sans', -apple-system, sans-serif;
  --font-body: 'Plus Jakarta Sans', -apple-system, sans-serif;
}
```

### Сводная таблица типографики в `.player-container`

| Элемент / Селектор | Шрифт (`font-family`) | Размер (`font-size`) | Жирность (`font-weight`) | Межбуквенный интервал (`letter-spacing`) | Высота строки (`line-height`) | Особенности текста / Содержимое |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Тег индекса трека** (`.track-tag`) | `--font-body` (`Plus Jakarta Sans`) | `0.62rem` (~9.9px) | `800` (ExtraBold) | `1.5px` | `Normal` | Uppercase, динамический текст `TRACK 01 / 21`, цвет `var(--m3-expressive-green)` |
| **Имя артиста** (`.artist-name`) | `--font-display` (**`Syne`**) | `1.4rem` (`1.15rem` при > 20 симв.) | `800` (ExtraBold) | `1px` | `1.1` | Uppercase, динамическое имя артиста (например, `PAXNKOXD`), кликабелен |
| **Название трека** (`.track-title`) | `--font-display` (**`Syne`**) | `1.3rem` | `800` (ExtraBold) | `-0.5px` | `1.1` | **`word-break: keep-all`**, `overflow-wrap: normal`, переносы по словам, переносы `<br>` при > 7 симв. с пробелами |
| **Таймер / Длительность** (`.time-display`) | `--font-body` (`Plus Jakarta Sans`) | `0.95rem` (~15.2px) | `800` (ExtraBold) | `0.5px` | `Normal` | **`font-variant-numeric: tabular-nums`**, `"tnum"` (моноширинный отсчет цифр), интерактивный клик настройки сна |
| **Разделитель времени** (`.time-separator`) | `--font-body` (`Plus Jakarta Sans`) | `0.85rem` | `800` | `Normal` | `Normal` | Символ `/` с прозрачностью `opacity: 0.5` |
| **Индикатор сна** (`.sleep-indicator-icon`) | `Material Symbols` | `15px` | `400` | `Normal` | `Normal` | Иконка `bedtime`, пульсирующая анимация `@keyframes pulseMoon` |
| **Кнопки управления** (`.material-symbols-rounded`) | `Material Symbols` | `24px` – `68px` | `400` – `800` | `Normal` | `Normal` | Иконки `favorite`, `shuffle`, `repeat`, `repeat_one`, `queue_music`, `play_arrow`, `pause`, `skip_previous`, `skip_next` |

---

## 🎨 2. Полный разбор алгоритма изменения цвета от обложки трека

Элементы плеера адаптируют свои акцентные цвета и свечения под палитру текущей обложки. 

### Пошаговая схема работы алгоритма в `app.js`

```
[ albumArt 'load' / complete ]
       │
       ▼
[ Offscreen Canvas 64×64 px ] ──> Draw Image downsampled
       │
       ▼
[ getImageData (Buffer 16,384 bytes) ] ──> Sampling every 4th pixel (i += 16)
       │
       ▼
[ RGB (0..255) ──> HSL (0..1) Conversion ]
       │
       ▼
[ Filter Thresholds ]
 ├─ l > 0.12  (Отсечение черного)
 ├─ l < 0.88  (Отсечение ослепительно белого)
 └─ s > 0.15  (Отсечение невыразительных серых градиентов)
       │
       ▼
[ Rating Score Formula ]
 Score = Saturation × 1.5 + (1 - |Luminance - 0.5|)
       │
       ▼
[ Sort candidates descending by Score ]
 ├─ Primary Accent (colorBins[0])
 └─ Secondary Accent (Contrasting Hue |ΔH| > 0.15)
       │
       ▼
[ Dynamic CSS Variable Injection ]
 ├─ --m3-expressive-green: primaryHex (#HEX)
 ├─ --m3-expressive-green-glow: rgba(r, g, b, 0.45)
 ├─ --m3-expressive-pink: secondaryHex (#HEX)
 ├─ --m3-expressive-pink-glow: rgba(r2, g2, b2, 0.45)
 └─ --m3-surface-gray: darkSurface (hslToHex(h, min(s,0.25), 0.28))
```

### Отклик визуальных элементов в CSS на извлеченную палитру

1. **Обложка альбома (`.art-wrapper.playing`)**:
   - При воспроизведении заднее свечение окрашивается в извлеченный цвет:
     `box-shadow: 0 18px 40px rgba(0, 0, 0, 0.5), 0 0 26px var(--m3-expressive-green-glow)`.
2. **Тег индекса трека (`.track-tag`)**:
   - Текст `TRACK 01 / 21` подсвечивается извлеченным акцентом: `color: var(--m3-expressive-green)` с плавным переходом `transition: color 0.6s ease`.
3. **Заполнение ползунка прогресса (`.slider-fill`)**:
   - Заполняемая полоса принимает первичный акцент: `background: var(--m3-expressive-green)` и свечение `box-shadow: 0 0 10px var(--m3-expressive-green-glow)`.
4. **Кнопка Избранное (`.btn-heart.active`)**:
   - При добавлении в любимые подсвечивается вторичным гармонирующим акцентом: `background: var(--m3-expressive-pink)` и тенью `box-shadow: 0 6px 16px var(--m3-expressive-pink-glow)`.
5. **Кнопки Shuffle 🔀 и Repeat 🔁 (`.active-green`)**:
   - При активации подсвечиваются основным тоном обложки: `background: var(--m3-expressive-green)` и `color: var(--m3-text-dark)`.
6. **Кнопки Переключения треков Prev / Next (`.btn-green:active`)**:
   - При нажатии вспыхивают акцентом: `background: var(--m3-expressive-green)`.

---

## 📐 3. Полный аудит блочной модели (Box Model) элементов `player-container`

Ниже приведен подробный технический спецификатор для каждого компонента внутри `.player-container` (Margin, Border, Padding, Content, Border-Radius, Shadows, Glows).

### 1. Главный контейнер плеера (`.player-container`)
- **Content**: Flexbox контейнер (`flex: 1`, `display: flex`, `flex-direction: column`, `justify-content: space-between`).
- **Padding**: `10px 20px 14px 20px` (смартфон portrait) / `24px 32px 24px 32px` (планшет landscape).
- **Margin**: `0`.
- **Border**: Нет.
- **Grid Layout (Tablet Landscape)**: `display: grid; grid-template-columns: 380px 1fr; gap: 32px`.

---

### 2. Верхний блок артиста (`.artist-header`)
- **Content**: Выравнивание по правому краю (`text-align: right`).
- **Padding**: `padding-top: 4px`.
- **Margin**: `0`.
- **Border**: Нет.

#### `.track-tag`
- **Content**: Текст `TRACK 01 / 21`.
- **Padding**: `0`.
- **Margin**: `margin-bottom: 1px`.
- **Color**: `var(--m3-expressive-green)` (`transition: color 0.6s ease`).

#### `#artistName` (`.artist-name`)
- **Content**: Текст имени артиста в верхнем регистре (Uppercase).
- **Padding**: `0`.
- **Margin**: `0`.
- **Line-Height**: `1.1`.
- **Cursor**: `pointer`.

---

### 3. Секция обложки альбома (`.album-section`, `.art-wrapper`, `.album-art`)

#### `.album-section`
- **Content**: Квадратный контейнер `aspect-ratio: 1 / 1`, `width: 100%`.
- **Padding**: `0`.
- **Margin**: `6px 0` (в портретном режиме) / `0` (в альбомном режиме планшета).
- **Align**: `display: flex; align-items: center; justify-content: center`.

#### `.art-wrapper`
- **Content**: Обертка картинки `width: 100%; height: 100%`.
- **Padding**: `0`.
- **Margin**: `0`.
- **Border**: `1px solid rgba(255, 255, 255, 0.15)`.
- **Border-Radius**: `36px` (`border-radius: 36px`).
- **Box-Shadow (Paused)**: `0 12px 30px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.15)`.
- **Box-Shadow (Playing - `.art-wrapper.playing`)**: `0 18px 40px rgba(0, 0, 0, 0.5), 0 0 26px var(--m3-expressive-green-glow)`.
- **Transform (Playing)**: `scale(1.02)`.

#### `.album-art`
- **Content**: Изображение обложки `width: 100%; height: 100%; object-fit: cover`.

---

### 4. Строка с информацией о треке (`.track-info`, `.track-title`, `.time-display`)

#### `.track-info`
- **Content**: Flexbox контейнер (`display: flex; align-items: baseline; justify-content: space-between`).
- **Padding**: `0`.
- **Margin**: `margin-top: 2px`.

#### `#trackTitle` (`.track-title`)
- **Content**: Название трека.
- **Max-Width**: `220px`.
- **Padding**: `0`.
- **Margin**: `0`.
- **Word-Break**: **`keep-all`**, `overflow-wrap: normal`.

#### `#timeDisplay` (`.time-display`)
- **Content**: Интерактивное табло времени `<span id="currentTime">0:00</span> / <span id="totalDuration">1:36</span>`.
- **Padding**: `3px 8px`.
- **Margin**: `0`.
- **Border-Radius**: `10px`.
- **Hover State**: `background: rgba(255, 255, 255, 0.15)`.

---

### 5. Скруббер прогресса (`.progress-section`, `.slider-container`, `.slider-track`, `.slider-fill`, `.slider-thumb`)

#### `.progress-section`
- **Margin**: `6px 0 12px 0`.
- **Padding**: `0`.

#### `.slider-container`
- **Content**: Обертка интерактивного слайдера `width: 100%; height: 18px`.
- **Cursor**: `pointer`.

#### `.slider-track`
- **Content**: Фоновая дорожка слайдера `width: 100%; height: 10px`.
- **Border-Radius**: `100px`.
- **Background**: `var(--m3-surface-gray-dark)`.
- **Margin / Padding**: `0`.

#### `.slider-fill`
- **Content**: Полоса заполнения проигранного отрезка `height: 100%; width: X%`.
- **Border-Radius**: `100px`.
- **Background**: `var(--m3-expressive-green)`.
- **Box-Shadow**: `0 0 10px var(--m3-expressive-green-glow)`.

#### `.slider-thumb`
- **Content**: Белый круглый ползунок `width: 24px; height: 20px`.
- **Border-Radius**: `100px`.
- **Background**: `var(--m3-expressive-white)`.
- **Box-Shadow**: `0 2px 6px rgba(0, 0, 0, 0.4)`.
- **Transform**: `translate(-50%, -50%) scale(1)`.
- **Hover / Active Transform**: `translate(-50%, -50%) scale(1.25)`.

---

### 6. Экспрессивная сетка кнопок управления (`.expressive-controls-grid`)

- **Content**: 3-колоночная сетка `display: grid; grid-template-columns: 96px 1fr 96px; grid-template-rows: 154px; gap: 10px`.
- **Width**: `100%`.

#### Левая колонка (`.col-left`)
- **Content**: Flexbox контейнер (`display: flex; flex-direction: column; gap: 8px`).

##### Кнопка Избранное (`#heartBtn` / `.btn-heart`)
- **Size**: `width: 100%; flex: 1`.
- **Padding**: `0`.
- **Margin**: `0`.
- **Border-Radius**: `22px`.
- **Background**: `var(--m3-expressive-pink)`.
- **Box-Shadow**: `0 12px 30px rgba(0, 0, 0, 0.4), 0 6px 16px var(--m3-expressive-pink-glow), 0 0 0 1px rgba(255, 255, 255, 0.15)`.

##### Нижний ряд доп-управления (`.sub-controls-row`)
- **Content**: Flexbox контейнер (`display: flex; gap: 8px; height: 44px`).

##### Двухрежимный контейнер 250 мс (`.repeat-pill-container`)
- **Content**: Пилл-контейнер (`flex: 1; height: 100%`).
- **Border-Radius**: `14px`.
- **Background**: `rgba(255, 255, 255, 0.85)`.
- **Box-Shadow**: `0 12px 30px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.15)`.
- **Active State (`.active-green`)**: `background: var(--m3-expressive-green); color: var(--m3-text-dark)`.

##### Кнопка Очереди плейлиста (`#queueBtn`)
- **Size**: `flex: 1; height: 100%`.
- **Border-Radius**: `14px`.
- **Background**: `rgba(255, 255, 255, 0.85)`.
- **Box-Shadow**: `0 12px 30px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.15)`.

---

#### Центральная колонка (`.col-center`) & Кнопка Play/Pause (`#playPauseBtn`)
- **Content**: Одиночная стадионная кнопка `width: 100%; height: 100%`.
- **Border-Radius**: `100px` (Стадионная капсула).
- **Background**: `rgba(255, 255, 255, 0.88)`.
- **Icon Size**: `68px`.
- **Paused State (`.paused`)**:
  - `transform: translateY(-5px)`.
  - `box-shadow: 0 20px 42px rgba(0, 0, 0, 0.55), 0 0 0 1px rgba(255, 255, 255, 0.25)`.
- **Playing State (`.playing`)**:
  - `transform: translateY(0)`.
  - `box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.15)`.

---

#### Правая колонка (`.col-right`) & Кнопки Prev / Next (`#prevBtn`, `#nextBtn`)
- **Content**: 2 кнопки в столбик (`display: flex; flex-direction: column; gap: 8px`).
- **Button Size**: `width: 100%; flex: 1`.
- **Border-Radius**: `22px`.
- **Background**: `rgba(255, 255, 255, 0.85)`.
- **Box-Shadow**: `0 12px 30px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.15)`.
- **Icon Size**: `36px`.
