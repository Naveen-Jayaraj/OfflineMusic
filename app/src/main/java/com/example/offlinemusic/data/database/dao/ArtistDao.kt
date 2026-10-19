package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.database.entities.TrackArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists ORDER BY normalized_name ASC")
    fun getAllArtistsFlow(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists ORDER BY normalized_name ASC")
    suspend fun getAllArtists(): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getArtistByNormalizedName(normalizedName: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE normalized_name LIKE '%' || :query || '%' ORDER BY normalized_name ASC")
    fun searchArtists(query: String): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtist(artist: ArtistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateArtist(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Update
    suspend fun updateArtist(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackArtists(links: List<TrackArtistEntity>)

    @Query("DELETE FROM track_artists WHERE track_id = :trackId")
    suspend fun deleteTrackArtistsForTrack(trackId: String)

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN track_artists ta ON a.id = ta.artist_id
        WHERE ta.track_id = :trackId
        ORDER BY ta.artist_order ASC
    """)
    suspend fun getArtistsForTrack(trackId: String): List<ArtistEntity>

    @Query("""
        SELECT credited_name FROM track_artists
        WHERE track_id = :trackId
        ORDER BY artist_order ASC
    """)
    suspend fun getCreditedArtistNamesForTrack(trackId: String): List<String>

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT DISTINCT artist_id FROM track_artists)")
    suspend fun cleanupOrphanArtists()
}
