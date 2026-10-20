package com.example.offlinemusic.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.offlinemusic.downloader.models.DuplicatePromptData
import java.util.Locale

@Composable
fun DuplicateSongDialog(
    prompt: DuplicatePromptData,
    onKeepBoth: () -> Unit,
    onReplaceExisting: () -> Unit,
    onDiscard: () -> Unit,
    onDecideLater: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val spotifyGreen = Color(0xFF1DB954)
    val amberWarning = Color(0xFFFFB300)
    val matchBadgeColor = if (prompt.matchPercentage >= 85) spotifyGreen else amberWarning

    AlertDialog(
        onDismissRequest = onDecideLater,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(matchBadgeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = matchBadgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "DUPLICATE FOUND",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Song already in database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Match Percentage Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(matchBadgeColor.copy(alpha = 0.18f))
                        .border(1.dp, matchBadgeColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${prompt.matchPercentage}% MATCH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        ),
                        color = matchBadgeColor
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "A matching song was found in your library. Compare the original with the download and choose how to proceed:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Comparison Box (Existing vs Downloaded)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Original in Library
                    TrackComparisonItem(
                        badgeLabel = "IN LIBRARY (ORIGINAL)",
                        badgeIcon = Icons.Default.LibraryMusic,
                        badgeColor = MaterialTheme.colorScheme.primary,
                        title = prompt.existingSong.title,
                        artist = prompt.existingSong.artist,
                        album = prompt.existingSong.album,
                        artworkModel = prompt.existingSong.artworkUri,
                        durationStr = prompt.existingSong.formattedDuration,
                        formatStr = prompt.existingSong.formattedSize
                    )

                    // VS divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                    }

                    // 2. Newly Downloaded Track
                    val downloadedDurationFormatted = if (prompt.downloadedDurationMs > 0) {
                        val s = (prompt.downloadedDurationMs / 1000).coerceAtLeast(0)
                        String.format(Locale.US, "%d:%02d", s / 60, s % 60)
                    } else prompt.downloadedTrack.duration

                    val downloadedSizeFormatted = if (prompt.downloadedSizeBytes > 0) {
                        String.format(Locale.US, "%.1f MB", prompt.downloadedSizeBytes.toDouble() / (1024 * 1024))
                    } else prompt.downloadedTrack.quality

                    TrackComparisonItem(
                        badgeLabel = "NEW DOWNLOAD",
                        badgeIcon = Icons.Default.Download,
                        badgeColor = spotifyGreen,
                        title = prompt.enrichedTitle.ifBlank { prompt.downloadedTrack.title },
                        artist = prompt.enrichedArtist.ifBlank { prompt.downloadedTrack.artist },
                        album = prompt.enrichedAlbum.ifBlank { "Downloads" },
                        artworkModel = prompt.downloadedArtworkFile ?: prompt.downloadedTrack.thumbnailUrl,
                        durationStr = downloadedDurationFormatted,
                        formatStr = "${prompt.downloadedFormat.uppercase()} • $downloadedSizeFormatted"
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // The 3 User Action Buttons
                Text(
                    text = "SELECT AN ACTION:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Same song diff quality (Replace Existing & Upgrade)
                DuplicateActionButton(
                    title = "Replace with This Version",
                    subtitle = "Same song, different quality. Updates original track with new file while keeping all playlists and likes.",
                    icon = Icons.Default.SwapHoriz,
                    accentColor = spotifyGreen,
                    isFeatured = true,
                    onClick = onReplaceExisting
                )

                // Option 2: Different Song (Keep Both)
                DuplicateActionButton(
                    title = "Keep Both (Different Song)",
                    subtitle = "Save as a new independent song. Both versions will be kept in your library.",
                    icon = Icons.Default.LibraryAdd,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isFeatured = false,
                    onClick = onKeepBoth
                )

                // Option 3: Discard Download
                DuplicateActionButton(
                    title = "Discard Download",
                    subtitle = "Delete this downloaded file and keep only the original song in your library.",
                    icon = Icons.Default.DeleteOutline,
                    accentColor = MaterialTheme.colorScheme.error,
                    isFeatured = false,
                    onClick = onDiscard
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDecideLater,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Decide Later")
            }
        }
    )
}

@Composable
private fun TrackComparisonItem(
    badgeLabel: String,
    badgeIcon: ImageVector,
    badgeColor: Color,
    title: String,
    artist: String,
    album: String,
    artworkModel: Any?,
    durationStr: String,
    formatStr: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                color = badgeColor
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$artist • $album",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (durationStr.isNotBlank()) {
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (formatStr.isNotBlank()) {
                        Text(
                            text = "• $formatStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isFeatured: Boolean,
    onClick: () -> Unit
) {
    val bgModifier = if (isFeatured) {
        Modifier.background(
            Brush.horizontalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.16f),
                    accentColor.copy(alpha = 0.08f)
                )
            )
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(bgModifier)
            .border(
                width = if (isFeatured) 1.5.dp else 1.dp,
                color = if (isFeatured) accentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isFeatured) accentColor else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
