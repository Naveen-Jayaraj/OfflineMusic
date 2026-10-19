package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.database.entities.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY CASE WHEN playlist_type = 'LIKED' THEN 0 WHEN playlist_type = 'USER' THEN 1 ELSE 2 END, name COLLATE NOCASE ASC")
    fun getAllPlaylistsFlow(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE playlist_type = :type ORDER BY name COLLATE NOCASE ASC")
    fun getPlaylistsByTypeFlow(type: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE folder_source_id = :folderSourceId LIMIT 1")
    suspend fun getPlaylistByFolderSourceId(folderSourceId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE playlist_type = 'LIKED' LIMIT 1")
    suspend fun getLikedSongsPlaylist(): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId AND playlist_type != 'LIKED'")
    suspend fun deletePlaylistById(playlistId: String)

    // Playlist tracks
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrackEntity>)

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.track_id
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC, pt.added_timestamp ASC
    """)
    fun getTracksForPlaylistFlow(playlistId: String): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.track_id
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC, pt.added_timestamp ASC
    """)
    suspend fun getTracksForPlaylist(playlistId: String): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun getPlaylistTrackCount(playlistId: String): Int

    @Query("SELECT COALESCE(MAX(position), 0) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun getMaxPlaylistPosition(playlistId: String): Int

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("UPDATE playlist_tracks SET position = :newPosition WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun updateTrackPosition(playlistId: String, trackId: String, newPosition: Int)
}
