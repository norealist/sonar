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
