/* ==========================================================================
   SONAR MUSIC PLAYER - ARTIST PAGE LOGIC & DYNAMIC COLOR EXTRACTION
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  // DOM Elements
  const phoneFrame = document.getElementById('phoneFrame');
  const phoneScreen = document.getElementById('phoneScreen');
  const deviceSelector = document.getElementById('deviceSelector');
  const toggleOrientationBtn = document.getElementById('toggleOrientationBtn');
  const orientText = document.getElementById('orientText');
  const artistSelector = document.getElementById('artistSelector');
  const clockDisplay = document.getElementById('clockDisplay');

  const artistHeroImg = document.getElementById('artistHeroImg');
  const artistHeroName = document.getElementById('artistHeroName');
  const artistHeroTag = document.getElementById('artistHeroTag');
  const artistStatFans = document.getElementById('artistStatFans');
  const artistStatAlbums = document.getElementById('artistStatAlbums');
  const deezerLink = document.getElementById('deezerLink');
  const singlesGridContainer = document.getElementById('singlesGridContainer');
  const canvas = document.getElementById('colorCanvas');

  // State
  let currentDevice = 'phone';
  let isLandscape = false;
  let currentArtist = 'PAXNKOXD';

  // Local singles catalog for tiles
  const localSinglesMap = {
    'PAXNKOXD': [
      { title: 'proxy', cover: 'singles/PAXNKOXD/PAXNKOXD - proxy.jpg' },
      { title: 'bitcrushed tears', cover: 'singles/PAXNKOXD/PAXNKOXD - bitcrushed tears.jpg' },
      { title: 'derealization', cover: 'singles/PAXNKOXD/PAXNKOXD - derealization.jpg' },
      { title: 'fly me to the m00n', cover: 'singles/PAXNKOXD/PAXNKOXD - fly me to the m00n.jpg' },
      { title: 'collapse', cover: 'singles/PAXNKOXD/PAXNKOXD - collapse.jpg' },
      { title: 'broken promise', cover: 'singles/PAXNKOXD/PAXNKOXD - broken promise.jpg' },
      { title: 'my love', cover: 'singles/PAXNKOXD/PAXNKOXD - my love.jpg' }
    ],
    '1nonly': [
      { title: 'Split', cover: 'singles/1nonly/1nonly - Split.jpg' },
      { title: 'GRAILED', cover: 'singles/1nonly/1nonly - GRAILED.jpg' },
      { title: 'Meaningless Love', cover: 'singles/1nonly/1nonly - Meaningless Love.jpg' },
      { title: 'Mine', cover: 'singles/1nonly/1nonly - Mine.jpg' },
      { title: 'Stay With Me', cover: 'singles/1nonly/1nonly - Stay With Me.jpg' }
    ]
  };

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
      orientText.textContent = 'Альбом 🔄';
    } else {
      phoneScreen.classList.add('portrait');
      phoneScreen.classList.remove('landscape');
      orientText.textContent = 'Портрет 📱';
    }
  }

  deviceSelector.addEventListener('change', (e) => {
    currentDevice = e.target.value;
    applyDeviceAndOrientation();
  });

  toggleOrientationBtn.addEventListener('click', () => {
    isLandscape = !isLandscape;
    applyDeviceAndOrientation();
  });

  // Status Bar Clock
  function updateClock() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    if (clockDisplay) clockDisplay.textContent = `${hours}:${minutes}`;
  }
  updateClock();
  setInterval(updateClock, 10000);

  // --------------------------------------------------------------------------
  // 2. Dynamic Background Color Extraction from Cover Image (HSL/RGB)
  // --------------------------------------------------------------------------
  function extractArtistCoverColors(imgElement) {
    if (!canvas || !imgElement) return;
    const ctx = canvas.getContext('2d');
    
    const tempImg = new Image();
    // Only set crossOrigin if URL is external http/https
    if (imgElement.src.startsWith('http://') || imgElement.src.startsWith('https://')) {
      tempImg.crossOrigin = 'Anonymous';
    }
    tempImg.src = imgElement.src;

    tempImg.onload = () => {
      try {
        canvas.width = 40;
        canvas.height = 40;
        ctx.drawImage(tempImg, 0, 0, 40, 40);
        const imgData = ctx.getImageData(0, 0, 40, 40).data;

        let colorBins = [];
        for (let i = 0; i < imgData.length; i += 16) {
          const r = imgData[i];
          const g = imgData[i + 1];
          const b = imgData[i + 2];
          const hsl = rgbToHsl(r, g, b);
          
          if (hsl.l > 0.15 && hsl.l < 0.85 && hsl.s > 0.15) {
            colorBins.push({ r, g, b, hsl, score: hsl.s * 1.5 + (1 - Math.abs(hsl.l - 0.5)) });
          }
        }

        if (colorBins.length > 0) {
          colorBins.sort((a, b) => b.score - a.score);
          const primary = colorBins[0];
          
          const bgHex = hslToHex(primary.hsl.h, Math.min(primary.hsl.s, 0.45), 0.82);
          const darkCardHex = hslToHex(primary.hsl.h, Math.min(primary.hsl.s, 0.35), 0.16);
          const deezerBtnHex = hslToHex(primary.hsl.h, Math.max(primary.hsl.s, 0.65), 0.58);

          phoneScreen.style.setProperty('--artist-bg-color', bgHex);
          phoneScreen.style.setProperty('--artist-dark-surface', darkCardHex);
          phoneScreen.style.setProperty('--artist-deezer-btn-bg', deezerBtnHex);
        }
      } catch (err) {
        console.log('Color extraction fallback:', err);
      }
    };

    // If already complete in cache, trigger immediately
    if (tempImg.complete) {
      tempImg.onload();
    }
  }

  function rgbToHsl(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    let h, s, l = (max + min) / 2;

    if (max === min) h = s = 0;
    else {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      switch (max) {
        case r: h = (g - b) / d + (g < b ? 6 : 0); break;
        case g: h = (b - r) / d + 2; break;
        case b: h = (r - g) / d + 4; break;
      }
      h /= 6;
    }
    return { h, s, l };
  }

  function hslToHex(h, s, l) {
    let r, g, b;
    if (s === 0) r = g = b = l;
    else {
      const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
      const p = 2 * l - q;
      r = hueToRgb(p, q, h + 1/3);
      g = hueToRgb(p, q, h);
      b = hueToRgb(p, q, h - 1/3);
    }
    return '#' + [r, g, b].map(x => Math.round(x * 255).toString(16).padStart(2, '0')).join('');
  }

  function hueToRgb(p, q, t) {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1/6) return p + (q - p) * 6 * t;
    if (t < 1/2) return q;
    if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
    return p;
  }

  // --------------------------------------------------------------------------
  // 3. Deezer API Integration
  // --------------------------------------------------------------------------
  async function fetchDeezerArtistInfo(artistNameStr) {
    if (artistNameStr.toLowerCase() === '1nonly') {
      return {
        nb_fan: 39753,
        nb_album: 69,
        link: 'https://www.deezer.com/artist/13768407'
      };
    }

    let data = {
      nb_fan: 67,
      nb_album: 33,
      link: `https://www.deezer.com/search/${encodeURIComponent(artistNameStr)}`
    };

    try {
      const proxyUrl = `https://api.allorigins.win/get?url=${encodeURIComponent(`https://api.deezer.com/search/artist?q=${encodeURIComponent(artistNameStr)}`)}`;
      const res = await fetch(proxyUrl);
      if (res.ok) {
        const json = await res.json();
        const parsed = JSON.parse(json.contents);
        if (parsed.data && parsed.data.length > 0) {
          const match = parsed.data[0];
          data = {
            nb_fan: match.nb_fan || 67,
            nb_album: match.nb_album || 33,
            link: match.link || `https://www.deezer.com/search/${encodeURIComponent(artistNameStr)}`
          };
        }
      }
    } catch (err) {
      console.warn('Deezer API fallback:', err);
    }
    return data;
  }

  // View Mode Switcher (Grid vs List)
  const toggleGridViewBtn = document.getElementById('toggleGridViewBtn');
  const toggleListViewBtn = document.getElementById('toggleListViewBtn');
  let currentViewMode = localStorage.getItem('artist_view_mode') || 'grid';

  function setViewMode(mode) {
    currentViewMode = mode;
    localStorage.setItem('artist_view_mode', mode);

    if (singlesGridContainer) {
      if (mode === 'list') {
        singlesGridContainer.classList.remove('view-grid');
        singlesGridContainer.classList.add('view-list');
      } else {
        singlesGridContainer.classList.remove('view-list');
        singlesGridContainer.classList.add('view-grid');
      }
    }

    if (toggleGridViewBtn && toggleListViewBtn) {
      if (mode === 'list') {
        toggleListViewBtn.classList.add('active');
        toggleGridViewBtn.classList.remove('active');
      } else {
        toggleGridViewBtn.classList.add('active');
        toggleListViewBtn.classList.remove('active');
      }
    }
  }

  if (toggleGridViewBtn) toggleGridViewBtn.addEventListener('click', () => setViewMode('grid'));
  if (toggleListViewBtn) toggleListViewBtn.addEventListener('click', () => setViewMode('list'));

  // Fetch real tracks metadata from tracks.json
  let tracksMetadataMap = {};
  fetch('tracks.json')
    .then(res => res.json())
    .then(data => {
      data.forEach(t => {
        if (t.title) {
          tracksMetadataMap[t.title.toLowerCase().trim()] = t;
        }
      });
      renderArtistPage(currentArtist);
    })
    .catch(err => console.warn('Could not load tracks.json metadata:', err));

  // Extract dynamic colors for individual tile cards
  function extractTileColors(imgElement, tileElement) {
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const tempImg = new Image();
    
    if (imgElement.src.startsWith('http://') || imgElement.src.startsWith('https://')) {
      tempImg.crossOrigin = 'Anonymous';
    }
    
    tempImg.src = imgElement.src;
    tempImg.onload = () => {
      try {
        canvas.width = 40;
        canvas.height = 40;
        ctx.drawImage(tempImg, 0, 0, 40, 40);
        const imageData = ctx.getImageData(0, 0, 40, 40).data;
        
        let rSum = 0, gSum = 0, bSum = 0, count = 0;
        for (let i = 0; i < imageData.length; i += 16) {
          const r = imageData[i], g = imageData[i+1], b = imageData[i+2];
          const hsl = rgbToHsl(r, g, b);
          if (hsl.s > 0.12 && hsl.l > 0.12 && hsl.l < 0.88) {
            rSum += r; gSum += g; bSum += b; count++;
          }
        }
        
        let avgR = count > 0 ? Math.round(rSum / count) : 38;
        let avgG = count > 0 ? Math.round(gSum / count) : 32;
        let avgB = count > 0 ? Math.round(bSum / count) : 42;
        
        const hsl = rgbToHsl(avgR, avgG, avgB);
        const darkTileBg = hslToHex(hsl.h, Math.min(hsl.s, 0.35), 0.15);
        const contourBorder = hslToHex(hsl.h, Math.max(hsl.s, 0.55), 0.48);
        const hoverGlow = hslToHex(hsl.h, Math.max(hsl.s, 0.7), 0.65);
        
        tileElement.style.backgroundColor = darkTileBg;
        tileElement.style.borderColor = `${contourBorder}99`; // Distinct contour border
        tileElement.style.setProperty('--tile-border-hover', hoverGlow);
      } catch (err) {
        console.log('Tile color extraction fallback:', err);
      }
    };
  }

  // --------------------------------------------------------------------------
  // 4. Render Artist View
  // --------------------------------------------------------------------------
  async function renderArtistPage(artistNameStr) {
    currentArtist = artistNameStr;
    if (artistHeroName) artistHeroName.textContent = artistNameStr;

    // Local artist cover from artists/ directory
    const coverPath = `artists/${artistNameStr}.jpg`;
    artistHeroImg.src = coverPath;
    artistHeroImg.onerror = () => {
      artistHeroImg.src = 'cover.jpg';
    };

    // Trigger dynamic color extraction for screen background!
    extractArtistCoverColors(artistHeroImg);

    // Apply active view mode (grid / list)
    setViewMode(currentViewMode);

    // Render Singles Tiles / List Items
    if (singlesGridContainer) {
      singlesGridContainer.innerHTML = '';
      const singles = localSinglesMap[artistNameStr] || [];
      
      singles.forEach(single => {
        const matchedMeta = tracksMetadataMap[single.title.toLowerCase().trim()];
        const audioMetaStr = matchedMeta && matchedMeta.audioMeta 
          ? matchedMeta.audioMeta 
          : 'FLAC • 24 bit • 1600 kb/s • 44.1 kHz';

        const tile = document.createElement('div');
        tile.className = 'single-tile-card';
        tile.innerHTML = `
          <div class="single-tile-cover-box">
            <img src="${single.cover}" alt="${single.title}" class="single-tile-img">
          </div>
          <div class="single-tile-info">
            <h3 class="single-tile-title">${single.title}</h3>
            <div class="single-tile-audio-meta">${audioMetaStr}</div>
          </div>
        `;

        // Extract per-tile dynamic cover colors
        const tileImg = tile.querySelector('.single-tile-img');
        if (tileImg) {
          extractTileColors(tileImg, tile);
        }

        tile.addEventListener('click', () => {
          // Navigate to player with track
          window.location.href = `index.html?track=${encodeURIComponent(single.title)}`;
        });
        singlesGridContainer.appendChild(tile);
      });
    }

    // Fetch Deezer API info
    const info = await fetchDeezerArtistInfo(artistNameStr);
    if (artistStatFans) artistStatFans.textContent = info.nb_fan >= 1000 ? `${(info.nb_fan / 1000).toFixed(1)}k` : info.nb_fan;
    if (artistStatAlbums) artistStatAlbums.textContent = info.nb_album;
    if (deezerLink) deezerLink.href = info.link;
  }

  artistSelector.addEventListener('change', (e) => {
    renderArtistPage(e.target.value);
  });

  // Init
  const urlParams = new URLSearchParams(window.location.search);
  const artistParam = urlParams.get('artist') || 'PAXNKOXD';
  artistSelector.value = artistParam;
  renderArtistPage(artistParam);
});
