/* ==========================================================================
   SONAR MUSIC PLAYER - MAIN SCREEN LOGIC (main.js)
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  // DOM Elements
  const phoneFrame = document.getElementById('phoneFrame');
  const phoneScreen = document.getElementById('phoneScreen');
  const deviceSelector = document.getElementById('deviceSelector');
  const toggleOrientationBtn = document.getElementById('toggleOrientationBtn');
  const orientText = document.getElementById('orientText');
  const clockDisplay = document.getElementById('clockDisplay');

  const tracksCountTitle = document.getElementById('tracksCountTitle');
  const tracksListContainer = document.getElementById('tracksListContainer');
  const artistsFilterBtn = document.getElementById('artistsFilterBtn');
  const settingsBtn = document.getElementById('settingsBtn');

  // State
  let currentDevice = 'phone';
  let isLandscape = false;
  let tracksData = [];
  let isSortByArtist = true;

  // --------------------------------------------------------------------------
  // 1. Multi-Device & Orientation Handlers
  // --------------------------------------------------------------------------
  function applyDeviceAndOrientation() {
    phoneFrame.className = 'phone-frame';
    phoneScreen.className = 'phone-screen';

    phoneFrame.classList.add(`device-${currentDevice}`);
    if (currentDevice !== 'phone') {
      phoneScreen.classList.add('tablet-screen');
    }

    if (isLandscape) {
      phoneFrame.classList.add('landscape-frame');
      phoneScreen.classList.add('landscape');
      phoneScreen.classList.remove('portrait');
      if (orientText) orientText.textContent = 'Альбом 🔄';
    } else {
      phoneScreen.classList.add('portrait');
      phoneScreen.classList.remove('landscape');
      if (orientText) orientText.textContent = 'Портрет 📱';
    }
  }

  if (deviceSelector) {
    deviceSelector.addEventListener('change', (e) => {
      currentDevice = e.target.value;
      applyDeviceAndOrientation();
    });
  }

  if (toggleOrientationBtn) {
    toggleOrientationBtn.addEventListener('click', () => {
      isLandscape = !isLandscape;
      applyDeviceAndOrientation();
    });
  }

  // Status Bar Clock
  function updateClock() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    if (clockDisplay) clockDisplay.textContent = `${hours}:${minutes}`;
  }
  updateClock();
  setInterval(updateClock, 10000);

  // View Mode & Grid Columns Configurator State
  let currentViewMode = localStorage.getItem('main_view_mode') || 'list'; // 'list' or 'grid'
  let gridColumns = parseInt(localStorage.getItem('main_grid_columns'), 10) || 4;

  const viewModeToggle = document.getElementById('viewModeToggle');
  const toggleGridViewBtn = document.getElementById('toggleGridViewBtn');
  const toggleListViewBtn = document.getElementById('toggleListViewBtn');
  const gridColumnsPopout = document.getElementById('gridColumnsPopout');
  const gridColumnsRange = document.getElementById('gridColumnsRange');
  const gridColsVal = document.getElementById('gridColsVal');

  let toggleHoldTimer = null;
  let isToggleHoldAction = false;
  const HOLD_DURATION = 250; // Exact 250ms long press threshold

  function applyGridColumns(cols) {
    gridColumns = Math.max(2, Math.min(12, cols));
    localStorage.setItem('main_grid_columns', gridColumns);
    if (phoneScreen) {
      phoneScreen.style.setProperty('--grid-cols', gridColumns);
    }
    if (gridColumnsRange) gridColumnsRange.value = gridColumns;
    if (gridColsVal) gridColsVal.textContent = `${gridColumns} в ряд`;
  }

  applyGridColumns(gridColumns);

  function syncViewModeUI() {
    if (toggleGridViewBtn && toggleListViewBtn) {
      if (currentViewMode === 'grid') {
        toggleGridViewBtn.classList.add('active');
        toggleListViewBtn.classList.remove('active');
      } else {
        toggleListViewBtn.classList.add('active');
        toggleGridViewBtn.classList.remove('active');
      }
    }
  }

  if (viewModeToggle) {
    function startTogglePress() {
      isToggleHoldAction = false;
      clearTimeout(toggleHoldTimer);
      toggleHoldTimer = setTimeout(() => {
        isToggleHoldAction = true;
        if (gridColumnsPopout) {
          gridColumnsPopout.classList.toggle('open');
        }
      }, HOLD_DURATION);
    }

    function endTogglePress() {
      clearTimeout(toggleHoldTimer);
    }

    ['pointerdown', 'mousedown', 'touchstart'].forEach(evt => {
      viewModeToggle.addEventListener(evt, startTogglePress, { passive: true });
    });

    ['pointerup', 'mouseup', 'touchend', 'mouseleave', 'touchcancel'].forEach(evt => {
      viewModeToggle.addEventListener(evt, endTogglePress);
    });
  }

  if (toggleGridViewBtn) {
    toggleGridViewBtn.addEventListener('click', (e) => {
      clearTimeout(toggleHoldTimer);
      if (isToggleHoldAction) {
        e.stopImmediatePropagation();
        return;
      }
      currentViewMode = 'grid';
      localStorage.setItem('main_view_mode', 'grid');
      syncViewModeUI();
      renderTracksList();
    });
  }

  if (toggleListViewBtn) {
    toggleListViewBtn.addEventListener('click', (e) => {
      clearTimeout(toggleHoldTimer);
      if (isToggleHoldAction) {
        e.stopImmediatePropagation();
        return;
      }
      currentViewMode = 'list';
      localStorage.setItem('main_view_mode', 'list');
      syncViewModeUI();
      renderTracksList();
    });
  }

  if (gridColumnsRange) {
    gridColumnsRange.addEventListener('input', (e) => {
      const val = parseInt(e.target.value, 10);
      applyGridColumns(val);
    });
  }

  // Dismiss popout when clicking outside
  document.addEventListener('pointerdown', (e) => {
    if (gridColumnsPopout && gridColumnsPopout.classList.contains('open')) {
      if (!viewModeToggle.contains(e.target) && !gridColumnsPopout.contains(e.target)) {
        gridColumnsPopout.classList.remove('open');
      }
    }
  });

  syncViewModeUI();

  // --------------------------------------------------------------------------
  // 2. Fetch Tracks & Render List Grouped by Artist
  // --------------------------------------------------------------------------
  function renderTracksList() {
    if (!tracksListContainer) return;
    tracksListContainer.innerHTML = '';

    if (tracksData.length === 0) {
      tracksListContainer.innerHTML = '<div class="no-tracks">Треки не найдены</div>';
      return;
    }

    if (isSortByArtist) {
      // Group by Artist
      const groupedByArtist = {};
      tracksData.forEach(track => {
        const artistName = track.artist || 'Unknown Artist';
        if (!groupedByArtist[artistName]) {
          groupedByArtist[artistName] = [];
        }
        groupedByArtist[artistName].push(track);
      });

      Object.keys(groupedByArtist).forEach(artistName => {
        // Truncate artist tag if longer than 25 symbols
        let formattedArtistName = artistName.toUpperCase();
        if (formattedArtistName.length > 25) {
          formattedArtistName = formattedArtistName.substring(0, 25) + '...';
        }

        // Create Artist Divider Header
        const divider = document.createElement('div');
        divider.className = 'artist-group-divider';
        divider.innerHTML = `
          <div class="divider-line short-line"></div>
          <span class="divider-tag" title="${artistName.toUpperCase()}">${formattedArtistName}</span>
          <div class="divider-line long-line"></div>
        `;
        tracksListContainer.appendChild(divider);

        // Group container for tracks
        const groupContainer = document.createElement('div');
        groupContainer.className = 'track-list-group';
        if (currentViewMode === 'grid') {
          groupContainer.classList.add('view-grid');
        }

        groupedByArtist[artistName].forEach(track => {
          const card = currentViewMode === 'grid' ? createTileCard(track) : createTrackCard(track);
          groupContainer.appendChild(card);
        });

        tracksListContainer.appendChild(groupContainer);
      });
    } else {
      // Linear Track List
      const groupContainer = document.createElement('div');
      groupContainer.className = 'track-list-group';
      if (currentViewMode === 'grid') {
        groupContainer.classList.add('view-grid');
      }

      tracksData.forEach(track => {
        const card = currentViewMode === 'grid' ? createTileCard(track) : createTrackCard(track);
        groupContainer.appendChild(card);
      });
      tracksListContainer.appendChild(groupContainer);
    }
  }

  function createTrackCard(track) {
    const card = document.createElement('div');
    card.className = 'main-track-card';
    const audioMetaStr = track.audioMeta || 'FLAC • 24 bit • 1541 kb/s';

    card.innerHTML = `
      <img src="${track.cover || 'cover.jpg'}" alt="${track.title}" class="main-track-cover">
      <div class="main-track-info">
        <h3 class="main-track-title">${track.title}</h3>
        <span class="main-track-artist">${track.artist}</span>
        <span class="main-track-meta">${audioMetaStr}</span>
      </div>
      <button class="main-track-play-btn" title="Воспроизвести">
        <span class="material-symbols-rounded">play_arrow</span>
      </button>
    `;

    card.addEventListener('click', () => {
      // Navigate to player with selected track
      window.location.href = `index.html?track=${encodeURIComponent(track.title)}`;
    });

    return card;
  }

  // Create Tile Card (Identical layout to Artist screen tiles, without dynamic color extraction)
  function createTileCard(track) {
    const tile = document.createElement('div');
    tile.className = 'single-tile-card';
    const audioMetaStr = track.audioMeta || 'FLAC • 24 bit • 1541 kb/s';

    tile.innerHTML = `
      <div class="single-tile-cover-box">
        <img src="${track.cover || 'cover.jpg'}" alt="${track.title}" class="single-tile-img">
      </div>
      <div class="single-tile-info">
        <h3 class="single-tile-title">${track.title}</h3>
        <div class="single-tile-audio-meta">${audioMetaStr}</div>
      </div>
    `;

    tile.addEventListener('click', () => {
      window.location.href = `index.html?track=${encodeURIComponent(track.title)}`;
    });

    return tile;
  }

  // Load Tracks from tracks.json
  fetch('tracks.json')
    .then(res => res.json())
    .then(data => {
      tracksData = data;
      if (tracksCountTitle) {
        tracksCountTitle.textContent = `ТРЕКИ НА УСТРОЙСТВЕ (${tracksData.length})`;
      }
      renderTracksList();
    })
    .catch(err => {
      console.warn('Could not load tracks.json in main screen:', err);
    });

  // Filter / Sort toggle
  if (artistsFilterBtn) {
    artistsFilterBtn.addEventListener('click', () => {
      isSortByArtist = !isSortByArtist;
      artistsFilterBtn.classList.toggle('active', isSortByArtist);
      renderTracksList();
    });
  }

  // Settings button action
  if (settingsBtn) {
    settingsBtn.addEventListener('click', () => {
      alert('Настройки Sonar M3 Player: Высококачественное аудио FLAC Lossless активировано.');
    });
  }
});
