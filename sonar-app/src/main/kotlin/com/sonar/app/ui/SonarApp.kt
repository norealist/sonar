package com.sonar.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sonar.app.HapticHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.sonar.app.R
import com.sonar.app.data.AppLanguage
import com.sonar.app.PlayerViewModel
import com.sonar.app.data.AppScreen
import com.sonar.app.data.DEFAULT_PLAYLIST_NAME
import com.sonar.app.data.RepeatMode
import com.sonar.app.data.Sheet
import com.sonar.app.data.SubControlMode
import com.sonar.app.data.Track
import com.sonar.app.data.trackIncludesArtist
import com.sonar.app.player.PlayerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarApp(
    viewModel: PlayerViewModel,
    screen: AppScreen,
    onPickAudio: () -> Unit,
    context: Context,
) {
    val nav = rememberNavController()
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val library by viewModel.library.snapshot.collectAsStateWithLifecycle()
    val settings by viewModel.settings.settings.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val lastSelectedTrack by viewModel.lastSelectedTrack.collectAsStateWithLifecycle()
    val artist by viewModel.selectedArtist.collectAsStateWithLifecycle()
    val deezerArtist by viewModel.deezerArtist.collectAsStateWithLifecycle()
    val importing by viewModel.isImporting.collectAsStateWithLifecycle()
    val appError by viewModel.error.collectAsStateWithLifecycle()
    val miniTrack = player.selectedTrack ?: lastSelectedTrack ?: library.tracks.firstOrNull { it.id == settings.selectedTrackId }
    val snackbar = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(screen, navBackStackEntry?.destination?.route, player.selectedTrack?.id) {
        if (screen != AppScreen.PLAYER && nav.currentDestination?.route != screen.route) {
            nav.navigate(screen.route) {
                popUpTo(nav.graph.startDestinationId) { saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }
    }
    DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val destinationScreen = AppScreen.values().firstOrNull { it.route == destination.route }
            if (destinationScreen != null && destinationScreen != AppScreen.PLAYER && viewModel.screen.value != destinationScreen) {
                viewModel.navigate(destinationScreen)
            }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    LaunchedEffect(appError) {
        appError?.let { snackbar.showSnackbar(it) }
    }

    Surface(color = SonarBackground, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = AppScreen.LIBRARY.route,
                    modifier = Modifier.padding(padding),
                ) {
                composable(AppScreen.LIBRARY.route) {
                    LibraryScreen(
                        tracks = library.tracks,
                        grid = settings.libraryGrid,
                        columns = settings.gridColumns,
                        importing = importing,
                        onImport = onPickAudio,
                        onSettings = { viewModel.navigate(AppScreen.SETTINGS) },
                        onToggleGrid = { viewModel.settings.update { it.copy(libraryGrid = !it.libraryGrid) } },
                        onToggleGridColumn = { columns -> viewModel.settings.update { it.copy(gridColumns = columns) } },
                        onTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onArtist = viewModel::openArtist,
                    )
                }
                composable(AppScreen.ARTIST.route) {
                    Box(Modifier.fillMaxSize())
                }
                composable(AppScreen.SETTINGS.route) {
                    SettingsScreen(
                        highResolution = settings.highResolutionOutput,
                        resumeAfterFocusLoss = settings.resumeAfterFocusLoss,
                        hapticFeedback = settings.hapticFeedback,
                        volume = settings.volume,
                        outputDescription = player.outputDescription,
                        sessionId = player.audioSessionId,
                        currentLanguage = settings.language,
                        importing = importing,
                        onImport = onPickAudio,
                        onBack = { viewModel.navigate(AppScreen.LIBRARY) },
                        onAbout = { viewModel.navigate(AppScreen.ABOUT) },
                        onHighResolution = viewModel::toggleHighResolution,
                        onResumeAfterFocusLoss = viewModel::setResumeAfterFocusLoss,
                        onHapticFeedback = viewModel::setHapticFeedback,
                        onLanguage = viewModel::setLanguage,
                        onVolume = viewModel.controller::setVolume,
                    )
                }
                composable(AppScreen.ABOUT.route) {
                    AboutScreen(
                        context = context,
                        onBack = { viewModel.navigate(AppScreen.SETTINGS) },
                    )
                }
                }
            }
            AnimatedVisibility(
                visible = screen == AppScreen.PLAYER,
                enter = slideInVertically(animationSpec = tween(350, easing = ExpressiveEasing)) { it },
                exit = slideOutVertically(animationSpec = tween(350, easing = ExpressiveEasing)) { it },
                modifier = Modifier.fillMaxSize().zIndex(20f),
            ) {
                PlayerScreen(
                    state = player,
                    onCollapse = { viewModel.navigate(AppScreen.LIBRARY) },
                    onSwipeNext = viewModel.controller::next,
                    onSwipePrevious = viewModel.controller::previous,
                    onToggle = viewModel.controller::togglePlayPause,
                    onPrevious = viewModel.controller::previous,
                    onNext = viewModel.controller::next,
                    onSeek = viewModel.controller::seekTo,
                    onSeeking = viewModel.controller::setSeeking,
                    onSubTap = viewModel.controller::tapSubControl,
                    onSubLongPress = {
                        if (settings.hapticFeedback) {
                            HapticHelper.performTick(context)
                        }
                        viewModel.controller.toggleSubControlMode()
                    },
                    onTimer = { viewModel.setSheet(Sheet.SLEEP_TIMER) },
                    onQueue = { viewModel.setSheet(Sheet.QUEUE) },
                    onFavorite = viewModel::toggleFavorite,
                    onArtist = { viewModel.openArtist(it) },
                )
            }
            AnimatedVisibility(
                visible = screen == AppScreen.ARTIST,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180)),
                modifier = Modifier.fillMaxSize().zIndex(20f),
            ) {
                val selectedArtist = artist
                ArtistScreen(
                    artist = selectedArtist ?: "Unknown artist",
                    tracks = library.tracks.filter { track ->
                        selectedArtist != null && trackIncludesArtist(track.artist, selectedArtist)
                    },
                    grid = settings.artistGrid,
                    onBack = { viewModel.navigate(AppScreen.LIBRARY) },
                    onToggleGrid = { viewModel.settings.update { it.copy(artistGrid = !it.artistGrid) } },
                    onTrack = { track -> viewModel.playTrack(track) },
                    context = context,
                    deezerState = deezerArtist,
                )
            }
            AnimatedVisibility(
                visible = screen != AppScreen.SETTINGS && screen != AppScreen.PLAYER && miniTrack != null,
                    enter = slideInVertically(animationSpec = tween(350, easing = ExpressiveEasing)) { it } + fadeIn(animationSpec = tween(300, easing = ExpressiveEasing)),
                    exit = slideOutVertically(animationSpec = tween(350, easing = ExpressiveEasing)) { it } + fadeOut(animationSpec = tween(250, easing = ExpressiveEasing)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(30f)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) {
                miniTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        state = player,
                        onOpen = viewModel::openPlayer,
                        onToggle = viewModel.controller::togglePlayPause,
                    )
                }
            }
        }
    }

    player.activeSheet?.let { sheet ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.setSheet(null) },
            sheetState = sheetState,
            containerColor = SonarSurface,
        ) {
            when (sheet) {
                Sheet.QUEUE -> QueueSheet(player, viewModel::selectTrack) { viewModel.setSheet(null) }
                Sheet.SLEEP_TIMER -> SleepSheet(
                    timer = player.sleepTimer,
                    onSet = { minutes -> viewModel.controller.setSleepTimer(minutes); viewModel.setSheet(null) },
                    onCancel = viewModel.controller::cancelSleepTimer,
                    onClose = { viewModel.setSheet(null) },
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }

    BackHandler(enabled = screen != AppScreen.LIBRARY || player.activeSheet != null) {
        if (player.activeSheet != null) {
            viewModel.setSheet(null)
        } else if (screen == AppScreen.ABOUT) {
            viewModel.navigate(AppScreen.SETTINGS)
        } else {
            viewModel.navigate(AppScreen.LIBRARY)
        }
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    state: PlayerUiState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val surfaceColor by animateColorAsState(
        targetValue = if (state.isPlaying) Color(0xFF46434F) else Color(0xFF3B3942),
        animationSpec = tween(260),
        label = "miniPlayerSurface",
    )
    Surface(
        color = surfaceColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .45f))
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track, Modifier.size(48.dp), 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = Color.White, fontFamily = PlayerDisplayFont, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(alpha = .7f), fontFamily = PlayerBodyFont, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(42.dp)) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.cd_play_or_pause), tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

