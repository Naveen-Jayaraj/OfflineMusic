package com.example.offlinemusic.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import java.io.File

object TrackMetadataWriter {

    private const val TAG = "TrackMetadataWriter"

    /**
     * Writes updated metadata (Title, Artists, Genre, Cover Art) directly into the physical audio file (MP3 ID3v2).
     * Uses Storage Access Framework streams to ensure compatibility across all Android storage versions.
     */
    fun writePhysicalMetadata(
        context: Context,
        trackUri: Uri,
        title: String,
        artists: List<String>,
        album: String? = null,
        genre: String?,
        coverBytes: ByteArray?
    ): Boolean {
        var tempIn: File? = null
        var tempOut: File? = null

        return try {
            val cacheDir = context.cacheDir
            tempIn = File(cacheDir, "temp_tag_in_${System.currentTimeMillis()}.mp3")
            tempOut = File(cacheDir, "temp_tag_out_${System.currentTimeMillis()}.mp3")

            // 1. Copy original audio file stream to a cache file
            val copied = context.contentResolver.openInputStream(trackUri)?.use { input ->
                tempIn.outputStream().use { output ->
                    input.copyTo(output) > 0
                }
            } ?: false

            if (!copied || !tempIn.exists() || tempIn.length() == 0L) {
                Log.w(TAG, "Failed to copy audio stream to cache for $trackUri")
                return false
            }

            // 2. Load with mp3agic and apply ID3v2 tags
            val mp3File = Mp3File(tempIn.absolutePath)
            val id3Tag = if (mp3File.hasId3v2Tag()) mp3File.id3v2Tag else ID3v24Tag().also { mp3File.id3v2Tag = it }

            id3Tag.title = title.trim()
            // Store artists delimited by " / " so other modern players and our normalizer recognize multi-artist credits
            id3Tag.artist = artists.joinToString(" / ")

            if (album != null) {
                id3Tag.album = album.trim()
            }

            if (!genre.isNullOrBlank()) {
                id3Tag.genreDescription = genre.trim()
            }

            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val mimeType = if (coverBytes.size > 2 && coverBytes[0] == 0x89.toByte() && coverBytes[1] == 0x50.toByte()) {
                    "image/png"
                } else {
                    "image/jpeg"
                }
                id3Tag.setAlbumImage(coverBytes, mimeType)
            }

            // 3. Save modified MP3
            mp3File.save(tempOut.absolutePath)

            // 4. Stream back to original physical file via SAF
            if (tempOut.exists() && tempOut.length() > 0L) {
                context.contentResolver.openOutputStream(trackUri, "wt")?.use { output ->
                    tempOut.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Successfully updated physical ID3 tags for $trackUri")
                true
            } else {
                Log.w(TAG, "Temp output file not generated or empty for $trackUri")
                false
            }
        } catch (e: Exception) {
            // Handles unsupported non-MP3 files, read-only volumes, etc. gracefully
            Log.w(TAG, "Could not write physical ID3 tag for $trackUri: ${e.message}")
            false
        } finally {
            try { tempIn?.delete() } catch (_: Exception) {}
            try { tempOut?.delete() } catch (_: Exception) {}
        }
    }
}
