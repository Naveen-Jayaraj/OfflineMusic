package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinemusic.data.database.entities.FolderSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderSourceDao {

    @Query("SELECT * FROM folder_sources ORDER BY folder_name ASC")
    fun getAllFolderSourcesFlow(): Flow<List<FolderSourceEntity>>

    @Query("SELECT * FROM folder_sources ORDER BY folder_name ASC")
    suspend fun getAllFolderSources(): List<FolderSourceEntity>

    @Query("SELECT * FROM folder_sources WHERE id = :id LIMIT 1")
    suspend fun getFolderSourceById(id: String): FolderSourceEntity?

    @Query("SELECT * FROM folder_sources WHERE folder_uri = :uri LIMIT 1")
    suspend fun getFolderSourceByUri(uri: String): FolderSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolderSource(source: FolderSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFolderSource(source: FolderSourceEntity)

    @Update
    suspend fun updateFolderSource(source: FolderSourceEntity)

    @Query("UPDATE folder_sources SET scan_status = :status, last_synced_timestamp = :timestamp WHERE id = :id")
    suspend fun updateScanStatus(id: String, status: String, timestamp: Long)

    @Query("DELETE FROM folder_sources WHERE id = :id")
    suspend fun deleteFolderSourceById(id: String)
}
