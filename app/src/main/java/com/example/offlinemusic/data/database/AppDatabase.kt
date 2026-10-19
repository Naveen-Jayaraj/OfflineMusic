package com.example.offlinemusic.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.offlinemusic.data.database.dao.AlbumDao
import com.example.offlinemusic.data.database.dao.ArtistDao
import com.example.offlinemusic.data.database.dao.ArtistReviewQueueDao
import com.example.offlinemusic.data.database.dao.FolderSourceDao
import com.example.offlinemusic.data.database.dao.GenreDao
import com.example.offlinemusic.data.database.dao.LibraryDao
import com.example.offlinemusic.data.database.dao.PlayEventDao
import com.example.offlinemusic.data.database.dao.PlaylistDao
import com.example.offlinemusic.data.database.dao.TrackDao
import com.example.offlinemusic.data.database.entities.AlbumEntity
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.database.entities.ArtistReviewQueueEntity
import com.example.offlinemusic.data.database.entities.FolderSourceEntity
import com.example.offlinemusic.data.database.entities.GenreEntity
import com.example.offlinemusic.data.database.entities.PlayEventEntity
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.database.entities.TrackArtistEntity
import com.example.offlinemusic.data.database.entities.TrackEntity
import com.example.offlinemusic.data.database.entities.TrackGenreEntity

