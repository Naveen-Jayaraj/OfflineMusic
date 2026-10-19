package com.example.offlinemusic.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.ui.MainViewModel
import com.example.offlinemusic.ui.components.AddToAlbumDialog
import com.example.offlinemusic.ui.components.AddSongsToAlbumSheet
import com.example.offlinemusic.ui.components.AlbumCard
import com.example.offlinemusic.ui.components.ArtworkView
import com.example.offlinemusic.ui.components.CreateAlbumDialog
import com.example.offlinemusic.ui.components.FolderPickerPrompt
import com.example.offlinemusic.ui.components.FullPlayerSheet
import com.example.offlinemusic.ui.components.MiniPlayer
import com.example.offlinemusic.ui.components.MoveSongDialog
import com.example.offlinemusic.ui.components.SortFilterSheet

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
    val albums by viewModel.filteredAlbums.collectAsState()
    val allAlbums by viewModel.allAlbums.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedAlbumDetail by viewModel.selectedAlbumDetail.collectAsState()
    val selectedArtistProfile by viewModel.selectedArtistProfile.collectAsState()

    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsState()
    val isSortSheetVisible by viewModel.isSortSheetVisible.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()

    // Dialog states for customizable albums
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var songForAddToAlbum by remember { mutableStateOf<Song?>(null) }
    var songForMove by remember { mutableStateOf<Song?>(null) }
    var showAddSongsToAlbum by remember { mutableStateOf<Album?>(null) }

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

    BackHandler(enabled = !isFullPlayerVisible && selectedAlbumDetail != null) {
        viewModel.closeAlbumDetail()
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedAlbumDetail == null && selectedArtistProfile != null) {
        viewModel.closeArtistProfile()
    }

    BackHandler(enabled = !isFullPlayerVisible && selectedAlbumDetail == null && selectedArtistProfile == null && searchQuery.isNotBlank()) {
        viewModel.setSearchQuery("")
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Active Screen Content
        when {
            selectedFolderUri == null -> {
                FolderPickerPrompt(onSelectFolderClick = onPickFolderClick)
            }

            // 1. Album Detail Screen
            selectedAlbumDetail != null -> {
                AlbumDetailScreen(
                    album = selectedAlbumDetail!!,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onBackClick = { viewModel.closeAlbumDetail() },
                    onSongClick = { song, albumSongs -> viewModel.playSong(song, albumSongs) },
                    onPlayAllClick = { viewModel.playAlbum(selectedAlbumDetail!!) },
                    onShuffleAllClick = { viewModel.shuffleAlbum(selectedAlbumDetail!!) },
                    onAddSongsClick = { showAddSongsToAlbum = selectedAlbumDetail },
                    onRemoveSongClick = { song -> viewModel.removeSongFromAlbum(selectedAlbumDetail!!.id, song.id) },
                    onMoveSongClick = { song -> songForMove = song },
                    onAddToAnotherAlbumClick = { song -> songForAddToAlbum = song },
                    onDeleteAlbumClick = { viewModel.deleteAlbum(selectedAlbumDetail!!.id) }
                )
            }

            // 2. Spotify-style Artist Profile Screen
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
                    onAddToAlbumClick = { song -> songForAddToAlbum = song }
                )
            }

            // 3. Main Home Screen (Spotify Albums Grid & Categorized Search)
            else -> {
                HomeScreen(
                    selectedFolderName = selectedFolderName,
                    searchQuery = searchQuery,
                    isLoading = isLoading,
                    albums = albums,
                    searchResults = searchResults,
                    onPickFolderClick = onPickFolderClick,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onCreateAlbumClick = { showCreateAlbumDialog = true },
                    onRescanClick = { viewModel.rescan() },
                    onSortClick = { viewModel.showSortSheet() },
                    onAlbumClick = { viewModel.openAlbumDetail(it) },
                    onPlayAlbumClick = { viewModel.playAlbum(it) },
                    onSongClick = { song, list -> viewModel.playSong(song, list) },
                    onArtistClick = { viewModel.openArtistProfile(it) },
                    onSongAddToAlbumClick = { songForAddToAlbum = it }
                )
            }
        }

        // Global Docked MiniPlayer (Always visible at the bottom on ANY screen when playing/paused)
        if (currentSong != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
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
                    onExpandClick = { viewModel.showFullPlayer() }
                )
            }
        }

        // Global Dialogs & Sheets (Always rendered so they trigger anywhere without closing albums)
        if (showCreateAlbumDialog) {
            CreateAlbumDialog(
                onDismiss = { showCreateAlbumDialog = false },
                onConfirm = { name ->
                    viewModel.createCustomAlbum(name)
                    showCreateAlbumDialog = false
                }
            )
        }

        if (songForAddToAlbum != null) {
            AddToAlbumDialog(
                song = songForAddToAlbum!!,
                albums = allAlbums,
                onDismiss = { songForAddToAlbum = null },
                onSelectAlbum = { targetAlbum ->
                    viewModel.addSongToAlbum(targetAlbum.id, songForAddToAlbum!!.id)
                    songForAddToAlbum = null
                },
                onCreateNewAlbumClick = {
                    showCreateAlbumDialog = true
                }
            )
        }

        if (songForMove != null && selectedAlbumDetail != null) {
            MoveSongDialog(
                song = songForMove!!,
                currentAlbumId = selectedAlbumDetail!!.id,
                albums = allAlbums,
                onDismiss = { songForMove = null },
                onTargetAlbumSelected = { destAlbum ->
                    viewModel.moveSongBetweenAlbums(songForMove!!.id, selectedAlbumDetail!!.id, destAlbum.id)
                    songForMove = null
                }
            )
        }

        if (showAddSongsToAlbum != null) {
            AddSongsToAlbumSheet(
                album = showAddSongsToAlbum!!,
                allSongs = allSongs,
                onDismiss = { showAddSongsToAlbum = null },
                onAddSongs = { songsToAdd ->
                    viewModel.addSongsToAlbum(showAddSongsToAlbum!!.id, songsToAdd.map { it.id })
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
                onAddToAlbumClick = { songForAddToAlbum = currentSong },
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
                availableYears = emptyList(),
                selectedYear = null,
                onSelectYear = {},
                availableGenres = emptyList(),
                selectedGenre = null,
                onSelectGenre = {},
                onDismiss = { viewModel.hideSortSheet() }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    selectedFolderName: String?,
    searchQuery: String,
    isLoading: Boolean,
    albums: List<Album>,
    searchResults: com.example.offlinemusic.ui.SearchResults,
    onPickFolderClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCreateAlbumClick: () -> Unit,
    onRescanClick: () -> Unit,
    onSortClick: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlayAlbumClick: (Album) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onArtistClick: (String) -> Unit,
    onSongAddToAlbumClick: (Song) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ALBUMS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Folder Pill
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable { onPickFolderClick() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = selectedFolderName ?: "Folder",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• Change",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Create Album
                        IconButton(onClick = onCreateAlbumClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Album",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // Rescan
                        IconButton(onClick = onRescanClick, enabled = !isLoading) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rescan",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        // Sort
                        IconButton(onClick = onSortClick) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search songs, artists, albums...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (searchQuery.isNotBlank()) {
                // Categorized Search Results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Artists Section
                    if (searchResults.artists.isNotEmpty()) {
                        item {
                            Text(
                                text = "ARTISTS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(searchResults.artists) { artistName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .clickable { onArtistClick(artistName) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = artistName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Artist • View profile & albums",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Songs Section
                    if (searchResults.songs.isNotEmpty()) {
                        item {
                            Text(
                                text = "SONGS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(searchResults.songs, key = { it.id }) { song ->
                            var songMenuExpanded by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable { onSongClick(song, searchResults.songs) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkView(
                                    artworkUri = song.artworkUri,
                                    size = 40.dp,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${song.artist} • ${song.formattedDuration}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { songMenuExpanded = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = songMenuExpanded,
                                        onDismissRequest = { songMenuExpanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Add to Album...", color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                songMenuExpanded = false
                                                onSongAddToAlbumClick(song)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("View Artist Profile", color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                songMenuExpanded = false
                                                onArtistClick(song.artist)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Albums Section
                    if (searchResults.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = "ALBUMS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(searchResults.albums, key = { it.id }) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable { onAlbumClick(album) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkView(
                                    artworkUri = album.artworkUri,
                                    size = 44.dp,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Album • ${album.songCount} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            } else {
                // Default View: Just Show Albums (Spotify Style)
                if (isLoading && albums.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Loading albums...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (albums.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "No albums found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create a custom album with '+', or choose a folder containing music.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                onPlayClick = { onPlayAlbumClick(album) }
                            )
                        }
                    }
                }
            }
        }
    }
}
