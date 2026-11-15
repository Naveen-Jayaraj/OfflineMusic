package com.example.offlinemusic.data.remote

import com.google.gson.annotations.SerializedName

data class SingleSongResponse(
    val success: Boolean,
    val song: EnrichedSongDto,
    val audit: EnrichmentAudit? = null,
    val lyricsSample: String? = null
)

data class BatchSongResponse(
    val success: Boolean,
    val total: Int = 0,
    val enriched: Int = 0,
    val tracks: List<SingleSongResponse> = emptyList()
)

data class EnrichmentAudit(
    val matched: Boolean = false,
    val confidenceScore: Float = 0f,
    val fieldsUpdated: List<String> = emptyList(),
    val fieldsCorrected: List<String> = emptyList(),
    val sourcesConsulted: List<String> = emptyList(),
    val rawTitleCleaned: Boolean = false,
    val artistsReformatted: Boolean = false,
    val lyricsFound: Boolean = false,
    val detectedLanguage: String? = null,
    val detectedMood: String? = null
)

data class LyricQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null
)

data class LyricResponse(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val lyricsAvailable: Boolean = false,
    val hasSynced: Boolean = false,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val source: String = "",
    val linesCount: Int = 0,
    val lyricsUri: String? = null
)

data class HealthResponse(
    val status: String = "ok",
    val server: String = "",
    val version: String = "",
    val uptimeSeconds: Long = 0L,
    val totalSongRequests: Long = 0L,
    val totalLyricRequests: Long = 0L,
    val metadataCacheSize: Long = 0L,
    val lyricsCacheSize: Long = 0L
)

data class EnrichedSongDto(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val producer: String? = null,
    val composer: String? = null,
    val language: String? = null,
    val mood: String? = null,
    val source: String? = null,
    val lyricsAvailable: Boolean = false,
    val metachecked: Boolean = true,
    val durationMs: Long? = null,
    val fileUri: String? = null,
    val artworkUri: String? = null,
    val lyricsUri: String? = null,
    val format: String? = null,
    val sizeBytes: Long? = null,
    val metadataStatus: String? = "CONFIRMED"
)
