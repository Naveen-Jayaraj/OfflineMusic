package com.example.offlinemusic.downloader.models

import android.net.Uri

enum class DownloadSource(val displayName: String) {
    YOUTUBE("YouTube"),
    LUCIDA("Lucida Lossless"),
    BOTH("Both")
}

enum class DownloadSourceFilter(val displayName: String) {
    BOTH("Both (YouTube & Lucida)"),
    YOUTUBE_ONLY("YouTube Only"),
    LUCIDA_ONLY("Lucida Only")
}

data class DownloadableTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: String = "",
    val durationMs: Long = 0L,
    val views: String = "",
    val source: DownloadSource,
    val sourceUrl: String,
    val quality: String = "High Quality",
    val thumbnailUrl: String? = null
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    ENRICHING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadProgress(
    val downloadId: String,
    val trackTitle: String,
    val artist: String,
    val progress: Float = 0f, // 0.0 to 1.0
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
    val isSpotifyBatch: Boolean = false,
    val batchCurrent: Int = 0,
    val batchTotal: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

data class SpotifyPlaylistInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val owner: String? = null,
    val thumbnail: String? = null,
    val totalTracks: Int = 0,
    val tracks: List<SpotifyTrackInfo> = emptyList()
)

data class SpotifyTrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val album: String? = null,
    val previewUrl: String? = null
)

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

data class SyncedLyrics(
    val lines: List<LyricsLine> = emptyList(),
    val plainLyrics: String? = null
) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    fun findActiveLineIndex(currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var activeIndex = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= currentPositionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
