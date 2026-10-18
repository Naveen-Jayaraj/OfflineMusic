package com.example.offlinemusic.data.model

import android.net.Uri

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumFolder: String, // Folder name representing album
    val year: Int?,
    val genre: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val uri: Uri,
    val relativePath: String,
    val trackNumber: Int? = null,
    val artworkUri: Uri? = null
) {
    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val formattedSize: String
        get() {
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return String.format("%.1f MB", mb)
        }
}
