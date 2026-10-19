package com.example.offlinemusic.data.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.Song
import java.util.UUID

class MusicDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "offline_music.db"
        private const val DATABASE_VERSION = 3

        const val ALBUM_ID_LIKED = "liked_songs"

        private const val TABLE_SONGS = "songs"
        private const val COL_SONG_ID = "id"
        private const val COL_URI = "uri"
        private const val COL_TITLE = "title"
        private const val COL_ARTIST = "artist"
        private const val COL_ALBUM = "album"
        private const val COL_ALBUM_FOLDER = "album_folder"
        private const val COL_YEAR = "year"
        private const val COL_GENRE = "genre"
        private const val COL_DURATION_MS = "duration_ms"
        private const val COL_SIZE_BYTES = "size_bytes"
        private const val COL_DATE_MODIFIED = "date_modified"
        private const val COL_RELATIVE_PATH = "relative_path"
        private const val COL_TRACK_NUMBER = "track_number"
        private const val COL_ARTWORK_URI = "artwork_uri"
        private const val COL_IS_LIKED = "is_liked"

        private const val TABLE_ALBUMS = "albums"
        private const val COL_ALBUM_ID = "id"
        private const val COL_ALBUM_NAME = "name"
        private const val COL_FOLDER_PATH = "folder_path"
        private const val COL_ALBUM_ARTWORK = "artwork_uri"
        private const val COL_IS_CUSTOM = "is_custom"
        private const val COL_CREATED_AT = "created_at"

        private const val TABLE_ALBUM_SONGS = "album_songs"
        private const val COL_AS_ALBUM_ID = "album_id"
        private const val COL_AS_SONG_ID = "song_id"
        private const val COL_TRACK_ORDER = "track_order"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_SONGS (
                $COL_SONG_ID TEXT PRIMARY KEY,
                $COL_URI TEXT UNIQUE NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_ALBUM TEXT NOT NULL,
                $COL_ALBUM_FOLDER TEXT NOT NULL,
                $COL_YEAR INTEGER,
                $COL_GENRE TEXT,
                $COL_DURATION_MS INTEGER NOT NULL,
                $COL_SIZE_BYTES INTEGER NOT NULL,
                $COL_DATE_MODIFIED INTEGER NOT NULL,
                $COL_RELATIVE_PATH TEXT NOT NULL,
                $COL_TRACK_NUMBER INTEGER,
                $COL_ARTWORK_URI TEXT,
                $COL_IS_LIKED INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_ALBUMS (
                $COL_ALBUM_ID TEXT PRIMARY KEY,
                $COL_ALBUM_NAME TEXT NOT NULL,
                $COL_FOLDER_PATH TEXT NOT NULL,
                $COL_ALBUM_ARTWORK TEXT,
                $COL_IS_CUSTOM INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_ALBUM_SONGS (
                $COL_AS_ALBUM_ID TEXT NOT NULL,
                $COL_AS_SONG_ID TEXT NOT NULL,
                $COL_TRACK_ORDER INTEGER DEFAULT 0,
                PRIMARY KEY ($COL_AS_ALBUM_ID, $COL_AS_SONG_ID)
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX idx_songs_artist ON $TABLE_SONGS ($COL_ARTIST)")
        db.execSQL("CREATE INDEX idx_songs_title ON $TABLE_SONGS ($COL_TITLE)")
        db.execSQL("CREATE INDEX idx_album_songs_album ON $TABLE_ALBUM_SONGS ($COL_AS_ALBUM_ID)")
        db.execSQL("CREATE INDEX idx_album_songs_song ON $TABLE_ALBUM_SONGS ($COL_AS_SONG_ID)")

        ensureLikedSongsAlbum(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_ALBUM_SONGS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_ALBUMS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SONGS")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_SONGS ADD COLUMN $COL_IS_LIKED INTEGER DEFAULT 0")
            } catch (_: Exception) {}
            ensureLikedSongsAlbum(db)
        }
    }

    private fun ensureLikedSongsAlbum(db: SQLiteDatabase) {
        val likedValues = ContentValues().apply {
            put(COL_ALBUM_ID, ALBUM_ID_LIKED)
            put(COL_ALBUM_NAME, "Liked Songs")
            put(COL_FOLDER_PATH, "Favorites")
            put(COL_IS_CUSTOM, 1)
            put(COL_CREATED_AT, 0L)
        }
        db.insertWithOnConflict(TABLE_ALBUMS, null, likedValues, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getAllSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val db = readableDatabase
        val cursor = db.query(TABLE_SONGS, null, null, null, null, null, "$COL_TITLE COLLATE NOCASE ASC")
        cursor.use { c ->
            while (c.moveToNext()) {
                songs.add(cursorToSong(c))
            }
        }
        return songs
    }

    fun getSongById(songId: String): Song? {
        val db = readableDatabase
        val cursor = db.query(TABLE_SONGS, null, "$COL_SONG_ID = ?", arrayOf(songId), null, null, null)
        cursor.use { c ->
            if (c.moveToNext()) {
                return cursorToSong(c)
            }
        }
        return null
    }

    data class CachedFileInfo(val song: Song, val dateModified: Long, val sizeBytes: Long)

    fun getCachedFileMap(): Map<String, CachedFileInfo> {
        val map = mutableMapOf<String, CachedFileInfo>()
        for (song in getAllSongs()) {
            map[song.uri.toString()] = CachedFileInfo(song, song.dateModified, song.sizeBytes)
        }
        return map
    }

    fun getAllAlbums(): List<Album> {
        val albums = mutableListOf<Album>()
        val db = readableDatabase
        ensureLikedSongsAlbum(db)

        val query = """
            SELECT a.$COL_ALBUM_ID, a.$COL_ALBUM_NAME, a.$COL_FOLDER_PATH, a.$COL_ALBUM_ARTWORK, a.$COL_IS_CUSTOM,
                   COUNT(as_link.$COL_AS_SONG_ID) AS song_count,
                   COALESCE(SUM(s.$COL_DURATION_MS), 0) AS total_duration
            FROM $TABLE_ALBUMS a
            LEFT JOIN $TABLE_ALBUM_SONGS as_link ON a.$COL_ALBUM_ID = as_link.$COL_AS_ALBUM_ID
            LEFT JOIN $TABLE_SONGS s ON as_link.$COL_AS_SONG_ID = s.$COL_SONG_ID
            GROUP BY a.$COL_ALBUM_ID
            ORDER BY CASE WHEN a.$COL_ALBUM_ID = '$ALBUM_ID_LIKED' THEN 0 ELSE 1 END, a.$COL_ALBUM_NAME COLLATE NOCASE ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, null)
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ALBUM_ID)
            val nameIdx = c.getColumnIndexOrThrow(COL_ALBUM_NAME)
            val pathIdx = c.getColumnIndexOrThrow(COL_FOLDER_PATH)
            val artIdx = c.getColumnIndexOrThrow(COL_ALBUM_ARTWORK)
            val customIdx = c.getColumnIndexOrThrow(COL_IS_CUSTOM)
            val countIdx = c.getColumnIndexOrThrow("song_count")
            val durIdx = c.getColumnIndexOrThrow("total_duration")

            while (c.moveToNext()) {
                val artUri = if (c.isNull(artIdx)) null else Uri.parse(c.getString(artIdx))
                albums.add(
                    Album(
                        id = c.getString(idIdx),
                        name = c.getString(nameIdx),
                        folderPath = c.getString(pathIdx),
                        songCount = c.getInt(countIdx),
                        totalDurationMs = c.getLong(durIdx),
                        artworkUri = artUri,
                        isCustom = c.getInt(customIdx) == 1,
                        songs = emptyList()
                    )
                )
            }
        }
        return albums
    }

    fun getAlbumWithSongs(albumId: String): Album? {
        val db = readableDatabase
        val albumCursor = db.query(TABLE_ALBUMS, null, "$COL_ALBUM_ID = ?", arrayOf(albumId), null, null, null)
        var albumMeta: Album? = null
        albumCursor.use { c ->
            if (c.moveToNext()) {
                val artVal = c.getString(c.getColumnIndexOrThrow(COL_ALBUM_ARTWORK))
                albumMeta = Album(
                    id = c.getString(c.getColumnIndexOrThrow(COL_ALBUM_ID)),
                    name = c.getString(c.getColumnIndexOrThrow(COL_ALBUM_NAME)),
                    folderPath = c.getString(c.getColumnIndexOrThrow(COL_FOLDER_PATH)),
                    songCount = 0,
                    totalDurationMs = 0L,
                    artworkUri = if (artVal != null) Uri.parse(artVal) else null,
                    isCustom = c.getInt(c.getColumnIndexOrThrow(COL_IS_CUSTOM)) == 1,
                    songs = emptyList()
                )
            }
        }

        val baseAlbum = albumMeta ?: return null

        val songsQuery = """
            SELECT s.* FROM $TABLE_SONGS s
            INNER JOIN $TABLE_ALBUM_SONGS as_link ON s.$COL_SONG_ID = as_link.$COL_AS_SONG_ID
            WHERE as_link.$COL_AS_ALBUM_ID = ?
            ORDER BY as_link.$COL_TRACK_ORDER ASC, s.$COL_TRACK_NUMBER ASC, s.$COL_TITLE COLLATE NOCASE ASC
        """.trimIndent()

        val songs = mutableListOf<Song>()
        val songsCursor = db.rawQuery(songsQuery, arrayOf(albumId))
        songsCursor.use { c ->
            while (c.moveToNext()) {
                songs.add(cursorToSong(c))
            }
        }

        val firstArt = baseAlbum.artworkUri ?: songs.firstOrNull { it.artworkUri != null }?.artworkUri

        return baseAlbum.copy(
            songCount = songs.size,
            totalDurationMs = songs.sumOf { it.durationMs },
            artworkUri = firstArt,
            songs = songs
        )
    }

    fun getSongsForArtist(artistName: String): List<Song> {
        val songs = mutableListOf<Song>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SONGS,
            null,
            "$COL_ARTIST = ? COLLATE NOCASE",
            arrayOf(artistName),
            null,
            null,
            "$COL_TITLE COLLATE NOCASE ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                songs.add(cursorToSong(c))
            }
        }
        return songs
    }

    fun getAlbumsForArtist(artistName: String): List<Album> {
        val albums = mutableListOf<Album>()
        val db = readableDatabase
        val query = """
            SELECT DISTINCT a.$COL_ALBUM_ID, a.$COL_ALBUM_NAME, a.$COL_FOLDER_PATH, a.$COL_ALBUM_ARTWORK, a.$COL_IS_CUSTOM,
                   COUNT(as_all.$COL_AS_SONG_ID) AS song_count,
                   COALESCE(SUM(s_all.$COL_DURATION_MS), 0) AS total_duration
            FROM $TABLE_ALBUMS a
            INNER JOIN $TABLE_ALBUM_SONGS as_match ON a.$COL_ALBUM_ID = as_match.$COL_AS_ALBUM_ID
            INNER JOIN $TABLE_SONGS s_match ON as_match.$COL_AS_SONG_ID = s_match.$COL_SONG_ID
            LEFT JOIN $TABLE_ALBUM_SONGS as_all ON a.$COL_ALBUM_ID = as_all.$COL_AS_ALBUM_ID
            LEFT JOIN $TABLE_SONGS s_all ON as_all.$COL_AS_SONG_ID = s_all.$COL_SONG_ID
            WHERE s_match.$COL_ARTIST = ? COLLATE NOCASE AND a.$COL_ALBUM_ID != '$ALBUM_ID_LIKED'
            GROUP BY a.$COL_ALBUM_ID
            ORDER BY a.$COL_ALBUM_NAME COLLATE NOCASE ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(artistName))
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ALBUM_ID)
            val nameIdx = c.getColumnIndexOrThrow(COL_ALBUM_NAME)
            val pathIdx = c.getColumnIndexOrThrow(COL_FOLDER_PATH)
            val artIdx = c.getColumnIndexOrThrow(COL_ALBUM_ARTWORK)
            val customIdx = c.getColumnIndexOrThrow(COL_IS_CUSTOM)
            val countIdx = c.getColumnIndexOrThrow("song_count")
            val durIdx = c.getColumnIndexOrThrow("total_duration")

            while (c.moveToNext()) {
                val artUri = if (c.isNull(artIdx)) null else Uri.parse(c.getString(artIdx))
                albums.add(
                    Album(
                        id = c.getString(idIdx),
                        name = c.getString(nameIdx),
                        folderPath = c.getString(pathIdx),
                        songCount = c.getInt(countIdx),
                        totalDurationMs = c.getLong(durIdx),
                        artworkUri = artUri,
                        isCustom = c.getInt(customIdx) == 1,
                        songs = emptyList()
                    )
                )
            }
        }
        return albums
    }

    fun getLikedSongIds(): Set<String> {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ALBUM_SONGS,
            arrayOf(COL_AS_SONG_ID),
            "$COL_AS_ALBUM_ID = ?",
            arrayOf(ALBUM_ID_LIKED),
            null, null, null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                set.add(c.getString(0))
            }
        }
        return set
    }

    fun toggleLikeSong(songId: String): Boolean {
        val db = writableDatabase
        ensureLikedSongsAlbum(db)
        db.beginTransaction()
        try {
            val cursor = db.query(
                TABLE_ALBUM_SONGS,
                arrayOf(COL_AS_SONG_ID),
                "$COL_AS_ALBUM_ID = ? AND $COL_AS_SONG_ID = ?",
                arrayOf(ALBUM_ID_LIKED, songId),
                null, null, null
            )
            val alreadyLiked = cursor.moveToNext()
            cursor.close()

            val newLikedState = !alreadyLiked
            if (newLikedState) {
                val maxOrderCursor = db.rawQuery(
                    "SELECT COALESCE(MAX($COL_TRACK_ORDER), 0) + 1 FROM $TABLE_ALBUM_SONGS WHERE $COL_AS_ALBUM_ID = ?",
                    arrayOf(ALBUM_ID_LIKED)
                )
                val nextOrder = if (maxOrderCursor.moveToNext()) maxOrderCursor.getInt(0) else 1
                maxOrderCursor.close()

                val values = ContentValues().apply {
                    put(COL_AS_ALBUM_ID, ALBUM_ID_LIKED)
                    put(COL_AS_SONG_ID, songId)
                    put(COL_TRACK_ORDER, nextOrder)
                }
                db.insertWithOnConflict(TABLE_ALBUM_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE)

                val songVal = ContentValues().apply { put(COL_IS_LIKED, 1) }
                db.update(TABLE_SONGS, songVal, "$COL_SONG_ID = ?", arrayOf(songId))
            } else {
                db.delete(
                    TABLE_ALBUM_SONGS,
                    "$COL_AS_ALBUM_ID = ? AND $COL_AS_SONG_ID = ?",
                    arrayOf(ALBUM_ID_LIKED, songId)
                )
                val songVal = ContentValues().apply { put(COL_IS_LIKED, 0) }
                db.update(TABLE_SONGS, songVal, "$COL_SONG_ID = ?", arrayOf(songId))
            }

            db.setTransactionSuccessful()
            return newLikedState
        } finally {
            db.endTransaction()
        }
    }

    fun isSongLiked(songId: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ALBUM_SONGS,
            arrayOf(COL_AS_SONG_ID),
            "$COL_AS_ALBUM_ID = ? AND $COL_AS_SONG_ID = ?",
            arrayOf(ALBUM_ID_LIKED, songId),
            null, null, null
        )
        val exists = cursor.moveToNext()
        cursor.close()
        return exists
    }

    fun createCustomAlbum(name: String): String {
        val albumId = "custom_${UUID.randomUUID()}"
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ALBUM_ID, albumId)
            put(COL_ALBUM_NAME, name.trim())
            put(COL_FOLDER_PATH, "Custom Albums")
            put(COL_IS_CUSTOM, 1)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_ALBUMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return albumId
    }

    fun deleteAlbum(albumId: String) {
        if (albumId == ALBUM_ID_LIKED) return // Protect Liked Songs special album
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ALBUM_SONGS, "$COL_AS_ALBUM_ID = ?", arrayOf(albumId))
            db.delete(TABLE_ALBUMS, "$COL_ALBUM_ID = ?", arrayOf(albumId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addSongsToAlbum(albumId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery(
                "SELECT COALESCE(MAX($COL_TRACK_ORDER), 0) FROM $TABLE_ALBUM_SONGS WHERE $COL_AS_ALBUM_ID = ?",
                arrayOf(albumId)
            )
            var currentOrder = if (cursor.moveToNext()) cursor.getInt(0) else 0
            cursor.close()

            for (songId in songIds) {
                currentOrder++
                val values = ContentValues().apply {
                    put(COL_AS_ALBUM_ID, albumId)
                    put(COL_AS_SONG_ID, songId)
                    put(COL_TRACK_ORDER, currentOrder)
                }
                db.insertWithOnConflict(TABLE_ALBUM_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }

            val firstSongId = songIds.first()
            val songCursor = db.query(TABLE_SONGS, arrayOf(COL_ARTWORK_URI), "$COL_SONG_ID = ?", arrayOf(firstSongId), null, null, null)
            var songArt: String? = null
            if (songCursor.moveToNext()) {
                songArt = songCursor.getString(0)
            }
            songCursor.close()

            if (songArt != null && albumId != ALBUM_ID_LIKED) {
                db.execSQL(
                    "UPDATE $TABLE_ALBUMS SET $COL_ALBUM_ARTWORK = ? WHERE $COL_ALBUM_ID = ? AND ($COL_ALBUM_ARTWORK IS NULL OR $COL_ALBUM_ARTWORK = '')",
                    arrayOf(songArt, albumId)
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addSongToAlbum(albumId: String, songId: String) {
        addSongsToAlbum(albumId, listOf(songId))
    }

    fun removeSongFromAlbum(albumId: String, songId: String) {
        val db = writableDatabase
        db.delete(
            TABLE_ALBUM_SONGS,
            "$COL_AS_ALBUM_ID = ? AND $COL_AS_SONG_ID = ?",
            arrayOf(albumId, songId)
        )
        if (albumId == ALBUM_ID_LIKED) {
            val songVal = ContentValues().apply { put(COL_IS_LIKED, 0) }
            db.update(TABLE_SONGS, songVal, "$COL_SONG_ID = ?", arrayOf(songId))
        }
    }

    fun moveSongBetweenAlbums(songId: String, fromAlbumId: String, toAlbumId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                TABLE_ALBUM_SONGS,
                "$COL_AS_ALBUM_ID = ? AND $COL_AS_SONG_ID = ?",
                arrayOf(fromAlbumId, songId)
            )
            addSongToAlbum(toAlbumId, songId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertOrUpdateScannedData(songs: List<Song>) {
        if (songs.isEmpty()) return
        val db = writableDatabase
        ensureLikedSongsAlbum(db)
        db.beginTransaction()
        try {
            for (song in songs) {
                // 1. Insert/Replace Song
                val songValues = ContentValues().apply {
                    put(COL_SONG_ID, song.id)
                    put(COL_URI, song.uri.toString())
                    put(COL_TITLE, song.title)
                    put(COL_ARTIST, song.artist)
                    put(COL_ALBUM, song.album)
                    put(COL_ALBUM_FOLDER, song.albumFolder)
                    put(COL_YEAR, song.year)
                    put(COL_GENRE, song.genre)
                    put(COL_DURATION_MS, song.durationMs)
                    put(COL_SIZE_BYTES, song.sizeBytes)
                    put(COL_DATE_MODIFIED, song.dateModified)
                    put(COL_RELATIVE_PATH, song.relativePath)
                    put(COL_TRACK_NUMBER, song.trackNumber)
                    put(COL_ARTWORK_URI, song.artworkUri?.toString())
                }
                db.insertWithOnConflict(TABLE_SONGS, null, songValues, SQLiteDatabase.CONFLICT_REPLACE)

                // 2. Ensure folder album exists
                val albumId = song.albumFolder
                val albumValues = ContentValues().apply {
                    put(COL_ALBUM_ID, albumId)
                    put(COL_ALBUM_NAME, song.albumFolder)
                    put(COL_FOLDER_PATH, song.albumFolder)
                    put(COL_ALBUM_ARTWORK, song.artworkUri?.toString())
                    put(COL_IS_CUSTOM, 0)
                    put(COL_CREATED_AT, System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_ALBUMS, null, albumValues, SQLiteDatabase.CONFLICT_IGNORE)

                // 3. Link song to folder album
                val linkValues = ContentValues().apply {
                    put(COL_AS_ALBUM_ID, albumId)
                    put(COL_AS_SONG_ID, song.id)
                    put(COL_TRACK_ORDER, song.trackNumber ?: 0)
                }
                db.insertWithOnConflict(TABLE_ALBUM_SONGS, null, linkValues, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteSongsByUris(uris: List<String>) {
        if (uris.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (chunk in uris.chunked(50)) {
                val placeholders = chunk.joinToString(",") { "?" }
                val songIds = mutableListOf<String>()
                val cursor = db.query(TABLE_SONGS, arrayOf(COL_SONG_ID), "$COL_URI IN ($placeholders)", chunk.toTypedArray(), null, null, null)
                cursor.use { c ->
                    while (c.moveToNext()) {
                        songIds.add(c.getString(0))
                    }
                }

                if (songIds.isNotEmpty()) {
                    val idPlaceholders = songIds.joinToString(",") { "?" }
                    db.delete(TABLE_ALBUM_SONGS, "$COL_AS_SONG_ID IN ($idPlaceholders)", songIds.toTypedArray())
                }
                db.delete(TABLE_SONGS, "$COL_URI IN ($placeholders)", chunk.toTypedArray())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_ALBUM_SONGS, "$COL_AS_ALBUM_ID != ?", arrayOf(ALBUM_ID_LIKED))
        db.delete(TABLE_ALBUMS, "$COL_ALBUM_ID != ?", arrayOf(ALBUM_ID_LIKED))
        db.delete(TABLE_SONGS, null, null)
    }

    private fun cursorToSong(c: Cursor): Song {
        val yearIdx = c.getColumnIndexOrThrow(COL_YEAR)
        val trackIdx = c.getColumnIndexOrThrow(COL_TRACK_NUMBER)
        val artIdx = c.getColumnIndexOrThrow(COL_ARTWORK_URI)
        val genreIdx = c.getColumnIndexOrThrow(COL_GENRE)

        val yearVal = if (c.isNull(yearIdx)) null else c.getInt(yearIdx)
        val trackVal = if (c.isNull(trackIdx)) null else c.getInt(trackIdx)
        val artVal = if (c.isNull(artIdx)) null else Uri.parse(c.getString(artIdx))
        val genreVal = if (c.isNull(genreIdx)) null else c.getString(genreIdx)

        return Song(
            id = c.getString(c.getColumnIndexOrThrow(COL_SONG_ID)),
            title = c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
            artist = c.getString(c.getColumnIndexOrThrow(COL_ARTIST)),
            album = c.getString(c.getColumnIndexOrThrow(COL_ALBUM)),
            albumFolder = c.getString(c.getColumnIndexOrThrow(COL_ALBUM_FOLDER)),
            year = yearVal,
            genre = genreVal,
            durationMs = c.getLong(c.getColumnIndexOrThrow(COL_DURATION_MS)),
            sizeBytes = c.getLong(c.getColumnIndexOrThrow(COL_SIZE_BYTES)),
            dateModified = c.getLong(c.getColumnIndexOrThrow(COL_DATE_MODIFIED)),
            uri = Uri.parse(c.getString(c.getColumnIndexOrThrow(COL_URI))),
            relativePath = c.getString(c.getColumnIndexOrThrow(COL_RELATIVE_PATH)),
            trackNumber = trackVal,
            artworkUri = artVal
        )
    }
}
