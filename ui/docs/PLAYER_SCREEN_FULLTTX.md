точный отчет по всем 5 основным блокам интерфейса плеера в  .player-container  на основе кода  index.html ,  styles.css  и  app.js .
──────
### 1. 🎤 Блок артиста ( .artist-header )

Этот блок расположен вверху панели и отвечает за отображение индекса текущего трека и имени исполнителя.

• Размеры и позиционирование:
    •  width: 100%
    •  text-align: right  (выравнивание текста по правому краю)
    • Padding:  padding-top: 4px; padding-bottom: 0; padding-left: 0; padding-right: 0
    • Margin:  0
    • Border: Отсутствует
• Внутренние элементы (Content):
    1. Тег индекса трека ( .track-tag ):
        • Content: Динамическая строка формата  TRACK 01 / 21 .
        • Font:  Plus Jakarta Sans ,  font-size: 0.62rem  (~9.9px),  font-weight: 800  (ExtraBold).
        • Letter-spacing:  1.5px .
        • Color:  var(--m3-expressive-green)  (плавно меняет цвет с  transition: color 0.6s ease  под обложку).
        • Margin:  margin-bottom: 1px .
        • Padding:  0 .
    2. Имя артиста ( #artistName  /  .artist-name ):
        • Content: Текст имени исполнителя в верхнем регистре (например,  PAXNKOXD ).
        • Font:  Syne ,  font-size: 1.4rem  (~22.4px),  font-weight: 800  (ExtraBold).
        • Letter-spacing:  1px .
        • Text-transform:  uppercase .
        • Line-height:  1.1 .
        • Color:  var(--m3-text-primary)  ( #FFFFFF ).
        • Margin:  0 .
        • Padding:  0 .
        • Адаптивность ( .artist-name.long-text ): При длине имени > 20 символов шрифт уменьшается до  1.15rem  ( transition: font-size 0.3s ease ). Клик
        по имени открывает страницу артиста  artist.html .


──────
### 2. 💿 Блок обложки альбома ( .album-section )

Центральный визуальный элемент с динамическим свечением и реакцией на статус воспроизведения.

• Размеры и позиционирование:
    •  width: 100%
    •  aspect-ratio: 1 / 1  (строго квадратная пропорция 1:1)
    •  display: flex; align-items: center; justify-content: center
    • Margin:  6px 0  (отступы сверху и снизу по  6px , слева и справа  0 )
    • Padding:  0
    • Border: Отсутствует
• Обертка обложки ( .art-wrapper ):
    • Content: Картинка  <img class="album-art">  ( width: 100%; height: 100%; object-fit: cover ).
    • Border-Radius:  36px  со всех 4 углов.
    • Border:  1px solid rgba(255, 255, 255, 0.15)  (тонкий полупрозрачный белый контур).
    • Box-Shadow в паузе:
        • Основная тень:  0 12px 30px rgba(0, 0, 0, 0.4)
        • Белая контурная тень:  0 0 0 1px rgba(255, 255, 255, 0.15)
    • Box-Shadow и Transform в игре ( .art-wrapper.playing ):
        • При воспроизведении обложка плавно увеличивается:  transform: scale(1.02)  ( transition: transform 0.4s var(--spring-bounce) ).
        • Тень глубокого уровня:  0 18px 40px rgba(0, 0, 0, 0.5) .
        • Динамическое эмбиентное свечение:  0 0 26px var(--m3-expressive-green-glow)  (окрашивается в извлеченный акцент текущей обложки).


──────
### 3. 🎵 Блок информации о треке и времени ( .track-info )

Строка, размещенная прямо под обложкой, содержащая название трека и кликабельный моноширинный таймер.

• Размеры и позиционирование:
    •  width: 100%
    •  display: flex; align-items: baseline; justify-content: space-between
    • Margin:  margin-top: 2px; margin-bottom: 0; margin-left: 0; margin-right: 0
    • Padding:  0
    • Border: Отсутствует
• Внутренние элементы (Content):
    1. Название трека ( #trackTitle  /  .track-title ):
        • Content: Текст названия песни (например,  proxy ).
        • Font:  Syne ,  font-size: 1.3rem  (~20.8px),  font-weight: 800  (ExtraBold).
        • Letter-spacing:  -0.5px .
        • Line-height:  1.1 .
        • Word-break:  keep-all ,  overflow-wrap: normal  (слово не разрывается посередине).
        • Max-width:  220px  (с авто-переносом пробелов на новую строку через  <br>  при длине > 7 символов).
        • Color:  var(--m3-text-primary)  ( #FFFFFF ).
        • Margin / Padding:  0 .
    2. Индикатор времени и таймера сна ( #timeDisplay  /  .time-display ):
        • Content: Интерактивное табло  <span id="currentTime">0:00</span> <span class="time-separator">/</span> <span id="totalDuration">1:36</span>  +
        иконка  bedtime .
        • Font:  Plus Jakarta Sans ,  font-size: 0.95rem  (~15.2px),  font-weight: 800  (ExtraBold).
        • Tabular Precision:  font-variant-numeric: tabular-nums ,  font-feature-settings: "tnum"  (цифры имеют одинаковую ширину, исключая подрагивание
        при отсчете секунд).
        • Padding:  3px 8px .
        • Border-Radius:  10px .
        • Hover State: При наведении плавно появляется подложка  background: rgba(255, 255, 255, 0.15) .
        • Иконка таймера сна ( .sleep-indicator-icon ):  font-size: 15px ,  color: #FFFFFF , анимация  @keyframes pulseMoon 2s infinite ease-in-out .


──────
### 4. 🎚️ Скруббер прогресса ( .progress-section )

Интерактивный дорожный слайдер с функцией Zero-Lag Scrubbing (без задержек отклика).

• Размеры и отступы внешнего блока ( .progress-section ):
    •  width: 100%
    • Margin:  6px 0 12px 0  (сверху  6px , снизу  12px )
    • Padding:  0
• Интерактивный контейнер ( .slider-container ):
    •  height: 18px
    •  position: relative; width: 100%; display: flex; align-items: center; cursor: pointer
    • Невидимый нативный элемент  <input type="range"> :  position: absolute; width: 100%; height: 100%; opacity: 0; z-index: 10 .
• Дорожка слайдера ( .slider-track ):
    • Height:  10px
    • Border-Radius:  100px  (скругленный капсюль)
    • Background:  var(--m3-surface-gray-dark)  ( #585E65 )
    • Margin / Padding:  0
• Полоса заполнения ( .slider-fill ):
    • Height:  100%  ( 10px )
    • Width:  0%  –  100%  (динамический процент воспроизведения)
    • Border-Radius:  100px
    • Background:  var(--m3-expressive-green)  (подхватывает акцент обложки)
    • Box-Shadow:  0 0 10px var(--m3-expressive-green-glow)  (неоновое свечение)
    • Транзишн:  transition: background-color 0.6s ease .
• Белый ползунок ( .slider-thumb ):
    • Dimensions:  width: 24px; height: 20px  (овальный индикатор)
    • Border-Radius:  100px
    • Background:  var(--m3-expressive-white)  ( #FFFFFF )
    • Box-Shadow:  0 2px 6px rgba(0, 0, 0, 0.4)
    • Transform в покое:  translate(-50%, -50%) scale(1)
    • Transform при наведении/зажатии:  translate(-50%, -50%) scale(1.25)  (увеличивается на 25%)
    • Механика Zero-Lag: При зажатии ( .slider-container.seeking ) свойства  transition  на  .slider-fill  и  .slider-thumb  сбрасываются в  none
    !important , что исключает лаг следования ползунка за курсором/пальцем.

──────
### 5. 🎛️ Экспрессивная сетка кнопок управления ( .expressive-controls-grid )

Главная панель управления плеером, построенная по 3-колоночной асимметричной схеме.

• Размеры и пропорции сетки:
    •  width: 100%
    •  display: grid
    • Grid-Template-Columns:  96px 1fr 96px  (Левая колонка  96px , Центральная  1fr , Правая  96px )
    • Grid-Template-Rows:  154px
    • Gap:  10px
    • Align-Items:  stretch
    • Margin / Padding:  0

──────
#### 📍 Левая колонка ( .col-left )

• Layout:  display: flex; flex-direction: column; gap: 8px .

1. Кнопка Избранное ( #heartBtn  /  .btn-heart ):
    • Size:  width: 100%; flex: 1
    • Border-Radius:  22px
    • Background (Выкл):  var(--m3-expressive-pink)  ( #FF7B93 )
    • Box-Shadow:  0 12px 30px rgba(0, 0, 0, 0.4), 0 6px 16px var(--m3-expressive-pink-glow), 0 0 0 1px rgba(255, 255, 255, 0.15)
    • Heart Icon ( .heart-icon ):  font-size: 38px , Material Symbol  FILL: 1 ,  wght: 700 , цвет  #FFFFFF .
2. Ряд доп-кнопок ( .sub-controls-row ):
    • Size:  height: 44px; display: flex; gap: 8px; position: relative .
3. Двухрежимная кнопка 250 мс ( .repeat-pill-container ):
    • Size:  flex: 1; height: 100%  (занимает левую половину ряда)
    • Border-Radius:  14px
    • Background:  rgba(255, 255, 255, 0.85)
    • Box-Shadow:  0 12px 30px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.15)
    • Механика 250 мс: Зажатие на 250 мс сменяет активную кнопку (Shuffle 🔀 $\leftrightarrow$ Repeat 🔁) с пульсом  scale(1.12) .
    • Active Green State ( .active-green ): При активации любого из двух режимов окрашивается в  background: var(--m3-expressive-green)  с текстом
    #04240C .
4. Кнопка вызова плейлиста ( #queueBtn ):
    • Size:  flex: 1; height: 100%  (занимает правую половину ряда)
    • Border-Radius:  14px
    • Background:  rgba(255, 255, 255, 0.85)
    • Box-Shadow:  0 12px 30px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.15)
    • Icon:  queue_music ,  font-size: 22px .

──────
#### 📍 Центральная колонка ( .col-center ) & Кнопка Play/Pause ( #playPauseBtn )

• Layout:  display: flex; width: 100%; height: 100% .

1. Стадионная кнопка Play/Pause ( .btn-play-main ):
    • Dimensions:  width: 100%; height: 100%
    • Border-Radius:  38px  (Стадионный округлый капсюль)
    • Background:  var(--m3-expressive-white)  ( #FFFFFF )
    • Hover Background:  var(--m3-expressive-white-off)  ( #E2E8EE )
    • Play/Pause Icon ( .play-icon ):  font-size: 56px ,  color: #111318 , Material Symbol  FILL: 1 ,  wght: 800 .
2. Состояния Высоты и Теней:
    • Состояние Паузы ( paused  / по умолчанию):
        • Elevation: Приподнята выше всех элементов:  transform: translateY(-5px)
        • 3D-Тень:  box-shadow: 0 20px 42px rgba(0, 0, 0, 0.55), 0 10px 24px rgba(255, 255, 255, 0.3), 0 0 0 1px rgba(255, 255, 255, 0.9)
    • Состояние Воспроизведения ( playing ):
        • Elevation: Опущена вровень с соседями:  transform: translateY(0)
        • Тень:  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.8)


──────
#### 📍 Правая колонка ( .col-right ) & Кнопки Prev / Next

• Layout:  display: flex; flex-direction: column; gap: 8px; width: 100%; height: 100% .

1. Кнопки Prev ( #prevBtn ) и Next ( #nextBtn ):
    • Dimensions:  width: 100%; flex: 1
    • Border-Radius:  22px
    • Background:  var(--m3-expressive-green)  ( #3CE068 )
    • Box-Shadow:  0 12px 30px rgba(0, 0, 0, 0.4), 0 6px 16px var(--m3-expressive-green-glow), 0 0 0 1px rgba(255, 255, 255, 0.15)
    • Icons:  skip_previous  и  skip_next ,  font-size: 34px ,  color: #111318 ,  FILL: 1 ,  wght: 800 .
    • Active Click State:  transform: scale(0.92)  при клике/нажатии.