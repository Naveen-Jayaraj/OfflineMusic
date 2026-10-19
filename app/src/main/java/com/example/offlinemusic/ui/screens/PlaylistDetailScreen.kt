package com.example.offlinemusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.offlinemusic.data.model.Playlist
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.ui.components.ArtworkView
import com.example.offlinemusic.ui.components.EqualizerBars

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentSong: Song?,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onAddSongsClick: () -> Unit,
    onRemoveSongClick: (Song) -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onAddToAnotherPlaylistClick: (Song) -> Unit = {},
    onEditSongClick: (Song) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isLikedSpecial = playlist.isLikedSpecial
    val isFolder = playlist.playlistType == "FOLDER"
    val isUserPlaylist = playlist.playlistType == "USER"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = when {
                        isLikedSpecial -> Icons.Default.Favorite
                        isFolder -> Icons.Default.Folder
                        else -> Icons.Default.QueueMusic
                    },
                    contentDescription = null,
                    tint = when {
                        isLikedSpecial -> Color(0xFFE91E63)
                        isFolder -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isLikedSpecial -> "LIKED SONGS"
                        isFolder -> "FOLDER PLAYLIST"
                        else -> "PLAYLIST"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                if (isUserPlaylist) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Songs to Playlist...", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                onAddSongsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeletePlaylistClick()
                            }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover Box
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    ) {
                        if (isLikedSpecial) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFE91E63),
                                                Color(0xFF9C27B0),
                                                Color(0xFF3F51B5)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Liked Songs",
                                    tint = Color.White,
                                    modifier = Modifier.size(68.dp)
                                )
                            }
                        } else if (playlist.artworkUri != null) {
                            ArtworkView(
                                artworkUri = playlist.artworkUri,
                                size = 190.dp,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFolder) Icons.Default.Folder else Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = if (isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(68.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                isLikedSpecial -> Icons.Default.Favorite
                                isFolder -> Icons.Default.Folder
                                else -> Icons.Default.QueueMusic
                            },
                            contentDescription = null,
                            tint = when {
                                isLikedSpecial -> Color(0xFFE91E63)
                                isFolder -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when {
                            isLikedSpecial -> "Your collection of favorite tracks"
                            isFolder -> "All tracks in this folder"
                            else -> playlist.description ?: "User Playlist"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${playlist.songCount} songs${if (playlist.totalDurationMs > 0) " • ${playlist.formattedDuration}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPlayAllClick,
                            enabled = playlist.songs.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "PLAY", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }

                        OutlinedButton(
                            onClick = onShuffleAllClick,
                            enabled = playlist.songs.isNotEmpty(),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "SHUFFLE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }

                    if (isUserPlaylist) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onAddSongsClick,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ADD SONGS TO PLAYLIST", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Tracks header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TRACKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${playlist.songs.size} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (playlist.songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLikedSpecial) "No liked songs yet. Tap the heart on any song to add it."
                            else "No songs in this playlist. Tap 'Add Songs' to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(playlist.songs, key = { _, s -> s.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    var songMenuExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                            .clickable { onSongClick(song, playlist.songs) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Track index / Equalizer
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent && isPlaying) {
                                EqualizerBars(modifier = Modifier.size(18.dp))
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        ArtworkView(
                            artworkUri = song.artworkUri,
                            size = 44.dp,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = song.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(4.dp))

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
                                    text = { Text("Edit Song Info...", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        songMenuExpanded = false
                                        onEditSongClick(song)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to another Playlist...", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        songMenuExpanded = false
                                        onAddToAnotherPlaylistClick(song)
                                    }
                                )

                                val songArtists = song.artist.split(",", "/", ";", "\\")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                if (songArtists.size <= 1) {
                                    DropdownMenuItem(
                                        text = { Text("View Artist Profile", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            songMenuExpanded = false
                                            onArtistClick(songArtists.firstOrNull() ?: song.artist)
                                        }
                                    )
                                } else {
                                    songArtists.forEach { individualArtist ->
                                        DropdownMenuItem(
                                            text = { Text("Artist: $individualArtist", color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                songMenuExpanded = false
                                                onArtistClick(individualArtist)
                                            }
                                        )
                                    }
                                }

                                DropdownMenuItem(
                                    text = { Text(if (isLikedSpecial) "Unlike (Remove from Liked)" else "Remove from this Playlist", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        songMenuExpanded = false
                                        onRemoveSongClick(song)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}
