package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.example.offlinemusic.data.database.entities.AlbumEntity
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.database.entities.TrackEntity
import kotlinx.coroutines.flow.Flow

data class TrackWithDetails(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val durationMs: Long,
    val fileUri: String,
    val artworkUri: String?,
    val isLiked: Boolean,
    val trackNumber: Int?,
    val year: Int?,
    val genre: String?,
    val sizeBytes: Long,
    val dateModified: Long,
    val relativePath: String,
    val albumId: String?,
    val lyricsUri: String? = null,
    val producer: String? = null,
    val composer: String? = null,
    val language: String? = null,
    val mood: String? = null,
    val source: String? = "LOCAL",
    val lyricsAvailable: Boolean = false,
    val metachecked: Boolean = false,
    val dateAdded: Long = 0L,
    val playCount: Int = 0,
    val playlistAddedTimestamp: Long? = null
)

@Dao
interface LibraryDao {

    @Query("""
        SELECT t.id, t.title,
               COALESCE((
                   SELECT GROUP_CONCAT(COALESCE(a.name, ta.credited_name), ', ')
                   FROM track_artists ta
                   LEFT JOIN artists a ON ta.artist_id = a.id
                   WHERE ta.track_id = t.id
               ), t.artist, 'Unknown Artist') AS artistName,
               COALESCE(a.title, t.album, 'Unknown Album') AS albumTitle,
               t.duration_ms AS durationMs,
               t.file_uri AS fileUri,
               COALESCE(t.artwork_uri, a.artwork_uri) AS artworkUri,
               t.is_liked AS isLiked,
               t.track_number AS trackNumber,
               t.year,
               COALESCE((
                   SELECT g.canonical_name FROM genres g
                   INNER JOIN track_genres tg ON g.id = tg.genre_id
                   WHERE tg.track_id = t.id LIMIT 1
               ), t.genre) AS genre,
               t.size_bytes AS sizeBytes,
               t.date_modified AS dateModified,
               t.relative_path AS relativePath,
               t.album_id AS albumId,
               t.lyrics_uri AS lyricsUri,
               t.producer AS producer,
               t.composer AS composer,
               t.language AS language,
               t.mood AS mood,
               COALESCE(t.source, 'LOCAL') AS source,
               CASE WHEN (t.lyrics_available = 1 OR (t.lyrics_uri IS NOT NULL AND t.lyrics_uri != '')) THEN 1 ELSE 0 END AS lyricsAvailable,
               COALESCE(t.metachecked, 0) AS metachecked,
               COALESCE(t.date_added, 0) AS dateAdded,
               COALESCE(t.play_count, 0) AS playCount,
               COALESCE(t.date_added, 0) AS playlistAddedTimestamp
        FROM tracks t
        LEFT JOIN albums a ON t.album_id = a.id
        ORDER BY t.normalized_title ASC
    """)
    fun getAllTracksWithDetailsFlow(): Flow<List<TrackWithDetails>>

