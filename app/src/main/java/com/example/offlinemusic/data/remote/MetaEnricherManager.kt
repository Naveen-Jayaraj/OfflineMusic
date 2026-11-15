package com.example.offlinemusic.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.entities.TrackEntity
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.data.repository.MusicRepository
import com.example.offlinemusic.player.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

enum class ServerStatus {
    OFFLINE,
    WAKING_UP,
    ONLINE
}

data class EnrichmentStats(
    val totalTracks: Int = 0,
    val enrichedTracks: Int = 0,
    val pendingTracks: Int = 0
)

class MetaEnricherManager private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val repository = MusicRepository(context)

    private val _serverStatus = MutableStateFlow(ServerStatus.OFFLINE)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _enrichmentStats = MutableStateFlow(EnrichmentStats())
    val enrichmentStats: StateFlow<EnrichmentStats> = _enrichmentStats.asStateFlow()

    private val _isEnriching = MutableStateFlow(false)
    val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

    private val _currentEnrichingTitle = MutableStateFlow<String?>(null)
    val currentEnrichingTitle: StateFlow<String?> = _currentEnrichingTitle.asStateFlow()

    private var sessionJob: Job? = null
    private val priorityQueue = ArrayDeque<String>()
    private val batchMutex = Mutex()

    companion object {
        private const val TAG = "MetaEnricherManager"

        @Volatile
        private var instance: MetaEnricherManager? = null

        fun getInstance(context: Context): MetaEnricherManager {
            return instance ?: synchronized(this) {
                instance ?: MetaEnricherManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Wakes up the Render backend with retries and updates server status.
     */
    suspend fun wakeUpServer(retries: Int = 3): Boolean = withContext(Dispatchers.IO) {
        _serverStatus.value = ServerStatus.WAKING_UP
        for (attempt in 1..retries) {
            try {
                val health = ApiClient.metaEnricherApi.getHealth()
                if (health.status == "ok" || health.status.isNotBlank()) {
                    _serverStatus.value = ServerStatus.ONLINE
                    Log.i(TAG, "MetaEnricher server is online: ${health.server} v${health.version}")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server wake-up ping attempt $attempt/$retries failed: ${e.message}")
                if (attempt < retries) {
                    delay(3000L)
                }
            }
        }
        _serverStatus.value = ServerStatus.OFFLINE
        false
    }

    /**
     * Starts the keep-alive and background batch enrichment session loop.
     */
    fun startSession(scope: CoroutineScope) {
        if (sessionJob?.isActive == true) return

        sessionJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Starting MetaEnricher Session...")
            wakeUpServer(retries = 3)
            updateStats()

            while (isActive) {
                updateStats()

                // 1. Process any priority tracks queued by user first
                val priorityTrackId = synchronized(priorityQueue) {
                    if (priorityQueue.isNotEmpty()) priorityQueue.removeFirst() else null
                }

                if (priorityTrackId != null) {
                    val track = db.trackDao().getTrackById(priorityTrackId)
                    if (track != null) {
                        Log.i(TAG, "Processing priority enrichment for: ${track.title}")
                        enrichTrackInternal(track)
                    }
                    delay(1000L)
                    continue
                }

                // 2. Fetch tracks that have not been enriched yet
                val unenrichedTracks = db.trackDao().getUnenrichedTracks()

                if (unenrichedTracks.isNotEmpty()) {
                    val batch = unenrichedTracks.take(5)
                    Log.d(TAG, "Processing background batch of ${batch.size} tracks...")
                    enrichTracksBatchInternal(batch)
                    updateStats()
                    // Delay between batches to be respectful to rate limits and network
                    delay(3000L)
                } else {
                    // No songs need enrichment right now.
                    // Keep Render awake by sending a health ping every 3-4 minutes while session is on.
                    Log.d(TAG, "All songs enriched. Waiting in keep-alive idle mode...")
                    for (i in 0 until 24) { // 24 * 10s = 240 seconds = 4 minutes
                        if (!isActive) break
                        val hasPriority = synchronized(priorityQueue) { priorityQueue.isNotEmpty() }
                        val hasPending = db.trackDao().getUnenrichedCount() > 0
                        if (hasPriority || hasPending) break
                        delay(10_000L)
                    }

                    if (isActive) {
                        Log.d(TAG, "Sending keep-alive heartbeat to Render...")
                        wakeUpServer(retries = 1)
                    }
                }
            }
        }
    }

    /**
     * Priority enrichment: executed immediately when user clicks the Enrich button.
     */
    suspend fun enrichSongImmediately(trackId: String): Song? = withContext(Dispatchers.IO) {
        val track = db.trackDao().getTrackById(trackId) ?: return@withContext null
        _isEnriching.value = true
        _currentEnrichingTitle.value = track.title
        try {
            val updatedSong = enrichTrackInternal(track)
            updateStats()
            updatedSong
        } finally {
            _isEnriching.value = false
            _currentEnrichingTitle.value = null
        }
    }

    private fun sanitizeForApi(track: TrackEntity): TrackEntity {
        return track.copy(
            title = track.title.ifBlank { "Unknown Title" },
            artist = track.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
            album = track.album?.ifBlank { "Unknown Album" } ?: "Unknown Album",
            normalizedTitle = (track.normalizedTitle.ifBlank { track.title }).lowercase().trim()
        )
    }

    /**
     * Batch enrich a collection of tracks using backend PUT /songs.
     */
    suspend fun enrichTracksBatchInternal(tracks: List<TrackEntity>): Int = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext 0
        batchMutex.withLock {
            _isEnriching.value = true
            var enrichedCount = 0
            try {
                if (_serverStatus.value != ServerStatus.ONLINE) {
                    val ok = wakeUpServer(retries = 2)
                    if (!ok) {
                        Log.w(TAG, "Server not reachable for batch, aborting")
                        return@withContext 0
                    }
                }

                val sanitizedTracks = tracks.map { sanitizeForApi(it) }
                Log.i(TAG, "Sending batch of ${sanitizedTracks.size} tracks to MetaEnricher PUT /songs")
                val batchResponse = try {
                    ApiClient.metaEnricherApi.enrichSongsBatch(sanitizedTracks)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Batch call failed: ${e.message}")
                    null
                }

                if (batchResponse != null && batchResponse.success && batchResponse.tracks.isNotEmpty()) {
                    val responseMap = batchResponse.tracks.associateBy { it.song.id ?: "" }
                    for (track in tracks) {
                        if (!isActive) break
                        _currentEnrichingTitle.value = track.title
                        val single = responseMap[track.id]
                        if (single != null && single.success) {
                            try {
                                applyEnrichedMetadata(track, single.song, single.lyricsSample)
                                enrichedCount++
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed applying metadata for ${track.title}: ${e.message}")
                                db.trackDao().markTrackEnriched(track.id)
                            }
                        } else {
                            // Mark as enriched so it is never stuck in infinite retry loop
                            db.trackDao().markTrackEnriched(track.id)
                        }
                    }
                } else if (isActive) {
                    // Fallback: Enrich one by one if batch endpoint failed
                    for (track in tracks) {
                        if (!isActive) break
                        _currentEnrichingTitle.value = track.title
                        try {
                            val res = enrichTrackInternal(track)
                            if (res != null) enrichedCount++
                            else db.trackDao().markTrackEnriched(track.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            db.trackDao().markTrackEnriched(track.id)
                        }
                        delay(1000L)
                    }
                }
            } finally {
                _isEnriching.value = false
                _currentEnrichingTitle.value = null
                updateStats()
            }
            enrichedCount
        }
    }

    /**
     * Queue track for high priority enrichment.
     */
    fun queuePriorityEnrichment(trackId: String) {
        synchronized(priorityQueue) {
            if (!priorityQueue.contains(trackId)) {
                priorityQueue.addFirst(trackId)
            }
        }
    }

    /**
     * Refresh database enrichment stats.
     */
    suspend fun updateStats() = withContext(Dispatchers.IO) {
        try {
            val total = db.trackDao().getAllTracks().size
            val enriched = db.trackDao().getEnrichedCount()
            val pending = db.trackDao().getUnenrichedCount()
            _enrichmentStats.value = EnrichmentStats(total, enriched, pending)
        } catch (_: Exception) {}
    }

    /**
     * Core enrichment implementation calling MetaEnricher server for a single track.
     */
    private suspend fun enrichTrackInternal(track: TrackEntity): Song? = withContext(Dispatchers.IO) {
        _isEnriching.value = true
        _currentEnrichingTitle.value = track.title
        try {
            val sanitized = sanitizeForApi(track)
            val response = ApiClient.metaEnricherApi.enrichSong(sanitized)
            if (!response.success) {
                Log.w(TAG, "Server returned success = false for ${track.title}")
                db.trackDao().markTrackEnriched(track.id)
                return@withContext null
            }

            _serverStatus.value = ServerStatus.ONLINE
            applyEnrichedMetadata(track, response.song, response.lyricsSample)
        } catch (e: Exception) {
            Log.w(TAG, "Error enriching ${track.title}: ${e.message}")
            db.trackDao().markTrackEnriched(track.id)
            null
        } finally {
            _isEnriching.value = false
            _currentEnrichingTitle.value = null
        }
    }

    /**
     * Applies enriched metadata DTO to database, downloads high-res artwork, and saves .lrc lyrics.
     */
    private suspend fun applyEnrichedMetadata(
        track: TrackEntity,
        songDto: EnrichedSongDto,
        lyricsSample: String? = null
    ): Song? {
        val newTitle = songDto.title?.ifBlank { track.title } ?: track.title
        val newArtist = songDto.artist?.ifBlank { track.artist ?: "Unknown Artist" } ?: track.artist ?: "Unknown Artist"
        val newAlbum = songDto.album ?: track.album
        val newYear = songDto.year ?: track.year
        val newGenre = songDto.genre ?: track.genre
        val newProducer = songDto.producer ?: track.producer
        val newComposer = songDto.composer ?: track.composer
        val newLanguage = songDto.language ?: track.language
        val newMood = songDto.mood ?: track.mood
        val newSource = songDto.source ?: track.source

        // 1. Download cover artwork if high-res URL is returned
        var newArtworkUri: Uri? = null
        val remoteArtUrl = songDto.artworkUri
        if (!remoteArtUrl.isNullOrBlank() && remoteArtUrl.startsWith("http")) {
            try {
                val artDir = File(context.filesDir, "artwork").apply { mkdirs() }
                val artFile = File(artDir, "art_${track.id}_${System.currentTimeMillis()}.jpg")
                val req = Request.Builder().url(remoteArtUrl).build()
                ApiClient.okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(artFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (artFile.exists() && artFile.length() > 0) {
                            newArtworkUri = try {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    artFile
                                )
                            } catch (_: Exception) {
                                Uri.fromFile(artFile)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed downloading artwork from $remoteArtUrl: ${e.message}")
            }
        }
        if (newArtworkUri == null && !track.artworkUri.isNullOrBlank()) {
            newArtworkUri = Uri.parse(track.artworkUri)
        }

        // 2. Fetch and save line-synced lyrics (.lrc)
        var lyricsAvailable = songDto.lyricsAvailable || track.lyricsAvailable
        try {
            val lyricRes = ApiClient.metaEnricherApi.getLyrics(
                LyricQuery(
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    durationMs = if (track.durationMs > 0) track.durationMs else null
                )
            )
            if (lyricRes.lyricsAvailable) {
                val lrcText = lyricRes.syncedLyrics ?: lyricRes.plainLyrics
                if (!lrcText.isNullOrBlank()) {
                    lyricsAvailable = true
                    saveLyricsToDisk(track, lrcText)
                }
            } else if (!lyricsSample.isNullOrBlank() && lyricsSample.contains("[")) {
                lyricsAvailable = true
                saveLyricsToDisk(track, lyricsSample)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching lyrics for $newTitle: ${e.message}")
        }

        // 3. Update database and physical ID3 tags via repository
        val updatedSong = repository.updateTrackMetadata(
            trackId = track.id,
            newTitle = newTitle,
            rawArtistString = newArtist,
            newAlbum = newAlbum,
            newGenre = newGenre,
            newArtworkUri = newArtworkUri,
            newYear = newYear,
            newProducer = newProducer,
            newComposer = newComposer,
            newLanguage = newLanguage,
            newMood = newMood,
            newSource = newSource,
            newLyricsAvailable = lyricsAvailable,
            newMetachecked = true
        )

        // Explicitly set confirmed status
        db.trackDao().markTrackEnriched(track.id)

        if (updatedSong != null) {
            withContext(Dispatchers.Main) {
                MusicPlayerManager.getInstance(context).updateSongMetadata(updatedSong)
            }
        }

        Log.i(TAG, "Successfully enriched track: $newTitle by $newArtist")
        return updatedSong
    }

    /**
     * Saves line-synced lyrics to internal storage and next to local song file if accessible.
     */
    fun saveLyricsToDisk(track: TrackEntity, lyricsContent: String) {
        try {
            // Save to persistent internal app storage
            val lyricsDir = File(context.filesDir, "lyrics").apply { mkdirs() }
            val appLrc = File(lyricsDir, "${track.id}.lrc")
            appLrc.writeText(lyricsContent)

            val uriStr = Uri.fromFile(appLrc).toString()
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                db.trackDao().updateLyricsUri(track.id, uriStr)
            }

            // If track is a local file URI, write sibling .lrc file
            val trackUri = Uri.parse(track.fileUri)
            val filePath = if (trackUri.scheme == "file" || trackUri.scheme == null) trackUri.path else null
            if (filePath != null) {
                val audioFile = File(filePath)
                val parent = audioFile.parentFile
                if (audioFile.exists() && parent != null && parent.canWrite()) {
                    val siblingLrc = File(parent, "${audioFile.nameWithoutExtension}.lrc")
                    siblingLrc.writeText(lyricsContent)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving lyrics file: ${e.message}")
        }
    }
}
