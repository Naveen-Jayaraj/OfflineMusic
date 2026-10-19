package com.example.offlinemusic.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.offlinemusic.data.database.entities.ArtistReviewQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistReviewQueueDao {

    @Query("SELECT * FROM artist_review_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    fun getPendingReviewsFlow(): Flow<List<ArtistReviewQueueEntity>>

    @Query("SELECT * FROM artist_review_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingReviews(): List<ArtistReviewQueueEntity>

    @Query("SELECT COUNT(*) FROM artist_review_queue WHERE status = 'PENDING'")
    fun getPendingReviewCountFlow(): Flow<Int>

    @Query("SELECT * FROM artist_review_queue WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 1")
    suspend fun getOldestPendingReview(): ArtistReviewQueueEntity?

    @Query("SELECT raw_credited_string FROM artist_review_queue")
    suspend fun getAllRawCreditedStrings(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReview(review: ArtistReviewQueueEntity): Long

    @Query("UPDATE artist_review_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM artist_review_queue WHERE id = :id")
    suspend fun deleteReview(id: String)
}
