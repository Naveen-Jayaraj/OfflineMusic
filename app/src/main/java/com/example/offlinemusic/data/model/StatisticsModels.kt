package com.example.offlinemusic.data.model

import java.util.Locale

data class ListeningOverviewStats(
    val totalDurationMs: Long = 0L,
    val totalPlays: Int = 0,
    val completedPlays: Int = 0,
    val skippedPlays: Int = 0,
    val uniqueTracksCount: Int = 0,
    val uniqueArtistsCount: Int = 0
) {
    val completionRate: Float
        get() = if (totalPlays > 0) (completedPlays.toFloat() / totalPlays.toFloat()) * 100f else 0f

    val formattedTotalDuration: String
        get() {
            if (totalDurationMs <= 0L) return "0m"
            val totalSeconds = totalDurationMs / 1000
            val totalMinutes = totalSeconds / 60
            val hours = totalMinutes / 60
            val remainingMins = totalMinutes % 60
            return if (hours > 0) {
                "${hours}h ${remainingMins}m"
            } else {
                "${remainingMins}m"
            }
        }

    val totalHoursDecimal: String
        get() {
            val hours = totalDurationMs.toDouble() / 3_600_000.0
            return String.format(Locale.getDefault(), "%.1f", hours)
        }
}

data class SongStat(
    val trackId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val artworkUri: String?,
    val durationMs: Long,
    val playCount: Int,
    val totalDurationMs: Long,
    val lastPlayedTimestamp: Long?
) {
    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return if (hours > 0) {
                "${hours}h ${remainingMins}m"
            } else if (minutes > 0) {
                "${minutes}m"
            } else {
                "${totalSeconds}s"
            }
        }
}

data class ArtistStat(
    val artistId: String,
    val artistName: String,
    val artworkUri: String?,
    val playCount: Int,
    val totalDurationMs: Long,
    val trackCount: Int
) {
    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return if (hours > 0) {
                "${hours}h ${remainingMins}m"
            } else if (minutes > 0) {
                "${minutes}m"
            } else {
                "${totalSeconds}s"
            }
        }
}

data class MoodStat(
    val mood: String,
    val playCount: Int,
    val totalDurationMs: Long,
    val trackCount: Int
) {
    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return if (hours > 0) {
                "${hours}h ${remainingMins}m"
            } else if (minutes > 0) {
                "${minutes}m"
            } else {
                "${totalSeconds}s"
            }
        }
}

data class GenreStat(
    val genre: String,
    val playCount: Int,
    val totalDurationMs: Long,
    val trackCount: Int
) {
    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return if (hours > 0) {
                "${hours}h ${remainingMins}m"
            } else if (minutes > 0) {
                "${minutes}m"
            } else {
                "${totalSeconds}s"
            }
        }
}

data class AlbumStat(
    val albumId: String,
    val albumTitle: String,
    val artistName: String?,
    val artworkUri: String?,
    val playCount: Int,
    val totalDurationMs: Long
) {
    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return if (hours > 0) {
                "${hours}h ${remainingMins}m"
            } else if (minutes > 0) {
                "${minutes}m"
            } else {
                "${totalSeconds}s"
            }
        }
}

data class PlayHistoryItem(
    val eventId: Long,
    val trackId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val artworkUri: String?,
    val playbackTimestamp: Long,
    val playedDurationMs: Long,
    val completionStatus: Boolean,
    val playbackSource: String
) {
    val formattedPlayedDuration: String
        get() {
            val totalSeconds = playedDurationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes > 0) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }
        }
}

enum class StatsTimeRange(val label: String) {
    ALL_TIME("All Time"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_7_DAYS("Last 7 Days"),
    TODAY("Today")
}

