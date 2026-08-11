package com.sonar.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import com.sonar.app.data.Track
import com.sonar.app.data.RepeatMode
import com.sonar.app.data.SubControlMode
import java.util.Locale
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    extractPalette: Boolean = false,
    onPalette: (ArtworkPalette) -> Unit = {},
) {
    val artwork = rememberTrackArtwork(track, extractPalette)
    LaunchedEffect(artwork.palette, artwork.bitmap, artwork.paletteReady) {
        if (artwork.bitmap != null && artwork.paletteReady) onPalette(artwork.palette)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(
                        SonarGreen.copy(alpha = .8f),
                        SonarPink.copy(alpha = .75f),
                        Color(0xFF3B3449),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = artwork.bitmap, animationSpec = tween(400, easing = ExpressiveEasing), label = "artworkLoad") { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Artwork for ${track?.title ?: "track"}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.Album, contentDescription = null, tint = Color.White.copy(alpha = .86f), modifier = Modifier.size(42.dp))
            }
        }
    }
}

@Composable
fun AssetArtwork(
    assetPath: String,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    shape: Shape? = null,
    onPalette: (ArtworkPalette) -> Unit = {},
) {
    val artwork = rememberAssetArtwork(assetPath)
    LaunchedEffect(artwork.palette, artwork.paletteReady) {
        if (artwork.paletteReady) onPalette(artwork.palette)
    }
    Box(
        modifier = modifier.clip(shape ?: RoundedCornerShape(corner)).background(
            Brush.linearGradient(listOf(artwork.palette.primary, artwork.palette.secondary, Color(0xFF3B3449))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork.bitmap != null) {
            Image(
                bitmap = artwork.bitmap.asImageBitmap(),
                contentDescription = assetPath.substringAfterLast('/').substringBeforeLast('.'),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = Color.White.copy(alpha = .86f), modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
fun AssetImage(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: ColorFilter? = null,
) {
    val artwork = rememberAssetArtwork(assetPath)
    if (artwork.bitmap != null) {
        Image(
            bitmap = artwork.bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = tint,
            modifier = modifier,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.US),
        color = SonarMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier,
    )
}

@Composable
fun TrackRow(
    track: Track,
    selected: Boolean,
    onClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowColor by animateColorAsState(if (selected) SonarGreen.copy(alpha = .16f) else SonarOutline, animationSpec = tween(220), label = "trackRowColor")
    val rowBorder by animateColorAsState(if (selected) SonarGreen.copy(alpha = .55f) else Color.White.copy(alpha = .12f), animationSpec = tween(220), label = "trackRowBorder")
    Surface(
        color = rowColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, rowBorder),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(220))
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = .25f), spotColor = Color.Black.copy(alpha = .35f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play ${track.title} by ${track.artist}" },
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track, Modifier.size(65.dp), 14.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = track.artist,
                    color = if (selected) SonarGreen else SonarMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onArtistClick),
                )
                Text(trackMeta(track), color = Color.White.copy(alpha = .45f), fontSize = 9.3.sp, letterSpacing = .8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = Color.White.copy(alpha = .1f), shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(36.dp).clickable(onClick = onClick)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyLibrary(onImport: () -> Unit, importing: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork(null, Modifier.size(118.dp), 36.dp)
        Text("Your library is quiet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Выберите папку Music или любую папку с аудио. Вложенные директории тоже будут просканированы.", color = SonarMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        androidx.compose.material3.Button(
            onClick = onImport,
            enabled = !importing,
            colors = ButtonDefaults.buttonColors(containerColor = SonarControlSurface, contentColor = SonarControlContent),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (importing) "Сканирование..." else "Выбрать папку")
        }
    }
}

fun formatMs(value: Long): String {
    if (value <= 0L) return "--:--"
    val seconds = value / 1_000L
    return "%02d:%02d".format(Locale.US, seconds / 60L, seconds % 60L)
}

fun trackMeta(track: Track): String = listOfNotNull(
    track.codec?.uppercase(),
    track.sourceBitDepth?.let { "$it BIT" },
    track.bitrateKbps?.let { "$it KB/S" },
    track.sampleRate?.let { "%.1f KHZ".format(it / 1000f) },
).joinToString(" • ").ifBlank { "LOCAL AUDIO" }

@Composable
fun ArtistDivider(artist: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(20.dp).height(1.dp).background(Color.White.copy(alpha = .25f)))
        Text(artist.uppercase().take(25).let { if (artist.length > 25) "$it..." else it }, color = Color.White, fontSize = 10.4.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = .25f)))
    }
}