private val AppScreen.route: String
    get() = name.lowercase()

private enum class LibrarySortMode(@get:StringRes val labelRes: Int) {
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc),
    ARTIST_ASC(R.string.sort_artist_asc),
    ARTIST_DESC(R.string.sort_artist_desc),
    CREATED_DESC(R.string.sort_created_desc),
    CREATED_ASC(R.string.sort_created_asc),
}

@Composable
private fun LibraryScreen(
    tracks: List<Track>,
    grid: Boolean,
    columns: Int,
    importing: Boolean,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleGridColumn: (Int) -> Unit,
    onTrack: (Track, List<Track>) -> Unit,
    onArtist: (String) -> Unit,
) {
    var sortMode by remember { mutableStateOf(LibrarySortMode.ARTIST_ASC) }
    var showColumns by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredTracks = if (normalizedQuery.isBlank()) {
        tracks
    } else {
        tracks.filter { track ->
            listOf(track.title, track.artist, track.album, track.uri.substringAfterLast('/'))
                .any { value -> value.lowercase().contains(normalizedQuery) }
        }
    }
    val displayTracks = when (sortMode) {
        LibrarySortMode.NAME_ASC -> filteredTracks.sortedBy { it.title.lowercase() }
        LibrarySortMode.NAME_DESC -> filteredTracks.sortedByDescending { it.title.lowercase() }
        LibrarySortMode.ARTIST_ASC -> filteredTracks.sortedWith(compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
        LibrarySortMode.ARTIST_DESC -> filteredTracks.sortedWith(compareByDescending<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
        LibrarySortMode.CREATED_DESC -> filteredTracks.sortedByDescending { it.createdAt }
        LibrarySortMode.CREATED_ASC -> filteredTracks.sortedBy { it.createdAt }
    }
    Scaffold(containerColor = SonarElevated) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssetImage("logo2.png", "SONAR logo", Modifier.size(28.dp), ColorFilter.tint(Color.White))
                Text("SONAR", color = Color.White, fontFamily = SonarLogoFont, fontSize = 35.2.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
                IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.size(40.dp)) {
                    Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, stringResource(if (searchOpen) R.string.search_close else R.string.search_open), tint = Color.White, modifier = Modifier.size(25.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title), tint = Color.White, modifier = Modifier.size(26.dp)) }
            }
            AnimatedVisibility(
                visible = searchOpen,
                enter = slideInVertically(animationSpec = tween(220, easing = ExpressiveEasing)) { -it } + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(animationSpec = tween(180, easing = ExpressiveEasing)) { -it } + fadeOut(animationSpec = tween(120)),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.btn_clear)) }
                    },
                    shape = RoundedCornerShape(16.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.library_tracks_count_header, displayTracks.size), color = Color.White, fontFamily = RubikFont, fontSize = 9.6.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp, modifier = Modifier.weight(1f))
                Row(
                    Modifier.offset(x = (-4).dp).clickable { sortMenuOpen = !sortMenuOpen }.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.btn_sort), color = Color.White, fontFamily = RubikFont, fontSize = 9.6.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.btn_sort), tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Surface(color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(10.dp)) {
                    ViewModeToggle(
                        grid = grid,
                        onGrid = { if (!grid) onToggleGrid() },
                        onList = { if (grid) onToggleGrid() },
                        onLongPress = { showColumns = !showColumns },
                    )
                }
            }
            if (showColumns && grid) {
                Surface(color = SonarSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.grid_settings_title), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.grid_columns_in_row, columns), color = SonarControlSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = columns.toFloat(), onValueChange = { value ->
                            val next = value.toInt().coerceIn(2, 12)
                            if (next != columns) onToggleGridColumn(next)
                        }, valueRange = 2f..12f, steps = 9, colors = SliderDefaults.colors(
                            thumbColor = SonarControlSurface,
                            activeTrackColor = SonarControlSurface,
                            inactiveTrackColor = Color.White.copy(alpha = .2f),
                            activeTickColor = Color.White,
                            inactiveTickColor = Color.White,
                        ))
                    }
                }
            }
            LaunchedEffect(sortMode, searchQuery) {
                listState.scrollToItem(0)
                gridState.scrollToItem(0)
            }
            if (tracks.isEmpty()) {
                EmptyLibrary(onImport, importing)
            } else if (displayTracks.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = SonarMuted, modifier = Modifier.size(44.dp))
                    Text(stringResource(R.string.search_empty_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.search_empty_subtitle), color = SonarMuted, fontSize = 13.sp)
                }
            } else {
                if (grid) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (sortMode == LibrarySortMode.ARTIST_ASC || sortMode == LibrarySortMode.ARTIST_DESC) displayTracks.groupBy { it.artist }.forEach { (artist, artistTracks) ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "artist-$artist") { ArtistDivider(artist) }
                            items(artistTracks, key = { it.id }) { track ->
                                TrackGridCard(track, onClick = { onTrack(track, displayTracks) })
                            }
                        } else {
                            items(displayTracks, key = { it.id }) { track ->
                                TrackGridCard(track, onClick = { onTrack(track, displayTracks) })
                            }
                        }
                    }
                } else {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (sortMode == LibrarySortMode.ARTIST_ASC || sortMode == LibrarySortMode.ARTIST_DESC) displayTracks.groupBy { it.artist }.forEach { (artist, artistTracks) ->
                            item(key = "artist-$artist") { ArtistDivider(artist) }
                            items(artistTracks, key = { it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    selected = false,
                                    onClick = { onTrack(track, displayTracks) },
                                    onArtistClick = { onArtist(track.artist) },
                                )
                            }
                        } else {
                            items(displayTracks, key = { it.id }) { track ->
                                TrackRow(track, false, { onTrack(track, displayTracks) }, { onArtist(track.artist) })
                            }
                        }
                    }
                }
            }
            }
            AnimatedVisibility(
                visible = sortMenuOpen,
                enter = slideInVertically(animationSpec = tween(220, easing = ExpressiveEasing)) { -it } + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(animationSpec = tween(180, easing = ExpressiveEasing)) { -it } + fadeOut(animationSpec = tween(120)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 18.dp),
            ) {
                SortMenu(
                    selected = sortMode,
                    onSelect = { mode ->
                        sortMode = mode
                        sortMenuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(
    selected: LibrarySortMode,
    onSelect: (LibrarySortMode) -> Unit,
) {
    Surface(
        color = SonarSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
        modifier = Modifier
            .width(232.dp)
            .padding(top = 4.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = .4f), spotColor = Color.Black.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(stringResource(R.string.sort_title), color = SonarMuted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            LibrarySortMode.entries.forEach { mode ->
                val active = mode == selected
                Surface(
                    color = if (active) SonarControlSurface.copy(alpha = .18f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(mode.labelRes), color = if (active) Color.White else SonarMuted, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (active) Text("✓", color = SonarControlSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackGridCard(track: Track, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SonarOutline),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Artwork(track, Modifier.fillMaxWidth().aspectRatio(1f), 16.dp)
            Spacer(Modifier.height(9.dp))
            Text(track.title, fontWeight = FontWeight.ExtraBold, fontSize = 16.8.sp, lineHeight = 18.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(trackMeta(track), color = Color.White.copy(alpha = .45f), fontSize = 8.3.sp, letterSpacing = .88.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(subtitle, color = SonarMuted, fontSize = 10.sp, letterSpacing = 1.3.sp)
        }
        actions()
    }
}

@Composable
private fun PlayerScreen(
    state: PlayerUiState,
    onCollapse: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeeking: (Boolean) -> Unit,
    onSubTap: () -> Unit,
    onSubLongPress: () -> Unit,
    onTimer: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onArtist: (String) -> Unit,
) {
    val track = state.selectedTrack
    val density = androidx.compose.ui.platform.LocalDensity.current
    var verticalDragPx by remember { mutableFloatStateOf(0f) }
    var horizontalOffsetPx by remember { mutableFloatStateOf(0f) }
    var horizontalDragging by remember { mutableStateOf(false) }
    val animatedHorizontalOffset by animateFloatAsState(
        targetValue = horizontalOffsetPx,
        animationSpec = if (horizontalDragging) snap() else tween(220, easing = LinearOutSlowInEasing),
        label = "swipeOffset",
    )
    var palette by remember(track?.id) { mutableStateOf(ArtworkPalette()) }
    var scrub by remember(track?.id) { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val artworkScale by animateFloatAsState(if (state.isPlaying) 1.02f else 1f, animationSpec = tween(400, easing = SpringBounce), label = "artworkScale")
    val artworkShadow by animateDpAsState(if (state.isPlaying) 18.dp else 12.dp, animationSpec = tween(400, easing = ExpressiveEasing), label = "artworkShadow")
    val artworkGlow by animateFloatAsState(if (state.isPlaying) .45f else 0f, animationSpec = tween(400, easing = ExpressiveEasing), label = "artworkGlow")
    LaunchedEffect(state.positionMs, state.isSeeking) {
        if (!state.isSeeking) scrub = state.positionMs.toFloat().coerceIn(0f, duration.toFloat())
    }
    val swipeModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { horizontalDragging = true },
            onHorizontalDrag = { change, dragAmount ->
                horizontalOffsetPx += dragAmount
                change.consume()
            },
            onDragEnd = {
                val threshold = with(density) { 72.dp.toPx() }
                when {
                    horizontalOffsetPx > threshold -> onSwipeNext()
                    horizontalOffsetPx < -threshold -> onSwipePrevious()
                }
                horizontalDragging = false
                horizontalOffsetPx = 0f
            },
            onDragCancel = {
                horizontalDragging = false
                horizontalOffsetPx = 0f
            },
        )
    }
    Scaffold(containerColor = Color.Transparent) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .background(palette.darkSurface)
                .padding(padding)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f) {
                                verticalDragPx += dragAmount
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            val threshold = with(density) { 120.dp.toPx() }
                            if (verticalDragPx >= threshold) onCollapse()
                            verticalDragPx = 0f
                        },
                        onDragCancel = { verticalDragPx = 0f },
                    )
                }
        ) {
            val horizontalOffset = animatedHorizontalOffset
            val expanded = maxWidth >= 700.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (expanded) 32.dp else 20.dp,
                        end = if (expanded) 32.dp else 20.dp,
                        top = if (expanded) 24.dp else 10.dp,
                        bottom = if (expanded) 24.dp else 14.dp,
                    ),
            ) {
                if (expanded) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(swipeModifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ArtistHeader(track, state, palette.primary, Modifier.fillMaxWidth().padding(top = 4.dp), alignEnd = false, onArtist = onArtist, artistTranslationX = horizontalOffset)
                            Artwork(track, Modifier.widthIn(max = 320.dp).align(Alignment.CenterHorizontally).aspectRatio(1f).graphicsLayer { translationX = horizontalOffset; scaleX = artworkScale; scaleY = artworkScale }.border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(32.dp)).shadow(artworkShadow, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = if (state.isPlaying) .5f else .4f), spotColor = Color.Black.copy(alpha = if (state.isPlaying) .5f else .4f)).shadow(6.dp, RoundedCornerShape(32.dp), ambientColor = palette.primary.copy(alpha = artworkGlow), spotColor = palette.primary.copy(alpha = artworkGlow)), 32.dp, extractPalette = true, onPalette = { palette = it })
                        }
                    PlayerDetails(track, state, scrub, duration, onToggle, onPrevious, onNext, onSeek, onSeeking, { scrub = it }, onSubTap, onSubLongPress, onTimer, onQueue, onFavorite, onArtist, palette.primary, palette.secondary, wide = true, Modifier.weight(1f))
                }
                } else {
                    Column(swipeModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        ArtistHeader(track, state, palette.primary, Modifier.fillMaxWidth().padding(top = 4.dp), alignEnd = true, onArtist = onArtist, artistTranslationX = horizontalOffset)
                        Spacer(Modifier.height(28.dp))
                        Artwork(track, Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { translationX = horizontalOffset; scaleX = artworkScale; scaleY = artworkScale }.border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(36.dp)).shadow(artworkShadow, RoundedCornerShape(36.dp), ambientColor = Color.Black.copy(alpha = if (state.isPlaying) .5f else .4f), spotColor = Color.Black.copy(alpha = if (state.isPlaying) .5f else .4f)).shadow(6.dp, RoundedCornerShape(36.dp), ambientColor = palette.primary.copy(alpha = artworkGlow), spotColor = palette.primary.copy(alpha = artworkGlow)), 36.dp, extractPalette = true, onPalette = { palette = it })
                    }
                    Spacer(Modifier.height(6.dp))
                    Spacer(Modifier.weight(1f))
                    PlayerDetails(track, state, scrub, duration, onToggle, onPrevious, onNext, onSeek, onSeeking, { scrub = it }, onSubTap, onSubLongPress, onTimer, onQueue, onFavorite, onArtist, palette.primary, palette.secondary, wide = false, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    track: Track?,
    state: PlayerUiState,
    accent: Color,
    modifier: Modifier,
    alignEnd: Boolean,
    onArtist: (String) -> Unit,
    artistTranslationX: Float = 0f,
) {
    val animatedAccent by animateColorAsState(accent, animationSpec = tween(600), label = "playerAccent")
    val defaultNoArtist = stringResource(R.string.player_no_artist)
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            if (state.currentIndex >= 0) stringResource(R.string.player_track_index, state.currentIndex + 1, state.queue.size) else stringResource(R.string.player_track_index_empty),
            color = animatedAccent,
            fontSize = 9.9.sp,
            fontFamily = PlayerBodyFont,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(1.dp))
        AnimatedContent(
            targetState = track?.artist?.uppercase() ?: defaultNoArtist,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = .96f, animationSpec = tween(180))) togetherWith
                    (fadeOut(tween(140)) + scaleOut(targetScale = .96f, animationSpec = tween(140)))
            },
            label = "artistNameTransition",
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = artistTranslationX }
                .clickable(enabled = track != null) { track?.let { onArtist(it.artist) } },
        ) { artistName ->
            Text(
                artistName,
                color = SonarText,
                fontSize = if (artistName.length > 20) 18.4.sp else 22.4.sp,
                fontFamily = PlayerDisplayFont,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 2,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerDetails(
    track: Track?,
    state: PlayerUiState,
    scrub: Float,
    duration: Long,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeeking: (Boolean) -> Unit,
    onScrubChange: (Float) -> Unit,
    onSubTap: () -> Unit,
    onSubLongPress: () -> Unit,
    onTimer: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onArtist: (String) -> Unit,
    accent: Color,
    secondary: Color,
    wide: Boolean,
    modifier: Modifier,
) {
    val moonTransition = rememberInfiniteTransition(label = "sleepMoon")
    val moonAlpha = if (state.sleepTimer.isActive) {
        moonTransition.animateFloat(
            initialValue = .55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
            label = "sleepMoonAlpha",
        ).value
    } else {
        1f
    }
    val moonScale = if (state.sleepTimer.isActive) {
        moonTransition.animateFloat(
            initialValue = .92f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(1000), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
            label = "sleepMoonScale",
        ).value
    } else {
        1f
    }
    val defaultNoTrack = stringResource(R.string.player_no_track)
    Column(modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        val titleSize = 20.8.sp
        val displayTitle = track?.title?.let { title ->
            if (title.length > 7 && title.contains(' ')) title.replaceFirst(" ", "\n") else title
        } ?: defaultNoTrack
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = displayTitle,
                    transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = .96f, animationSpec = tween(180))) togetherWith (fadeOut(tween(140)) + scaleOut(targetScale = .96f, animationSpec = tween(140))) },
                    label = "trackTitleTransition",
                ) { title ->
                    Text(title, fontSize = titleSize, fontFamily = PlayerDisplayFont, fontWeight = FontWeight.ExtraBold, lineHeight = 22.9.sp, letterSpacing = (-0.5).sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onTimer).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(formatPlayerTime(scrub.toLong()), color = SonarText, style = TextStyle(fontFamily = PlayerBodyFont, fontSize = 15.2.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp, fontFeatureSettings = "tnum"))
                Text("/", color = SonarText.copy(alpha = .5f), style = TextStyle(fontFamily = PlayerBodyFont, fontSize = 12.9.sp, fontFeatureSettings = "tnum"))
                Text(formatPlayerTime(duration), color = SonarText, style = TextStyle(fontFamily = PlayerBodyFont, fontSize = 15.2.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp, fontFeatureSettings = "tnum"))
                if (state.sleepTimer.isActive) Icon(Icons.Rounded.Bedtime, stringResource(R.string.cd_sleep_timer_active), tint = SonarText, modifier = Modifier.size(15.dp).graphicsLayer { alpha = moonAlpha; scaleX = moonScale; scaleY = moonScale })
            }
        }
        if (!wide) {
            PlayerMiddleSection(
                scrub = scrub,
                duration = duration,
                accent = accent,
                onSeek = onSeek,
                onSeeking = onSeeking,
                onScrubChange = onScrubChange,
                enabled = track != null,
            )
        } else {
            PlayerScrubber(scrub, duration, accent, onSeek, onSeeking, onScrubChange, track != null)
        }
        ExpressiveControlGrid(state, onToggle, onPrevious, onNext, onFavorite, onQueue, onSubTap, onSubLongPress, accent, secondary, wide)
    }
}

