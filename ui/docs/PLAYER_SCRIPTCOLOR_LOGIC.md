### 1. ⚙️ Триггеры запуска и инициализация

В  app.js  функция извлечения цвета  extractColorsFromImage  подвязана к событию загрузки элемента обложки  <img id="albumArt"> :

// Запуск при каждом срабатывании загрузки картинки (при переключении треков)
albumArt.addEventListener('load', () => extractColorsFromImage(albumArt));

// Резервный запуск, если картинка уже закеширована браузером
if (albumArt.complete && albumArt.naturalWidth !== 0) {
    extractColorsFromImage(albumArt);
}

При каждом вызове  loadTrack(index)  меняется атрибут  albumArt.src = track.cover , что мгновенно запускает цепочку пересчета палитры.
──────
### 2. 🖼️ Даунсэмплирование изображения через Offscreen Canvas

Чтобы обработка происходила мгновенно (за < 2 миллисекунд) без подвисания UI, исходная картинка (которая может быть $1000 \times 1000 \text{ px}$)
уменьшается в памяти до миниатюры $64 \times 64 \text{ px}$:

const sampleCanvas = document.createElement('canvas');
const sampleCtx = sampleCanvas.getContext('2d');
sampleCanvas.width = 64;
sampleCanvas.height = 64;

// Отрисовка обложки в offscreen холст 64x64 px
sampleCtx.drawImage(imgEl, 0, 0, 64, 64);

// Считывание одномерного массива RGBA пикселей (длина массива: 64 * 64 * 4 = 16,384 байта)
const imgData = sampleCtx.getImageData(0, 0, 64, 64).data;
──────
### 3. 🧪 Сэмплирование пикселей и фильтрация тонов (HSL)

Скрипт проходит по массиву байтов пикселей с шагом  16  (то есть анализирует каждый 4-й пиксель изображения) и переводит цвета из модели RGB в модель HSL
( Hue ,  Saturation ,  Luminance ):

let colorBins = [];
for (let i = 0; i < imgData.length; i += 16) {
    const r = imgData[i];
    const g = imgData[i + 1];
    const b = imgData[i + 2];

    // Конвертация RGB -> HSL (значения от 0 до 1)
    const hsl = rgbToHsl(r, g, b);

    // ─── ФИЛЬТРАЦИЯ ───
    // Исключаем грязные и слепящие крайности
    if (hsl.l > 0.12 && hsl.l < 0.88 && hsl.s > 0.15) {
    colorBins.push({
        r, g, b, hsl,
        // Формула расчета сочности и выразительности тона
        score: hsl.s * 1.5 + (1 - Math.abs(hsl.l - 0.5))
    });
    }
}

#### Критерии отсечения:

•  hsl.l > 0.12 : Исключает глубокие черные тени.
•  hsl.l < 0.88 : Исключает слепяще-белые участки и блики.
•  hsl.s > 0.15 : Исключает тусклые серые тона.

#### Формула рейтинга цвета ( score ):

$$\text{Score} = \text{Saturation} \times 1.5 + (1 - |\text{Luminance} - 0.5|)$$
Приоритет отдается максимально насыщенным ( Saturation ) цветам, имеющим яркость ( Luminance ), близкую к идеальной медиане $0.5$.
──────
### 4. 🎯 Выбор первичного, вторичного и темного фонового тона

Скрипт сортирует отфильтрованные цвета по рейтингу и выбирает 3 ключевых тона:

// Сортировка по убыванию рейтинга выразительности
colorBins.sort((a, b) => b.score - a.score);

// 1. Первичный сочный акцент (Primary Accent)
const primary = colorBins[0];

// 2. Вторичный акцент (Secondary Accent) - ищет тон с разницей по цветовому кругу > 54° (> 0.15)
let secondary = colorBins.find(c => Math.abs(c.hsl.h - primary.hsl.h) > 0.15)
                || colorBins[Math.floor(colorBins.length / 2)];

// 3. Гармонирующий темный фон поверхности (Dark Surface)
// Берет оттенок (Hue) обложки, занижает насыщенность (<= 25%) и ставит темную яркость (28%)
const darkSurface = hslToHex(primary.hsl.h, Math.min(primary.hsl.s, 0.25), 0.28);

// 4. RGBA строчки для неонового свечения
const glowRGBA = `rgba(${primary.r}, ${primary.g}, ${primary.b}, 0.45)`;
const glowPinkRGBA = `rgba(${secondary.r}, ${secondary.g}, ${secondary.b}, 0.45)`;
──────
### 5. 💉 Динамическая запись в CSS-переменные DOM ( :root )

Вычисленные цвета мгновенно записываются в главные CSS-переменные через  document.documentElement.style :

const root = document.documentElement;

// Запись первичного HEX-акцента и его свечения
root.style.setProperty('--m3-expressive-green', primaryHex);
root.style.setProperty('--m3-expressive-green-glow', glowRGBA);

// Запись вторичного HEX-акцента и его свечения
root.style.setProperty('--m3-expressive-pink', secondaryHex);
root.style.setProperty('--m3-expressive-pink-glow', glowPinkRGBA);

// Запись сгенерированной темной поверхности
root.style.setProperty('--m3-surface-gray', darkSurface);
──────
### 6. 🖼️ Визуальная реакция элементов UI плеера в CSS

Как только  app.js  обновляет CSS-переменные в  :root , элементы интерфейса плеера автоматически изменяют свой внешний вид благодаря реактивным связям в
styles.css :

                CSS-Переменная                        Визуальный элемент Плеера
┌──────────────────────────────────────────┐     ┌──────────────────────────────────────────────┐
│ --m3-expressive-green                    │ ──> │ 1. Полоса прогресс-бара (#sliderFill)        │
│ (Первичный HEX-акцент обложки)           │     │ 2. Кнопки Shuffle 🔀 / Repeat 🔁 (.active)    │
│                                          │     │ 3. Кнопки Prev/Next (.btn-green:active)     │
│                                          │     │ 4. Тег индекса трека (.track-tag)            │
└──────────────────────────────────────────┘     └──────────────────────────────────────────────┘

┌──────────────────────────────────────────┐     ┌──────────────────────────────────────────────┐
│ --m3-expressive-green-glow               │ ──> │ 1. Неоновое свечение обложки (.playing)     │
│ (Прозрачный RGBA-соус свечения)          │     │ 2. Свечение ползунка прогресса (.slider-fill)│
└──────────────────────────────────────────┘     └──────────────────────────────────────────────┘

┌──────────────────────────────────────────┐     ┌──────────────────────────────────────────────┐
│ --m3-expressive-pink / -glow             │ ──> │ Кнопка Избранное (#heartBtn / .active)       │
└──────────────────────────────────────────┘     └──────────────────────────────────────────────┘

┌──────────────────────────────────────────┐     ┌──────────────────────────────────────────────┐
│ --m3-surface-gray                        │ ──> │ Гармонирующий темный тон контейнера поверхности│
└──────────────────────────────────────────┘     └──────────────────────────────────────────────┘

#### Плавность переходов (Transitions):

• Цвет тега индекса трека ( .track-tag ):  transition: color 0.6s ease .
• Цвет заполнения скруббера ( .slider-fill ):  transition: background-color 0.6s ease .
• Свечение и объем тени обложки ( .art-wrapper ):  transition: box-shadow 0.4s var(--ease-expressive) .