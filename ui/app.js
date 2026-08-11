/* ==========================================================================
   MATERIAL 3 EXPRESSIVE MUSIC PLAYER - MULTI-DEVICE & TABLET LOGIC
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
  // DOM Elements
  const audio = document.getElementById('audioPlayer');
  const playPauseBtn = document.getElementById('playPauseBtn');
  const playIcon = document.getElementById('playIcon');
  const progressBar = document.getElementById('progressBar');
  const sliderFill = document.getElementById('sliderFill');
  const sliderThumb = document.getElementById('sliderThumb');
  
  // Timer Display
  const currentTimeEl = document.getElementById('currentTime');
  const totalDurationEl = document.getElementById('totalDuration');
  
  const artWrapper = document.getElementById('artWrapper');
  const albumArt = document.getElementById('albumArt');
  const artistNameEl = document.getElementById('artistName');
  const trackTitleEl = document.getElementById('trackTitle');
  const trackIndexTag = document.getElementById('trackIndexTag');
  
  const heartBtn = document.getElementById('heartBtn');
  const repeatBtn = document.getElementById('repeatBtn');
  const queueBtn = document.getElementById('queueBtn');
  const nextBtn = document.getElementById('nextBtn');
  const prevBtn = document.getElementById('prevBtn');
  
  // Device & Orientation Elements
  const deviceSelector = document.getElementById('deviceSelector');
  const toggleOrientationBtn = document.getElementById('toggleOrientationBtn');
  const orientIcon = document.getElementById('orientIcon');
  const orientText = document.getElementById('orientText');
  const phoneFrame = document.getElementById('phoneFrame');
  const phoneScreen = document.getElementById('phoneScreen');
  const panelTagText = document.getElementById('panelTagText');

  // Navigation Bar Elements
  const navigationPanel = document.getElementById('navigationPanel');
  const toggleNavModeBtn = document.getElementById('toggleNavModeBtn');
  const navModeText = document.getElementById('navModeText');
  const navBackBtn = document.getElementById('navBackBtn');
  const navHomeBtn = document.getElementById('navHomeBtn');
  const navRecentsBtn = document.getElementById('navRecentsBtn');
  const navGestureContent = document.getElementById('navGestureContent');
  
  const clockDisplay = document.getElementById('clockDisplay');
  const toggleThemeBtn = document.getElementById('toggleThemeBtn');
  
  const queueDrawerBackdrop = document.getElementById('queueDrawerBackdrop');
  const closeDrawerBtn = document.getElementById('closeDrawerBtn');
  const playlistContainer = document.getElementById('playlistItemsContainer');

  // State
  let isGestureNavMode = false;
  let isLandscape = false;
  let currentDevice = 'phone';
  let playlist = [];
  let currentTrackIndex = 0;
  let isPlaying = false;
  let isFavorited = false;
  let repeatState = 0; // 0: off, 1: repeat all, 2: repeat one
  let isSeeking = false;
  
  // Extracted Current Theme RGB
  let currentPrimaryRGB = { r: 60, g: 224, b: 104 };

  // --------------------------------------------------------------------------
  // 1. Device Selection & Orientation Toggle
  // --------------------------------------------------------------------------
  function applyDeviceAndOrientation() {
    // Remove all device classes
    phoneFrame.className = 'phone-frame';
    phoneScreen.className = 'phone-screen';

    // Add specific device class
    phoneFrame.classList.add(`device-${currentDevice}`);

    if (currentDevice !== 'phone') {
      phoneScreen.classList.add('tablet-screen');
    }

    if (isLandscape) {
      phoneFrame.classList.add('landscape-frame');
      phoneScreen.classList.add('landscape');
      phoneScreen.classList.remove('portrait');
      orientText.textContent = 'Альбом 🔄';
      if (panelTagText) panelTagText.textContent = `${currentDevice.toUpperCase()} • LANDSCAPE`;
    } else {
      phoneScreen.classList.add('portrait');
      phoneScreen.classList.remove('landscape');
      orientText.textContent = 'Портрет 📱';
      if (panelTagText) panelTagText.textContent = `${currentDevice.toUpperCase()} • PORTRAIT`;
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

  // --------------------------------------------------------------------------
  // 2. Navigation Mode Toggle
  // --------------------------------------------------------------------------
  function setNavMode(useGesture) {
    isGestureNavMode = useGesture;
    if (isGestureNavMode) {
      navigationPanel.classList.add('gesture-mode');
      navModeText.textContent = 'Nav: Gesture Pill (16px)';
      toggleNavModeBtn.classList.add('active');
    } else {
      navigationPanel.classList.remove('gesture-mode');
      navModeText.textContent = 'Nav: 3-Buttons (48px)';
      toggleNavModeBtn.classList.remove('active');
    }
  }

  toggleNavModeBtn.addEventListener('click', () => setNavMode(!isGestureNavMode));
  navGestureContent.addEventListener('click', () => queueDrawerBackdrop.classList.add('open'));

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
  // 3. Playlist Loading & Metadata
  // --------------------------------------------------------------------------
  async function loadPlaylist() {
    try {
      const res = await fetch('tracks.json');
      if (res.ok) playlist = await res.json();
    } catch (err) {
      console.warn('Could not load tracks.json, fallback:', err);
      playlist = [{
        filename: 'proxy - PAXNKOXD.flac',
        url: 'music/proxy - PAXNKOXD.flac',
        title: 'proxy',
        artist: 'PAXNKOXD',
        cover: 'cover.jpg'
      }];
    }

    renderPlaylistDrawer();
    loadTrack(0, false);
  }

  function renderPlaylistDrawer() {
    if (!playlistContainer) return;
    playlistContainer.innerHTML = '';

    const badge = document.getElementById('playlistCountBadge');
    if (badge) badge.textContent = `${playlist.length} ТРЕКОВ`;

    playlist.forEach((track, index) => {
      const item = document.createElement('div');
      item.className = `playlist-item ${index === currentTrackIndex ? 'active' : ''}`;
      item.innerHTML = `
        <div class="playlist-meta">
          <span class="playlist-title">${track.title}</span>
          <span class="playlist-artist">${track.artist}</span>
        </div>
      `;

      item.addEventListener('click', () => {
        loadTrack(index, true);
      });

      playlistContainer.appendChild(item);
    });
  }

  function loadTrack(index, autoPlay = true) {
    if (index < 0 || index >= playlist.length) return;
    currentTrackIndex = index;
    const track = playlist[currentTrackIndex];

    audio.src = track.url;
    artistNameEl.textContent = track.artist;
    
    // Dynamic Font Optimization for Artist Name (> 20 chars)
    if (track.artist && track.artist.length > 20) {
      artistNameEl.classList.add('long-text');
    } else {
      artistNameEl.classList.remove('long-text');
    }

    // Dynamic Font Optimization for Track Title (> 20 chars)
    if (track.title && track.title.length > 20) {
      trackTitleEl.classList.add('long-text');
    } else {
      trackTitleEl.classList.remove('long-text');
    }

    if (track.title && track.title.length > 7) {
      if (track.title.includes(' ')) {
        trackTitleEl.innerHTML = track.title.replace(' ', '<br>');
      } else {
        trackTitleEl.textContent = track.title;
      }
    } else {
      trackTitleEl.textContent = track.title;
    }

    trackIndexTag.textContent = `TRACK ${String(index + 1).padStart(2, '0')} / ${String(playlist.length).padStart(2, '0')}`;
    albumArt.src = track.cover;
    renderPlaylistDrawer();
    syncPlaylistDrawerUI(index);

    if (autoPlay) setPlayState(true);
    else setPlayState(false);
  }

  // --------------------------------------------------------------------------
  // 4. Dynamic Color Extraction
  // --------------------------------------------------------------------------
  albumArt.addEventListener('load', () => extractColorsFromImage(albumArt));
  if (albumArt.complete && albumArt.naturalWidth !== 0) extractColorsFromImage(albumArt);

  function extractColorsFromImage(imgEl) {
    try {
      const sampleCanvas = document.createElement('canvas');
      const sampleCtx = sampleCanvas.getContext('2d');
      sampleCanvas.width = 64;
      sampleCanvas.height = 64;
      
      sampleCtx.drawImage(imgEl, 0, 0, 64, 64);
      const imgData = sampleCtx.getImageData(0, 0, 64, 64).data;

      let colorBins = [];
      for (let i = 0; i < imgData.length; i += 16) {
        const r = imgData[i];
        const g = imgData[i + 1];
        const b = imgData[i + 2];
        const hsl = rgbToHsl(r, g, b);
        
        if (hsl.l > 0.12 && hsl.l < 0.88 && hsl.s > 0.15) {
          colorBins.push({ r, g, b, hsl, score: hsl.s * 1.5 + (1 - Math.abs(hsl.l - 0.5)) });
        }
      }

      if (colorBins.length === 0) return;

      colorBins.sort((a, b) => b.score - a.score);
      const primary = colorBins[0];
      let secondary = colorBins.find(c => Math.abs(c.hsl.h - primary.hsl.h) > 0.15) || colorBins[Math.floor(colorBins.length / 2)];

      currentPrimaryRGB = { r: primary.r, g: primary.g, b: primary.b };

      const primaryHex = rgbToHex(primary.r, primary.g, primary.b);
      const secondaryHex = rgbToHex(secondary.r, secondary.g, secondary.b);
      const darkSurface = hslToHex(primary.hsl.h, Math.min(primary.hsl.s, 0.25), 0.28);
      const glowRGBA = `rgba(${primary.r}, ${primary.g}, ${primary.b}, 0.45)`;

      const root = document.documentElement;
      root.style.setProperty('--m3-expressive-green', primaryHex);
      root.style.setProperty('--m3-expressive-green-glow', glowRGBA);
      root.style.setProperty('--m3-expressive-pink', secondaryHex);
      root.style.setProperty('--m3-expressive-pink-glow', `rgba(${secondary.r}, ${secondary.g}, ${secondary.b}, 0.45)`);
      root.style.setProperty('--m3-surface-gray', darkSurface);

    } catch (e) {
      console.log('Dynamic color extraction deferred:', e);
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

  function rgbToHex(r, g, b) {
    return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');
  }

  function hslToHex(h, s, l) {
    let r, g, b;
    if (s === 0) r = g = b = l;
    else {
      const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
      const p = 2 * l - q;
      r = hue2rgb(p, q, h + 1/3);
      g = hue2rgb(p, q, h);
      b = hue2rgb(p, q, h - 1/3);
    }
    return rgbToHex(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
  }

  function hue2rgb(p, q, t) {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1/6) return p + (q - p) * 6 * t;
    if (t < 1/2) return q;
    if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
    return p;
  }

  // --------------------------------------------------------------------------
  // 5. Audio Progress & Time Scrubbing
  // --------------------------------------------------------------------------
  const sliderContainer = document.getElementById('sliderContainer');
  let wasPlayingBeforeSeek = false;

  function formatTime(seconds) {
    if (isNaN(seconds) || seconds < 0) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  }

  function syncDuration() {
    if (audio.duration && !isNaN(audio.duration)) {
      progressBar.max = audio.duration;
      totalDurationEl.textContent = formatTime(audio.duration);
    }
  }

  audio.addEventListener('loadedmetadata', syncDuration);
  audio.addEventListener('durationchange', syncDuration);

  audio.addEventListener('timeupdate', () => {
    if (!isSeeking && audio.duration && !audio.seeking) {
      const current = audio.currentTime;
      progressBar.value = current;
      updateScrubberUI(current);
    }
  });

  audio.addEventListener('ended', () => {
    if (repeatState === 2) {
      audio.currentTime = 0;
      audio.play();
    } else if (repeatState === 1 || currentTrackIndex < playlist.length - 1) {
      nextTrack();
    } else {
      setPlayState(false);
      audio.currentTime = 0;
      updateScrubberUI(0);
    }
  });

  function updateScrubberUI(value) {
    const duration = audio.duration || parseFloat(progressBar.max) || 1;
    const progressPercent = Math.min(100, Math.max(0, (value / duration) * 100));
    sliderFill.style.width = `${progressPercent}%`;
    sliderThumb.style.left = `${progressPercent}%`;
    currentTimeEl.textContent = formatTime(value);
  }

  function startSeeking() {
    if (!isSeeking) {
      isSeeking = true;
      if (sliderContainer) sliderContainer.classList.add('seeking');
      if (!audio.paused) {
        wasPlayingBeforeSeek = true;
        audio.pause();
      } else {
        wasPlayingBeforeSeek = false;
      }
    }
  }

  function handleSeekInput(e) {
    startSeeking();
    const val = parseFloat(e.target.value);
    if (!isNaN(val) && isFinite(val)) {
      updateScrubberUI(val);
    }
  }

  function finishSeeking() {
    if (!isSeeking) return;
    const val = parseFloat(progressBar.value);
    if (!isNaN(val) && isFinite(val)) {
      audio.currentTime = val;
      updateScrubberUI(val);
    }
    
    if (sliderContainer) sliderContainer.classList.remove('seeking');

    if (wasPlayingBeforeSeek) {
      audio.play().then(() => {
        playIcon.textContent = 'pause';
        artWrapper.classList.add('playing');
      }).catch(err => console.log('Resume playback after seek:', err));
    }

    setTimeout(() => {
      isSeeking = false;
    }, 250);
  }

  ['pointerdown', 'mousedown', 'touchstart'].forEach(evt => {
    progressBar.addEventListener(evt, startSeeking, { passive: true });
  });

  progressBar.addEventListener('input', handleSeekInput);
  progressBar.addEventListener('change', finishSeeking);

  ['pointerup', 'mouseup', 'touchend'].forEach(evt => {
    progressBar.addEventListener(evt, finishSeeking);
  });

  audio.addEventListener('seeked', () => {
    setTimeout(() => { isSeeking = false; }, 150);
  });

  // --------------------------------------------------------------------------
  // 6. Playback & Button Handlers
  // --------------------------------------------------------------------------
  function setPlayState(play) {
    isPlaying = play;
    if (isPlaying) {
      audio.play().then(() => {
        playIcon.textContent = 'pause';
        artWrapper.classList.add('playing');
        playPauseBtn.classList.add('playing');
        playPauseBtn.classList.remove('paused');
      }).catch((err) => {
        console.error('Playback error:', err);
        isPlaying = false;
        playIcon.textContent = 'play_arrow';
        artWrapper.classList.remove('playing');
        playPauseBtn.classList.remove('playing');
        playPauseBtn.classList.add('paused');
      });
    } else {
      audio.pause();
      playIcon.textContent = 'play_arrow';
      artWrapper.classList.remove('playing');
      playPauseBtn.classList.remove('playing');
      playPauseBtn.classList.add('paused');
    }
  }

  playPauseBtn.addEventListener('click', () => setPlayState(!isPlaying));

  let isShuffle = false;
  const shuffleBtn = document.getElementById('shuffleBtn');
  const repeatPopoutContainer = document.getElementById('repeatPopoutContainer');

  function nextTrack() {
    if (!playlist || playlist.length === 0) return;
    let nextIdx;
    if (isShuffle && playlist.length > 1 && repeatState !== 2) {
      do {
        nextIdx = Math.floor(Math.random() * playlist.length);
      } while (nextIdx === currentTrackIndex);
    } else {
      nextIdx = (currentTrackIndex + 1) % playlist.length;
    }
    loadTrack(nextIdx, true);
  }

  function prevTrack() {
    if (audio.currentTime > 3) {
      audio.currentTime = 0;
    } else {
      const prevIdx = (currentTrackIndex - 1 + playlist.length) % playlist.length;
      loadTrack(prevIdx, true);
    }
  }

  nextBtn.addEventListener('click', nextTrack);
  prevBtn.addEventListener('click', prevTrack);

  // Popup Playlist Sheet Handlers
  if (queueBtn) {
    queueBtn.addEventListener('click', () => queueDrawerBackdrop.classList.add('open'));
  }
  if (closeDrawerBtn) {
    closeDrawerBtn.addEventListener('click', () => queueDrawerBackdrop.classList.remove('open'));
  }
  if (queueDrawerBackdrop) {
    queueDrawerBackdrop.addEventListener('click', (e) => {
      if (e.target === queueDrawerBackdrop) queueDrawerBackdrop.classList.remove('open');
    });
  }

  // Android Navigation Actions
  navBackBtn.addEventListener('click', () => {
    if (queueDrawerBackdrop && queueDrawerBackdrop.classList.contains('open')) {
      queueDrawerBackdrop.classList.remove('open');
    } else {
      audio.currentTime = 0;
      updateScrubberUI(0);
    }
  });

  navHomeBtn.addEventListener('click', () => {
    if (queueDrawerBackdrop) queueDrawerBackdrop.classList.remove('open');
  });

  navRecentsBtn.addEventListener('click', () => {
    if (queueDrawerBackdrop) queueDrawerBackdrop.classList.toggle('open');
  });

  // --------------------------------------------------------------------------
  // Add to Playlist Selection Drawer & Heart Pill Logic
  // --------------------------------------------------------------------------
  const playlistDrawerBackdrop = document.getElementById('playlistDrawerBackdrop');
  const closePlaylistDrawerBtn = document.getElementById('closePlaylistDrawerBtn');
  const playlistCardRows = document.querySelectorAll('.playlist-card-row');
  
  // Track playlist membership map: { trackIndex: { vibe: bool, mice: bool, fav: bool } }
  const trackPlaylistMap = {};

  function getTrackPlaylists(idx) {
    if (!trackPlaylistMap[idx]) {
      trackPlaylistMap[idx] = { vibe: false, mice: false, fav: false };
    }
    return trackPlaylistMap[idx];
  }

  function syncPlaylistDrawerUI(idx) {
    const states = getTrackPlaylists(idx);
    let isAnyAdded = false;

    playlistCardRows.forEach(row => {
      const pId = row.getAttribute('data-playlist-id');
      if (pId && states[pId]) {
        row.classList.add('added');
        isAnyAdded = true;
      } else {
        row.classList.remove('added');
      }
    });

    if (heartBtn) {
      heartBtn.classList.toggle('active', isAnyAdded);
    }
  }

  // Open Playlist Selection Drawer on Heart Click
  if (heartBtn) {
    heartBtn.addEventListener('click', () => {
      syncPlaylistDrawerUI(currentTrackIndex);
      if (playlistDrawerBackdrop) playlistDrawerBackdrop.classList.add('open');
    });
  }

  if (closePlaylistDrawerBtn) {
    closePlaylistDrawerBtn.addEventListener('click', () => {
      if (playlistDrawerBackdrop) playlistDrawerBackdrop.classList.remove('open');
    });
  }

  if (playlistDrawerBackdrop) {
    playlistDrawerBackdrop.addEventListener('click', (e) => {
      if (e.target === playlistDrawerBackdrop) {
        playlistDrawerBackdrop.classList.remove('open');
      }
    });
  }

  // Playlist Card Row Click Toggle
  playlistCardRows.forEach(row => {
    row.addEventListener('click', () => {
      const pId = row.getAttribute('data-playlist-id');
      if (!pId) return;
      const states = getTrackPlaylists(currentTrackIndex);
      states[pId] = !states[pId];
      syncPlaylistDrawerUI(currentTrackIndex);
    });
  });

  // --------------------------------------------------------------------------
  // Sleep Timer Feature Logic
  // --------------------------------------------------------------------------
  const timeDisplay = document.getElementById('timeDisplay');
  const sleepTimerDrawerBackdrop = document.getElementById('sleepTimerDrawerBackdrop');
  const closeSleepTimerBtn = document.getElementById('closeSleepTimerBtn');
  const sleepActiveBanner = document.getElementById('sleepActiveBanner');
  const sleepCountdownText = document.getElementById('sleepCountdownText');
  const cancelSleepTimerBtn = document.getElementById('cancelSleepTimerBtn');
  const sleepOptionBtns = document.querySelectorAll('.sleep-option-btn[data-minutes]');
  const customSleepBtn = document.getElementById('customSleepBtn');
  const sleepCustomGroup = document.getElementById('sleepCustomGroup');
  const customMinutesInput = document.getElementById('customMinutesInput');
  const submitCustomSleepBtn = document.getElementById('submitCustomSleepBtn');
  const sleepIndicatorIcon = document.getElementById('sleepIndicatorIcon');

  let sleepTimerTimeout = null;
  let sleepTimerInterval = null;
  let sleepTimerEndTime = null;

  function updateSleepTimerUI() {
    if (sleepTimerEndTime) {
      const remainingMs = Math.max(0, sleepTimerEndTime - Date.now());
      const remainingSec = Math.ceil(remainingMs / 1000);
      const mins = Math.floor(remainingSec / 60);
      const secs = remainingSec % 60;
      
      if (sleepCountdownText) {
        sleepCountdownText.textContent = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
      }
      if (sleepActiveBanner) sleepActiveBanner.style.display = 'flex';
      if (sleepIndicatorIcon) sleepIndicatorIcon.style.display = 'inline-block';
    } else {
      if (sleepActiveBanner) sleepActiveBanner.style.display = 'none';
      if (sleepIndicatorIcon) sleepIndicatorIcon.style.display = 'none';
    }
  }

  function startSleepTimer(minutes) {
    clearSleepTimer();
    const durationMs = minutes * 60 * 1000;
    sleepTimerEndTime = Date.now() + durationMs;

    sleepTimerTimeout = setTimeout(() => {
      setPlayState(false);
      clearSleepTimer();
    }, durationMs);

    sleepTimerInterval = setInterval(() => {
      if (Date.now() >= sleepTimerEndTime) {
        clearSleepTimer();
      } else {
        updateSleepTimerUI();
      }
    }, 1000);

    updateSleepTimerUI();
    if (sleepTimerDrawerBackdrop) sleepTimerDrawerBackdrop.classList.remove('open');
  }

  function clearSleepTimer() {
    if (sleepTimerTimeout) clearTimeout(sleepTimerTimeout);
    if (sleepTimerInterval) clearInterval(sleepTimerInterval);
    sleepTimerTimeout = null;
    sleepTimerInterval = null;
    sleepTimerEndTime = null;
    if (sleepCustomGroup) sleepCustomGroup.style.display = 'none';
    updateSleepTimerUI();
  }

  if (timeDisplay) {
    timeDisplay.addEventListener('click', () => {
      updateSleepTimerUI();
      if (sleepTimerDrawerBackdrop) sleepTimerDrawerBackdrop.classList.add('open');
    });
  }

  if (closeSleepTimerBtn) {
    closeSleepTimerBtn.addEventListener('click', () => {
      if (sleepTimerDrawerBackdrop) sleepTimerDrawerBackdrop.classList.remove('open');
    });
  }

  if (sleepTimerDrawerBackdrop) {
    sleepTimerDrawerBackdrop.addEventListener('click', (e) => {
      if (e.target === sleepTimerDrawerBackdrop) {
        sleepTimerDrawerBackdrop.classList.remove('open');
      }
    });
  }

  sleepOptionBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const mins = parseInt(btn.getAttribute('data-minutes'), 10);
      if (!isNaN(mins)) startSleepTimer(mins);
    });
  });

  if (customSleepBtn && sleepCustomGroup) {
    customSleepBtn.addEventListener('click', () => {
      sleepCustomGroup.style.display = sleepCustomGroup.style.display === 'none' ? 'flex' : 'none';
      if (customMinutesInput) customMinutesInput.focus();
    });
  }

  if (submitCustomSleepBtn && customMinutesInput) {
    submitCustomSleepBtn.addEventListener('click', () => {
      const mins = parseInt(customMinutesInput.value, 10);
      if (!isNaN(mins) && mins > 0) {
        startSleepTimer(mins);
        customMinutesInput.value = '';
      }
    });
  }

  if (cancelSleepTimerBtn) {
    cancelSleepTimerBtn.addEventListener('click', () => {
      clearSleepTimer();
    });
  }

  // --------------------------------------------------------------------------
  // Dual-Function 250ms Hold Swap Mode: Shuffle <-> Repeat
  // --------------------------------------------------------------------------
  const repeatPillContainer = document.getElementById('repeatPillContainer');
  let activeSubMode = localStorage.getItem('player_sub_mode') || 'shuffle'; // 'shuffle' or 'repeat'
  let holdTimer = null;
  let isHoldAction = false;
  const HOLD_DURATION_MS = 250; // Exact 250ms threshold

  function updateSubControlUI() {
    if (activeSubMode === 'repeat') {
      if (shuffleBtn) shuffleBtn.classList.add('hidden-mode');
      if (repeatBtn) repeatBtn.classList.remove('hidden-mode');
    } else {
      if (repeatBtn) repeatBtn.classList.add('hidden-mode');
      if (shuffleBtn) shuffleBtn.classList.remove('hidden-mode');
    }

    if (shuffleBtn) {
      shuffleBtn.classList.remove('active-green', 'active-pink');
      if (isShuffle) shuffleBtn.classList.add('active-green');
    }

    if (repeatBtn) {
      repeatBtn.classList.remove('active-green', 'active-pink');
      if (repeatState !== 0) repeatBtn.classList.add('active-green');
      const repeatIcon = repeatBtn.querySelector('.material-symbols-rounded');
      if (repeatIcon) {
        repeatIcon.textContent = repeatState === 2 ? 'repeat_one' : 'repeat';
      }
    }
  }

  function swapSubControlMode() {
    activeSubMode = activeSubMode === 'shuffle' ? 'repeat' : 'shuffle';
    localStorage.setItem('player_sub_mode', activeSubMode);
    
    // Add quick scale pulse animation to pill container
    if (repeatPillContainer) {
      repeatPillContainer.style.transform = 'scale(1.12)';
      setTimeout(() => {
        repeatPillContainer.style.transform = '';
      }, 200);
    }
    
    updateSubControlUI();
  }

  function attachHoldSwapListeners(btnElement) {
    if (!btnElement) return;

    function handleHoldStart() {
      isHoldAction = false;
      clearTimeout(holdTimer);
      holdTimer = setTimeout(() => {
        isHoldAction = true;
        swapSubControlMode();
      }, HOLD_DURATION_MS);
    }

    function handleHoldEnd() {
      clearTimeout(holdTimer);
    }

    ['pointerdown', 'mousedown', 'touchstart'].forEach(evt => {
      btnElement.addEventListener(evt, handleHoldStart, { passive: true });
    });

    ['pointerup', 'mouseup', 'touchend', 'pointercancel', 'mouseleave'].forEach(evt => {
      btnElement.addEventListener(evt, handleHoldEnd);
    });
  }

  if (shuffleBtn) {
    attachHoldSwapListeners(shuffleBtn);
    shuffleBtn.addEventListener('click', (e) => {
      clearTimeout(holdTimer);
      if (isHoldAction) {
        e.stopImmediatePropagation();
        return;
      }
      isShuffle = !isShuffle;
      updateSubControlUI();
    });
  }

  if (repeatBtn) {
    attachHoldSwapListeners(repeatBtn);
    repeatBtn.addEventListener('click', (e) => {
      clearTimeout(holdTimer);
      if (isHoldAction) {
        e.stopImmediatePropagation();
        return;
      }
      repeatState = (repeatState + 1) % 3;
      updateSubControlUI();
    });
  }

  // Initial sync
  updateSubControlUI();

  toggleThemeBtn.addEventListener('click', () => {
    const bgs = ['#121418', '#0A120D', '#1A0C14', '#0D111A'];
    const current = document.body.style.backgroundColor;
    const nextIdx = (bgs.indexOf(current) + 1) % bgs.length;
    document.body.style.backgroundColor = bgs[nextIdx];
  });

  // --------------------------------------------------------------------------
  // 8. Artist Page Logic (Deezer API & Singles 2-Column Grid)
  // --------------------------------------------------------------------------
  const artistPageBackdrop = document.getElementById('artistPageBackdrop');
  const artistBackBtn = document.getElementById('artistBackBtn');
  const artistHeroImg = document.getElementById('artistHeroImg');
  const artistHeroName = document.getElementById('artistHeroName');
  const artistHeroTag = document.getElementById('artistHeroTag');
  const artistStatFans = document.getElementById('artistStatFans');
  const artistStatAlbums = document.getElementById('artistStatAlbums');
  const deezerLink = document.getElementById('deezerLink');
  const singlesGridContainer = document.getElementById('singlesGridContainer');

  // Local singles catalog for artists
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

  async function fetchDeezerArtist(artistNameStr) {
    let artistData = {
      name: artistNameStr,
      picture: null,
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
          artistData = {
            name: match.name || artistNameStr,
            picture: match.picture_xl || match.picture_big || match.picture,
            nb_fan: match.nb_fan || 67,
            nb_album: match.nb_album || 33,
            link: match.link || `https://www.deezer.com/search/${encodeURIComponent(artistNameStr)}`
          };
        }
      }
    } catch (err) {
      console.warn('Deezer API fetch active fallback:', err);
    }
    return artistData;
  }

  async function openArtistPage(artistNameStr) {
    if (!artistNameStr) return;

    if (artistHeroName) artistHeroName.textContent = artistNameStr;
    if (artistHeroTag) artistHeroTag.textContent = `ARTIST // ${artistNameStr.toUpperCase()}`;

    const currentTrack = playlist[currentTrackIndex];
    if (currentTrack && artistHeroImg) {
      artistHeroImg.src = currentTrack.cover || 'cover.jpg';
    }

    if (artistPageBackdrop) artistPageBackdrop.classList.add('open');

    // Populate Singles 2-Column Grid
    if (singlesGridContainer) {
      singlesGridContainer.innerHTML = '';
      
      // Match exact artist or check catalog
      let singlesList = localSinglesMap[artistNameStr];
      if (!singlesList) {
        const matchKey = Object.keys(localSinglesMap).find(k => k.toLowerCase() === artistNameStr.toLowerCase());
        if (matchKey) singlesList = localSinglesMap[matchKey];
      }

      if (!singlesList || singlesList.length === 0) {
        const artistTracks = playlist.filter(t => t.artist.toLowerCase().includes(artistNameStr.toLowerCase()));
        singlesList = artistTracks.map(t => ({ title: t.title, cover: t.cover }));
      }

      if (singlesList.length === 0 && currentTrack) {
        singlesList = [{ title: currentTrack.title, cover: currentTrack.cover }];
      }

      singlesList.forEach(single => {
        const tile = document.createElement('div');
        tile.className = 'single-tile-card';
        tile.innerHTML = `
          <img src="${single.cover}" alt="${single.title}" class="single-tile-img">
          <span class="single-tile-title">${single.title}</span>
        `;
        tile.addEventListener('click', () => {
          const trackIdx = playlist.findIndex(t => t.title.toLowerCase() === single.title.toLowerCase());
          if (trackIdx !== -1) {
            loadTrack(trackIdx, true);
          }
        });
        singlesGridContainer.appendChild(tile);
      });
    }

    // Fetch Deezer API metadata asynchronously
    const deezerInfo = await fetchDeezerArtist(artistNameStr);
    if (artistHeroImg && deezerInfo.picture) artistHeroImg.src = deezerInfo.picture;
    if (artistStatFans) artistStatFans.textContent = deezerInfo.nb_fan >= 1000 ? `${(deezerInfo.nb_fan / 1000).toFixed(1)}k` : deezerInfo.nb_fan;
    if (artistStatAlbums) artistStatAlbums.textContent = deezerInfo.nb_album;
    if (deezerLink) deezerLink.href = deezerInfo.link;
  }

  // Click artist name in player header to open standalone Artist Page (artist.html)
  if (artistNameEl) {
    artistNameEl.addEventListener('click', () => {
      const currentTrack = playlist[currentTrackIndex];
      const artistNameStr = currentTrack ? currentTrack.artist : artistNameEl.textContent;
      window.location.href = `artist.html?artist=${encodeURIComponent(artistNameStr)}`;
    });
  }

  // Init & check URL param for track auto-play
  loadPlaylist().then(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const trackParam = urlParams.get('track');
    if (trackParam && playlist.length > 0) {
      const matchIdx = playlist.findIndex(t => t.title.toLowerCase().includes(trackParam.toLowerCase()));
      if (matchIdx !== -1) {
        loadTrack(matchIdx, true);
      }
    }
  });
});
