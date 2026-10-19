package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinemusic.data.database.entities.GenreEntity
import com.example.offlinemusic.data.database.entities.TrackEntity
import com.example.offlinemusic.data.database.entities.TrackGenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {

    @Query("SELECT * FROM genres ORDER BY canonical_name ASC")
    fun getAllGenresFlow(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres ORDER BY canonical_name ASC")
    suspend fun getAllGenres(): List<GenreEntity>

    @Query("SELECT * FROM genres WHERE id = :id LIMIT 1")
    suspend fun getGenreById(id: String): GenreEntity?

    @Query("SELECT * FROM genres WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getGenreByNormalizedName(normalizedName: String): GenreEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genre: GenreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGenre(genre: GenreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackGenres(links: List<TrackGenreEntity>)

    @Query("""
        SELECT g.* FROM genres g
        INNER JOIN track_genres tg ON g.id = tg.genre_id
        WHERE tg.track_id = :trackId
    """)
    suspend fun getGenresForTrack(trackId: String): List<GenreEntity>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN track_genres tg ON t.id = tg.track_id
        WHERE tg.genre_id = :genreId
        ORDER BY t.normalized_title ASC
    """)
    fun getTracksForGenreFlow(genreId: String): Flow<List<TrackEntity>>

    @Query("DELETE FROM track_genres WHERE track_id = :trackId")
    suspend fun deleteTrackGenresForTrack(trackId: String)
}
