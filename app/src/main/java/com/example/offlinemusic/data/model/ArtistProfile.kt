package com.example.offlinemusic.data.model

import android.net.Uri

data class ArtistProfile(
    val name: String,
    val songs: List<Song>,
    val albums: List<Album>,
    val totalDurationMs: Long,
    val artworkUri: Uri?
) {
    val songCount: Int get() = songs.size
    val albumCount: Int get() = albums.size

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
}
