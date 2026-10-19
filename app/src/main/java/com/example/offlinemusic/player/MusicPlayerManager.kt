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

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

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
            activeTrackId = mediaItem?.mediaId
            if (_isPlaying.value) {
                trackPlayStartTimeMs = System.currentTimeMillis()
            }

            val song = resolveSong(mediaItem)
            _currentSong.value = song
            _durationMs.value = song?.durationMs ?: (mediaController?.duration?.coerceAtLeast(0L) ?: 0L)
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
        _queue.value = queueToUse

        val mediaItems = queueToUse.map { it.toMediaItem() }
        val startIndex = queueToUse.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()

        _currentSong.value = song
        _durationMs.value = song.durationMs
        _currentPositionMs.value = 0L
    }

    fun updateSongMetadata(updatedSong: Song) {
        songMap[updatedSong.id] = updatedSong
        if (_currentSong.value?.id == updatedSong.id) {
            _currentSong.value = updatedSong
        }
        _queue.value = _queue.value.map { if (it.id == updatedSong.id) updatedSong else it }
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
        val currentQ = _queue.value.toMutableList()
        currentQ.add(song)
        songMap[song.id] = song
        _queue.value = currentQ
        if (controller != null) {
            controller.addMediaItem(song.toMediaItem())
            if (_currentSong.value == null) {
                playSong(song, currentQ)
            }
        }
    }

    fun playSongAtIndex(index: Int) {
        val controller = mediaController ?: return
        val currentQ = _queue.value
        if (index in currentQ.indices) {
            controller.seekTo(index, 0L)
            controller.play()
            val song = currentQ[index]
            _currentSong.value = song
            _durationMs.value = song.durationMs
            _currentPositionMs.value = 0L
        }
    }

    fun pause() {
        mediaController?.pause()
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

    fun stopPlayback() {
        val controller = mediaController ?: return
        controller.stop()
        controller.clearMediaItems()
        _currentSong.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
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
