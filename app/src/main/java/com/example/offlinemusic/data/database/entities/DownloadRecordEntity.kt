package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_records",
    indices = [
        Index(value = ["created_timestamp"]),
        Index(value = ["status"])
    ]
)
data class DownloadRecordEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val source: String = "YOUTUBE", // YOUTUBE, LUCIDA, SPOTIFY
    @ColumnInfo(name = "source_url")
    val sourceUrl: String = "",
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,
    val status: String = "QUEUED", // QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED, DUPLICATE_FOUND
    val progress: Float = 0f,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,
    @ColumnInfo(name = "created_timestamp")
    val createdTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_timestamp")
    val completedTimestamp: Long? = null
)
