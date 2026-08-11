package com.sonar.app.data

data class DeezerArtistInfo(
    val id: Long,
    val name: String,
    val link: String,
    val pictureXlUrl: String,
    val nbAlbum: Int,
    val nbFan: Long,
    val cachedPicturePath: String?,
)

sealed interface DeezerArtistState {
    data object Idle : DeezerArtistState

    data class Loading(val cached: DeezerArtistInfo? = null) : DeezerArtistState

    data class Success(val info: DeezerArtistInfo) : DeezerArtistState

    data class Error(
        val message: String? = null,
        val cached: DeezerArtistInfo? = null,
    ) : DeezerArtistState
}

val DeezerArtistState.info: DeezerArtistInfo?
    get() = when (this) {
        DeezerArtistState.Idle -> null
        is DeezerArtistState.Loading -> cached
        is DeezerArtistState.Success -> info
        is DeezerArtistState.Error -> cached
    }
