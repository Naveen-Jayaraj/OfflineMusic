package com.example.offlinemusic.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.entities.AlbumEntity
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.database.entities.ArtistReviewQueueEntity
import com.example.offlinemusic.data.database.entities.FolderSourceEntity
import com.example.offlinemusic.data.database.entities.GenreEntity
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.database.entities.TrackArtistEntity
import com.example.offlinemusic.data.database.entities.TrackEntity
import com.example.offlinemusic.data.database.entities.TrackGenreEntity
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
import java.util.UUID

class MusicScanner(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    val repository = MusicRepository(context)
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

    data class ExtractedMetadata(
        val track: TrackEntity,
        val artistResult: ArtistParseResult,
        val albumTitle: String?,
        val albumArtist: String?,
        val genreName: String?,
        val folderName: String
    )

    suspend fun getCachedScanResult(): ScanResult = withContext(Dispatchers.IO) {
        val songs = repository.getAllSongs()
        val albums = repository.getAllAlbumsList()
        ScanResult(songs = songs, albums = albums)
    }

    suspend fun scanTree(treeUri: Uri, forceFullRescan: Boolean = false): ScanResult = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext getCachedScanResult()

        val rootFolderName = rootDoc.name ?: "Music Folder"
        val folderSourceId = "source_${treeUri.toString().hashCode().toUInt().toString(16)}"

        // Register or update folder source
        db.folderSourceDao().insertOrUpdateFolderSource(
            FolderSourceEntity(
                id = folderSourceId,
                folderUri = treeUri.toString(),
                folderName = rootFolderName,
                scanStatus = "SCANNING",
                lastSyncedTimestamp = System.currentTimeMillis()
            )
        )

        // Cached track map for fast incremental scanning
        val existingTracks = if (forceFullRescan) emptyList() else db.trackDao().getAllTracks()
        val cachedMap = existingTracks.associateBy { it.fileUri }

        val filesToExtract = mutableListOf<Pair<DocumentFile, String>>()
        val seenUris = mutableSetOf<String>()

        val artworkCacheDir = File(context.cacheDir, "artwork").apply { mkdirs() }

        // Breadth-first traversal of the file tree
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(Pair(rootDoc, rootFolderName))

        while (queue.isNotEmpty()) {
            val (currentDir, currentFolder) = queue.removeFirst()
            val files = try {
                currentDir.listFiles()
            } catch (e: Exception) {
                emptyArray<DocumentFile>()
            }

            for (file in files) {
                if (file.isDirectory) {
                    val subfolderName = file.name ?: "Unknown Folder"
                    queue.add(Pair(file, subfolderName))
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
                            // Unchanged file - already in database
                        } else {
                            filesToExtract.add(Pair(file, currentFolder))
                        }
                    }
                }
            }
        }

        // Deleted or inaccessible files detection
        val deletedUris = cachedMap.keys.filter { it !in seenUris }
        if (deletedUris.isNotEmpty()) {
            db.trackDao().deleteTracksByUris(deletedUris)
        }

        // Parallel metadata extraction for new/modified files
        if (filesToExtract.isNotEmpty()) {
            val extractedList = mutableListOf<ExtractedMetadata>()
            filesToExtract.chunked(8).forEach { chunk ->
                val chunkResults = chunk.map { (file, folderName) ->
                    async(Dispatchers.IO) {
                        extractSongMetadata(file, folderName, artworkCacheDir)
                    }
                }.awaitAll().filterNotNull()

                extractedList.addAll(chunkResults)
            }

            // Batch insert into Room database
            batchInsertScannedData(extractedList, folderSourceId)
        }

        // Refresh stats
        db.albumDao().refreshAllAlbumStats()
        db.albumDao().cleanupOrphanAlbums()
        repository.normalizeAndCleanArtistProfiles()
        repository.syncFolderAndLikedPlaylists()

        // Auto-resolve any exact normalized matches in review queue (no user confirmation needed for exact match)
        repository.autoResolveExactReviews()

        db.folderSourceDao().updateScanStatus(folderSourceId, "SYNCED", System.currentTimeMillis())

        // Enqueue background genre enrichment (deferred, non-blocking)
        GenreEnrichmentWorker.enqueue(context)

        getCachedScanResult()
    }

    private suspend fun batchInsertScannedData(
        metadataList: List<ExtractedMetadata>,
        folderSourceId: String
    ) = withContext(Dispatchers.IO) {
        if (metadataList.isEmpty()) return@withContext

        val tracksToInsert = mutableListOf<TrackEntity>()
        val artistsToInsert = mutableMapOf<String, ArtistEntity>()
        val trackArtistsToInsert = mutableListOf<TrackArtistEntity>()
        val albumsToInsert = mutableMapOf<String, AlbumEntity>()
        val genresToInsert = mutableMapOf<String, GenreEntity>()
        val trackGenresToInsert = mutableListOf<TrackGenreEntity>()
        val folderPlaylists = mutableMapOf<String, PlaylistEntity>()
        val playlistTracksToInsert = mutableListOf<PlaylistTrackEntity>()
        val reviewQueueItems = mutableListOf<ArtistReviewQueueEntity>()

        // Preload existing library artists for smart matching and alias lookup
        val existingArtists = db.artistDao().getAllArtists()
        val existingByNormalized = existingArtists.associateBy { it.normalizedName }.toMutableMap()
        val existingByAlias = mutableMapOf<String, ArtistEntity>()
        for (artist in existingArtists) {
            artist.aliases?.split(",")?.forEach { alias ->
                val normAlias = ArtistNormalizer.normalize(alias)
                if (normAlias.isNotEmpty()) {
                    existingByAlias[normAlias] = artist
                }
            }
        }

        // Deduplicate review queue items so bulk scanning never spams the user
        val reviewedCreditedStrings = db.artistReviewQueueDao().getAllRawCreditedStrings()
            .map { it.lowercase().trim() }
            .toMutableSet()
        val newlyQueuedStrings = mutableSetOf<String>()

        for (item in metadataList) {
            var track = item.track

            // 1. Process Album Association
            val rawAlbum = item.albumTitle?.trim()
            if (!rawAlbum.isNullOrBlank() && !rawAlbum.equals("Unknown Album", ignoreCase = true) && !rawAlbum.equals("None", ignoreCase = true)) {
                val normalizedAlbum = rawAlbum.lowercase()
                val albumId = "album_${normalizedAlbum.hashCode().toUInt().toString(16)}"
                val albumEntity = AlbumEntity(
                    id = albumId,
                    title = rawAlbum,
                    normalizedTitle = normalizedAlbum,
                    albumArtist = item.albumArtist ?: item.artistResult.primaryArtist,
                    releaseYear = track.year,
                    artworkUri = track.artworkUri
                )
                albumsToInsert[albumId] = albumEntity
                track = track.copy(albumId = albumId)
            } else {
                // If album metadata is absent, leave albumId null (Unassigned representation in UI, Spec Section 4)
                track = track.copy(albumId = null)
            }

            tracksToInsert.add(track)

            // 2. Process Artists & Credits with Smart Identification
            for (credit in item.artistResult.artists) {
                val norm = credit.normalizedName
                var targetArtistId: String

                // Check A: Is it already present in existing database artists or user-defined aliases? (Exact normalized match)
                val exactDbMatch = existingByNormalized[norm] ?: existingByAlias[norm]
                if (exactDbMatch != null) {
                    targetArtistId = exactDbMatch.id
                } else {
                    // Check B: Known global alias (e.g. "marshmellow" -> "Marshmello", "eminem" -> "Eminem")
                    val isGlobalKnown = ArtistNormalizer.isKnownAlias(credit.creditedName)
                    val targetArtistName = credit.canonicalName
                    val targetNormalizedName = ArtistNormalizer.normalize(targetArtistName)

                    // Also check if canonical name has an exact match
                    val canonicalDbMatch = existingByNormalized[targetNormalizedName] ?: existingByAlias[targetNormalizedName]
                    if (canonicalDbMatch != null) {
                        targetArtistId = canonicalDbMatch.id
                    } else {
                        targetArtistId = ArtistNormalizer.generateArtistId(targetNormalizedName)

                        val newArtist = ArtistEntity(
                            id = targetArtistId,
                            name = targetArtistName,
                            normalizedName = targetNormalizedName,
                            aliases = if (credit.creditedName.lowercase() != targetNormalizedName) credit.creditedName else null,
                            metadataStatus = if (isGlobalKnown) "CONFIRMED" else "AUTO"
                        )
                        artistsToInsert[targetArtistId] = newArtist
                        existingByNormalized[targetNormalizedName] = newArtist
                        existingByNormalized[norm] = newArtist

                        // Check C: If it's NOT an exact match while normalized, ask user ONLY if there is a close fuzzy match in the library
                        val rawLower = credit.creditedName.lowercase().trim()
                        val isPlaceholder = rawLower.isBlank() || rawLower == "unknown artist" || rawLower == "unknown"

                        if (!isGlobalKnown && !isPlaceholder && rawLower !in reviewedCreditedStrings && rawLower !in newlyQueuedStrings) {
                            // Find potential near matches in the existing library (MUST NOT be exact match, similarity between 0.65f and 0.99f)
                            val candidateFuzzyMatches = existingArtists
                                .filter { it.normalizedName != targetNormalizedName && it.normalizedName != norm }
                                .map { it to ArtistNormalizer.similarity(it.normalizedName, targetNormalizedName) }
                                .filter { it.second in 0.65f..0.99f }
                                .sortedByDescending { it.second }
                                .take(3)
                                .map { it.first.name }

                            // ONLY enqueue if there are genuine near-miss fuzzy matches to ask user!
                            if (candidateFuzzyMatches.isNotEmpty()) {
                                reviewQueueItems.add(
                                    ArtistReviewQueueEntity(
                                        id = "review_${UUID.randomUUID()}",
                                        trackId = track.id,
                                        rawCreditedString = credit.creditedName.trim(),
                                        suggestedArtistIds = candidateFuzzyMatches.joinToString(","),
                                        status = "PENDING"
                                    )
                                )
                                newlyQueuedStrings.add(rawLower)
                            }
                        }
                    }
                }

                trackArtistsToInsert.add(
                    TrackArtistEntity(
                        trackId = track.id,
                        artistId = targetArtistId,
                        creditedName = credit.creditedName,
                        artistRole = credit.role,
                        artistOrder = credit.order
                    )
                )
            }

            // 3. Process Genre
            val rawGenre = item.genreName?.trim()
            if (!rawGenre.isNullOrBlank()) {
                val normGenre = rawGenre.lowercase()
                val genreId = "genre_${normGenre.hashCode().toUInt().toString(16)}"
                genresToInsert[genreId] = GenreEntity(
                    id = genreId,
                    canonicalName = rawGenre,
                    normalizedName = normGenre
                )
                trackGenresToInsert.add(
                    TrackGenreEntity(
                        trackId = track.id,
                        genreId = genreId,
                        source = "EMBEDDED",
                        confidence = 1.0f
                    )
                )
            }

            // 4. Folder-based Playlist Synchronization (Spec Section 5)
            val folderName = item.folderName
            val folderPlaylistId = "folder_playlist_${folderName.hashCode().toUInt().toString(16)}"
            if (!folderPlaylists.containsKey(folderPlaylistId)) {
                folderPlaylists[folderPlaylistId] = PlaylistEntity(
                    id = folderPlaylistId,
                    name = folderName,
                    description = "Folder: $folderName",
                    artworkUri = track.artworkUri,
                    playlistType = "FOLDER",
                    folderSourceId = folderSourceId,
                    createdAt = System.currentTimeMillis()
                )
            }
            playlistTracksToInsert.add(
                PlaylistTrackEntity(
                    playlistId = folderPlaylistId,
                    trackId = track.id,
                    position = track.trackNumber ?: 0,
                    addedTimestamp = System.currentTimeMillis()
                )
            )
        }

        // Perform transactional writes
        db.withTransaction {
            db.albumDao().insertAlbums(albumsToInsert.values.toList())
            db.artistDao().insertArtists(artistsToInsert.values.toList())
            db.genreDao().getAllGenres() // warm up
            genresToInsert.values.forEach { db.genreDao().insertGenre(it) }

            db.trackDao().insertTracks(tracksToInsert)
            db.artistDao().insertTrackArtists(trackArtistsToInsert)
            db.genreDao().insertTrackGenres(trackGenresToInsert)

            folderPlaylists.values.forEach { db.playlistDao().insertOrUpdatePlaylist(it) }
            db.playlistDao().insertPlaylistTracks(playlistTracksToInsert)

            reviewQueueItems.forEach { db.artistReviewQueueDao().insertReview(it) }
        }
    }

    private fun extractSongMetadata(
        file: DocumentFile,
        folderName: String,
        artworkCacheDir: File
    ): ExtractedMetadata? {
        val uri = file.uri
        val fileName = file.name ?: "Unknown"
        val defaultTitle = fileName.substringBeforeLast('.')
        val sizeBytes = file.length()
        val dateModified = file.lastModified()
        val trackId = uri.toString().hashCode().toUInt().toString(16)
        val ext = fileName.substringAfterLast('.', "mp3").lowercase()

        var title = defaultTitle
        var rawArtist = "Unknown Artist"
        var rawAlbum: String? = null
        var rawAlbumArtist: String? = null
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
                if (it.isNotEmpty()) rawArtist = it
            }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.let {
                if (it.isNotEmpty()) rawAlbumArtist = it
            }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.let {
                if (it.isNotEmpty()) rawAlbum = it
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
            val albumSafeKey = (rawAlbum ?: folderName).replace("[^a-zA-Z0-9_-]".toRegex(), "_")
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

        val artistParseResult = ArtistNormalizer.parseCredits(rawArtist)

        val trackEntity = TrackEntity(
            id = trackId,
            title = title,
            normalizedTitle = title.trim().lowercase(),
            durationMs = durationMs,
            fileUri = uri.toString(),
            format = ext,
            sizeBytes = sizeBytes,
            metadataStatus = "PARSED",
            year = year,
            trackNumber = trackNumber,
            artworkUri = artworkUri?.toString(),
            dateAdded = System.currentTimeMillis(),
            dateModified = dateModified,
            relativePath = file.name ?: ""
        )

        return ExtractedMetadata(
            track = trackEntity,
            artistResult = artistParseResult,
            albumTitle = rawAlbum,
            albumArtist = rawAlbumArtist,
            genreName = genre,
            folderName = folderName
        )
    }

    // Repository delegate methods for backwards compatibility
    suspend fun getAlbumWithSongs(albumId: String): Album? = repository.getAlbumWithSongs(albumId)
    suspend fun getArtistProfile(artistName: String): ArtistProfile = repository.getArtistProfile(artistName)
    suspend fun createCustomAlbum(name: String): String = repository.createCustomAlbum(name)
    suspend fun deleteAlbum(albumId: String) = repository.deleteAlbum(albumId)
    suspend fun addSongsToAlbum(albumId: String, songIds: List<String>) = repository.addSongsToAlbum(albumId, songIds)
    suspend fun addSongToAlbum(albumId: String, songId: String) = repository.addSongToAlbum(albumId, songId)
    suspend fun removeSongFromAlbum(albumId: String, songId: String) = repository.removeSongFromAlbum(albumId, songId)
    suspend fun moveSongBetweenAlbums(songId: String, fromAlbumId: String, toAlbumId: String) = repository.moveSongBetweenAlbums(songId, fromAlbumId, toAlbumId)
    suspend fun getLikedSongIds(): Set<String> = repository.getLikedSongIds()
    suspend fun toggleLikeSong(songId: String): Boolean = repository.toggleLikeSong(songId)
    suspend fun isSongLiked(songId: String): Boolean = repository.isSongLiked(songId)
    suspend fun updateTrackMetadata(
        trackId: String,
        newTitle: String,
        rawArtistString: String,
        newAlbum: String?,
        newGenre: String?,
        newArtworkUri: Uri?
    ): Song? = repository.updateTrackMetadata(trackId, newTitle, rawArtistString, newAlbum, newGenre, newArtworkUri)
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        db.trackDao().deleteAllTracks()
    }
}
