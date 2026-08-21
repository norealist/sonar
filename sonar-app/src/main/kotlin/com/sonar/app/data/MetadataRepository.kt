package com.sonar.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkPath: String?,
    val codec: String?,
    val bitrateKbps: Int?,
    val sourceBitDepth: Int?,
    val sampleRate: Int?,
)

interface MetadataRepository {
    suspend fun extract(uri: Uri, fallbackName: String): ExtractedMetadata
}

class MediaMetadataRepository(context: Context) : MetadataRepository {
    private val appContext = context.applicationContext

    override suspend fun extract(uri: Uri, fallbackName: String): ExtractedMetadata = withContext(Dispatchers.IO) {
        val fallbackTitle = fallbackName.substringBeforeLast('.', fallbackName).ifBlank { "Untitled" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallbackTitle
            val artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown artist"
            val album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown album"
            val duration = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            ExtractedMetadata(
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                artworkPath = null,
                codec = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?.substringAfterLast('/')?.uppercase(),
                bitrateKbps = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000),
                sourceBitDepth = if (Build.VERSION.SDK_INT >= 29) {
                    retriever.metadata(METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                } else {
                    null
                },
                sampleRate = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull(),
            )
        } catch (_: Throwable) {
            ExtractedMetadata(fallbackTitle, "Unknown artist", "Unknown album", 0L, null, null, null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.metadata(key: Int): String? =
        extractMetadata(key)?.takeIf { it.isNotBlank() }

    private companion object {
        const val METADATA_KEY_BITS_PER_SAMPLE = 39
    }
}
