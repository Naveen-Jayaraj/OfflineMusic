package com.example.offlinemusic.downloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.example.offlinemusic.data.database.AppDatabase
import com.example.offlinemusic.data.database.entities.PlaylistEntity
import com.example.offlinemusic.data.database.entities.PlaylistTrackEntity
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.data.repository.MusicScanner
import com.example.offlinemusic.downloader.engine.FuzzyMatcher
import com.example.offlinemusic.downloader.engine.LrclibClient
import com.example.offlinemusic.downloader.engine.LucidaDownloader
import com.example.offlinemusic.downloader.engine.SpotifyPlaylistClient
import com.example.offlinemusic.downloader.engine.YouTubeDownloader
import com.example.offlinemusic.downloader.models.DownloadProgress
import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadStatus
import com.example.offlinemusic.downloader.models.DownloadableTrack
import com.example.offlinemusic.downloader.models.SpotifyPlaylistInfo
import com.example.offlinemusic.downloader.models.SpotifyTrackInfo
import com.example.offlinemusic.downloader.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = AppDatabase.getInstance(context)
    private val playlistDao = db.playlistDao()
    private val musicScanner = MusicScanner(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

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

        _downloads.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to existing.copy(status = DownloadStatus.CANCELLED, error = "Cancelled by user"))
        }

        checkAndStopServiceIfIdle()
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

        val job = scope.launch {
            DownloadService.startService(context, "Downloading ${track.title}...", 0)
            try {
                executeTrackDownload(track, addToRecentlyDownloaded = true)
            } catch (e: Exception) {
                if (cancelledIds.contains(downloadId)) {
                    _downloads.update {
                        it + (downloadId to initialProgress.copy(status = DownloadStatus.CANCELLED, error = "Cancelled"))
                    }
                } else {
                    _downloads.update {
                        it + (downloadId to initialProgress.copy(status = DownloadStatus.FAILED, error = e.localizedMessage ?: "Download failed"))
                    }
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

                var completedCount = 0

                for ((index, spotifyTrack) in playlistInfo.tracks.withIndex()) {
                    if (cancelledIds.contains(batchId)) {
                        break
                    }

                    _downloads.update { current ->
                        current + (batchId to initialBatch.copy(
                            status = DownloadStatus.DOWNLOADING,
                            batchCurrent = index + 1,
                            trackTitle = spotifyTrack.title,
                            artist = spotifyTrack.artist,
                            progress = (index.toFloat() / totalTracks.toFloat()).coerceIn(0f, 1f)
                        ))
                    }

                    val notifProgress = ((index.toFloat() / totalTracks.toFloat()) * 100).toInt()
                    DownloadService.updateProgress(context, "(${index + 1}/$totalTracks) ${spotifyTrack.title}", notifProgress)

                    try {
                        // Find match on YouTube or Lucida
                        val candidateTrack = resolveSpotifyTrackToCandidate(spotifyTrack)
                        if (candidateTrack != null) {
                            val downloadedSong = executeTrackDownload(
                                candidateTrack,
                                customTargetDir = playlistSubDir,
                                addToRecentlyDownloaded = false // DO NOT add to Recently Downloaded
                            )

                            if (downloadedSong != null) {
                                // Add to imported playlist preserving position
                                playlistDao.insertPlaylistTrack(
                                    PlaylistTrackEntity(
                                        playlistId = playlistEntityId,
                                        trackId = downloadedSong.id,
                                        position = index,
                                        addedTimestamp = System.currentTimeMillis()
                                    )
                                )
                                completedCount++
                            }
                        }
                    } catch (e: Exception) {
                        // Continue to next track in playlist on single track failure
                    }
                }

                if (cancelledIds.contains(batchId)) {
                    _downloads.update {
                        it + (batchId to initialBatch.copy(status = DownloadStatus.CANCELLED, error = "Cancelled by user"))
                    }
                } else {
                    _downloads.update {
                        it + (batchId to initialBatch.copy(
                            status = DownloadStatus.COMPLETED,
                            batchCurrent = completedCount,
                            progress = 1.0f
                        ))
                    }
                }
            } catch (e: Exception) {
                _downloads.update {
                    it + (batchId to initialBatch.copy(status = DownloadStatus.FAILED, error = e.localizedMessage ?: "Playlist download failed"))
                }
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
        addToRecentlyDownloaded: Boolean = true
    ): Song? = withContext(Dispatchers.IO) {
        val downloadId = track.id
        val targetDir = customTargetDir ?: downloadDir

        _downloads.update { current ->
            val existing = current[downloadId] ?: DownloadProgress(downloadId, track.title, track.artist)
            current + (downloadId to existing.copy(status = DownloadStatus.DOWNLOADING, progress = 0.05f))
        }

        // 1. Resolve Audio Stream URL
        val (streamUrl, formatExt) = resolveStream(track)
            ?: throw IllegalStateException("Could not obtain audio stream for ${track.title}")

        val safeFilename = sanitizeFilename("${track.artist} - ${track.title}")
        val targetAudioFile = File(targetDir, "${safeFilename}.${formatExt}")
        val tempAudioFile = File(targetDir, "${safeFilename}.${formatExt}.tmp")

        // 2. Stream Audio Bytes with Progress
        downloadFileWithProgress(streamUrl, tempAudioFile, downloadId)

        if (cancelledIds.contains(downloadId)) {
            tempAudioFile.delete()
            return@withContext null
        }

        if (targetAudioFile.exists()) targetAudioFile.delete()
        tempAudioFile.renameTo(targetAudioFile)

        _downloads.update { current ->
            val existing = current[downloadId] ?: return@update current
            current + (downloadId to existing.copy(status = DownloadStatus.ENRICHING, progress = 0.85f))
        }

        // 3. Fetch Lyrics (.lrc)
        val lrcFile = File(targetDir, "${safeFilename}.lrc")
        try {
            val durationSec = track.durationMs / 1000
            val lyrics = LrclibClient.fetchLyrics(
                artist = track.artist,
                title = track.title,
                album = track.album.ifBlank { null },
                durationSeconds = if (durationSec > 0) durationSec else null
            )
            if (lyrics != null) {
                LrclibClient.saveToLrcFile(lyrics, lrcFile)
            }
        } catch (_: Exception) {}

        // 4. Download Thumbnail Artwork
        val artworkFile = File(targetDir, "${safeFilename}.jpg")
        if (!track.thumbnailUrl.isNullOrBlank()) {
            try {
                val req = Request.Builder().url(track.thumbnailUrl).build()
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

        // 5. Register in Central Library without Duplicates
        val registeredSong = musicScanner.registerDownloadedTrack(
            audioFile = targetAudioFile,
            title = track.title,
            artist = track.artist,
            album = track.album.ifBlank { "Downloads" },
            artworkFile = if (artworkFile.exists()) artworkFile else null,
            lrcFile = if (lrcFile.exists()) lrcFile else null
        )

        // 6. Add to Recently Downloaded if Individual Song (Max 50)
        if (addToRecentlyDownloaded) {
            addToRecentlyDownloadedPlaylist(registeredSong.id)
        }

        _downloads.update { current ->
            val existing = current[downloadId] ?: return@update current
            current + (downloadId to existing.copy(status = DownloadStatus.COMPLETED, progress = 1.0f))
        }

        registeredSong
    }

    private suspend fun resolveStream(track: DownloadableTrack): Pair<String, String>? {
        if (track.source == DownloadSource.LUCIDA) {
            val lucidaStream = LucidaDownloader.getDownloadStreamUrl(track.id)
            if (lucidaStream != null) {
                return Pair(lucidaStream, "flac")
            }
            // Fallback to YouTube if Lucida fails
            val ytFallback = YouTubeDownloader.search("${track.artist} - ${track.title}", limit = 1).firstOrNull()
            if (ytFallback != null) {
                val ytStream = YouTubeDownloader.getAudioStreamUrl(ytFallback.id)
                if (ytStream != null) return Pair(ytStream, "mp3")
            }
        } else {
            val ytStream = YouTubeDownloader.getAudioStreamUrl(track.id)
            if (ytStream != null) {
                return Pair(ytStream, "mp3")
            }
            // Fallback to Lucida if YouTube fails
            val lucidaFallback = LucidaDownloader.search("${track.artist} - ${track.title}", limit = 1).firstOrNull()
            if (lucidaFallback != null) {
                val lucidaStream = LucidaDownloader.getDownloadStreamUrl(lucidaFallback.id)
                if (lucidaStream != null) return Pair(lucidaStream, "flac")
            }
        }
        return null
    }

    private fun downloadFileWithProgress(url: String, destination: File, downloadId: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: Failed to download stream")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(destination)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead: Long = 0
            var lastUpdate = System.currentTimeMillis()

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (cancelledIds.contains(downloadId)) {
                        break
                    }
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 300) {
                        lastUpdate = now
                        val fraction = if (totalBytes > 0) {
                            (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.85f)
                        } else {
                            0.5f
                        }
                        _downloads.update { current ->
                            val item = current[downloadId] ?: return@update current
                            current + (downloadId to item.copy(
                                progress = fraction,
                                downloadedBytes = totalRead,
                                totalBytes = totalBytes
                            ))
                        }

                        val notifProgress = (fraction * 100).toInt()
                        DownloadService.updateProgress(context, "Downloading ${_downloads.value[downloadId]?.trackTitle ?: "Music"}...", notifProgress)
                    }
                }
                outputStream.flush()
            } finally {
                outputStream.close()
                inputStream.close()
            }
        }
    }

    private suspend fun addToRecentlyDownloadedPlaylist(trackId: String) {
        val playlistId = AppDatabase.PLAYLIST_ID_RECENTLY_DOWNLOADED

        // Check if already in playlist
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
                // Only removes playlist relation; never deletes the actual song file!
                playlistDao.removeTrackFromPlaylist(playlistId, oldId)
            }
        }
    }

    private fun checkAndStopServiceIfIdle() {
        val hasActive = _downloads.value.values.any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.ENRICHING
        }
        if (!hasActive) {
            DownloadService.stopService(context)
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80)
    }
}