    @Query("""
        SELECT t.id, t.title,
               COALESCE((
                   SELECT GROUP_CONCAT(COALESCE(a.name, ta.credited_name), ', ')
                   FROM track_artists ta
                   LEFT JOIN artists a ON ta.artist_id = a.id
                   WHERE ta.track_id = t.id
               ), t.artist, 'Unknown Artist') AS artistName,
               COALESCE(a.title, t.album, 'Unknown Album') AS albumTitle,
               t.duration_ms AS durationMs,
               t.file_uri AS fileUri,
               COALESCE(t.artwork_uri, a.artwork_uri) AS artworkUri,
               t.is_liked AS isLiked,
               t.track_number AS trackNumber,
               t.year,
               COALESCE((
                   SELECT g.canonical_name FROM genres g
                   INNER JOIN track_genres tg ON g.id = tg.genre_id
                   WHERE tg.track_id = t.id LIMIT 1
               ), t.genre) AS genre,
               t.size_bytes AS sizeBytes,
               t.date_modified AS dateModified,
               t.relative_path AS relativePath,
               t.album_id AS albumId,
               t.lyrics_uri AS lyricsUri,
               t.producer AS producer,
               t.composer AS composer,
               t.language AS language,
               t.mood AS mood,
               COALESCE(t.source, 'LOCAL') AS source,
               CASE WHEN (t.lyrics_available = 1 OR (t.lyrics_uri IS NOT NULL AND t.lyrics_uri != '')) THEN 1 ELSE 0 END AS lyricsAvailable,
               COALESCE(t.metachecked, 0) AS metachecked,
               COALESCE(t.date_added, 0) AS dateAdded,
               COALESCE(t.play_count, 0) AS playCount,
               COALESCE(t.date_added, 0) AS playlistAddedTimestamp
        FROM tracks t
        LEFT JOIN albums a ON t.album_id = a.id
        WHERE t.id = :trackId LIMIT 1
    """)
    suspend fun getTrackWithDetailsById(trackId: String): TrackWithDetails?

    @Query("""
        SELECT t.id, t.title,
               COALESCE((
                   SELECT GROUP_CONCAT(COALESCE(a.name, ta.credited_name), ', ')
                   FROM track_artists ta
                   LEFT JOIN artists a ON ta.artist_id = a.id
                   WHERE ta.track_id = t.id
               ), t.artist, 'Unknown Artist') AS artistName,
               COALESCE(a.title, t.album, 'Unknown Album') AS albumTitle,
               t.duration_ms AS durationMs,
               t.file_uri AS fileUri,
               COALESCE(t.artwork_uri, a.artwork_uri) AS artworkUri,
               t.is_liked AS isLiked,
               t.track_number AS trackNumber,
               t.year,
               COALESCE((
                   SELECT g.canonical_name FROM genres g
                   INNER JOIN track_genres tg ON g.id = tg.genre_id
                   WHERE tg.track_id = t.id LIMIT 1
               ), t.genre) AS genre,
               t.size_bytes AS sizeBytes,
               t.date_modified AS dateModified,
               t.relative_path AS relativePath,
               t.album_id AS albumId,
               t.lyrics_uri AS lyricsUri,
               t.producer AS producer,
               t.composer AS composer,
               t.language AS language,
               t.mood AS mood,
               COALESCE(t.source, 'LOCAL') AS source,
               CASE WHEN (t.lyrics_available = 1 OR (t.lyrics_uri IS NOT NULL AND t.lyrics_uri != '')) THEN 1 ELSE 0 END AS lyricsAvailable,
               COALESCE(t.metachecked, 0) AS metachecked,
               COALESCE(t.date_added, 0) AS dateAdded,
               COALESCE(t.play_count, 0) AS playCount,
               COALESCE(t.date_added, 0) AS playlistAddedTimestamp
        FROM tracks t
        LEFT JOIN albums a ON t.album_id = a.id
        WHERE t.album_id = :albumId
        ORDER BY t.track_number ASC, t.normalized_title ASC
    """)
    suspend fun getTracksWithDetailsForAlbum(albumId: String): List<TrackWithDetails>

