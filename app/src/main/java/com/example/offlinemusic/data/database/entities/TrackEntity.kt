package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["file_uri"], unique = true),
        Index(value = ["normalized_title"]),
        Index(value = ["album_id"]),
        Index(value = ["is_liked"]),
        Index(value = ["date_added"]),
        Index(value = ["play_count"])
    ]
)
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "file_uri")
    val fileUri: String,
    val format: String = "mp3",
    val bitrate: Int? = null,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0L,
    @ColumnInfo(name = "metadata_status")
    val metadataStatus: String = "PARSED", // PENDING, PARSED, ENRICHED, MANUAL
    @ColumnInfo(name = "album_id")
    val albumId: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "track_number")
    val trackNumber: Int? = null,
    @ColumnInfo(name = "disc_number")
    val discNumber: Int? = 1,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "date_modified")
    val dateModified: Long = 0L,
    @ColumnInfo(name = "is_liked")
    val isLiked: Boolean = false,
    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,
    @ColumnInfo(name = "last_played_timestamp")
    val lastPlayedTimestamp: Long? = null,
    @ColumnInfo(name = "relative_path")
    val relativePath: String = ""
)
