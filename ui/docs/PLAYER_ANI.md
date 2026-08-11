полный технический отчет со всеми анимациями, функциями сглаживания (Easing Curves), микро-взаимодействиями и keyframe-эффектами, используемыми на
экране Player Screen ( index.html ,  styles.css ,  app.js ).
──────
### 🚀 1. Собственные функции сглаживания (CSS Motion Tokens)

В основе всех физических анимаций интерфейса плеера лежат два фирменных временных графика Material 3 Expressive:

:root {
    /* 1. Пружинистый отклик с вылетом за пределы (Overshoot Bounce) */
    --spring-bounce: cubic-bezier(0.34, 1.56, 0.64, 1);

    /* 2. Плавное ускорение и гашение по стандарту Material 3 */
    --ease-expressive: cubic-bezier(0.2, 0.0, 0.0, 1.0);
}
──────
### 🌕 2. Циклическая Keyframe-анимация индикатора сна ( @keyframes pulseMoon )

При активации таймера сна у времени воспроизведения появляется иконка луны ( .sleep-indicator-icon ), которая непрерывно пульсирует:

.sleep-indicator-icon {
    animation: pulseMoon 2s infinite ease-in-out;
}

@keyframes pulseMoon {
    0%, 100% {
    opacity: 0.6;
    transform: scale(0.92);
    }
    50% {
    opacity: 1;
    transform: scale(1.12); /* Увеличение на 12% с нарастанием прозрачности */
    }
}
──────
### 💿 3. Анимация приподнимания и свечения обложки альбома ( .art-wrapper )

Обложка реагирует на воспроизведение и паузу с использованием пружинистой физики:

• Переход:  transition: transform 0.4s var(--spring-bounce), box-shadow 0.4s var(--ease-expressive);
• Состояние Паузы: Стандартный размер  transform: scale(1) , базовая тень  0 12px 30px rgba(0, 0, 0, 0.4) .
• Состояние Воспроизведения ( .art-wrapper.playing ):
    • Обложка приподнимается и увеличивается:  transform: scale(1.02)  (на 2%).
    • Разрастается глубокое динамическое неоновое свечение:  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.5), 0 0 26px var(--m3-expressive-green-glow) .

──────
### 🎛️ 4. Анимация кнопок управления ( .m3-btn ,  #playPauseBtn ,  #heartBtn )

#### А. Пружинистый отклик на нажатие (Press Down Bounce)

Все экспрессивные кнопки плеера при клике или касании пальцем сжимаются, давая тактильную визуальную отдачу:

.m3-btn {
    transition: transform 0.18s var(--spring-bounce), background-color 0.6s ease, box-shadow 0.6s ease;
}

.m3-btn:active {
    transform: scale(0.92) !important; /* Быстрое сжатие до 92% при нажатии */
}

#### Б. 3D-анимация высоты центральной кнопки Play/Pause ( #playPauseBtn )

Главная стадионная кнопка изменяет свою высоту над поверхностью (Elevation) в зависимости от статуса игры:

.btn-play-main {
    transition: transform 0.3s var(--spring-bounce), box-shadow 0.3s var(--ease-expressive);
}

/* 1. Пауза: Кнопка приподнята выше остальных на 5px */
.btn-play-main.paused {
    transform: translateY(-5px);
    box-shadow: 0 20px 42px rgba(0, 0, 0, 0.55), 0 10px 24px rgba(255, 255, 255, 0.3);
}

/* 2. Воспроизведение: Кнопка утоплена вровень с другими кнопками */
.btn-play-main.playing {
    transform: translateY(0);
    box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4);
}

#### В. Анимация пульса при зажатии на 250 мс ( swapSubControlMode() )

При удерживании двухрежимной кнопки Shuffle 🔀 / Repeat 🔁 в течение 250 мс выполняется смена режима, сопровождаемая мгновенной пульсацией:

