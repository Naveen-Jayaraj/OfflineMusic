package com.example.offlinemusic.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.offlinemusic.data.database.MusicDatabase
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.ArtistProfile
import com.example.offlinemusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque

class MusicScanner(private val context: Context) {

    val database = MusicDatabase(context)
    private val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus", "wma")

    private fun getArtworkUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    data class ScanResult(
        val songs: List<Song>,
        val albums: List<Album>
    )

    suspend fun getCachedScanResult(): ScanResult = withContext(Dispatchers.IO) {
        val songs = database.getAllSongs()
        val albums = database.getAllAlbums()
        ScanResult(songs = songs, albums = albums)
    }

    suspend fun scanTree(treeUri: Uri, forceFullRescan: Boolean = false): ScanResult = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext getCachedScanResult()

        val rootFolderName = rootDoc.name ?: "Music Folder"
        val cachedMap = if (forceFullRescan) emptyMap() else database.getCachedFileMap()

        val currentSongs = mutableListOf<Song>()
        val filesToExtract = mutableListOf<Pair<DocumentFile, String>>()
        val seenUris = mutableSetOf<String>()

        val artworkCacheDir = File(context.cacheDir, "artwork").apply { mkdirs() }

        // Breadth-first traversal
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(Pair(rootDoc, rootFolderName))

        while (queue.isNotEmpty()) {
            val (currentDir, currentAlbumName) = queue.removeFirst()
            val files = try {
                currentDir.listFiles()
            } catch (e: Exception) {
                emptyArray<DocumentFile>()
            }

            for (file in files) {
                if (file.isDirectory) {
                    val subfolderAlbumName = file.name ?: "Unknown Album"
                    queue.add(Pair(file, subfolderAlbumName))
                } else if (file.isFile) {
                    val fileName = file.name ?: continue
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (ext in supportedExtensions || file.type?.startsWith("audio/") == true) {
                        val uriStr = file.uri.toString()
                        seenUris.add(uriStr)

                        val cached = cachedMap[uriStr]
                        val fileLastModified = file.lastModified()
                        val fileLength = file.length()

                        if (!forceFullRescan && cached != null && cached.dateModified == fileLastModified && cached.sizeBytes == fileLength) {
                            currentSongs.add(cached.song)
                        } else {
                            filesToExtract.add(Pair(file, currentAlbumName))
                        }
                    }
                }
            }
        }

        // Deleted files
        val deletedUris = cachedMap.keys.filter { it !in seenUris }
        if (deletedUris.isNotEmpty()) {
            database.deleteSongsByUris(deletedUris)
        }

        // Parallel metadata extraction for new/modified files
        if (filesToExtract.isNotEmpty()) {
            val newlyExtractedSongs = mutableListOf<Song>()
            filesToExtract.chunked(4).forEach { chunk ->
                val chunkResults = chunk.map { (file, albumName) ->
                    async(Dispatchers.IO) {
                        extractSongMetadata(file, albumName, artworkCacheDir)
                    }
                }.awaitAll().filterNotNull()

                newlyExtractedSongs.addAll(chunkResults)
            }

            database.insertOrUpdateScannedData(newlyExtractedSongs)
            currentSongs.addAll(newlyExtractedSongs)
        }

        val allDbSongs = database.getAllSongs()
        val allDbAlbums = database.getAllAlbums()

