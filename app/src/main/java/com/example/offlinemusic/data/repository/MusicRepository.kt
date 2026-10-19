package com.example.offlinemusic.data.repository

import android.content.Context
import android.net.Uri
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.dao.TrackWithDetails
import com.example.offlinemusic.data.database.entities.AlbumEntity
import com.example.offlinemusic.data.database.entities.PlayEventEntity
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.ArtistProfile
import com.example.offlinemusic.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class MusicRepository(context: Context) {

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

    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylistsFlow()

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
        val norm = ArtistNormalizer.normalize(artistName)
        val artistEntity = artistDao.getArtistByNormalizedName(norm)
        val artistId = artistEntity?.id ?: ArtistNormalizer.generateArtistId(artistName)

        val tracks = libraryDao.getTracksWithDetailsForArtist(artistId).map { it.toSong() }
        val albums = libraryDao.getAlbumsForArtist(artistId).map { it.toAlbum() }
        val firstArt = artistEntity?.artworkUri?.let { Uri.parse(it) }
            ?: tracks.firstOrNull { it.artworkUri != null }?.artworkUri
            ?: albums.firstOrNull { it.artworkUri != null }?.artworkUri

        ArtistProfile(
            name = artistEntity?.name ?: artistName,
            songs = tracks,
            albums = albums,
            totalDurationMs = tracks.sumOf { it.durationMs },
            artworkUri = firstArt
        )
    }

    suspend fun getPlaylistWithSongs(playlistId: String): Pair<PlaylistEntity, List<Song>>? = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext null
        val tracks = if (playlist.playlistType == "LIKED") {
            trackDao.getLikedTracks().mapNotNull { libraryDao.getTrackWithDetailsById(it.id)?.toSong() }
        } else {
            libraryDao.getTracksWithDetailsForPlaylist(playlistId).map { it.toSong() }
        }
        Pair(playlist, tracks)
    }

    suspend fun toggleLikeSong(songId: String): Boolean = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(songId) ?: return@withContext false
        val newLiked = !track.isLiked
        trackDao.setLiked(songId, newLiked)

        val likedPlaylist = playlistDao.getLikedSongsPlaylist()
        if (likedPlaylist != null) {
            if (newLiked) {
                val nextPos = playlistDao.getMaxPlaylistPosition(likedPlaylist.id) + 1
                playlistDao.insertPlaylistTracks(
                    listOf(PlaylistTrackEntity(playlistId = likedPlaylist.id, trackId = songId, position = nextPos))
                )
            } else {
                playlistDao.removeTrackFromPlaylist(likedPlaylist.id, songId)
            }
        }
        newLiked
    }

    suspend fun isSongLiked(songId: String): Boolean = withContext(Dispatchers.IO) {
        trackDao.getTrackById(songId)?.isLiked ?: false
    }

    suspend fun getLikedSongIds(): Set<String> = withContext(Dispatchers.IO) {
        trackDao.getLikedTrackIds().toSet()
    }

    suspend fun createCustomAlbum(name: String): String = withContext(Dispatchers.IO) {
        val playlistId = "playlist_${UUID.randomUUID()}"
        val playlist = PlaylistEntity(
            id = playlistId,
            name = name.trim(),
            description = "Custom Playlist",
            playlistType = "USER",
            createdAt = System.currentTimeMillis()
        )
        playlistDao.insertOrUpdatePlaylist(playlist)
        playlistId
    }

    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistById(albumId)
    }

    suspend fun addSongsToAlbum(albumId: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext
        var currentPos = playlistDao.getMaxPlaylistPosition(albumId)
        val entities = songIds.map { songId ->
            currentPos++
            PlaylistTrackEntity(
                playlistId = albumId,
                trackId = songId,
                position = currentPos,
                addedTimestamp = System.currentTimeMillis()
            )
        }
        playlistDao.insertPlaylistTracks(entities)
    }

    suspend fun addSongToAlbum(albumId: String, songId: String) {
        addSongsToAlbum(albumId, listOf(songId))
    }

    suspend fun removeSongFromAlbum(albumId: String, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(albumId, songId)
    }

    suspend fun moveSongBetweenAlbums(songId: String, fromAlbumId: String, toAlbumId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(fromAlbumId, songId)
        addSongToAlbum(toAlbumId, songId)
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
            artworkUri = artworkUri?.let { Uri.parse(it) }
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
            artworkUri = artworkUri?.let { Uri.parse(it) }
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
}
