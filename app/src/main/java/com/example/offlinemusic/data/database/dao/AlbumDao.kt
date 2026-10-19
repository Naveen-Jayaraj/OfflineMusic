package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinemusic.data.database.entities.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY normalized_title ASC")
    fun getAllAlbumsFlow(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY normalized_title ASC")
    suspend fun getAllAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE normalized_title = :normalizedTitle LIMIT 1")
    suspend fun getAlbumByNormalizedTitle(normalizedTitle: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE normalized_title = :normalizedTitle AND (album_artist = :albumArtist OR album_artist IS NULL) LIMIT 1")
    suspend fun getAlbumByTitleAndArtist(normalizedTitle: String, albumArtist: String?): AlbumEntity?

    @Query("SELECT * FROM albums WHERE normalized_title LIKE '%' || :query || '%' OR album_artist LIKE '%' || :query || '%' ORDER BY normalized_title ASC")
    fun searchAlbums(query: String): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAlbum(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteAlbumById(id: String)

    @Query("""
        UPDATE albums
        SET track_count = (SELECT COUNT(*) FROM tracks WHERE tracks.album_id = albums.id),
            total_duration_ms = COALESCE((SELECT SUM(duration_ms) FROM tracks WHERE tracks.album_id = albums.id), 0)
        WHERE id = :albumId
    """)
    suspend fun refreshAlbumStats(albumId: String)

    @Query("""
        UPDATE albums
        SET track_count = (SELECT COUNT(*) FROM tracks WHERE tracks.album_id = albums.id),
            total_duration_ms = COALESCE((SELECT SUM(duration_ms) FROM tracks WHERE tracks.album_id = albums.id), 0)
    """)
    suspend fun refreshAllAlbumStats()

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT album_id FROM tracks WHERE album_id IS NOT NULL)")
    suspend fun cleanupOrphanAlbums()
}
