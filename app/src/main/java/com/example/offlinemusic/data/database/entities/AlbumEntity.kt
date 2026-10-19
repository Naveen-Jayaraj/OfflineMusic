package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["normalized_title"]),
        Index(value = ["album_artist"])
    ]
)
data class AlbumEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,
    @ColumnInfo(name = "album_artist")
    val albumArtist: String? = null,
    @ColumnInfo(name = "release_year")
    val releaseYear: Int? = null,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
    @ColumnInfo(name = "external_release_id")
    val externalReleaseId: String? = null,
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,
    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long = 0L
)
