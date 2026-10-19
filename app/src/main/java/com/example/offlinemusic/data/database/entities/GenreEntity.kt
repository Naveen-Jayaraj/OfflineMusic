package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "genres",
    indices = [
        Index(value = ["normalized_name"], unique = true)
    ]
)
data class GenreEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val description: String? = null
)