    @Query("""
        SELECT t.id, t.title,
               COALESCE((
                   SELECT GROUP_CONCAT(COALESCE(a.name, ta.credited_name), ', ')
                   FROM track_artists ta
                   LEFT JOIN artists a ON ta.artist_id = a.id
                   WHERE ta.track_id = t.id
               ), t.artist, 'Unknown Artist') AS artistName,
               COALESCE(a.title, t.album, 'Unknown Album') AS albumTitle,
               t.duration_ms AS durationMs,
               t.file_uri AS fileUri,
               COALESCE(t.artwork_uri, a.artwork_uri) AS artworkUri,
               t.is_liked AS isLiked,
               t.track_number AS trackNumber,
               t.year,
               COALESCE((
                   SELECT g.canonical_name FROM genres g
                   INNER JOIN track_genres tg ON g.id = tg.genre_id
                   WHERE tg.track_id = t.id LIMIT 1
               ), t.genre) AS genre,
               t.size_bytes AS sizeBytes,
               t.date_modified AS dateModified,
               t.relative_path AS relativePath,
               t.album_id AS albumId,
               t.lyrics_uri AS lyricsUri,
               t.producer AS producer,
               t.composer AS composer,
               t.language AS language,
               t.mood AS mood,
               COALESCE(t.source, 'LOCAL') AS source,
               CASE WHEN (t.lyrics_available = 1 OR (t.lyrics_uri IS NOT NULL AND t.lyrics_uri != '')) THEN 1 ELSE 0 END AS lyricsAvailable,
               COALESCE(t.metachecked, 0) AS metachecked,
               COALESCE(t.date_added, 0) AS dateAdded,
               COALESCE(t.play_count, 0) AS playCount,
               COALESCE(t.date_added, 0) AS playlistAddedTimestamp
        FROM tracks t
        INNER JOIN track_artists ta ON t.id = ta.track_id
        LEFT JOIN albums a ON t.album_id = a.id
        WHERE ta.artist_id = :artistId
        ORDER BY t.normalized_title ASC
    """)
    suspend fun getTracksWithDetailsForArtist(artistId: String): List<TrackWithDetails>

    @Query("""
        SELECT t.id, t.title,
               COALESCE((
                   SELECT GROUP_CONCAT(COALESCE(a.name, ta.credited_name), ', ')
                   FROM track_artists ta
                   LEFT JOIN artists a ON ta.artist_id = a.id
                   WHERE ta.track_id = t.id
               ), t.artist, 'Unknown Artist') AS artistName,
               COALESCE(a.title, t.album, 'Unknown Album') AS albumTitle,
               t.duration_ms AS durationMs,
               t.file_uri AS fileUri,
               COALESCE(t.artwork_uri, a.artwork_uri) AS artworkUri,
               t.is_liked AS isLiked,
               t.track_number AS trackNumber,
               t.year,
               COALESCE((
                   SELECT g.canonical_name FROM genres g
                   INNER JOIN track_genres tg ON g.id = tg.genre_id
                   WHERE tg.track_id = t.id LIMIT 1
               ), t.genre) AS genre,
               t.size_bytes AS sizeBytes,
               t.date_modified AS dateModified,
               t.relative_path AS relativePath,
               t.album_id AS albumId,
               t.lyrics_uri AS lyricsUri,
               t.producer AS producer,
               t.composer AS composer,
               t.language AS language,
               t.mood AS mood,
               COALESCE(t.source, 'LOCAL') AS source,
               CASE WHEN (t.lyrics_available = 1 OR (t.lyrics_uri IS NOT NULL AND t.lyrics_uri != '')) THEN 1 ELSE 0 END AS lyricsAvailable,
               COALESCE(t.metachecked, 0) AS metachecked,
               COALESCE(t.date_added, 0) AS dateAdded,
               COALESCE(t.play_count, 0) AS playCount,
               COALESCE(pt.added_timestamp, t.date_added, 0) AS playlistAddedTimestamp
        FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.track_id
        LEFT JOIN albums a ON t.album_id = a.id
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC, pt.added_timestamp ASC
    """)
    suspend fun getTracksWithDetailsForPlaylist(playlistId: String): List<TrackWithDetails>

    @Query("""
        SELECT DISTINCT a.* FROM albums a
        INNER JOIN tracks t ON a.id = t.album_id
        INNER JOIN track_artists ta ON t.id = ta.track_id
        WHERE ta.artist_id = :artistId
        ORDER BY a.normalized_title ASC
    """)
    suspend fun getAlbumsForArtist(artistId: String): List<AlbumEntity>
}
