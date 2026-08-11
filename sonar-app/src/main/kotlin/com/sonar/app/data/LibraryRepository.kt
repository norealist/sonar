package com.sonar.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

interface LibraryRepository {
    val snapshot: StateFlow<LibrarySnapshot>

    suspend fun addTracks(tracks: List<Track>)
    suspend fun removeTrack(trackId: String)
    suspend fun setFavorite(trackId: String, favorite: Boolean)
}

class InMemoryLibraryRepository(
    initial: LibrarySnapshot = LibrarySnapshot(),
) : LibraryRepository {
    private val mutable = MutableStateFlow(initial)
    override val snapshot: StateFlow<LibrarySnapshot> = mutable.asStateFlow()

    override suspend fun addTracks(tracks: List<Track>) {
        val byId = LinkedHashMap<String, Track>()
        mutable.value.tracks.forEach { byId[it.id] = it }
        tracks.forEach { byId[it.id] = it }
        mutable.value = mutable.value.copy(tracks = byId.values.toList())
    }

    override suspend fun removeTrack(trackId: String) {
        mutable.value = mutable.value.copy(
            tracks = mutable.value.tracks.filterNot { it.id == trackId },
            favoriteTrackIds = mutable.value.favoriteTrackIds - trackId,
        )
    }

    override suspend fun setFavorite(trackId: String, favorite: Boolean) {
        mutable.value = mutable.value.copy(
            favoriteTrackIds = if (favorite) mutable.value.favoriteTrackIds + trackId else mutable.value.favoriteTrackIds - trackId,
        )
    }
}

class PersistentLibraryRepository(context: Context) : LibraryRepository {
    private val preferences = context.applicationContext.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val delegate = InMemoryLibraryRepository(load())

    override val snapshot: StateFlow<LibrarySnapshot> = delegate.snapshot

    override suspend fun addTracks(tracks: List<Track>) = persist { delegate.addTracks(tracks) }

    override suspend fun removeTrack(trackId: String) = persist { delegate.removeTrack(trackId) }

    override suspend fun setFavorite(trackId: String, favorite: Boolean) = persist {
        delegate.setFavorite(trackId, favorite)
    }

    private suspend fun persist(operation: suspend () -> Unit) {
        operation()
        val state = delegate.snapshot.value
        preferences.edit()
            .putString("tracks", JSONArray(state.tracks.map { it.toJson() }).toString())
            .putString("favorites", JSONArray(state.favoriteTrackIds.toList()).toString())
            .apply()
    }

    private fun load(): LibrarySnapshot {
        val tracks = runCatching {
            val array = JSONArray(preferences.getString("tracks", "[]"))
            List(array.length()) { index -> Track.fromJson(array.getJSONObject(index)) }
        }.getOrDefault(emptyList())
        val favorites = runCatching {
            val array = JSONArray(preferences.getString("favorites", "[]"))
            List(array.length()) { index -> array.getString(index) }.toSet()
        }.getOrDefault(emptySet())
        return LibrarySnapshot(tracks, favorites)
    }
}

private fun Track.toJson() = JSONObject().apply {
    put("id", id)
    put("uri", uri)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("durationMs", durationMs)
    put("artworkPath", artworkPath)
    put("codec", codec)
    put("bitrateKbps", bitrateKbps)
    put("sampleRate", sampleRate)
    put("sourceBitDepth", sourceBitDepth)
    put("createdAt", createdAt)
}

private fun Track.Companion.fromJson(value: JSONObject) = Track(
    id = value.getString("id"),
    uri = value.getString("uri"),
    title = value.getString("title"),
    artist = value.getString("artist"),
    album = value.getString("album"),
    durationMs = value.optLong("durationMs"),
    artworkPath = value.optString("artworkPath").takeIf { it.isNotBlank() && it != "null" },
    codec = value.optString("codec").takeIf { it.isNotBlank() && it != "null" },
    bitrateKbps = value.optInt("bitrateKbps").takeIf { it > 0 },
    sampleRate = value.optInt("sampleRate").takeIf { it > 0 },
    sourceBitDepth = value.optInt("sourceBitDepth").takeIf { it > 0 },
    createdAt = value.optLong("createdAt", System.currentTimeMillis()),
)
