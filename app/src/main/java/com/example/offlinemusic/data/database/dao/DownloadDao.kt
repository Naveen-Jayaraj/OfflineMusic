package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.offlinemusic.data.database.entities.DownloadRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_records ORDER BY created_timestamp DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM download_records ORDER BY created_timestamp DESC")
    suspend fun getAllDownloads(): List<DownloadRecordEntity>

    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: DownloadRecordEntity)

    @Update
    suspend fun update(record: DownloadRecordEntity)

    @Query("UPDATE download_records SET progress = :progress, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, status: String)

    @Query("UPDATE download_records SET status = :status, error_message = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null)

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_records WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM download_records")
    suspend fun clearAll()
}