// В app.js при срабатывании длинного зажатия:
repeatPillContainer.style.transform = 'scale(1.12)'; // Вспышка укрупнения до 112%
setTimeout(() => {
    repeatPillContainer.style.transform = ''; // Возврат в норму через 200 мс
}, 200);
──────
### 🎚️ 5. Анимации ползунка прогресса ( .slider-thumb  &  .slider-fill )

• Увеличение ползунка (Thumb Zoom):
    • При наведении курсора или перетаскивании белый круглый ползунок увелочивается на 25%:

.slider-thumb {
    transition: transform 0.2s var(--spring-bounce);
    transform: translate(-50%, -50%) scale(1);
}

.slider-container:hover .slider-thumb,
.slider-container:active .slider-thumb {
    transform: translate(-50%, -50%) scale(1.25);
}

• Плавная смена цвета закраски:  transition: background-color 0.6s ease  (меняет цвет закраски при смене трека).
• Сброс транзишнов при скруббинге ( .seeking ):  transition: none !important  (гарантирует мгновенное перемещение ползунка под пальцем без отставаний).
──────
### 📜 6. Анимация выезда всплывающего плейлиста ( .drawer-backdrop  &  .drawer-content )

Всплывающее меню плейлиста всплывает снизу вверх поверх плеера:

• Прозрачность подложки (Backdrop Fade):
.drawer-backdrop {
    opacity: 0;
    transition: opacity 0.3s ease;
}
.drawer-backdrop.open {
    opacity: 1;
}

• Выезд листа снизу (Slide-Up Drawer):
.drawer-content {
    transform: translateY(100%); /* Скрыт за нижней границей */
    transition: transform 0.35s var(--ease-expressive);
}

.drawer-backdrop.open .drawer-content {
    transform: translateY(0); /* Плавный выезд на экран */
}

──────
### 📱 7. Анимация панели навигации Android ( .android-navigation-panel )

При переключении режима навигации (3-кнопочный $\leftrightarrow$ Жестовый полоса):

• Трансформация высоты панели:  transition: height 0.35s var(--ease-expressive), min-height 0.35s var(--ease-expressive) .
• Нажатие на системные кнопки ( .nav-sys-btn:active ):  transform: scale(0.85)  (быстрый клик-отклик).
──────
### 📊 Сводный обзор всех анимаций Player Screen

Элемент UI                 │ Эффект / Анимация                       │ Длительность / Функция времени              │ Триггер / Условие
────────────────────────────┼─────────────────────────────────────────┼─────────────────────────────────────────────┼────────────────────────────────────
Обложка альбома            │ Scale 1.02 + неоновый HSL-Glow          │  0.4s   cubic-bezier(0.34, 1.56, 0.64, 1)   │ Воспроизведение трека ( .playing )
Play/Pause кнопка          │ translateY(-5px) $\to$ 0px              │  0.3s   cubic-bezier(0.34, 1.56, 0.64, 1)   │ Переключение Play / Pause
Shuffle/Repeat зажатие     │ Scale 1.12 pulse                        │  0.2s   ease                                │ Hold 250 мс
Все кнопки  .m3-btn        │ Scale 0.92 press down                   │  0.18s   cubic-bezier(0.34, 1.56, 0.64, 1)  │ Нажатие ( :active )
Ползунок слайдера          │ Scale 1.25 zoom                         │  0.2s   cubic-bezier(0.34, 1.56, 0.64, 1)   │ Hover / Dragging
Иконка Луны (Сон)          │ Pulse opacity & scale (0.92 $\to$ 1.12) │  2s   ease-in-out  (Infinite)               │ Активный таймер сна
Плейлист Drawer            │ Slide-Up translateY(100% $\to$ 0)       │  0.35s   cubic-bezier(0.2, 0, 0, 1)         │ Открытие меню плейлиста
Цвета интерфейса           │ Color & Glow transition                 │  0.6s   ease                                │ Смена обложки трека