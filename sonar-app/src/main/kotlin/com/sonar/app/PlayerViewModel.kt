package com.sonar.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonar.app.data.AppScreen
import com.sonar.app.data.DeezerArtistRepository
import com.sonar.app.data.DeezerArtistState
import com.sonar.app.data.primaryArtist
import com.sonar.app.data.LibraryRepository
import com.sonar.app.data.MediaMetadataRepository
import com.sonar.app.data.PersistentLibraryRepository
import com.sonar.app.data.PersistentSettingsRepository
import com.sonar.app.data.SettingsRepository
import com.sonar.app.data.Sheet
import com.sonar.app.data.Track
import com.sonar.app.player.AppPlayerController
import com.sonar.app.player.PlayerControllerHolder
import com.sonar.app.player.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val library: LibraryRepository = PersistentLibraryRepository(application)
    val settings: SettingsRepository = PersistentSettingsRepository(application)
    private val metadata = MediaMetadataRepository(application)
    private val deezerArtists = DeezerArtistRepository(application)
    val controller = PlayerControllerHolder.getOrCreate(application, settings)

    private val mutableScreen = MutableStateFlow(AppScreen.LIBRARY)
    private val mutableArtist = MutableStateFlow<String?>(null)
    private val mutableImporting = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableLastSelectedTrack = MutableStateFlow<Track?>(null)
    private val mutableDeezerArtist = MutableStateFlow<DeezerArtistState>(DeezerArtistState.Idle)
    private var deezerJob: Job? = null

    val screen: StateFlow<AppScreen> = mutableScreen.asStateFlow()
    val selectedArtist: StateFlow<String?> = mutableArtist.asStateFlow()
    val isImporting: StateFlow<Boolean> = mutableImporting.asStateFlow()
    val error: StateFlow<String?> = mutableError.asStateFlow()
    val player: StateFlow<PlayerUiState> = controller.state
    val lastSelectedTrack: StateFlow<Track?> = mutableLastSelectedTrack.asStateFlow()
    val deezerArtist: StateFlow<DeezerArtistState> = mutableDeezerArtist.asStateFlow()

    init {
        viewModelScope.launch {
            library.snapshot.collectLatest { snapshot ->
                controller.setQueue(snapshot.tracks, settings.current.selectedTrackId)
                controller.setFavorite(controller.state.value.selectedTrack?.let { it.id in snapshot.favoriteTrackIds } ?: false)
                settings.current.selectedTrackId?.let { selectedId ->
                    snapshot.tracks.firstOrNull { it.id == selectedId }?.let { mutableLastSelectedTrack.value = it }
                }
            }
        }
    }

    fun navigate(screen: AppScreen) {
        mutableScreen.value = screen
    }

    fun openArtist(artist: String) {
        val selectedArtist = primaryArtist(artist)
        mutableArtist.value = selectedArtist
        deezerJob?.cancel()
        mutableDeezerArtist.value = DeezerArtistState.Loading(deezerArtists.cachedArtist(selectedArtist))
        deezerJob = viewModelScope.launch {
            mutableDeezerArtist.value = deezerArtists.loadArtist(selectedArtist)
        }
        mutableScreen.value = AppScreen.ARTIST
    }

    fun importDirectory(treeUri: Uri) {
        viewModelScope.launch {
            mutableImporting.value = true
            mutableError.value = null
            try {
                takePersistablePermission(treeUri)
                val candidates = withContext(Dispatchers.IO) { scanAudioFiles(treeUri) }
                if (candidates.isEmpty()) {
                    error(getApplication<Application>().getString(R.string.err_no_audio_found))
                }
                importCandidates(candidates)
            } catch (failure: Throwable) {
                mutableError.value = failure.message ?: getApplication<Application>().getString(R.string.err_import_failed)
            } finally {
                mutableImporting.value = false
            }
        }
    }

    fun setLanguage(language: com.sonar.app.data.AppLanguage) {
        settings.update { it.copy(language = language) }
        LocaleHelper.applyLanguage(language)
    }

    private suspend fun importCandidates(candidates: List<AudioCandidate>) {
        val tracks = withContext(Dispatchers.IO) {
            candidates.map { candidate ->
                val extracted = metadata.extract(candidate.uri, candidate.name)
                Track(
                    id = candidate.uri.toString(),
                    uri = candidate.uri.toString(),
                    title = extracted.title,
                    artist = extracted.artist,
                    album = extracted.album,
                    durationMs = extracted.durationMs,
                    artworkPath = extracted.artworkPath,
                    codec = extracted.codec,
                    bitrateKbps = extracted.bitrateKbps,
                    sourceBitDepth = extracted.sourceBitDepth,
                    sampleRate = extracted.sampleRate,
                    createdAt = System.currentTimeMillis(),
                )
            }
        }
        library.addTracks(tracks)
    }

    fun removeTrack(track: Track) {
        viewModelScope.launch {
            library.removeTrack(track.id)
            if (controller.state.value.selectedTrack?.id == track.id) controller.stop()
        }
    }

    fun selectTrack(track: Track, autoplay: Boolean = true) {
        mutableLastSelectedTrack.value = track
        controller.selectTrack(track, autoplay)
        controller.setFavorite(track.id in library.snapshot.value.favoriteTrackIds)
    }

    fun playTrack(track: Track, queue: List<Track> = library.snapshot.value.tracks) {
        controller.setQueue(queue, track.id)
        selectTrack(track)
        mutableScreen.value = AppScreen.PLAYER
    }

    fun openPlayer() {
        val track = controller.state.value.selectedTrack
            ?: settings.current.selectedTrackId?.let { selectedId ->
                library.snapshot.value.tracks.firstOrNull { it.id == selectedId }
            }
        if (track != null && controller.state.value.selectedTrack?.id != track.id) {
            mutableLastSelectedTrack.value = track
            controller.selectTrack(track, autoplay = false)
            controller.setFavorite(track.id in library.snapshot.value.favoriteTrackIds)
        }
        mutableScreen.value = AppScreen.PLAYER
    }

    fun toggleFavorite() {
        val track = controller.state.value.selectedTrack ?: return
        val next = track.id !in library.snapshot.value.favoriteTrackIds
        controller.setFavorite(next)
        viewModelScope.launch { library.setFavorite(track.id, next) }
    }

    fun setSheet(sheet: Sheet?) = controller.setSheet(sheet)

    fun toggleHighResolution(enabled: Boolean) = controller.setHighResolution(enabled)

    fun setResumeAfterFocusLoss(enabled: Boolean) {
        settings.update { it.copy(resumeAfterFocusLoss = enabled) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        settings.update { it.copy(hapticFeedback = enabled) }
    }

    private fun takePersistablePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags) }
            .recoverCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
    }

    private fun scanAudioFiles(treeUri: Uri): List<AudioCandidate> {
        val resolver = getApplication<Application>().contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val result = mutableListOf<AudioCandidate>()

        fun scanChildren(childrenUri: Uri) {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    val name = cursor.getString(nameIndex).orEmpty()
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanChildren(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId))
                    } else if (isAudioFile(mime, name)) {
                        result += AudioCandidate(documentUri, name.ifBlank { "audio" })
                    }
                }
            }
        }

        scanChildren(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId))
        return result
    }

    private fun isAudioFile(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/")) return true
        return name.substringAfterLast('.', "").lowercase() in setOf(
            "aac", "alac", "flac", "m4a", "mp3", "oga", "ogg", "opus", "wav", "wma",
        )
    }

    private data class AudioCandidate(val uri: Uri, val name: String)

    override fun onCleared() {
        if (!controller.state.value.isPlaying) {
            controller.release()
            com.sonar.app.player.MediaPlaybackService.stop(getApplication())
        }
        super.onCleared()
    }
}
