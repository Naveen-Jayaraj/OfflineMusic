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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import com.example.offlinemusic.data.database.MusicDatabase
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.ui.components.ArtworkView
import com.example.offlinemusic.ui.components.EqualizerBars

@Composable
fun AlbumDetailScreen(
    album: Album,
    currentSong: Song?,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onAddSongsClick: () -> Unit,
    onRemoveSongClick: (Song) -> Unit,
    onMoveSongClick: (Song) -> Unit,
    onAddToAnotherAlbumClick: (Song) -> Unit,
    onDeleteAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAlbumMenu by remember { mutableStateOf(false) }
    val isLikedSpecial = album.id == MusicDatabase.ALBUM_ID_LIKED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // Back Button & Header
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
                Text(
                    text = if (isLikedSpecial) "FAVORITES" else if (album.isCustom) "CUSTOM ALBUM" else "ALBUM",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { showAlbumMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                DropdownMenu(
                    expanded = showAlbumMenu,
                    onDismissRequest = { showAlbumMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Songs to Album...", color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showAlbumMenu = false
                            onAddSongsClick()
                        }
                    )
                    if (album.isCustom && !isLikedSpecial) {
                        DropdownMenuItem(
                            text = { Text("Delete Album", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showAlbumMenu = false
                                onDeleteAlbumClick()
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
                    // Album Cover
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    ) {
                        ArtworkView(
                            artworkUri = album.artworkUri,
                            size = 190.dp,
                            shape = RoundedCornerShape(20.dp),
                            isLikedSongsSpecial = isLikedSpecial,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Album Name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isLikedSpecial) Icons.Filled.Favorite else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isLikedSpecial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = album.artistSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${album.songCount} songs • ${album.formattedDuration}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons: Play, Shuffle, Add Songs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPlayAllClick,
                            enabled = album.songs.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "PLAY", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }

                        OutlinedButton(
                            onClick = onShuffleAllClick,
                            enabled = album.songs.isNotEmpty(),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "SHUFFLE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onBackground)
                        }

                        OutlinedButton(
                            onClick = onAddSongsClick,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (album.songs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "This album is empty",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add Songs' above to add music to this album.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(album.songs, key = { _, s -> s.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    var songMenuExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                            )
                            .border(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSongClick(song, album.songs) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(26.dp)
                        )

                        ArtworkView(
                            artworkUri = song.artworkUri,
                            size = 38.dp,
                            shape = RoundedCornerShape(6.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                ),
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

                        if (isCurrent && isPlaying) {
                            EqualizerBars()
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // 3-dots popup menu
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
                                    text = { Text("Add to another Album...", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        songMenuExpanded = false
                                        onAddToAnotherAlbumClick(song)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to another Album...", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        songMenuExpanded = false
                                        onMoveSongClick(song)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isLikedSpecial) "Unlike (Remove from Liked)" else "Remove from this Album", color = MaterialTheme.colorScheme.error) },
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
