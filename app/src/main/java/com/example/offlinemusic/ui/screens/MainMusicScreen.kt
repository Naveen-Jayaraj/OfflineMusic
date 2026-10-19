package com.example.offlinemusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.offlinemusic.data.model.Playlist
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.ui.MainViewModel
import com.example.offlinemusic.ui.components.AddToPlaylistDialog
import com.example.offlinemusic.ui.components.AddSongsToPlaylistSheet
import com.example.offlinemusic.ui.components.ArtistChooserDialog
import com.example.offlinemusic.ui.components.ArtistConfirmationDialog
import com.example.offlinemusic.ui.components.BottomNavBar
import com.example.offlinemusic.ui.components.CreatePlaylistDialog
import com.example.offlinemusic.ui.components.FolderPickerPrompt
import com.example.offlinemusic.ui.components.FullPlayerSheet
import com.example.offlinemusic.ui.components.MetadataEditorDialog
import com.example.offlinemusic.ui.components.MiniPlayer
import com.example.offlinemusic.ui.components.SortFilterSheet
import com.example.offlinemusic.ui.navigation.BottomNavTab

@Composable
fun MainMusicScreen(
    viewModel: MainViewModel,
    onPickFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val selectedFolderName by viewModel.selectedFolderName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val playlists by viewModel.filteredPlaylists.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val allAlbums by viewModel.allAlbums.collectAsState()
    val allArtists by viewModel.allArtists.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    val selectedPlaylistDetail by viewModel.selectedPlaylistDetail.collectAsState()
    val selectedAlbumDetail by viewModel.selectedAlbumDetail.collectAsState()
    val selectedArtistProfile by viewModel.selectedArtistProfile.collectAsState()

    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsState()
    val isSortSheetVisible by viewModel.isSortSheetVisible.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()

    val pendingReviewCount by viewModel.pendingReviewCount.collectAsState()
    val artistReviewPrompt by viewModel.currentArtistReview.collectAsState()
    val songForEditMetadata by viewModel.songForEditMetadata.collectAsState()

    val downloads by viewModel.downloads.collectAsState()
    val onlineSearchResults by viewModel.onlineSearchResults.collectAsState()
    val isOnlineSearching by viewModel.isOnlineSearching.collectAsState()
    val detectedSpotifyPlaylist by viewModel.detectedSpotifyPlaylist.collectAsState()
    val isFetchingSpotifyPlaylist by viewModel.isFetchingSpotifyPlaylist.collectAsState()
    val downloadSourceFilter by viewModel.downloadSourceFilter.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val currentLyricsIndex by viewModel.currentLyricsIndex.collectAsState()
    val artistChooserList by viewModel.artistChooserList.collectAsState()

    // Dialog states for customizable playlists
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showAddSongsToPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // Player state
    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val positionMs by viewModel.playerManager.currentPositionMs.collectAsState()
    val durationMs by viewModel.playerManager.durationMs.collectAsState()
    val isShuffleEnabled by viewModel.playerManager.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsState()
    val queue by viewModel.playerManager.queue.collectAsState()

    // System Back button handling
    BackHandler(enabled = isFullPlayerVisible) {
        viewModel.hideFullPlayer()
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedPlaylistDetail != null) {
        viewModel.closePlaylistDetail()
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedPlaylistDetail == null && selectedAlbumDetail != null) {
        viewModel.closeAlbumDetail()
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedPlaylistDetail == null && selectedAlbumDetail == null && selectedArtistProfile != null) {
        viewModel.closeArtistProfile()
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedPlaylistDetail == null && selectedAlbumDetail == null && selectedArtistProfile == null && searchQuery.isNotBlank()) {
        viewModel.setSearchQuery("")
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedPlaylistDetail == null && selectedAlbumDetail == null && selectedArtistProfile == null && searchQuery.isBlank() && currentTab != BottomNavTab.HOME) {
        viewModel.selectTab(BottomNavTab.HOME)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (selectedFolderUri == null) {
            FolderPickerPrompt(onSelectFolderClick = onPickFolderClick)
        } else {
            // Main Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // 1. Playlist Detail Screen
                    selectedPlaylistDetail != null -> {
                        PlaylistDetailScreen(
                            playlist = selectedPlaylistDetail!!,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onBackClick = { viewModel.closePlaylistDetail() },
                            onSongClick = { song, songs -> viewModel.playSong(song, songs) },
                            onPlayAllClick = { viewModel.playPlaylist(selectedPlaylistDetail!!) },
                            onPauseClick = { viewModel.pausePlayback() },
                            onShuffleAllClick = { viewModel.shufflePlaylist(selectedPlaylistDetail!!) },
                            onAddSongsClick = { showAddSongsToPlaylist = selectedPlaylistDetail },
                            onRemoveSongClick = { song -> viewModel.removeSongFromPlaylist(selectedPlaylistDetail!!.id, song.id) },
                            onDeletePlaylistClick = { viewModel.deletePlaylist(selectedPlaylistDetail!!.id) },
                            onRenamePlaylistClick = { newName -> viewModel.updatePlaylistName(selectedPlaylistDetail!!.id, newName) },
                            onEditDescriptionClick = { newDesc -> viewModel.updatePlaylistDescription(selectedPlaylistDetail!!.id, newDesc) },
                            onChangeCoverClick = { newArtUri -> viewModel.updatePlaylistArtwork(selectedPlaylistDetail!!.id, newArtUri) },
                            onAddToAnotherPlaylistClick = { song -> songForAddToPlaylist = song },
                            onAddToQueueClick = { song -> viewModel.addToQueue(song) },
                            onEditSongClick = { song -> viewModel.openMetadataEditor(song) },
                            onArtistClick = { viewModel.openArtistProfile(it) }
                        )
                    }

                    // 2. Album Detail Screen
                    selectedAlbumDetail != null -> {
                        AlbumDetailScreen(
                            album = selectedAlbumDetail!!,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onBackClick = { viewModel.closeAlbumDetail() },
                            onSongClick = { song, albumSongs -> viewModel.playSong(song, albumSongs) },
                            onPlayAllClick = { viewModel.playAlbum(selectedAlbumDetail!!) },
                            onPauseClick = { viewModel.pausePlayback() },
                            onShuffleAllClick = { viewModel.shuffleAlbum(selectedAlbumDetail!!) },
                            onEditAlbumName = { newName -> viewModel.updateAlbumName(selectedAlbumDetail!!.id, newName) },
                            onAddToPlaylistClick = { song -> songForAddToPlaylist = song },
                            onAddToQueueClick = { song -> viewModel.addToQueue(song) },
                            onEditSongClick = { song -> viewModel.openMetadataEditor(song) },
                            onArtistClick = { viewModel.openArtistProfile(it) }
                        )
                    }

                    // 3. Artist Profile Screen
                    selectedArtistProfile != null -> {
                        ArtistProfileScreen(
                            artistProfile = selectedArtistProfile!!,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onBackClick = { viewModel.closeArtistProfile() },
                            onSongClick = { song, songs -> viewModel.playSong(song, songs) },
                            onPlayAllClick = { viewModel.playArtist(selectedArtistProfile!!) },
                            onShuffleAllClick = { viewModel.shuffleArtist(selectedArtistProfile!!) },
                            onAlbumClick = { album -> viewModel.openAlbumDetail(album) },
                            onAddToPlaylistClick = { song -> songForAddToPlaylist = song },
                            onAddToQueueClick = { song -> viewModel.addToQueue(song) },
                            onEditSongClick = { song -> viewModel.openMetadataEditor(song) }
                        )
                    }

                    // 4. Bottom Navigation Tabs
                    else -> {
                        when (currentTab) {
                            BottomNavTab.HOME -> {
                                HomeScreen(
                                    playlists = playlists,
                                    folderName = selectedFolderName,
                                    isLoading = isLoading,
                                    pendingReviewCount = pendingReviewCount,
                                    onPlaylistClick = { viewModel.openPlaylistDetail(it) },
                                    onPlayPlaylistClick = { viewModel.playPlaylist(it) },
                                    onNavigateToLibrary = { viewModel.selectTab(BottomNavTab.LIBRARY) },
                                    onRescanClick = { viewModel.rescan() },
                                    onChangeFolderClick = onPickFolderClick,
                                    onReviewClick = { viewModel.triggerManualReview() }
                                )
                            }
                            BottomNavTab.SEARCH -> {
                                SearchScreen(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    recentSearches = recentSearches,
                                    onRecentSearchClick = { query ->
                                        viewModel.setSearchQuery(query)
                                        viewModel.addRecentSearch(query)
                                    },
                                    onRemoveRecentSearch = { query ->
                                        viewModel.removeRecentSearch(query)
                                    },
                                    onClearRecentSearches = {
                                        viewModel.clearRecentSearches()
                                    },
                                    searchResults = searchResults,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    onSongClick = { song, list -> viewModel.playSong(song, list) },
                                    onPlaylistClick = { viewModel.openPlaylistDetail(it) },
                                    onAlbumClick = { viewModel.openAlbumDetail(it) },
                                    onArtistClick = { viewModel.openArtistProfile(it) },
                                    onSongAddToPlaylistClick = { songForAddToPlaylist = it },
                                    onAddToQueueClick = { viewModel.addToQueue(it) },
                                    onEditSongClick = { song -> viewModel.openMetadataEditor(song) },
                                    onSaveRecentSearch = { query -> viewModel.addRecentSearch(query) },
                                    availableGenres = availableGenres,
                                    detectedSpotifyPlaylist = detectedSpotifyPlaylist,
                                    isFetchingSpotifyPlaylist = isFetchingSpotifyPlaylist,
                                    onDownloadSpotifyPlaylist = { viewModel.downloadSpotifyPlaylist(it) },
                                    onlineSearchResults = onlineSearchResults,
                                    isOnlineSearching = isOnlineSearching,
                                    downloads = downloads,
                                    onDownloadTrack = { viewModel.downloadTrack(it) },
                                    onCancelDownload = { viewModel.cancelDownload(it) },
                                    onRetryDownload = { id, track -> viewModel.retryDownload(id, track) }
                                )
                            }
                            BottomNavTab.LIBRARY -> {
                                LibraryScreen(
                                    playlists = allPlaylists,
                                    albums = allAlbums,
                                    artists = allArtists,
                                    onPlaylistClick = { viewModel.openPlaylistDetail(it) },
                                    onPlayPlaylistClick = { viewModel.playPlaylist(it) },
                                    onAlbumClick = { viewModel.openAlbumDetail(it) },
                                    onArtistClick = { viewModel.openArtistProfile(it) },
                                    onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                    onSortClick = { viewModel.showSortSheet() }
                                )
                            }
                            BottomNavTab.SETTINGS -> {
                                SettingsScreen(
                                    folderName = selectedFolderName,
                                    songCount = allSongs.size,
                                    playlistCount = allPlaylists.size,
                                    albumCount = allAlbums.size,
                                    artistCount = allArtists.size,
                                    pendingReviewCount = pendingReviewCount,
                                    isLoading = isLoading,
                                    onChangeFolderClick = onPickFolderClick,
                                    onRescanClick = { viewModel.rescan() },
                                    onReviewArtistsClick = { viewModel.triggerManualReview() },
                                    downloadSourceFilter = downloadSourceFilter,
                                    onDownloadSourceFilterChange = { viewModel.setDownloadSourceFilter(it) },
                                    onNavigateToTab = { viewModel.selectTab(it) }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Docked MiniPlayer + Bottom Navigation Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // MiniPlayer (floats directly above Bottom Navigation Bar)
                if (currentSong != null && !isFullPlayerVisible) {
                    MiniPlayer(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isLiked = currentSong?.let { it.id in likedSongIds } ?: false,
                        onToggleLike = { currentSong?.let { viewModel.toggleLikeSong(it.id) } },
                        onPlayPauseClick = { viewModel.playerManager.togglePlayPause() },
                        onSkipNextClick = { viewModel.playerManager.skipToNext() },
                        onStopClick = { viewModel.playerManager.stopPlayback() },
                        onExpandClick = { viewModel.showFullPlayer() },
                        onArtistClick = { viewModel.handleArtistClick(it) }
                    )
                }

                // Bottom Navigation Bar
                BottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        if (selectedPlaylistDetail != null) viewModel.closePlaylistDetail()
                        if (selectedAlbumDetail != null) viewModel.closeAlbumDetail()
                        if (selectedArtistProfile != null) viewModel.closeArtistProfile()
                        viewModel.selectTab(tab)
                    }
                )
            }
        }

        // Global Dialogs & Sheets
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onConfirm = { name ->
                    viewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                }
            )
        }

        if (songForAddToPlaylist != null) {
            AddToPlaylistDialog(
                song = songForAddToPlaylist!!,
                playlists = allPlaylists,
                onDismiss = { songForAddToPlaylist = null },
                onSelectPlaylist = { targetPlaylist ->
                    viewModel.addSongToPlaylist(targetPlaylist.id, songForAddToPlaylist!!.id)
                    songForAddToPlaylist = null
                },
                onCreateNewPlaylistClick = {
                    showCreatePlaylistDialog = true
                }
            )
        }

        if (artistReviewPrompt != null) {
            ArtistConfirmationDialog(
                prompt = artistReviewPrompt!!,
                existingArtists = allArtists,
                onSelectExisting = { existing ->
                    viewModel.resolveArtistReviewWithExisting(
                        reviewId = artistReviewPrompt!!.reviewId,
                        rawCreditedString = artistReviewPrompt!!.rawCreditedString,
                        existingArtistId = existing.id
                    )
                },
                onCreateNew = { newName ->
                    viewModel.resolveArtistReviewWithNew(
                        reviewId = artistReviewPrompt!!.reviewId,
                        rawCreditedString = artistReviewPrompt!!.rawCreditedString,
                        newArtistName = newName
                    )
                },
                onRemindLater = {
                    viewModel.skipArtistReview(artistReviewPrompt!!.reviewId)
                },
                onDismissForever = {
                    viewModel.dismissArtistReviewForever(artistReviewPrompt!!.reviewId)
                }
            )
        }

        if (songForEditMetadata != null) {
            MetadataEditorDialog(
                song = songForEditMetadata!!,
                onDismiss = { viewModel.closeMetadataEditor() },
                onSave = { newTitle, newArtist, newAlbum, newGenre, newArtUri ->
                    viewModel.updateTrackMetadata(
                        trackId = songForEditMetadata!!.id,
                        newTitle = newTitle,
                        rawArtistString = newArtist,
                        newAlbum = newAlbum,
                        newGenre = newGenre,
                        newArtworkUri = newArtUri
                    )
                }
            )
        }

        if (showAddSongsToPlaylist != null) {
            AddSongsToPlaylistSheet(
                playlist = showAddSongsToPlaylist!!,
                allSongs = allSongs,
                onDismiss = { showAddSongsToPlaylist = null },
                onAddSongs = { songIdsToAdd ->
                    viewModel.addSongsToPlaylist(showAddSongsToPlaylist!!.id, songIdsToAdd)
                }
            )
        }

        // Full Player Modal Sheet
        if (isFullPlayerVisible && currentSong != null) {
            FullPlayerSheet(
                song = currentSong,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                queue = queue,
                isLiked = currentSong?.let { it.id in likedSongIds } ?: false,
                onToggleLike = { currentSong?.let { viewModel.toggleLikeSong(it.id) } },
                onAddToPlaylistClick = { songForAddToPlaylist = currentSong },
                onPlayPauseClick = { viewModel.playerManager.togglePlayPause() },
                onSkipNextClick = { viewModel.playerManager.skipToNext() },
                onSkipPreviousClick = { viewModel.playerManager.skipToPrevious() },
                onSeekTo = { viewModel.playerManager.seekTo(it) },
                onToggleShuffle = { viewModel.playerManager.toggleShuffle() },
                onToggleRepeat = { viewModel.playerManager.toggleRepeat() },
                onStopClick = { viewModel.playerManager.stopPlayback() },
                onPlayQueueIndex = { viewModel.playerManager.playSongAtIndex(it) },
                onDismiss = { viewModel.hideFullPlayer() },
                onArtistClick = { artistName ->
                    viewModel.handleArtistClick(artistName)
                },
                onEditMetadataClick = { currentSong?.let { viewModel.openMetadataEditor(it) } },
                lyrics = currentLyrics,
                currentLyricsIndex = currentLyricsIndex
            )
        }

        if (artistChooserList != null) {
            ArtistChooserDialog(
                artists = artistChooserList!!,
                onSelectArtist = { chosenArtist ->
                    viewModel.closeArtistChooser()
                    viewModel.hideFullPlayer()
                    viewModel.openArtistProfile(chosenArtist)
                },
                onDismiss = { viewModel.closeArtistChooser() }
            )
        }

        // Sort & Filter Sheet
        if (isSortSheetVisible) {
            SortFilterSheet(
                sortOption = sortOption,
                onSortOptionChange = { viewModel.setSortOption(it) },
                availableYears = emptyList(),
                selectedYear = null,
                onSelectYear = {},
                availableGenres = availableGenres,
                selectedGenre = null,
                onSelectGenre = {},
                onDismiss = { viewModel.hideSortSheet() }
            )
        }
    }
}