@Composable
private fun PlayerMiddleSection(
    scrub: Float,
    duration: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    onSeeking: (Boolean) -> Unit,
    onScrubChange: (Float) -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        PlayerScrubber(scrub, duration, accent, onSeek, onSeeking, onScrubChange, enabled)
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun PlayerScrubber(
    value: Float,
    duration: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    onSeeking: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
) {
    val safeDuration = duration.coerceAtLeast(1L).toFloat()
    var dragging by remember { mutableStateOf(false) }
    var pendingValue by remember(value) { mutableFloatStateOf(value) }
    val thumbScale by animateFloatAsState(if (dragging) 1.25f else 1f, animationSpec = tween(200, easing = SpringBounce), label = "sliderThumbZoom")
    val animatedAccent by animateColorAsState(accent, animationSpec = tween(600, easing = ExpressiveEasing), label = "sliderAccent")
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp).height(18.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 10.dp.toPx()
            val centerY = size.height / 2f
            val progress = (value / safeDuration).coerceIn(0f, 1f)
            drawRoundRect(
                color = Color(0xFF585E65),
                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
            )
            drawRoundRect(
                color = animatedAccent.copy(alpha = .45f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight * .7f),
                size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight * 1.4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight * .7f),
            )
            drawRoundRect(
                color = animatedAccent,
                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
            )
            val thumbWidth = 24.dp.toPx() * thumbScale
            val thumbHeight = 20.dp.toPx() * thumbScale
            drawRoundRect(
                color = Color.Black.copy(alpha = .4f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * progress - thumbWidth / 2f, centerY - thumbHeight / 2f + 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbHeight / 2f),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * progress - thumbWidth / 2f, centerY - thumbHeight / 2f),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbHeight / 2f),
            )
        }
        Slider(
            value = value,
            onValueChange = { next -> pendingValue = next; dragging = true; onSeeking(true); onValueChange(next) },
            onValueChangeFinished = { onSeek(pendingValue.toLong()); dragging = false; onSeeking(false) },
            valueRange = 0f..safeDuration,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ExpressiveControlGrid(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onQueue: () -> Unit,
    onSubTap: () -> Unit,
    onSubLongPress: () -> Unit,
    accent: Color,
    secondary: Color,
    wide: Boolean,
) {
    val sideWidth = if (wide) 120.dp else 96.dp
    val playOffset by animateDpAsState(if (state.isPlaying) 0.dp else (-5).dp, animationSpec = tween(300, easing = SpringBounce), label = "playOffset")
    val playBlackShadow = if (state.isPlaying) 12.dp else 20.dp
    val playColorShadow = if (state.isPlaying) 0.dp else 10.dp
    val favPlaylistName = stringResource(R.string.default_favorites_playlist)
    val favDesc = if (state.isFavorite) stringResource(R.string.cd_remove_favorite, favPlaylistName) else stringResource(R.string.cd_add_favorite, favPlaylistName)
    Row(Modifier.fillMaxWidth().height(if (wide) 190.dp else 154.dp), horizontalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 10.dp)) {
        Column(Modifier.width(sideWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveButton(secondary, if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, favDesc, Modifier.weight(1f), Color.White, onFavorite, iconSize = if (wide) 46.dp else 38.dp)
            Row(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DualModeControl(state.subControlMode, state.shuffle, state.repeatMode, accent, onSubTap, onSubLongPress, Modifier.weight(1f))
                ExpressiveButton(Color.White.copy(alpha = .86f), Icons.Rounded.LibraryMusic, stringResource(R.string.cd_queue), Modifier.weight(1f), Color(0xFF111318), onQueue, corner = 14.dp, iconSize = 22.dp)
            }
        }
        ExpressiveButton(Color.White, if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.cd_play_or_pause), Modifier.weight(1f).fillMaxSize().offset(y = playOffset), Color(0xFF111318), onToggle, corner = 38.dp, iconSize = if (wide) 72.dp else 56.dp, blackShadow = playBlackShadow, colorShadow = playColorShadow, borderColor = Color.White.copy(alpha = if (state.isPlaying) .8f else .9f))
        Column(Modifier.width(sideWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveButton(accent, Icons.Rounded.SkipNext, stringResource(R.string.cd_next), Modifier.weight(1f), Color(0xFF111318), onNext, iconSize = 34.dp, pressedColor = accent)
            ExpressiveButton(accent, Icons.Rounded.SkipPrevious, stringResource(R.string.cd_previous), Modifier.weight(1f), Color(0xFF111318), onPrevious, iconSize = 34.dp, pressedColor = accent)
        }
    }
}

@Composable
private fun SettingsScreen(
    highResolution: Boolean,
    resumeAfterFocusLoss: Boolean,
    hapticFeedback: Boolean,
    volume: Float,
    outputDescription: String,
    sessionId: Int,
    currentLanguage: AppLanguage,
    importing: Boolean,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onHighResolution: (Boolean) -> Unit,
    onResumeAfterFocusLoss: (Boolean) -> Unit,
    onHapticFeedback: (Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onVolume: (Float) -> Unit,
) {
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Header(
                stringResource(R.string.settings_title),
                stringResource(R.string.settings_subtitle_audio_session),
                { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.btn_back)) } },
            ) {}
            SettingSwitch(
                stringResource(R.string.setting_high_res_title),
                stringResource(R.string.setting_high_res_desc),
                highResolution,
                onHighResolution,
            )
            SettingSwitch(
                stringResource(R.string.setting_audio_focus_title),
                stringResource(R.string.setting_audio_focus_desc),
                resumeAfterFocusLoss,
                onResumeAfterFocusLoss,
            )
            SettingSwitch(
                stringResource(R.string.setting_haptic_feedback_title),
                stringResource(R.string.setting_haptic_feedback_desc),
                hapticFeedback,
                onHapticFeedback,
            )
            SettingLanguageRow(current = currentLanguage, onSelect = onLanguage)
            SectionLabel(stringResource(R.string.section_volume), Modifier.padding(top = 16.dp))
            Slider(
                value = volume,
                onValueChange = onVolume,
                colors = SliderDefaults.colors(
                    thumbColor = SonarControlSurface,
                    activeTrackColor = SonarControlSurface,
                    inactiveTrackColor = Color.White.copy(alpha = .2f),
                ),
            )
            HorizontalDivider(color = SonarOutline, modifier = Modifier.padding(vertical = 14.dp))
            SectionLabel(stringResource(R.string.section_current_output), Modifier.padding(bottom = 8.dp))
            Text(outputDescription, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.output_negotiated_desc), color = SonarMuted, fontSize = 12.sp)
            Text(stringResource(R.string.output_session_id, sessionId), color = SonarMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = onImport,
                enabled = !importing,
                colors = ButtonDefaults.buttonColors(containerColor = SonarControlSurface, contentColor = SonarControlContent),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) {
                Text(if (importing) stringResource(R.string.state_scanning_caps) else stringResource(R.string.btn_select_audio_folder))
            }
            Spacer(Modifier.weight(1f))
            Surface(
                color = Color.White.copy(alpha = .06f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAbout),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_about_app), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text(stringResource(R.string.about_app_subtitle), color = SonarMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.btn_about_app), tint = SonarMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingLanguageRow(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabelRes = when (current) {
        AppLanguage.SYSTEM -> R.string.lang_system
        AppLanguage.RU -> R.string.lang_ru
        AppLanguage.EN -> R.string.lang_en
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = true }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_app_language_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.setting_app_language_desc), color = SonarMuted, fontSize = 12.sp)
        }
        Box {
            Surface(
                color = Color.White.copy(alpha = .08f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(currentLabelRes),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = SonarMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(SonarSurface, RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = .14f)), RoundedCornerShape(16.dp))
                    .padding(4.dp),
            ) {
                val languages = listOf(
                    AppLanguage.SYSTEM to R.string.lang_system,
                    AppLanguage.RU to R.string.lang_ru,
                    AppLanguage.EN to R.string.lang_en,
                )
                languages.forEach { (lang, labelRes) ->
                    val selected = current == lang
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    color = if (selected) SonarControlSurface else Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                )
                                if (selected) {
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = "✓",
                                        color = SonarControlSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(lang)
                            expanded = false
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color.White.copy(alpha = .08f) else Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(
    context: Context,
    onBack: () -> Unit,
) {
    Scaffold(containerColor = SonarElevated) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(
                title = stringResource(R.string.about_title),
                subtitle = stringResource(R.string.about_subtitle),
                leading = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.btn_back)) } },
                actions = {},
            )
            Spacer(Modifier.height(30.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssetImage("logo2.png", "SONAR logo", Modifier.size(28.dp), ColorFilter.tint(Color.White))
                Text(
                    "SONAR",
                    color = Color.White,
                    fontFamily = SonarLogoFont,
                    fontSize = 35.2.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.about_version), fontFamily = RubikFont, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.about_app_subtitle), color = SonarMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(32.dp))
            Surface(
                color = Color.White.copy(alpha = .06f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/norealist/sonar")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(stringResource(R.string.about_source_code), fontFamily = RubikFont, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "https://github.com/norealist/sonar",
                        color = SonarMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = SonarMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = SonarControlContent,
                checkedTrackColor = SonarControlSurface,
                checkedBorderColor = SonarControlSurface,
                uncheckedThumbColor = SonarMuted,
                uncheckedTrackColor = Color.White.copy(alpha = .12f),
                uncheckedBorderColor = Color.White.copy(alpha = .25f),
            ),
        )
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.6.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            subtitle?.let { Text(it, color = SonarMuted, fontSize = 10.4.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Surface(color = Color.White.copy(alpha = .1f), shape = androidx.compose.foundation.shape.CircleShape) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.btn_close), modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun QueueSheet(state: PlayerUiState, onSelect: (Track, Boolean) -> Unit, onClose: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.activeSheet, state.selectedTrack?.id, state.queue) {
        val currentIndex = state.queue.indexOfFirst { it.id == state.selectedTrack?.id }
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val countText = pluralStringResource(R.plurals.queue_track_count, state.queue.size, state.queue.size)
        SheetHeader(stringResource(R.string.queue_title), countText, onClose)
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = listState) { items(state.queue, key = { it.id }) { track ->
            Surface(
                color = if (track.id == state.selectedTrack?.id) Color.White.copy(alpha = .24f) else Color.White.copy(alpha = .05f),
                shape = RoundedCornerShape(16.dp),
                border = if (track.id == state.selectedTrack?.id) BorderStroke(1.dp, Color.White.copy(alpha = .35f)) else null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(track, true) },
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(track.title, color = Color.White, fontSize = 15.2.sp, fontWeight = if (track.id == state.selectedTrack?.id) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Color.White.copy(alpha = if (track.id == state.selectedTrack?.id) .9f else .7f), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } }
    }
}

