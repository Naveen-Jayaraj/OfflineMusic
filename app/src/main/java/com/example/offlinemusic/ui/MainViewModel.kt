package com.example.offlinemusic.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinemusic.data.database.MusicDatabase
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.ArtistProfile
import com.example.offlinemusic.data.model.Playlist
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.data.model.SortBy
import com.example.offlinemusic.data.model.SortOption
import com.example.offlinemusic.data.model.SortOrder
import com.example.offlinemusic.data.repository.MusicPreferences
import com.example.offlinemusic.data.repository.MusicScanner
import com.example.offlinemusic.player.MusicPlayerManager
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.database.entities.DownloadRecordEntity
import com.example.offlinemusic.data.repository.ArtistNormalizer
import com.example.offlinemusic.ui.components.ArtistReviewPromptData
import com.example.offlinemusic.ui.navigation.BottomNavTab
import com.example.offlinemusic.downloader.DownloadManager
import com.example.offlinemusic.downloader.engine.LrclibClient
import com.example.offlinemusic.downloader.engine.LucidaDownloader
import com.example.offlinemusic.downloader.engine.SpotifyPlaylistClient
import com.example.offlinemusic.downloader.engine.YouTubeDownloader
import com.example.offlinemusic.downloader.models.DownloadProgress
import com.example.offlinemusic.downloader.models.DownloadSource
import com.example.offlinemusic.downloader.models.DownloadSourceFilter
import com.example.offlinemusic.downloader.models.DownloadableTrack
import com.example.offlinemusic.downloader.models.DuplicatePromptData
import com.example.offlinemusic.downloader.models.DuplicateResolution
import com.example.offlinemusic.downloader.models.SpotifyPlaylistInfo
import com.example.offlinemusic.downloader.models.SyncedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DetailScreenType {
    PLAYLIST,
    ALBUM,
    ARTIST,
    DOWNLOADS
}

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<String> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val albums: List<Album> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = MusicPreferences(application)
    val scanner = MusicScanner(application)
    val playerManager = MusicPlayerManager.getInstance(application)

    private val _selectedFolderUri = MutableStateFlow(preferences.selectedFolderUri)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _selectedFolderName = MutableStateFlow(preferences.selectedFolderName)
    val selectedFolderName: StateFlow<String?> = _selectedFolderName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _allPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val allPlaylists: StateFlow<List<Playlist>> = _allPlaylists.asStateFlow()

    private val _allAlbums = MutableStateFlow<List<Album>>(emptyList())
    val allAlbums: StateFlow<List<Album>> = _allAlbums.asStateFlow()

    private val _allArtists = MutableStateFlow<List<ArtistEntity>>(emptyList())
    val allArtists: StateFlow<List<ArtistEntity>> = _allArtists.asStateFlow()

    private val _pendingReviewCount = MutableStateFlow(0)
    val pendingReviewCount: StateFlow<Int> = _pendingReviewCount.asStateFlow()

    private val _currentArtistReview = MutableStateFlow<ArtistReviewPromptData?>(null)
    val currentArtistReview: StateFlow<ArtistReviewPromptData?> = _currentArtistReview.asStateFlow()

    private var lastPromptActionTime = 0L
    private val PROMPT_COOLDOWN_MS = 60_000L // 60 seconds minimum between automatic popups

    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private val _currentTab = MutableStateFlow(BottomNavTab.HOME)
    val currentTab: StateFlow<BottomNavTab> = _currentTab.asStateFlow()

    private val _recentSearches = MutableStateFlow(preferences.getRecentSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(preferences.sortOption)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedPlaylistDetail = MutableStateFlow<Playlist?>(null)
    val selectedPlaylistDetail: StateFlow<Playlist?> = _selectedPlaylistDetail.asStateFlow()

    private val _selectedAlbumDetail = MutableStateFlow<Album?>(null)
    val selectedAlbumDetail: StateFlow<Album?> = _selectedAlbumDetail.asStateFlow()

    private val _selectedArtistProfile = MutableStateFlow<ArtistProfile?>(null)
    val selectedArtistProfile: StateFlow<ArtistProfile?> = _selectedArtistProfile.asStateFlow()

    private val _artistChooserList = MutableStateFlow<List<String>?>(null)
    val artistChooserList: StateFlow<List<String>?> = _artistChooserList.asStateFlow()

    private val _detailStack = MutableStateFlow<List<DetailScreenType>>(emptyList())
    val detailStack: StateFlow<List<DetailScreenType>> = _detailStack.asStateFlow()

    private val _currentPlayingPlaylistId = MutableStateFlow<String?>(null)
    val currentPlayingPlaylistId: StateFlow<String?> = _currentPlayingPlaylistId.asStateFlow()

    private val _songForEditMetadata = MutableStateFlow<Song?>(null)
    val songForEditMetadata: StateFlow<Song?> = _songForEditMetadata.asStateFlow()

    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    private val _isSortSheetVisible = MutableStateFlow(false)
    val isSortSheetVisible: StateFlow<Boolean> = _isSortSheetVisible.asStateFlow()

    private val _selectedYearFilter = MutableStateFlow<Int?>(null)
    val selectedYearFilter: StateFlow<Int?> = _selectedYearFilter.asStateFlow()

    private val _selectedGenreFilter = MutableStateFlow<String?>(null)
    val selectedGenreFilter: StateFlow<String?> = _selectedGenreFilter.asStateFlow()

    val downloadManager = DownloadManager.getInstance(application)
    val downloads: StateFlow<Map<String, DownloadProgress>> = downloadManager.downloads
    val duplicatePrompt: StateFlow<DuplicatePromptData?> = downloadManager.duplicatePrompt
    val allDownloadRecords: StateFlow<List<DownloadRecordEntity>> = downloadManager.allDownloadRecords
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _downloadSourceFilter = MutableStateFlow(preferences.downloadSourcePreference)
    val downloadSourceFilter: StateFlow<DownloadSourceFilter> = _downloadSourceFilter.asStateFlow()

    private val _onlineSearchResults = MutableStateFlow<List<DownloadableTrack>>(emptyList())
    val onlineSearchResults: StateFlow<List<DownloadableTrack>> = _onlineSearchResults.asStateFlow()

    private val _isOnlineSearching = MutableStateFlow(false)
    val isOnlineSearching: StateFlow<Boolean> = _isOnlineSearching.asStateFlow()

    private val _detectedSpotifyPlaylist = MutableStateFlow<SpotifyPlaylistInfo?>(null)
    val detectedSpotifyPlaylist: StateFlow<SpotifyPlaylistInfo?> = _detectedSpotifyPlaylist.asStateFlow()

    private val _isFetchingSpotifyPlaylist = MutableStateFlow(false)
    val isFetchingSpotifyPlaylist: StateFlow<Boolean> = _isFetchingSpotifyPlaylist.asStateFlow()

    private val _currentLyrics = MutableStateFlow<SyncedLyrics?>(null)
    val currentLyrics: StateFlow<SyncedLyrics?> = _currentLyrics.asStateFlow()

    private val _currentLyricsIndex = MutableStateFlow(-1)
    val currentLyricsIndex: StateFlow<Int> = _currentLyricsIndex.asStateFlow()

    private var onlineSearchJob: Job? = null

    // Sorted Playlists for main Spotify-style view (Home & Library screens)
    val filteredPlaylists: StateFlow<List<Playlist>> = combine(
        _allPlaylists,
        _sortOption
    ) { playlists, sort ->
        // Liked Songs always appears first, followed by Recently Downloaded
        val liked = playlists.filter { it.isLikedSpecial }
        val downloaded = playlists.filter { it.isRecentlyDownloadedSpecial }
        val others = playlists.filter { !it.isLikedSpecial && !it.isRecentlyDownloadedSpecial }

        val sortedOthers = when (sort.sortBy) {
            SortBy.TITLE, SortBy.ALBUM -> {
                if (sort.sortOrder == SortOrder.DESCENDING) {
                    others.sortedByDescending { it.name.lowercase() }
                } else {
                    others.sortedBy { it.name.lowercase() }
                }
            }
            SortBy.DURATION -> {
                if (sort.sortOrder == SortOrder.DESCENDING) {
                    others.sortedByDescending { it.totalDurationMs }
                } else {
                    others.sortedBy { it.totalDurationMs }
                }
            }
            else -> {
                if (sort.sortOrder == SortOrder.DESCENDING) {
                    others.sortedByDescending { it.songCount }
                } else {
                    others.sortedBy { it.songCount }
                }
            }
        }
        liked + downloaded + sortedOthers
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Albums for albums listing / search
    val filteredAlbums: StateFlow<List<Album>> = combine(
        _allAlbums,
        _searchQuery,
        _sortOption
    ) { albums, query, sort ->
        var result = if (query.isBlank()) {
            albums
        } else {
            val q = query.trim().lowercase()
            albums.filter { it.name.lowercase().contains(q) || it.artistSummary.lowercase().contains(q) }
        }

        when (sort.sortBy) {
            SortBy.TITLE, SortBy.ALBUM -> {
                result = if (sort.sortOrder == SortOrder.DESCENDING) {
                    result.sortedByDescending { it.name.lowercase() }
                } else {
                    result.sortedBy { it.name.lowercase() }
                }
            }
            SortBy.DURATION -> {
                result = if (sort.sortOrder == SortOrder.DESCENDING) {
                    result.sortedByDescending { it.totalDurationMs }
                } else {
                    result.sortedBy { it.totalDurationMs }
                }
            }
            else -> {
                if (sort.sortOrder == SortOrder.DESCENDING) {
                    result.sortedByDescending { it.songCount }
                } else {
                    result.sortedBy { it.songCount }
                }
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search Results categorized into Songs, Artists, Playlists, Albums
    val searchResults: StateFlow<SearchResults> = combine(
        _allSongs,
        _allPlaylists,
        _allAlbums,
        _allArtists,
        _searchQuery
    ) { songs, playlists, albums, artists, query ->
        if (query.isBlank()) {
            SearchResults()
        } else {
            val q = query.trim().lowercase()

            val matchedSongs = songs.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q) ||
                        it.albumFolder.lowercase().contains(q)
            }

            val matchedArtists = artists
                .filter { it.name.lowercase().contains(q) }
                .map { it.name }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)

            val matchedPlaylists = playlists.filter {
                it.name.lowercase().contains(q) || (it.description?.lowercase()?.contains(q) == true)
            }

            val matchedAlbums = albums.filter {
                it.name.lowercase().contains(q) || it.artistSummary.lowercase().contains(q)
            }

            SearchResults(
                songs = matchedSongs,
                artists = matchedArtists,
                playlists = matchedPlaylists,
                albums = matchedAlbums
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    val availableYears: StateFlow<List<Int>> = _allSongs.combine(_allSongs) { songs, _ ->
        songs.mapNotNull { it.year }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableGenres: StateFlow<List<String>> = _allSongs.combine(_allSongs) { songs, _ ->
        songs.mapNotNull { it.genre?.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Clean zero-song artists, normalize existing artist profiles, and sync folder/liked playlists on startup
        viewModelScope.launch {
            scanner.repository.normalizeAndCleanArtistProfiles()
            scanner.repository.syncFolderAndLikedPlaylists()
        }

        // Collect reactive Room flows
        viewModelScope.launch {
            scanner.repository.allTracks.collect { tracks ->
                _allSongs.value = tracks
            }
        }
        viewModelScope.launch {
            scanner.repository.allPlaylists.collect { playlists ->
                _allPlaylists.value = playlists
            }
        }
        viewModelScope.launch {
            scanner.repository.allAlbums.collect { albums ->
                _allAlbums.value = albums
            }
        }
        viewModelScope.launch {
            scanner.repository.likedTrackIds.collect { likedIds ->
                _likedSongIds.value = likedIds
            }
        }
        viewModelScope.launch {
            scanner.repository.allArtists.collect { artists ->
                _allArtists.value = artists
            }
        }
        viewModelScope.launch {
            scanner.repository.pendingReviewCount.collect { count ->
                _pendingReviewCount.value = count
            }
        }
        val savedUri = preferences.selectedFolderUri
        if (savedUri != null) {
            viewModelScope.launch {
                val cached = scanner.getCachedScanResult()
                if (cached.albums.isNotEmpty() || cached.songs.isNotEmpty()) {
                    _allSongs.value = cached.songs
                    _allAlbums.value = cached.albums
                    _isLoading.value = false
                } else {
                    _isLoading.value = true
                }
                rescanFolder(savedUri, forceFullRescan = false, isBackgroundSync = cached.albums.isNotEmpty())
            }
        }

        // Observe current song changes to load synchronized lyrics
        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                loadLyricsForSong(song)
            }
        }

        // Observe current position to update active lyrics line
        viewModelScope.launch {
            playerManager.currentPositionMs.collect { pos ->
                val lyrics = _currentLyrics.value
                if (lyrics != null && lyrics.isSynced) {
                    _currentLyricsIndex.value = lyrics.findActiveLineIndex(pos)
                }
            }
        }
    }

    fun onFolderSelected(uri: Uri, folderDisplayName: String) {
        preferences.selectedFolderUri = uri
        preferences.selectedFolderName = folderDisplayName
        _selectedFolderUri.value = uri
        _selectedFolderName.value = folderDisplayName
        viewModelScope.launch {
            scanner.clearCache()
            rescanFolder(uri, forceFullRescan = true, isBackgroundSync = false)
        }
    }

    fun rescan() {
        _selectedFolderUri.value?.let { uri ->
            rescanFolder(uri, forceFullRescan = false, isBackgroundSync = false)
        }
    }

    private fun rescanFolder(uri: Uri, forceFullRescan: Boolean, isBackgroundSync: Boolean) {
        viewModelScope.launch {
            if (!isBackgroundSync) {
                _isLoading.value = true
            }
            try {
                val result = scanner.scanTree(uri, forceFullRescan)
                _allSongs.value = result.songs
                _allAlbums.value = result.albums
                _likedSongIds.value = scanner.getLikedSongIds()
            } catch (e: Exception) {
                // Keep existing on error
            } finally {
                _isLoading.value = false
                // Gentle follow-up check: Wait 2.5s after scan completes so user can see their music first
                delay(2500)
                checkAndPresentNextReview(force = false)
            }
        }
    }

    fun checkAndPresentNextReview(force: Boolean = false) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            if (_currentArtistReview.value != null) return@launch
            val now = System.currentTimeMillis()
            if (!force && (now - lastPromptActionTime < PROMPT_COOLDOWN_MS)) return@launch

            val review = scanner.repository.getOldestPendingReview() ?: return@launch
            val normRaw = ArtistNormalizer.normalize(review.rawCreditedString)
            val existingList = _allArtists.value

            // 1. If an exact match while normalized already exists in library, auto-resolve immediately!
            val exactMatch = existingList.firstOrNull { it.normalizedName == normRaw }
            if (exactMatch != null) {
                scanner.repository.resolveArtistReviewWithExisting(review.id, review.rawCreditedString, exactMatch.id)
                checkAndPresentNextReview(force = force)
                return@launch
            }

            // 2. Filter candidate matches: MUST NOT be exact match while normalized
            val suggestedNames = review.suggestedArtistIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val candidateMatches = existingList.filter { artist ->
                artist.normalizedName != normRaw && (
                    artist.name in suggestedNames ||
                    ArtistNormalizer.similarity(artist.normalizedName, normRaw) in 0.65f..0.99f
                )
            }.take(3)

            // 3. If there are no candidate near-matches at all, auto-resolve cleanly as a new artist without interrupting user
            if (candidateMatches.isEmpty()) {
                val resolvedName = ArtistNormalizer.resolveCanonicalName(review.rawCreditedString)
                scanner.repository.resolveArtistReviewWithNew(review.id, review.rawCreditedString, resolvedName)
                checkAndPresentNextReview(force = force)
                return@launch
            }

            val track = scanner.repository.getTrackByIdRaw(review.trackId)
            val trackTitle = track?.title ?: "Unknown Track"

            _currentArtistReview.value = ArtistReviewPromptData(
                reviewId = review.id,
                trackId = review.trackId,
                trackTitle = trackTitle,
                rawCreditedString = review.rawCreditedString,
                suggestedNewName = ArtistNormalizer.resolveCanonicalName(review.rawCreditedString),
                candidateMatches = candidateMatches
            )
            lastPromptActionTime = now
        }
    }

    fun triggerManualReview() {
        checkAndPresentNextReview(force = true)
    }

    fun resolveArtistReviewWithExisting(
        reviewId: String,
        rawCreditedString: String,
        existingArtistId: String
    ) {
        viewModelScope.launch {
            _currentArtistReview.value = null
            lastPromptActionTime = System.currentTimeMillis()
            scanner.repository.resolveArtistReviewWithExisting(reviewId, rawCreditedString, existingArtistId)
            refreshAlbums()
        }
    }

    fun resolveArtistReviewWithNew(
        reviewId: String,
        rawCreditedString: String,
        newArtistName: String
    ) {
        viewModelScope.launch {
            _currentArtistReview.value = null
            lastPromptActionTime = System.currentTimeMillis()
            scanner.repository.resolveArtistReviewWithNew(reviewId, rawCreditedString, newArtistName)
            refreshAlbums()
        }
    }

    fun skipArtistReview(@Suppress("UNUSED_PARAMETER") reviewId: String) {
        _currentArtistReview.value = null
        lastPromptActionTime = System.currentTimeMillis()
    }

    fun dismissArtistReviewForever(reviewId: String) {
        viewModelScope.launch {
            _currentArtistReview.value = null
            lastPromptActionTime = System.currentTimeMillis()
            scanner.repository.dismissArtistReviewForever(reviewId)
        }
    }

    fun selectTab(tab: BottomNavTab) {
        _detailStack.value = emptyList()
        _selectedPlaylistDetail.value = null
        _selectedAlbumDetail.value = null
        _selectedArtistProfile.value = null
        _currentTab.value = tab
    }

    fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        preferences.addRecentSearch(query)
        _recentSearches.value = preferences.getRecentSearches()
    }

    fun removeRecentSearch(query: String) {
        preferences.removeRecentSearch(query)
        _recentSearches.value = preferences.getRecentSearches()
    }

    fun clearRecentSearches() {
        preferences.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    fun clearFolder() {
        preferences.clearFolder()
        _selectedFolderUri.value = null
        _selectedFolderName.value = null
        _allSongs.value = emptyList()
        _allPlaylists.value = emptyList()
        _allAlbums.value = emptyList()
        _allArtists.value = emptyList()
        viewModelScope.launch {
            scanner.clearCache()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        triggerOnlineSearch(query)
    }

    fun setDownloadSourceFilter(filter: DownloadSourceFilter) {
        _downloadSourceFilter.value = filter
        preferences.downloadSourcePreference = filter
        triggerOnlineSearch(_searchQuery.value)
    }

    fun downloadTrack(track: DownloadableTrack) {
        downloadManager.downloadTrack(track)
    }

    fun downloadSpotifyPlaylist(playlist: SpotifyPlaylistInfo) {
        downloadManager.downloadSpotifyPlaylist(playlist)
    }

    fun cancelDownload(id: String) {
        downloadManager.cancelDownload(id)
    }

    fun retryDownload(id: String, track: DownloadableTrack) {
        downloadManager.retryDownload(id, track)
    }

    fun resolveDuplicate(downloadId: String, resolution: DuplicateResolution) {
        downloadManager.resolveDuplicate(downloadId, resolution)
    }

    fun reopenDuplicatePrompt(downloadId: String) {
        downloadManager.reopenDuplicatePrompt(downloadId)
    }

    fun dismissDuplicatePromptTemporarily() {
        downloadManager.dismissDuplicatePromptTemporarily()
    }

    fun openDownloadsScreen() {
        _detailStack.value = _detailStack.value.filter { it != DetailScreenType.DOWNLOADS } + DetailScreenType.DOWNLOADS
    }

    fun deleteDownloadRecord(id: String) {
        downloadManager.deleteDownloadRecord(id)
    }

    fun clearDownloadHistory() {
        downloadManager.clearDownloadHistory()
    }

    fun retryDownloadRecord(record: DownloadRecordEntity) {
        val track = DownloadableTrack(
            id = record.id,
            title = record.title,
            artist = record.artist,
            durationMs = record.durationMs,
            source = when (record.source.uppercase()) {
                "LUCIDA" -> DownloadSource.LUCIDA
                else -> DownloadSource.YOUTUBE
            },
            sourceUrl = record.sourceUrl,
            thumbnailUrl = record.thumbnailUrl
        )
        downloadManager.retryDownload(record.id, track)
    }

    fun playDownloadedSong(record: DownloadRecordEntity) {
        val song = _allSongs.value.find { it.id == record.id || it.title.equals(record.title, ignoreCase = true) }
        if (song != null) {
            playSong(song, listOf(song))
        }
    }

    fun attachLyricsToSong(song: Song, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    val success = scanner.repository.saveLyricsForTrack(song.id, content)
                    if (success) {
                        val parsed = LrclibClient.parseLrcContent(content)
                        if (playerManager.currentSong.value?.id == song.id) {
                            _currentLyrics.value = parsed
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeLyricsFromSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = scanner.repository.removeLyricsForTrack(song.id)
                if (success) {
                    if (playerManager.currentSong.value?.id == song.id) {
                        _currentLyrics.value = null
                        _currentLyricsIndex.value = -1
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val sleepTimerRemainingMs: StateFlow<Long?> = playerManager.sleepTimerRemainingMs
    val sleepTimerSelectedMinutes: StateFlow<Int?> = playerManager.sleepTimerSelectedMinutes

    fun startSleepTimer(minutes: Int) {
        playerManager.startSleepTimer(minutes)
    }

    fun startSleepTimerEndOfTrack() {
        playerManager.startSleepTimerEndOfTrack()
    }

    fun cancelSleepTimer() {
        playerManager.cancelSleepTimer()
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            // Remove from player queue / playback if currently loaded
            playerManager.removeTrackFromPlayer(song.id)

            // Permanently delete file, LRC lyrics, and Room DB records
            val success = scanner.repository.deleteSongPermanently(song.id)
            if (success) {
                // If playlist detail is currently open, refresh its tracks
                val currentPl = _selectedPlaylistDetail.value
                if (currentPl != null) {
                    val full = scanner.repository.getPlaylistWithSongs(currentPl.id)
                    _selectedPlaylistDetail.value = full
                }
                // If album detail is open, refresh
                val currentAlbum = _selectedAlbumDetail.value
                if (currentAlbum != null) {
                    val full = scanner.repository.getAlbumWithSongs(currentAlbum.id)
                    _selectedAlbumDetail.value = full
                }
                // If artist profile is open, refresh
                val currentArtist = _selectedArtistProfile.value
                if (currentArtist != null) {
                    val profile = scanner.repository.getArtistProfile(currentArtist.name)
                    _selectedArtistProfile.value = profile
                }
            }
        }
    }

    private fun triggerOnlineSearch(query: String) {
        onlineSearchJob?.cancel()
        val trimmed = query.trim()

        if (SpotifyPlaylistClient.isSpotifyPlaylistUrl(trimmed)) {
            _onlineSearchResults.value = emptyList()
            _isOnlineSearching.value = false
            _isFetchingSpotifyPlaylist.value = true
            _detectedSpotifyPlaylist.value = null

            onlineSearchJob = viewModelScope.launch(Dispatchers.IO) {
                val cleanId = SpotifyPlaylistClient.extractPlaylistId(trimmed) ?: trimmed
                val info = SpotifyPlaylistClient.fetchPlaylist(cleanId).getOrNull()
                _detectedSpotifyPlaylist.value = info
                _isFetchingSpotifyPlaylist.value = false
            }
            return
        }

        _detectedSpotifyPlaylist.value = null
        _isFetchingSpotifyPlaylist.value = false

        if (trimmed.length < 2) {
            _onlineSearchResults.value = emptyList()
            _isOnlineSearching.value = false
            return
        }

        onlineSearchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350)
            _isOnlineSearching.value = true

            try {
                val filter = _downloadSourceFilter.value
                val results = when (filter) {
                    DownloadSourceFilter.YOUTUBE_ONLY -> {
                        YouTubeDownloader.search(trimmed, limit = 15)
                    }
                    DownloadSourceFilter.LUCIDA_ONLY -> {
                        LucidaDownloader.search(trimmed, limit = 15)
                    }
                    DownloadSourceFilter.BOTH -> {
                        val ytDef = async { YouTubeDownloader.search(trimmed, limit = 10) }
                        val lucidaDef = async { LucidaDownloader.search(trimmed, limit = 10) }
                        val yt = try { ytDef.await() } catch (_: Exception) { emptyList() }
                        val lucida = try { lucidaDef.await() } catch (_: Exception) { emptyList() }
                        (yt + lucida).distinctBy { "${it.title.lowercase()}_${it.artist.lowercase()}" }
                    }
                }
                _onlineSearchResults.value = results
            } catch (e: Exception) {
                _onlineSearchResults.value = emptyList()
            } finally {
                _isOnlineSearching.value = false
            }
        }
    }

    private fun loadLyricsForSong(song: Song?) {
        _currentLyrics.value = null
        _currentLyricsIndex.value = -1
        if (song == null) return

        viewModelScope.launch(Dispatchers.IO) {
            val lyricsUri = song.lyricsUri
            if (!lyricsUri.isNullOrBlank()) {
                try {
                    val file = File(Uri.parse(lyricsUri).path ?: lyricsUri)
                    if (file.exists()) {
                        _currentLyrics.value = LrclibClient.parseLrcContent(file.readText())
                        return@launch
                    }
                } catch (_: Exception) {}
            }

            // Sibling .lrc file check
            try {
                val songPath = song.uri.path
                if (songPath != null) {
                    val songFile = File(songPath)
                    val siblingLrc = File(songFile.parentFile, "${songFile.nameWithoutExtension}.lrc")
                    if (siblingLrc.exists()) {
                        _currentLyrics.value = LrclibClient.parseLrcContent(siblingLrc.readText())
                        return@launch
                    }
                }
            } catch (_: Exception) {}

            // Try fetching from LRCLIB online
            try {
                val fetched = LrclibClient.fetchLyrics(
                    artist = song.artist,
                    title = song.title,
                    album = song.album,
                    durationSeconds = if (song.durationMs > 0) song.durationMs / 1000 else null
                )
                if (fetched != null) {
                    _currentLyrics.value = fetched
                }
            } catch (_: Exception) {}
        }
    }


    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        preferences.sortOption = option
    }

    fun setSelectedYearFilter(year: Int?) {
        _selectedYearFilter.value = if (_selectedYearFilter.value == year) null else year
    }

    fun setSelectedGenreFilter(genre: String?) {
        _selectedGenreFilter.value = if (_selectedGenreFilter.value == genre) null else genre
    }

    fun openPlaylistDetail(playlist: Playlist) {
        _selectedPlaylistDetail.value = playlist
        _detailStack.value = _detailStack.value.filter { it != DetailScreenType.PLAYLIST } + DetailScreenType.PLAYLIST
        viewModelScope.launch {
            val full = scanner.repository.getPlaylistWithSongs(playlist.id)
            if (full != null && _selectedPlaylistDetail.value?.id == playlist.id) {
                _selectedPlaylistDetail.value = full
            }
        }
    }

    fun closePlaylistDetail() {
        _selectedPlaylistDetail.value = null
        _detailStack.value = _detailStack.value.filter { it != DetailScreenType.PLAYLIST }
    }

    fun openAlbumDetail(album: Album) {
        _detailStack.value = _detailStack.value.filter { it != DetailScreenType.ALBUM } + DetailScreenType.ALBUM
        viewModelScope.launch {
            val albumWithSongs = scanner.getAlbumWithSongs(album.id) ?: album
            _selectedAlbumDetail.value = albumWithSongs
        }
    }

    fun closeAlbumDetail() {
        _selectedAlbumDetail.value = null
        _detailStack.value = _detailStack.value.filter { it != DetailScreenType.ALBUM }
    }

    fun updateAlbumName(albumId: String, newName: String) {
        viewModelScope.launch {
            val updated = scanner.repository.updateAlbumName(albumId, newName)
            if (updated != null) {
                _selectedAlbumDetail.value = updated
            }
            refreshAlbums()
        }
    }

    fun handleArtistClick(artistString: String) {
        val collaborators = ArtistNormalizer.splitCollaborators(artistString)
            .map { ArtistNormalizer.resolveCanonicalName(it) }
            .filter { it.isNotBlank() }
            .distinct()

        if (collaborators.size > 1) {
            _artistChooserList.value = collaborators
        } else if (collaborators.isNotEmpty()) {
            openArtistProfile(collaborators.first())
        }
    }

    fun closeArtistChooser() {
        _artistChooserList.value = null
    }

    fun openArtistProfile(artistName: String) {
        _isFullPlayerVisible.value = false
        viewModelScope.launch {
            val collaborators = ArtistNormalizer.splitCollaborators(artistName)
                .map { ArtistNormalizer.resolveCanonicalName(it) }
                .filter { it.isNotBlank() }
            val targetName = if (collaborators.isNotEmpty()) collaborators.first() else artistName
            val profile = scanner.getArtistProfile(targetName)
            _selectedArtistProfile.value = profile
            _detailStack.value = _detailStack.value.filter { it != DetailScreenType.ARTIST } + DetailScreenType.ARTIST
        }
    }

    fun closeArtistProfile() {
        _selectedArtistProfile.value = null
        _detailStack.value = _detailStack.value.filter { it != DetailScreenType.ARTIST }
    }

    fun popDetailStack(): Boolean {
        val current = _detailStack.value
        if (current.isEmpty()) return false
        val newStack = current.dropLast(1)
        _detailStack.value = newStack
        if (!newStack.contains(DetailScreenType.PLAYLIST)) {
            _selectedPlaylistDetail.value = null
        }
        if (!newStack.contains(DetailScreenType.ALBUM)) {
            _selectedAlbumDetail.value = null
        }
        if (!newStack.contains(DetailScreenType.ARTIST)) {
            _selectedArtistProfile.value = null
        }
        return true
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val id = scanner.repository.createPlaylist(name)
            val full = scanner.repository.getPlaylistWithSongs(id)
            if (full != null) {
                openPlaylistDetail(full)
            }
        }
    }

    fun createCustomAlbum(name: String) = createPlaylist(name)

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            scanner.repository.deletePlaylist(playlistId)
            if (_selectedPlaylistDetail.value?.id == playlistId) {
                closePlaylistDetail()
            }
        }
    }

    fun deleteAlbum(albumId: String) = deletePlaylist(albumId)

    fun addSongsToPlaylist(playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch {
            scanner.repository.addSongsToPlaylist(playlistId, songIds)
            val updated = scanner.repository.getPlaylistWithSongs(playlistId)
            if (_selectedPlaylistDetail.value?.id == playlistId && updated != null) {
                _selectedPlaylistDetail.value = updated
            }
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        addSongsToPlaylist(playlistId, listOf(songId))
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            scanner.repository.removeSongFromPlaylist(playlistId, songId)
            val updated = scanner.repository.getPlaylistWithSongs(playlistId)
            if (_selectedPlaylistDetail.value?.id == playlistId && updated != null) {
                _selectedPlaylistDetail.value = updated
            }
        }
    }

    fun updatePlaylistName(playlistId: String, newName: String) {
        viewModelScope.launch {
            scanner.repository.updatePlaylistName(playlistId, newName)
            val updated = scanner.repository.getPlaylistWithSongs(playlistId)
            if (_selectedPlaylistDetail.value?.id == playlistId && updated != null) {
                _selectedPlaylistDetail.value = updated
            }
        }
    }

    fun updatePlaylistDescription(playlistId: String, newDescription: String?) {
        viewModelScope.launch {
            scanner.repository.updatePlaylistDescription(playlistId, newDescription)
            val updated = scanner.repository.getPlaylistWithSongs(playlistId)
            if (_selectedPlaylistDetail.value?.id == playlistId && updated != null) {
                _selectedPlaylistDetail.value = updated
            } else if (_selectedPlaylistDetail.value?.id == playlistId) {
                _selectedPlaylistDetail.value = _selectedPlaylistDetail.value?.copy(description = newDescription)
            }
        }
    }

    fun pausePlayback() {
        playerManager.pause()
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun updatePlaylistArtwork(playlistId: String, artworkUri: Uri?) {
        viewModelScope.launch {
            scanner.repository.updatePlaylistArtwork(playlistId, artworkUri)
            val updated = scanner.repository.getPlaylistWithSongs(playlistId)
            if (_selectedPlaylistDetail.value?.id == playlistId && updated != null) {
                _selectedPlaylistDetail.value = updated
            }
        }
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val full = if (playlist.songs.isEmpty()) scanner.repository.getPlaylistWithSongs(playlist.id) ?: playlist else playlist
            if (full.songs.isNotEmpty()) {
                val isCurrentlyPlayingThis = playerManager.isPlaying.value && (
                    _currentPlayingPlaylistId.value == playlist.id ||
                    (playerManager.currentSong.value != null && full.songs.any { it.id == playerManager.currentSong.value?.id })
                )
                if (isCurrentlyPlayingThis) {
                    playerManager.pause()
                } else {
                    _currentPlayingPlaylistId.value = playlist.id
                    playerManager.playSong(full.songs.first(), full.songs)
                }
            }
        }
    }

    fun shufflePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val full = if (playlist.songs.isEmpty()) scanner.repository.getPlaylistWithSongs(playlist.id) ?: playlist else playlist
            if (full.songs.isNotEmpty()) {
                val shuffled = full.songs.shuffled()
                playerManager.playSong(shuffled.first(), shuffled)
            }
        }
    }

    fun openMetadataEditor(song: Song) {
        _songForEditMetadata.value = song
    }

    fun closeMetadataEditor() {
        _songForEditMetadata.value = null
    }

    fun updateTrackMetadata(
        trackId: String,
        newTitle: String,
        rawArtistString: String,
        newAlbum: String?,
        newGenre: String?,
        newArtworkUri: Uri?
    ) {
        viewModelScope.launch {
            val updatedSong = scanner.updateTrackMetadata(trackId, newTitle, rawArtistString, newAlbum, newGenre, newArtworkUri)
            if (updatedSong != null) {
                playerManager.updateSongMetadata(updatedSong)
                if (_selectedAlbumDetail.value != null) {
                    _selectedAlbumDetail.value = scanner.getAlbumWithSongs(_selectedAlbumDetail.value!!.id)
                }
                if (_selectedPlaylistDetail.value != null) {
                    _selectedPlaylistDetail.value = scanner.repository.getPlaylistWithSongs(_selectedPlaylistDetail.value!!.id)
                }
            }
            _songForEditMetadata.value = null
            refreshAlbums()
        }
    }

    fun addSongsToAlbum(albumId: String, songIds: List<String>) = addSongsToPlaylist(albumId, songIds)

    fun addSongToAlbum(albumId: String, songId: String) = addSongToPlaylist(albumId, songId)

    fun removeSongFromAlbum(albumId: String, songId: String) = removeSongFromPlaylist(albumId, songId)

    fun toggleLikeSong(songId: String) {
        viewModelScope.launch {
            val isNowLiked = scanner.toggleLikeSong(songId)
            val current = _likedSongIds.value.toMutableSet()
            if (isNowLiked) {
                current.add(songId)
            } else {
                current.remove(songId)
            }
            _likedSongIds.value = current
            refreshAlbums()
            if (_selectedAlbumDetail.value?.id == MusicDatabase.ALBUM_ID_LIKED) {
                _selectedAlbumDetail.value = scanner.getAlbumWithSongs(MusicDatabase.ALBUM_ID_LIKED)
            }
        }
    }

    fun moveSongBetweenAlbums(songId: String, fromAlbumId: String, toAlbumId: String) {
        viewModelScope.launch {
            scanner.moveSongBetweenAlbums(songId, fromAlbumId, toAlbumId)
            refreshAlbums()
            if (_selectedAlbumDetail.value?.id == fromAlbumId) {
                _selectedAlbumDetail.value = scanner.getAlbumWithSongs(fromAlbumId)
            }
        }
    }

    private suspend fun refreshAlbums() {
        val albums = scanner.repository.getAllAlbumsList()
        _allAlbums.value = albums
    }

    fun showFullPlayer() {
        _isFullPlayerVisible.value = true
    }

    fun hideFullPlayer() {
        _isFullPlayerVisible.value = false
    }

    fun showSortSheet() {
        _isSortSheetVisible.value = true
    }

    fun hideSortSheet() {
        _isSortSheetVisible.value = false
    }

    fun playSong(song: Song, queue: List<Song>) {
        playerManager.playSong(song, queue)
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            val fullAlbum = if (album.songs.isEmpty()) scanner.getAlbumWithSongs(album.id) ?: album else album
            if (fullAlbum.songs.isNotEmpty()) {
                playerManager.playSong(fullAlbum.songs.first(), fullAlbum.songs)
            }
        }
    }

    fun shuffleAlbum(album: Album) {
        viewModelScope.launch {
            val fullAlbum = if (album.songs.isEmpty()) scanner.getAlbumWithSongs(album.id) ?: album else album
            if (fullAlbum.songs.isNotEmpty()) {
                val shuffled = fullAlbum.songs.shuffled()
                playerManager.playSong(shuffled.first(), shuffled)
            }
        }
    }

    fun playArtist(artistProfile: ArtistProfile) {
        if (artistProfile.songs.isNotEmpty()) {
            playerManager.playSong(artistProfile.songs.first(), artistProfile.songs)
        }
    }

    fun shuffleArtist(artistProfile: ArtistProfile) {
        if (artistProfile.songs.isNotEmpty()) {
            val shuffled = artistProfile.songs.shuffled()
            playerManager.playSong(shuffled.first(), shuffled)
        }
    }
}
