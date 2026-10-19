package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["playlist_type"]),
        Index(value = ["folder_source_id"])
    ]
)
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
    @ColumnInfo(name = "playlist_type")
    val playlistType: String = "USER", // USER, SMART, FOLDER, LIKED
    @ColumnInfo(name = "folder_source_id")
    val folderSourceId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sort_order")
    val sortOrder: String = "MANUAL" // MANUAL, TITLE, ARTIST, DATE_ADDED
)
