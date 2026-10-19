package com.example.offlinemusic.downloader.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class EnrichedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val year: Int?,
    val genre: String?,
    val artworkUrl: String?
)

object MetadataEnricher {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val BRACKET_PATTERNS = listOf(
        Pattern.compile("(?i)\\((?:official\\s+)?(?:music\\s+)?video\\)"),
        Pattern.compile("(?i)\\[(?:official\\s+)?(?:music\\s+)?video\\]"),
        Pattern.compile("(?i)\\((?:official\\s+)?audio\\)"),
        Pattern.compile("(?i)\\[(?:official\\s+)?audio\\]"),
        Pattern.compile("(?i)\\((?:official\\s+)?lyric\\s+video\\)"),
        Pattern.compile("(?i)\\[(?:official\\s+)?lyric\\s+video\\]"),
        Pattern.compile("(?i)\\((?:lyrics?|lyric\\s+video)\\)"),
        Pattern.compile("(?i)\\[(?:lyrics?|lyric\\s+video)\\]"),
        Pattern.compile("(?i)\\((?:visualizer|audio|hd|4k|sped\\s+up|slowed\\s*\\+?\\s*reverb)\\)"),
        Pattern.compile("(?i)\\[(?:visualizer|audio|hd|4k|sped\\s+up|slowed\\s*\\+?\\s*reverb)\\]")
    )

    /**
     * Clean YouTube title noise (matching clean_track_title from lucida-flow)
     */
    fun cleanTrackTitle(title: String): String {
        var s = title.replace("\u2013", "-").replace("\u2014", "-").replace("\u2212", "-")

        for (ext in listOf(".flac", ".mp3", ".m4a", ".opus", ".wav", ".aac", ".ogg")) {
            if (s.endsWith(ext, ignoreCase = true)) {
                s = s.substring(0, s.length - ext.length)
            }
        }

        for (pat in BRACKET_PATTERNS) {
            s = pat.matcher(s).replaceAll("")
        }

        return s.trim()
    }

    /**
     * Clean artist channel suffix like "- Topic" or "VEVO"
     */
    fun cleanArtistName(artist: String): String {
        var s = artist
        val topicIdx = s.indexOf(" - Topic", ignoreCase = true)
        if (topicIdx != -1) s = s.substring(0, topicIdx)
        val vevoIdx = s.indexOf("VEVO", ignoreCase = true)
        if (vevoIdx != -1) s = s.substring(0, vevoIdx)
        return s.trim()
    }

    /**
     * Parse artist and title from song title strings like "Artist - Title" or "Title | Artist"
     */
    fun parseArtistAndTitle(rawTitle: String, explicitArtist: String?): Pair<String, String?> {
        val cleaned = cleanTrackTitle(rawTitle)
        val cleanExplicit = explicitArtist?.let { cleanArtistName(it) }?.takeIf {
            it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) && !it.equals("Unknown Channel", ignoreCase = true)
        }

        // Check for "Artist - Title" separator
        val dashIdx = cleaned.indexOf(" - ")
        if (dashIdx != -1) {
            val artPart = cleanArtistName(cleaned.substring(0, dashIdx))
            val titlePart = cleaned.substring(dashIdx + 3).trim()
            if (artPart.isNotBlank() && titlePart.isNotBlank()) {
                return Pair(titlePart, artPart)
            }
        }

        // Check for "Title | Artist" separator
        val pipeIdx = cleaned.indexOf(" | ")
        if (pipeIdx != -1) {
            val titlePart = cleaned.substring(0, pipeIdx).trim()
            val artPart = cleanArtistName(cleaned.substring(pipeIdx + 3))
            if (titlePart.isNotBlank()) {
                return Pair(titlePart, artPart.ifBlank { cleanExplicit })
            }
        }

