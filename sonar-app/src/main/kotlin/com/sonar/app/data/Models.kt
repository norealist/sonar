package com.sonar.app.data

import android.net.Uri

const val DEFAULT_PLAYLIST_NAME = "Избранное"

data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val artworkPath: String? = null,
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRate: Int? = null,
    val sourceBitDepth: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object

    val contentUri: Uri
        get() = Uri.parse(uri)
}

data class LibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

enum class SubControlMode {
    SHUFFLE,
    REPEAT,
}

enum class AppScreen {
    LIBRARY,
    PLAYER,
    ARTIST,
    SETTINGS,
}

enum class Sheet {
    QUEUE,
    SLEEP_TIMER,
}

data class AppSettings(
    val highResolutionOutput: Boolean = false,
    val resumeAfterFocusLoss: Boolean = true,
    val volume: Float = 1f,
    val libraryGrid: Boolean = false,
    val gridColumns: Int = 4,
    val artistGrid: Boolean = true,
    val subControlMode: SubControlMode = SubControlMode.SHUFFLE,
    val selectedTrackId: String? = null,
    val selectedIndex: Int = 0,
)

data class SleepTimerState(
    val expiresAtEpochMs: Long? = null,
) {
    val isActive: Boolean
        get() = expiresAtEpochMs != null

    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        (expiresAtEpochMs?.minus(now) ?: 0L).coerceAtLeast(0L)
}

data class AppError(
    val code: Int? = null,
    val message: String,
)
