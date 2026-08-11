# Technical Architecture & Automated Pipeline Documentation

## ⚙️ System Overview

This document describes the underlying technical architecture of the **Sonar Music Player**, including FLAC audio metadata parsing, multi-screen SPA routing, 250ms long-press gesture handling, audio scrubbing synchronization, and multi-device frame emulation.

---

## 🏗️ Multi-Screen Architecture & Navigation

The application consists of three decoupled HTML screens sharing common CSS variables, font definitions, and audio state parameters:

1. **Main Screen (`main.html`, `main.css`, `main.js`)**:
   - Primary track catalog view with List and Grid layout options.
   - Dynamic track grouping by Artist with 25-character tag truncation.
   - Interactive 250ms long-press grid column popout slider (`2` to `12` columns in a row).
   - Instant track selection routing (`index.html?track=...`).

2. **Player Screen (`index.html`, `styles.css`, `app.js`)**:
   - Full audio playback engine with HTML5 Audio API.
   - Dual-function 250ms hold swap button between `Shuffle` 🔀 and `Repeat` 🔁.
   - Dynamic real-time HSL palette extraction from album covers via HTML5 `<canvas>`.
   - Popup playlist drawer and Add-to-Playlist sheet modal.

3. **Artist Screen (`artist.html`, `artist.css`, `artist.js`)**:
   - Dynamic artist hero page with real-time Deezer API integration (`https://api.deezer.com/search/artist`).
   - Standalone cover color background extraction.
   - Local singles grid catalog (`singles/PAXNKOXD/`, `singles/1nonly/`).

---

## 🎵 FLAC Metadata & Embedded Cover Extractor (`scan_music.py`)

The python script `scan_music.py` parses native FLAC files without external binary dependencies:

1. **Vorbis Comment Block (Block Type 4)**: Extracts UTF-8 encoded `TITLE` and `ARTIST` metadata tags.
2. **Picture Block (Block Type 6)**: Extracts raw PNG/JPEG binary cover data and saves it to `music/covers/`.
3. **JSON Manifest Generation**: Generates `tracks.json` with file paths, titles, artists, covers, and audio quality tags (`FLAC • 24 bit • 1541 kb/s`).

### Automatic Directory Watcher (`--watch` mode)
```bash
python scan_music.py --watch
```
- Monitors `music/` file modification timestamps (`mtime_sum`).
- Automatically re-scans metadata and updates `tracks.json` in real time whenever audio files are added, removed, or edited.

---

## 👆 250ms Long-Press Gesture Engine

Both the Player control bar (`#shuffleBtn` / `#repeatBtn`) and the Main Screen view mode toggle (`#viewModeToggle`) utilize a unified pointer hold timer:

```javascript
let holdTimer = null;
let isHoldAction = false;
const HOLD_DURATION_MS = 250;

function handleHoldStart() {
  isHoldAction = false;
  clearTimeout(holdTimer);
  holdTimer = setTimeout(() => {
    isHoldAction = true;
    // Execute long-press action (e.g. swap button mode or open grid popout)
    triggerHoldAction();
  }, HOLD_DURATION_MS);
}

function handleHoldEnd() {
  clearTimeout(holdTimer);
}

['pointerdown', 'mousedown', 'touchstart'].forEach(evt => {
  element.addEventListener(evt, handleHoldStart, { passive: true });
});

['pointerup', 'mouseup', 'touchend', 'mouseleave', 'touchcancel'].forEach(evt => {
  element.addEventListener(evt, handleHoldEnd);
});
```

---

## ⏯️ Audio Seeking & Buffer Sync Architecture

To prevent thumb jumping and flickering during audio scrubbing:

1. **Pause on Touch/Pointer Down**: Pauses playback and flags `isSeeking = true`.
2. **Instant UI Scrubbing Update**: Updates `sliderFill.style.width` and `sliderThumb.style.left` directly during `input` events without touching `audio.currentTime`.
3. **Buffering Lock Grace Period**: Holds `isSeeking = true` for `250ms` after seek release so asynchronous HTML5 audio buffering `timeupdate` events reporting `0` or old timestamps do not override the thumb position.

---

## 📱 Device Emulation & Navigation Switcher

- **Desktop Toolbar**: Integrated across all 3 pages (`main.html`, `index.html`, `artist.html`) with device selection dropdown, orientation toggle, and seamless screen switcher buttons.
- **3-Button Nav Bar (`48px`)**: Renders Android system back, home, and recents buttons.
- **Gesture Bar (`16px`)**: Renders thin gesture pill handle (`108px × 4px`).
- **Dynamic Device Frames**: Supports `Android Phone`, `iPad Pro 11"`, `iPad Mini`, and `Android Tablet` in both Portrait and Landscape viewports with automatic font scaling.
