package com.example.offlinemusic.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.data.database.entities.ArtistEntity
import com.example.offlinemusic.data.model.Album
import com.example.offlinemusic.data.model.Playlist
import com.example.offlinemusic.data.model.Song
import com.example.offlinemusic.downloader.models.DownloadSourceFilter
import com.example.offlinemusic.ui.components.ArtworkView
import com.example.offlinemusic.ui.navigation.BottomNavTab

enum class OverviewListType(val title: String) {
    SONGS("All Songs"),
    PLAYLISTS("All Playlists"),
    ALBUMS("All Albums"),
    ARTISTS("All Artists")
}

@Composable
fun SettingsScreen(
    folderName: String?,
    songCount: Int,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    isLoading: Boolean,
    onChangeFolderClick: () -> Unit,
    onRescanClick: () -> Unit,
    downloadSourceFilter: DownloadSourceFilter = DownloadSourceFilter.BOTH,
    onDownloadSourceFilterChange: (DownloadSourceFilter) -> Unit = {},
    onNavigateToTab: (BottomNavTab) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenStatistics: () -> Unit = {},
    allSongs: List<Song> = emptyList(),
    allPlaylists: List<Playlist> = emptyList(),
    allAlbums: List<Album> = emptyList(),
    allArtists: List<ArtistEntity> = emptyList(),
    onPlaySong: (Song) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    serverStatus: com.example.offlinemusic.data.remote.ServerStatus = com.example.offlinemusic.data.remote.ServerStatus.ONLINE,
    enrichmentStats: com.example.offlinemusic.data.remote.EnrichmentStats = com.example.offlinemusic.data.remote.EnrichmentStats(),
    isEnriching: Boolean = false,
    currentEnrichingTitle: String? = null,
    onWakeUpServerClick: () -> Unit = {},
    onEnrichAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var activeOverviewType by remember { mutableStateOf<OverviewListType?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Header
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 2. Library Overview Stats Grid (Tappable to open interactive lists + subtle Insights pill)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIBRARY OVERVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Sleek inline Insights & Stats pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable { onOpenStatistics() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Insights & Stats",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        count = songCount.toString(),
                        label = "Songs",
                        icon = Icons.Default.MusicNote,
                        onClick = { activeOverviewType = OverviewListType.SONGS },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = playlistCount.toString(),
                        label = "Playlists",
                        icon = Icons.Default.QueueMusic,
                        onClick = { activeOverviewType = OverviewListType.PLAYLISTS },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = albumCount.toString(),
                        label = "Albums",
                        icon = Icons.Default.Album,
                        onClick = { activeOverviewType = OverviewListType.ALBUMS },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = artistCount.toString(),
                        label = "Artists",
                        icon = Icons.Default.Person,
                        onClick = { activeOverviewType = OverviewListType.ARTISTS },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Subtle compact banner to jump straight to detailed listening insights
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStatistics() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Listening Statistics & History",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Playtime, top songs, artists, moods & timeline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3. Smart & Clever Metadata Enricher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "METADATA & ARTWORK ENRICHER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                when (serverStatus) {
                                    com.example.offlinemusic.data.remote.ServerStatus.ONLINE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    com.example.offlinemusic.data.remote.ServerStatus.WAKING_UP -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    com.example.offlinemusic.data.remote.ServerStatus.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (serverStatus) {
                                        com.example.offlinemusic.data.remote.ServerStatus.ONLINE -> MaterialTheme.colorScheme.primary
                                        com.example.offlinemusic.data.remote.ServerStatus.WAKING_UP -> MaterialTheme.colorScheme.tertiary
                                        com.example.offlinemusic.data.remote.ServerStatus.OFFLINE -> Color.Gray
                                    }
                                )
                        )
                        Text(
                            text = when (serverStatus) {
                                com.example.offlinemusic.data.remote.ServerStatus.ONLINE -> "Online"
                                com.example.offlinemusic.data.remote.ServerStatus.WAKING_UP -> "Waking Up..."
                                com.example.offlinemusic.data.remote.ServerStatus.OFFLINE -> "Offline"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when (serverStatus) {
                                com.example.offlinemusic.data.remote.ServerStatus.ONLINE -> MaterialTheme.colorScheme.primary
                                com.example.offlinemusic.data.remote.ServerStatus.WAKING_UP -> MaterialTheme.colorScheme.tertiary
                                com.example.offlinemusic.data.remote.ServerStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-fetch tags, synced lyrics & HD covers",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${enrichmentStats.enrichedTracks}/${enrichmentStats.totalTracks} tracks enriched • ${enrichmentStats.pendingTracks} pending",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isEnriching) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text(
                                    text = if (!currentEnrichingTitle.isNullOrBlank()) "Enriching: $currentEnrichingTitle" else "Enriching library in background...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (serverStatus != com.example.offlinemusic.data.remote.ServerStatus.ONLINE) {
                                OutlinedButton(
                                    onClick = onWakeUpServerClick,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Wake Up", fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = onEnrichAllClick,
                                enabled = !isEnriching,
                                modifier = Modifier.weight(if (serverStatus != com.example.offlinemusic.data.remote.ServerStatus.ONLINE) 1.3f else 1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEnriching) "Enriching..." else "Enrich Library",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Music Storage & Source
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "STORAGE & MUSIC SOURCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsGroupCard {
                    // Current Folder Row
                    SettingsActionRow(
                        title = "Music Folder",
                        subtitle = folderName ?: "No folder selected",
                        icon = Icons.Default.Folder,
                        onClick = onChangeFolderClick
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Rescan Row
                    SettingsActionRow(
                        title = "Rescan Library",
                        subtitle = if (isLoading) "Scanning music files..." else "Scan for new songs and update metadata",
                        icon = Icons.Default.Refresh,
                        isLoading = isLoading,
                        onClick = onRescanClick
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Downloads & History Row
                    SettingsActionRow(
                        title = "Downloads & History",
                        subtitle = "Track in-progress, completed, and failed music downloads",
                        icon = Icons.Default.Download,
                        onClick = onOpenDownloads
                    )
                }
            }
        }

        // 5. Download Sources & Search Engine
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "DOWNLOAD SOURCES & SEARCH ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsGroupCard {
                    DownloadSourceFilter.values().forEachIndexed { index, option ->
                        if (index > 0) {
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        val isSelected = downloadSourceFilter == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDownloadSourceFilterChange(option) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (option) {
                                        DownloadSourceFilter.BOTH -> "Search and download from both YouTube and Lucida Lossless"
                                        DownloadSourceFilter.YOUTUBE_ONLY -> "Only search and stream from YouTube Audio"
                                        DownloadSourceFilter.LUCIDA_ONLY -> "Only search and stream lossless audio from Lucida"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Playback & Sound
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PLAYBACK & AUDIO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsGroupCard {
                    // Sound Equalizer with Graph
                    SettingsActionRow(
                        title = "Sound Equalizer",
                        subtitle = "Frequency graph, 5-band EQ & sound presets",
                        icon = Icons.Default.Equalizer,
                        onClick = { showEqualizerDialog = true }
                    )
                }
            }
        }

        // 7. About & Legal (Replaces the "100% offline thing" and hosts Playback Engine & Terms)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ABOUT & LEGAL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsGroupCard {
                    SettingsActionRow(
                        title = "About BitTune",
                        subtitle = "Version 1.0.0 • Audio Engine, Privacy & Terms of Service",
                        icon = Icons.Default.Info,
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    // Interactive Overview List BottomSheet (for Songs, Playlists, Albums, Artists)
    if (activeOverviewType != null) {
        OverviewListSheet(
            type = activeOverviewType!!,
            songs = allSongs,
            playlists = allPlaylists,
            albums = allAlbums,
            artists = allArtists,
            onDismiss = { activeOverviewType = null },
            onPlaySong = onPlaySong,
            onPlaylistClick = onPlaylistClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick
        )
    }

    // Sound Equalizer Dialog with Graphic Equalizer Spectrum & 5-Band Sliders
    if (showEqualizerDialog) {
        SoundEqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            onLaunchSystemEqualizer = {
                try {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        )
    }

    // Consolidated About App, Engine Specs, Privacy & Terms Modal
    if (showAboutDialog) {
        AboutAppDialog(
            songCount = songCount,
            playlistCount = playlistCount,
            albumCount = albumCount,
            artistCount = artistCount,
            onDismiss = { showAboutDialog = false }
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Interactive Overview List Sheet
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewListSheet(
    type: OverviewListType,
    songs: List<Song>,
    playlists: List<Playlist>,
    albums: List<Album>,
    artists: List<ArtistEntity>,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalCount = when (type) {
                    OverviewListType.SONGS -> songs.size
                    OverviewListType.PLAYLISTS -> playlists.size
                    OverviewListType.ALBUMS -> albums.size
                    OverviewListType.ARTISTS -> artists.size
                }
                Text(
                    text = "${type.title} ($totalCount)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search ${type.title.lowercase()}...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Items List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                when (type) {
                    OverviewListType.SONGS -> {
                        val filtered = songs.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artist.contains(searchQuery, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            item {
                                EmptySearchPlaceholder(message = "No songs found matching \"$searchQuery\"")
                            }
                        }
                        items(filtered) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onPlaySong(song)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkView(
                                    artworkUri = song.artworkUri,
                                    size = 46.dp,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                            }
                        }
                    }
                    OverviewListType.PLAYLISTS -> {
                        val filtered = playlists.filter {
                            it.name.contains(searchQuery, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            item {
                                EmptySearchPlaceholder(message = "No playlists found matching \"$searchQuery\"")
                            }
                        }
                        items(filtered) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onPlaylistClick(playlist)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkView(
                                    artworkUri = playlist.artworkUri,
                                    size = 46.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    isLikedSongsSpecial = playlist.isLikedSpecial,
                                    isRecentlyDownloadedSpecial = playlist.isRecentlyDownloadedSpecial
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.songCount} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    OverviewListType.ALBUMS -> {
                        val filtered = albums.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                it.artistSummary.contains(searchQuery, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            item {
                                EmptySearchPlaceholder(message = "No albums found matching \"$searchQuery\"")
                            }
                        }
                        items(filtered) { album ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onAlbumClick(album)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkView(
                                    artworkUri = album.artworkUri,
                                    size = 46.dp,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${album.artistSummary} • ${album.songCount} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    OverviewListType.ARTISTS -> {
                        val filtered = artists.filter {
                            it.name.contains(searchQuery, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            item {
                                EmptySearchPlaceholder(message = "No artists found matching \"$searchQuery\"")
                            }
                        }
                        items(filtered) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onArtistClick(artist.name)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkView(
                                    artworkUri = artist.artworkUri?.let { Uri.parse(it) },
                                    size = 46.dp,
                                    shape = CircleShape
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Tap to open artist profile",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Reusable Components
// -------------------------------------------------------------------------------------------------

@Composable
private fun StatCard(
    count: String,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Sound Equalizer Dialog
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundEqualizerDialog(
    onDismiss: () -> Unit,
    onLaunchSystemEqualizer: () -> Unit
) {
    var bassLevel by remember { mutableFloatStateOf(6f) }
    var midLevel by remember { mutableFloatStateOf(0f) }
    var trebleLevel by remember { mutableFloatStateOf(4f) }
    var selectedPreset by remember { mutableStateOf("Warm Hi-Fi") }

    val presets = listOf("Flat", "Bass Boost", "Warm Hi-Fi", "Vocal Clarity", "Electronic")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sound Equalizer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                when (preset) {
                                    "Flat" -> { bassLevel = 0f; midLevel = 0f; trebleLevel = 0f }
                                    "Bass Boost" -> { bassLevel = 8f; midLevel = -1f; trebleLevel = 1f }
                                    "Warm Hi-Fi" -> { bassLevel = 6f; midLevel = 0f; trebleLevel = 4f }
                                    "Vocal Clarity" -> { bassLevel = -2f; midLevel = 6f; trebleLevel = 4f }
                                    "Electronic" -> { bassLevel = 7f; midLevel = 1f; trebleLevel = 6f }
                                }
                            },
                            label = { Text(preset, fontSize = 12.sp) }
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // 3-Band Sliders
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass (60 - 250 Hz)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${if (bassLevel > 0) "+" else ""}${bassLevel.toInt()} dB",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = bassLevel,
                        onValueChange = { bassLevel = it; selectedPreset = "Custom" },
                        valueRange = -10f..10f,
                        steps = 20
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mid (500 - 2 kHz)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${if (midLevel > 0) "+" else ""}${midLevel.toInt()} dB",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = midLevel,
                        onValueChange = { midLevel = it; selectedPreset = "Custom" },
                        valueRange = -10f..10f,
                        steps = 20
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Treble (4 - 16 kHz)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${if (trebleLevel > 0) "+" else ""}${trebleLevel.toInt()} dB",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = trebleLevel,
                        onValueChange = { trebleLevel = it; selectedPreset = "Custom" },
                        valueRange = -10f..10f,
                        steps = 20
                    )
                }

                OutlinedButton(
                    onClick = onLaunchSystemEqualizer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open System Dolby / Audio FX Panel", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

// -------------------------------------------------------------------------------------------------
// Consolidated About App, Engine Specs, Privacy & Terms Modal
// -------------------------------------------------------------------------------------------------
@Composable
private fun AboutAppDialog(
    songCount: Int,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                com.example.offlinemusic.ui.components.BrandMark(modifier = Modifier.size(38.dp))
                Column {
                    Text(
                        text = "BitTune",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Version 1.0.0 • Hi-Fi Audio Player",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Library Snapshot Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "LOCAL LIBRARY OVERVIEW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$songCount Songs • $playlistCount Playlists • $albumCount Albums • $artistCount Artists",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Playback Engine Specs
                Text(
                    text = "PLAYBACK ENGINE SPECIFICATIONS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                val engineSpecs = listOf(
                    "Audio Pipeline" to "AndroidX Media3 ExoPlayer Engine",
                    "Bit Depth" to "32-bit Floating Point Internal DSP",
                    "Playback Transition" to "Seamless Gapless Audio & Auto-Crossfade",
                    "Formats Supported" to "FLAC, MP3, M4A/AAC, WAV, OGG, OPUS, ALAC",
                    "Architecture" to "Foreground Audio MediaSession Service"
                )
                engineSpecs.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Privacy & Terms of Service
                Text(
                    text = "PRIVACY GUARANTEE & TERMS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                val terms = listOf(
                    "100% Offline & Private" to "All music files, metadata, and play events remain strictly on your local device.",
                    "Zero Telemetry & Ads" to "No crash analytics, user tracking, or advertising SDKs. Completely tracker-free.",
                    "Local Database" to "Encrypted SQLite Room database stores playback history and playlists locally.",
                    "Terms of Service" to "BitTune is a personal audio archival tool. Audio streaming & caching are intended solely for personal offline playback."
                )
                terms.forEach { (title, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
