package com.sonar.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonar.app.data.Track

@Composable
fun ArtistScreen(
    artist: String,
    tracks: List<Track>,
    grid: Boolean,
    onBack: () -> Unit,
    onToggleGrid: () -> Unit,
    onTrack: (Track) -> Unit,
    context: Context,
) {
    val hero = rememberArtistArtwork("artists/$artist.jpg")
    val palette = hero.palette
    val singles = ArtistCatalog.singlesFor(artist).ifEmpty {
        tracks.map { ArtistSingle(artist, it.title, it.artworkPath ?: "") }
    }
    Scaffold(containerColor = palette.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                if (hero.bitmap != null) {
                    Image(bitmap = hero.bitmap.asImageBitmap(), contentDescription = "Artwork for $artist", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)))
                } else {
                    Box(Modifier.fillMaxSize().background(palette.darkSurface))
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f)))))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(44.dp, 20.dp, 18.dp, 20.dp), horizontalAlignment = Alignment.End) {
                    Text(artist.uppercase(), color = Color.White, fontFamily = PlayerDisplayFont, fontSize = 19.2.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = .88f), shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = .12f))) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ArtistStat("0", Icons.Rounded.Person)
                        ArtistStat("0", Icons.Rounded.Album)
                        Spacer(Modifier.weight(1f))
                        Surface(color = palette.deezer, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(width = 72.dp, height = 36.dp).clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.deezer.com/search/${Uri.encode(artist)}")))
                        }) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AssetImage("deezer-logo.png", "Deezer", Modifier.width(52.dp).height(22.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ТРЕКИ НА УСТРОЙСТВЕ", color = Color.Black.copy(alpha = .65f), fontFamily = PlayerDisplayFont, fontSize = 14.1.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                ViewModeToggle(grid, { if (!grid) onToggleGrid() }, { if (grid) onToggleGrid() }, darkContent = true)
            }
            if (grid) {
                LazyVerticalGrid(GridCells.Fixed(2), Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(singles, key = { it.title }) { single -> ArtistTile(single, tracks.firstOrNull { it.title.equals(single.title, true) }, onTrack, palette.darkSurface) }
                }
            } else {
                LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(singles, key = { it.title }) { single -> ArtistList(single, tracks.firstOrNull { it.title.equals(single.title, true) }, onTrack, palette.darkSurface) }
                }
            }
        }
    }
}

@Composable
private fun ArtistStat(value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, color = Color(0xFF121418), fontSize = 22.4.sp, fontFamily = PlayerBodyFont, fontWeight = FontWeight.ExtraBold)
        Icon(icon, contentDescription = null, tint = Color(0xFF121418), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ArtistTile(single: ArtistSingle, playable: Track?, onTrack: (Track) -> Unit, surface: Color) {
    Card(onClick = { playable?.let(onTrack) }, enabled = playable != null, colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(26.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .14f))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AssetArtwork(single.coverAsset, Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(single.title, color = Color.White, fontFamily = PlayerBodyFont, fontSize = 18.4.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.2.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (playable == null) "ИМПОРТИРУЙТЕ ТРЕК" else "FLAC • 24 BIT • LOCAL", color = Color.White.copy(alpha = .45f), fontFamily = PlayerBodyFont, fontSize = 8.3.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .88.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ArtistList(single: ArtistSingle, playable: Track?, onTrack: (Track) -> Unit, surface: Color) {
    Surface(color = surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(enabled = playable != null) { playable?.let(onTrack) }) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssetArtwork(single.coverAsset, Modifier.size(70.dp), 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(single.title, color = Color.White, fontFamily = PlayerBodyFont, fontSize = 15.2.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (playable == null) "ИМПОРТИРУЙТЕ ТРЕК" else "FLAC • 24 BIT • LOCAL", color = Color.White.copy(alpha = .4f), fontFamily = PlayerBodyFont, fontSize = 9.9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp)
            }
            Icon(Icons.Rounded.PlayArrow, "Play", tint = if (playable == null) Color.White.copy(alpha = .25f) else SonarGreen)
        }
    }
}
