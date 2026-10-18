package com.example.offlinemusic.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.offlinemusic.data.database.MusicDatabase
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque

class MusicScanner(private val context: Context) {

    private val database = MusicDatabase(context)
    private val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus", "wma")

    data class ScanResult(
        val songs: List<Song>,
        val albums: List<Album>
    )

    /**
     * Instantly loads previously scanned songs from the SQLite database.
     * Takes only a few milliseconds with 0 filesystem I/O overhead.
     */
    suspend fun getCachedScanResult(): ScanResult = withContext(Dispatchers.IO) {
        val songs = database.getAllSongs()
        ScanResult(songs = songs, albums = groupSongsIntoAlbums(songs))
    }

    /**
     * Performs an incremental scan of the chosen directory tree.
     * Unchanged files are reused from cache without running MediaMetadataRetriever.
     * Only new or modified files undergo metadata extraction.
     */
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

                        // Check if file is already in database and unmodified
                        if (!forceFullRescan && cached != null && cached.dateModified == fileLastModified && cached.sizeBytes == fileLength) {
                            currentSongs.add(cached.song)
                        } else {
                            // Needs metadata extraction (new or modified)
                            filesToExtract.add(Pair(file, currentAlbumName))
                        }
                    }
                }
            }
        }

        // Deleted files: uris in cache that no longer exist on disk
        val deletedUris = cachedMap.keys.filter { it !in seenUris }
        if (deletedUris.isNotEmpty()) {
            database.deleteSongsByUris(deletedUris)
        }

        // Extract metadata for new/modified files in parallel (chunks of 4)
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

            // Persist newly extracted songs in SQLite database
            database.insertOrReplaceSongs(newlyExtractedSongs)
            currentSongs.addAll(newlyExtractedSongs)
        }

        val albums = groupSongsIntoAlbums(currentSongs)
        ScanResult(songs = currentSongs, albums = albums)
    }

    private fun groupSongsIntoAlbums(songs: List<Song>): List<Album> {
        val albumsMap = songs.groupBy { it.albumFolder }
        return albumsMap.map { (folderName, folderSongs) ->
            val totalDuration = folderSongs.sumOf { it.durationMs }
            val firstArtwork = folderSongs.firstOrNull { it.artworkUri != null }?.artworkUri
            Album(
                name = folderName,
                folderPath = folderName,
                songCount = folderSongs.size,
                totalDurationMs = totalDuration,
                artworkUri = firstArtwork,
                songs = folderSongs.sortedBy { it.trackNumber ?: Int.MAX_VALUE }
            )
        }.sortedBy { it.name.lowercase() }
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

            // Embedded Album Art caching
            val albumSafeKey = albumFolderName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val cachedAlbumArt = File(artworkCacheDir, "art_album_${albumSafeKey}.jpg")
            if (cachedAlbumArt.exists() && cachedAlbumArt.length() > 0) {
                artworkUri = Uri.fromFile(cachedAlbumArt)
            } else {
                val picBytes = mmr.embeddedPicture
                if (picBytes != null && picBytes.isNotEmpty()) {
                    try {
                        FileOutputStream(cachedAlbumArt).use { it.write(picBytes) }
                        artworkUri = Uri.fromFile(cachedAlbumArt)
                    } catch (e: Exception) {
                        val songArt = File(artworkCacheDir, "art_song_${songId}.jpg")
                        try {
                            FileOutputStream(songArt).use { it.write(picBytes) }
                            artworkUri = Uri.fromFile(songArt)
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
