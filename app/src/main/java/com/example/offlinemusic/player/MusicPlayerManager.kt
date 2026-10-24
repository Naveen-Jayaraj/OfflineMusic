package com.example.offlinemusic.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.offlinemusic.data.repository.MusicRepository
import com.example.offlinemusic.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerManager private constructor(private val context: Context) {

    private val repository = MusicRepository(context)
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private var activeTrackId: String? = null
    private var trackPlayStartTimeMs: Long = 0L
    private var accumulatedPlayedMs: Long = 0L

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _userQueuedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val userQueuedSongIds: StateFlow<Set<String>> = _userQueuedSongIds.asStateFlow()

    data class QueueEntry(
        val instanceId: String = java.util.UUID.randomUUID().toString(),
        val song: Song,
        val isUserQueued: Boolean
    )

    private val internalQueue = mutableListOf<QueueEntry>()
    private var lastPlayedIndex: Int = -1

    private fun updateUserQueuedIds() {
        _userQueuedSongIds.value = internalQueue.filter { it.isUserQueued }.map { it.song.id }.toSet()
    }

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private val _sleepTimerSelectedMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerSelectedMinutes: StateFlow<Int?> = _sleepTimerSelectedMinutes.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var isEndOfTrackTimer: Boolean = false

    private val songMap = mutableMapOf<String, Song>()

    companion object {
        @Volatile
        private var INSTANCE: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicPlayerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get().apply {
                    addListener(PlayerListener())
                    _isPlaying.value = isPlaying
                    _isShuffleEnabled.value = shuffleModeEnabled
                    _repeatMode.value = repeatMode

                    // Restore state immediately upon connection (e.g. returning from background)
                    val currentItem = currentMediaItem
                    if (currentItem != null) {
                        val song = resolveSong(currentItem)
                        _currentSong.value = song
                        _durationMs.value = if (duration > 0) duration else (song?.durationMs ?: 0L)
                        _currentPositionMs.value = currentPosition.coerceAtLeast(0L)
                    }

                    val count = mediaItemCount
                    if (count > 0 && _queue.value.isEmpty()) {
                        val items = mutableListOf<Song>()
                        for (i in 0 until count) {
                            val resolved = resolveSong(getMediaItemAt(i))
                            if (resolved != null) {
                                items.add(resolved)
                                songMap[resolved.id] = resolved
                            }
                        }
                        if (items.isNotEmpty()) {
                            internalQueue.clear()
                            items.forEach { internalQueue.add(QueueEntry(song = it, isUserQueued = false)) }
                            _queue.value = items
                            updateUserQueuedIds()
                            lastPlayedIndex = currentMediaItemIndex
                        }
                    }
                }
                _isConnected.value = true
                startProgressTracker()
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun commitCurrentPlayEvent() {
        val trackId = activeTrackId ?: return
        if (trackPlayStartTimeMs > 0L) {
            val sessionPlayed = System.currentTimeMillis() - trackPlayStartTimeMs
            accumulatedPlayedMs += sessionPlayed
            trackPlayStartTimeMs = 0L
        }
        val totalPlayed = accumulatedPlayedMs
        val duration = _durationMs.value
        if (totalPlayed > 3000L) {
            scope.launch(Dispatchers.IO) {
                repository.recordPlayEvent(trackId, totalPlayed, duration)
            }
        }
        accumulatedPlayedMs = 0L
    }

    private fun resolveSong(mediaItem: MediaItem?): Song? {
        if (mediaItem == null) return null
        val id = mediaItem.mediaId
        if (id.isNotEmpty()) {
            val cached = songMap[id]
            if (cached != null) return cached
            scope.launch(Dispatchers.IO) {
                val fromDb = repository.getSongById(id)
                if (fromDb != null) {
                    songMap[id] = fromDb
                    if (_currentSong.value?.id == id) {
                        _currentSong.value = fromDb
                    }
                }
            }
        }
        val meta = mediaItem.mediaMetadata
        val title = meta.title?.toString()
        if (title != null) {
            return Song(
                id = id.ifEmpty { "unknown" },
                title = title,
                artist = meta.artist?.toString() ?: "Unknown Artist",
                album = meta.albumTitle?.toString() ?: "Unknown Album",
                albumFolder = meta.albumTitle?.toString() ?: "Music",
                year = null,
                genre = null,
                durationMs = mediaController?.duration?.coerceAtLeast(0L) ?: 0L,
                sizeBytes = 0L,
                dateModified = 0L,
                uri = mediaItem.requestMetadata.mediaUri ?: Uri.EMPTY,
                relativePath = "",
                artworkUri = meta.artworkUri
            )
        }
        return null
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                trackPlayStartTimeMs = System.currentTimeMillis()
                startProgressTracker()
            } else {
                commitCurrentPlayEvent()
                stopProgressTracker()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            commitCurrentPlayEvent()

            if (isEndOfTrackTimer && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                pause()
                cancelSleepTimer()
                return
            }

            val controller = mediaController
            val newIndex = controller?.currentMediaItemIndex ?: -1

            // 1. Shuffle redirection check:
            // If shuffle mode is enabled and reason is AUTO, ExoPlayer might have jumped
            // according to its internal shuffle order instead of playing our queued song.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _isShuffleEnabled.value) {
                val nextExpectedQueued = lastPlayedIndex + 1
                if (nextExpectedQueued in internalQueue.indices && internalQueue[nextExpectedQueued].isUserQueued) {
                    if (newIndex != nextExpectedQueued) {
                        controller?.seekTo(nextExpectedQueued, 0L)
                        controller?.play()
                        return
                    }
                }
            }

            // 2. Consume finished queued songs if playback moved forward:
            if (lastPlayedIndex in internalQueue.indices && lastPlayedIndex < newIndex) {
                val prevEntry = internalQueue[lastPlayedIndex]
                if (prevEntry.isUserQueued) {
                    internalQueue.removeAt(lastPlayedIndex)
                    controller?.removeMediaItem(lastPlayedIndex)
                    _queue.value = internalQueue.map { it.song }
                    updateUserQueuedIds()
                    val adjustedCurrentIndex = controller?.currentMediaItemIndex ?: (newIndex - 1)
                    lastPlayedIndex = adjustedCurrentIndex
                    activeTrackId = mediaItem?.mediaId
                    if (_isPlaying.value) {
                        trackPlayStartTimeMs = System.currentTimeMillis()
                    }
                    val song = resolveSong(mediaItem)
                    _currentSong.value = song
                    _durationMs.value = song?.durationMs ?: (controller?.duration?.coerceAtLeast(0L) ?: 0L)
                    _currentPositionMs.value = 0L
                    return
                }
            }

            lastPlayedIndex = newIndex
            activeTrackId = mediaItem?.mediaId
            if (_isPlaying.value) {
                trackPlayStartTimeMs = System.currentTimeMillis()
            }

            val song = resolveSong(mediaItem)
            _currentSong.value = song
            _durationMs.value = song?.durationMs ?: (controller?.duration?.coerceAtLeast(0L) ?: 0L)
            _currentPositionMs.value = 0L
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                if (_currentSong.value == null && mediaController?.currentMediaItem != null) {
                    _currentSong.value = resolveSong(mediaController?.currentMediaItem)
                }
                val dur = mediaController?.duration ?: 0L
                if (dur > 0L) {
                    _durationMs.value = dur
                }
            } else if (playbackState == Player.STATE_ENDED) {
                commitCurrentPlayEvent()
                _isPlaying.value = false
                _currentPositionMs.value = 0L
                if (isEndOfTrackTimer) {
                    cancelSleepTimer()
                }
                val controller = mediaController
                val curr = controller?.currentMediaItemIndex ?: -1
                if (curr in internalQueue.indices && internalQueue[curr].isUserQueued) {
                    internalQueue.removeAt(curr)
                    controller?.removeMediaItem(curr)
                    _queue.value = internalQueue.map { it.song }
                    updateUserQueuedIds()
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                        val dur = controller.duration
                        if (dur > 0L) {
                            _durationMs.value = dur
                        }
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        mediaController?.let {
            _currentPositionMs.value = it.currentPosition.coerceAtLeast(0L)
        }
    }

    fun playSong(song: Song, newQueue: List<Song>) {
        val controller = mediaController ?: return
        val queueToUse = if (newQueue.isEmpty()) listOf(song) else newQueue

        songMap.clear()
        queueToUse.forEach { songMap[it.id] = it }

        internalQueue.clear()
        queueToUse.forEach { internalQueue.add(QueueEntry(song = it, isUserQueued = false)) }
        _queue.value = queueToUse
        updateUserQueuedIds()

        val mediaItems = queueToUse.map { it.toMediaItem() }
        val startIndex = queueToUse.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        lastPlayedIndex = startIndex

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()

        _currentSong.value = song
        _durationMs.value = song.durationMs
        _currentPositionMs.value = 0L
    }

    fun playStreamingTrack(
        trackId: String,
        title: String,
        artist: String,
        streamUrl: String,
        durationMs: Long,
        thumbnailUrl: String?
    ) {
        val streamSong = Song(
            id = "stream_$trackId",
            title = title,
            artist = artist,
            album = "Online Stream",
            albumFolder = "Streams",
            year = null,
            genre = "Stream",
            durationMs = durationMs,
            sizeBytes = 0L,
            dateModified = System.currentTimeMillis(),
            uri = Uri.parse(streamUrl),
            relativePath = "",
            artworkUri = if (!thumbnailUrl.isNullOrBlank()) Uri.parse(thumbnailUrl) else null
        )
        playSong(streamSong, listOf(streamSong))
        android.widget.Toast.makeText(context, "Streaming \"$title\"...", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun onStreamDownloadCompleted(streamId: String, localSong: Song) {
        songMap[localSong.id] = localSong

        var updatedQueue = false
        for (i in internalQueue.indices) {
            if (internalQueue[i].song.id == streamId) {
                internalQueue[i] = internalQueue[i].copy(song = localSong)
                updatedQueue = true
            }
        }
        if (updatedQueue) {
            _queue.value = internalQueue.map { it.song }
        }

        if (_currentSong.value?.id == streamId) {
            _currentSong.value = localSong
        }
    }

    fun updateSongMetadata(updatedSong: Song) {
        songMap[updatedSong.id] = updatedSong
        if (_currentSong.value?.id == updatedSong.id) {
            _currentSong.value = updatedSong
        }
        for (i in internalQueue.indices) {
            if (internalQueue[i].song.id == updatedSong.id) {
                internalQueue[i] = internalQueue[i].copy(song = updatedSong)
            }
        }
        _queue.value = internalQueue.map { it.song }
        val controller = mediaController
        if (controller != null && controller.currentMediaItem?.mediaId == updatedSong.id) {
            val currIndex = controller.currentMediaItemIndex
            if (currIndex in 0 until controller.mediaItemCount) {
                controller.replaceMediaItem(currIndex, updatedSong.toMediaItem())
            }
        }
    }

    fun addToQueue(song: Song) {
        val controller = mediaController
        if (controller == null || _currentSong.value == null || internalQueue.isEmpty()) {
            playSong(song, listOf(song))
            android.widget.Toast.makeText(context, "\"${song.title}\" playing now", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        songMap[song.id] = song
        internalQueue.add(QueueEntry(song = song, isUserQueued = false))
        _queue.value = internalQueue.map { it.song }
        updateUserQueuedIds()

        controller.addMediaItem(song.toMediaItem())
        android.widget.Toast.makeText(context, "\"${song.title}\" added to bottom of queue", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun playNext(song: Song) {
        val controller = mediaController
        if (controller == null || _currentSong.value == null || internalQueue.isEmpty()) {
            playSong(song, listOf(song))
            android.widget.Toast.makeText(context, "\"${song.title}\" playing now", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        songMap[song.id] = song
        val currentIndex = controller.currentMediaItemIndex
        val baseIndex = if (currentIndex in internalQueue.indices) currentIndex else internalQueue.lastIndex

        var insertIndex = baseIndex + 1
        while (insertIndex < internalQueue.size && internalQueue[insertIndex].isUserQueued) {
            insertIndex++
        }
        insertIndex = insertIndex.coerceIn(0, internalQueue.size)

        internalQueue.add(insertIndex, QueueEntry(song = song, isUserQueued = true))
        _queue.value = internalQueue.map { it.song }
        updateUserQueuedIds()

        controller.addMediaItem(insertIndex, song.toMediaItem())
        android.widget.Toast.makeText(context, "\"${song.title}\" will play next", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in internalQueue.indices) {
            val isCurrent = index == controller.currentMediaItemIndex
            internalQueue.removeAt(index)
            controller.removeMediaItem(index)
            _queue.value = internalQueue.map { it.song }
            updateUserQueuedIds()
            if (isCurrent) {
                if (internalQueue.isEmpty()) {
                    stopPlayback()
                } else {
                    skipToNext()
                }
            }
        }
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        val currIdx = controller.currentMediaItemIndex
        for (i in internalQueue.lastIndex downTo 0) {
            if (i != currIdx && internalQueue[i].isUserQueued) {
                internalQueue.removeAt(i)
                controller.removeMediaItem(i)
            }
        }
        _queue.value = internalQueue.map { it.song }
        updateUserQueuedIds()
    }

    fun playSongAtIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in internalQueue.indices) {
            val currIdx = controller.currentMediaItemIndex
            if (index > currIdx) {
                var target = index
                var checkIdx = currIdx
                while (checkIdx < target) {
                    if (checkIdx in internalQueue.indices && internalQueue[checkIdx].isUserQueued) {
                        internalQueue.removeAt(checkIdx)
                        controller.removeMediaItem(checkIdx)
                        target--
                    } else {
                        checkIdx++
                    }
                }
                _queue.value = internalQueue.map { it.song }
                updateUserQueuedIds()
                lastPlayedIndex = target
                controller.seekTo(target, 0L)
                controller.play()
                val song = internalQueue[target].song
                _currentSong.value = song
                _durationMs.value = song.durationMs
                _currentPositionMs.value = 0L
                return
            }

            lastPlayedIndex = index
            controller.seekTo(index, 0L)
            controller.play()
            val song = internalQueue[index].song
            _currentSong.value = song
            _durationMs.value = song.durationMs
            _currentPositionMs.value = 0L
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_ENDED) {
                controller.seekTo(0, 0L)
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        val currIdx = controller.currentMediaItemIndex
        val nextQueuedIndex = currIdx + 1
        if (nextQueuedIndex in internalQueue.indices && internalQueue[nextQueuedIndex].isUserQueued) {
            controller.seekTo(nextQueuedIndex, 0L)
            controller.play()
            return
        }
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newState = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newState
        _isShuffleEnabled.value = newState
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun pause() {
        mediaController?.pause()
        _isPlaying.value = false
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return

        _sleepTimerSelectedMinutes.value = minutes
        val totalMs = minutes * 60 * 1000L
        val targetEndTime = System.currentTimeMillis() + totalMs
        _sleepTimerRemainingMs.value = totalMs

        sleepTimerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val remaining = targetEndTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    _sleepTimerRemainingMs.value = 0L
                    pause()
                    cancelSleepTimer()
                    break
                } else {
                    _sleepTimerRemainingMs.value = remaining
                }
            }
        }
    }

    fun startSleepTimerEndOfTrack() {
        cancelSleepTimer()
        isEndOfTrackTimer = true
        _sleepTimerSelectedMinutes.value = -1 // -1 signifies end of track

        val currentSongDuration = _durationMs.value
        val currentPos = _currentPositionMs.value
        val initialRemaining = (currentSongDuration - currentPos).coerceAtLeast(0L)
        _sleepTimerRemainingMs.value = initialRemaining

        sleepTimerJob = scope.launch {
            while (isActive) {
                delay(500L)
                val dur = _durationMs.value
                val pos = _currentPositionMs.value
                val rem = (dur - pos).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = rem
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        isEndOfTrackTimer = false
        _sleepTimerRemainingMs.value = null
        _sleepTimerSelectedMinutes.value = null
    }

    fun removeTrackFromPlayer(trackId: String) {
        val index = internalQueue.indexOfFirst { it.song.id == trackId }
        if (index != -1) {
            val wasPlayingDeletedSong = _currentSong.value?.id == trackId
            internalQueue.removeAt(index)
            songMap.remove(trackId)
            _queue.value = internalQueue.map { it.song }
            updateUserQueuedIds()
            val controller = mediaController
            if (controller != null && index < controller.mediaItemCount) {
                try {
                    controller.removeMediaItem(index)
                } catch (_: Exception) {}
            }
            if (internalQueue.isEmpty()) {
                stopPlayback()
            } else if (wasPlayingDeletedSong) {
                skipToNext()
            }
        }
    }

    fun stopPlayback() {
        cancelSleepTimer()
        val controller = mediaController ?: return
        controller.stop()
        controller.clearMediaItems()
        internalQueue.clear()
        _queue.value = emptyList()
        updateUserQueuedIds()
        _currentSong.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        lastPlayedIndex = -1
        stopProgressTracker()
    }

    private fun Song.toMediaItem(): MediaItem {
        val safeArtworkUri = artworkUri?.let { uri ->
            if (uri.scheme == "file" && uri.path != null) {
                try {
                    val file = java.io.File(uri.path!!)
                    if (file.exists()) {
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else uri
                } catch (e: Exception) {
                    uri
                }
            } else {
                uri
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(safeArtworkUri)
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
