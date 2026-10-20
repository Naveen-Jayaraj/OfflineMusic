package com.example.offlinemusic.downloader.engine

import com.example.offlinemusic.downloader.models.SpotifyPlaylistInfo
import com.example.offlinemusic.downloader.models.SpotifyTrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyPlaylistClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val SPOTIFY_URL_PATTERN = Pattern.compile("playlist/([a-zA-Z0-9]+)")
    private val NEXT_DATA_PATTERN = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>")
    private const val SPOTIFY_GRAPHQL_HASH = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"

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

    /**
     * Fetches a complete Spotify playlist with pagination support to retrieve ALL tracks (> 100).
     * Uses Spotify embed page to extract session token and initial 100 tracks,
     * then queries Spotify pathfinder GraphQL endpoint with pagination.
     * Falls back to public mirror if embed is unreachable.
     */
    suspend fun fetchPlaylist(playlistId: String): Result<SpotifyPlaylistInfo> = withContext(Dispatchers.IO) {
        try {
            // Attempt fetching via Spotify Embed + Pathfinder API
            val embedResult = fetchViaSpotifyEmbedAndApi(playlistId)
            if (embedResult.isSuccess && (embedResult.getOrNull()?.tracks?.isNotEmpty() == true)) {
                return@withContext embedResult
            }

            // Fallback to xwolf.space mirror
            fetchViaFallbackMirror(playlistId)
        } catch (e: Exception) {
            fetchViaFallbackMirror(playlistId)
        }
    }

    private fun fetchViaSpotifyEmbedAndApi(playlistId: String): Result<SpotifyPlaylistInfo> {
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                return Result.failure(Exception("Failed to load Spotify embed: HTTP ${resp.code}"))
            }

            val html = resp.body?.string() ?: return Result.failure(Exception("Empty embed HTML"))
            val matcher = NEXT_DATA_PATTERN.matcher(html)
            if (!matcher.find()) {
                return Result.failure(Exception("Could not find __NEXT_DATA__ in embed page"))
            }

            val jsonStr = matcher.group(1) ?: return Result.failure(Exception("Null __NEXT_DATA__ content"))
            val nextData = JSONObject(jsonStr)
            val props = nextData.optJSONObject("props")?.optJSONObject("pageProps") ?: JSONObject()
            val state = props.optJSONObject("state") ?: JSONObject()

            // 1. Extract session token
            val token = state.optJSONObject("settings")
                ?.optJSONObject("session")
                ?.optString("accessToken")

            // 2. Extract Playlist entity info
            val entity = state.optJSONObject("data")?.optJSONObject("entity")
                ?: return Result.failure(Exception("No entity in embed data"))

            val playlistName = entity.optString("name", "Spotify Playlist")
                .replace('\u00a0', ' ').trim()
            val description = entity.optString("subtitle", "").takeIf { it.isNotBlank() }
            val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            val thumbnail = if (coverSources != null && coverSources.length() > 0) {
                coverSources.getJSONObject(coverSources.length() - 1).optString("url")
            } else null

            // 3. Extract initial trackList (up to 100 tracks)
            val allTracks = mutableListOf<SpotifyTrackInfo>()
            val initialTrackList = entity.optJSONArray("trackList")
            if (initialTrackList != null) {
                for (i in 0 until initialTrackList.length()) {
                    val tObj = initialTrackList.getJSONObject(i)
                    val rawUri = tObj.optString("uri", "")
                    val tId = if (rawUri.startsWith("spotify:track:")) rawUri.removePrefix("spotify:track:") else i.toString()
                    val title = tObj.optString("title", "Unknown Title").replace('\u00a0', ' ').trim()
                    val subtitle = tObj.optString("subtitle", "Unknown Artist").replace('\u00a0', ' ').trim()
                    val durationMs = if (tObj.has("duration")) tObj.optLong("duration") else null

                    if (title.isNotBlank()) {
                        allTracks.add(
                            SpotifyTrackInfo(
                                id = tId,
                                title = title,
                                artist = subtitle,
                                durationMs = durationMs
                            )
                        )
                    }
                }
            }

            // 4. If we have a token, paginate using pathfinder API to fetch remaining tracks (> 100)
            if (!token.isNullOrBlank()) {
                var offset = allTracks.size
                var hasMore = true
                var loopSafety = 0

                while (hasMore && loopSafety < 50) { // Limit to 5,000 tracks max
                    loopSafety++
                    val pageTracks = fetchPlaylistPage(playlistId, token, offset, limit = 100)
                    if (pageTracks.isEmpty()) {
                        hasMore = false
                    } else {
                        allTracks.addAll(pageTracks)
                        offset += pageTracks.size
                        if (pageTracks.size < 100) {
                            hasMore = false
                        }
                    }
                }
            }

            return Result.success(
                SpotifyPlaylistInfo(
                    id = playlistId,
                    name = playlistName,
                    description = description,
                    owner = "Spotify",
                    thumbnail = thumbnail,
                    totalTracks = allTracks.size,
                    tracks = allTracks
                )
            )
        }
    }

    private fun fetchPlaylistPage(
        playlistId: String,
        token: String,
        offset: Int,
        limit: Int
    ): List<SpotifyTrackInfo> {
        return try {
            val variablesJson = JSONObject().apply {
                put("uri", "spotify:playlist:$playlistId")
                put("offset", offset)
                put("limit", limit)
            }.toString()

            val extensionsJson = JSONObject().apply {
                put("persistedQuery", JSONObject().apply {
                    put("version", 1)
                    put("sha256Hash", SPOTIFY_GRAPHQL_HASH)
                })
            }.toString()

            val encodedVars = URLEncoder.encode(variablesJson, "UTF-8")
            val encodedExts = URLEncoder.encode(extensionsJson, "UTF-8")
            val url = "https://api-partner.spotify.com/pathfinder/v1/query?operationName=fetchPlaylistContents&variables=$encodedVars&extensions=$encodedExts"

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("app-platform", "WebPlayer")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()

                val bodyStr = resp.body?.string() ?: return emptyList()
                val root = JSONObject(bodyStr)
                val content = root.optJSONObject("data")
                    ?.optJSONObject("playlistV2")
                    ?.optJSONObject("content")
                    ?: return emptyList()

                val items = content.optJSONArray("items") ?: return emptyList()
                val pageTracks = mutableListOf<SpotifyTrackInfo>()

                for (i in 0 until items.length()) {
                    val itemWrapper = items.getJSONObject(i)
                    val trackData = itemWrapper.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                    val title = trackData.optString("name", "").replace('\u00a0', ' ').trim()
                    if (title.isBlank()) continue

                    val artistsArray = trackData.optJSONObject("artists")?.optJSONArray("items")
                    val artistNames = mutableListOf<String>()
                    if (artistsArray != null) {
                        for (a in 0 until artistsArray.length()) {
                            val aObj = artistsArray.getJSONObject(a)
                            val name = aObj.optJSONObject("profile")?.optString("name")
                            if (!name.isNullOrBlank()) {
                                artistNames.add(name.trim())
                            }
                        }
                    }
                    val artistStr = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "Unknown Artist"

                    val uri = trackData.optString("uri", "")
                    val tId = if (uri.startsWith("spotify:track:")) uri.removePrefix("spotify:track:") else "$offset-$i"
                    val durationMs = trackData.optJSONObject("trackDuration")?.optLong("totalMilliseconds")

                    pageTracks.add(
                        SpotifyTrackInfo(
                            id = tId,
                            title = title,
                            artist = artistStr,
                            durationMs = durationMs
                        )
                    )
                }

                pageTracks
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchViaFallbackMirror(playlistId: String): Result<SpotifyPlaylistInfo> {
        return try {
            val url = "https://spotify.xwolf.space/api/playlist/$playlistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(Exception("Spotify API returned status ${resp.code}"))
                }

                val body = resp.body?.string() ?: return Result.failure(Exception("Empty Spotify response"))
                val root = JSONObject(body)

                if (root.optBoolean("success", true) == false) {
                    val err = root.optString("error", "Unknown Spotify API error")
                    return Result.failure(Exception(err))
                }

                val plObj = root.optJSONObject("playlist")
                    ?: return Result.failure(Exception("Missing playlist data in response"))

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
