package com.example.offlinemusic.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.example.offlinemusic.data.model.Song

class MusicDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "offline_music.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SONGS = "songs"
        private const val COL_ID = "id"
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
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_SONGS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_URI TEXT NOT NULL,
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
                $COL_ARTWORK_URI TEXT
            )
        """.trimIndent()
        db.execSQL(createQuery)
        db.execSQL("CREATE INDEX idx_songs_folder ON $TABLE_SONGS ($COL_ALBUM_FOLDER)")
        db.execSQL("CREATE INDEX idx_songs_uri ON $TABLE_SONGS ($COL_URI)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SONGS")
        onCreate(db)
    }

    fun getAllSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val db = readableDatabase
        val cursor = db.query(TABLE_SONGS, null, null, null, null, null, "$COL_TITLE COLLATE NOCASE ASC")
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val uriIdx = c.getColumnIndexOrThrow(COL_URI)
            val titleIdx = c.getColumnIndexOrThrow(COL_TITLE)
            val artistIdx = c.getColumnIndexOrThrow(COL_ARTIST)
            val albumIdx = c.getColumnIndexOrThrow(COL_ALBUM)
            val folderIdx = c.getColumnIndexOrThrow(COL_ALBUM_FOLDER)
            val yearIdx = c.getColumnIndexOrThrow(COL_YEAR)
            val genreIdx = c.getColumnIndexOrThrow(COL_GENRE)
            val durIdx = c.getColumnIndexOrThrow(COL_DURATION_MS)
            val sizeIdx = c.getColumnIndexOrThrow(COL_SIZE_BYTES)
            val modIdx = c.getColumnIndexOrThrow(COL_DATE_MODIFIED)
            val relIdx = c.getColumnIndexOrThrow(COL_RELATIVE_PATH)
            val trackIdx = c.getColumnIndexOrThrow(COL_TRACK_NUMBER)
            val artIdx = c.getColumnIndexOrThrow(COL_ARTWORK_URI)

            while (c.moveToNext()) {
                val yearVal = if (c.isNull(yearIdx)) null else c.getInt(yearIdx)
                val trackVal = if (c.isNull(trackIdx)) null else c.getInt(trackIdx)
                val artVal = if (c.isNull(artIdx)) null else Uri.parse(c.getString(artIdx))
                val genreVal = if (c.isNull(genreIdx)) null else c.getString(genreIdx)

                songs.add(
                    Song(
                        id = c.getString(idIdx),
                        title = c.getString(titleIdx),
                        artist = c.getString(artistIdx),
                        album = c.getString(albumIdx),
                        albumFolder = c.getString(folderIdx),
                        year = yearVal,
                        genre = genreVal,
                        durationMs = c.getLong(durIdx),
                        sizeBytes = c.getLong(sizeIdx),
                        dateModified = c.getLong(modIdx),
                        uri = Uri.parse(c.getString(uriIdx)),
                        relativePath = c.getString(relIdx),
                        trackNumber = trackVal,
                        artworkUri = artVal
                    )
                )
            }
        }
        return songs
    }

    data class CachedFileInfo(val song: Song, val dateModified: Long, val sizeBytes: Long)

    fun getCachedFileMap(): Map<String, CachedFileInfo> {
        val map = mutableMapOf<String, CachedFileInfo>()
        for (song in getAllSongs()) {
            map[song.uri.toString()] = CachedFileInfo(song, song.dateModified, song.sizeBytes)
        }
        return map
    }

    fun insertOrReplaceSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (song in songs) {
                val values = ContentValues().apply {
                    put(COL_ID, song.id)
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
                db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
                db.delete(TABLE_SONGS, "$COL_URI IN ($placeholders)", chunk.toTypedArray())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_SONGS, null, null)
    }
}
