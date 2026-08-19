package com.sonar.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import com.sonar.app.R
import com.sonar.app.data.DeezerArtistInfo
import com.sonar.app.data.DeezerArtistState
import com.sonar.app.data.isPrimaryArtist
import com.sonar.app.data.Track
import com.sonar.app.data.trackIncludesArtist
import com.sonar.app.data.info

private val ArtistHeroShape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
private val ArtistWideHeroShape = RoundedCornerShape(28.dp)

private data class ArtistTrackGroup(
    val title: String,
    val singles: List<ArtistSingle>,
    val tracks: List<Track>,
)

private fun artistTrackGroups(artist: String, tracks: List<Track>, sectionTitle: String): List<ArtistTrackGroup> {
    val (ownTracks, collaborationTracks) = tracks.partition { isPrimaryArtist(it.artist, artist) }
    val orderedTracks = ownTracks + collaborationTracks
    return if (orderedTracks.isEmpty()) {
        emptyList()
    } else {
        listOf(
            ArtistTrackGroup(
                title = sectionTitle,
                singles = ArtistCatalog.singlesForTracks(artist, orderedTracks),
                tracks = orderedTracks,
            ),
        )
    }
}

@Composable
fun ArtistScreen(
    artist: String,
    tracks: List<Track>,
    grid: Boolean,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onTrack: (Track) -> Unit,
    context: Context,
    deezerState: DeezerArtistState,
) {
    val view = LocalView.current
    val window = (context as? Activity)?.window
    val fallbackTrack = tracks.firstOrNull { !it.artworkPath.isNullOrBlank() } ?: tracks.firstOrNull()
    val heroAsset = ArtistCatalog.heroAssetFor(artist) ?: "artists/$artist.jpg"
    val deezerInfo = deezerState.info
    val hero = rememberArtistArtwork(heroAsset, fallbackTrack, deezerInfo?.cachedPicturePath)
    val palette = hero.palette
    val sectionTitle = stringResource(R.string.artist_tracks_on_device)
    val groups = artistTrackGroups(artist, tracks, sectionTitle)

    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val previousNavigationBarColor = window.navigationBarColor
        val previousStatusBarColor = window.statusBarColor
        val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousNavigationBarContrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else null
        val previousNavigationBarDividerColor = if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor else null
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.navigationBarColor = previousNavigationBarColor
            window.statusBarColor = previousStatusBarColor
            if (Build.VERSION.SDK_INT >= 29 && previousNavigationBarContrast != null) {
                window.isNavigationBarContrastEnforced = previousNavigationBarContrast
            }
            if (Build.VERSION.SDK_INT >= 28 && previousNavigationBarDividerColor != null) {
                window.navigationBarDividerColor = previousNavigationBarDividerColor
            }
            controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            controller.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }

    SideEffect {
        window?.let {
            it.statusBarColor = android.graphics.Color.TRANSPARENT
            it.navigationBarColor = palette.background.toArgb()
            if (Build.VERSION.SDK_INT >= 28) it.navigationBarDividerColor = palette.background.toArgb()
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = true
            }
        }
    }

    Scaffold(
        containerColor = palette.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val expanded = maxWidth >= 700.dp
            if (expanded) {
                ArtistExpandedLayout(
                    artist = artist,
                    hero = hero,
                    palette = palette,
                    groups = groups,
                    deezerInfo = deezerInfo,
                    grid = grid,
                    onBack = onBack,
                    onToggleGrid = onToggleGrid,
                    onTrack = onTrack,
                    context = context,
                )
            } else if (grid) {
                ArtistPortraitGrid(
                    artist = artist,
                    hero = hero,
                    palette = palette,
                    groups = groups,
                    deezerInfo = deezerInfo,
                    context = context,
                    onBack = onBack,
                    onToggleGrid = onToggleGrid,
                    onTrack = onTrack,
                )
            } else {
                ArtistPortraitList(
                    artist = artist,
                    hero = hero,
                    palette = palette,
                    groups = groups,
                    deezerInfo = deezerInfo,
                    context = context,
                    onBack = onBack,
                    onToggleGrid = onToggleGrid,
                    onTrack = onTrack,
                )
            }
        }
    }
}

