package com.sonar.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sonar.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

data class ArtworkPalette(
    val primary: Color = SonarGreen,
    val secondary: Color = SonarPink,
    val darkSurface: Color = Color(0xFF1E2228),
    val buttonSurface: Color = Color(0xFF16191E),
    val heartSurface: Color = Color(0xFF252936),
    val lightBackground: Color = Color(0xFFB4EAEE),
    val deezer: Color = SonarCyan,
)

data class LoadedArtwork(
    val bitmap: Bitmap?,
    val palette: ArtworkPalette,
    val paletteReady: Boolean = false,
)

private object ArtworkBitmapCache {
    private const val maxEntries = 32
    private val cache = object : LinkedHashMap<String, Bitmap>(maxEntries, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > maxEntries
    }
    private val palettes = LinkedHashMap<String, ArtworkPalette>(maxEntries, .75f, true)

    @Synchronized
    fun get(key: String): Bitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache[key] = bitmap
    }

    @Synchronized
    fun getPalette(key: String): ArtworkPalette? = palettes[key]

    @Synchronized
    fun putPalette(key: String, palette: ArtworkPalette) {
        palettes[key] = palette
    }
}

suspend fun loadTrackArtwork(context: Context, track: Track?, extractPalette: Boolean = true): LoadedArtwork = withContext(Dispatchers.IO) {
    val key = track?.id
    val cachedBitmap = key?.let(ArtworkBitmapCache::get)
    val bitmap = cachedBitmap ?: track?.let { currentTrack ->
        val fromPath = currentTrack.artworkPath?.let(BitmapFactory::decodeFile)
        if (fromPath != null) return@let fromPath

        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(currentTrack.uri))
                retriever.embeddedPicture?.let { pictureBytes ->
                    BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }
    if (bitmap != null && key != null && cachedBitmap == null) ArtworkBitmapCache.put(key, bitmap)
    val cachedPalette = if (extractPalette) key?.let(ArtworkBitmapCache::getPalette) else null
    val palette = if (extractPalette && bitmap != null) {
        cachedPalette ?: extractArtworkPalette(bitmap)?.also { extracted ->
            key?.let { ArtworkBitmapCache.putPalette(it, extracted) }
        }
    } else {
        null
    }
    LoadedArtwork(bitmap, palette ?: ArtworkPalette(), paletteReady = palette != null)
}

suspend fun loadAssetArtwork(context: Context, assetPath: String): LoadedArtwork = withContext(Dispatchers.IO) {
    val bitmap = runCatching { context.assets.open(assetPath).use(BitmapFactory::decodeStream) }.getOrNull()
    val palette = bitmap?.let(::extractArtworkPalette)
    LoadedArtwork(bitmap, palette ?: ArtworkPalette(), paletteReady = palette != null)
}

@Composable
fun rememberTrackArtwork(track: Track?, extractPalette: Boolean = true): LoadedArtwork {
    val context = LocalContext.current
    var artwork by remember { mutableStateOf(LoadedArtwork(null, ArtworkPalette())) }
    LaunchedEffect(track?.id, extractPalette) {
        val loaded = loadTrackArtwork(context, track, extractPalette)
        if (loaded.bitmap != null) {
            artwork = loaded.copy(
                palette = if (loaded.paletteReady) loaded.palette else artwork.palette,
                paletteReady = loaded.paletteReady || artwork.paletteReady,
            )
        }
    }
    return artwork
}

@Composable
fun rememberAssetArtwork(path: String): LoadedArtwork {
    val context = LocalContext.current
    var artwork by remember { mutableStateOf(LoadedArtwork(null, ArtworkPalette())) }
    LaunchedEffect(path) {
        val loaded = loadAssetArtwork(context, path)
        if (loaded.bitmap != null) artwork = loaded
    }
    return artwork
}

fun extractArtworkPalette(bitmap: Bitmap): ArtworkPalette? {
    val sample = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    val candidates = pixels.filterIndexed { index, _ -> index % 4 == 0 }.mapNotNull { pixel: Int ->
        val rgb = intArrayOf(
            android.graphics.Color.red(pixel),
            android.graphics.Color.green(pixel),
            android.graphics.Color.blue(pixel),
        )
        val hsl = rgbToHsl(rgb[0], rgb[1], rgb[2])
        if (hsl[2] <= 0.12f || hsl[2] >= 0.88f || hsl[1] <= 0.15f) return@mapNotNull null
        Triple(rgb, hsl, hsl[1] * 1.5f + (1f - kotlin.math.abs(hsl[2] - 0.5f)))
    }.sortedByDescending { it.third }
    if (candidates.isEmpty()) return null
    val primary = candidates.first()
    val secondary = candidates.firstOrNull { kotlin.math.abs(it.second[0] - primary.second[0]) > 0.15f }
        ?: candidates[candidates.size / 2]
    val primaryColor = Color(android.graphics.Color.rgb(primary.first[0], primary.first[1], primary.first[2]))
    val secondaryColor = Color(android.graphics.Color.rgb(secondary.first[0], secondary.first[1], secondary.first[2]))
    val darkSurfaceRgb = hslToRgb(primary.second[0], primary.second[1].coerceAtMost(.25f), .28f)
    return ArtworkPalette(
        primary = primaryColor,
        secondary = secondaryColor,
        darkSurface = Color(android.graphics.Color.rgb(darkSurfaceRgb[0], darkSurfaceRgb[1], darkSurfaceRgb[2])),
        lightBackground = Color.hsl(primary.second[0] * 360f, primary.second[1].coerceAtMost(.45f), .82f),
        deezer = Color.hsl(primary.second[0] * 360f, primary.second[1].coerceAtLeast(.65f), .58f),
    )
}

private fun rgbToHsl(red: Int, green: Int, blue: Int): FloatArray {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, lightness)
    val delta = max - min
    val saturation = if (lightness > .5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        r -> (g - b) / delta + if (g < b) 6f else 0f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } / 6f
    return floatArrayOf(hue, saturation, lightness)
}

private fun hslToRgb(hue: Float, saturation: Float, lightness: Float): IntArray {
    if (saturation == 0f) {
        val gray = kotlin.math.round(lightness * 255f).toInt()
        return intArrayOf(gray, gray, gray)
    }
    val q = if (lightness < .5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
    val p = 2f * lightness - q
    return intArrayOf(
        kotlin.math.round(hueToRgb(p, q, hue + 1f / 3f) * 255f).toInt(),
        kotlin.math.round(hueToRgb(p, q, hue) * 255f).toInt(),
        kotlin.math.round(hueToRgb(p, q, hue - 1f / 3f) * 255f).toInt(),
    )
}

private fun hueToRgb(p: Float, q: Float, rawT: Float): Float {
    var t = rawT
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}
