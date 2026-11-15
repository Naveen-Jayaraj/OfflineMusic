package com.example.offlinemusic.data.model

import android.net.Uri

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val songCount: Int,
    val totalDurationMs: Long,
    val artworkUri: Uri?,
    val playlistType: String, // "LIKED", "FOLDER", "USER", "SMART", "DYNAMIC_6H"
    val folderSourceId: String? = null,
    val createdAt: Long = 0L,
    val songs: List<Song> = emptyList()
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (totalDurationMs / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) {
                String.format("%d hr %d min", hours, minutes)
            } else {
                String.format("%d min", minutes)
            }
        }

    val isLikedSpecial: Boolean
        get() = playlistType == "LIKED" || id == "liked_songs"

    val isRecentlyDownloadedSpecial: Boolean
        get() = playlistType == "DOWNLOADS" || id == "recently_downloaded"

    val isDynamic6hSpecial: Boolean
        get() = playlistType == "DYNAMIC_6H" || id.startsWith("dynamic_")

    val remainingExpiryFormatted: String
        get() {
            val expiryTime = createdAt + 6 * 3600 * 1000L
            val remainingMs = expiryTime - System.currentTimeMillis()
            if (remainingMs <= 0) return "Expiring soon"
            val totalMinutes = (remainingMs / (60 * 1000L)).coerceAtLeast(1)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}
