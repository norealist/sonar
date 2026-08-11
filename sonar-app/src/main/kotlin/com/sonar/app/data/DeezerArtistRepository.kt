package com.sonar.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DeezerArtistRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val artistCacheDir = File(appContext.filesDir, "deezer/artists")

    fun cachedArtist(artist: String): DeezerArtistInfo? = readCachedArtist(artist)?.info

    suspend fun loadArtist(artist: String): DeezerArtistState = withContext(Dispatchers.IO) {
        val cached = readCachedArtist(artist)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < STATS_TTL_MS) {
            return@withContext DeezerArtistState.Success(
                cached.info.copy(cachedPicturePath = ensurePicture(cached.info)),
            )
        }

        try {
            val fresh = fetchArtist(artist)
            saveCachedArtist(artist, fresh)
            DeezerArtistState.Success(fresh.copy(cachedPicturePath = ensurePicture(fresh)))
        } catch (error: Exception) {
            DeezerArtistState.Error(error.message, cached?.info)
        }
    }

    private fun fetchArtist(artist: String): DeezerArtistInfo {
        val search = getJson(
            "https://api.deezer.com/search/artist?q=${Uri.encode(artist)}",
        )
        val result = search.getJSONArray("data").findExactArtist(artist)
            ?: throw IOException("Deezer artist not found: $artist")
        val artistId = result.getLong("id")
        val details = getJson("https://api.deezer.com/artist/$artistId")
        return DeezerArtistInfo(
            id = details.getLong("id"),
            name = details.getString("name"),
            link = details.optString("link").ifBlank { "https://www.deezer.com/artist/$artistId" },
            pictureXlUrl = details.optString("picture_xl"),
            nbAlbum = details.optInt("nb_album"),
            nbFan = details.optLong("nb_fan"),
            cachedPicturePath = null,
        )
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Deezer HTTP $status")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun ensurePicture(info: DeezerArtistInfo): String? {
        if (info.pictureXlUrl.isBlank()) return null
        artistCacheDir.mkdirs()
        val imageFile = File(artistCacheDir, "${info.id}.jpg")
        if (imageFile.isFile && imageFile.length() > 0L) return imageFile.absolutePath

        val temporaryFile = File(artistCacheDir, "${info.id}.jpg.tmp")
        val connection = (URL(info.pictureXlUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input -> temporaryFile.outputStream().use { output -> input.copyTo(output) } }
            if (!temporaryFile.renameTo(imageFile)) {
                temporaryFile.delete()
                null
            } else {
                imageFile.absolutePath
            }
        } catch (_: Exception) {
            temporaryFile.delete()
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readCachedArtist(artist: String): CachedArtist? {
        val key = cacheKey(artist)
        val id = preferences.getLong("$key.id", -1L)
        val fetchedAt = preferences.getLong("$key.fetchedAt", 0L)
        val name = preferences.getString("$key.name", null)
        val link = preferences.getString("$key.link", null)
        val pictureXlUrl = preferences.getString("$key.pictureXlUrl", null)
        if (id < 0L || fetchedAt <= 0L || name.isNullOrBlank() || link.isNullOrBlank()) return null

        return CachedArtist(
            fetchedAt = fetchedAt,
            info = DeezerArtistInfo(
                id = id,
                name = name,
                link = link,
                pictureXlUrl = pictureXlUrl.orEmpty(),
                nbAlbum = preferences.getInt("$key.nbAlbum", 0),
                nbFan = preferences.getLong("$key.nbFan", 0L),
                cachedPicturePath = File(artistCacheDir, "$id.jpg").takeIf { it.isFile && it.length() > 0L }?.absolutePath,
            ),
        )
    }

    private fun saveCachedArtist(artist: String, info: DeezerArtistInfo) {
        val key = cacheKey(artist)
        preferences.edit()
            .putLong("$key.id", info.id)
            .putLong("$key.fetchedAt", System.currentTimeMillis())
            .putString("$key.name", info.name)
            .putString("$key.link", info.link)
            .putString("$key.pictureXlUrl", info.pictureXlUrl)
            .putInt("$key.nbAlbum", info.nbAlbum)
            .putLong("$key.nbFan", info.nbFan)
            .apply()
    }

    private fun cacheKey(artist: String): String = "artist_${Uri.encode(normalizeArtistName(artist))}"

    private fun normalizeArtistName(artist: String): String =
        artist.trim().replace(Regex("\\s+"), " ").lowercase()

    private data class CachedArtist(
        val fetchedAt: Long,
        val info: DeezerArtistInfo,
    )

    private fun JSONArray.findExactArtist(artist: String): JSONObject? {
        val target = artist.trim().replace(Regex("\\s+"), " ").lowercase()
        for (index in 0 until length()) {
            val candidate = optJSONObject(index) ?: continue
            val name = candidate.optString("name").trim().replace(Regex("\\s+"), " ").lowercase()
            if (name == target) return candidate
        }
        return null
    }

    private companion object {
        const val PREFERENCES_NAME = "deezer_artist_cache"
        const val STATS_TTL_MS = 15 * 60 * 1000L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
