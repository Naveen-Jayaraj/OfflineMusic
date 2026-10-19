package com.example.offlinemusic.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.dao.PlaylistWithStats
import com.example.offlinemusic.data.database.dao.TrackWithDetails
import com.example.offlinemusic.data.database.entities.AlbumEntity
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.database.entities.GenreEntity
import com.example.offlinemusic.data.database.entities.PlayEventEntity
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.database.entities.TrackArtistEntity
import com.example.offlinemusic.data.database.entities.TrackGenreEntity
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.ArtistProfile
import com.example.offlinemusic.data.model.Playlist
import com.example.offlinemusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MusicRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val trackDao = db.trackDao()
    private val artistDao = db.artistDao()
    private val albumDao = db.albumDao()
    private val playlistDao = db.playlistDao()
    private val genreDao = db.genreDao()
    private val playEventDao = db.playEventDao()
    private val folderSourceDao = db.folderSourceDao()
    private val libraryDao = db.libraryDao()

    val allTracks: Flow<List<Song>> = libraryDao.getAllTracksWithDetailsFlow().map { list ->
        list.map { it.toSong() }
    }

    val likedTrackIds: Flow<Set<String>> = trackDao.getLikedTrackIdsFlow().map { it.toSet() }

    val allAlbums: Flow<List<Album>> = albumDao.getAllAlbumsFlow().map { list ->
        list.map { it.toAlbum() }
    }

    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylistsWithStatsFlow().map { list ->
        list.map { it.toPlaylist() }
    }

    val allArtists: Flow<List<com.example.offlinemusic.data.database.entities.ArtistEntity>> = artistDao.getAllArtistsFlow()

    val pendingReviewCount: Flow<Int> = db.artistReviewQueueDao().getPendingReviewCountFlow()

    suspend fun getAllArtistsList(): List<com.example.offlinemusic.data.database.entities.ArtistEntity> = withContext(Dispatchers.IO) {
        artistDao.getAllArtists()
    }

    suspend fun autoResolveExactReviews() = withContext(Dispatchers.IO) {
        val pendingReviews = db.artistReviewQueueDao().getPendingReviews()
        if (pendingReviews.isEmpty()) return@withContext
        val allArtists = artistDao.getAllArtists()
        val artistByNorm = allArtists.associateBy { it.normalizedName }
        for (review in pendingReviews) {
            val norm = ArtistNormalizer.normalize(review.rawCreditedString)
            val match = artistByNorm[norm]
            if (match != null) {
                artistDao.updateArtistIdForCreditedName(review.rawCreditedString, match.id)
                db.artistReviewQueueDao().updateStatus(review.id, "RESOLVED")
            }
        }
    }

    suspend fun normalizeAndCleanArtistProfiles() = withContext(Dispatchers.IO) {
        // 1. Clean track_artists with nonexistent tracks
        artistDao.deleteOrphanTrackArtists()

        // 2. Fix track_artists where credited_name has delimiters (like "AJR/Eminem")
        val combinedTrackArtists = artistDao.getTrackArtistsWithDelimiters()
        for (link in combinedTrackArtists) {
            val parts = ArtistNormalizer.splitCollaborators(link.creditedName)
                .map { ArtistNormalizer.resolveCanonicalName(it) }
                .filter { it.isNotBlank() }

            if (parts.size > 1) {
                artistDao.deleteTrackArtistsForTrack(link.trackId)
                val newLinks = mutableListOf<TrackArtistEntity>()
                var order = 0
                for (part in parts) {
                    val norm = ArtistNormalizer.normalize(part)
                    val artistId = ArtistNormalizer.generateArtistId(norm)
                    val artistEntity = ArtistEntity(
                        id = artistId,
                        name = part,
                        normalizedName = norm,
                        metadataStatus = "CONFIRMED"
                    )
                    artistDao.insertOrUpdateArtist(artistEntity)
                    newLinks.add(
                        TrackArtistEntity(
                            trackId = link.trackId,
                            artistId = artistId,
                            creditedName = part,
                            artistRole = link.artistRole,
                            artistOrder = order++
                        )
                    )
                }
                artistDao.insertTrackArtists(newLinks)
            }
        }

        // 3. Fix any artist entities whose names contain delimiters (like "AJR/Eminem")
        val combinedArtists = artistDao.getCombinedDelimitedArtists()
        for (artist in combinedArtists) {
            val trackLinks = artistDao.getTrackArtistsForArtistId(artist.id)
            val subArtists = ArtistNormalizer.splitCollaborators(artist.name)
                .map { ArtistNormalizer.resolveCanonicalName(it) }
                .filter { it.isNotBlank() }

            if (subArtists.size > 1 && trackLinks.isNotEmpty()) {
                val newLinks = mutableListOf<TrackArtistEntity>()
                for (link in trackLinks) {
                    var order = link.artistOrder
                    for (subName in subArtists) {
                        val subNorm = ArtistNormalizer.normalize(subName)
                        val subId = ArtistNormalizer.generateArtistId(subNorm)
                        val subEntity = ArtistEntity(
                            id = subId,
                            name = subName,
                            normalizedName = subNorm,
                            metadataStatus = "CONFIRMED"
                        )
                        artistDao.insertOrUpdateArtist(subEntity)
                        newLinks.add(
                            TrackArtistEntity(
                                trackId = link.trackId,
                                artistId = subId,
                                creditedName = subName,
                                artistRole = link.artistRole,
                                artistOrder = order++
                            )
                        )
                    }
                }
                artistDao.deleteTrackArtistsForArtistId(artist.id)
                artistDao.insertTrackArtists(newLinks)
            }
        }

        // 4. Delete all 0-song profiles from artists table!
        artistDao.deleteZeroSongArtists()
    }

    suspend fun getOldestPendingReview(): com.example.offlinemusic.data.database.entities.ArtistReviewQueueEntity? = withContext(Dispatchers.IO) {
        db.artistReviewQueueDao().getOldestPendingReview()
    }

    suspend fun getTrackByIdRaw(trackId: String): com.example.offlinemusic.data.database.entities.TrackEntity? = withContext(Dispatchers.IO) {
        trackDao.getTrackById(trackId)
    }

    suspend fun resolveArtistReviewWithExisting(
        reviewId: String,
        rawCreditedString: String,
        existingArtistId: String
    ) = withContext(Dispatchers.IO) {
        artistDao.updateArtistIdForCreditedName(rawCreditedString, existingArtistId)
        artistDao.addAliasToArtist(existingArtistId, rawCreditedString)
        normalizeAndCleanArtistProfiles()
        db.artistReviewQueueDao().updateStatus(reviewId, "RESOLVED")
    }

    suspend fun resolveArtistReviewWithNew(
        reviewId: String,
        rawCreditedString: String,
        newArtistName: String
    ) = withContext(Dispatchers.IO) {
        val norm = ArtistNormalizer.normalize(newArtistName)
        val artistId = ArtistNormalizer.generateArtistId(norm)
        val newArtist = com.example.offlinemusic.data.database.entities.ArtistEntity(
            id = artistId,
            name = newArtistName.trim(),
            normalizedName = norm,
            aliases = rawCreditedString.trim(),
            metadataStatus = "CONFIRMED"
        )
        artistDao.insertOrUpdateArtist(newArtist)
        artistDao.updateArtistIdForCreditedName(rawCreditedString, artistId)
        normalizeAndCleanArtistProfiles()
        db.artistReviewQueueDao().updateStatus(reviewId, "RESOLVED")
    }

    suspend fun dismissArtistReviewForever(reviewId: String) = withContext(Dispatchers.IO) {
        db.artistReviewQueueDao().updateStatus(reviewId, "DISMISSED")
    }

    suspend fun updateTrackMetadata(
        trackId: String,
        newTitle: String,
        rawArtistString: String,
        newAlbum: String?,
        newGenre: String?,
        newArtworkUri: Uri?
    ): Song? = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId) ?: return@withContext null
        val fileUri = Uri.parse(track.fileUri)

        // Parse artists, supporting "/" as multiple artists per user request
        val parsedArtists = ArtistNormalizer.splitCollaborators(rawArtistString)
            .map { ArtistNormalizer.resolveCanonicalName(it) }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("Unknown Artist") }

        // Process cover image bytes if a new artwork Uri was picked
        var artworkBytes: ByteArray? = null
        var finalArtworkUri: String? = track.artworkUri

        if (newArtworkUri != null && newArtworkUri.toString() != track.artworkUri) {
            try {
                context.contentResolver.openInputStream(newArtworkUri)?.use { input ->
                    artworkBytes = input.readBytes()
                }
                if (artworkBytes != null && artworkBytes!!.isNotEmpty()) {
                    val artworkDir = File(context.cacheDir, "artwork").apply { mkdirs() }
                    val artFile = File(artworkDir, "art_${trackId}_${System.currentTimeMillis()}.jpg")
                    artFile.writeBytes(artworkBytes!!)
                    finalArtworkUri = Uri.fromFile(artFile).toString()
                }
            } catch (_: Exception) {}
        }

        // Write directly to physical audio file metadata (ID3v2)
        TrackMetadataWriter.writePhysicalMetadata(
            context = context,
            trackUri = fileUri,
            title = newTitle,
            artists = parsedArtists,
            album = newAlbum,
            genre = newGenre,
            coverBytes = artworkBytes
        )

        // Update database within transaction
        db.withTransaction {
            var targetAlbumId: String? = null
            if (!newAlbum.isNullOrBlank() && !newAlbum.equals("Unknown Album", ignoreCase = true)) {
                val normAlbum = newAlbum.trim().lowercase()
                val albumId = "album_${normAlbum.hashCode().toUInt().toString(16)}"
                val albumEntity = AlbumEntity(
                    id = albumId,
                    title = newAlbum.trim(),
                    normalizedTitle = normAlbum,
                    albumArtist = parsedArtists.firstOrNull() ?: "Various Artists",
                    releaseYear = track.year,
                    artworkUri = finalArtworkUri
                )
                albumDao.insertOrUpdateAlbum(albumEntity)
                targetAlbumId = albumId
            }

            // 1. Update track entity
            val updatedTrack = track.copy(
                title = newTitle.trim(),
                normalizedTitle = newTitle.trim().lowercase(),
                albumId = targetAlbumId,
                artworkUri = finalArtworkUri,
                dateModified = System.currentTimeMillis()
            )
            trackDao.updateTrack(updatedTrack)

            // 2. Update artists & track_artists junction table
            artistDao.deleteTrackArtistsForTrack(trackId)
            val trackArtists = mutableListOf<TrackArtistEntity>()
            var order = 0
            for (artistName in parsedArtists) {
                val norm = ArtistNormalizer.normalize(artistName)
                val artistId = ArtistNormalizer.generateArtistId(norm)
                val artistEntity = ArtistEntity(
                    id = artistId,
                    name = artistName,
                    normalizedName = norm,
                    metadataStatus = "MANUAL"
                )
                artistDao.insertOrUpdateArtist(artistEntity)
                trackArtists.add(
                    TrackArtistEntity(
                        trackId = trackId,
                        artistId = artistId,
                        creditedName = artistName,
                        artistRole = "PRIMARY",
                        artistOrder = order++
                    )
                )
            }
            artistDao.insertTrackArtists(trackArtists)
            artistDao.cleanupOrphanArtists()

            // 3. Update genre & track_genres junction table
            if (!newGenre.isNullOrBlank()) {
                val normGenre = newGenre.trim().lowercase()
                val genreId = "genre_${normGenre.hashCode().toUInt().toString(16)}"
                val genreEntity = GenreEntity(
                    id = genreId,
                    canonicalName = newGenre.trim(),
                    normalizedName = normGenre
                )
                genreDao.insertOrUpdateGenre(genreEntity)
                genreDao.deleteTrackGenresForTrack(trackId)
                genreDao.insertTrackGenres(
                    listOf(
                        TrackGenreEntity(
                            trackId = trackId,
                            genreId = genreId,
                            source = "MANUAL",
                            confidence = 1.0f
                        )
                    )
                )
            }

            albumDao.cleanupOrphanAlbums()
            albumDao.refreshAllAlbumStats()
        }

        normalizeAndCleanArtistProfiles()

        // Return refreshed song
        libraryDao.getTrackWithDetailsById(trackId)?.toSong()
    }

    /**
     * Updates the official album name for all songs belonging to an album.
     * Updates each track's ID3v2 tag on disk and updates the Room database.
     */
    suspend fun updateAlbumName(albumId: String, newAlbumName: String): Album? = withContext(Dispatchers.IO) {
        val trimmed = newAlbumName.trim()
        if (trimmed.isBlank()) return@withContext null
        val oldAlbum = albumDao.getAlbumById(albumId) ?: return@withContext null
        val tracks = libraryDao.getTracksWithDetailsForAlbum(albumId)

        val norm = trimmed.lowercase()
        val newAlbumId = "album_${norm.hashCode().toUInt().toString(16)}"

        // 1. Update physical ID3 tags for all songs in this album
        for (track in tracks) {
            val fileUri = Uri.parse(track.fileUri)
            val artists = ArtistNormalizer.splitCollaborators(track.artistName)
            TrackMetadataWriter.writePhysicalMetadata(
                context = context,
                trackUri = fileUri,
                title = track.title,
                artists = artists,
                album = trimmed,
                genre = track.genre,
                coverBytes = null
            )
        }

        // 2. Update database records within transaction
        db.withTransaction {
            val newAlbumEntity = AlbumEntity(
                id = newAlbumId,
                title = trimmed,
                normalizedTitle = norm,
                albumArtist = oldAlbum.albumArtist,
                releaseYear = oldAlbum.releaseYear,
                artworkUri = oldAlbum.artworkUri
            )
            albumDao.insertOrUpdateAlbum(newAlbumEntity)
            trackDao.updateAlbumForTracks(albumId, newAlbumId)
            albumDao.cleanupOrphanAlbums()
            albumDao.refreshAllAlbumStats()
        }

        getAlbumWithSongs(newAlbumId)
    }

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        trackDao.getAllTracks().map { track ->
            libraryDao.getTrackWithDetailsById(track.id)?.toSong() ?: track.toFallbackSong()
        }
    }

    suspend fun getSongById(songId: String): Song? = withContext(Dispatchers.IO) {
        libraryDao.getTrackWithDetailsById(songId)?.toSong()
    }

    suspend fun getAllAlbumsList(): List<Album> = withContext(Dispatchers.IO) {
        albumDao.getAllAlbums().map { it.toAlbum() }
    }

    suspend fun getAlbumWithSongs(albumId: String): Album? = withContext(Dispatchers.IO) {
        val entity = albumDao.getAlbumById(albumId) ?: return@withContext null
        val tracks = libraryDao.getTracksWithDetailsForAlbum(albumId).map { it.toSong() }
        val firstArt = entity.artworkUri?.let { Uri.parse(it) }
            ?: tracks.firstOrNull { it.artworkUri != null }?.artworkUri

        entity.toAlbum().copy(
            songCount = tracks.size,
            totalDurationMs = tracks.sumOf { it.durationMs },
            artworkUri = firstArt,
            songs = tracks
        )
    }

    suspend fun getArtistProfile(artistName: String): ArtistProfile = withContext(Dispatchers.IO) {
        val canonicalName = ArtistNormalizer.resolveCanonicalName(artistName)
        val norm = ArtistNormalizer.normalize(canonicalName)
        var artistEntity = artistDao.getArtistByNormalizedName(norm)

        // Automatically create and persist profile if it doesn't exist yet
        if (artistEntity == null) {
            val artistId = ArtistNormalizer.generateArtistId(norm)
            artistEntity = ArtistEntity(
                id = artistId,
                name = canonicalName,
                normalizedName = norm,
                metadataStatus = "CONFIRMED"
            )
            artistDao.insertOrUpdateArtist(artistEntity)
        }

        val artistId = artistEntity.id
        var tracks = libraryDao.getTracksWithDetailsForArtist(artistId).map { it.toSong() }

        // If no direct link yet, check if any tracks have this credited name and link them
        if (tracks.isEmpty()) {
            val allRawTracks = trackDao.getAllTracks()
            val matchingTracks = allRawTracks.filter { t ->
                val credits = artistDao.getCreditedArtistNamesForTrack(t.id)
                credits.any { ArtistNormalizer.normalize(it) == norm }
            }
            if (matchingTracks.isNotEmpty()) {
                val relinks = matchingTracks.map { t ->
                    TrackArtistEntity(
                        trackId = t.id,
                        artistId = artistId,
                        creditedName = canonicalName,
                        artistRole = "PRIMARY",
                        artistOrder = 0
                    )
                }
                artistDao.insertTrackArtists(relinks)
                tracks = libraryDao.getTracksWithDetailsForArtist(artistId).map { it.toSong() }
            }
        }

        val albums = libraryDao.getAlbumsForArtist(artistId).map { it.toAlbum() }
        val firstArt = artistEntity.artworkUri?.let { Uri.parse(it) }
            ?: tracks.firstOrNull { it.artworkUri != null }?.artworkUri
            ?: albums.firstOrNull { it.artworkUri != null }?.artworkUri

        ArtistProfile(
            name = artistEntity.name,
            songs = tracks,
            albums = albums,
            totalDurationMs = tracks.sumOf { it.durationMs },
            artworkUri = firstArt
        )
    }

    suspend fun getPlaylistWithSongs(playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext null
        val tracks = if (playlist.playlistType == "LIKED") {
            trackDao.getLikedTracks().mapNotNull { libraryDao.getTrackWithDetailsById(it.id)?.toSong() }
        } else {
            libraryDao.getTracksWithDetailsForPlaylist(playlistId).map { it.toSong() }
        }
        val firstArt = playlist.artworkUri?.let { Uri.parse(it) }
            ?: tracks.firstOrNull { it.artworkUri != null }?.artworkUri

        Playlist(
            id = playlist.id,
            name = playlist.name,
            description = playlist.description,
            songCount = tracks.size,
            totalDurationMs = tracks.sumOf { it.durationMs },
            artworkUri = firstArt,
            playlistType = playlist.playlistType,
            folderSourceId = playlist.folderSourceId,
            songs = tracks
        )
    }

    suspend fun toggleLikeSong(songId: String): Boolean = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(songId) ?: return@withContext false
        val newLiked = !track.isLiked
        trackDao.setLiked(songId, newLiked)

        val likedPlaylist = playlistDao.getLikedSongsPlaylist()
        val likedId = likedPlaylist?.id ?: "liked_songs"
        if (newLiked) {
            val nextPos = playlistDao.getMaxPlaylistPosition(likedId) + 1
            playlistDao.insertPlaylistTracks(
                listOf(PlaylistTrackEntity(playlistId = likedId, trackId = songId, position = nextPos))
            )
        } else {
            playlistDao.removeTrackFromPlaylist(likedId, songId)
        }
        newLiked
    }

    suspend fun isSongLiked(songId: String): Boolean = withContext(Dispatchers.IO) {
        trackDao.getTrackById(songId)?.isLiked ?: false
    }

    suspend fun getLikedSongIds(): Set<String> = withContext(Dispatchers.IO) {
        trackDao.getLikedTrackIds().toSet()
    }

    suspend fun createPlaylist(name: String): String = withContext(Dispatchers.IO) {
        val playlistId = "playlist_${UUID.randomUUID()}"
        val playlist = PlaylistEntity(
            id = playlistId,
            name = name.trim(),
            description = "User Playlist",
            playlistType = "USER",
            createdAt = System.currentTimeMillis()
        )
        playlistDao.insertOrUpdatePlaylist(playlist)
        playlistId
    }

    suspend fun updatePlaylistName(playlistId: String, newName: String) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylistName(playlistId, newName.trim())
    }

    suspend fun updatePlaylistArtwork(playlistId: String, artworkUri: Uri?) = withContext(Dispatchers.IO) {
        val finalUriString = if (artworkUri != null) {
            try {
                val coversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
                val destFile = File(coversDir, "playlist_${playlistId}.jpg")
                context.contentResolver.openInputStream(artworkUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(destFile).toString()
            } catch (e: Exception) {
                artworkUri.toString()
            }
        } else {
            null
        }
        playlistDao.updatePlaylistArtwork(playlistId, finalUriString)
    }

    suspend fun createCustomAlbum(name: String): String = createPlaylist(name)

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun deleteAlbum(albumId: String) = deletePlaylist(albumId)

    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext
        var currentPos = playlistDao.getMaxPlaylistPosition(playlistId)
        val entities = songIds.map { songId ->
            currentPos++
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = songId,
                position = currentPos,
                addedTimestamp = System.currentTimeMillis()
            )
        }
        playlistDao.insertPlaylistTracks(entities)
    }

    suspend fun addSongsToAlbum(albumId: String, songIds: List<String>) = addSongsToPlaylist(albumId, songIds)

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        addSongsToPlaylist(playlistId, listOf(songId))
    }

    suspend fun addSongToAlbum(albumId: String, songId: String) = addSongToPlaylist(albumId, songId)

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, songId)
    }

    suspend fun removeSongFromAlbum(albumId: String, songId: String) = removeSongFromPlaylist(albumId, songId)

    suspend fun moveSongBetweenPlaylists(songId: String, fromPlaylistId: String, toPlaylistId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(fromPlaylistId, songId)
        addSongToPlaylist(toPlaylistId, songId)
    }

    suspend fun moveSongBetweenAlbums(songId: String, fromAlbumId: String, toAlbumId: String) =
        moveSongBetweenPlaylists(songId, fromAlbumId, toAlbumId)

    /**
     * Synchronizes Liked Songs and Folder-based playlists.
     * Ensures:
     * 1. Liked Songs playlist exists and has all liked songs.
     * 2. All discovered songs are placed in a playlist named after their containing folder.
     */
    suspend fun syncFolderAndLikedPlaylists() = withContext(Dispatchers.IO) {
        // 1. Sync Liked Songs
        var likedPlaylist = playlistDao.getLikedSongsPlaylist()
        if (likedPlaylist == null) {
            likedPlaylist = PlaylistEntity(
                id = "liked_songs",
                name = "Liked Songs",
                description = "Songs you've liked",
                playlistType = "LIKED",
                createdAt = 0L
            )
            playlistDao.insertOrUpdatePlaylist(likedPlaylist)
        }
        val likedTracks = trackDao.getLikedTracks()
        playlistDao.clearPlaylistTracks(likedPlaylist.id)
        if (likedTracks.isNotEmpty()) {
            val likedLinks = likedTracks.mapIndexed { idx, t ->
                PlaylistTrackEntity(
                    playlistId = likedPlaylist.id,
                    trackId = t.id,
                    position = idx,
                    addedTimestamp = t.dateAdded
                )
            }
            playlistDao.insertPlaylistTracks(likedLinks)
        }

        // 2. Sync Folder Playlists for every song in the database
        val allTracks = trackDao.getAllTracks()
        val folderTracksMap = mutableMapOf<String, MutableList<com.example.offlinemusic.data.database.entities.TrackEntity>>()
        for (track in allTracks) {
            val rel = track.relativePath.replace('\\', '/')
            val folderName = if (rel.contains('/')) {
                rel.substringBeforeLast('/').substringAfterLast('/').ifEmpty { "Music" }
            } else {
                "Music"
            }.trim()

            folderTracksMap.getOrPut(folderName) { mutableListOf() }.add(track)
        }

        for ((folderName, tracksInFolder) in folderTracksMap) {
            val folderPlaylistId = "folder_playlist_${folderName.hashCode().toUInt().toString(16)}"
            var existing = playlistDao.getPlaylistById(folderPlaylistId)
            val firstArt = tracksInFolder.firstOrNull { it.artworkUri != null }?.artworkUri

            if (existing == null) {
                existing = PlaylistEntity(
                    id = folderPlaylistId,
                    name = folderName,
                    description = "Folder: $folderName",
                    artworkUri = firstArt,
                    playlistType = "FOLDER",
                    createdAt = System.currentTimeMillis()
                )
                playlistDao.insertOrUpdatePlaylist(existing)
            }

            val links = tracksInFolder.mapIndexed { idx, t ->
                PlaylistTrackEntity(
                    playlistId = folderPlaylistId,
                    trackId = t.id,
                    position = t.trackNumber ?: idx,
                    addedTimestamp = t.dateAdded
                )
            }
            playlistDao.insertPlaylistTracks(links)
        }
    }

    /**
     * Records an actual playback event and updates statistics:
     * - Completion threshold: Played at least 30 seconds OR >= 50% of track
     * - Increments play count and last played timestamp
     */
    suspend fun recordPlayEvent(
        trackId: String,
        playedDurationMs: Long,
        totalDurationMs: Long,
        source: String = "ALL_SONGS",
        playlistId: String? = null
    ) = withContext(Dispatchers.IO) {
        if (playedDurationMs < 5000L) return@withContext // Ignore accidental skips under 5 seconds

        val isCompleted = playedDurationMs >= 30_000L || (totalDurationMs > 0 && playedDurationMs >= totalDurationMs / 2)
        val now = System.currentTimeMillis()

        playEventDao.insertPlayEvent(
            PlayEventEntity(
                trackId = trackId,
                playbackTimestamp = now,
                playedDurationMs = playedDurationMs,
                completionStatus = isCompleted,
                playbackSource = source,
                playlistId = playlistId
            )
        )

        if (isCompleted) {
            trackDao.incrementPlayCount(trackId, now)
        }
    }

    fun getRecentlyAdded(limit: Int = 50): Flow<List<Song>> {
        return trackDao.getRecentlyAddedTracks(limit).map { list ->
            list.map { it.toFallbackSong() }
        }
    }

    fun getRecentlyPlayed(limit: Int = 50): Flow<List<Song>> {
        return trackDao.getRecentlyPlayedTracks(limit).map { list ->
            list.map { it.toFallbackSong() }
        }
    }

    fun getMostPlayed(limit: Int = 50): Flow<List<Song>> {
        return trackDao.getMostPlayedTracks(limit).map { list ->
            list.map { it.toFallbackSong() }
        }
    }

    private fun TrackWithDetails.toSong(): Song {
        return Song(
            id = id,
            title = title,
            artist = artistName,
            album = albumTitle,
            albumFolder = albumTitle,
            year = year,
            genre = genre,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            dateModified = dateModified,
            uri = Uri.parse(fileUri),
            relativePath = relativePath,
            trackNumber = trackNumber,
            artworkUri = artworkUri?.let { Uri.parse(it) },
            lyricsUri = lyricsUri
        )
    }

    private fun com.example.offlinemusic.data.database.entities.TrackEntity.toFallbackSong(): Song {
        return Song(
            id = id,
            title = title,
            artist = "Unknown Artist",
            album = "Unknown Album",
            albumFolder = "Music",
            year = year,
            genre = null,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            dateModified = dateModified,
            uri = Uri.parse(fileUri),
            relativePath = relativePath,
            trackNumber = trackNumber,
            artworkUri = artworkUri?.let { Uri.parse(it) },
            lyricsUri = lyricsUri
        )
    }

    private fun AlbumEntity.toAlbum(): Album {
        return Album(
            id = id,
            name = title,
            folderPath = albumArtist ?: "Various Artists",
            songCount = trackCount,
            totalDurationMs = totalDurationMs,
            artworkUri = artworkUri?.let { Uri.parse(it) },
            isCustom = false,
            songs = emptyList()
        )
    }

    private fun PlaylistWithStats.toPlaylist(): Playlist {
        return Playlist(
            id = id,
            name = name,
            description = description,
            songCount = trackCount,
            totalDurationMs = totalDurationMs,
            artworkUri = artworkUri?.let { Uri.parse(it) },
            playlistType = playlistType,
            folderSourceId = folderSourceId,
            songs = emptyList()
        )
    }
}
