package com.example.offlinemusic.data.model

import android.net.Uri

data class Album(
    val id: String,
    val name: String,
    val folderPath: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val artworkUri: Uri?,
    val isCustom: Boolean = false,
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

    val artistSummary: String
        get() {
            val artists = songs.map { it.artist }.distinct()
            return when {
                artists.isEmpty() -> if (isCustom) "Custom Album" else "Unknown Artist"
                artists.size == 1 -> artists.first()
                artists.size == 2 -> "${artists[0]} & ${artists[1]}"
                else -> "${artists.first()} & Various Artists"
            }
        }
}