@Composable
private fun SleepSheet(
    timer: com.sonar.app.data.SleepTimerState,
    onSet: (Int) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var custom by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    LaunchedEffect(timer.expiresAtEpochMs) {
        while (timer.isActive) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SheetHeader(stringResource(R.string.sleep_timer_title), onClose = onClose)
        if (timer.isActive) {
            Surface(color = Color(0xFFB3261E).copy(alpha = .2f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = .35f)), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sleep_timer_stopping_in), color = SonarMuted, fontSize = 12.sp)
                        Text(formatMs(timer.remainingMs(now)), color = Color(0xFFFFB4AB), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(onClick = onCancel) { Text(stringResource(R.string.btn_turn_off)) }
                }
            }
        } else {
            Text(stringResource(R.string.sleep_timer_stopping_in), color = SonarMuted)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(5, 10, 15).forEach { minutes ->
                        SleepOptionButton(stringResource(R.string.sleep_option_minutes, minutes), Modifier.weight(1f)) { onSet(minutes) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(30, 45, 60).forEach { minutes ->
                        val label = if (minutes == 60) stringResource(R.string.sleep_option_one_hour) else stringResource(R.string.sleep_option_minutes, minutes)
                        SleepOptionButton(label, Modifier.weight(1f)) { onSet(minutes) }
                    }
                }
                if (!showCustom) {
                    SleepOptionButton(stringResource(R.string.sleep_option_custom), Modifier.fillMaxWidth()) { showCustom = true }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(custom, { custom = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.sleep_custom_minutes_label)) }, modifier = Modifier.weight(1f), singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { custom.toIntOrNull()?.let { onSet(it.coerceIn(1, 600)); custom = "" } }) { Text(stringResource(R.string.btn_ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepOptionButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .08f), contentColor = Color.White),
    ) { Text(text, fontSize = 14.7.sp, fontWeight = FontWeight.SemiBold) }
}
