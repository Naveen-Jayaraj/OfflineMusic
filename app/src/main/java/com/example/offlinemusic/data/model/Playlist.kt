package com.example.offlinemusic.data.model

import android.net.Uri

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val songCount: Int,
    val totalDurationMs: Long,
    val artworkUri: Uri?,
    val playlistType: String, // "LIKED", "FOLDER", "USER", "SMART"
    val folderSourceId: String? = null,
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
}