@Composable
private fun ArtistPortraitGrid(
    artist: String,
    hero: ArtistArtwork,
    palette: ArtistPalette,
    groups: List<ArtistTrackGroup>,
    deezerInfo: DeezerArtistInfo?,
    context: Context,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ArtistHeroCard(artist, hero, onBack, ArtistHeroShape)
        }
        item {
            ArtistStatsPill(
                palette = palette,
                artist = artist,
                info = deezerInfo,
                context = context,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
        groups.forEachIndexed { groupIndex, group ->
            item(key = "header:${group.title}") {
                ArtistSectionHeader(
                    title = group.title,
                    grid = true,
                    onToggleGrid = onToggleGrid,
                    showToggle = groupIndex == 0,
                )
            }
            group.singles.chunked(2).forEachIndexed { rowIndex, row ->
                item(key = "grid:${groupIndex}:$rowIndex") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        row.forEach { single ->
                            ArtistTile(
                                single = single,
                                playable = playableTrack(single, group.tracks),
                                onTrack = onTrack,
                                surface = palette.darkSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistPortraitList(
    artist: String,
    hero: ArtistArtwork,
    palette: ArtistPalette,
    groups: List<ArtistTrackGroup>,
    deezerInfo: DeezerArtistInfo?,
    context: Context,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            ArtistHeroCard(artist, hero, onBack, ArtistHeroShape)
        }
        item {
            ArtistStatsPill(
                palette = palette,
                artist = artist,
                info = deezerInfo,
                context = context,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
        groups.forEachIndexed { groupIndex, group ->
            item(key = "header:${group.title}") {
                ArtistSectionHeader(
                    title = group.title,
                    grid = false,
                    onToggleGrid = onToggleGrid,
                    showToggle = groupIndex == 0,
                )
            }
            columnItems(group.singles, key = { "list:$groupIndex:${it.trackId ?: "${it.artist}:${it.title}:${it.coverAsset}"}" }) { single ->
                ArtistList(
                    single = single,
                    playable = playableTrack(single, group.tracks),
                    onTrack = onTrack,
                    surface = palette.darkSurface,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistExpandedLayout(
    artist: String,
    hero: ArtistArtwork,
    palette: ArtistPalette,
    groups: List<ArtistTrackGroup>,
    deezerInfo: DeezerArtistInfo?,
    grid: Boolean,
    context: Context,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onTrack: (Track) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(340.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArtistHeroCard(artist, hero, onBack, ArtistWideHeroShape)
            ArtistStatsPill(palette, artist, deezerInfo, context, Modifier.fillMaxWidth())
        }
        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                groups.forEachIndexed { groupIndex, group ->
                    item(key = "header:${group.title}", span = { GridItemSpan(maxLineSpan) }) {
                        ArtistSectionHeader(
                            title = group.title,
                            grid = true,
                            onToggleGrid = onToggleGrid,
                            wide = true,
                            showToggle = groupIndex == 0,
                        )
                    }
                    gridItems(group.singles, key = { "grid:$groupIndex:${it.trackId ?: "${it.artist}:${it.title}:${it.coverAsset}"}" }) { single ->
                        ArtistTile(
                            single = single,
                            playable = playableTrack(single, group.tracks),
                            onTrack = onTrack,
                            surface = palette.darkSurface,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEachIndexed { groupIndex, group ->
                    item(key = "header:${group.title}") {
                        ArtistSectionHeader(
                            title = group.title,
                            grid = false,
                            onToggleGrid = onToggleGrid,
                            wide = true,
                            showToggle = groupIndex == 0,
                        )
                    }
                    columnItems(group.singles, key = { "list:$groupIndex:${it.trackId ?: "${it.artist}:${it.title}:${it.coverAsset}"}" }) { single ->
                        ArtistList(
                            single = single,
                            playable = playableTrack(single, group.tracks),
                            onTrack = onTrack,
                            surface = palette.darkSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeroCard(
    artist: String,
    hero: ArtistArtwork,
    onBack: () -> Unit,
    shape: RoundedCornerShape,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = .42f), spotColor = Color.Black.copy(alpha = .42f))
            .clip(shape)
            .background(hero.palette.darkSurface),
    ) {
        if (hero.bitmap != null) {
            Image(
                bitmap = hero.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.artist_cd_artwork, artist),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = .92f)),
                    ),
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.btn_back), tint = Color.White)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, top = 44.dp, end = 20.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = artist.uppercase(),
                color = Color.White,
                fontFamily = displayFontFor(artist),
                fontSize = 19.2.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ArtistStatsPill(
    palette: ArtistPalette,
    artist: String,
    info: DeezerArtistInfo?,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val fans = info?.nbFan?.let(::formatDeezerFans) ?: "0"
    val albums = info?.nbAlbum?.toString() ?: "0"
    val deezerLink = info?.link ?: "https://www.deezer.com/search/${Uri.encode(artist)}"
    Surface(
        color = Color.White.copy(alpha = .88f),
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = .12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtistStat(fans, Icons.Rounded.Person)
            ArtistStat(albums, Icons.Rounded.Album)
            Spacer(Modifier.weight(1f))
            Surface(
                color = palette.deezer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(width = 110.dp, height = 36.dp)
                    .clickable {
                        runCatching {
                            val uri = Uri.parse(deezerLink)
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AssetImage("deezer-logo.png", "Deezer", Modifier.width(80.dp).height(22.dp))
                }
            }
        }
    }
}

private fun formatDeezerFans(value: Long): String = when {
    value >= 1_000_000 -> "${"%.1f".format(java.util.Locale.US, value / 1_000_000.0)}m"
    value >= 1_000 -> "${"%.1f".format(java.util.Locale.US, value / 1_000.0)}k"
    else -> value.toString()
}

@Composable
private fun ArtistSectionHeader(
    title: String,
    grid: Boolean,
    onToggleGrid: () -> Unit,
    wide: Boolean = false,
    showToggle: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (wide) 0.dp else 16.dp,
                end = if (wide) 0.dp else 16.dp,
                top = if (wide) 0.dp else 10.dp,
                bottom = if (wide) 4.dp else 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.Black.copy(alpha = .65f),
            fontFamily = RubikFont,
            fontSize = 14.1.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f),
        )
        if (showToggle) {
            ViewModeToggle(
                grid = grid,
                onGrid = { if (!grid) onToggleGrid() },
                onList = { if (grid) onToggleGrid() },
                darkContent = true,
            )
        }
    }
}

@Composable
private fun ArtistStat(value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            value,
            color = Color(0xFF121418),
            fontSize = 22.4.sp,
            fontFamily = RubikFont,
            fontWeight = FontWeight.ExtraBold,
        )
        Icon(icon, contentDescription = null, tint = Color(0xFF121418), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ArtistTile(
    single: ArtistSingle,
    playable: Track?,
    onTrack: (Track) -> Unit,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberArtistArtwork(single.coverAsset, playable)
    val tilePalette = remember(artwork.bitmap) { artwork.bitmap?.let(::extractArtistTilePalette) }
    val tileSurface = tilePalette?.surface ?: surface
    val tileBorder = tilePalette?.border ?: Color.White.copy(alpha = .14f)
    val shape = RoundedCornerShape(26.dp)

    Card(
        onClick = { playable?.let(onTrack) },
        enabled = playable != null,
        colors = CardDefaults.cardColors(containerColor = tileSurface),
        shape = shape,
        border = BorderStroke(1.dp, tileBorder),
        modifier = modifier
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .4f))
            .shadow(1.dp, shape, ambientColor = Color.White.copy(alpha = .06f), spotColor = Color.White.copy(alpha = .06f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ArtistArtworkBox(artwork, Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    single.title,
                    color = Color.White,
                    fontFamily = RubikFont,
                    fontSize = 18.4.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 20.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playable?.let(::trackMeta) ?: stringResource(R.string.artist_import_track),
                    color = Color.White.copy(alpha = .45f),
                    fontFamily = PlayerBodyFont,
                    fontSize = 8.3.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .88.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistList(
    single: ArtistSingle,
    playable: Track?,
    onTrack: (Track) -> Unit,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    val artwork = rememberArtistArtwork(single.coverAsset, playable)
    val tilePalette = remember(artwork.bitmap) { artwork.bitmap?.let(::extractArtistTilePalette) }
    val tileSurface = tilePalette?.surface ?: surface
    val shape = RoundedCornerShape(16.dp)

    Surface(
        color = tileSurface,
        shape = shape,
        border = BorderStroke(1.dp, tilePalette?.border ?: Color.White.copy(alpha = .14f)),
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .4f))
            .clickable(enabled = playable != null) { playable?.let(onTrack) },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtistArtworkBox(
                artwork = artwork,
                modifier = Modifier
                    .size(70.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .4f)),
                corner = 12.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    single.title,
                    color = Color.White,
                    fontFamily = RubikFont,
                    fontSize = 15.2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playable?.let(::trackMeta) ?: stringResource(R.string.artist_import_track),
                    color = Color.White.copy(alpha = .4f),
                    fontFamily = PlayerBodyFont,
                    fontSize = 9.9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistArtworkBox(
    artwork: ArtistArtwork,
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(artwork.palette.darkSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork.bitmap != null) {
            Image(
                bitmap = artwork.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Album,
                contentDescription = null,
                tint = Color.White.copy(alpha = .86f),
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

private fun playableTrack(single: ArtistSingle, tracks: List<Track>): Track? =
    single.trackId?.let { trackId -> tracks.firstOrNull { it.id == trackId } }
        ?: tracks.firstOrNull {
            it.title.equals(single.title, ignoreCase = true) &&
                trackIncludesArtist(it.artist, single.artist)
        }
