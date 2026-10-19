package com.example.offlinemusic.downloader.engine

import com.example.offlinemusic.downloader.models.SpotifyPlaylistInfo
import com.example.offlinemusic.downloader.models.SpotifyTrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyPlaylistClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val SPOTIFY_URL_PATTERN = Pattern.compile("playlist/([a-zA-Z0-9]+)")

    fun isSpotifyPlaylistUrl(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.contains("spotify.com/playlist/") ||
                trimmed.startsWith("spotify:playlist:") ||
                (trimmed.contains("spotify") && trimmed.contains("playlist")) ||
                (trimmed.length in 15..32 && trimmed.all { it.isLetterOrDigit() } && !trimmed.contains(" "))
    }

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()

        // 1. spotify:playlist:ID format
        if (trimmed.startsWith("spotify:playlist:")) {
            val rest = trimmed.removePrefix("spotify:playlist:")
            val id = rest.takeWhile { it.isLetterOrDigit() }
            if (id.isNotBlank()) return id
        }

        // 2. https://open.spotify.com/playlist/<id>?si=...
        val matcher = SPOTIFY_URL_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            val id = matcher.group(1)
            if (!id.isNullOrBlank()) return id
        }

        // 3. Raw Spotify ID
        if (trimmed.length in 15..32 && trimmed.all { it.isLetterOrDigit() }) {
            return trimmed
        }

        return null
    }

    suspend fun fetchPlaylist(playlistId: String): Result<SpotifyPlaylistInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://spotify.xwolf.space/api/playlist/$playlistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Spotify API returned status ${resp.code}"))
                }

                val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty Spotify response"))
                val root = JSONObject(body)

                if (root.optBoolean("success", true) == false) {
                    val err = root.optString("error", "Unknown Spotify API error")
                    return@withContext Result.failure(Exception(err))
                }

                val plObj = root.optJSONObject("playlist")
                    ?: return@withContext Result.failure(Exception("Missing playlist data in response"))

                val id = plObj.optString("id", playlistId)
                val name = plObj.optString("name", "Spotify Playlist")
                val description = plObj.optString("description", "").takeIf { it.isNotBlank() }
                val owner = plObj.optString("owner", "Spotify User").takeIf { it.isNotBlank() }
                val thumbnail = plObj.optString("thumbnail", "").takeIf { it.isNotBlank() }
                val totalTracks = plObj.optInt("total_tracks", 0)

                val tracksArray = plObj.optJSONArray("tracks")
                val trackList = mutableListOf<SpotifyTrackInfo>()

                if (tracksArray != null) {
                    for (i in 0 until tracksArray.length()) {
                        val tObj = tracksArray.getJSONObject(i)
                        val tId = tObj.optString("id", i.toString())
                        val title = tObj.optString("title", "Unknown Title")
                            .replace('\u00a0', ' ')
                            .trim()
                        val artist = tObj.optString("artist", "Unknown Artist")
                            .replace('\u00a0', ' ')
                            .trim()
                        val durationMs = if (tObj.has("duration_ms")) tObj.optLong("duration_ms") else null

                        trackList.add(
                            SpotifyTrackInfo(
                                id = tId,
                                title = title,
                                artist = artist,
                                durationMs = durationMs
                            )
                        )
                    }
                }

                Result.success(
                    SpotifyPlaylistInfo(
                        id = id,
                        name = name,
                        description = description,
                        owner = owner,
                        thumbnail = thumbnail,
                        totalTracks = if (totalTracks > 0) totalTracks else trackList.size,
                        tracks = trackList
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
