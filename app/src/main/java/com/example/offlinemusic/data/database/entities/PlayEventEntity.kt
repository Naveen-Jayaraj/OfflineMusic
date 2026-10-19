package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_events",
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
        Index(value = ["playback_timestamp"])
    ]
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "playback_timestamp")
    val playbackTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "played_duration_ms")
    val playedDurationMs: Long,
    @ColumnInfo(name = "completion_status")
    val completionStatus: Boolean,
    @ColumnInfo(name = "playback_source")
    val playbackSource: String = "ALL_SONGS", // ALL_SONGS, ALBUM, ARTIST, PLAYLIST, FOLDER
    @ColumnInfo(name = "playlist_id")
    val playlistId: String? = null
)