@Composable
fun ViewModeToggle(
    grid: Boolean,
    onGrid: () -> Unit,
    onList: () -> Unit,
    onLongPress: () -> Unit = {},
    darkContent: Boolean = false,
) {
    val content = if (darkContent) Color(0xFF121418) else Color.White
    val gridBackground by animateColorAsState(if (grid) Color.White.copy(alpha = .9f) else Color.Transparent, animationSpec = tween(180), label = "gridToggle")
    val listBackground by animateColorAsState(if (!grid) Color.White.copy(alpha = .9f) else Color.Transparent, animationSpec = tween(180), label = "listToggle")
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = if (darkContent) .5f else .08f), RoundedCornerShape(10.dp))
            .padding(2.dp),
    ) {
        Box(Modifier.size(34.dp).expressivePress(onGrid, onLongPress).background(gridBackground, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.GridView, contentDescription = "Grid view", tint = content)
        }
        Box(Modifier.size(34.dp).expressivePress(onList, onLongPress).background(listBackground, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.List, contentDescription = "List view", tint = content)
        }
    }
}

@Composable
fun ExpressiveButton(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Black,
    onClick: () -> Unit,
    corner: Dp = 22.dp,
    iconSize: Dp = 34.dp,
    blackShadow: Dp = 12.dp,
    colorShadow: Dp = 6.dp,
    pressedColor: Color? = null,
    borderColor: Color = Color.White.copy(alpha = .15f),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val targetColor = if (pressed && pressedColor != null) pressedColor else color
    val displayColor by animateColorAsState(targetColor, animationSpec = tween(600, easing = ExpressiveEasing), label = "buttonColor")
    val pressScale by animateFloatAsState(if (pressed) .92f else 1f, animationSpec = tween(180, easing = SpringBounce), label = "buttonPress")
    val animatedBlackShadow by animateDpAsState(blackShadow, animationSpec = tween(300, easing = ExpressiveEasing), label = "buttonBlackShadow")
    val animatedColorShadow by animateDpAsState(colorShadow, animationSpec = tween(600, easing = ExpressiveEasing), label = "buttonColorShadow")
    val animatedBorderColor by animateColorAsState(borderColor, animationSpec = tween(600, easing = ExpressiveEasing), label = "buttonBorder")
    Surface(
        color = displayColor,
        shape = RoundedCornerShape(corner),
        border = BorderStroke(1.dp, animatedBorderColor),
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .shadow(animatedBlackShadow, RoundedCornerShape(corner), ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .5f))
            .shadow(animatedColorShadow, RoundedCornerShape(corner), ambientColor = displayColor.copy(alpha = .35f), spotColor = displayColor.copy(alpha = .4f))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun DualModeControl(
    mode: SubControlMode,
    shuffle: Boolean,
    repeat: RepeatMode,
    accent: Color = SonarGreen,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pulse by remember { mutableStateOf(false) }
    var hasRendered by remember { mutableStateOf(false) }
    val pulseScale by animateFloatAsState(if (pulse) 1.12f else 1f, animationSpec = tween(200, easing = ExpressiveEasing), label = "subControlPulse")
    LaunchedEffect(mode) {
        if (hasRendered) {
            pulse = true
            delay(200L)
            pulse = false
        }
        hasRendered = true
    }
    val icon = if (mode == SubControlMode.SHUFFLE) {
        Icons.Rounded.Shuffle
    } else if (repeat == RepeatMode.ONE) {
        Icons.Rounded.RepeatOne
    } else {
        Icons.Rounded.Repeat
    }
    val active = if (mode == SubControlMode.SHUFFLE) shuffle else repeat != RepeatMode.OFF
    Surface(
        color = if (active) {
            accent
        } else {
            Color.White.copy(alpha = .86f)
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .15f)),
        modifier = modifier
            .heightIn(min = 44.dp)
            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
            .shadow(12.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .4f))
            .expressivePress(onTap, onLongPress),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = if (mode == SubControlMode.SHUFFLE) "Shuffle" else "Repeat", tint = Color(0xFF111318), modifier = Modifier.size(22.dp))
        }
    }
}

private fun Modifier.expressivePress(onTap: () -> Unit, onLongPress: () -> Unit): Modifier = pointerInput(onTap, onLongPress) {
    awaitEachGesture {
        awaitFirstDown()
        val up = withTimeoutOrNull(250L) { waitForUpOrCancellation() }
        if (up == null) {
            onLongPress()
            waitForUpOrCancellation()
        } else {
            onTap()
        }
    }
}
