package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_artists",
    primaryKeys = ["track_id", "artist_id"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["artist_id"])
    ]
)
data class TrackArtistEntity(
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    @ColumnInfo(name = "credited_name")
    val creditedName: String,
    @ColumnInfo(name = "artist_role")
    val artistRole: String = "PRIMARY", // PRIMARY, FEATURED, REMIXER, PRODUCER
    @ColumnInfo(name = "artist_order")
    val artistOrder: Int = 0
)
