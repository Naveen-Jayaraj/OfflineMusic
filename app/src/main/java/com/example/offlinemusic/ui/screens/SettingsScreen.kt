package com.example.offlinemusic.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offlinemusic.R
import com.example.offlinemusic.downloader.models.DownloadSourceFilter
import com.example.offlinemusic.ui.navigation.BottomNavTab

@Composable
fun SettingsScreen(
    folderName: String?,
    songCount: Int,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    pendingReviewCount: Int,
    isLoading: Boolean,
    onChangeFolderClick: () -> Unit,
    onRescanClick: () -> Unit,
    onReviewArtistsClick: () -> Unit,
    downloadSourceFilter: DownloadSourceFilter = DownloadSourceFilter.BOTH,
    onDownloadSourceFilterChange: (DownloadSourceFilter) -> Unit = {},
    onNavigateToTab: (BottomNavTab) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showEngineSpecsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

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
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 2. Library Overview Stats Grid (Tappable to jump to Library)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "LIBRARY OVERVIEW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        count = songCount.toString(),
                        label = "Songs",
                        icon = Icons.Default.MusicNote,
                        onClick = { onNavigateToTab(BottomNavTab.LIBRARY) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = playlistCount.toString(),
                        label = "Playlists",
                        icon = Icons.Default.QueueMusic,
                        onClick = { onNavigateToTab(BottomNavTab.LIBRARY) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = albumCount.toString(),
                        label = "Albums",
                        icon = Icons.Default.Album,
                        onClick = { onNavigateToTab(BottomNavTab.LIBRARY) },
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        count = artistCount.toString(),
                        label = "Artists",
                        icon = Icons.Default.Person,
                        onClick = { onNavigateToTab(BottomNavTab.LIBRARY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Music Storage & Source
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

        // 4. Metadata & Artist Review
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "METADATA & ARTIST NORMALIZATION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsGroupCard {
                    SettingsActionRow(
                        title = "Review Artist Normalizations",
                        subtitle = if (pendingReviewCount > 0) "$pendingReviewCount unconfirmed artist groupings pending" else "All artists verified & normalized",
                        icon = Icons.Default.Person,
                        badge = if (pendingReviewCount > 0) "$pendingReviewCount" else null,
                        onClick = onReviewArtistsClick
                    )
                }
            }
        }

        // 5. Playback & Sound
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
                        subtitle = "Frequency graph, 5-band EQ & sound enhancement presets",
                        icon = Icons.Default.Equalizer,
                        onClick = { showEqualizerDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Audio Engine Info
                    SettingsActionRow(
                        title = "Playback Engine",
                        subtitle = "AndroidX Media3 ExoPlayer • Lossless 32-bit audio pipeline",
                        icon = Icons.Default.MusicNote,
                        onClick = { showEngineSpecsDialog = true }
                    )
                }
            }
        }

        // 6. Download Sources & Search Engine
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

        // 7. About & Privacy
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ABOUT & PRIVACY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsGroupCard {
                    SettingsActionRow(
                        title = "100% Offline & Private",
                        subtitle = "No internet permissions, zero telemetry, no ads. All music stays on your device.",
                        icon = Icons.Default.Security,
                        onClick = { showPrivacyDialog = true }
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsActionRow(
                        title = "BitTune",
                        subtitle = "Version 1.0.0 • Retro Hi-Fi Audio Player",
                        icon = Icons.Default.Info,
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
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

    // Playback Engine Specs Modal
    if (showEngineSpecsDialog) {
        AudioEngineSpecsDialog(onDismiss = { showEngineSpecsDialog = false })
    }

    // Privacy Guarantee Dialog
    if (showPrivacyDialog) {
        PrivacyGuaranteeDialog(onDismiss = { showPrivacyDialog = false })
    }

    // About App Dialog
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
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsGroupCard(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        content()
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    badge: String? = null,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Interactive Dialogs for Settings
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SoundEqualizerDialog(
    onDismiss: () -> Unit,
    onLaunchSystemEqualizer: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf("Flat") }
    var band60Hz by remember { mutableFloatStateOf(0f) }
    var band230Hz by remember { mutableFloatStateOf(0f) }
    var band910Hz by remember { mutableFloatStateOf(0f) }
    var band3600Hz by remember { mutableFloatStateOf(0f) }
    var band14kHz by remember { mutableFloatStateOf(0f) }

    fun applyPreset(preset: String) {
        selectedPreset = preset
        when (preset) {
            "Flat" -> {
                band60Hz = 0f; band230Hz = 0f; band910Hz = 0f; band3600Hz = 0f; band14kHz = 0f
            }
            "Bass Boost" -> {
                band60Hz = 6f; band230Hz = 4.5f; band910Hz = 1.5f; band3600Hz = 0f; band14kHz = -1f
            }
            "Vocal" -> {
                band60Hz = -2f; band230Hz = 1.5f; band910Hz = 5f; band3600Hz = 3.5f; band14kHz = 1f
            }
            "Rock" -> {
                band60Hz = 4.5f; band230Hz = 2f; band910Hz = -1f; band3600Hz = 2.5f; band14kHz = 5f
            }
            "Pop" -> {
                band60Hz = -1f; band230Hz = 2f; band910Hz = 4f; band3600Hz = 3f; band14kHz = 0.5f
            }
            "Electronic" -> {
                band60Hz = 5f; band230Hz = 3.5f; band910Hz = 0f; band3600Hz = 2.5f; band14kHz = 4.5f
            }
        }
    }

    val bands = listOf(
        Triple("60 Hz", "Bass", band60Hz),
        Triple("230 Hz", "Low-Mid", band230Hz),
        Triple("910 Hz", "Mid", band910Hz),
        Triple("3.6 kHz", "High-Mid", band3600Hz),
        Triple("14 kHz", "Treble", band14kHz)
    )

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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Sound Equalizer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Graphic Visualizer Spectrum Graph
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141416))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        bands.forEach { (freq, _, value) ->
                            val normalizedHeight by animateFloatAsState(
                                targetValue = ((value + 10f) / 20f).coerceIn(0.12f, 1f),
                                animationSpec = tween(durationMillis = 200),
                                label = "eqBar"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.height(72.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height((normalizedHeight * 52).dp)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    Color(0xFF00E676),
                                                    Color(0xFF1DB954).copy(alpha = 0.7f)
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = freq,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color(0xFFAAAAAA)
                                )
                            }
                        }
                    }
                }

                // Preset selector chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Flat", "Bass Boost", "Vocal", "Rock", "Pop", "Electronic").forEach { preset ->
                        val isSelected = selectedPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { applyPreset(preset) },
                            label = { Text(preset, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                // Interactive 5 Frequency Sliders
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bands.forEachIndexed { index, (freq, name, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$freq ($name)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(95.dp)
                            )

                            Slider(
                                value = value,
                                onValueChange = { newVal ->
                                    selectedPreset = "Custom"
                                    when (index) {
                                        0 -> band60Hz = newVal
                                        1 -> band230Hz = newVal
                                        2 -> band910Hz = newVal
                                        3 -> band3600Hz = newVal
                                        4 -> band14kHz = newVal
                                    }
                                },
                                valueRange = -10f..10f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Text(
                                text = "${if (value >= 0) "+" else ""}${value.toInt()}dB",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(42.dp)
                            )
                        }
                    }
                }

                // System Panel Shortcut
                OutlinedButton(
                    onClick = onLaunchSystemEqualizer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open System Equalizer Panel", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun AudioEngineSpecsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Audio Engine Specs",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "High-fidelity local playback specifications:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val specs = listOf(
                    "Media Pipeline" to "AndroidX Media3 ExoPlayer 1.2.0",
                    "Audio Decoders" to "FLAC Lossless, Opus, MP3, AAC, WAV",
                    "Internal Pipeline" to "32-bit floating point processing",
                    "Gapless Playback" to "Hardware-assisted zero-latency transitions",
                    "Sample Rates" to "44.1 kHz, 48 kHz, 96 kHz Hi-Res support",
                    "Audio Focus" to "Automatic ducking & transient audio pause"
                )

                specs.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

@Composable
private fun PrivacyGuaranteeDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Privacy & Security",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val guarantees = listOf(
                    "100% Offline Storage" to "Your audio files and metadata remain strictly on your local device.",
                    "Zero Telemetry" to "No crash reporters, user profiling, or background tracking scripts.",
                    "No Third-Party Ads" to "Completely ad-free forever with zero marketing SDKs.",
                    "Direct SQLite Database" to "Local Room database stores all playlists and preferences locally."
                )

                guarantees.forEach { (title, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Understood")
            }
        }
    )
}

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
                Image(
                    painter = painterResource(id = R.drawable.ic_bittune_logo),
                    contentDescription = "BitTune Mascot",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                )
                Column {
                    Text(
                        text = "BitTune",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Retro Hi-Fi Music Experience",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "A modern, beautiful, and fluid retro-styled music player with lossless streaming and full offline playback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CURRENT LIBRARY SNAPSHOT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• $songCount Songs • $playlistCount Playlists • $albumCount Albums • $artistCount Artists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = "Built with Jetpack Compose, Material 3, AndroidX Media3 ExoPlayer, and Room Database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