        ScanResult(songs = allDbSongs, albums = allDbAlbums)
    }

    suspend fun getAlbumWithSongs(albumId: String): Album? = withContext(Dispatchers.IO) {
        database.getAlbumWithSongs(albumId)
    }

    suspend fun getArtistProfile(artistName: String): ArtistProfile = withContext(Dispatchers.IO) {
        val songs = database.getSongsForArtist(artistName)
        val albums = database.getAlbumsForArtist(artistName)
        val firstArt = songs.firstOrNull { it.artworkUri != null }?.artworkUri
            ?: albums.firstOrNull { it.artworkUri != null }?.artworkUri

        ArtistProfile(
            name = artistName,
            songs = songs,
            albums = albums,
            totalDurationMs = songs.sumOf { it.durationMs },
            artworkUri = firstArt
        )
    }

    suspend fun createCustomAlbum(name: String): String = withContext(Dispatchers.IO) {
        database.createCustomAlbum(name)
    }

    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        database.deleteAlbum(albumId)
    }

    suspend fun addSongsToAlbum(albumId: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        database.addSongsToAlbum(albumId, songIds)
    }

    suspend fun addSongToAlbum(albumId: String, songId: String) = withContext(Dispatchers.IO) {
        database.addSongToAlbum(albumId, songId)
    }

    suspend fun removeSongFromAlbum(albumId: String, songId: String) = withContext(Dispatchers.IO) {
        database.removeSongFromAlbum(albumId, songId)
    }

    suspend fun moveSongBetweenAlbums(songId: String, fromAlbumId: String, toAlbumId: String) = withContext(Dispatchers.IO) {
        database.moveSongBetweenAlbums(songId, fromAlbumId, toAlbumId)
    }

    suspend fun getLikedSongIds(): Set<String> = withContext(Dispatchers.IO) {
        database.getLikedSongIds()
    }

    suspend fun toggleLikeSong(songId: String): Boolean = withContext(Dispatchers.IO) {
        database.toggleLikeSong(songId)
    }

    suspend fun isSongLiked(songId: String): Boolean = withContext(Dispatchers.IO) {
        database.isSongLiked(songId)
    }

    private fun extractSongMetadata(
        file: DocumentFile,
        albumFolderName: String,
        artworkCacheDir: File
    ): Song? {
        val uri = file.uri
        val fileName = file.name ?: "Unknown"
        val defaultTitle = fileName.substringBeforeLast('.')
        val sizeBytes = file.length()
        val dateModified = file.lastModified()
        val songId = uri.toString().hashCode().toString()

        var title = defaultTitle
        var artist = "Unknown Artist"
        var id3Album = albumFolderName
        var year: Int? = null
        var genre: String? = null
        var durationMs = 0L
        var trackNumber: Int? = null
        var artworkUri: Uri? = null

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.let {
                if (it.isNotEmpty()) title = it
            }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.let {
                if (it.isNotEmpty()) artist = it
            } ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.let {
                if (it.isNotEmpty()) artist = it
            }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.let {
                if (it.isNotEmpty()) id3Album = it
            }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.let {
                if (it.isNotEmpty()) genre = it
            }
            val dateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            year = dateStr?.filter { it.isDigit() }?.take(4)?.toIntOrNull()

            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                durationMs = it
            }

            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let {
                trackNumber = it.split('/').firstOrNull()?.trim()?.toIntOrNull()
            }

            // Embedded Album Art caching with fast downsampled thumbnail creation
            val albumSafeKey = albumFolderName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val cachedAlbumArt = File(artworkCacheDir, "art_album_${albumSafeKey}.jpg")
            if (cachedAlbumArt.exists() && cachedAlbumArt.length() > 0) {
                artworkUri = getArtworkUri(cachedAlbumArt)
            } else {
                val picBytes = mmr.embeddedPicture
                if (picBytes != null && picBytes.isNotEmpty()) {
                    try {
                        val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size, boundsOptions)
                        var sampleSize = 1
                        while (boundsOptions.outWidth / (sampleSize * 2) >= 360 && boundsOptions.outHeight / (sampleSize * 2) >= 360) {
                            sampleSize *= 2
                        }
                        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size, decodeOptions)
                        if (bitmap != null) {
                            FileOutputStream(cachedAlbumArt).use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            bitmap.recycle()
                            artworkUri = getArtworkUri(cachedAlbumArt)
                        }
                    } catch (e: Exception) {
                        try {
                            FileOutputStream(cachedAlbumArt).use { it.write(picBytes) }
                            artworkUri = getArtworkUri(cachedAlbumArt)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully on file metadata
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }

        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = id3Album,
            albumFolder = albumFolderName,
            year = year,
            genre = genre,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            dateModified = dateModified,
            uri = uri,
            relativePath = file.name ?: "",
            trackNumber = trackNumber,
            artworkUri = artworkUri
        )
    }

    fun clearCache() {
        database.clearAll()
    }
}