@Database(
    entities = [
        TrackEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        GenreEntity::class,
        TrackArtistEntity::class,
        TrackGenreEntity::class,
        PlayEventEntity::class,
        FolderSourceEntity::class,
        ArtistReviewQueueEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun genreDao(): GenreDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun folderSourceDao(): FolderSourceDao
    abstract fun artistReviewQueueDao(): ArtistReviewQueueDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val DATABASE_NAME = "offline_music.db"
        const val PLAYLIST_ID_LIKED = "liked_songs"
        const val PLAYLIST_ID_RECENTLY_DOWNLOADED = "recently_downloaded"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize permanent special Liked Songs playlist
                            db.execSQL("""
                                INSERT OR IGNORE INTO playlists (id, name, description, playlist_type, created_at, sort_order)
                                VALUES ('$PLAYLIST_ID_LIKED', 'Liked Songs', 'Your favorite tracks', 'LIKED', ${System.currentTimeMillis()}, 'DATE_ADDED')
                            """.trimIndent())
                            // Initialize permanent special Recently Downloaded playlist
                            db.execSQL("""
                                INSERT OR IGNORE INTO playlists (id, name, description, playlist_type, created_at, sort_order)
                                VALUES ('$PLAYLIST_ID_RECENTLY_DOWNLOADED', 'Recently Downloaded', 'Songs downloaded to your device', 'DOWNLOADS', ${System.currentTimeMillis()}, 'DATE_ADDED')
                            """.trimIndent())
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("""
                                INSERT OR IGNORE INTO playlists (id, name, description, playlist_type, created_at, sort_order)
                                VALUES ('$PLAYLIST_ID_RECENTLY_DOWNLOADED', 'Recently Downloaded', 'Songs downloaded to your device', 'DOWNLOADS', ${System.currentTimeMillis()}, 'DATE_ADDED')
                            """.trimIndent())
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new Room tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `artists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalized_name` TEXT NOT NULL,
                        `aliases` TEXT,
                        `external_id` TEXT,
                        `metadata_status` TEXT NOT NULL DEFAULT 'AUTO',
                        `artwork_uri` TEXT,
                        `bio` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_normalized_name` ON `artists` (`normalized_name`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `albums_new` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `normalized_title` TEXT NOT NULL,
                        `album_artist` TEXT,
                        `release_year` INTEGER,
                        `artwork_uri` TEXT,
                        `external_release_id` TEXT,
                        `track_count` INTEGER NOT NULL DEFAULT 0,
                        `total_duration_ms` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_new_normalized_title` ON `albums_new` (`normalized_title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_new_album_artist` ON `albums_new` (`album_artist`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tracks` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `normalized_title` TEXT NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `file_uri` TEXT NOT NULL,
                        `format` TEXT NOT NULL DEFAULT 'mp3',
                        `bitrate` INTEGER,
                        `size_bytes` INTEGER NOT NULL DEFAULT 0,
                        `metadata_status` TEXT NOT NULL DEFAULT 'PARSED',
                        `album_id` TEXT,
                        `year` INTEGER,
                        `track_number` INTEGER,
                        `disc_number` INTEGER DEFAULT 1,
                        `artwork_uri` TEXT,
                        `date_added` INTEGER NOT NULL,
                        `date_modified` INTEGER NOT NULL,
                        `is_liked` INTEGER NOT NULL DEFAULT 0,
                        `play_count` INTEGER NOT NULL DEFAULT 0,
                        `last_played_timestamp` INTEGER,
                        `relative_path` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`album_id`) REFERENCES `albums_new`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_file_uri` ON `tracks` (`file_uri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_normalized_title` ON `tracks` (`normalized_title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_album_id` ON `tracks` (`album_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_is_liked` ON `tracks` (`is_liked`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_date_added` ON `tracks` (`date_added`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_play_count` ON `tracks` (`play_count`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `artwork_uri` TEXT,
                        `playlist_type` TEXT NOT NULL DEFAULT 'USER',
                        `folder_source_id` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `sort_order` TEXT NOT NULL DEFAULT 'MANUAL',
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_playlist_type` ON `playlists` (`playlist_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_folder_source_id` ON `playlists` (`folder_source_id`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                        `playlist_id` TEXT NOT NULL,
                        `track_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL DEFAULT 0,
                        `added_timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`playlist_id`, `track_id`),
                        FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlist_id` ON `playlist_tracks` (`playlist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_track_id` ON `playlist_tracks` (`track_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlist_id_position` ON `playlist_tracks` (`playlist_id`, `position`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `genres` (
                        `id` TEXT NOT NULL,
                        `canonical_name` TEXT NOT NULL,
                        `normalized_name` TEXT NOT NULL,
                        `description` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_genres_normalized_name` ON `genres` (`normalized_name`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `track_artists` (
                        `track_id` TEXT NOT NULL,
                        `artist_id` TEXT NOT NULL,
                        `credited_name` TEXT NOT NULL,
                        `artist_role` TEXT NOT NULL DEFAULT 'PRIMARY',
                        `artist_order` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`track_id`, `artist_id`),
                        FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`artist_id`) REFERENCES `artists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_artists_track_id` ON `track_artists` (`track_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_artists_artist_id` ON `track_artists` (`artist_id`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `track_genres` (
                        `track_id` TEXT NOT NULL,
                        `genre_id` TEXT NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'EMBEDDED',
                        `confidence` REAL NOT NULL DEFAULT 1.0,
                        PRIMARY KEY(`track_id`, `genre_id`),
                        FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`genre_id`) REFERENCES `genres`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_genres_track_id` ON `track_genres` (`track_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_genres_genre_id` ON `track_genres` (`genre_id`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `play_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `track_id` TEXT NOT NULL,
                        `playback_timestamp` INTEGER NOT NULL,
                        `played_duration_ms` INTEGER NOT NULL,
                        `completion_status` INTEGER NOT NULL,
                        `playback_source` TEXT NOT NULL DEFAULT 'ALL_SONGS',
                        `playlist_id` TEXT,
                        FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_track_id` ON `play_events` (`track_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_playback_timestamp` ON `play_events` (`playback_timestamp`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `folder_sources` (
                        `id` TEXT NOT NULL,
                        `folder_uri` TEXT NOT NULL,
                        `folder_name` TEXT NOT NULL,
                        `scan_status` TEXT NOT NULL DEFAULT 'IDLE',
                        `last_synced_timestamp` INTEGER NOT NULL DEFAULT 0,
                        `track_count` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folder_sources_folder_uri` ON `folder_sources` (`folder_uri`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `artist_review_queue` (
                        `id` TEXT NOT NULL,
                        `track_id` TEXT NOT NULL,
                        `raw_credited_string` TEXT NOT NULL,
                        `suggested_artist_ids` TEXT NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_artist_review_queue_track_id` ON `artist_review_queue` (`track_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_artist_review_queue_status` ON `artist_review_queue` (`status`)")

                // 2. Migrate legacy data if old tables exist
                try {
                    // Migrate albums
                    db.execSQL("""
                        INSERT OR IGNORE INTO albums_new (id, title, normalized_title, album_artist, artwork_uri, track_count)
                        SELECT id, name, LOWER(TRIM(name)), 'Various Artists', artwork_uri, 0
                        FROM albums WHERE is_custom = 0 AND id != '$PLAYLIST_ID_LIKED'
                    """.trimIndent())

                    // Migrate legacy custom albums to playlists
                    db.execSQL("""
                        INSERT OR IGNORE INTO playlists (id, name, description, artwork_uri, playlist_type, created_at)
                        SELECT id, name, 'Custom Album', artwork_uri, 'USER', created_at
                        FROM albums WHERE is_custom = 1 AND id != '$PLAYLIST_ID_LIKED'
                    """.trimIndent())

                    // Migrate folder albums to playlists as well (Spec Section 5)
                    db.execSQL("""
                        INSERT OR IGNORE INTO playlists (id, name, description, artwork_uri, playlist_type, created_at)
                        SELECT id, name, 'Folder Playlist', artwork_uri, 'FOLDER', created_at
                        FROM albums WHERE is_custom = 0 AND id != '$PLAYLIST_ID_LIKED'
                    """.trimIndent())

                    // Migrate Liked Songs playlist
                    db.execSQL("""
                        INSERT OR IGNORE INTO playlists (id, name, description, playlist_type, created_at, sort_order)
                        VALUES ('$PLAYLIST_ID_LIKED', 'Liked Songs', 'Your favorite tracks', 'LIKED', ${System.currentTimeMillis()}, 'DATE_ADDED')
                    """.trimIndent())

                    // Migrate legacy songs to tracks
                    db.execSQL("""
                        INSERT OR IGNORE INTO tracks (
                            id, title, normalized_title, duration_ms, file_uri, format, size_bytes,
                            metadata_status, album_id, year, track_number, artwork_uri, date_added,
                            date_modified, is_liked, relative_path
                        )
                        SELECT 
                            id, title, LOWER(TRIM(title)), duration_ms, uri, 'mp3', size_bytes,
                            'PARSED', album_folder, year, track_number, artwork_uri, date_modified,
                            date_modified, is_liked, relative_path
                        FROM songs
                    """.trimIndent())

                    // Extract artists from legacy songs table
                    db.execSQL("""
                        INSERT OR IGNORE INTO artists (id, name, normalized_name, metadata_status)
                        SELECT DISTINCT 
                            LOWER(TRIM(artist)), artist, LOWER(TRIM(artist)), 'AUTO'
                        FROM songs WHERE artist IS NOT NULL AND TRIM(artist) != ''
                    """.trimIndent())

                    // Link track artists
                    db.execSQL("""
                        INSERT OR IGNORE INTO track_artists (track_id, artist_id, credited_name, artist_role, artist_order)
                        SELECT id, LOWER(TRIM(artist)), artist, 'PRIMARY', 0
                        FROM songs WHERE artist IS NOT NULL AND TRIM(artist) != ''
                    """.trimIndent())

                    // Migrate legacy album_songs to playlist_tracks
                    db.execSQL("""
                        INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position, added_timestamp)
                        SELECT album_id, song_id, track_order, ${System.currentTimeMillis()}
                        FROM album_songs
                    """.trimIndent())

                    // Drop legacy tables
                    db.execSQL("DROP TABLE IF EXISTS `album_songs`")
                    db.execSQL("DROP TABLE IF EXISTS `songs`")
                    db.execSQL("DROP TABLE IF EXISTS `albums`")
                } catch (_: Exception) {
                    // Safe catch if legacy tables were already cleaned or altered
                }

                // Finalize albums table name
                db.execSQL("ALTER TABLE `albums_new` RENAME TO `albums`")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `lyrics_uri` TEXT DEFAULT NULL")
            }
        }
    }
}
