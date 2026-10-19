package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.offlinemusic.data.database.entities.PlayEventEntity
import kotlinx.coroutines.flow.Flow

data class PlayStats(
    val id: String,
    val name: String,
    val totalPlays: Int,
    val totalDurationMs: Long
)

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insertPlayEvent(event: PlayEventEntity): Long

    @Query("SELECT * FROM play_events ORDER BY playback_timestamp DESC LIMIT :limit")
    fun getRecentPlayEvents(limit: Int = 100): Flow<List<PlayEventEntity>>

    @Query("SELECT COUNT(*) FROM play_events WHERE track_id = :trackId")
    suspend fun getPlayCountForTrack(trackId: String): Int

    @Query("""
        SELECT a.id, a.name, COUNT(pe.id) AS totalPlays, COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs
        FROM artists a
        INNER JOIN track_artists ta ON a.id = ta.artist_id
        INNER JOIN play_events pe ON ta.track_id = pe.track_id
        WHERE pe.completion_status = 1
        GROUP BY a.id
        ORDER BY totalPlays DESC
        LIMIT :limit
    """)
    fun getTopArtistsByPlays(limit: Int = 10): Flow<List<PlayStats>>

    @Query("""
        SELECT alb.id, alb.title AS name, COUNT(pe.id) AS totalPlays, COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs
        FROM albums alb
        INNER JOIN tracks t ON alb.id = t.album_id
        INNER JOIN play_events pe ON t.id = pe.track_id
        WHERE pe.completion_status = 1
        GROUP BY alb.id
        ORDER BY totalPlays DESC
        LIMIT :limit
    """)
    fun getTopAlbumsByPlays(limit: Int = 10): Flow<List<PlayStats>>
}
