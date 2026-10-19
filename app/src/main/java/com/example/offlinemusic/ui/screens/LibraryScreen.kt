package com.example.offlinemusic.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.Playlist
import com.example.offlinemusic.ui.components.ArtworkView

enum class LibraryFilter(val label: String) {
    ALL("All"),
    PLAYLISTS("Playlists"),
    FOLDERS("Folders"),
    ALBUMS("Albums"),
    ARTISTS("Artists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    albums: List<Album>,
    artists: List<ArtistEntity>,
    onPlaylistClick: (Playlist) -> Unit,
    onPlayPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }

    // Separate playlists into liked, recently downloaded, custom, and folder
    val likedPlaylist = playlists.firstOrNull { it.isLikedSpecial }
    val downloadedPlaylist = playlists.firstOrNull { it.isRecentlyDownloadedSpecial }
    val userPlaylists = playlists.filter { !it.isLikedSpecial && !it.isRecentlyDownloadedSpecial && it.playlistType == "USER" }
    val folderPlaylists = playlists.filter { !it.isLikedSpecial && !it.isRecentlyDownloadedSpecial && it.playlistType == "FOLDER" }
    val otherPlaylists = playlists.filter { !it.isLikedSpecial && !it.isRecentlyDownloadedSpecial && it.playlistType != "USER" && it.playlistType != "FOLDER" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top Bar: "Your Library" with "+" create playlist & sort action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Create Playlist Button
                IconButton(onClick = onCreatePlaylistClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Sort Button
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort Library",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // 2. Filter Chips Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryFilter.values()) { filter ->
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = filter.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        selectedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Main List of All Created Libraries & Collections
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Liked Songs Special Library (Shown for ALL, PLAYLISTS)
            if ((selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.PLAYLISTS) && likedPlaylist != null) {
                item(key = "liked_songs") {
                    LibraryItemRow(
                        title = likedPlaylist.name,
                        subtitle = "Auto-Playlist • ${likedPlaylist.songCount} liked songs",
                        artworkUri = likedPlaylist.artworkUri,
                        isLikedSpecial = true,
                        icon = Icons.Default.Favorite,
                        iconTint = Color(0xFFE91E63),
                        onClick = { onPlaylistClick(likedPlaylist) },
                        onPlayClick = { onPlayPlaylistClick(likedPlaylist) }
                    )
                }
            }

            // Recently Downloaded Special Library (Shown for ALL, PLAYLISTS)
            if ((selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.PLAYLISTS) && downloadedPlaylist != null) {
                item(key = "recently_downloaded") {
                    LibraryItemRow(
                        title = downloadedPlaylist.name,
                        subtitle = "Auto-Playlist • ${downloadedPlaylist.songCount} downloaded songs",
                        artworkUri = downloadedPlaylist.artworkUri,
                        isRecentlyDownloadedSpecial = true,
                        icon = Icons.Default.DownloadDone,
                        iconTint = Color(0xFF00E676),
                        onClick = { onPlaylistClick(downloadedPlaylist) },
                        onPlayClick = { onPlayPlaylistClick(downloadedPlaylist) }
                    )
                }
            }

            // User-Created Playlists
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.PLAYLISTS) {
                items(userPlaylists, key = { it.id }) { playlist ->
                    LibraryItemRow(
                        title = playlist.name,
                        subtitle = "Playlist • ${playlist.songCount} songs${if (playlist.totalDurationMs > 0) " • ${playlist.formattedDuration}" else ""}",
                        artworkUri = playlist.artworkUri,
                        icon = Icons.Default.QueueMusic,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onPlaylistClick(playlist) },
                        onPlayClick = { onPlayPlaylistClick(playlist) }
                    )
                }
            }

            // Folder-based Libraries
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.FOLDERS) {
                items(folderPlaylists, key = { it.id }) { playlist ->
                    LibraryItemRow(
                        title = playlist.name,
                        subtitle = "Folder Library • ${playlist.songCount} songs${if (playlist.totalDurationMs > 0) " • ${playlist.formattedDuration}" else ""}",
                        artworkUri = playlist.artworkUri,
                        icon = Icons.Default.Folder,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onPlaylistClick(playlist) },
                        onPlayClick = { onPlayPlaylistClick(playlist) }
                    )
                }
            }

            // Other Playlists
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.PLAYLISTS) {
                items(otherPlaylists, key = { it.id }) { playlist ->
                    LibraryItemRow(
                        title = playlist.name,
                        subtitle = "Playlist • ${playlist.songCount} songs",
                        artworkUri = playlist.artworkUri,
                        icon = Icons.Default.QueueMusic,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onPlaylistClick(playlist) },
                        onPlayClick = { onPlayPlaylistClick(playlist) }
                    )
                }
            }

            // Official Albums (When ALL or ALBUMS selected)
            if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.ALBUMS) {
                items(albums, key = { it.id }) { album ->
                    LibraryItemRow(
                        title = album.name,
                        subtitle = "Album • ${album.artistSummary} • ${album.songCount} songs",
                        artworkUri = album.artworkUri,
                        icon = Icons.Default.Album,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { onAlbumClick(album) },
                        onPlayClick = null
                    )
                }
            }

            // Artists (When ARTISTS selected)
            if (selectedFilter == LibraryFilter.ARTISTS) {
                items(artists, key = { it.id }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { onArtistClick(artist.name) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Empty state
            val totalItems = when (selectedFilter) {
                LibraryFilter.ALL -> playlists.size + albums.size
                LibraryFilter.PLAYLISTS -> playlists.filter { it.playlistType != "FOLDER" }.size
                LibraryFilter.FOLDERS -> folderPlaylists.size
                LibraryFilter.ALBUMS -> albums.size
                LibraryFilter.ARTISTS -> artists.size
            }

            if (totalItems == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No items in this section",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Create a new playlist to begin organizing your collection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onCreatePlaylistClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Playlist")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItemRow(
    title: String,
    subtitle: String,
    artworkUri: android.net.Uri?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)?,
    isLikedSpecial: Boolean = false,
    isRecentlyDownloadedSpecial: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (isLikedSpecial) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Liked Songs",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (isRecentlyDownloadedSpecial) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF304FFE))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Recently Downloaded",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (artworkUri != null) {
                ArtworkView(
                    artworkUri = artworkUri,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Quick play button
        if (onPlayClick != null) {
            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
