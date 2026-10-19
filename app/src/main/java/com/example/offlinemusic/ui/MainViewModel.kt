package com.example.offlinemusic.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinemusic.data.database.MusicDatabase
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.ArtistProfile
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.data.model.SortBy
import com.example.offlinemusic.data.model.SortOption
import com.example.offlinemusic.data.model.SortOrder
import com.example.offlinemusic.data.repository.MusicPreferences
import com.example.offlinemusic.data.repository.MusicScanner
import com.example.offlinemusic.player.MusicPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<String> = emptyList(),
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

    private val _allAlbums = MutableStateFlow<List<Album>>(emptyList())
    val allAlbums: StateFlow<List<Album>> = _allAlbums.asStateFlow()

    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(preferences.sortOption)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedAlbumDetail = MutableStateFlow<Album?>(null)
    val selectedAlbumDetail: StateFlow<Album?> = _selectedAlbumDetail.asStateFlow()

    private val _selectedArtistProfile = MutableStateFlow<ArtistProfile?>(null)
    val selectedArtistProfile: StateFlow<ArtistProfile?> = _selectedArtistProfile.asStateFlow()

    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    private val _isSortSheetVisible = MutableStateFlow(false)
    val isSortSheetVisible: StateFlow<Boolean> = _isSortSheetVisible.asStateFlow()

    private val _selectedYearFilter = MutableStateFlow<Int?>(null)
    val selectedYearFilter: StateFlow<Int?> = _selectedYearFilter.asStateFlow()

    private val _selectedGenreFilter = MutableStateFlow<String?>(null)
    val selectedGenreFilter: StateFlow<String?> = _selectedGenreFilter.asStateFlow()

    // Filtered Albums for main Spotify-style view
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

    // Search Results categorized into Songs, Artists, Albums
    val searchResults: StateFlow<SearchResults> = combine(
        _allSongs,
        _allAlbums,
        _searchQuery
    ) { songs, albums, query ->
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

            val matchedArtists = songs.map { it.artist }
                .distinct()
                .filter { it.lowercase().contains(q) }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)

            val matchedAlbums = albums.filter {
                it.name.lowercase().contains(q) || it.artistSummary.lowercase().contains(q)
            }

            SearchResults(
                songs = matchedSongs,
                artists = matchedArtists,
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
        viewModelScope.launch {
            _likedSongIds.value = scanner.getLikedSongIds()
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
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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

    fun openAlbumDetail(album: Album) {
        viewModelScope.launch {
            val albumWithSongs = scanner.getAlbumWithSongs(album.id) ?: album
            _selectedAlbumDetail.value = albumWithSongs
        }
    }

    fun closeAlbumDetail() {
        _selectedAlbumDetail.value = null
    }

    fun openArtistProfile(artistName: String) {
        viewModelScope.launch {
            val profile = scanner.getArtistProfile(artistName)
            _selectedArtistProfile.value = profile
        }
    }

    fun closeArtistProfile() {
        _selectedArtistProfile.value = null
    }

    fun createCustomAlbum(name: String) {
        viewModelScope.launch {
            scanner.createCustomAlbum(name)
            refreshAlbums()
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            scanner.deleteAlbum(albumId)
            if (_selectedAlbumDetail.value?.id == albumId) {
                _selectedAlbumDetail.value = null
            }
            refreshAlbums()
        }
    }

    fun addSongsToAlbum(albumId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch {
            scanner.addSongsToAlbum(albumId, songIds)
            refreshAlbums()
            if (_selectedAlbumDetail.value?.id == albumId) {
                _selectedAlbumDetail.value = scanner.getAlbumWithSongs(albumId)
            }
        }
    }

    fun addSongToAlbum(albumId: String, songId: String) {
        viewModelScope.launch {
            scanner.addSongToAlbum(albumId, songId)
            if (albumId == MusicDatabase.ALBUM_ID_LIKED) {
                val current = _likedSongIds.value.toMutableSet()
                current.add(songId)
                _likedSongIds.value = current
            }
            refreshAlbums()
            if (_selectedAlbumDetail.value?.id == albumId) {
                _selectedAlbumDetail.value = scanner.getAlbumWithSongs(albumId)
            }
        }
    }

    fun removeSongFromAlbum(albumId: String, songId: String) {
        viewModelScope.launch {
            scanner.removeSongFromAlbum(albumId, songId)
            if (albumId == MusicDatabase.ALBUM_ID_LIKED) {
                val current = _likedSongIds.value.toMutableSet()
                current.remove(songId)
                _likedSongIds.value = current
            }
            refreshAlbums()
            if (_selectedAlbumDetail.value?.id == albumId) {
                _selectedAlbumDetail.value = scanner.getAlbumWithSongs(albumId)
            }
        }
    }

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
        val albums = scanner.database.getAllAlbums()
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
