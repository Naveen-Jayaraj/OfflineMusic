package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.offlinemusic.data.database.entities.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY normalized_title ASC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY normalized_title ASC")
    suspend fun getAllTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE file_uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE album_id = :albumId ORDER BY track_number ASC, normalized_title ASC")
    fun getTracksForAlbumFlow(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE album_id = :albumId ORDER BY track_number ASC, normalized_title ASC")
    suspend fun getTracksForAlbum(albumId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE is_liked = 1 ORDER BY date_added DESC")
    fun getLikedTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_liked = 1 ORDER BY date_added DESC")
    suspend fun getLikedTracks(): List<TrackEntity>

    @Query("SELECT id FROM tracks WHERE is_liked = 1")
    fun getLikedTrackIdsFlow(): Flow<List<String>>

    @Query("SELECT id FROM tracks WHERE is_liked = 1")
    suspend fun getLikedTrackIds(): List<String>

    @Query("SELECT * FROM tracks ORDER BY date_added DESC LIMIT :limit")
    fun getRecentlyAddedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE last_played_timestamp IS NOT NULL ORDER BY last_played_timestamp DESC LIMIT :limit")
    fun getRecentlyPlayedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE play_count > 0 ORDER BY play_count DESC, last_played_timestamp DESC LIMIT :limit")
    fun getMostPlayedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE normalized_title LIKE '%' || :query || '%' ORDER BY normalized_title ASC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET is_liked = :isLiked WHERE id = :trackId")
    suspend fun setLiked(trackId: String, isLiked: Boolean)

    @Query("UPDATE tracks SET play_count = play_count + 1, last_played_timestamp = :timestamp WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String, timestamp: Long)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("DELETE FROM tracks WHERE file_uri IN (:uris)")
    suspend fun deleteTracksByUris(uris: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
}
