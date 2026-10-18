package com.example.offlinemusic.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinemusic.data.model.Album
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

enum class MusicTab(val title: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists")
}

data class ArtistGroup(
    val name: String,
    val songCount: Int,
    val songs: List<Song>,
    val totalDurationMs: Long,
    val artworkUri: Uri?
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = MusicPreferences(application)
    private val scanner = MusicScanner(application)
    val playerManager = MusicPlayerManager(application)

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(preferences.sortOption)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedTab = MutableStateFlow(MusicTab.SONGS)
    val selectedTab: StateFlow<MusicTab> = _selectedTab.asStateFlow()

    private val _selectedAlbumDetail = MutableStateFlow<Album?>(null)
    val selectedAlbumDetail: StateFlow<Album?> = _selectedAlbumDetail.asStateFlow()

    private val _selectedArtistDetail = MutableStateFlow<ArtistGroup?>(null)
    val selectedArtistDetail: StateFlow<ArtistGroup?> = _selectedArtistDetail.asStateFlow()

    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    private val _isSortSheetVisible = MutableStateFlow(false)
    val isSortSheetVisible: StateFlow<Boolean> = _isSortSheetVisible.asStateFlow()

    private val _selectedYearFilter = MutableStateFlow<Int?>(null)
    val selectedYearFilter: StateFlow<Int?> = _selectedYearFilter.asStateFlow()

    private val _selectedGenreFilter = MutableStateFlow<String?>(null)
    val selectedGenreFilter: StateFlow<String?> = _selectedGenreFilter.asStateFlow()

    // Filtered & Sorted Songs
    val filteredSongs: StateFlow<List<Song>> = combine(
        _allSongs,
        _searchQuery,
        _sortOption,
        _selectedYearFilter,
        _selectedGenreFilter
    ) { songs, query, sort, year, genre ->
        var result = songs

        // Filter by Year
        if (year != null) {
            result = result.filter { it.year == year }
        }

        // Filter by Genre
        if (genre != null && genre.isNotEmpty()) {
            result = result.filter { it.genre.equals(genre, ignoreCase = true) }
        }

        // Filter by Query (title, artist, album, folder, year)
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q) ||
                        it.albumFolder.lowercase().contains(q) ||
                        it.genre?.lowercase()?.contains(q) == true ||
                        it.year?.toString()?.contains(q) == true
            }
        }

        // Sorting
        val comparator = when (sort.sortBy) {
            SortBy.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortBy.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
            SortBy.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.albumFolder }
            SortBy.YEAR -> compareBy<Song> { it.year ?: 0 }
            SortBy.DURATION -> compareBy<Song> { it.durationMs }
            SortBy.DATE_ADDED -> compareBy<Song> { it.dateModified }
            SortBy.FILE_SIZE -> compareBy<Song> { it.sizeBytes }
        }

        if (sort.sortOrder == SortOrder.DESCENDING) {
            result.sortedWith(comparator.reversed())
        } else {
            result.sortedWith(comparator)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Albums (Subfolders)
    val filteredAlbums: StateFlow<List<Album>> = combine(
        _allAlbums,
        _searchQuery
    ) { albums, query ->
        if (query.isBlank()) {
            albums
        } else {
            val q = query.trim().lowercase()
            albums.filter { album ->
                album.name.lowercase().contains(q) ||
                        album.artistSummary.lowercase().contains(q) ||
                        album.songs.any { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Artist Groups
    val artistGroups: StateFlow<List<ArtistGroup>> = combine(
        _allSongs,
        _searchQuery
    ) { songs, query ->
        val groups = songs.groupBy { it.artist }.map { (artistName, artistSongs) ->
            ArtistGroup(
                name = artistName,
                songCount = artistSongs.size,
                songs = artistSongs.sortedBy { it.title.lowercase() },
                totalDurationMs = artistSongs.sumOf { it.durationMs },
                artworkUri = artistSongs.firstOrNull { it.artworkUri != null }?.artworkUri
            )
        }.sortedBy { it.name.lowercase() }

        if (query.isBlank()) {
            groups
        } else {
            val q = query.trim().lowercase()
            groups.filter {
                it.name.lowercase().contains(q) ||
                        it.songs.any { song -> song.title.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Metadata filter options extracted from loaded songs
    val availableYears: StateFlow<List<Int>> = _allSongs.combine(_allSongs) { songs, _ ->
        songs.mapNotNull { it.year }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableGenres: StateFlow<List<String>> = _allSongs.combine(_allSongs) { songs, _ ->
        songs.mapNotNull { it.genre?.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val savedUri = preferences.selectedFolderUri
        if (savedUri != null) {
            viewModelScope.launch {
                // Instantly load from persistent SQLite cache
                val cached = scanner.getCachedScanResult()
                if (cached.songs.isNotEmpty()) {
                    _allSongs.value = cached.songs
                    _allAlbums.value = cached.albums
                    _isLoading.value = false
                } else {
                    _isLoading.value = true
                }
                // Perform quick incremental diff in background (reusing unchanged files, only scanning new ones)
                rescanFolder(savedUri, forceFullRescan = false, isBackgroundSync = cached.songs.isNotEmpty())
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
            } catch (e: Exception) {
                // Keep existing cached items on error
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

    fun setSelectedTab(tab: MusicTab) {
        _selectedTab.value = tab
    }

    fun openAlbumDetail(album: Album) {
        _selectedAlbumDetail.value = album
    }

    fun closeAlbumDetail() {
        _selectedAlbumDetail.value = null
    }

    fun openArtistDetail(artist: ArtistGroup) {
        _selectedArtistDetail.value = artist
    }

    fun closeArtistDetail() {
        _selectedArtistDetail.value = null
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
        if (album.songs.isNotEmpty()) {
            playerManager.playSong(album.songs.first(), album.songs)
        }
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            val shuffled = songs.shuffled()
            playerManager.playSong(shuffled.first(), shuffled)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
