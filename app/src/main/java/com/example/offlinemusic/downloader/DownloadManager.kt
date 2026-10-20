package com.example.offlinemusic.downloader

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.entities.DownloadRecordEntity
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.data.repository.MusicScanner
import com.example.offlinemusic.downloader.engine.FuzzyMatcher
import com.example.offlinemusic.downloader.engine.LrclibClient
import com.example.offlinemusic.downloader.engine.LucidaDownloader
import com.example.offlinemusic.downloader.engine.MetadataEnricher
import com.example.offlinemusic.downloader.engine.SpotifyPlaylistClient
import com.example.offlinemusic.downloader.engine.YouTubeDownloader
import com.example.offlinemusic.downloader.models.DownloadProgress
import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadStatus
import com.example.offlinemusic.downloader.models.DownloadableTrack
import com.example.offlinemusic.downloader.models.DuplicatePromptData
import com.example.offlinemusic.downloader.models.DuplicateResolution
import com.example.offlinemusic.downloader.models.SpotifyPlaylistInfo
import com.example.offlinemusic.downloader.models.SpotifyTrackInfo
import com.example.offlinemusic.downloader.service.DownloadService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = AppDatabase.getInstance(context)
    private val playlistDao = db.playlistDao()
    val downloadDao = db.downloadDao()
    val allDownloadRecords = downloadDao.getAllDownloadsFlow()

    private val musicScanner = MusicScanner(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private val _duplicatePrompt = MutableStateFlow<DuplicatePromptData?>(null)
    val duplicatePrompt: StateFlow<DuplicatePromptData?> = _duplicatePrompt.asStateFlow()

    private val duplicateQueue = ArrayDeque<DuplicatePromptData>()

    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val runningJobs = ConcurrentHashMap<String, Job>()

    companion object {
        const val MAX_RECENTLY_DOWNLOADED = 50

        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val downloadDir: File by lazy {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        File(base, "OfflineMusic").apply { mkdirs() }
    }

    fun isDownloading(id: String): Boolean {
        val item = _downloads.value[id]
        return item != null && (item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.ENRICHING)
    }

    fun isCompleted(id: String): Boolean {
        return _downloads.value[id]?.status == DownloadStatus.COMPLETED
    }

    fun cancelDownload(id: String) {
        cancelledIds.add(id)
        runningJobs[id]?.cancel()
        runningJobs.remove(id)

        // Cancel any pending duplicate prompt for this download
        val currentPrompt = _duplicatePrompt.value
        if (currentPrompt?.downloadId == id) {
            currentPrompt.continuation.complete(DuplicateResolution.DISCARD)
            _duplicatePrompt.value = duplicateQueue.pollFirst()
        } else {
            val it = duplicateQueue.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item.downloadId == id) {
                    item.continuation.complete(DuplicateResolution.DISCARD)
                    it.remove()
                    break
                }
            }
        }

        _downloads.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to existing.copy(status = DownloadStatus.CANCELLED, error = "Cancelled by user"))
        }

        checkAndStopServiceIfIdle()
    }

    fun resolveDuplicate(downloadId: String, resolution: DuplicateResolution) {
        val current = _duplicatePrompt.value
        if (current?.downloadId == downloadId) {
            _duplicatePrompt.value = duplicateQueue.pollFirst()
            current.continuation.complete(resolution)
        } else {
            val it = duplicateQueue.iterator()
            while (it.hasNext()) {
                val item = it.next()
                if (item.downloadId == downloadId) {
                    item.continuation.complete(resolution)
                    it.remove()
                    break
                }
            }
        }
    }

    fun reopenDuplicatePrompt(downloadId: String) {
        val current = _duplicatePrompt.value
        if (current?.downloadId == downloadId) return
        val item = duplicateQueue.firstOrNull { it.downloadId == downloadId }
        if (item != null) {
            duplicateQueue.remove(item)
            if (current != null) {
                duplicateQueue.addFirst(current)
            }
            _duplicatePrompt.value = item
        }
    }

    fun dismissDuplicatePromptTemporarily() {
        val current = _duplicatePrompt.value ?: return
        duplicateQueue.addFirst(current)
        _duplicatePrompt.value = null
    }

    private fun enqueueDuplicatePrompt(prompt: DuplicatePromptData) {
        if (_duplicatePrompt.value == null) {
            _duplicatePrompt.value = prompt
        } else {
            duplicateQueue.add(prompt)
        }
    }

    fun retryDownload(id: String, track: DownloadableTrack) {
        cancelledIds.remove(id)
        downloadTrack(track)
    }

    /**
     * Download an individual song
     */
    fun downloadTrack(track: DownloadableTrack) {
        val downloadId = track.id
        cancelledIds.remove(downloadId)

        val initialProgress = DownloadProgress(
            downloadId = downloadId,
            trackTitle = track.title,
            artist = track.artist,
            status = DownloadStatus.QUEUED
        )
        _downloads.update { it + (downloadId to initialProgress) }

        scope.launch(Dispatchers.IO) {
            downloadDao.insertOrUpdate(
                DownloadRecordEntity(
                    id = downloadId,
                    title = track.title,
                    artist = track.artist,
                    source = track.source.name,
                    sourceUrl = track.sourceUrl,
                    thumbnailUrl = track.thumbnailUrl,
                    status = "DOWNLOADING",
                    progress = 0.05f,
                    durationMs = track.durationMs,
                    createdTimestamp = System.currentTimeMillis()
                )
            )
        }

        val job = scope.launch {
            DownloadService.startService(context, "Downloading ${track.title}...", 0)
            try {
                executeTrackDownload(track, addToRecentlyDownloaded = true)
            } catch (e: Exception) {
                if (cancelledIds.contains(downloadId)) {
                    _downloads.update {
                        it + (downloadId to initialProgress.copy(status = DownloadStatus.CANCELLED, error = "Cancelled"))
                    }
                    downloadDao.updateStatus(downloadId, "CANCELLED", "Cancelled by user")
                } else {
                    _downloads.update {
                        it + (downloadId to initialProgress.copy(status = DownloadStatus.FAILED, error = e.localizedMessage ?: "Download failed"))
                    }
                    downloadDao.updateStatus(downloadId, "FAILED", e.localizedMessage ?: "Download failed")
                }
            } finally {
                runningJobs.remove(downloadId)
                checkAndStopServiceIfIdle()
            }
        }
        runningJobs[downloadId] = job
    }

    /**
     * Download a complete Spotify playlist preserving order and creating the playlist.
     * Songs are NOT added to Recently Downloaded.
     */
    fun downloadSpotifyPlaylist(playlistInfo: SpotifyPlaylistInfo) {
        val batchId = "spotify_playlist_${playlistInfo.id}"
        cancelledIds.remove(batchId)

        val totalTracks = playlistInfo.tracks.size
        val initialBatch = DownloadProgress(
            downloadId = batchId,
            trackTitle = playlistInfo.name,
            artist = "${totalTracks} tracks",
            status = DownloadStatus.QUEUED,
            isSpotifyBatch = true,
            batchCurrent = 0,
            batchTotal = totalTracks
        )
        _downloads.update { it + (batchId to initialBatch) }

        val job = scope.launch {
            DownloadService.startService(context, "Importing ${playlistInfo.name} (0/$totalTracks)...", 0)

            downloadDao.insertOrUpdate(
                DownloadRecordEntity(
                    id = batchId,
                    title = playlistInfo.name,
                    artist = "${totalTracks} tracks (Spotify)",
                    source = "SPOTIFY",
                    sourceUrl = "spotify:playlist:${playlistInfo.id}",
                    thumbnailUrl = playlistInfo.thumbnail,
                    status = "DOWNLOADING",
                    progress = 0f,
                    createdTimestamp = System.currentTimeMillis()
                )
            )

            try {
                // 1. Create Playlist Entity
                val playlistEntityId = "spotify_pl_${playlistInfo.id}_${System.currentTimeMillis()}"
                val playlistEntity = PlaylistEntity(
                    id = playlistEntityId,
                    name = playlistInfo.name,
                    description = playlistInfo.description ?: "Imported from Spotify (${playlistInfo.totalTracks} tracks)",
                    artworkUri = playlistInfo.thumbnail,
                    playlistType = "USER",
                    createdAt = System.currentTimeMillis()
                )
                playlistDao.insertOrUpdatePlaylist(playlistEntity)

                val playlistSubDir = File(downloadDir, sanitizeFilename(playlistInfo.name)).apply { mkdirs() }

                // 2. High-performance single-pass in-memory duplicate check
                val existingSongs = musicScanner.repository.getAllSongs()
                val duplicatesMap = mutableMapOf<Int, Song>()
                val toDownload = mutableListOf<IndexedValue<SpotifyTrackInfo>>()

                for ((index, spotifyTrack) in playlistInfo.tracks.withIndex()) {
                    val duplicateMatch = FuzzyMatcher.findDuplicateSong(
                        queryTitle = spotifyTrack.title,
                        queryArtist = spotifyTrack.artist,
                        databaseSongs = existingSongs,
                        threshold = 0.70
                    )
                    if (duplicateMatch != null) {
                        duplicatesMap[index] = duplicateMatch.matchedSong
                    } else {
                        toDownload.add(IndexedValue(index, spotifyTrack))
                    }
                }

                // Immediately insert all duplicates into playlist with their preserved positions
                if (duplicatesMap.isNotEmpty()) {
                    val duplicateEntities = duplicatesMap.map { (index, song) ->
                        PlaylistTrackEntity(
                            playlistId = playlistEntityId,
                            trackId = song.id,
                            position = index,
                            addedTimestamp = System.currentTimeMillis()
                        )
                    }
                    playlistDao.insertPlaylistTracks(duplicateEntities)
                }

                val completedCounter = AtomicInteger(duplicatesMap.size)
                val initialDupCount = duplicatesMap.size

                _downloads.update { current ->
                    current + (batchId to initialBatch.copy(
                        status = DownloadStatus.DOWNLOADING,
                        batchCurrent = initialDupCount,
                        progress = (initialDupCount.toFloat() / totalTracks.toFloat()).coerceIn(0f, 1f)
                    ))
                }

                // 3. Autobatched concurrent download pipeline (3 parallel workers)
                val semaphore = Semaphore(3)
                coroutineScope {
                    toDownload.map { indexedTrack ->
                        launch {
                            if (cancelledIds.contains(batchId)) return@launch
                            val index = indexedTrack.index
                            val spotifyTrack = indexedTrack.value

                            semaphore.withPermit {
                                if (cancelledIds.contains(batchId)) return@withPermit
                                try {
                                    val candidateTrack = resolveSpotifyTrackToCandidate(spotifyTrack)
                                    if (candidateTrack != null) {
                                        val downloadedSong = executeTrackDownload(
                                            candidateTrack,
                                            customTargetDir = playlistSubDir,
                                            addToRecentlyDownloaded = false,
                                            checkDuplicates = false
                                        )

                                        if (downloadedSong != null) {
                                            playlistDao.insertPlaylistTrack(
                                                PlaylistTrackEntity(
                                                    playlistId = playlistEntityId,
                                                    trackId = downloadedSong.id,
                                                    position = index, // Preserve original index
                                                    addedTimestamp = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                } catch (_: Exception) {
                                } finally {
                                    val currentDone = completedCounter.incrementAndGet()
                                    val frac = (currentDone.toFloat() / totalTracks.toFloat()).coerceIn(0f, 1f)

                                    _downloads.update { current ->
                                        current + (batchId to initialBatch.copy(
                                            status = DownloadStatus.DOWNLOADING,
                                            batchCurrent = currentDone,
                                            trackTitle = spotifyTrack.title,
                                            artist = spotifyTrack.artist,
                                            progress = frac
                                        ))
                                    }

                                    val notifProg = (frac * 100).toInt()
                                    DownloadService.updateProgress(
                                        context,
                                        "($currentDone/$totalTracks) ${spotifyTrack.title}",
                                        notifProg
                                    )
                                }
                            }
                        }
                    }.joinAll()
                }

                val finalCompleted = completedCounter.get()
                if (cancelledIds.contains(batchId)) {
                    _downloads.update {
                        it + (batchId to initialBatch.copy(status = DownloadStatus.CANCELLED, error = "Cancelled by user"))
                    }
                    downloadDao.updateStatus(batchId, "CANCELLED", "Cancelled by user")
                } else {
                    _downloads.update {
                        it + (batchId to initialBatch.copy(
                            status = DownloadStatus.COMPLETED,
                            batchCurrent = finalCompleted,
                            progress = 1.0f
                        ))
                    }
                    downloadDao.insertOrUpdate(
                        DownloadRecordEntity(
                            id = batchId,
                            title = playlistInfo.name,
                            artist = "${finalCompleted}/$totalTracks tracks downloaded",
                            source = "SPOTIFY",
                            sourceUrl = "spotify:playlist:${playlistInfo.id}",
                            thumbnailUrl = playlistInfo.thumbnail,
                            status = "COMPLETED",
                            progress = 1.0f,
                            filePath = playlistSubDir.absolutePath,
                            completedTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                _downloads.update {
                    it + (batchId to initialBatch.copy(status = DownloadStatus.FAILED, error = e.localizedMessage ?: "Playlist download failed"))
                }
                downloadDao.updateStatus(batchId, "FAILED", e.localizedMessage ?: "Playlist download failed")
            } finally {
                runningJobs.remove(batchId)
                checkAndStopServiceIfIdle()
            }
        }
        runningJobs[batchId] = job
    }

    private suspend fun resolveSpotifyTrackToCandidate(track: SpotifyTrackInfo): DownloadableTrack? {
        val query = "${track.artist} - ${track.title}"

        // Try YouTube search first
        val ytResults = YouTubeDownloader.search(query, limit = 5)
        if (ytResults.isNotEmpty()) {
            val best = FuzzyMatcher.findBestMatch(track.title, track.artist, ytResults) { it.title to it.artist }
            if (best != null) return best
            return ytResults.first()
        }

        // Try Lucida search as fallback
        val lucidaResults = LucidaDownloader.search(query, limit = 5)
        if (lucidaResults.isNotEmpty()) {
            val best = FuzzyMatcher.findBestMatch(track.title, track.artist, lucidaResults) { it.title to it.artist }
            if (best != null) return best
            return lucidaResults.first()
        }

        return null
    }

    private suspend fun executeTrackDownload(
        track: DownloadableTrack,
        customTargetDir: File? = null,
        addToRecentlyDownloaded: Boolean = true,
        checkDuplicates: Boolean = true
    ): Song? = withContext(Dispatchers.IO) {
        val downloadId = track.id
        val targetDir = customTargetDir ?: downloadDir

        _downloads.update { current ->
            val existing = current[downloadId] ?: DownloadProgress(downloadId, track.title, track.artist)
            current + (downloadId to existing.copy(status = DownloadStatus.DOWNLOADING, progress = 0.05f))
        }

        var formatExt = if (track.source == DownloadSource.LUCIDA) "flac" else "m4a"
        val safeFilename = sanitizeFilename("${track.artist} - ${track.title}")
        var targetAudioFile = File(targetDir, "${safeFilename}.${formatExt}")
        val tempAudioFile = File(targetDir, "${safeFilename}.${formatExt}.tmp")

        val streamIdentifier = if (track.source == DownloadSource.LUCIDA) {
            track.sourceUrl.ifBlank { track.id }
        } else {
            if (track.sourceUrl.isNotBlank()) track.sourceUrl else track.id
        }

        var downloadResult = if (track.source == DownloadSource.LUCIDA) {
            LucidaDownloader.downloadTrack(streamIdentifier, tempAudioFile) { prog, dl, tot ->
                updateDownloadProgress(downloadId, prog, dl, tot)
            }
        } else {
            YouTubeDownloader.downloadTrack(streamIdentifier, tempAudioFile) { prog, dl, tot ->
                updateDownloadProgress(downloadId, prog, dl, tot)
            }
        }

        // Seamless fallback to YouTube if Lucida stream fails
        if (downloadResult.isFailure && track.source == DownloadSource.LUCIDA) {
            val ytFallback = YouTubeDownloader.search("${track.artist} - ${track.title}", limit = 1).firstOrNull()
            if (ytFallback != null) {
                formatExt = "m4a"
                targetAudioFile = File(targetDir, "${safeFilename}.${formatExt}")
                val ytTemp = File(targetDir, "${safeFilename}.${formatExt}.tmp")
                val ytUrl = ytFallback.sourceUrl.ifBlank { ytFallback.id }
                downloadResult = YouTubeDownloader.downloadTrack(ytUrl, ytTemp) { prog, dl, tot ->
                    updateDownloadProgress(downloadId, prog, dl, tot)
                }
                if (downloadResult.isSuccess) {
                    if (targetAudioFile.exists()) targetAudioFile.delete()
                    if (!ytTemp.renameTo(targetAudioFile)) {
                        try {
                            ytTemp.copyTo(targetAudioFile, overwrite = true)
                            ytTemp.delete()
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (cancelledIds.contains(downloadId)) {
            tempAudioFile.delete()
            return@withContext null
        }

        if (downloadResult.isFailure) {
            tempAudioFile.delete()
            throw downloadResult.exceptionOrNull() ?: IllegalStateException("Could not download audio stream for ${track.title}")
        }

        if (tempAudioFile.exists()) {
            if (targetAudioFile.exists()) targetAudioFile.delete()
            if (!tempAudioFile.renameTo(targetAudioFile)) {
                try {
                    tempAudioFile.copyTo(targetAudioFile, overwrite = true)
                    tempAudioFile.delete()
                } catch (_: Exception) {}
            }
        }

        _downloads.update { current ->
            val existing = current[downloadId] ?: return@update current
            current + (downloadId to existing.copy(status = DownloadStatus.ENRICHING, progress = 0.85f))
        }

        // Enrich metadata (clean title, canonical artist, album, year, genre, and high-res cover art)
        val enriched = MetadataEnricher.enrich(
            rawTitle = track.title,
            rawArtist = track.artist,
            fallbackThumbnailUrl = track.thumbnailUrl
        )

        // Fetch Lyrics (.lrc)
        val lrcFile = File(targetDir, "${safeFilename}.lrc")
        try {
            val durationSec = track.durationMs / 1000
            val lyrics = LrclibClient.fetchLyrics(
                title = enriched.title,
                artist = enriched.artist,
                album = enriched.album.ifBlank { null },
                durationSeconds = if (durationSec > 0) durationSec else null
            )
            if (lyrics != null) {
                LrclibClient.saveToLrcFile(lyrics, lrcFile)
            }
        } catch (_: Exception) {}

        // Download cover art into internal app cache (keeps music folder free of loose image clutter)
        val artworkCacheDir = File(context.cacheDir, "artwork").apply { mkdirs() }
        val artworkFile = File(artworkCacheDir, "art_${downloadId.hashCode().toUInt().toString(16)}.jpg")
        val artUrl = enriched.artworkUrl ?: track.thumbnailUrl
        if (!artUrl.isNullOrBlank()) {
            try {
                val req = Request.Builder().url(artUrl).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(artworkFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Automatically delete any loose thumbnail in the target directory
        val looseThumb = File(targetDir, "${safeFilename}.jpg")
        if (looseThumb.exists()) {
            try { looseThumb.delete() } catch (_: Exception) {}
        }

        // Check whether the same song already exists in the database (Fuzzy matching on Title and Artist >= 70%)
        if (checkDuplicates) {
            val databaseSongs = musicScanner.repository.getAllSongs()
            val duplicateResult = FuzzyMatcher.findDuplicateSong(
                queryTitle = enriched.title.ifBlank { track.title },
                queryArtist = enriched.artist.ifBlank { track.artist },
                databaseSongs = databaseSongs,
                threshold = 0.70
            )

            if (duplicateResult != null) {
                val continuation = CompletableDeferred<DuplicateResolution>()
                val prompt = DuplicatePromptData(
                    downloadId = downloadId,
                    downloadedTrack = track,
                    downloadedAudioFile = targetAudioFile,
                    downloadedFormat = formatExt,
                    downloadedSizeBytes = targetAudioFile.length(),
                    downloadedDurationMs = if (track.durationMs > 0) track.durationMs else 0L,
                    downloadedArtworkFile = if (artworkFile.exists()) artworkFile else null,
                    downloadedLrcFile = if (lrcFile.exists()) lrcFile else null,
                    enrichedTitle = enriched.title,
                    enrichedArtist = enriched.artist,
                    enrichedAlbum = enriched.album,
                    existingSong = duplicateResult.matchedSong,
                    matchPercentage = duplicateResult.percentage,
                    continuation = continuation
                )

                _downloads.update { current ->
                    val existing = current[downloadId] ?: return@update current
                    current + (downloadId to existing.copy(
                        status = DownloadStatus.DUPLICATE_FOUND,
                        error = "Duplicate found (${duplicateResult.percentage}% match with '${duplicateResult.matchedSong.title}')"
                    ))
                }

                DownloadService.updateProgress(
                    context,
                    "Duplicate found: ${enriched.title} (${duplicateResult.percentage}% match)",
                    100
                )

                enqueueDuplicatePrompt(prompt)

                val resolution = try {
                    continuation.await()
                } catch (e: Exception) {
                    DuplicateResolution.DISCARD
                }

                when (resolution) {
                    DuplicateResolution.DISCARD -> {
                        // Discard downloaded file and cached files
                        try { if (targetAudioFile.exists()) targetAudioFile.delete() } catch (_: Exception) {}
                        try { if (lrcFile.exists()) lrcFile.delete() } catch (_: Exception) {}
                        try { if (artworkFile.exists()) artworkFile.delete() } catch (_: Exception) {}

                        _downloads.update { current ->
                            val existing = current[downloadId] ?: return@update current
                            current + (downloadId to existing.copy(status = DownloadStatus.CANCELLED, error = "Discarded download"))
                        }
                        return@withContext null
                    }
                    DuplicateResolution.REPLACE_EXISTING -> {
                        // Replace existing song in DB with new audio file & metadata
                        val updatedSong = musicScanner.replaceExistingTrackWithDownload(
                            existingTrackId = duplicateResult.matchedSong.id,
                            newAudioFile = targetAudioFile,
                            newTitle = enriched.title,
                            newArtist = enriched.artist,
                            newAlbum = enriched.album.ifBlank { "Downloads" },
                            newArtworkFile = if (artworkFile.exists()) artworkFile else null,
                            newLrcFile = if (lrcFile.exists()) lrcFile else null
                        )

                        exportToPublicMusicFolder(
                            audioFile = targetAudioFile,
                            title = enriched.title,
                            artist = enriched.artist,
                            album = enriched.album.ifBlank { "OfflineMusic" }
                        )

                        if (addToRecentlyDownloaded) {
                            addToRecentlyDownloadedPlaylist(updatedSong.id)
                        }

                        _downloads.update { current ->
                            val existing = current[downloadId] ?: return@update current
                            current + (downloadId to existing.copy(status = DownloadStatus.COMPLETED, progress = 1.0f))
                        }

                        return@withContext updatedSong
                    }
                    DuplicateResolution.KEEP_BOTH_DIFFERENT -> {
                        // Ensure filename is unique so it does not overwrite the existing song on disk
                        val existingPath = duplicateResult.matchedSong.uri.path
                        if (existingPath != null && File(existingPath).canonicalPath == targetAudioFile.canonicalPath) {
                            val uniqueFile = File(targetDir, "${safeFilename}_${System.currentTimeMillis()}.${formatExt}")
                            if (targetAudioFile.renameTo(uniqueFile)) {
                                targetAudioFile = uniqueFile
                            }
                        }
                        // Continue to register as a separate track below
                    }
                }
            }
        }

        // Register in Central Library without Duplicates
        val registeredSong = musicScanner.registerDownloadedTrack(
            audioFile = targetAudioFile,
            title = enriched.title,
            artist = enriched.artist,
            album = enriched.album.ifBlank { "Downloads" },
            artworkFile = if (artworkFile.exists()) artworkFile else null,
            lrcFile = if (lrcFile.exists()) lrcFile else null
        )

        // Notify Android system MediaScanner
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetAudioFile.absolutePath),
                arrayOf("audio/*"),
                null
            )
        } catch (_: Exception) {}

        // Export copy to public standard Music folder (Music/OfflineMusic/)
        exportToPublicMusicFolder(
            audioFile = targetAudioFile,
            title = enriched.title,
            artist = enriched.artist,
            album = enriched.album.ifBlank { "OfflineMusic" }
        )

        // Add to Recently Downloaded if Individual Song (Max 50)
        if (addToRecentlyDownloaded) {
            addToRecentlyDownloadedPlaylist(registeredSong.id)
        }

        _downloads.update { current ->
            val existing = current[downloadId] ?: return@update current
            current + (downloadId to existing.copy(status = DownloadStatus.COMPLETED, progress = 1.0f))
        }

        downloadDao.insertOrUpdate(
            DownloadRecordEntity(
                id = downloadId,
                title = enriched.title,
                artist = enriched.artist,
                source = track.source.name,
                sourceUrl = track.sourceUrl,
                thumbnailUrl = enriched.artworkUrl ?: track.thumbnailUrl,
                status = "COMPLETED",
                progress = 1.0f,
                filePath = targetAudioFile.absolutePath,
                fileSizeBytes = targetAudioFile.length(),
                durationMs = track.durationMs,
                completedTimestamp = System.currentTimeMillis()
            )
        )

        registeredSong
    }

    private fun exportToPublicMusicFolder(
        audioFile: File,
        title: String,
        artist: String,
        album: String,
        subDirName: String = "OfflineMusic"
    ) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, audioFile.name)
                    put(android.provider.MediaStore.Audio.Media.TITLE, title)
                    put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
                    put(android.provider.MediaStore.Audio.Media.ALBUM, album)
                    put(android.provider.MediaStore.Audio.Media.MIME_TYPE, if (audioFile.extension.equals("flac", true)) "audio/flac" else "audio/mp4")
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/$subDirName/")
                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        audioFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } else {
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), subDirName)
                publicDir.mkdirs()
                val dest = File(publicDir, audioFile.name)
                audioFile.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
            }
        } catch (_: Exception) {}
    }

    private fun updateDownloadProgress(downloadId: String, fraction: Float, downloadedBytes: Long, totalBytes: Long) {
        val bounded = fraction.coerceIn(0f, 0.85f)
        _downloads.update { current ->
            val item = current[downloadId] ?: return@update current
            current + (downloadId to item.copy(
                progress = bounded,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            ))
        }
        val notifProgress = (bounded * 100).toInt()
        DownloadService.updateProgress(context, "Downloading ${_downloads.value[downloadId]?.trackTitle ?: "Music"}...", notifProgress)
    }

    private suspend fun addToRecentlyDownloadedPlaylist(trackId: String) {
        val playlistId = AppDatabase.PLAYLIST_ID_RECENTLY_DOWNLOADED

        // Remove if previously entered to place at the top
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)

        // Insert at the top (position 0)
        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = 0,
                addedTimestamp = System.currentTimeMillis()
            )
        )

        // Enforce max 50 entries in Recently Downloaded
        val count = playlistDao.getPlaylistTrackCount(playlistId)
        if (count > MAX_RECENTLY_DOWNLOADED) {
            val oldestIds = playlistDao.getOldestTrackIdsForPlaylist(playlistId)
            val excessCount = count - MAX_RECENTLY_DOWNLOADED
            val toRemove = oldestIds.take(excessCount)
            for (oldId in toRemove) {
                playlistDao.removeTrackFromPlaylist(playlistId, oldId)
            }
        }
    }

    private fun checkAndStopServiceIfIdle() {
        val hasActive = _downloads.value.values.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.ENRICHING || it.status == DownloadStatus.DUPLICATE_FOUND
        }
        if (!hasActive) {
            DownloadService.stopService(context)
        }
    }

    fun deleteDownloadRecord(id: String) {
        scope.launch(Dispatchers.IO) {
            downloadDao.deleteById(id)
        }
    }

    fun clearDownloadHistory() {
        scope.launch(Dispatchers.IO) {
            downloadDao.clearAll()
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|“”„‟«»`~]"), "_")
            .replace(Regex("[\\p{Cntrl}]"), "")
            .trim()
            .take(80)
    }
}
