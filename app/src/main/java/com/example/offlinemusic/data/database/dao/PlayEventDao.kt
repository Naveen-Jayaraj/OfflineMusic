package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.offlinemusic.data.database.entities.PlayEventEntity
import com.example.offlinemusic.data.model.AlbumStat
import com.example.offlinemusic.data.model.ArtistStat
import com.example.offlinemusic.data.model.GenreStat
import com.example.offlinemusic.data.model.ListeningOverviewStats
import com.example.offlinemusic.data.model.MoodStat
import com.example.offlinemusic.data.model.PlayHistoryItem
import com.example.offlinemusic.data.model.SongStat
import kotlinx.coroutines.flow.Flow

data class PlayStats(
    val id: String,
    val name: String,
    val totalPlays: Int,
    val totalDurationMs: Long
)

data class PlayEventTrackInfo(
    val trackId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val genre: String?,
    val mood: String?,
    val language: String?,
    val playbackTimestamp: Long,
    val playedDurationMs: Long,
    val completionStatus: Boolean
)

@Dao
interface PlayEventDao {

    @Query("""
        SELECT pe.track_id AS trackId,
               t.title AS title,
               COALESCE(t.artist, 'Unknown Artist') AS artistName,
               COALESCE(t.album, 'Unknown Album') AS albumTitle,
               t.genre AS genre,
               t.mood AS mood,
               t.language AS language,
               pe.playback_timestamp AS playbackTimestamp,
               pe.played_duration_ms AS playedDurationMs,
               pe.completion_status AS completionStatus
        FROM play_events pe
        INNER JOIN tracks t ON pe.track_id = t.id
        WHERE pe.playback_timestamp >= :sinceTimestamp
        ORDER BY pe.playback_timestamp DESC
    """)
    suspend fun getPlayEventsWithTrackInfo(sinceTimestamp: Long): List<PlayEventTrackInfo>

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

    @Query("""
        SELECT 
            COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs,
            COUNT(pe.id) AS totalPlays,
            COALESCE(SUM(CASE WHEN pe.completion_status = 1 THEN 1 ELSE 0 END), 0) AS completedPlays,
            COALESCE(SUM(CASE WHEN pe.completion_status = 0 THEN 1 ELSE 0 END), 0) AS skippedPlays,
            COUNT(DISTINCT pe.track_id) AS uniqueTracksCount,
            COUNT(DISTINCT COALESCE(t.artist, 'Unknown')) AS uniqueArtistsCount
        FROM play_events pe
        INNER JOIN tracks t ON pe.track_id = t.id
        WHERE pe.playback_timestamp >= :sinceTimestamp
    """)
    fun getListeningOverview(sinceTimestamp: Long = 0L): Flow<ListeningOverviewStats?>

