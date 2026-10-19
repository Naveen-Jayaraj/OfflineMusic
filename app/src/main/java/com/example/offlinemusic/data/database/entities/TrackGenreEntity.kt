package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_genres",
    primaryKeys = ["track_id", "genre_id"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genre_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["genre_id"])
    ]
)
data class TrackGenreEntity(
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "genre_id")
    val genreId: String,
    val source: String = "EMBEDDED", // EMBEDDED, ARTIST_INFERRED, ONLINE, MANUAL, ANALYSIS
    val confidence: Float = 1.0f
)
