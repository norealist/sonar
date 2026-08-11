# Material 3 Expressive UI & Design System Documentation

## 🎨 Overview & Design Philosophy

This document outlines the complete **Material 3 Expressive Design System** built for high-end web applications and multi-screen mobile/tablet music player interfaces (`Main Screen`, `Player Screen`, and `Artist Screen`). It provides reusable tokens, component specifications, typography matrices, color algorithms, and responsive layout rules.

---

## 💎 Design Pillars

1. **Expressive Geometry**: Organic, asymmetrical, and stadium-pill shapes combined with rounded corners (`14px` to `100px`).
2. **Dynamic Palette Extraction**: Real-time color extraction from album artwork and artist hero banners using HTML5 `<canvas>` and ambient HSL glows.
3. **Tabular & Typographic Precision**: Clean typography utilizing tabular monospaced numbers (`tnum`) for timers and strictly scoped font hierarchies per screen.
4. **Fluid Multi-Device Layouts**: Seamless adaptation between handheld smartphones (`360×800`) and tablet devices (`iPad Pro 11"`, `iPad Mini`, `Android Tablet`) in both Portrait and Landscape orientations.
5. **Interactive Controls**: 250ms long-press gestures for mode swapping (Shuffle 🔀 $\leftrightarrow$ Repeat 🔁 and Grid Tile Configurator 🔳).

---

## 🔤 Screen-by-Screen Typography Matrix

### 1. Main Screen (`main.html`, `main.css`, `main.js`)

| Element / Selector | Font Family | Size | Weight | Properties & Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Brand Logo Text** (`.main-logo-text`) | `Disco` (`fonts/Disco.ttf`) | `2.2rem` | `Normal` | `@font-face`, `letter-spacing: 5px`, `line-height: 1` |
| **Section Subheader Title** (`.subheader-title`) | `Rubik` | `0.6rem` (Phone) / `0.8rem` (Tablet) | `800` | Uppercase, `letter-spacing: 0.8px` |
| **Sort / Filter Button** (`.filter-btn`) | `Rubik` | `0.6rem` (Phone) / `0.8rem` (Tablet) | `700` | Uppercase, `letter-spacing: 0.8px` |
| **Artist Divider Tag** (`.divider-tag`) | `Rubik` | `0.65rem` | `800` | Uppercase, `letter-spacing: 1.5px`, auto-truncated at 25 chars (`...`) |
| **Track Row Title** (`.main-track-title`) | `Rubik` | `0.98rem` | `800` | Ellipsis truncation on overflow |
| **Tile Card Title** (`.single-tile-title`) | `Rubik` | `1.05rem` | `800` | `line-height: 1.1`, `word-break: break-word` |
| **Track Row Artist** (`.main-track-artist`) | `Plus Jakarta Sans` | `0.8rem` | `500` | Semi-transparent secondary text |
| **Audio Metadata Badge** (`.main-track-meta`, `.single-tile-audio-meta`) | `Plus Jakarta Sans` | `0.58rem` / `0.52rem` | `600` | Uppercase, `letter-spacing: 0.88px`, FLAC Lossless specs |
| **Grid Popout Title** (`.popout-title`, `.popout-val`) | `Rubik` / `Plus Jakarta Sans` | `0.72rem` / `0.7rem` | `800` | Uppercase title, green accent value |
| **System Status Bar Clock** (`.time-text`) | `Plus Jakarta Sans` | `0.78rem` | `800` | Tabular monospaced numbers |
| **System & Desktop UI Icons** | `Material Symbols Rounded` | `16px` – `26px` | `400`–`700` | Google Material Symbols font |

---

### 2. Player Screen (`index.html`, `styles.css`, `app.js`)

| Element / Selector | Font Family | Size | Weight | Properties & Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Track Title** (`.track-title`) | `Plus Jakarta Sans` | `1.3rem` | `800` | `line-height: 1.1`, `letter-spacing: -0.5px`, `word-break: keep-all` |
| **Artist Name Header** (`#artistName`) | `Syne` | `1.4rem` (Standard) / `1.15rem` (Long > 20 chars) | `800` | Uppercase, `letter-spacing: 1px`, clickable to open Artist Page |
| **Track Index Tag** (`#trackIndexTag`) | `Syne` | `0.72rem` | `800` | Uppercase, `letter-spacing: 1.5px` (`TRACK 01 / 21`) |
| **Time Display & Timers** (`#currentTime`, `#totalDuration`) | `Plus Jakarta Sans` | `0.95rem` | `800` | Monospaced Tabular (`font-variant-numeric: tabular-nums`, `"tnum"`) |
| **Playlist Card Name** (`.playlist-card-name`) | `Rubik` | `1.05rem` | `600` | Clean bold playlist row titles |
| **Playlist Item Title** (`.playlist-title`) | `Plus Jakarta Sans` | `0.95rem` | `700` / `800` | Highlighted on active playing track |
| **Drawer Header Header** (`.drawer-header h3`) | `Syne` | `0.85rem` | `800` | Uppercase, `letter-spacing: 1px` |
| **Format Badge** (`#formatBadge`) | `Plus Jakarta Sans` | `0.75rem` | `800` | `FLAC 24-BIT / LOSSLESS` |
| **Control Icons** (`.material-symbols-rounded`) | `Material Symbols Rounded` | `24px` – `56px` | `400`–`800` | Dynamic `active-green` & `active-pink` states |