    @Query("""
        SELECT 
            t.id AS trackId,
            t.title AS title,
            COALESCE((
                SELECT GROUP_CONCAT(COALESCE(a.name, ta.credited_name), ', ')
                FROM track_artists ta
                LEFT JOIN artists a ON ta.artist_id = a.id
                WHERE ta.track_id = t.id
            ), t.artist, 'Unknown Artist') AS artistName,
            COALESCE(alb.title, t.album, 'Unknown Album') AS albumTitle,
            COALESCE(t.artwork_uri, alb.artwork_uri) AS artworkUri,
            t.duration_ms AS durationMs,
            COUNT(pe.id) AS playCount,
            COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs,
            MAX(pe.playback_timestamp) AS lastPlayedTimestamp
        FROM tracks t
        INNER JOIN play_events pe ON t.id = pe.track_id
        LEFT JOIN albums alb ON t.album_id = alb.id
        WHERE pe.playback_timestamp >= :sinceTimestamp
        GROUP BY t.id
        ORDER BY totalDurationMs DESC, playCount DESC
        LIMIT :limit
    """)
    fun getTopSongs(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<SongStat>>

    @Query("""
        SELECT 
            COALESCE(a.id, 'artist_' || LOWER(TRIM(t.artist))) AS artistId,
            COALESCE(a.name, t.artist, 'Unknown Artist') AS artistName,
            COALESCE(a.artwork_uri, t.artwork_uri) AS artworkUri,
            COUNT(pe.id) AS playCount,
            COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs,
            COUNT(DISTINCT t.id) AS trackCount
        FROM tracks t
        INNER JOIN play_events pe ON t.id = pe.track_id
        LEFT JOIN track_artists ta ON t.id = ta.track_id
        LEFT JOIN artists a ON ta.artist_id = a.id
        WHERE pe.playback_timestamp >= :sinceTimestamp
          AND COALESCE(a.name, t.artist) IS NOT NULL
          AND TRIM(COALESCE(a.name, t.artist)) != ''
        GROUP BY artistName
        ORDER BY totalDurationMs DESC, playCount DESC
        LIMIT :limit
    """)
    fun getTopArtists(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<ArtistStat>>

    @Query("""
        SELECT 
            t.mood AS mood,
            COUNT(pe.id) AS playCount,
            COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs,
            COUNT(DISTINCT t.id) AS trackCount
        FROM tracks t
        INNER JOIN play_events pe ON t.id = pe.track_id
        WHERE pe.playback_timestamp >= :sinceTimestamp
          AND t.mood IS NOT NULL
          AND TRIM(t.mood) != ''
        GROUP BY t.mood
        ORDER BY totalDurationMs DESC, playCount DESC
        LIMIT :limit
    """)
    fun getTopMoods(sinceTimestamp: Long = 0L, limit: Int = 20): Flow<List<MoodStat>>

    @Query("""
        SELECT 
            COALESCE(g.canonical_name, t.genre, 'Unknown') AS genre,
            COUNT(pe.id) AS playCount,
            COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs,
            COUNT(DISTINCT t.id) AS trackCount
        FROM tracks t
        INNER JOIN play_events pe ON t.id = pe.track_id
        LEFT JOIN track_genres tg ON t.id = tg.track_id
        LEFT JOIN genres g ON tg.genre_id = g.id
        WHERE pe.playback_timestamp >= :sinceTimestamp
          AND COALESCE(g.canonical_name, t.genre) IS NOT NULL
          AND TRIM(COALESCE(g.canonical_name, t.genre)) != ''
        GROUP BY genre
        ORDER BY totalDurationMs DESC, playCount DESC
        LIMIT :limit
    """)
    fun getTopGenres(sinceTimestamp: Long = 0L, limit: Int = 20): Flow<List<GenreStat>>

    @Query("""
        SELECT 
            COALESCE(alb.id, 'album_' || LOWER(TRIM(t.album))) AS albumId,
            COALESCE(alb.title, t.album, 'Unknown Album') AS albumTitle,
            COALESCE(alb.album_artist, t.artist, 'Unknown Artist') AS artistName,
            COALESCE(alb.artwork_uri, t.artwork_uri) AS artworkUri,
            COUNT(pe.id) AS playCount,
            COALESCE(SUM(pe.played_duration_ms), 0) AS totalDurationMs
        FROM tracks t
        INNER JOIN play_events pe ON t.id = pe.track_id
        LEFT JOIN albums alb ON t.album_id = alb.id
        WHERE pe.playback_timestamp >= :sinceTimestamp
          AND COALESCE(alb.title, t.album) IS NOT NULL
          AND TRIM(COALESCE(alb.title, t.album)) != ''
        GROUP BY albumTitle
        ORDER BY totalDurationMs DESC, playCount DESC
        LIMIT :limit
    """)
    fun getTopAlbums(sinceTimestamp: Long = 0L, limit: Int = 30): Flow<List<AlbumStat>>

    @Query("""
        SELECT 
            pe.id AS eventId,
            pe.track_id AS trackId,
            t.title AS title,
            COALESCE((
                SELECT GROUP_CONCAT(COALESCE(a.name, ta.credited_name), ', ')
                FROM track_artists ta
                LEFT JOIN artists a ON ta.artist_id = a.id
                WHERE ta.track_id = t.id
            ), t.artist, 'Unknown Artist') AS artistName,
            COALESCE(alb.title, t.album, 'Unknown Album') AS albumTitle,
            COALESCE(t.artwork_uri, alb.artwork_uri) AS artworkUri,
            pe.playback_timestamp AS playbackTimestamp,
            pe.played_duration_ms AS playedDurationMs,
            pe.completion_status AS completionStatus,
            pe.playback_source AS playbackSource
        FROM play_events pe
        INNER JOIN tracks t ON pe.track_id = t.id
        LEFT JOIN albums alb ON t.album_id = alb.id
        ORDER BY pe.playback_timestamp DESC
        LIMIT :limit
    """)
    fun getRecentHistory(limit: Int = 100): Flow<List<PlayHistoryItem>>
}
