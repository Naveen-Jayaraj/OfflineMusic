package com.example.offlinemusic.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folder_sources",
    indices = [
        Index(value = ["folder_uri"], unique = true)
    ]
)
data class FolderSourceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "folder_uri")
    val folderUri: String,
    @ColumnInfo(name = "folder_name")
    val folderName: String,
    @ColumnInfo(name = "scan_status")
    val scanStatus: String = "IDLE", // IDLE, SCANNING, SYNCED, ERROR
    @ColumnInfo(name = "last_synced_timestamp")
    val lastSyncedTimestamp: Long = 0L,
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0
)
