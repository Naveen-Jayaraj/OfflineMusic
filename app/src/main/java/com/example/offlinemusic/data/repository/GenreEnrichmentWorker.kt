package com.example.offlinemusic.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.entities.GenreEntity
import com.example.offlinemusic.data.database.entities.TrackGenreEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GenreEnrichmentWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getInstance(appContext)

    companion object {
        private const val WORK_NAME = "genre_enrichment_work"
        private val negativeCache = mutableMapOf<String, Long>() // Cache negative lookups for 24 hours
        private const val NEGATIVE_CACHE_TTL_MS = 24 * 60 * 60 * 1000L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<GenreEnrichmentWorker>()
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tracksWithoutGenre = db.trackDao().getAllTracks().filter { track ->
            val genres = db.genreDao().getGenresForTrack(track.id)
            genres.isEmpty()
        }

        if (tracksWithoutGenre.isEmpty()) {
            return@withContext Result.success()
        }

        // Cache of artistId -> verified genre to avoid repeated lookups (Spec Section 6)
        val artistGenreCache = mutableMapOf<String, String>()

        for (track in tracksWithoutGenre.take(20)) {
            val trackArtists = db.artistDao().getArtistsForTrack(track.id)
            val primaryArtist = trackArtists.firstOrNull()?.name ?: "Unknown Artist"
            val artistId = trackArtists.firstOrNull()?.id ?: ""

            // Priority 3: Reuse verified artist genre if available
            val cachedArtistGenre = artistGenreCache[artistId]
            if (cachedArtistGenre != null) {
                assignGenre(track.id, cachedArtistGenre, source = "ARTIST_INFERRED", confidence = 0.85f)
                continue
            }

            // Priority 4: Query online MusicBrainz service with rate-limiting & negative caching
            val lookupKey = "${primaryArtist.lowercase()}_${track.title.lowercase()}"
            val lastNegativeTime = negativeCache[lookupKey]
            val now = System.currentTimeMillis()
            if (lastNegativeTime != null && (now - lastNegativeTime) < NEGATIVE_CACHE_TTL_MS) {
                // Negative cache hit, skip online query
                continue
            }

            val onlineGenre = queryMusicBrainzGenre(track.title, primaryArtist)
            if (onlineGenre != null) {
                assignGenre(track.id, onlineGenre, source = "MUSICBRAINZ", confidence = 0.90f)
                artistGenreCache[artistId] = onlineGenre
            } else {
                negativeCache[lookupKey] = now
            }

            // Respect MusicBrainz rate limit: at least 1 second between requests
            delay(1100)
        }

        Result.success()
    }

    private suspend fun assignGenre(trackId: String, genreName: String, source: String, confidence: Float) {
        val norm = genreName.trim().lowercase()
        val genreId = "genre_${norm.hashCode().toUInt().toString(16)}"

        db.genreDao().insertGenre(
            GenreEntity(
                id = genreId,
                canonicalName = genreName.trim(),
                normalizedName = norm
            )
        )

        db.genreDao().insertTrackGenres(
            listOf(
                TrackGenreEntity(
                    trackId = trackId,
                    genreId = genreId,
                    source = source,
                    confidence = confidence
                )
            )
        )
    }

    private fun queryMusicBrainzGenre(title: String, artist: String): String? {
        if (artist.isBlank() || artist.equals("Unknown Artist", ignoreCase = true)) return null

        return try {
            val query = "recording:${URLEncoder.encode(title, "UTF-8")} AND artist:${URLEncoder.encode(artist, "UTF-8")}"
            val url = URL("https://musicbrainz.org/ws/2/recording?query=$query&limit=1&fmt=json")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "OfflineMusic/1.0 (contact@offlinemusic.app)")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val recordings = root.optJSONArray("recordings")
                if (recordings != null && recordings.length() > 0) {
                    val recording = recordings.getJSONObject(0)
                    val tags = recording.optJSONArray("tags")
                    if (tags != null && tags.length() > 0) {
                        return tags.getJSONObject(0).optString("name")
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
