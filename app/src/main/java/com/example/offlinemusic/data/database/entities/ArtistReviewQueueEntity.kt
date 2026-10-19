package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artist_review_queue",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["status"])
    ]
)
data class ArtistReviewQueueEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "raw_credited_string")
    val rawCreditedString: String,
    @ColumnInfo(name = "suggested_artist_ids")
    val suggestedArtistIds: String, // Comma-separated or JSON list
    val status: String = "PENDING", // PENDING, RESOLVED, DISMISSED
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