        return Pair(cleaned, cleanExplicit)
    }

    /**
     * Enrich track metadata using iTunes Search API and MusicBrainz (matching lucida-flow)
     */
    suspend fun enrich(rawTitle: String, rawArtist: String?, fallbackThumbnailUrl: String?): EnrichedMetadata = withContext(Dispatchers.IO) {
        val (parsedTitle, inferredArtist) = parseArtistAndTitle(rawTitle, rawArtist)
        val finalTitle = parsedTitle.ifBlank { rawTitle.trim() }
        val finalArtist = inferredArtist ?: rawArtist?.let { cleanArtistName(it) } ?: "Unknown Artist"

        // 1. Try iTunes Search API (very fast, high quality canonical tags and 600x600 cover art)
        try {
            val searchTerm = if (finalArtist.isNotBlank() && !finalArtist.equals("Unknown Artist", ignoreCase = true)) {
                "$finalArtist $finalTitle"
            } else {
                finalTitle
            }
            val encodedQuery = URLEncoder.encode(searchTerm, "UTF-8")
            val itunesUrl = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=1"

            val req = Request.Builder().url(itunesUrl).header("User-Agent", "Mozilla/5.0").build()
            val respStr = httpClient.newCall(req).execute().use { it.body?.string() ?: "" }
            if (respStr.isNotBlank()) {
                val json = JSONObject(respStr)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    val itunesTitle = item.optString("trackName", finalTitle)
                    val itunesArtist = item.optString("artistName", finalArtist)
                    val itunesAlbum = item.optString("collectionName", "Single")
                    val itunesGenre = item.optString("primaryGenreName").takeIf { it.isNotBlank() }
                    val releaseDate = item.optString("releaseDate", "")
                    val itunesYear = if (releaseDate.length >= 4) releaseDate.substring(0, 4).toIntOrNull() else null
                    val rawArtwork = item.optString("artworkUrl100", "")
                    val highResArt = if (rawArtwork.isNotBlank()) {
                        rawArtwork.replace("100x100bb.jpg", "600x600bb.jpg")
                    } else fallbackThumbnailUrl

                    return@withContext EnrichedMetadata(
                        title = itunesTitle,
                        artist = itunesArtist,
                        album = itunesAlbum,
                        year = itunesYear,
                        genre = itunesGenre,
                        artworkUrl = highResArt
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Try MusicBrainz API
        try {
            val q = if (finalArtist.isNotBlank() && !finalArtist.equals("Unknown Artist", ignoreCase = true)) {
                "recording:\"$finalTitle\" AND artist:\"$finalArtist\""
            } else {
                "\"$finalTitle\""
            }
            val mbUrl = "https://musicbrainz.org/ws/2/recording?query=${URLEncoder.encode(q, "UTF-8")}&fmt=json&limit=1"
            val mbReq = Request.Builder()
                .url(mbUrl)
                .header("User-Agent", "OfflineMusic/1.0 (contact@offlinemusic.app)")
                .header("Accept", "application/json")
                .build()

            val mbStr = httpClient.newCall(mbReq).execute().use { it.body?.string() ?: "" }
            if (mbStr.isNotBlank()) {
                val mbJson = JSONObject(mbStr)
                val recs = mbJson.optJSONArray("recordings")
                if (recs != null && recs.length() > 0) {
                    val rec = recs.getJSONObject(0)
                    val mbTitle = rec.optString("title", finalTitle)
                    var mbAlbum = "Single"
                    var mbYear: Int? = null
                    var coverArtUrl: String? = null

                    val releases = rec.optJSONArray("releases")
                    if (releases != null && releases.length() > 0) {
                        val rel = releases.getJSONObject(0)
                        mbAlbum = rel.optString("title", "Single")
                        val date = rel.optString("date", "")
                        if (date.length >= 4) mbYear = date.substring(0, 4).toIntOrNull()
                        val relId = rel.optString("id", "")
                        if (relId.isNotBlank()) {
                            coverArtUrl = "https://coverartarchive.org/release/$relId/front-500"
                        }
                    }

                    return@withContext EnrichedMetadata(
                        title = mbTitle,
                        artist = finalArtist,
                        album = mbAlbum,
                        year = mbYear,
                        genre = null,
                        artworkUrl = coverArtUrl ?: fallbackThumbnailUrl
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback: cleaned title and artist with original thumbnail
        EnrichedMetadata(
            title = finalTitle,
            artist = finalArtist,
            album = "Downloads",
            year = null,
            genre = null,
            artworkUrl = fallbackThumbnailUrl
        )
    }
}
