package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [
        Index(value = ["normalized_name"], unique = true)
    ]
)
data class ArtistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val aliases: String? = null, // Comma-separated list of known aliases
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    @ColumnInfo(name = "metadata_status")
    val metadataStatus: String = "AUTO", // AUTO, MANUAL, CONFIRMED
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String? = null,
    val bio: String? = null
)
