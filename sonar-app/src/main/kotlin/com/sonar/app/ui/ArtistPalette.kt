package com.sonar.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

data class ArtistPalette(
    val background: Color = Color(0xFFB4EAEE),
    val darkSurface: Color = Color(0xFF221C28),
    val deezer: Color = Color(0xFF35C5F0),
)

data class ArtistArtwork(
    val bitmap: Bitmap?,
    val palette: ArtistPalette,
)

data class ArtistTilePalette(
    val surface: Color,
    val border: Color,
)

@Composable
fun rememberArtistArtwork(
    assetPath: String,
    fallbackTrack: Track? = null,
    cachedFilePath: String? = null,
): ArtistArtwork {
    val context = LocalContext.current
    var artwork by remember { mutableStateOf(ArtistArtwork(null, ArtistPalette())) }
    LaunchedEffect(assetPath, fallbackTrack?.id, cachedFilePath) {
        artwork = ArtistArtwork(null, ArtistPalette())
        artwork = cachedFilePath?.let { loadCachedArtistArtwork(it) }
            ?.takeIf { it.bitmap != null }
            ?: loadArtistArtwork(context, assetPath)
            .takeIf { it.bitmap != null }
            ?: loadFallbackArtistArtwork(context, fallbackTrack)
    }
    return artwork
}

private suspend fun loadArtistArtwork(context: Context, assetPath: String): ArtistArtwork = withContext(Dispatchers.IO) {
    val bitmap = runCatching { context.assets.open(assetPath).use(BitmapFactory::decodeStream) }.getOrNull()
    ArtistArtwork(bitmap, bitmap?.let(::extractArtistPalette) ?: ArtistPalette())
}

private suspend fun loadCachedArtistArtwork(path: String): ArtistArtwork = withContext(Dispatchers.IO) {
    val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    ArtistArtwork(bitmap, bitmap?.let(::extractArtistPalette) ?: ArtistPalette())
}

private suspend fun loadFallbackArtistArtwork(context: Context, track: Track?): ArtistArtwork =
    withContext(Dispatchers.IO) {
        val bitmap = track?.let { loadTrackArtwork(context, it, extractPalette = false).bitmap }
        ArtistArtwork(bitmap, bitmap?.let(::extractArtistPalette) ?: ArtistPalette())
    }

fun extractArtistTilePalette(bitmap: Bitmap): ArtistTilePalette {
    val sample = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)

    var red = 0
    var green = 0
    var blue = 0
    var count = 0
    pixels.filterIndexed { index, _ -> index % 4 == 0 }.forEach { pixel ->
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        val hsl = rgbToHsl(r, g, b)
        if (hsl[1] > .12f && hsl[2] > .12f && hsl[2] < .88f) {
            red += r
            green += g
            blue += b
            count++
        }
    }

    val averageRed = if (count > 0) red / count else 38
    val averageGreen = if (count > 0) green / count else 32
    val averageBlue = if (count > 0) blue / count else 42
    val hsl = rgbToHsl(averageRed, averageGreen, averageBlue)
    val surfaceRgb = hslToRgb(hsl[0], maxOf(hsl[1], .35f), .15f)
    val borderRgb = hslToRgb(hsl[0], maxOf(hsl[1], .55f), .48f)

    return ArtistTilePalette(
        surface = Color(android.graphics.Color.rgb(surfaceRgb[0], surfaceRgb[1], surfaceRgb[2])),
        border = Color(android.graphics.Color.rgb(borderRgb[0], borderRgb[1], borderRgb[2])).copy(alpha = .6f),
    )
}

private fun extractArtistPalette(bitmap: Bitmap): ArtistPalette {
    val sample = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    val candidates = pixels.filterIndexed { index, _ -> index % 4 == 0 }.mapNotNull { pixel ->
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        val hsl = rgbToHsl(r, g, b)
        if (hsl[2] <= .15f || hsl[2] >= .85f || hsl[1] <= .15f) return@mapNotNull null
        Triple(hsl, intArrayOf(r, g, b), hsl[1] * 1.5f + (1f - kotlin.math.abs(hsl[2] - .5f)))
    }.sortedByDescending { it.third }
    if (candidates.isEmpty()) return ArtistPalette()
    val primary = candidates.first().first
    val backgroundRgb = hslToRgb(primary[0], primary[1].coerceAtMost(.45f), .82f)
    val darkSurfaceRgb = hslToRgb(primary[0], primary[1].coerceAtMost(.35f), .16f)
    val deezerRgb = hslToRgb(primary[0], primary[1].coerceAtLeast(.65f), .58f)
    return ArtistPalette(
        background = Color(android.graphics.Color.rgb(backgroundRgb[0], backgroundRgb[1], backgroundRgb[2])),
        darkSurface = Color(android.graphics.Color.rgb(darkSurfaceRgb[0], darkSurfaceRgb[1], darkSurfaceRgb[2])),
        deezer = Color(android.graphics.Color.rgb(deezerRgb[0], deezerRgb[1], deezerRgb[2])),
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
    fun hueToRgb(raw: Float): Float {
        var t = raw
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < .5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return intArrayOf(
        kotlin.math.round(hueToRgb(hue + 1f / 3f) * 255f).toInt(),
        kotlin.math.round(hueToRgb(hue) * 255f).toInt(),
        kotlin.math.round(hueToRgb(hue - 1f / 3f) * 255f).toInt(),
    )
}
