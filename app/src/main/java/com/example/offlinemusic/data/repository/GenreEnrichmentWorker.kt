package com.example.offlinemusic.data.repository

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.remote.MetaEnricherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GenreEnrichmentWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getInstance(appContext)

    companion object {
        private const val TAG = "GenreEnrichmentWorker"
        private const val WORK_NAME = "meta_enrichment_work_v2"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<GenreEnrichmentWorker>()
                .setConstraints(constraints)
                .setInitialDelay(3, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val unenrichedTracks = db.trackDao().getUnenrichedTracks()

        if (unenrichedTracks.isEmpty()) {
            Log.d(TAG, "All tracks already enriched. Nothing to do.")
            return@withContext Result.success()
        }

        Log.i(TAG, "Found ${unenrichedTracks.size} tracks pending background enrichment.")
        val manager = MetaEnricherManager.getInstance(applicationContext)

        val serverReady = manager.wakeUpServer(retries = 3)
        if (!serverReady) {
            Log.w(TAG, "MetaEnricher server cold start/offline. Retrying later.")
            return@withContext Result.retry()
        }

        // Process in manageable chunks of 5 to respect server latency
        val batch = unenrichedTracks.take(5)
        manager.enrichTracksBatchInternal(batch)

        Result.success()
    }
}