---

### 3. Artist Screen (`artist.html`, `artist.css`, `artist.js`)

| Element / Selector | Font Family | Size | Weight | Properties & Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Hero Artist Name** (`#artistHeroName`) | `Syne` | `1.8rem` (Phone) / `2.6rem` (Tablet) | `800` | Heavy display title overlaid on hero cover |
| **Hero Subtitle Tag** (`#artistHeroTag`) | `Syne` | `0.72rem` | `800` | `ARTIST // PAXNKOXD`, `letter-spacing: 2px` |
| **Section Header** (`.singles-section-title`) | `Syne` | `0.88rem` | `800` | `ТРЕКИ НА УСТРОЙСТВЕ`, `letter-spacing: 1.5px` |
| **Tile Card Title** (`.single-tile-title`) | `Rubik` | `1.05rem` | `800` | `line-height: 1.1`, `word-break: break-word` |
| **Audio Metadata** (`.single-tile-audio-meta`) | `Plus Jakarta Sans` | `0.52rem` | `600` | `letter-spacing: 0.88px`, positioned above bottom contour |
| **Stats Pill Values** (`#artistStatFans`, `#artistStatAlbums`) | `Plus Jakarta Sans` | `0.88rem` | `800` | Monospaced numeric stats |
| **Stats Pill Labels** (`.artist-stat-label`) | `Rubik` | `0.65rem` | `700` | `ФАНАТЫ`, `АЛЬБОМЫ` |

---

## 🎨 Color System & Dynamic Tokens

### Base Theme Surface Tokens
```css
:root {
  /* Main Screen Tokens */
  --main-bg: #26252a;
  --main-surface-dark: #1e1d22;
  --main-surface-card: #2f2d34;
  --main-card-border: rgba(255, 255, 255, 0.12);
  --main-accent-green: #3CE068;

  /* Player Screen Tokens */
  --m3-expressive-green: #3CE068;
  --m3-expressive-green-glow: rgba(60, 224, 104, 0.45);
  --m3-expressive-pink: #FF9EE2;
  --m3-expressive-pink-glow: rgba(255, 158, 226, 0.45);
  --m3-surface-gray: #4A5057;

  /* Typography Variables */
  --font-disco: 'Disco', sans-serif;
  --font-display: 'Syne', sans-serif;
  --font-body: 'Plus Jakarta Sans', sans-serif;
  --font-rubik: 'Rubik', sans-serif;

  /* Motion Curves */
  --spring-bounce: cubic-bezier(0.34, 1.56, 0.64, 1);
  --ease-expressive: cubic-bezier(0.2, 0.0, 0.0, 1.0);
}
```

---

## 📱 Component Design Specifications

### 1. Main Screen (`main.html`)
- **Header**: Contains clean vector disc logo (`logo2.png`) directly adjacent to custom `Disco` font text `SONAR`, and settings gear button ⚙️.
- **Section Subheader**: Displays dynamic track counter (`ТРЕКИ НА УСТРОЙСТВЕ (21)`), `СОРТИРОВАТЬ` button, and view mode toggle buttons (`grid_view` 🔳 vs `format_list_bulleted` 📑).
- **250ms Grid Configurator**: Long pressing the view mode toggle for 250ms opens a glass popout containing a tile grid slider (`2` to `12` columns in a row).
- **Artist Dividers**: Dual-line headers with a short line on the left, upper-case truncated artist name (max 25 characters), and long line on the right (`— PAXNKOXD ------------------------`).
- **Tile Cards (`.single-tile-card`)**: 1:1 aspect ratio cover box, Rubik title, and metadata badge situated strictly under the title and above the bottom contour.

### 2. Player Screen (`index.html`)
- **250ms Dual-Function Button (Shuffle 🔀 $\leftrightarrow$ Repeat 🔁)**: Long pressing the sub-control button for 250ms seamlessly toggles the active button view between Shuffle and Repeat with a scale pulse animation (`scale(1.12)`).
- **Synchronized Accent Colors**: Activated Repeat state adopts the exact same green accent theme (`.active-green`) as Shuffle.
- **Playlist Drawer**: Cards configured with `8px` padding, `60px × 60px` cover image, and `52px × 78px` stadium heart pill button.

### 3. Artist Screen (`artist.html`)
- **Hero & Deezer Integration**: Displays high-resolution artist backdrop image, Fans/Albums stadium pill bar, and Deezer API external link.
- **2-Column Tile Grid**: Max 4-column responsive grid on tablet landscape viewports (`@media (min-width: 900px)`).

---

## 📐 Multi-Device Viewport Presets

- **Android Phone**: `360×800 px` portrait viewport.
- **iPad Pro 11"**: `1194×834 px` landscape / `834×1194 px` portrait viewport.
- **iPad Mini**: `1024×768 px` landscape viewport.
- **Android Tablet**: `1280×800 px` landscape viewport.
