package com.example.offlinemusic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.ui.MainViewModel
import com.example.offlinemusic.ui.MusicTab
import com.example.offlinemusic.ui.components.AlbumCard
import com.example.offlinemusic.ui.components.ArtistCard
import com.example.offlinemusic.ui.components.FolderPickerPrompt
import com.example.offlinemusic.ui.components.FullPlayerSheet
import com.example.offlinemusic.ui.components.MiniPlayer
import com.example.offlinemusic.ui.components.SongListItem
import com.example.offlinemusic.ui.components.SortFilterSheet
import com.example.offlinemusic.ui.components.TopBar

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
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedAlbumDetail by viewModel.selectedAlbumDetail.collectAsState()
    val selectedArtistDetail by viewModel.selectedArtistDetail.collectAsState()
    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsState()
    val isSortSheetVisible by viewModel.isSortSheetVisible.collectAsState()
    val selectedYearFilter by viewModel.selectedYearFilter.collectAsState()
    val selectedGenreFilter by viewModel.selectedGenreFilter.collectAsState()

    val songs by viewModel.filteredSongs.collectAsState()
    val albums by viewModel.filteredAlbums.collectAsState()
    val artistGroups by viewModel.artistGroups.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()
    val availableGenres by viewModel.availableGenres.collectAsState()

    // Player state
    val currentSong by viewModel.playerManager.currentSong.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val positionMs by viewModel.playerManager.currentPositionMs.collectAsState()
    val durationMs by viewModel.playerManager.durationMs.collectAsState()
    val isShuffleEnabled by viewModel.playerManager.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.playerManager.repeatMode.collectAsState()
    val queue by viewModel.playerManager.queue.collectAsState()

    if (selectedFolderUri == null) {
        FolderPickerPrompt(onSelectFolderClick = onPickFolderClick)
        return
    }

    // Detail screens if selected
    if (selectedAlbumDetail != null) {
        AlbumDetailScreen(
            album = selectedAlbumDetail!!,
            currentSong = currentSong,
            isPlaying = isPlaying,
            onBackClick = { viewModel.closeAlbumDetail() },
            onSongClick = { song, albumSongs -> viewModel.playSong(song, albumSongs) },
            onPlayAllClick = { viewModel.playAlbum(selectedAlbumDetail!!) },
            onShuffleAllClick = { viewModel.shuffleAll(selectedAlbumDetail!!.songs) }
        )
        return
    }

    if (selectedArtistDetail != null) {
        ArtistDetailScreen(
            artistGroup = selectedArtistDetail!!,
            currentSong = currentSong,
            isPlaying = isPlaying,
            onBackClick = { viewModel.closeArtistDetail() },
            onSongClick = { song, artistSongs -> viewModel.playSong(song, artistSongs) },
            onPlayAllClick = {
                if (selectedArtistDetail!!.songs.isNotEmpty()) {
                    viewModel.playSong(selectedArtistDetail!!.songs.first(), selectedArtistDetail!!.songs)
                }
            },
            onShuffleAllClick = { viewModel.shuffleAll(selectedArtistDetail!!.songs) }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                folderName = selectedFolderName,
                isLoading = isLoading,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                hasActiveFilter = selectedYearFilter != null || selectedGenreFilter != null,
                onChangeFolderClick = onPickFolderClick,
                onRescanClick = { viewModel.rescan() },
                onSortFilterClick = { viewModel.showSortSheet() }
            )
        },
        bottomBar = {
            if (currentSong != null) {
                MiniPlayer(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPauseClick = { viewModel.playerManager.togglePlayPause() },
                    onSkipNextClick = { viewModel.playerManager.skipToNext() },
                    onStopClick = { viewModel.playerManager.stopPlayback() },
                    onExpandClick = { viewModel.showFullPlayer() }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Modern Monochrome Tabs (Songs, Albums, Artists)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MusicTab.values().forEach { tab ->
                    val isTabSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isTabSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.setSelectedTab(tab) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (tab) {
                                MusicTab.SONGS -> "SONGS (${songs.size})"
                                MusicTab.ALBUMS -> "ALBUMS (${albums.size})"
                                MusicTab.ARTISTS -> "ARTISTS (${artistGroups.size})"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            ),
                            color = if (isTabSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Play / Shuffle actions bar and active filter pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Play All
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable {
                                if (songs.isNotEmpty()) {
                                    viewModel.playSong(songs.first(), songs)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Play All",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Shuffle All
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable { viewModel.shuffleAll(songs) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Active Filters
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedYearFilter != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { viewModel.setSelectedYearFilter(null) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Year: $selectedYearFilter",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    if (selectedGenreFilter != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { viewModel.setSelectedGenreFilter(null) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedGenreFilter!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Tab Content
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Scanning music folders...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (songs.isEmpty() && albums.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No audio files found in selected directory",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Make sure your music folder has MP3, M4A, FLAC, or WAV files inside it or its subfolders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                when (selectedTab) {
                    MusicTab.SONGS -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(songs, key = { it.id }) { song ->
                                SongListItem(
                                    song = song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentSong?.id == song.id,
                                    onClick = { viewModel.playSong(song, songs) }
                                )
                            }
                        }
                    }

                    MusicTab.ALBUMS -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albums, key = { it.name }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { viewModel.openAlbumDetail(album) },
                                    onPlayClick = { viewModel.playAlbum(album) }
                                )
                            }
                        }
                    }

                    MusicTab.ARTISTS -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(artistGroups, key = { it.name }) { artistGroup ->
                                ArtistCard(
                                    artistGroup = artistGroup,
                                    onClick = { viewModel.openArtistDetail(artistGroup) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Player BottomSheet
    if (isFullPlayerVisible && currentSong != null) {
        FullPlayerSheet(
            song = currentSong,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            queue = queue,
            onPlayPauseClick = { viewModel.playerManager.togglePlayPause() },
            onSkipNextClick = { viewModel.playerManager.skipToNext() },
            onSkipPreviousClick = { viewModel.playerManager.skipToPrevious() },
            onSeekTo = { viewModel.playerManager.seekTo(it) },
            onToggleShuffle = { viewModel.playerManager.toggleShuffle() },
            onToggleRepeat = { viewModel.playerManager.toggleRepeat() },
            onStopClick = { viewModel.playerManager.stopPlayback() },
            onPlayQueueIndex = { viewModel.playerManager.playSongAtIndex(it) },
            onDismiss = { viewModel.hideFullPlayer() }
        )
    }

    // Sort & Filter Sheet
    if (isSortSheetVisible) {
        SortFilterSheet(
            sortOption = sortOption,
            onSortOptionChange = { viewModel.setSortOption(it) },
            availableYears = availableYears,
            selectedYear = selectedYearFilter,
            onSelectYear = { viewModel.setSelectedYearFilter(it) },
            availableGenres = availableGenres,
            selectedGenre = selectedGenreFilter,
            onSelectGenre = { viewModel.setSelectedGenreFilter(it) },
            onDismiss = { viewModel.hideSortSheet() }
        )
    }
}
