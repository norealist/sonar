package com.sonar.app.ui

import com.sonar.app.data.Track

data class ArtistSingle(
    val artist: String,
    val title: String,
    val coverAsset: String,
    val trackId: String? = null,
)

object ArtistCatalog {
    private val paxnkoXd = listOf(
        "proxy" to "singles/PAXNKOXD/PAXNKOXD - proxy.jpg",
        "bitcrushed tears" to "singles/PAXNKOXD/PAXNKOXD - bitcrushed tears.jpg",
        "derealization" to "singles/PAXNKOXD/PAXNKOXD - derealization.jpg",
        "fly me to the m00n" to "singles/PAXNKOXD/PAXNKOXD - fly me to the m00n.jpg",
        "collapse" to "singles/PAXNKOXD/PAXNKOXD - collapse.jpg",
        "broken promise" to "singles/PAXNKOXD/PAXNKOXD - broken promise.jpg",
        "my love" to "singles/PAXNKOXD/PAXNKOXD - my love.jpg",
    )
    private val oneNonly = listOf(
        "Split" to "singles/1nonly/1nonly - Split.jpg",
        "GRAILED" to "singles/1nonly/1nonly - GRAILED.jpg",
        "Meaningless Love" to "singles/1nonly/1nonly - Meaningless Love.jpg",
        "Mine" to "singles/1nonly/1nonly - Mine.jpg",
        "Stay With Me" to "singles/1nonly/1nonly - Stay With Me.jpg",
    )

    fun singlesFor(artist: String): List<ArtistSingle> {
        val source = when {
            artist.equals("PAXNKOXD", ignoreCase = true) -> paxnkoXd
            artist.equals("1nonly", ignoreCase = true) -> oneNonly
            else -> emptyList()
        }
        return source.map { (title, cover) -> ArtistSingle(artist, title, cover) }
    }

    fun singlesForTracks(artist: String, tracks: List<Track>): List<ArtistSingle> {
        return tracks.map { track -> ArtistSingle(artist, track.title, track.artworkPath.orEmpty(), track.id) }
    }

    fun heroAssetFor(artist: String): String? = when {
        artist.equals("PAXNKOXD", ignoreCase = true) -> "artists/PAXNKOXD.jpg"
        artist.equals("1nonly", ignoreCase = true) -> "artists/1nonly.jpg"
        else -> null
    }

    fun statsFor(artist: String): Pair<String, String> = when {
        artist.equals("1nonly", ignoreCase = true) -> "39.8k" to "69"
        artist.equals("PAXNKOXD", ignoreCase = true) -> "67" to "33"
        else -> "67" to "33"
    }
}
