package com.example.offlinemusic.downloader.engine

import android.util.Log
import com.example.offlinemusic.data.database.entities.TrackEntity
import com.example.offlinemusic.data.remote.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class EnrichedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val year: Int?,
    val genre: String?,
    val artworkUrl: String?,
    val producer: String? = null,
    val composer: String? = null,
    val language: String? = null,
    val mood: String? = null,
    val lyricsAvailable: Boolean = false,
    val lyricsUri: String? = null,
    val metachecked: Boolean = false,
    val trackNumber: Int? = null
)

object MetadataEnricher {

    private const val TAG = "MetadataEnricher"

    private val BRACKET_PATTERNS = listOf(
        Pattern.compile("(?i)\\((?:official\\s+)?(?:music\\s+)?video\\)"),
        Pattern.compile("(?i)\\[(?:official\\s+)?(?:music\\s+)?video\\]"),
        Pattern.compile("(?i)\\((?:official\\s+)?audio\\)"),
        Pattern.compile("(?i)\\[(?:official\\s+)?audio\\]"),
        Pattern.compile("(?i)\\((?:official\\s+)?lyric\\s+video\\)"),
        Pattern.compile("(?i)\\[(?:official\\s+)?lyric\\s+video\\]"),
        Pattern.compile("(?i)\\((?:lyrics?|lyric\\s+video)\\)"),
        Pattern.compile("(?i)\\[(?:lyrics?|lyric\\s+video)\\]"),
        Pattern.compile("(?i)\\((?:visualizer|audio|hd|4k)\\)"),
        Pattern.compile("(?i)\\[(?:visualizer|audio|hd|4k)\\]")
    )

    /**
     * Clean title noise before sending to MetaEnricher
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
     * Enrich track metadata using remote MetaEnricher backend (https://meta-enricher.onrender.com)
     */
    suspend fun enrich(
        rawTitle: String,
        rawArtist: String?,
        fallbackThumbnailUrl: String?,
        durationMs: Long = 0L,
        album: String? = null,
        youtubeId: String? = null
    ): EnrichedMetadata = withContext(Dispatchers.IO) {
        val (parsedTitle, inferredArtist) = parseArtistAndTitle(rawTitle, rawArtist)
        val finalTitle = parsedTitle.ifBlank { rawTitle.trim() }
        val finalArtist = inferredArtist ?: rawArtist?.let { cleanArtistName(it) } ?: "Unknown Artist"

        try {
            val dummyId = "dl_${finalTitle.hashCode().toUInt().toString(16)}"
            val dummyTrack = TrackEntity(
                id = dummyId,
                title = rawTitle.ifBlank { finalTitle },
                normalizedTitle = rawTitle.lowercase(),
                durationMs = durationMs,
                fileUri = "file:///temp_download.mp3",
                artist = rawArtist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
                album = album,
                artworkUri = fallbackThumbnailUrl
            )

            val response = ApiClient.metaEnricherApi.enrichSong(dummyTrack, youtubeId)
            if (response.success && response.audit?.matched != false) {
                val song = response.song
                Log.i(TAG, "MetaEnricher enriched: ${song.title} - ${song.artist}")
                return@withContext EnrichedMetadata(
                    title = song.title?.ifBlank { finalTitle } ?: finalTitle,
                    artist = song.artist?.ifBlank { finalArtist } ?: finalArtist,
                    album = song.album?.takeIf { it.isNotBlank() } ?: album.orEmpty().ifBlank { "Downloads" },
                    year = song.year,
                    genre = song.genre,
                    artworkUrl = song.artworkUri ?: fallbackThumbnailUrl,
                    producer = song.producer,
                    composer = song.composer,
                    language = song.language,
                    mood = song.mood,
                    lyricsAvailable = song.lyricsAvailable,
                    lyricsUri = song.lyricsUri,
                    metachecked = song.metachecked,
                    trackNumber = song.trackNumber
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "MetaEnricher remote query failed, fallback used: ${e.message}")
        }

        // Clean fallback
        EnrichedMetadata(
            title = finalTitle,
            artist = finalArtist,
            album = album.orEmpty().ifBlank { "Downloads" },
            year = null,
            genre = null,
            artworkUrl = fallbackThumbnailUrl,
            producer = null,
            composer = null,
            language = null,
            mood = null,
            lyricsAvailable = false,
            lyricsUri = null,
            metachecked = false
        )
    }
}
